package com.enviouse.futureshopsp.server.transaction;

import com.enviouse.futureshopsp.data.TransactionHistoryEntry;
import com.enviouse.futureshopsp.network.ShopPackets;
import com.enviouse.futureshopsp.network.packets.S2CHistoryResponsePacket;
import com.enviouse.futureshopsp.server.session.ShopSessionManager;
import com.enviouse.futureshopsp.server.shop.ShopDataService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Minimal transaction-history service used by buy/sell/barter and history fetch packet. */
public final class TransactionHistoryService {
    private static final Map<UUID, HistorySubscription> SUBSCRIPTIONS = new ConcurrentHashMap<>();

    private TransactionHistoryService() {
    }

    public static void record(ServerPlayer player, String shopId, String type, String itemId, int quantity, long totalMinorUnits, String note) {
        if (player.getServer() == null) {
            return;
        }
        MinecraftServer server = player.getServer();
        TransactionHistorySavedData.get(server).append(
                player.getUUID(),
                new TransactionHistoryEntry(Instant.now().getEpochSecond(), type, itemId, quantity, totalMinorUnits, note));
        pushLatestToSubscriber(server, player.getUUID(), ShopDataService.resolveShopId(shopId));
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

