package com.enviouse.futureshops.server.shop;

import com.enviouse.futureshops.block.ShopBlockEntity;
import com.enviouse.futureshops.data.BalanceTopEntry;
import com.enviouse.futureshops.data.OwnedShopSummary;
import com.enviouse.futureshops.data.TransactionHistoryEntry;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.S2CBalTopUiPacket;
import com.enviouse.futureshops.network.packets.S2CBalanceUiPacket;
import com.enviouse.futureshops.server.economy.BalanceEntry;
import com.enviouse.futureshops.server.economy.BalanceManager;
import com.enviouse.futureshops.server.economy.EconomyProvider;
import com.enviouse.futureshops.server.transaction.TransactionHistorySavedData;
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
        ShopPackets.sendToPlayer(player, new S2CBalanceUiPacket(
                player.getUUID(),
                player.getGameProfile().getName(),
                provider.getBalance(player.getUUID()),
                provider.getCurrencyName(),
                provider.getDecimalPlaces(),
                snapshot.totalRevenueMinor(),
                snapshot.totalPendingMinor(),
                snapshot.shopCount(),
                snapshot.listingCount(),
                snapshot.totalStock(),
                snapshot.lowSupplyCount(),
                snapshot.shopSummaries(),
                snapshot.alerts()));
    }

    public static void sendLeaderboard(ServerPlayer player, int page) {
        MinecraftServer server = player.server;
        EconomyProvider provider = BalanceManager.getProvider();
        int safePage = Math.max(1, page);
        List<BalanceTopEntry> topBalances = BalanceManager.getTopBalances(safePage, BALTOP_PAGE_SIZE).stream()
                .map(entry -> new BalanceTopEntry(entry.playerUUID(), resolvePlayerName(server, entry.playerUUID()), entry.balanceMinorUnits()))
                .toList();
        int totalPages = topBalances.isEmpty() && safePage > 1 ? safePage : Math.max(1, safePage + (topBalances.size() == BALTOP_PAGE_SIZE ? 1 : 0));

        PlayerMetric activityLeader = resolveActivityLeader(server);
        PlayerMetric sellerLeader = resolveTopSeller(server);
        ProductMetric productMetric = resolvePopularProduct(server);

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
                productMetric.totalQuantity()));
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

            int shopListingCount = shop.getListings().size();
            int shopTotalStock = 0;
            int shopLowCount = 0;
            String featuredItemId = shopListingCount > 0 ? shop.getListings().get(0).itemId() : "";
            for (ShopBlockEntity.Listing listing : shop.getListings()) {
                int stock = PlayerShopBlockService.countStock(level, shop, pos, listing);
                shopTotalStock += stock;
                if (stock <= LOW_STOCK_THRESHOLD) {
                    shopLowCount++;
                    alerts.add(displayItemName(listing.itemId()) + " low at " + displayDimension(ref.dimension()) + " " + formatPos(pos) + " (" + stock + ")");
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

    private static PlayerMetric resolveActivityLeader(MinecraftServer server) {
        Map<UUID, List<TransactionHistoryEntry>> entriesByPlayer = TransactionHistorySavedData.get(server).snapshotEntriesByPlayer();
        return entriesByPlayer.entrySet().stream()
                .map(entry -> new PlayerMetric(entry.getKey(), resolvePlayerName(server, entry.getKey()), entry.getValue().size()))
                .max(Comparator.comparingInt(PlayerMetric::value))
                .orElse(PlayerMetric.NONE);
    }

    private static PlayerMetric resolveTopSeller(MinecraftServer server) {
        return PlayerShopSettlementSavedData.get(server).snapshotSaleCountByOwner().entrySet().stream()
                .map(entry -> new PlayerMetric(entry.getKey(), resolvePlayerName(server, entry.getKey()), entry.getValue()))
                .max(Comparator.comparingInt(PlayerMetric::value))
                .orElse(PlayerMetric.NONE);
    }

    private static ProductMetric resolvePopularProduct(MinecraftServer server) {
        Map<String, ProductMetric> productTotals = new HashMap<>();
        TransactionHistorySavedData.get(server).snapshotEntriesByPlayer().values().forEach(entries -> entries.forEach(entry -> {
            if ((!"BUY".equalsIgnoreCase(entry.type()) && !"BARTER".equalsIgnoreCase(entry.type()))
                    || entry.itemId() == null || entry.itemId().isBlank() || "cart".equalsIgnoreCase(entry.itemId())) {
                return;
            }
            productTotals.compute(entry.itemId(), (itemId, current) -> current == null
                    ? new ProductMetric(itemId, 1, Math.max(1, entry.quantity()))
                    : new ProductMetric(itemId, current.tradeCount() + 1, current.totalQuantity() + Math.max(1, entry.quantity())));
        }));
        return productTotals.values().stream()
                .max(Comparator.comparingLong(ProductMetric::totalQuantity).thenComparingInt(ProductMetric::tradeCount))
                .orElse(ProductMetric.NONE);
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
        Item item = itemId == null || itemId.isBlank() ? Items.AIR : net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(itemId));
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
}

