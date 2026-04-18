package com.enviouse.futureshops.data;

import net.minecraft.core.BlockPos;

import java.util.UUID;

/**
 * DTO for a nearby player-owned shop block, sent from server to client
 * as part of the extended S2CShopDataPacket.
 */
public record NearbyShopEntry(
        BlockPos pos,
        UUID ownerUuid,
        String ownerName,
        String shopName,
        int listingCount,
        int totalStock,
        double distance
) {
}

