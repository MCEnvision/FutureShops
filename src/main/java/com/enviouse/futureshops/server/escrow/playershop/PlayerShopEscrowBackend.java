package com.enviouse.futureshops.server.escrow.playershop;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public interface PlayerShopEscrowBackend {
    Optional<PlayerShopExecutionSnapshot> load(UUID requestId);

    void persistIntent(PlayerShopExecutionSnapshot snapshot);

    PlayerShopPreparedExecution prepare(
            PlayerShopRequestIdentity requestIdentity,
            PlayerShopEscrowIntent intent
    );

    void persistPreparation(PlayerShopPreparedExecution preparation);

    PlayerShopFundingEvidence commitFunding(
            PlayerShopPreparedExecution preparation
    );

    void persistFunding(PlayerShopFundingEvidence funding);

    PlayerShopClaimCreationEvidence createClaims(
            PlayerShopPreparedExecution preparation,
            PlayerShopFundingEvidence funding
    );

    void persistClaimCreation(PlayerShopClaimCreationEvidence claims);

    void persistCommit(PlayerShopAtomicCommit commit);

    DeliveryResult deliverClaims(
            PlayerShopAtomicCommit commit,
            PlayerShopPreparedExecution preparation
    );

    RecoveryResult recover(PlayerShopExecutionSnapshot snapshot);

    void markSettlementImported(
            PlayerShopSettlementImportEvidence settlement,
            PlayerShopAtomicCommit commit
    );

    record DeliveryResult(DeliveryStatus status, String detail) {
        public DeliveryResult {
            status = Objects.requireNonNull(status, "status");
            detail = PlayerShopBinarySupport.optionalString(detail,
                    PlayerShopEscrowConstants.MAX_TEXT_LENGTH,
                    "delivery detail");
            if (status == DeliveryStatus.DELIVERED && !detail.isEmpty()
                    || status != DeliveryStatus.DELIVERED
                    && detail.isEmpty()) {
                throw new IllegalArgumentException("Player shop delivery result is invalid");
            }
        }
    }

    enum DeliveryStatus {
        DELIVERED,
        CLAIMS_PENDING,
        RECOVERY_REQUIRED,
        QUARANTINED
    }

    record RecoveryResult(RecoveryStatus status, String detail) {
        public RecoveryResult {
            status = Objects.requireNonNull(status, "status");
            detail = PlayerShopBinarySupport.requireString(detail,
                    PlayerShopEscrowConstants.MAX_TEXT_LENGTH,
                    "recovery detail");
        }
    }

    enum RecoveryStatus {
        RESUMABLE,
        RECOVERY_REQUIRED,
        QUARANTINED
    }
}
