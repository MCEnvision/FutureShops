package com.enviouse.futureshops.server.shop;

import com.enviouse.futureshops.block.ShopBlockEntity;
import com.enviouse.futureshops.catalog.ItemDef;
import com.enviouse.futureshops.catalog.ShopCatalog;
import com.enviouse.futureshops.catalog.ShopDefinition;
import com.enviouse.futureshops.data.BalanceTopEntry;
import com.enviouse.futureshops.data.FranchiseLeaderboardEntry;
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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;
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

    /**
     * Sends the target player's dashboard data to the viewer (admin).
     * This lets admins inspect another player's marketplace profile.
     */
    public static void sendDashboardForViewer(ServerPlayer viewer, ServerPlayer target) {
        EconomyProvider provider = BalanceManager.getProvider();
        DashboardSnapshot snapshot = snapshotDashboard(target);
        ShopPackets.sendToPlayer(viewer, new S2CBalanceUiPacket(
                target.getUUID(),
                target.getGameProfile().getName(),
                provider.getBalance(target.getUUID()),
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
                resolvePopularItemNbt(productMetric.itemId())));
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
            CompoundTag featuredTag = shopListingCount > 0 ? shop.getListings().get(0).nbtTag() : null;
            String featuredNbtJson = featuredTag != null ? featuredTag.toString() : "";
            for (ShopBlockEntity.Listing listing : shop.getListings()) {
                int stock = PlayerShopBlockService.countStock(level, shop, pos, listing);
                shopTotalStock += stock;
                if (stock <= LOW_STOCK_THRESHOLD) {
                    shopLowCount++;
                    alerts.add(displayItemName(listing.itemId(), listing.nbtTag()) + " low at " + shopNameOrBlank
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
                    settlement.lifetimeMinor(),
                    featuredNbtJson));
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

    /**
     * Best-effort NBT resolution for the popular-product spotlight: history entries carry only
     * the registry itemId, so look the item up in the admin catalog. Returns the listing's
     * nbtJson only when it is unambiguous — i.e. every catalog listing minting this registry id
     * agrees on the same tag. Ambiguous (multiple NBT variants) or absent → "" (bare icon).
     */
    private static String resolvePopularItemNbt(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return "";
        }
        String resolved = null;
        for (ShopDefinition definition : ShopCatalog.getAllDefinitions()) {
            for (ItemDef item : definition.items()) {
                if (!itemId.equals(item.itemId())) {
                    continue;
                }
                String nbt = item.nbtJson() == null ? "" : item.nbtJson();
                if (resolved == null) {
                    resolved = nbt;
                } else if (!resolved.equals(nbt)) {
                    return ""; // ambiguous: multiple variants share this registry id
                }
            }
        }
        return resolved == null ? "" : resolved;
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

    private static String displayItemName(String itemId, @Nullable CompoundTag nbtTag) {
        Item item = itemId == null || itemId.isBlank() ? Items.AIR : net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(itemId));
        if (item == null || item == Items.AIR) {
            return itemId;
        }
        // Resolve the name from a tag-stamped stack, not the item's raw description
        // key — tag-dependent items (TacZ guns) take their name from the stack NBT
        // (mirrors LocalShopAggregator's server-side fallback).
        ItemStack nameStack = new ItemStack(item);
        if (nbtTag != null) {
            nameStack.setTag(nbtTag.copy());
        }
        return nameStack.getHoverName().getString();
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

