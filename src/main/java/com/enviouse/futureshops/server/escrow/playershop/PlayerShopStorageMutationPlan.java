package com.enviouse.futureshops.server.escrow.playershop;

import java.util.Objects;
import java.util.UUID;

public record PlayerShopStorageMutationPlan(
        UUID mutationId,
        int sequence,
        Direction direction,
        PlayerShopStorageEndpoint endpoint,
        UUID itemTransferId,
        UUID claimId,
        PlayerShopItemLot lot,
        String expectedStateFingerprint
) {
    private static final UUID ZERO_UUID = new UUID(0L, 0L);

    public PlayerShopStorageMutationPlan {
        mutationId = PlayerShopBinarySupport.requireUuid(mutationId,
                "storage mutation id");
        if (sequence < 0 || sequence >= PlayerShopEscrowConstants.MAX_STORAGE_MUTATIONS) {
            throw new IllegalArgumentException("Player shop storage sequence is invalid");
        }
        direction = Objects.requireNonNull(direction, "direction");
        endpoint = Objects.requireNonNull(endpoint, "endpoint");
        itemTransferId = PlayerShopBinarySupport.requireUuid(itemTransferId,
                "storage item transfer id");
        claimId = Objects.requireNonNull(claimId, "claimId");
        lot = Objects.requireNonNull(lot, "lot");
        expectedStateFingerprint = PlayerShopBinarySupport.requireString(
                expectedStateFingerprint, 128, "storage state fingerprint");
        if (direction == Direction.INSERT && ZERO_UUID.equals(claimId)) {
            throw new IllegalArgumentException("Player shop storage insertion claim is invalid");
        }
        if (direction == Direction.EXTRACT && !ZERO_UUID.equals(claimId)) {
            throw new IllegalArgumentException("Player shop storage extraction claim is invalid");
        }
    }

    public static PlayerShopStorageMutationPlan extraction(
            UUID requestId,
            int sequence,
            PlayerShopStorageEndpoint endpoint,
            UUID transferId,
            PlayerShopItemLot lot,
            String expectedStateFingerprint
    ) {
        return new PlayerShopStorageMutationPlan(
                PlayerShopBinarySupport.deterministicUuid("storage mutation",
                        requestId, "extract." + sequence),
                sequence, Direction.EXTRACT, endpoint, transferId, ZERO_UUID,
                lot, expectedStateFingerprint);
    }

    public static PlayerShopStorageMutationPlan insertion(
            UUID requestId,
            int sequence,
            PlayerShopStorageEndpoint endpoint,
            UUID transferId,
            UUID claimId,
            PlayerShopItemLot lot,
            String expectedStateFingerprint
    ) {
        return new PlayerShopStorageMutationPlan(
                PlayerShopBinarySupport.deterministicUuid("storage mutation",
                        requestId, "insert." + sequence),
                sequence, Direction.INSERT, endpoint, transferId, claimId,
                lot, expectedStateFingerprint);
    }

    public enum Direction {
        EXTRACT,
        INSERT
    }
}
