package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.claim.ClaimSavedData;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountType;
import com.enviouse.futureshops.server.escrow.ledger.LedgerLeg;
import com.enviouse.futureshops.server.escrow.ledger.LedgerSavedData;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;
import com.enviouse.futureshops.server.escrow.store.EscrowTransactionSavedData;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerPaymentHistoryProjectorTest {
    @Test
    void restartRescansDurablePaymentOutboxInBoundedBatches() {
        PlayerPaymentCommit commit = PlayerPaymentTestFixtures.direct();
        Stores stores = Stores.materialized(commit);
        List<PlayerPaymentCommit> firstProjection = new ArrayList<>();
        PlayerPaymentHistoryProjector first =
                new PlayerPaymentHistoryProjector(
                        stores.transactions(), stores.ledger(),
                        stores.claims(), firstProjection::add);

        assertEquals(1, first.reconcileBatch(1));
        assertEquals(List.of(commit), firstProjection);
        assertEquals(0, first.reconcileBatch(1));
        assertTrue(first.complete());

        List<PlayerPaymentCommit> restartProjection = new ArrayList<>();
        PlayerPaymentHistoryProjector restarted =
                new PlayerPaymentHistoryProjector(
                        stores.transactions(), stores.ledger(),
                        stores.claims(), restartProjection::add);
        assertEquals(1, restarted.reconcileBatch(10));
        assertTrue(restarted.complete());
        assertEquals(List.of(commit), restartProjection);
    }

    @Test
    void corruptOutboxEvidenceDoesNotEmitPresentationHistory() {
        PlayerPaymentCommit commit = PlayerPaymentTestFixtures.direct();
        EscrowTransactionSavedData transactions =
                new EscrowTransactionSavedData();
        transactions.applyFoldedAtomicCompletionCommitted(
                commit.completedTransaction());
        List<PlayerPaymentCommit> projection = new ArrayList<>();
        PlayerPaymentHistoryProjector projector =
                new PlayerPaymentHistoryProjector(
                        transactions, new LedgerSavedData(),
                        new ClaimSavedData(), projection::add);

        assertEquals(1, projector.reconcileBatch(10));
        assertTrue(projector.complete());
        assertTrue(projection.isEmpty());
    }

    @Test
    void transientHistoryFailureRetriesWithoutRestart() {
        PlayerPaymentCommit commit = PlayerPaymentTestFixtures.direct();
        Stores stores = Stores.materialized(commit);
        List<PlayerPaymentCommit> projection = new ArrayList<>();
        AtomicInteger attempts = new AtomicInteger();
        PlayerPaymentHistoryProjector projector =
                new PlayerPaymentHistoryProjector(
                        stores.transactions(), stores.ledger(),
                        stores.claims(), value -> {
                    if (attempts.getAndIncrement() == 0) {
                        throw new IllegalStateException(
                                "Transient history failure");
                    }
                    projection.add(value);
                });

        assertEquals(1, projector.reconcileBatch(10));
        assertFalse(projector.complete());
        assertTrue(projection.isEmpty());
        assertEquals(1, projector.reconcileBatch(10));
        assertTrue(projector.complete());
        assertEquals(List.of(commit), projection);
    }

    private record Stores(
            EscrowTransactionSavedData transactions,
            LedgerSavedData ledger,
            ClaimSavedData claims
    ) {
        private static Stores materialized(PlayerPaymentCommit commit) {
            EscrowTransactionSavedData transactions =
                    new EscrowTransactionSavedData();
            LedgerSavedData ledger = new LedgerSavedData();
            ClaimSavedData claims = new ClaimSavedData();
            seed(ledger, PlayerPaymentCommit.walletAccount(
                    commit.payerId()),
                    commit.payerWalletBeforeMinorUnits(),
                    "history payer seed");
            seed(ledger, PlayerPaymentCommit.walletAccount(
                    commit.recipientId()),
                    commit.recipientWalletBeforeMinorUnits(),
                    "history recipient seed");
            transactions.applyFoldedAtomicCompletionCommitted(
                    commit.completedTransaction());
            ledger.applyCommitted(commit.ledgerTransaction());
            commit.overflowClaim().ifPresent(claims::createCommitted);
            return new Stores(transactions, ledger, claims);
        }

        private static void seed(
                LedgerSavedData ledger,
                LedgerAccountId account,
                long units,
                String key
        ) {
            if (units == 0L) {
                return;
            }
            ledger.applyCommitted(new LedgerTransaction(
                    UUID.nameUUIDFromBytes(
                            key.getBytes(StandardCharsets.UTF_8)),
                    key, "seed", List.of(
                    new LedgerLeg(LedgerAccountId.system(
                            LedgerAccountType.ADMIN_SOURCE),
                            Math.negateExact(units)),
                    new LedgerLeg(account, units))));
        }
    }
}
