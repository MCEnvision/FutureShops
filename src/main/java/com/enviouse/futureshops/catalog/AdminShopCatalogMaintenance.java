package com.enviouse.futureshops.catalog;

import com.enviouse.futureshops.Config;
import com.enviouse.futureshops.catalog.offer.OfferValidationIssue;
import com.enviouse.futureshops.catalog.offer.OfferValidationResult;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferCatalogValidator;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferJsonParser;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferListing;
import com.enviouse.futureshops.server.escrow.stock.migration.CatalogStockRuntime;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class AdminShopCatalogMaintenance {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson PRETTY = new GsonBuilder()
            .setPrettyPrinting().disableHtmlEscaping().create();
    private static final int MAX_RECOVERY_BYTES = 8 * 1024 * 1024;

    private AdminShopCatalogMaintenance() {
    }

    public static synchronized ValidationReport validate() {
        Path path = ShopDefinitionLoader.adminShopPath();
        JsonObject root = AdminShopConfigWriter.readRoot(path);
        if (root == null) {
            return new ValidationReport(0, Config.adminShopMaximumListings,
                    List.of(new CatalogIssue(-1, "catalog", "", "root",
                            "offer.catalog.unreadable")));
        }
        return validate(root);
    }

    static ValidationReport validate(JsonObject root) {
        Objects.requireNonNull(root, "root");
        JsonArray listings = root.has("listings")
                && root.get("listings").isJsonArray()
                ? root.getAsJsonArray("listings") : new JsonArray();
        List<CatalogIssue> issues = new ArrayList<>();
        Set<String> issueKeys = new HashSet<>();
        if (listings.size() > Config.adminShopMaximumListings) {
            addIssue(issues, issueKeys, new CatalogIssue(-1, "catalog", "",
                    "listings", "offer.catalog.too_many_listings"));
        }
        for (int index = 0; index < listings.size(); index++) {
            JsonElement value = listings.get(index);
            if (!value.isJsonObject()) {
                addIssue(issues, issueKeys, new CatalogIssue(index,
                        fallbackListingId(index), "",
                        "listings." + index,
                        "offer.listing.not_object"));
                continue;
            }
            JsonObject listing = value.getAsJsonObject();
            String listingId = listingId(listing, index);
            collectMissingItems(index, listingId, listing,
                    issues, issueKeys);
        }
        try {
            List<ServerShopOfferListing> parsed =
                    ServerShopOfferJsonParser.parse(root);
            OfferValidationResult validation =
                    ServerShopOfferCatalogValidator.validate(parsed,
                            AdminShopCatalogMaintenance::knownItem,
                            AdminShopCatalogMaintenance::validNbt,
                            com.enviouse.futureshops.catalog.offer
                                    .OfferEscrowFanout
                                    ::registeredMaximumStackSize);
            for (OfferValidationIssue issue : validation.issues()) {
                int index = listingIndex(issue.path());
                String listingId = index >= 0 && index < listings.size()
                        && listings.get(index).isJsonObject()
                        ? listingId(listings.get(index).getAsJsonObject(), index)
                        : "catalog";
                addIssue(issues, issueKeys, new CatalogIssue(index,
                        listingId, "", issue.path(), issue.code()));
            }
        } catch (RuntimeException exception) {
            addIssue(issues, issueKeys, new CatalogIssue(-1, "catalog", "",
                    "root", "offer.catalog.parse_failed"));
        }
        return new ValidationReport(listings.size(),
                Config.adminShopMaximumListings, issues);
    }

    public static synchronized QuarantineResult quarantineMissing(
            MinecraftServer server,
            String actor,
            String reason
    ) {
        Objects.requireNonNull(server, "server");
        String normalizedActor = requireText(actor, "actor", 160);
        String normalizedReason = requireText(reason, "reason", 512);
        Path catalogPath = ShopDefinitionLoader.adminShopPath();
        JsonObject root = AdminShopConfigWriter.readRoot(catalogPath);
        if (root == null || !root.has("listings")
                || !root.get("listings").isJsonArray()) {
            return QuarantineResult.failure("catalog_unreadable");
        }
        JsonArray listings = root.getAsJsonArray("listings");
        MissingItemPartition partition = partitionMissing(listings);
        JsonArray retained = partition.retained();
        JsonArray quarantined = partition.quarantined();
        if (quarantined.isEmpty()) {
            return new QuarantineResult(true, 0, null, "nothing_to_do");
        }
        JsonObject candidate = root.deepCopy();
        candidate.add("listings", retained);
        ValidationReport remaining = validate(candidate);
        if (!remaining.valid()) {
            return QuarantineResult.failure("remaining_catalog_invalid");
        }
        Path recoveryFile;
        try {
            recoveryFile = writeRecoveryFile(catalogPath, quarantined,
                    normalizedActor, normalizedReason);
        } catch (IOException | RuntimeException exception) {
            LOGGER.error(
                    "Admin shop missing item recovery file could not be written. catalog {}.",
                    catalogPath, exception);
            return QuarantineResult.failure("recovery_write_failed");
        }
        if (!AdminShopConfigWriter.writeValidatedRoot(
                catalogPath, candidate)) {
            return new QuarantineResult(false, quarantined.size(),
                    recoveryFile, "catalog_write_failed");
        }
        try {
            CatalogStockRuntime.reload(server);
        } catch (RuntimeException exception) {
            boolean restored = AdminShopConfigWriter
                    .restoreLatestBackup(catalogPath);
            if (restored) {
                try {
                    CatalogStockRuntime.reload(server);
                } catch (RuntimeException restoreFailure) {
                    exception.addSuppressed(restoreFailure);
                    restored = false;
                }
            }
            LOGGER.error(
                    "Admin shop missing item quarantine reload failed. catalog {}, recovery {}, restored {}.",
                    catalogPath, recoveryFile, restored, exception);
            return new QuarantineResult(false, quarantined.size(),
                    recoveryFile, restored
                    ? "reload_failed_restored" : "reload_failed");
        }
        LOGGER.warn(
                "Admin shop missing item listings were quarantined. actor {}, count {}, recovery {}.",
                normalizedActor, quarantined.size(), recoveryFile);
        return new QuarantineResult(true, quarantined.size(),
                recoveryFile, "quarantined");
    }

    static MissingItemPartition partitionMissing(JsonArray listings) {
        JsonArray retained = new JsonArray();
        JsonArray quarantined = new JsonArray();
        for (JsonElement value : Objects.requireNonNull(
                listings, "listings")) {
            if (value.isJsonObject()
                    && containsMissingItem(value.getAsJsonObject())) {
                quarantined.add(value.deepCopy());
            } else {
                retained.add(value.deepCopy());
            }
        }
        return new MissingItemPartition(retained, quarantined);
    }

    static Path writeRecoveryFile(
            Path catalogPath,
            JsonArray listings,
            String actor,
            String reason
    ) throws IOException {
        Path shopsDirectory = catalogPath.toAbsolutePath()
                .normalize().getParent();
        if (shopsDirectory == null) {
            throw new IOException("Admin shop directory is unavailable");
        }
        Path recoveryDirectory = shopsDirectory.resolve("recovery")
                .normalize();
        if (!recoveryDirectory.startsWith(shopsDirectory)) {
            throw new IOException("Admin shop recovery path escaped");
        }
        if (Files.exists(recoveryDirectory, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isDirectory(recoveryDirectory,
                    LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(recoveryDirectory)) {
                throw new IOException(
                        "Admin shop recovery directory is unsafe");
            }
        } else {
            Files.createDirectories(recoveryDirectory);
        }
        JsonObject recovery = new JsonObject();
        recovery.addProperty("schemaVersion", 1);
        recovery.addProperty("source", catalogPath.getFileName().toString());
        recovery.addProperty("createdAt", Instant.now().toString());
        recovery.addProperty("actor", actor);
        recovery.addProperty("reason", reason);
        recovery.add("listings", listings.deepCopy());
        byte[] bytes = PRETTY.toJson(recovery)
                .getBytes(StandardCharsets.UTF_8);
        if (bytes.length == 0 || bytes.length > MAX_RECOVERY_BYTES) {
            throw new IOException(
                    "Admin shop recovery file size is invalid");
        }
        String name = "missing-items-" + System.currentTimeMillis()
                + "-" + UUID.randomUUID() + ".json";
        Path target = recoveryDirectory.resolve(name).normalize();
        if (!target.startsWith(recoveryDirectory)) {
            throw new IOException("Admin shop recovery file escaped");
        }
        try (FileChannel channel = FileChannel.open(target,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
        return target;
    }

    private static void collectMissingItems(
            int index,
            String listingId,
            JsonObject listing,
            List<CatalogIssue> issues,
            Set<String> issueKeys
    ) {
        collectComponentItems(index, listingId, listing,
                "outputs", "listings." + index + ".outputs",
                issues, issueKeys);
        if (listing.has("icon") && listing.get("icon").isJsonObject()) {
            collectItem(index, listingId,
                    listing.getAsJsonObject("icon"), "itemId",
                    "listings." + index + ".icon.itemId",
                    issues, issueKeys);
        }
        collectOptionItems(index, listingId, listing,
                "acquireOptions", "itemCosts", issues, issueKeys);
        collectOptionItems(index, listingId, listing,
                "sellOptions", "inputs", issues, issueKeys);
    }

    private static void collectOptionItems(
            int index,
            String listingId,
            JsonObject listing,
            String optionsKey,
            String componentsKey,
            List<CatalogIssue> issues,
            Set<String> issueKeys
    ) {
        if (!listing.has(optionsKey)
                || !listing.get(optionsKey).isJsonArray()) {
            return;
        }
        JsonArray options = listing.getAsJsonArray(optionsKey);
        for (int option = 0; option < options.size(); option++) {
            if (!options.get(option).isJsonObject()) {
                continue;
            }
            collectComponentItems(index, listingId,
                    options.get(option).getAsJsonObject(), componentsKey,
                    "listings." + index + "." + optionsKey + "."
                            + option + "." + componentsKey,
                    issues, issueKeys);
        }
    }

    private static void collectComponentItems(
            int index,
            String listingId,
            JsonObject owner,
            String key,
            String path,
            List<CatalogIssue> issues,
            Set<String> issueKeys
    ) {
        if (!owner.has(key) || !owner.get(key).isJsonArray()) {
            return;
        }
        JsonArray values = owner.getAsJsonArray(key);
        for (int component = 0; component < values.size(); component++) {
            if (values.get(component).isJsonObject()) {
                collectItem(index, listingId,
                        values.get(component).getAsJsonObject(), "itemId",
                        path + "." + component + ".itemId",
                        issues, issueKeys);
            }
        }
    }

    private static void collectItem(
            int index,
            String listingId,
            JsonObject owner,
            String key,
            String path,
            List<CatalogIssue> issues,
            Set<String> issueKeys
    ) {
        if (!owner.has(key) || !owner.get(key).isJsonPrimitive()) {
            return;
        }
        String itemId = owner.get(key).getAsString();
        if (!knownItem(itemId)) {
            addIssue(issues, issueKeys, new CatalogIssue(index,
                    listingId, itemId, path, "offer.item.missing"));
        }
    }

    private static boolean containsMissingItem(JsonObject listing) {
        List<CatalogIssue> issues = new ArrayList<>();
        collectMissingItems(0, listingId(listing, 0), listing,
                issues, new HashSet<>());
        return !issues.isEmpty();
    }

    private static void addIssue(
            List<CatalogIssue> issues,
            Set<String> keys,
            CatalogIssue issue
    ) {
        String key = issue.listingIndex() + "\u0000" + issue.path()
                + "\u0000" + issue.code();
        if (keys.add(key)) {
            issues.add(issue);
        }
    }

    private static int listingIndex(String path) {
        if (path == null || !path.startsWith("listings.")) {
            return -1;
        }
        int start = "listings.".length();
        int end = path.indexOf('.', start);
        String value = end < 0 ? path.substring(start)
                : path.substring(start, end);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private static String listingId(JsonObject listing, int index) {
        if (listing.has("id") && listing.get("id").isJsonPrimitive()) {
            String value = listing.get("id").getAsString().strip();
            if (!value.isEmpty()) {
                return value;
            }
        }
        return fallbackListingId(index);
    }

    private static String fallbackListingId(int index) {
        return "index_" + index;
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

    private static String requireText(
            String value,
            String label,
            int maximumLength
    ) {
        String normalized = Objects.requireNonNull(value, label).strip();
        if (normalized.isEmpty() || normalized.length() > maximumLength
                || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(
                    "Admin shop quarantine " + label + " is invalid");
        }
        return normalized;
    }

    public record CatalogIssue(
            int listingIndex,
            String listingId,
            String itemId,
            String path,
            String code
    ) {
        public CatalogIssue {
            listingId = Objects.requireNonNull(listingId, "listingId");
            itemId = Objects.requireNonNull(itemId, "itemId");
            path = Objects.requireNonNull(path, "path");
            code = Objects.requireNonNull(code, "code");
        }
    }

    public record ValidationReport(
            int listingCount,
            int configuredMaximum,
            List<CatalogIssue> issues
    ) {
        public ValidationReport {
            issues = List.copyOf(Objects.requireNonNull(issues, "issues"));
        }

        public boolean valid() {
            return issues.isEmpty();
        }
    }

    public record QuarantineResult(
            boolean success,
            int quarantinedListings,
            Path recoveryFile,
            String status
    ) {
        public QuarantineResult {
            status = Objects.requireNonNull(status, "status");
        }

        private static QuarantineResult failure(String status) {
            return new QuarantineResult(false, 0, null, status);
        }
    }

    record MissingItemPartition(
            JsonArray retained,
            JsonArray quarantined
    ) {
        MissingItemPartition {
            retained = Objects.requireNonNull(retained, "retained");
            quarantined = Objects.requireNonNull(
                    quarantined, "quarantined");
        }
    }
}
