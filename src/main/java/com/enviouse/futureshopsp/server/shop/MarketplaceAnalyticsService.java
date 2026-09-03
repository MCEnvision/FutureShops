package com.enviouse.futureshopsp.server.shop;

import com.enviouse.futureshopsp.block.ShopBlockEntity;
import com.enviouse.futureshopsp.data.BalanceTopEntry;
import com.enviouse.futureshopsp.data.FranchiseLeaderboardEntry;
import com.enviouse.futureshopsp.data.OwnedShopSummary;
import com.enviouse.futureshopsp.data.TransactionHistoryEntry;
import com.enviouse.futureshopsp.network.ShopPackets;
import com.enviouse.futureshopsp.network.packets.S2CBalTopUiPacket;
import com.enviouse.futureshopsp.network.packets.S2CBalanceUiPacket;
import com.enviouse.futureshopsp.server.economy.BalanceEntry;
import com.enviouse.futureshopsp.server.economy.BalanceManager;
import com.enviouse.futureshopsp.server.economy.EconomyProvider;
import com.enviouse.futureshopsp.api.economy.BalanceSnapshot;
import com.enviouse.futureshopsp.api.economy.ProviderResult;
import com.enviouse.futureshopsp.server.transaction.TransactionHistorySavedData;
import com.enviouse.futureshopsp.server.util.PageBounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MarketplaceAnalyticsService {
    private static final int LOW_STOCK_THRESHOLD = 16;
    private static final int BALTOP_PAGE_SIZE = 10;

    private MarketplaceAnalyticsService() {
    }

    public static void sendDashboard(ServerPlayer player) {
        EconomyProvider provider = BalanceManager.getProvider();
        DashboardSnapshot snapshot = snapshotDashboard(player);
        ProviderResult<BalanceSnapshot> balance = BalanceManager.queryBalance(player.getUUID());
        var lifecycle = BalanceManager.getLifecycleSnapshotOrUnresolved();
        ShopPackets.sendToPlayer(player, new S2CBalanceUiPacket(
                player.getUUID(),
                player.getGameProfile().getName(),
                balance.value().map(BalanceSnapshot::balanceMinorUnits).orElse(0L),
                provider.getCurrencyName(),
                provider.getDecimalPlaces(),
                snapshot.totalRevenueMinor(),
                snapshot.totalPendingMinor(),
                snapshot.shopCount(),
                snapshot.listingCount(),
                snapshot.totalStock(),
                snapshot.lowSupplyCount(),
                snapshot.shopSummaries(),
                snapshot.alerts(),
                balance.confirmed(),
                lifecycle.providerId(),
                lifecycle.lifecycle().name(),
                lifecycle.diagnostic()));
    }

    /**
     * Sends the target player's dashboard data to the viewer (admin).
     * This lets admins inspect another player's marketplace profile.
     */
    public static void sendDashboardForViewer(ServerPlayer viewer, ServerPlayer target) {
        EconomyProvider provider = BalanceManager.getProvider();
        DashboardSnapshot snapshot = snapshotDashboard(target);
        ProviderResult<BalanceSnapshot> balance = BalanceManager.queryBalance(target.getUUID());
        var lifecycle = BalanceManager.getLifecycleSnapshotOrUnresolved();
        ShopPackets.sendToPlayer(viewer, new S2CBalanceUiPacket(
                target.getUUID(),
                target.getGameProfile().getName(),
                balance.value().map(BalanceSnapshot::balanceMinorUnits).orElse(0L),
                provider.getCurrencyName(),
                provider.getDecimalPlaces(),
                snapshot.totalRevenueMinor(),
                snapshot.totalPendingMinor(),
                snapshot.shopCount(),
                snapshot.listingCount(),
                snapshot.totalStock(),
                snapshot.lowSupplyCount(),
                snapshot.shopSummaries(),
                snapshot.alerts(),
                balance.confirmed(),
                lifecycle.providerId(),
                lifecycle.lifecycle().name(),
                lifecycle.diagnostic()));
    }

    public static void sendLeaderboard(ServerPlayer player, int page) {
        MinecraftServer server = player.server;
        EconomyProvider provider = BalanceManager.getProvider();
        var lifecycle = BalanceManager.getLifecycleSnapshotOrUnresolved();
        boolean rankingAvailable = BalanceManager.isInternalEconomyReady();
        if (page < 1 || page > PageBounds.MAX_PAGE_INDEX) {
            return;
        }
        int safePage = page;
        List<BalanceTopEntry> topBalances = (rankingAvailable ? BalanceManager.getTopBalances(safePage, BALTOP_PAGE_SIZE) : List.<BalanceEntry>of()).stream()
                .map(entry -> new BalanceTopEntry(entry.playerUUID(), resolvePlayerName(server, entry.playerUUID()), entry.balanceMinorUnits()))
                .toList();
        int totalPages = topBalances.isEmpty() && safePage > 1 ? safePage : Math.max(1, safePage + (topBalances.size() == BALTOP_PAGE_SIZE ? 1 : 0));

        // Single pass over transaction history computes both the activity leader and the popular-product metric.
        TransactionMetrics txMetrics = resolveTransactionMetrics(server);
        PlayerMetric activityLeader = txMetrics.activityLeader();
        ProductMetric productMetric = txMetrics.productMetric();
        PlayerMetric sellerLeader = resolveTopSeller(server);

        List<FranchiseLeaderboardEntry> franchises = FranchiseSavedData.get(server).getTopFranchises(10).stream()
                .map(f -> new FranchiseLeaderboardEntry(f.franchiseId(), f.name(), resolvePlayerName(server, f.leader()), f.memberCount()))
                .toList();

        ShopPackets.sendToPlayer(player, new S2CBalTopUiPacket(
                safePage,
                totalPages,
                topBalances,
                provider.getCurrencyName(),
                provider.getDecimalPlaces(),
                activityLeader.uuid(),
                activityLeader.name(),
                activityLeader.value(),
                sellerLeader.uuid(),
                sellerLeader.name(),
                sellerLeader.value(),
                productMetric.itemId(),
                productMetric.tradeCount(),
                productMetric.totalQuantity(),
                franchises,
                rankingAvailable,
                lifecycle.providerId(),
                lifecycle.lifecycle().name(),
                lifecycle.diagnostic()));
    }

    public static DashboardSnapshot snapshotDashboard(ServerPlayer player) {
        MinecraftServer server = player.server;
        PlayerShopRegistrySavedData registry = PlayerShopRegistrySavedData.get(server);
        PlayerShopSettlementSavedData settlementData = PlayerShopSettlementSavedData.get(server);
        List<OwnedShopSummary> summaries = new ArrayList<>();
        List<String> alerts = new ArrayList<>();
        long revenue = 0L;
        long pending = 0L;
        int listingCount = 0;
        int totalStock = 0;
        int lowSupplyCount = 0;

        for (PlayerShopRegistrySavedData.ShopRef ref : registry.getOwnedShops(player.getUUID())) {
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, ref.dimension()));
            if (level == null) {
                continue;
            }
            BlockPos pos = BlockPos.of(ref.posLong());
            if (!(level.getBlockEntity(pos) instanceof ShopBlockEntity shop)) {
                continue;
            }
            if (shop.getOwnerUuid() == null || !shop.getOwnerUuid().equals(player.getUUID())) {
                continue;
            }

            String shopLabel = shop.getShopName() == null || shop.getShopName().isBlank()
                    ? "Storage"
                    : shop.getShopName();
            String shopNameOrBlank = shop.getShopName() == null || shop.getShopName().isBlank()
                    ? ""
                    : shop.getShopName() + " ";

            if (shop.getLinkedStoragePos() == null) {
                alerts.add(shopLabel + " Unlinked at " + displayDimension(ref.dimension()) + " " + formatPos(pos));
            }

            int shopListingCount = shop.getListings().size();
            int shopTotalStock = 0;
            int shopLowCount = 0;
            String featuredItemId = shopListingCount > 0 ? shop.getListings().get(0).itemId() : "";
            for (ShopBlockEntity.Listing listing : shop.getListings()) {
                int stock = PlayerShopBlockService.countStock(level, shop, pos, listing);
                shopTotalStock += stock;
                if (stock <= LOW_STOCK_THRESHOLD) {
                    shopLowCount++;
                    alerts.add(displayItemName(listing.itemId()) + " low at " + shopNameOrBlank
                            + displayDimension(ref.dimension()) + " " + formatPos(pos) + " (" + stock + ")");
                }
            }

            PlayerShopSettlementSavedData.Snapshot settlement = settlementData.snapshot(player.getUUID(), pos.asLong(), 1);
            summaries.add(new OwnedShopSummary(
                    ref.dimension().toString(),
                    pos.asLong(),
                    featuredItemId,
                    shopListingCount,
                    shopTotalStock,
                    shopLowCount,
                    shop.getLinkedStoragePos() != null,
                    settlement.pendingMinor(),
                    settlement.lifetimeMinor()));
            revenue += settlement.lifetimeMinor();
            pending += settlement.pendingMinor();
            listingCount += shopListingCount;
            totalStock += shopTotalStock;
            lowSupplyCount += shopLowCount;
        }

        summaries.sort(Comparator.comparingLong(OwnedShopSummary::lifetimeMinor).reversed());
        alerts.sort(String::compareToIgnoreCase);
        return new DashboardSnapshot(summaries, alerts, revenue, pending, summaries.size(), listingCount, totalStock, lowSupplyCount);
    }

    /**
     * Walks {@link TransactionHistorySavedData} exactly once and returns both the activity-leader
     * {@link PlayerMetric} and the popular-product {@link ProductMetric}.
     */
    private static TransactionMetrics resolveTransactionMetrics(MinecraftServer server) {
        Map<UUID, List<TransactionHistoryEntry>> entriesByPlayer = TransactionHistorySavedData.get(server).snapshotEntriesByPlayer();

        UUID topPlayer = null;
        int topCount = -1;
        String topItemId = null;
        int topItemTradeCount = 0;
        long topItemTotalQty = 0L;
        Map<String, int[]> productTotals = new HashMap<>(); // itemId → [tradeCount, totalQuantity]

        for (Map.Entry<UUID, List<TransactionHistoryEntry>> playerEntry : entriesByPlayer.entrySet()) {
            List<TransactionHistoryEntry> entries = playerEntry.getValue();
            int size = entries.size();
            if (size > topCount) {
                topCount = size;
                topPlayer = playerEntry.getKey();
            }
            for (TransactionHistoryEntry entry : entries) {
                if ((!"BUY".equalsIgnoreCase(entry.type()) && !"BARTER".equalsIgnoreCase(entry.type()))
                        || entry.itemId() == null || entry.itemId().isBlank()
                        || "cart".equalsIgnoreCase(entry.itemId())) {
                    continue;
                }
                int[] totals = productTotals.computeIfAbsent(entry.itemId(), ignored -> new int[]{0, 0});
                totals[0]++;
                totals[1] += Math.max(1, entry.quantity());
                if (totals[1] > topItemTotalQty
                        || (totals[1] == topItemTotalQty && totals[0] > topItemTradeCount)) {
                    topItemId = entry.itemId();
                    topItemTradeCount = totals[0];
                    topItemTotalQty = totals[1];
                }
            }
        }

        PlayerMetric activity = topPlayer == null
                ? PlayerMetric.NONE
                : new PlayerMetric(topPlayer, resolvePlayerName(server, topPlayer), topCount);
        ProductMetric product = topItemId == null
                ? ProductMetric.NONE
                : new ProductMetric(topItemId, topItemTradeCount, topItemTotalQty);
        return new TransactionMetrics(activity, product);
    }

    private static PlayerMetric resolveTopSeller(MinecraftServer server) {
        return PlayerShopSettlementSavedData.get(server).snapshotSaleCountByOwner().entrySet().stream()
                .map(entry -> new PlayerMetric(entry.getKey(), resolvePlayerName(server, entry.getKey()), entry.getValue()))
                .max(Comparator.comparingInt(PlayerMetric::value))
                .orElse(PlayerMetric.NONE);
    }

    private static String resolvePlayerName(MinecraftServer server, UUID playerUUID) {
        ServerPlayer online = server.getPlayerList().getPlayer(playerUUID);
        if (online != null) {
            return online.getGameProfile().getName();
        }
        return server.getProfileCache().get(playerUUID)
                .map(profile -> profile.getName())
                .orElse(playerUUID.toString().substring(0, 8));
    }

    private static String displayDimension(ResourceLocation dimension) {
        String path = dimension.getPath().replace('_', ' ');
        return Character.toUpperCase(path.charAt(0)) + path.substring(1);
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    private static String displayItemName(String itemId) {
        Item item = itemId == null || itemId.isBlank() ? Items.AIR : net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(itemId)).orElse(null);
        return item == null || item == Items.AIR ? itemId : item.getDescription().getString();
    }

    public record DashboardSnapshot(
            List<OwnedShopSummary> shopSummaries,
            List<String> alerts,
            long totalRevenueMinor,
            long totalPendingMinor,
            int shopCount,
            int listingCount,
            int totalStock,
            int lowSupplyCount) {
    }

    private record PlayerMetric(UUID uuid, String name, int value) {
        private static final PlayerMetric NONE = new PlayerMetric(new UUID(0L, 0L), "Nobody", 0);
    }

    private record ProductMetric(String itemId, int tradeCount, long totalQuantity) {
        private static final ProductMetric NONE = new ProductMetric("", 0, 0L);
    }

    private record TransactionMetrics(PlayerMetric activityLeader, ProductMetric productMetric) {
    }
}
