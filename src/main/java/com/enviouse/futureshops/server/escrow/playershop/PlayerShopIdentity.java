package com.enviouse.futureshops.server.escrow.playershop;

import java.util.Objects;
import java.util.UUID;

public record PlayerShopIdentity(
        UUID registryShopId,
        long identityRevision,
        String shopId,
        String dimensionId,
        int blockX,
        int blockY,
        int blockZ,
        UUID ownerId
) {
    public PlayerShopIdentity {
        registryShopId = PlayerShopBinarySupport.requireUuid(registryShopId,
                "registry shop id");
        if (identityRevision < 0L) {
            throw new IllegalArgumentException("Player shop identity revision is invalid");
        }
        shopId = PlayerShopBinarySupport.requireString(shopId,
                PlayerShopEscrowConstants.MAX_IDENTIFIER_LENGTH, "shop id");
        dimensionId = PlayerShopBinarySupport.requireString(dimensionId,
                PlayerShopEscrowConstants.MAX_IDENTIFIER_LENGTH, "dimension id");
        ownerId = PlayerShopBinarySupport.requireUuid(ownerId, "owner id");
    }

    public String stableKey() {
        return registryShopId + "." + identityRevision + "." + dimensionId
                + "." + blockX + "." + blockY + "." + blockZ;
    }

    public boolean samePhysicalShop(PlayerShopIdentity other) {
        Objects.requireNonNull(other, "other");
        return registryShopId.equals(other.registryShopId)
                && identityRevision == other.identityRevision
                && dimensionId.equals(other.dimensionId)
                && blockX == other.blockX && blockY == other.blockY
                && blockZ == other.blockZ;
    }
}
