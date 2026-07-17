package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.playershop.PlayerShopAtomicCommit;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopClaimCreationEvidence;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopEscrowBackend;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopEscrowLifecycleEvent;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopExecutionSnapshot;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopFundingEvidence;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopPreparedExecution;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopRequestIdentity;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopEscrowIntent;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopSettlementImportEvidence;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

public final class RuntimePlayerShopEscrowBackend
        implements PlayerShopEscrowBackend {
    private final EscrowRuntimeService runtime;
    private final MutationDriver mutations;

    public RuntimePlayerShopEscrowBackend(
            EscrowRuntimeService runtime,
            MutationDriver mutations
    ) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.mutations = Objects.requireNonNull(mutations, "mutations");
    }

    @Override
    public Optional<PlayerShopExecutionSnapshot> load(UUID requestId) {
        return runtime.playerShopEscrowEntry(requestId)
                .map(PlayerShopEscrowSavedData.Entry::snapshot);
    }

    @Override
    public void persistIntent(PlayerShopExecutionSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        Optional<PlayerShopEscrowSavedData.Entry> current =
                runtime.playerShopEscrowEntry(
                        snapshot.intent().requestId());
        if (current.isPresent()) {
            if (!current.orElseThrow().snapshot().equals(snapshot)) {
                throw conflict();
            }
            return;
        }
        commit(snapshot, -1L, false);
    }

    @Override
    public PlayerShopPreparedExecution prepare(
            PlayerShopRequestIdentity requestIdentity,
            PlayerShopEscrowIntent intent
    ) {
        return mutations.prepare(requestIdentity, intent);
    }

    @Override
    public void persistPreparation(
            PlayerShopPreparedExecution preparation
    ) {
        advance(preparation.intent().requestId(), snapshot ->
                snapshot.withPreparation(preparation), false);
    }

    @Override
    public PlayerShopFundingEvidence commitFunding(
            PlayerShopPreparedExecution preparation
    ) {
        PlayerShopFundingEvidence existing = runtime
                .playerShopEscrowEntry(
                        preparation.intent().requestId())
                .map(PlayerShopEscrowSavedData.Entry::snapshot)
                .map(PlayerShopExecutionSnapshot::funding)
                .orElse(null);
        return mutations.commitFunding(preparation, existing,
                this::persistFunding);
    }

    @Override
    public void persistFunding(PlayerShopFundingEvidence funding) {
        advance(funding.requestId(), snapshot ->
                snapshot.withFunding(funding), false);
    }

    @Override
    public PlayerShopClaimCreationEvidence createClaims(
            PlayerShopPreparedExecution preparation,
            PlayerShopFundingEvidence funding
    ) {
        return mutations.createClaims(preparation, funding);
    }

    @Override
    public void persistClaimCreation(
            PlayerShopClaimCreationEvidence claims
    ) {
        advance(claims.requestId(), snapshot ->
                snapshot.withClaims(claims), false);
    }

    @Override
    public void persistCommit(PlayerShopAtomicCommit commit) {
        advance(commit.commitId(), snapshot ->
                snapshot.withCommit(commit), false);
    }

    @Override
    public DeliveryResult deliverClaims(
            PlayerShopAtomicCommit commit,
            PlayerShopPreparedExecution preparation
    ) {
        return mutations.deliverClaims(commit, preparation);
    }

    @Override
    public RecoveryResult recover(PlayerShopExecutionSnapshot snapshot) {
        return mutations.recover(snapshot);
    }

    @Override
    public void markSettlementImported(
            PlayerShopSettlementImportEvidence settlement,
            PlayerShopAtomicCommit commit
    ) {
        Optional<PlayerShopEscrowSavedData.Entry> current =
                runtime.playerShopEscrowEntry(commit.commitId());
        if (current.isPresent()
                && current.orElseThrow().settlementImported()) {
            return;
        }
        mutations.markSettlementImported(settlement, commit);
        advance(commit.commitId(), UnaryOperator.identity(), true);
    }

    private void advance(
            UUID requestId,
            UnaryOperator<PlayerShopExecutionSnapshot> update,
            boolean settlementImported
    ) {
        PlayerShopEscrowSavedData.Entry current = runtime
                .playerShopEscrowEntry(requestId).orElseThrow(() ->
                        new IllegalStateException(
                                "Player shop escrow intent is missing"));
        PlayerShopExecutionSnapshot next = Objects.requireNonNull(
                update.apply(current.snapshot()), "updated snapshot");
        boolean nextSettlement = current.settlementImported()
                || settlementImported;
        if (current.snapshot().equals(next)
                && current.settlementImported() == nextSettlement) {
            return;
        }
        commit(next, current.revision(), nextSettlement);
    }

    private void commit(
            PlayerShopExecutionSnapshot snapshot,
            long expectedRevision,
            boolean settlementImported
    ) {
        runtime.commitPlayerShopEscrowLifecycle(
                PlayerShopEscrowLifecycleEvent.advance(snapshot,
                        expectedRevision, settlementImported));
    }

    private static IllegalStateException conflict() {
        return new IllegalStateException(
                "Player shop request conflicts with durable escrow state");
    }

    public interface MutationDriver {
        PlayerShopPreparedExecution prepare(
                PlayerShopRequestIdentity requestIdentity,
                PlayerShopEscrowIntent intent
        );

        PlayerShopFundingEvidence commitFunding(
                PlayerShopPreparedExecution preparation,
                PlayerShopFundingEvidence existing,
                FundingProgress progress
        );

        PlayerShopClaimCreationEvidence createClaims(
                PlayerShopPreparedExecution preparation,
                PlayerShopFundingEvidence funding
        );

        DeliveryResult deliverClaims(
                PlayerShopAtomicCommit commit,
                PlayerShopPreparedExecution preparation
        );

        RecoveryResult recover(PlayerShopExecutionSnapshot snapshot);

        void markSettlementImported(
                PlayerShopSettlementImportEvidence settlement,
                PlayerShopAtomicCommit commit
        );
    }

    @FunctionalInterface
    public interface FundingProgress {
        void persist(PlayerShopFundingEvidence evidence);
    }
}
