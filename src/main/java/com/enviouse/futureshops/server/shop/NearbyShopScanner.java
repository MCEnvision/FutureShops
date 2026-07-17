package com.enviouse.futureshops.server.shop;

import com.enviouse.futureshops.block.ShopBlockEntity;
import com.enviouse.futureshops.data.NearbyShopEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Scans for player-owned shop blocks within a radius of a given player.
 * Uses the PlayerShopRegistrySavedData for efficient lookup rather than
 * iterating all block entities in range.
 */
public final class NearbyShopScanner {
    /** Maximum number of nearby shops to return. */
    public static final int MAX_RESULTS = 20;

    private NearbyShopScanner() {
    }

    /**
     * Scans for player-owned shop blocks within {@code radius} blocks of the player.
     *
     * @param player the scanning player
     * @param radius the search radius in blocks; {@code 0} or negative means unlimited (every shop in
     *               the current dimension whose chunk is currently loaded is considered)
     * @return a sorted list of nearby shop entries (closest first)
     */
    public static List<NearbyShopEntry> scanNearby(ServerPlayer player, int radius) {
        boolean unlimited = radius <= 0;
        long radiusSq = (long) radius * radius;
        Level level = player.level();
        BlockPos center = player.blockPosition();
        List<NearbyShopEntry> results = new ArrayList<>();

        // Use registry for fast lookup — it tracks all placed shops
        if (player.getServer() == null) {
            return results;
        }

        PlayerShopRegistrySavedData registry = PlayerShopRegistrySavedData.get(player.getServer());
        Map<PlayerShopRegistrySavedData.ShopLocation,
                PlayerShopRegistrySavedData.ShopRecord> allShops = registry.getAllShops();

        String currentDimension = level.dimension().location().toString();

        for (Map.Entry<PlayerShopRegistrySavedData.ShopLocation,
                PlayerShopRegistrySavedData.ShopRecord> entry : allShops.entrySet()) {
            PlayerShopRegistrySavedData.ShopRecord record = entry.getValue();
            // Only include shops in the same dimension
            if (!currentDimension.equals(record.dimension())) {
                continue;
            }

            BlockPos shopPos = BlockPos.of(entry.getKey().posLong());
            double distance = center.distSqr(shopPos);
            if (!unlimited && distance > (double) radiusSq) {
                continue;
            }

            // Verify the block entity still exists and has listings
            if (!level.hasChunkAt(shopPos)) {
                continue;
            }

            BlockEntity be = level.getBlockEntity(shopPos);
            if (!(be instanceof ShopBlockEntity shop)) {
                continue;
            }

            if (shop.getOwnerUuid() == null || shop.getListings().isEmpty()) {
                continue;
            }

            // Don't show the player's own shops unless they have items
            String ownerName = resolveOwnerName(player, shop.getOwnerUuid());
            String shopName = shop.getShopName().isBlank()
                    ? ownerName + "'s Shop"
                    : shop.getShopName();

            int listingCount = shop.getListings().size();
            int totalStock = PlayerShopBlockService.countStock(level, shop, shopPos);
            double dist = Math.sqrt(distance);

            results.add(new NearbyShopEntry(
                    shopPos,
                    shop.getOwnerUuid(),
                    ownerName,
                    shopName,
                    listingCount,
                    totalStock,
                    dist
            ));
        }

        // Sort by distance (closest first)
        results.sort(Comparator.comparingDouble(NearbyShopEntry::distance));

        // Limit results
        if (results.size() > MAX_RESULTS) {
            return results.subList(0, MAX_RESULTS);
        }

        return results;
    }

    private static String resolveOwnerName(ServerPlayer viewer, UUID ownerUuid) {
        if (ownerUuid == null) {
            return "Unknown";
        }
        net.minecraft.server.level.ServerPlayer online = viewer.server.getPlayerList().getPlayer(ownerUuid);
        if (online != null) {
            return online.getGameProfile().getName();
        }
        return viewer.server.getProfileCache().get(ownerUuid)
                .map(profile -> profile.getName())
                .orElse(ownerUuid.toString().substring(0, 8));
    }
}
