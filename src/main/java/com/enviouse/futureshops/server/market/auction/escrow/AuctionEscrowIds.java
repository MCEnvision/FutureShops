package com.enviouse.futureshops.server.market.auction.escrow;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

public final class AuctionEscrowIds {
    public static final int FUTURE_JOURNAL_WIRE_ID = 34;

    private AuctionEscrowIds() {
    }

    public static UUID custodyRequestId(UUID requestId) {
        return deterministic("custody request", requestId, null, 0);
    }

    public static UUID ledgerTransactionId(UUID requestId) {
        return deterministic("ledger transaction", requestId, null, 0);
    }

    public static UUID settlementBookRequestId(UUID requestId) {
        return deterministic("settlement book request", requestId, null, 0);
    }

    public static UUID refundClaimId(UUID requestId, UUID ownerId) {
        return deterministic("refund claim", requestId, ownerId, 0);
    }

    public static UUID sellerProceedsClaimId(UUID requestId, UUID ownerId) {
        return deterministic("seller proceeds claim", requestId, ownerId, 0);
    }

    @Deprecated
    public static UUID sellerOverflowClaimId(UUID requestId, UUID ownerId) {
        return sellerProceedsClaimId(requestId, ownerId);
    }

    public static UUID itemClaimId(UUID requestId, UUID ownerId, int index) {
        if (index < 0) {
            throw new IllegalArgumentException("Auction item claim index is invalid");
        }
        return deterministic("item claim", requestId, ownerId, index);
    }

    public static UUID assetLotId(UUID requestId, String purpose, int index) {
        if (index < 0) {
            throw new IllegalArgumentException("Auction asset lot index is invalid");
        }
        return deterministic(Objects.requireNonNull(purpose, "purpose"),
                requestId, null, index);
    }

    private static UUID deterministic(
            String purpose,
            UUID requestId,
            UUID ownerId,
            int index
    ) {
        requireId(requestId, "requestId");
        if (ownerId != null) {
            requireId(ownerId, "ownerId");
        }
        String material = "futureshops auction escrow v1\u0000"
                + purpose + "\u0000" + requestId + "\u0000"
                + (ownerId == null ? "" : ownerId) + "\u0000" + index;
        UUID result = UUID.nameUUIDFromBytes(material.getBytes(
                StandardCharsets.UTF_8));
        requireId(result, "derivedId");
        return result;
    }

    static UUID requireId(UUID value, String name) {
        UUID result = Objects.requireNonNull(value, name);
        if (result.getMostSignificantBits() == 0L
                && result.getLeastSignificantBits() == 0L) {
            throw new IllegalArgumentException(
                    "Auction escrow identifier is required");
        }
        return result;
    }
}
