package com.enviouse.futureshops.catalog;

import com.enviouse.futureshops.Config;
import com.enviouse.futureshops.server.shop.ShopDataService;
import com.enviouse.futureshops.server.economy.BalanceManager;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Logical server boundary for the bulk editor. Preview tickets are tied to the administrator who
 * opened them, the registry snapshot, and the exact catalog fingerprint. Completed request ids are
 * retained in a bounded replay cache so a lost response can be retried without writing twice.
 */
public final class AdminBulkListingService {
    public static final int MAX_REPLAY_ENTRIES = 512;

    private static final Map<UUID, Ticket> TICKETS = new LinkedHashMap<>();
    private static final Map<UUID, Completed> COMPLETED = new LinkedHashMap<>();

    private AdminBulkListingService() {
    }

    public record PreviewRequest(UUID requestId, List<AdminBulkListingPlanner.Selection> selections,
                                 String categoryId, String priceText, String stockText,
                                 String registryFingerprint) {
        public PreviewRequest {
            selections = List.copyOf(selections == null ? List.of() : selections);
        }
    }

    public record CommitRequest(UUID requestId, String previewFingerprint,
                                String catalogFingerprint, String registryFingerprint,
                                Set<String> replaceListingIds) {
        public CommitRequest {
            replaceListingIds = Set.copyOf(replaceListingIds == null ? Set.of() : replaceListingIds);
        }
    }

    public enum Status {
        PREVIEW_READY,
        COMMITTED,
        NO_CHANGES,
        DENIED,
        INVALID,
        STALE,
        REPLAY,
        REQUEST_CONFLICT,
        IO_ERROR
    }

    public record PreviewResult(Status status, String message, AdminBulkListingPlanner.Preview preview) {
        public boolean success() {
            return status == Status.PREVIEW_READY;
        }
    }

    public record CommitResult(Status status, String message, AdminBulkListingPlanner.Preview preview) {
        public boolean success() {
            return status == Status.COMMITTED || status == Status.NO_CHANGES || status == Status.REPLAY;
        }
    }

    public static synchronized PreviewResult preview(ServerPlayer player, PreviewRequest request) {
        if (player == null || !player.hasPermissions(2)) {
            return new PreviewResult(Status.DENIED, "administrator permission is required", null);
        }
        if (request == null || request.requestId() == null) {
            return new PreviewResult(Status.INVALID, "request id is required", null);
        }
        Set<String> registry = registrySnapshot();
        String actualRegistryFingerprint = AdminBulkListingPlanner.registryFingerprint(registry);
        if (request.registryFingerprint() != null && !request.registryFingerprint().isBlank()
                && !actualRegistryFingerprint.equals(request.registryFingerprint())) {
            return new PreviewResult(Status.STALE, "the item registry changed, refresh the picker", null);
        }
        JsonObject root = AdminShopConfigWriter.readRoot(ShopDefinitionLoader.adminShopPath());
        AdminBulkListingPlanner.Result planned = AdminBulkListingPlanner.preview(
                request.requestId(), registry, root, request.selections(), request.categoryId(),
                request.priceText(), request.stockText(), currencyDecimals(), maximumStock());
        if (!planned.valid()) {
            return new PreviewResult(Status.INVALID, planned.error(), null);
        }
        if (planned.preview().candidate().getAsJsonArray("items").size()
                > Config.adminShopMaximumListings) {
            return new PreviewResult(Status.INVALID,
                    "the admin shop listing limit would be exceeded", null);
        }
        TICKETS.put(request.requestId(), new Ticket(player.getUUID(), request, planned.preview()));
        trim(TICKETS);
        return new PreviewResult(Status.PREVIEW_READY, "preview ready", planned.preview());
    }

    public static synchronized CommitResult commit(ServerPlayer player, CommitRequest request) {
        if (player == null || !player.hasPermissions(2)) {
            return new CommitResult(Status.DENIED, "administrator permission is required", null);
        }
        if (request == null || request.requestId() == null) {
            return new CommitResult(Status.INVALID, "request id is required", null);
        }
        Completed prior = COMPLETED.get(request.requestId());
        AdminBulkReplaySavedData durable = player.getServer() == null
                ? null : AdminBulkReplaySavedData.get(player.getServer());
        if (prior == null && durable != null) {
            durable.find(request.requestId()).ifPresent(preview ->
                    COMPLETED.put(request.requestId(),
                            new Completed(preview.fingerprint(), preview)));
            prior = COMPLETED.get(request.requestId());
        }
        if (prior != null) {
            if (prior.fingerprint().equals(request.previewFingerprint())) {
                return new CommitResult(Status.REPLAY, "original bulk result replayed", prior.preview());
            }
            return new CommitResult(Status.REQUEST_CONFLICT, "request id is already bound to another batch", null);
        }
        Ticket ticket = TICKETS.get(request.requestId());
        if (ticket == null || !ticket.owner().equals(player.getUUID())) {
            return new CommitResult(Status.STALE, "preview expired, request a new preview", null);
        }
        if (!ticket.preview().fingerprint().equals(request.previewFingerprint())) {
            return new CommitResult(Status.STALE, "preview no longer matches the draft", null);
        }
        Set<String> registry = registrySnapshot();
        String registryFingerprint = AdminBulkListingPlanner.registryFingerprint(registry);
        if (!registryFingerprint.equals(request.registryFingerprint())
                || !registryFingerprint.equals(ticket.preview().registryFingerprint())) {
            return new CommitResult(Status.STALE, "the item registry changed, refresh the picker", null);
        }
        JsonObject currentRoot = AdminShopConfigWriter.readRoot(ShopDefinitionLoader.adminShopPath());
        String currentFingerprint = AdminBulkListingPlanner.catalogFingerprint(currentRoot);
        if (!currentFingerprint.equals(request.catalogFingerprint())
                || !currentFingerprint.equals(ticket.preview().catalogFingerprint())) {
            return new CommitResult(Status.STALE, "the catalog changed, refresh the preview", null);
        }
        AdminBulkListingPlanner.Preview currentPreview = replan(ticket.preview(), currentRoot, registry);
        if (currentPreview == null || !currentPreview.fingerprint().equals(ticket.preview().fingerprint())) {
            return new CommitResult(Status.STALE, "the catalog no longer matches the preview", null);
        }
        Set<String> replacements = normalizeIds(request.replaceListingIds());
        JsonObject candidate;
        try {
            candidate = AdminBulkListingPlanner.apply(currentPreview, replacements);
        } catch (RuntimeException exception) {
            return new CommitResult(Status.INVALID, "the bulk candidate is invalid", currentPreview);
        }
        if (!ShopDefinitionLoader.validCandidate(candidate.toString(), "admin.json")) {
            return new CommitResult(Status.INVALID, "the complete catalog candidate is invalid", currentPreview);
        }
        if (candidate.getAsJsonArray("items").size() > Config.adminShopMaximumListings) {
            return new CommitResult(Status.INVALID,
                    "the admin shop listing limit would be exceeded", currentPreview);
        }
        boolean changes = currentPreview.rows().stream().anyMatch(row ->
                row.action() == AdminBulkListingPlanner.Action.CREATE
                        || (row.replaceEligible() && replacements.contains(row.listingId().toLowerCase())));
        if (!changes) {
            Completed result = new Completed(currentPreview.fingerprint(), currentPreview);
            COMPLETED.put(request.requestId(), result);
            if (durable != null) {
                durable.record(request.requestId(), currentPreview);
            }
            trim(COMPLETED);
            TICKETS.remove(request.requestId());
            return new CommitResult(Status.NO_CHANGES, "all existing listings were skipped", currentPreview);
        }
        MinecraftServer server = player.getServer();
        if (server == null || !AdminShopConfigWriter.writeBulkCandidate(server, candidate)) {
            return new CommitResult(Status.IO_ERROR, "the catalog was not changed, check the server log", currentPreview);
        }
        ShopDataService.resendActiveSessions(server, false);
        Completed result = new Completed(currentPreview.fingerprint(), currentPreview);
        COMPLETED.put(request.requestId(), result);
        if (durable != null) {
            durable.record(request.requestId(), currentPreview);
        }
        trim(COMPLETED);
        TICKETS.remove(request.requestId());
        return new CommitResult(Status.COMMITTED, "bulk catalog update committed", currentPreview);
    }

    public static Set<String> registrySnapshot() {
        Set<String> ids = new LinkedHashSet<>();
        for (Item item : ForgeRegistries.ITEMS) {
            if (item == net.minecraft.world.item.Items.AIR) {
                continue;
            }
            var key = ForgeRegistries.ITEMS.getKey(item);
            if (key != null) {
                ids.add(key.toString());
            }
        }
        return ids;
    }

    private static AdminBulkListingPlanner.Preview replan(AdminBulkListingPlanner.Preview source,
                                                           JsonObject root, Set<String> registry) {
        List<AdminBulkListingPlanner.Selection> selections = source.rows().stream()
                .map(row -> new AdminBulkListingPlanner.Selection(row.itemId(), row.nbt(), row.displayName()))
                .toList();
        AdminBulkListingPlanner.Result result = AdminBulkListingPlanner.preview(
                source.requestId(), registry, root, selections, source.categoryId(),
                source.priceText(), source.stockText(), currencyDecimals(), maximumStock());
        return result.valid() ? result.preview() : null;
    }

    private static Set<String> normalizeIds(Set<String> values) {
        Set<String> result = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                result.add(value.trim().toLowerCase(Locale.ROOT));
            }
        }
        return result;
    }

    private static int currencyDecimals() {
        try {
            return BalanceManager.getProvider().getDecimalPlaces();
        } catch (IllegalStateException exception) {
            return 2;
        }
    }

    private static int maximumStock() {
        return Integer.MAX_VALUE;
    }

    private static <K, V> void trim(Map<K, V> values) {
        while (values.size() > AdminBulkListingService.MAX_REPLAY_ENTRIES) {
            values.remove(values.keySet().iterator().next());
        }
    }

    private record Ticket(UUID owner, PreviewRequest request, AdminBulkListingPlanner.Preview preview) {
    }

    private record Completed(String fingerprint, AdminBulkListingPlanner.Preview preview) {
    }
}
