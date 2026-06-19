package com.enviouse.futureshopsp.server.shop;

import com.enviouse.futureshopsp.Config;
import com.enviouse.futureshopsp.block.ShopBlockEntity;
import com.enviouse.futureshopsp.data.LocalShopOwnerEntry;
import com.enviouse.futureshopsp.data.LocalShopOwnerEntry.LocalDepartment;
import com.enviouse.futureshopsp.data.LocalShopOwnerEntry.LocalListing;
import com.enviouse.futureshopsp.network.ShopPackets;
import com.enviouse.futureshopsp.network.packets.S2CLocalShopsPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Server-side aggregator that collects all nearby player shops, groups them by owner
 * (or franchise), builds departments and listings, and sends the result to the client.
 */
public final class LocalShopAggregator {
    private static final int MAX_OWNERS = 30;

    private LocalShopAggregator() {}

    /**
     * Scans nearby shops, aggregates by owner/franchise, and sends an S2CLocalShopsPacket.
     * If ownerFilter is non-empty, only that owner's data is included (detail view).
     */
    public static void sendLocalShops(ServerPlayer player, String ownerFilter) {
        Level level = player.level();
        BlockPos center = player.blockPosition();
        String currentDimension = level.dimension().location().toString();

        PlayerShopRegistrySavedData registry = PlayerShopRegistrySavedData.get(player.getServer());
        FranchiseSavedData franchiseData = FranchiseSavedData.get(player.getServer());

        Map<Long, PlayerShopRegistrySavedData.ShopRecord> allShops = registry.getAllShops();
        int scanRadius = Config.localListingsScanRadiusBlocks;
        boolean unlimited = scanRadius <= 0;
        long scanRadiusSq = (long) scanRadius * scanRadius;

        // Group nearby shops by effective owner (franchise leader or individual owner)
        // Key = display UUID (owner or franchise leader), Value = list of (pos, shop BE)
        Map<UUID, List<ShopInfo>> shopsByGroup = new LinkedHashMap<>();
        Map<UUID, Double> closestDistance = new HashMap<>();
        Map<UUID, String> groupDisplayName = new HashMap<>();
        Map<UUID, String> groupFranchiseName = new HashMap<>();

        for (Map.Entry<Long, PlayerShopRegistrySavedData.ShopRecord> entry : allShops.entrySet()) {
            PlayerShopRegistrySavedData.ShopRecord record = entry.getValue();
            if (!currentDimension.equals(record.dimension())) continue;

            BlockPos shopPos = BlockPos.of(entry.getKey());
            double distSq = center.distSqr(shopPos);
            if (!unlimited && distSq > (double) scanRadiusSq) continue;
            if (!level.hasChunkAt(shopPos)) continue;

            BlockEntity be = level.getBlockEntity(shopPos);
            if (!(be instanceof ShopBlockEntity shop)) continue;
            if (shop.getOwnerUuid() == null || shop.getListings().isEmpty()) continue;

            UUID ownerUuid = shop.getOwnerUuid();

            // Determine group key: use franchise leader UUID if in franchise, else owner UUID
            UUID groupKey = ownerUuid;
            FranchiseSavedData.Franchise franchise = franchiseData.getFranchise(ownerUuid);
            if (franchise != null) {
                groupKey = franchise.leader;
                groupFranchiseName.put(groupKey, franchise.name);
            }

            // If filtering by a specific owner, skip others
            if (!ownerFilter.isBlank()) {
                if (!groupKey.toString().equals(ownerFilter) && !ownerUuid.toString().equals(ownerFilter)) {
                    continue;
                }
            }

            double dist = Math.sqrt(distSq);
            closestDistance.merge(groupKey, dist, Math::min);
            shopsByGroup.computeIfAbsent(groupKey, k -> new ArrayList<>())
                    .add(new ShopInfo(shopPos, shop, dist));

            if (!groupDisplayName.containsKey(groupKey)) {
                groupDisplayName.put(groupKey, resolveOwnerName(player, ownerUuid));
            }
        }

        // Build aggregated entries
        List<LocalShopOwnerEntry> owners = new ArrayList<>();
        for (Map.Entry<UUID, List<ShopInfo>> groupEntry : shopsByGroup.entrySet()) {
            if (owners.size() >= MAX_OWNERS) break;
            UUID groupKey = groupEntry.getKey();
            List<ShopInfo> shops = groupEntry.getValue();

            // Aggregate departments across all shop blocks
            Map<String, List<LocalListing>> deptMap = new LinkedHashMap<>();
            int totalListings = 0;
            int totalStock = 0;

            for (ShopInfo info : shops) {
                ShopBlockEntity shop = info.shop;
                List<ShopBlockEntity.Listing> listings = shop.getListings();
                for (int i = 0; i < listings.size(); i++) {
                    ShopBlockEntity.Listing listing = listings.get(i);
                    if (listing.itemId().isBlank()) continue;

                    String dept = listing.department().isBlank() ? "General" : listing.department();
                    int stock = PlayerShopBlockService.countStock(level, shop, info.pos, listing);

                    Item item = BuiltInRegistries.ITEM.getOptional(
                            net.minecraft.resources.ResourceLocation.tryParse(listing.itemId())).orElse(null);
                    String displayName = item != null && item != Items.AIR
                            ? item.getDescription().getString() : listing.itemId();

                    long price = listing.moneyPriceMinor();
                    boolean hasPromo = listing.promo().active();
                    long promoPrice = hasPromo ? listing.promo().applyUnitPrice(price) : price;

                    String nbtJson = "";
                    

                    LocalListing ll = new LocalListing(
                            listing.itemId(), displayName, stock, price,
                            listing.tradeMode().name(),
                            listing.barterItemId(), listing.barterItemCount(),
                            listing.nbtAware(), nbtJson,
                            shop.getShopName().isBlank()
                                    ? groupDisplayName.getOrDefault(groupKey, "Shop") + "'s Shop"
                                    : shop.getShopName(),
                            info.pos.asLong(), i, listing.baseQuantity(),
                            hasPromo, promoPrice);

                    deptMap.computeIfAbsent(dept, k -> new ArrayList<>()).add(ll);
                    totalListings++;
                    totalStock += stock;
                }
            }

            List<LocalDepartment> departments = deptMap.entrySet().stream()
                    .map(e -> new LocalDepartment(e.getKey(), List.copyOf(e.getValue())))
                    .collect(Collectors.toList());

            owners.add(new LocalShopOwnerEntry(
                    groupKey,
                    groupDisplayName.getOrDefault(groupKey, "Unknown"),
                    groupFranchiseName.getOrDefault(groupKey, ""),
                    shops.size(),
                    totalListings,
                    totalStock,
                    closestDistance.getOrDefault(groupKey, 0.0),
                    departments));
        }

        // Sort by distance
        owners.sort(Comparator.comparingDouble(LocalShopOwnerEntry::closestDistance));

        ShopPackets.sendToPlayer(player, new S2CLocalShopsPacket(owners));
    }

    private static String resolveOwnerName(ServerPlayer viewer, UUID ownerUuid) {
        var profile = viewer.getServer().getProfileCache();
        if (profile != null) {
            var opt = profile.get(ownerUuid);
            if (opt.isPresent()) return opt.get().getName();
        }
        return "Player";
    }

    private record ShopInfo(BlockPos pos, ShopBlockEntity shop, double distance) {}
}



