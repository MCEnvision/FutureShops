package com.enviouse.futureshops.server.market.bazaar.escrow;

import com.enviouse.futureshops.server.market.bazaar.BazaarSettlementKind;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

public final class BazaarEscrowIds {
    private static final UUID ZERO = new UUID(0L, 0L);

    private BazaarEscrowIds() {
    }

    public static UUID requireId(UUID value, String label) {
        UUID result = Objects.requireNonNull(value, label);
        if (ZERO.equals(result)) {
            throw new IllegalArgumentException(
                    "Bazaar escrow identifier is invalid");
        }
        return result;
    }

    public static UUID moneyClaimId(
            UUID transactionId,
            BazaarSettlementKind kind,
            UUID orderId,
            UUID ownerId
    ) {
        return derive("money claim", transactionId, kind.name(), orderId,
                ownerId);
    }

    public static UUID rejectedCustodyReturnTransactionId(
            UUID requestId,
            UUID orderId
    ) {
        return derive("rejected custody return", requestId, orderId);
    }

    public static UUID transactionAssetId(
            UUID transactionId,
            String role,
            int ordinal
    ) {
        if (ordinal < 0) {
            throw new IllegalArgumentException(
                    "Bazaar escrow asset ordinal is invalid");
        }
        return derive("transaction asset", transactionId,
                Objects.requireNonNull(role, "role"), ordinal);
    }

    public static UUID derive(String purpose, Object... parts) {
        StringBuilder identity = new StringBuilder(
                "futureshops bazaar escrow v1");
        identity.append('\u0000').append(Objects.requireNonNull(
                purpose, "purpose"));
        for (Object part : parts) {
            identity.append('\u0000').append(Objects.requireNonNull(
                    part, "identity part"));
        }
        return UUID.nameUUIDFromBytes(identity.toString().getBytes(
                StandardCharsets.UTF_8));
    }
}
