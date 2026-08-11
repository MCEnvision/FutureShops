package com.enviouse.futureshops.server.transaction;

import com.enviouse.futureshops.catalog.offer.ServerShopBundleSavings;
import com.enviouse.futureshops.data.TransactionHistoryEntry;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.S2CHistoryResponsePacket;
import com.enviouse.futureshops.server.session.ShopSessionManager;
import com.enviouse.futureshops.server.shop.ShopDataService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Minimal transaction-history service used by buy/sell/barter and history fetch packet. */
public final class TransactionHistoryService {
    private static final Map<UUID, HistorySubscription> SUBSCRIPTIONS = new ConcurrentHashMap<>();

    private TransactionHistoryService() {
    }

    public static void record(ServerPlayer player, String shopId, String type, String itemId, int quantity, long totalMinorUnits, String note) {
        record(player, shopId, type, itemId, quantity, totalMinorUnits, note, "");
    }

    /** Additive overload: {@code nbtJson} carries the transacted listing's SNBT ("" = no NBT). */
    public static void record(ServerPlayer player, String shopId, String type, String itemId, int quantity, long totalMinorUnits, String note, String nbtJson) {
        if (player.getServer() == null) {
            return;
        }
        MinecraftServer server = player.getServer();
        TransactionHistorySavedData.get(server).append(
                player.getUUID(),
                new TransactionHistoryEntry(Instant.now().getEpochSecond(), type, itemId, quantity, totalMinorUnits, note,
                        nbtJson == null ? "" : nbtJson));
        pushLatestToSubscriber(server, player.getUUID(), ShopDataService.resolveShopId(shopId));
    }

    public static void recordPlayerPayment(
            MinecraftServer server,
            UUID payerId,
            UUID recipientId,
            UUID transactionId,
            long amountMinorUnits,
            Instant occurredAt
    ) {
        java.util.Objects.requireNonNull(server, "server");
        java.util.Objects.requireNonNull(payerId, "payerId");
        java.util.Objects.requireNonNull(recipientId, "recipientId");
        java.util.Objects.requireNonNull(transactionId, "transactionId");
        long occurredAtEpochSeconds = java.util.Objects.requireNonNull(
                occurredAt, "occurredAt").getEpochSecond();
        TransactionHistorySavedData data =
                TransactionHistorySavedData.get(server);
        String sentMarker = "player.payment.sent." + transactionId;
        String receivedMarker = "player.payment.received." + transactionId;
        if (data.appendIfAbsent(payerId, sentMarker,
                new TransactionHistoryEntry(
                        occurredAtEpochSeconds, "PAY_SENT",
                        "futureshops:wallet", 1,
                        amountMinorUnits,
                        "Paid " + recipientId, ""))) {
            pushLatestToSubscriber(server, payerId, "");
        }
        if (data.appendIfAbsent(recipientId, receivedMarker,
                new TransactionHistoryEntry(
                        occurredAtEpochSeconds, "PAY_RECEIVED",
                        "futureshops:wallet", 1,
                        amountMinorUnits,
                        "Received from " + payerId, ""))) {
            pushLatestToSubscriber(server, recipientId, "");
        }
    }

    public static void recordServerPurchase(
            MinecraftServer server,
            UUID playerId,
            String shopId,
            UUID transactionId,
            int lineIndex,
            String itemId,
            int quantity,
            long totalMinorUnits,
            String note,
            String nbtJson,
            Instant occurredAt
    ) {
        java.util.Objects.requireNonNull(server, "server");
        java.util.Objects.requireNonNull(playerId, "playerId");
        java.util.Objects.requireNonNull(transactionId, "transactionId");
        String marker = "server.shop.purchase." + transactionId + "."
                + lineIndex;
        if (TransactionHistorySavedData.get(server).appendIfAbsent(
                playerId, marker, new TransactionHistoryEntry(
                        java.util.Objects.requireNonNull(
                                occurredAt, "occurredAt").getEpochSecond(),
                        "BUY", itemId,
                        quantity, totalMinorUnits, note,
                        nbtJson == null ? "" : nbtJson))) {
            pushLatestToSubscriber(server, playerId,
                    ShopDataService.resolveShopId(shopId));
        }
    }

    public static void recordServerSell(
            MinecraftServer server,
            UUID playerId,
            String shopId,
            UUID transactionId,
            String itemId,
            int quantity,
            long totalMinorUnits,
            String nbtJson,
            Instant occurredAt
    ) {
        java.util.Objects.requireNonNull(server, "server");
        java.util.Objects.requireNonNull(playerId, "playerId");
        java.util.Objects.requireNonNull(transactionId, "transactionId");
        String marker = "server.shop.sell." + transactionId;
        if (TransactionHistorySavedData.get(server).appendIfAbsent(
                playerId, marker, new TransactionHistoryEntry(
                        java.util.Objects.requireNonNull(
                                occurredAt, "occurredAt").getEpochSecond(),
                        "SELL", itemId, quantity, totalMinorUnits,
                        "DETAIL", nbtJson == null ? "" : nbtJson))) {
            pushLatestToSubscriber(server, playerId,
                    ShopDataService.resolveShopId(shopId));
        }
    }

    public static void recordServerBarter(
            MinecraftServer server,
            UUID playerId,
            String shopId,
            UUID transactionId,
            String itemId,
            int quantity,
            String note,
            String nbtJson,
            Instant occurredAt
    ) {
        java.util.Objects.requireNonNull(server, "server");
        java.util.Objects.requireNonNull(playerId, "playerId");
        java.util.Objects.requireNonNull(transactionId, "transactionId");
        String marker = "server.shop.barter." + transactionId;
        if (TransactionHistorySavedData.get(server).appendIfAbsent(
                playerId, marker, new TransactionHistoryEntry(
                        java.util.Objects.requireNonNull(
                                occurredAt, "occurredAt").getEpochSecond(),
                        "BARTER", itemId, quantity, 0L,
                        note == null ? "" : note,
                        nbtJson == null ? "" : nbtJson))) {
            pushLatestToSubscriber(server, playerId,
                    ShopDataService.resolveShopId(shopId));
        }
    }

    public static void recordServerOffer(
            MinecraftServer server,
            UUID playerId,
            String shopId,
            UUID transactionId,
            String listingId,
            String type,
            String itemId,
            int quantity,
            long totalMinorUnits,
            String optionId,
            String nbtJson,
            Instant occurredAt
    ) {
        recordServerOfferComponents(server, playerId, shopId,
                transactionId, listingId, type, quantity,
                totalMinorUnits, optionId, List.of(
                        new ServerOfferComponent(
                                ComponentRole.OUTPUT, "primary",
                                itemId, 1,
                                nbtJson == null ? "" : nbtJson)),
                Optional.empty(), occurredAt);
    }

    public static void recordServerOfferComponents(
            MinecraftServer server,
            UUID playerId,
            String shopId,
            UUID transactionId,
            String listingId,
            String type,
            int offerQuantity,
            long totalMinorUnits,
            String optionId,
            List<ServerOfferComponent> components,
            Optional<ServerShopBundleSavings.Snapshot> savings,
            Instant occurredAt
    ) {
        java.util.Objects.requireNonNull(server, "server");
        java.util.Objects.requireNonNull(playerId, "playerId");
        java.util.Objects.requireNonNull(transactionId, "transactionId");
        java.util.Objects.requireNonNull(savings, "savings");
        List<ServerOfferComponent> exactComponents = List.copyOf(
                java.util.Objects.requireNonNull(
                        components, "components"));
        if (offerQuantity <= 0 || exactComponents.isEmpty()
                || exactComponents.size() > 72) {
            throw new IllegalArgumentException(
                    "Server offer history components are invalid");
        }
        long epoch = java.util.Objects.requireNonNull(
                occurredAt, "occurredAt").getEpochSecond();
        boolean appended = false;
        for (int index = 0; index < exactComponents.size(); index++) {
            ServerOfferComponent component = exactComponents.get(index);
            int exactQuantity = Math.multiplyExact(
                    component.unitsPerOffer(), offerQuantity);
            String marker = "server.shop.offer." + transactionId + "."
                    + listingId + "." + optionId + "."
                    + component.role().name().toLowerCase(
                    java.util.Locale.ROOT) + "."
                    + component.componentId();
            String note = historyNote(
                    optionId, component, index,
                    exactComponents.size(),
                    index == 0 ? savings : Optional.empty());
            appended |= TransactionHistorySavedData.get(server)
                    .appendIfAbsent(
                            playerId, marker,
                            new TransactionHistoryEntry(
                                    epoch, type, component.itemId(),
                                    exactQuantity,
                                    index == 0
                                            ? totalMinorUnits : 0L,
                                    note, component.exactNbt()));
        }
        if (appended) {
            pushLatestToSubscriber(server, playerId,
                    ShopDataService.resolveShopId(shopId));
        }
    }

    private static String historyNote(
            String optionId,
            ServerOfferComponent component,
            int index,
            int count,
            Optional<ServerShopBundleSavings.Snapshot> savings
    ) {
        StringBuilder note = new StringBuilder("option ")
                .append(optionId).append(", ")
                .append(component.role().name().toLowerCase(
                        java.util.Locale.ROOT))
                .append(" component ")
                .append(component.componentId())
                .append(", bundle line ")
                .append(index + 1).append(" of ").append(count);
        savings.ifPresent(snapshot -> {
            note.append(", individual total ")
                    .append(snapshot.individualTotalMinorUnits())
                    .append(", bundle total ")
                    .append(snapshot.bundleTotalMinorUnits())
                    .append(", savings ")
                    .append(snapshot.savingsMinorUnits())
                    .append(", savings basis points ")
                    .append(snapshot.savingsBasisPoints());
            for (ServerShopBundleSavings.ComparisonRevision comparison
                    : snapshot.comparisonRevisions()) {
                note.append(", comparison ")
                        .append(comparison.componentId()).append(" ")
                        .append(comparison.listingId()).append(" ")
                        .append(comparison.optionId()).append(" revision ")
                        .append(comparison.revision());
            }
        });
        return note.toString();
    }

    public record ServerOfferComponent(
            ComponentRole role,
            String componentId,
            String itemId,
            int unitsPerOffer,
            String exactNbt
    ) {
        public ServerOfferComponent {
            role = java.util.Objects.requireNonNull(role, "role");
            componentId = required(componentId, "componentId");
            itemId = required(itemId, "itemId");
            exactNbt = java.util.Objects.requireNonNull(
                    exactNbt, "exactNbt");
            if (unitsPerOffer <= 0) {
                throw new IllegalArgumentException(
                        "Server offer history quantity is invalid");
            }
        }

        private static String required(String value, String field) {
            String exact = java.util.Objects.requireNonNull(
                    value, field).strip();
            if (exact.isEmpty() || exact.length() > 160) {
                throw new IllegalArgumentException(
                        "Server offer history identifier is invalid");
            }
            return exact;
        }
    }

    public enum ComponentRole {
        OUTPUT,
        INPUT
    }

    public static void sendHistoryPage(ServerPlayer player, String requestedShopId, int page, int pageSize,
                                       TransactionHistoryEntry.HistoryFilter filter,
                                       String searchText,
                                       TransactionHistoryEntry.SortOrder sortOrder,
                                       TransactionHistoryEntry.TimeWindow timeWindow) {
        if (player.getServer() == null) {
            return;
        }

        String shopId = ShopDataService.resolveShopId(requestedShopId);
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, pageSize);
        TransactionHistoryEntry.HistoryFilter safeFilter = filter == null
                ? TransactionHistoryEntry.HistoryFilter.ALL
                : filter;
        String safeSearch = searchText == null ? "" : searchText.trim();
        TransactionHistoryEntry.SortOrder safeSortOrder = sortOrder == null ? TransactionHistoryEntry.SortOrder.NEWEST : sortOrder;
        TransactionHistoryEntry.TimeWindow safeTimeWindow = timeWindow == null ? TransactionHistoryEntry.TimeWindow.ALL : timeWindow;
        SUBSCRIPTIONS.put(player.getUUID(), new HistorySubscription(shopId, safePage, safePageSize, safeFilter, safeSearch, safeSortOrder, safeTimeWindow));

        TransactionHistorySavedData data = TransactionHistorySavedData.get(player.getServer());
        int totalPages = data.getTotalPages(player.getUUID(), safePageSize, safeFilter, safeSearch, safeSortOrder, safeTimeWindow);
        int responsePage = Math.min(safePage, totalPages);
        List<TransactionHistoryEntry> entries = data.getPage(player.getUUID(), responsePage, safePageSize, safeFilter, safeSearch, safeSortOrder, safeTimeWindow);

        ShopPackets.sendToPlayer(player, new S2CHistoryResponsePacket(shopId, responsePage, totalPages, safeFilter, entries));
    }

    public static void clearSubscription(UUID playerUUID) {
        SUBSCRIPTIONS.remove(playerUUID);
    }

    private static void pushLatestToSubscriber(MinecraftServer server, UUID playerUUID, String transactionShopId) {
        HistorySubscription subscription = SUBSCRIPTIONS.get(playerUUID);
        if (subscription == null || !subscription.shopId().equals(transactionShopId)) {
            return;
        }

        ServerPlayer subscriber = server.getPlayerList().getPlayer(playerUUID);
        if (subscriber == null) {
            SUBSCRIPTIONS.remove(playerUUID);
            return;
        }

        String activeShopId = ShopSessionManager.get(playerUUID).map(session -> session.shopId()).orElse("");
        if (!subscription.shopId().equals(activeShopId)) {
            SUBSCRIPTIONS.remove(playerUUID);
            return;
        }

        TransactionHistorySavedData data = TransactionHistorySavedData.get(server);
        int totalPages = data.getTotalPages(
                playerUUID,
                subscription.pageSize(),
                subscription.filter(),
                subscription.searchText(),
                subscription.sortOrder(),
                subscription.timeWindow());
        int responsePage = Math.min(subscription.page(), totalPages);
        List<TransactionHistoryEntry> entries = data.getPage(
                playerUUID,
                responsePage,
                subscription.pageSize(),
                subscription.filter(),
                subscription.searchText(),
                subscription.sortOrder(),
                subscription.timeWindow());
        ShopPackets.sendToPlayer(subscriber,
                new S2CHistoryResponsePacket(subscription.shopId(), responsePage, totalPages, subscription.filter(), entries));
    }

    private record HistorySubscription(
            String shopId,
            int page,
            int pageSize,
            TransactionHistoryEntry.HistoryFilter filter,
            String searchText,
            TransactionHistoryEntry.SortOrder sortOrder,
            TransactionHistoryEntry.TimeWindow timeWindow) {
    }
}
