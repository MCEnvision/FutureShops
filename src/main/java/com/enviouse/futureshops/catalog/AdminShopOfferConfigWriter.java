package com.enviouse.futureshops.catalog;

import com.enviouse.futureshops.catalog.offer.OfferValidationIssue;
import com.enviouse.futureshops.catalog.offer.OfferValidationResult;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferCatalogValidator;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferJsonParser;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferJsonWriter;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferListing;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferRevision;
import com.enviouse.futureshops.server.escrow.stock.migration.CatalogStockRuntime;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class AdminShopOfferConfigWriter {
    private static final String RECEIPTS_KEY = "offerMutationReceipts";
    private static final int MAXIMUM_DURABLE_RECEIPTS = 128;

    private AdminShopOfferConfigWriter() {
    }

    public static synchronized SaveResult save(
            MinecraftServer server,
            Operation operation,
            String shopId,
            String listingId,
            long expectedRevision,
            ServerShopOfferListing candidate
    ) {
        return save(server, operation, shopId, listingId,
                expectedRevision, candidate, null);
    }

    public static synchronized SaveResult save(
            MinecraftServer server,
            Operation operation,
            String shopId,
            String listingId,
            long expectedRevision,
            ServerShopOfferListing candidate,
            MutationIdentity mutation
    ) {
        java.util.Objects.requireNonNull(server, "server");
        java.util.Objects.requireNonNull(operation, "operation");
        String targetId = mutationTargetId(
                operation, listingId, candidate);
        String targetShopId = java.util.Objects.requireNonNullElse(
                shopId, "").strip();
        ShopDefinition currentDefinition = ShopCatalog.get(targetShopId)
                .orElse(null);
        if (currentDefinition == null) {
            return SaveResult.failure(Status.NOT_FOUND, 0L,
                    List.of());
        }
        List<ServerShopOfferListing> current =
                new ArrayList<>(currentDefinition.offers());
        int currentIndex = indexOf(current, targetId);
        if (operation == Operation.CREATE) {
            if (candidate == null || indexOf(current,
                    candidate.listingId()) >= 0) {
                return SaveResult.failure(Status.CONFLICT, 0L,
                        List.of());
            }
        } else if (currentIndex < 0) {
            return SaveResult.failure(Status.NOT_FOUND, 0L,
                    List.of());
        } else if (current.get(currentIndex).revision()
                != expectedRevision) {
            return SaveResult.stale(current.get(currentIndex));
        }

        ServerShopOfferListing normalized = null;
        if (operation != Operation.REMOVE) {
            if (candidate == null) {
                return SaveResult.failure(Status.INVALID, 0L,
                        List.of(new OfferValidationIssue(
                                OfferValidationIssue.Severity.ERROR,
                                "listing",
                                "offer.listing.missing")));
            }
            normalized = candidate.withRevision(
                    ServerShopOfferRevision.compute(candidate));
            if (operation == Operation.UPDATE
                    && !normalized.listingId().equals(targetId)) {
                return SaveResult.failure(Status.INVALID,
                        normalized.revision(),
                        List.of(new OfferValidationIssue(
                                OfferValidationIssue.Severity.ERROR,
                                "listing.listingId",
                                "offer.listing.id.change_not_allowed")));
            }
        }

        switch (operation) {
            case CREATE -> current.add(normalized);
            case UPDATE -> current.set(currentIndex, normalized);
            case DUPLICATE -> {
                if (indexOf(current, normalized.listingId()) >= 0) {
                    return SaveResult.failure(
                            Status.CONFLICT, 0L, List.of());
                }
                current.add(normalized);
            }
            case REMOVE -> current.remove(currentIndex);
        }
        OfferValidationResult validation =
                ServerShopOfferCatalogValidator.validate(
                        current,
                        AdminShopOfferConfigWriter::knownItem,
                        AdminShopOfferConfigWriter::validNbt,
                        com.enviouse.futureshops.catalog.offer
                                .OfferEscrowFanout
                                ::registeredMaximumStackSize);
        if (!validation.valid()) {
            return SaveResult.failure(Status.INVALID,
                    normalized == null ? 0L : normalized.revision(),
                    validation.issues());
        }
        Path path = ShopDefinitionLoader.adminShopPath();
        JsonObject root = AdminShopConfigWriter.readRoot(path);
        if (root == null) {
            return SaveResult.failure(
                    Status.IO_ERROR, 0L, List.of());
        }
        root.addProperty("schemaVersion",
                ServerShopOfferJsonParser.SCHEMA_VERSION);
        root.add("listings",
                ServerShopOfferJsonWriter.writeListings(current));
        ServerShopOfferListing receiptSnapshot =
                operation == Operation.REMOVE ? null : normalized;
        long receiptRevision = receiptSnapshot == null
                ? 0L : receiptSnapshot.revision();
        DurableReceipt pendingReceipt = mutation == null ? null
                : new DurableReceipt(
                mutation.requestId(), mutation.playerId(),
                mutation.requestFingerprint(), receiptRevision,
                Optional.ofNullable(receiptSnapshot),
                targetShopId, targetId, operation, Status.UNAVAILABLE);
        if (mutation != null) {
            appendDurableReceipt(root, pendingReceipt);
        }
        if (!AdminShopConfigWriter.writeValidatedRoot(path, root)) {
            return SaveResult.failure(
                    Status.IO_ERROR, 0L, List.of());
        }
        try {
            CatalogStockRuntime.reload(server);
        } catch (RuntimeException exception) {
            boolean restored = AdminShopConfigWriter
                    .restoreLatestBackup(path);
            boolean restoredRuntime = false;
            if (restored) {
                try {
                    CatalogStockRuntime.reload(server);
                    restoredRuntime = true;
                } catch (RuntimeException restoreException) {
                    exception.addSuppressed(restoreException);
                }
            }
            if (restoredRuntime) {
                if (pendingReceipt == null
                        || persistDurableOutcome(path,
                        pendingReceipt.withStatus(
                                Status.RELOAD_FAILED))) {
                    return SaveResult.failure(
                            Status.RELOAD_FAILED, 0L, List.of());
                }
            }
            return SaveResult.failure(
                    Status.UNAVAILABLE, 0L, List.of());
        }
        ServerShopOfferListing acknowledged =
                operation == Operation.REMOVE ? null
                        : ShopCatalog.getOffer(
                        targetShopId, normalized.listingId())
                        .orElse(normalized);
        if (pendingReceipt != null) {
            DurableReceipt completed = new DurableReceipt(
                    pendingReceipt.requestId(),
                    pendingReceipt.playerId(),
                    pendingReceipt.requestFingerprint(),
                    acknowledged == null ? 0L
                            : acknowledged.revision(),
                    Optional.ofNullable(acknowledged),
                    targetShopId, targetId, operation,
                    Status.SUCCESS);
            if (!persistDurableOutcome(path, completed)) {
                return SaveResult.failure(
                        Status.UNAVAILABLE, 0L, List.of());
            }
        }
        if (operation == Operation.REMOVE) {
            return new SaveResult(Status.SUCCESS, true, 0L,
                    null, List.of());
        }
        return new SaveResult(Status.SUCCESS, true,
                acknowledged.revision(), acknowledged, List.of());
    }

    public static synchronized Optional<DurableReceipt>
    resolveDurableReceipt(
            MinecraftServer server,
            UUID requestId
    ) {
        java.util.Objects.requireNonNull(server, "server");
        Optional<DurableReceipt> found = findDurableReceipt(requestId);
        if (found.isEmpty()
                || found.orElseThrow().status() != Status.UNAVAILABLE) {
            return found;
        }
        DurableReceipt pending = found.orElseThrow();
        if (!pending.hasRecoveryIdentity()) {
            return found;
        }
        if (!pending.matchesCurrentCatalog()) {
            try {
                CatalogStockRuntime.reload(server);
            } catch (RuntimeException exception) {
                return found;
            }
        }
        if (!pending.matchesCurrentCatalog()) {
            return found;
        }
        DurableReceipt completed =
                pending.withStatus(Status.SUCCESS);
        if (!persistDurableOutcome(
                ShopDefinitionLoader.adminShopPath(), completed)) {
            return found;
        }
        return Optional.of(completed);
    }

    public static synchronized Optional<DurableReceipt>
    findDurableReceipt(UUID requestId) {
        java.util.Objects.requireNonNull(requestId, "requestId");
        JsonObject root = AdminShopConfigWriter.readRoot(
                ShopDefinitionLoader.adminShopPath());
        if (root == null) {
            return Optional.empty();
        }
        return findDurableReceipt(root, requestId);
    }

    static Optional<DurableReceipt> findDurableReceipt(
            JsonObject root,
            UUID requestId
    ) {
        java.util.Objects.requireNonNull(root, "root");
        java.util.Objects.requireNonNull(requestId, "requestId");
        if (!root.has(RECEIPTS_KEY)) {
            return Optional.empty();
        }
        JsonElement value = root.get(RECEIPTS_KEY);
        if (!value.isJsonArray()
                || value.getAsJsonArray().size()
                > MAXIMUM_DURABLE_RECEIPTS) {
            throw new IllegalStateException(
                    "Admin offer durable receipts are invalid");
        }
        DurableReceipt match = null;
        for (JsonElement element : value.getAsJsonArray()) {
            DurableReceipt receipt = DurableReceipt.parse(element);
            if (receipt.requestId().equals(requestId)) {
                if (match != null) {
                    throw new IllegalStateException(
                            "Admin offer durable receipt is duplicated");
                }
                match = receipt;
            }
        }
        return Optional.ofNullable(match);
    }

    static void appendDurableReceipt(
            JsonObject root,
            DurableReceipt receipt
    ) {
        JsonArray current = root.has(RECEIPTS_KEY)
                ? root.getAsJsonArray(RECEIPTS_KEY) : new JsonArray();
        JsonArray retained = new JsonArray();
        for (JsonElement element : current) {
            DurableReceipt existing = DurableReceipt.parse(element);
            if (existing.requestId().equals(receipt.requestId())) {
                if (!existing.equals(receipt)) {
                    throw new IllegalStateException(
                            "Admin offer durable receipt identity conflicts");
                }
                return;
            }
            retained.add(element.deepCopy());
        }
        while (retained.size() >= MAXIMUM_DURABLE_RECEIPTS) {
            retained.remove(0);
        }
        retained.add(receipt.toJson());
        root.add(RECEIPTS_KEY, retained);
    }

    private static boolean persistDurableOutcome(
            Path path,
            DurableReceipt receipt
    ) {
        JsonObject root = AdminShopConfigWriter.readRoot(path);
        if (root == null) {
            return false;
        }
        replaceDurableReceipt(root, receipt);
        return AdminShopConfigWriter.writeValidatedRoot(path, root);
    }

    static void replaceDurableReceipt(
            JsonObject root,
            DurableReceipt receipt
    ) {
        JsonArray current = root.has(RECEIPTS_KEY)
                ? root.getAsJsonArray(RECEIPTS_KEY) : new JsonArray();
        JsonArray replaced = new JsonArray();
        boolean found = false;
        for (JsonElement element : current) {
            DurableReceipt existing = DurableReceipt.parse(element);
            if (!existing.requestId().equals(receipt.requestId())) {
                replaced.add(element.deepCopy());
                continue;
            }
            if (found || !existing.sameMutation(receipt)) {
                throw new IllegalStateException(
                        "Admin offer durable receipt identity conflicts");
            }
            replaced.add(receipt.toJson());
            found = true;
        }
        if (!found) {
            while (replaced.size() >= MAXIMUM_DURABLE_RECEIPTS) {
                replaced.remove(0);
            }
            replaced.add(receipt.toJson());
        }
        root.add(RECEIPTS_KEY, replaced);
    }

    private static int indexOf(
            List<ServerShopOfferListing> listings,
            String listingId
    ) {
        for (int index = 0; index < listings.size(); index++) {
            if (listings.get(index).listingId()
                    .equals(listingId)) {
                return index;
            }
        }
        return -1;
    }

    static String mutationTargetId(
            Operation operation,
            String requestedListingId,
            ServerShopOfferListing candidate
    ) {
        if (operation == Operation.CREATE && candidate != null) {
            return candidate.listingId();
        }
        return java.util.Objects.requireNonNullElse(
                requestedListingId, "").strip();
    }

    private static boolean knownItem(String itemId) {
        ResourceLocation identifier = ResourceLocation.tryParse(itemId);
        return identifier != null
                && !"minecraft:air".equals(itemId)
                && ForgeRegistries.ITEMS.containsKey(identifier);
    }

    private static boolean validNbt(String nbt) {
        if (nbt == null || nbt.isBlank()) {
            return true;
        }
        try {
            TagParser.parseTag(nbt);
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    public enum Operation {
        CREATE,
        UPDATE,
        DUPLICATE,
        REMOVE
    }

    public enum Status {
        SUCCESS,
        INVALID,
        STALE,
        CONFLICT,
        NOT_FOUND,
        IO_ERROR,
        RELOAD_FAILED,
        UNAVAILABLE
    }

    public record MutationIdentity(
            UUID requestId,
            UUID playerId,
            String requestFingerprint
    ) {
        public MutationIdentity {
            java.util.Objects.requireNonNull(requestId, "requestId");
            java.util.Objects.requireNonNull(playerId, "playerId");
            requestFingerprint = java.util.Objects.requireNonNull(
                    requestFingerprint, "requestFingerprint");
            if (requestId.equals(new UUID(0L, 0L))
                    || playerId.equals(new UUID(0L, 0L))
                    || !requestFingerprint.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException(
                        "Admin offer mutation identity is invalid");
            }
        }
    }

    public record DurableReceipt(
            UUID requestId,
            UUID playerId,
            String requestFingerprint,
            long revision,
            Optional<ServerShopOfferListing> snapshot,
            String shopId,
            String listingId,
            Operation operation,
            Status status
    ) {
        public DurableReceipt(
                UUID requestId,
                UUID playerId,
                String requestFingerprint,
                long revision,
                Optional<ServerShopOfferListing> snapshot
        ) {
            this(requestId, playerId, requestFingerprint, revision,
                    snapshot, "", "", Operation.UPDATE,
                    Status.SUCCESS);
        }

        public DurableReceipt {
            java.util.Objects.requireNonNull(requestId, "requestId");
            java.util.Objects.requireNonNull(playerId, "playerId");
            requestFingerprint = java.util.Objects.requireNonNull(
                    requestFingerprint, "requestFingerprint");
            snapshot = java.util.Objects.requireNonNull(
                    snapshot, "snapshot");
            shopId = java.util.Objects.requireNonNull(
                    shopId, "shopId").strip();
            listingId = java.util.Objects.requireNonNull(
                    listingId, "listingId").strip();
            java.util.Objects.requireNonNull(operation, "operation");
            java.util.Objects.requireNonNull(status, "status");
            if (requestId.equals(new UUID(0L, 0L))
                    || playerId.equals(new UUID(0L, 0L))
                    || !requestFingerprint.matches("[0-9a-f]{64}")
                    || revision < 0L
                    || snapshot.isEmpty() && revision != 0L
                    || snapshot.isPresent()
                    && snapshot.orElseThrow().revision() != revision
                    || status == Status.UNAVAILABLE
                    && (shopId.isEmpty() || listingId.isEmpty())) {
                throw new IllegalArgumentException(
                        "Admin offer durable receipt is invalid");
            }
        }

        public SaveResult result() {
            return new SaveResult(status,
                    status == Status.SUCCESS, revision,
                    status == Status.SUCCESS
                            ? snapshot.orElse(null) : null,
                    List.of());
        }

        private boolean sameMutation(DurableReceipt other) {
            return requestId.equals(other.requestId)
                    && playerId.equals(other.playerId)
                    && requestFingerprint.equals(
                    other.requestFingerprint)
                    && shopId.equals(other.shopId)
                    && listingId.equals(other.listingId)
                    && operation == other.operation;
        }

        private DurableReceipt withStatus(Status replacement) {
            return new DurableReceipt(
                    requestId, playerId, requestFingerprint,
                    revision, snapshot, shopId, listingId,
                    operation, replacement);
        }

        private boolean hasRecoveryIdentity() {
            return !shopId.isEmpty() && !listingId.isEmpty();
        }

        private boolean matchesCurrentCatalog() {
            Optional<ServerShopOfferListing> current =
                    ShopCatalog.getOffer(shopId,
                            snapshot.map(
                                    ServerShopOfferListing::listingId)
                                    .orElse(listingId));
            if (operation == Operation.REMOVE) {
                return current.isEmpty();
            }
            return current.isPresent()
                    && snapshot.isPresent()
                    && current.orElseThrow().equals(
                    snapshot.orElseThrow());
        }

        private JsonObject toJson() {
            JsonObject object = new JsonObject();
            object.addProperty("requestId", requestId.toString());
            object.addProperty("playerId", playerId.toString());
            object.addProperty("requestFingerprint",
                    requestFingerprint);
            object.addProperty("revision", revision);
            object.addProperty("shopId", shopId);
            object.addProperty("listingId", listingId);
            object.addProperty("operation", operation.name());
            object.addProperty("status", status.name());
            snapshot.ifPresent(value -> object.add(
                    "snapshot",
                    ServerShopOfferJsonWriter.writeListing(value)));
            return object;
        }

        private static DurableReceipt parse(JsonElement element) {
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException(
                        "Admin offer durable receipt must be an object");
            }
            JsonObject object = element.getAsJsonObject();
            UUID requestId = UUID.fromString(
                    requiredText(object, "requestId"));
            UUID playerId = UUID.fromString(
                    requiredText(object, "playerId"));
            String fingerprint = requiredText(
                    object, "requestFingerprint");
            long revision = object.has("revision")
                    ? object.get("revision").getAsLong() : -1L;
            Optional<ServerShopOfferListing> snapshot =
                    object.has("snapshot")
                            ? Optional.of(parseListing(
                            object.getAsJsonObject("snapshot")))
                            : Optional.empty();
            String shopId = object.has("shopId")
                    ? requiredText(object, "shopId") : "";
            String listingId = object.has("listingId")
                    ? requiredText(object, "listingId") : "";
            Operation operation = object.has("operation")
                    ? Operation.valueOf(
                    requiredText(object, "operation"))
                    : Operation.UPDATE;
            Status status = object.has("status")
                    ? Status.valueOf(requiredText(object, "status"))
                    : Status.SUCCESS;
            return new DurableReceipt(requestId, playerId,
                    fingerprint, revision, snapshot,
                    shopId, listingId, operation, status);
        }

        private static ServerShopOfferListing parseListing(
                JsonObject listing
        ) {
            JsonObject root = new JsonObject();
            root.addProperty("schemaVersion",
                    ServerShopOfferJsonParser.SCHEMA_VERSION);
            JsonArray listings = new JsonArray();
            listings.add(listing.deepCopy());
            root.add("listings", listings);
            return ServerShopOfferJsonParser.parse(root).get(0);
        }

        private static String requiredText(
                JsonObject object,
                String key
        ) {
            if (!object.has(key)
                    || !object.get(key).isJsonPrimitive()) {
                throw new IllegalArgumentException(
                        "Admin offer durable receipt field is missing");
            }
            return object.get(key).getAsString();
        }
    }

    public record SaveResult(
            Status status,
            boolean success,
            long revision,
            ServerShopOfferListing snapshot,
            List<OfferValidationIssue> issues
    ) {
        public SaveResult {
            java.util.Objects.requireNonNull(status, "status");
            issues = List.copyOf(issues);
        }

        private static SaveResult failure(
                Status status,
                long revision,
                List<OfferValidationIssue> issues
        ) {
            return new SaveResult(status, false, revision,
                    null, issues);
        }

        private static SaveResult stale(
                ServerShopOfferListing snapshot
        ) {
            return new SaveResult(Status.STALE, false,
                    snapshot.revision(), snapshot, List.of());
        }
    }
}
