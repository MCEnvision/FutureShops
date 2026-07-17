package com.enviouse.futureshops.server.escrow.playershop;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record PlayerShopClaimCreationEvidence(
        UUID requestId,
        Status status,
        List<PlayerShopClaimPlan> claims,
        String backendEvidence,
        String detail
) {
    public PlayerShopClaimCreationEvidence {
        requestId = PlayerShopBinarySupport.requireUuid(requestId,
                "claim creation request id");
        status = Objects.requireNonNull(status, "status");
        claims = List.copyOf(Objects.requireNonNull(claims, "claims"));
        if (claims.size() > PlayerShopEscrowConstants.MAX_CLAIMS) {
            throw new IllegalArgumentException("Player shop created claims are too large");
        }
        backendEvidence = PlayerShopBinarySupport.requireString(
                backendEvidence, PlayerShopEscrowConstants.MAX_TEXT_LENGTH,
                "claim creation evidence");
        detail = PlayerShopBinarySupport.optionalString(detail,
                PlayerShopEscrowConstants.MAX_TEXT_LENGTH,
                "claim creation detail");
        if (status.isComplete() && !detail.isEmpty()
                || !status.isComplete() && detail.isEmpty()) {
            throw new IllegalArgumentException("Player shop claim creation state is invalid");
        }
    }

    public boolean completeFor(PlayerShopEscrowIntent intent) {
        return status.isComplete() && requestId.equals(intent.requestId())
                && claims.equals(intent.claims());
    }

    public enum Status {
        CREATED,
        IDEMPOTENT_REPLAY,
        RECOVERY_REQUIRED,
        QUARANTINED;

        public boolean isComplete() {
            return this == CREATED || this == IDEMPOTENT_REPLAY;
        }
    }
}
