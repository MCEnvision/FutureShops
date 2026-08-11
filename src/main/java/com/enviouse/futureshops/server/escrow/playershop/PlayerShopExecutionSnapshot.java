package com.enviouse.futureshops.server.escrow.playershop;

import java.util.Objects;
import java.util.Optional;

public record PlayerShopExecutionSnapshot(
        PlayerShopRequestIdentity requestIdentity,
        PlayerShopEscrowIntent intent,
        PlayerShopSettlementImportEvidence settlementImport,
        PlayerShopPreparedExecution preparation,
        PlayerShopFundingEvidence funding,
        PlayerShopClaimCreationEvidence claimCreation,
        PlayerShopAtomicCommit commit
) {
    public PlayerShopExecutionSnapshot {
        requestIdentity = Objects.requireNonNull(requestIdentity,
                "requestIdentity");
        intent = Objects.requireNonNull(intent, "intent");
        if (!requestIdentity.matches(intent)) {
            throw new IllegalArgumentException("Player shop snapshot request is invalid");
        }
        if (settlementImport != null && !settlementImport.matches(intent)) {
            throw new IllegalArgumentException("Player shop snapshot settlement is invalid");
        }
        if (preparation != null && (!preparation.requestIdentity().equals(
                requestIdentity)
                || !preparation.intent().equals(intent))) {
            throw new IllegalArgumentException("Player shop snapshot preparation is invalid");
        }
        if (funding != null && preparation == null) {
            throw new IllegalArgumentException("Player shop snapshot funding order is invalid");
        }
        if (claimCreation != null && (funding == null
                || funding.status() != PlayerShopFundingEvidence.Status.COMPLETE
                || !claimCreation.completeFor(intent))) {
            throw new IllegalArgumentException("Player shop snapshot claims are invalid");
        }
        if (commit != null && (claimCreation == null
                || !commit.commitId().equals(intent.requestId())
                || !commit.committedIntent().intentFingerprint().equals(
                intent.intentFingerprint()))) {
            throw new IllegalArgumentException("Player shop snapshot commit is invalid");
        }
    }

    public static PlayerShopExecutionSnapshot intentOnly(
            PlayerShopRequestIdentity identity,
            PlayerShopEscrowIntent intent,
            PlayerShopSettlementImportEvidence settlementImport
    ) {
        return new PlayerShopExecutionSnapshot(identity, intent,
                settlementImport, null, null, null, null);
    }

    public PlayerShopExecutionSnapshot withPreparation(
            PlayerShopPreparedExecution value
    ) {
        return new PlayerShopExecutionSnapshot(requestIdentity, intent,
                settlementImport, value, null, null, null);
    }

    public PlayerShopExecutionSnapshot withFunding(
            PlayerShopFundingEvidence value
    ) {
        return new PlayerShopExecutionSnapshot(requestIdentity, intent,
                settlementImport, preparation, value, null, null);
    }

    public PlayerShopExecutionSnapshot withClaims(
            PlayerShopClaimCreationEvidence value
    ) {
        return new PlayerShopExecutionSnapshot(requestIdentity, intent,
                settlementImport, preparation, funding, value, null);
    }

    public PlayerShopExecutionSnapshot withCommit(PlayerShopAtomicCommit value) {
        return new PlayerShopExecutionSnapshot(requestIdentity, intent,
                settlementImport, preparation, funding, claimCreation, value);
    }

    public Optional<PlayerShopSettlementImportEvidence> settlementValue() {
        return Optional.ofNullable(settlementImport);
    }

    public Optional<PlayerShopPreparedExecution> preparationValue() {
        return Optional.ofNullable(preparation);
    }

    public Optional<PlayerShopFundingEvidence> fundingValue() {
        return Optional.ofNullable(funding);
    }

    public Optional<PlayerShopClaimCreationEvidence> claimCreationValue() {
        return Optional.ofNullable(claimCreation);
    }

    public Optional<PlayerShopAtomicCommit> commitValue() {
        return Optional.ofNullable(commit);
    }
}
