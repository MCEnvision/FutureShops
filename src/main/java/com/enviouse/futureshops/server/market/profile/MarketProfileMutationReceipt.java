package com.enviouse.futureshops.server.market.profile;

import java.util.Objects;
import java.util.UUID;

public record MarketProfileMutationReceipt(
        UUID ownerId,
        String fingerprint,
        MarketProfileMutationResult result
) {
    private static final UUID ZERO = new UUID(0L, 0L);

    public MarketProfileMutationReceipt {
        ownerId = Objects.requireNonNull(ownerId, "ownerId");
        fingerprint = Objects.requireNonNull(fingerprint,
                "fingerprint");
        result = Objects.requireNonNull(result, "result");
        if (ZERO.equals(ownerId)
                || !fingerprint.matches("[0-9a-f]{64}")
                || result.replayed()) {
            throw new IllegalArgumentException(
                    "Market profile mutation receipt is invalid");
        }
    }

    public UUID requestId() {
        return result.requestId();
    }

    public boolean matches(
            UUID owner,
            MarketProfileMutationCommand command,
            String requestFingerprint
    ) {
        return ownerId.equals(Objects.requireNonNull(owner, "owner"))
                && result.requestId().equals(command.requestId())
                && result.routeNonce().equals(command.routeNonce())
                && result.module() == command.module()
                && result.type() == command.mutation().type()
                && fingerprint.equals(requestFingerprint);
    }
}
