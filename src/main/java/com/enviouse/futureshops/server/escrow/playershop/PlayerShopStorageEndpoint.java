package com.enviouse.futureshops.server.escrow.playershop;

public record PlayerShopStorageEndpoint(
        String dimensionId,
        int blockX,
        int blockY,
        int blockZ,
        int linkOrdinal,
        long linkRevision,
        String adapterId
) {
    public PlayerShopStorageEndpoint {
        dimensionId = PlayerShopBinarySupport.requireString(dimensionId,
                PlayerShopEscrowConstants.MAX_IDENTIFIER_LENGTH, "storage dimension id");
        adapterId = PlayerShopBinarySupport.requireString(adapterId,
                PlayerShopEscrowConstants.MAX_IDENTIFIER_LENGTH, "storage adapter id");
        if (linkOrdinal < 0 || linkOrdinal > 255 || linkRevision < 0L) {
            throw new IllegalArgumentException("Player shop storage identity is invalid");
        }
    }

    public String stableKey() {
        return dimensionId + "." + blockX + "." + blockY + "." + blockZ
                + "." + linkOrdinal + "." + linkRevision + "." + adapterId;
    }
}
