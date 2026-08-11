package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.journal.JournalRecord;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerPaymentServiceTest {
    private static final PlayerPaymentService.FreshSettings SETTINGS =
            new PlayerPaymentService.FreshSettings(100L, "Coins", 2);

    @Test
    void offlineRecipientOverflowCommitsDebtWalletAndClaimExactlyOnce() {
        FakeBackend backend = overflowBackend();
        PlayerPaymentService.PlayerPaymentRequest request = request(
                PlayerPaymentTestFixtures.REQUEST_ID, 1_000L);

        PlayerPaymentService.PlayerPaymentResult result =
                PlayerPaymentService.execute(
                        request, backend, SETTINGS,
                        PlayerPaymentTestFixtures.NOW);

        assertTrue(result.successful());
        assertFalse(result.replayed());
        assertEquals(150L, result.acceptedMinorUnits());
        assertEquals(850L, result.overflowClaimMinorUnits());
        assertEquals(1, backend.commitCount);
        assertEquals(1_000L, backend.balance(
                PlayerPaymentCommit.walletAccount(request.payerId())));
        assertEquals(0L, backend.balance(
                PlayerPaymentCommit.debtAccount(request.recipientId())));
        assertEquals(100L, backend.balance(
                PlayerPaymentCommit.walletAccount(request.recipientId())));
        assertEquals(request.recipientId(), backend.claims.get(0).ownerId());
    }

    @Test
    void exactReplayIgnoresLiveConfigAndCollectedClaimState() {
        FakeBackend backend = overflowBackend();
        PlayerPaymentService.PlayerPaymentRequest request = request(
                PlayerPaymentTestFixtures.REQUEST_ID, 1_000L);
        PlayerPaymentService.PlayerPaymentResult first =
                PlayerPaymentService.execute(
                        request, backend, SETTINGS,
                        PlayerPaymentTestFixtures.NOW);
        backend.claims.set(0, backend.claims.get(0).deliver(
                100L, PlayerPaymentTestFixtures.NOW.plusSeconds(1)));

        PlayerPaymentService.PlayerPaymentResult replay =
                PlayerPaymentService.execute(
                        request, backend,
                        new PlayerPaymentService.FreshSettings(
                                999L, "Credits", 0),
                        PlayerPaymentTestFixtures.NOW.plusSeconds(2));

        assertTrue(replay.successful());
        assertTrue(replay.replayed());
        assertEquals(first.amountMinorUnits(), replay.amountMinorUnits());
        assertEquals("Coins", replay.currencyName());
        assertEquals(1, backend.commitCount);
    }

    @Test
    void changedSemanticsOnACompletedOrCreatedRequestConflict() {
        FakeBackend completed = overflowBackend();
        PlayerPaymentService.PlayerPaymentRequest original = request(
                PlayerPaymentTestFixtures.REQUEST_ID, 1_000L);
        PlayerPaymentService.execute(original, completed, SETTINGS,
                PlayerPaymentTestFixtures.NOW);

        assertEquals(PlayerPaymentService.Status.REQUEST_CONFLICT,
                PlayerPaymentService.execute(
                        request(PlayerPaymentTestFixtures.REQUEST_ID, 999L),
                        completed, SETTINGS,
                        PlayerPaymentTestFixtures.NOW).status());

        PlayerPaymentCommit commit = PlayerPaymentTestFixtures.overflow();
        FakeBackend created = overflowBackend();
        created.transaction = created(commit.completedTransaction());
        PlayerPaymentService.PlayerPaymentRequest changedRecipient =
                new PlayerPaymentService.PlayerPaymentRequest(
                        commit.requestId(), commit.payerId(),
                        UUID.randomUUID(), commit.amountMinorUnits());
        assertEquals(PlayerPaymentService.Status.REQUEST_CONFLICT,
                PlayerPaymentService.execute(
                        changedRecipient, created, SETTINGS,
                        PlayerPaymentTestFixtures.NOW).status());
        assertEquals(PlayerPaymentService.Status.RECOVERY_REQUIRED,
                PlayerPaymentService.execute(
                        original, created, SETTINGS,
                        PlayerPaymentTestFixtures.NOW).status());
    }

    @Test
    void partialCommitAndExtraClaimEvidenceRequireRecovery() {
        FakeBackend partial = overflowBackend();
        partial.failAfterTransaction = true;
        PlayerPaymentService.PlayerPaymentRequest request = request(
                PlayerPaymentTestFixtures.REQUEST_ID, 1_000L);

        assertEquals(PlayerPaymentService.Status.RECOVERY_REQUIRED,
                PlayerPaymentService.execute(
                        request, partial, SETTINGS,
                        PlayerPaymentTestFixtures.NOW).status());
        assertEquals(1, partial.commitCount);

        FakeBackend extraClaim = overflowBackend();
        PlayerPaymentService.execute(request, extraClaim, SETTINGS,
                PlayerPaymentTestFixtures.NOW);
        extraClaim.claims.add(new EscrowClaim(
                UUID.randomUUID(), request.requestId(),
                request.recipientId(),
                "player.payment.extra." + request.requestId(),
                ClaimKind.MONEY, 1L, 1L, new byte[0],
                ClaimStatus.PENDING, "Unexpected payment claim",
                PlayerPaymentTestFixtures.NOW,
                PlayerPaymentTestFixtures.NOW));
        assertEquals(PlayerPaymentService.Status.RECOVERY_REQUIRED,
                PlayerPaymentService.execute(
                        request, extraClaim, SETTINGS,
                PlayerPaymentTestFixtures.NOW).status());
    }

    @Test
    void quarantinedOverflowClaimCannotReplayAsCompletedPayment() {
        FakeBackend backend = overflowBackend();
        PlayerPaymentService.PlayerPaymentRequest request = request(
                PlayerPaymentTestFixtures.REQUEST_ID, 1_000L);
        PlayerPaymentService.execute(request, backend, SETTINGS,
                PlayerPaymentTestFixtures.NOW);
        backend.claims.set(0, backend.claims.get(0).quarantine(
                PlayerPaymentTestFixtures.NOW.plusSeconds(1)));

        assertEquals(PlayerPaymentService.Status.RECOVERY_REQUIRED,
                PlayerPaymentService.execute(
                        request, backend, SETTINGS,
                        PlayerPaymentTestFixtures.NOW.plusSeconds(2))
                        .status());
        assertEquals(1, backend.commitCount);
    }

    @Test
    void validatesSelfAmountAndAvailableFundsBeforeCommit() {
        FakeBackend backend = overflowBackend();
        PlayerPaymentService.PlayerPaymentRequest self =
                new PlayerPaymentService.PlayerPaymentRequest(
                        UUID.randomUUID(),
                        PlayerPaymentTestFixtures.PAYER_ID,
                        PlayerPaymentTestFixtures.PAYER_ID, 1L);
        PlayerPaymentService.PlayerPaymentRequest invalid = request(
                UUID.randomUUID(), 0L);
        PlayerPaymentService.PlayerPaymentRequest insufficient = request(
                UUID.randomUUID(), 2_001L);

        assertEquals(PlayerPaymentService.Status.SELF_PAYMENT,
                PlayerPaymentService.execute(self, backend, SETTINGS,
                        PlayerPaymentTestFixtures.NOW).status());
        assertEquals(PlayerPaymentService.Status.INVALID_AMOUNT,
                PlayerPaymentService.execute(invalid, backend, SETTINGS,
                        PlayerPaymentTestFixtures.NOW).status());
        assertEquals(PlayerPaymentService.Status.INSUFFICIENT_FUNDS,
                PlayerPaymentService.execute(insufficient, backend, SETTINGS,
                        PlayerPaymentTestFixtures.NOW).status());
        assertEquals(0, backend.commitCount);
    }

    @Test
    void concurrentDuplicateRequestHasOneCommitAndOneReplay()
            throws Exception {
        FakeBackend backend = overflowBackend();
        PlayerPaymentService.PlayerPaymentRequest request = request(
                PlayerPaymentTestFixtures.REQUEST_ID, 1_000L);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<PlayerPaymentService.PlayerPaymentResult> first =
                    executor.submit(() -> PlayerPaymentService.execute(
                            request, backend, SETTINGS,
                            PlayerPaymentTestFixtures.NOW));
            Future<PlayerPaymentService.PlayerPaymentResult> second =
                    executor.submit(() -> PlayerPaymentService.execute(
                            request, backend, SETTINGS,
                            PlayerPaymentTestFixtures.NOW));
            List<PlayerPaymentService.PlayerPaymentResult> results =
                    List.of(first.get(), second.get());

            assertTrue(results.stream().allMatch(
                    PlayerPaymentService.PlayerPaymentResult::successful));
            assertEquals(1L, results.stream().filter(
                    PlayerPaymentService.PlayerPaymentResult::replayed)
                    .count());
            assertEquals(1, backend.commitCount);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void unreadableReplayStateFailsUnavailableInsteadOfThrowing() {
        FakeBackend backend = overflowBackend();
        backend.failEvidenceReads = true;
        PlayerPaymentService.PlayerPaymentRequest request = request(
                PlayerPaymentTestFixtures.REQUEST_ID, 1_000L);

        PlayerPaymentService.PlayerPaymentResult result =
                PlayerPaymentService.execute(
                        request, backend, SETTINGS,
                        PlayerPaymentTestFixtures.NOW);

        assertEquals(PlayerPaymentService.Status.ESCROW_UNAVAILABLE,
                result.status());
        assertEquals(0, backend.commitCount);
    }

    private static PlayerPaymentService.PlayerPaymentRequest request(
            UUID requestId,
            long amount
    ) {
        return new PlayerPaymentService.PlayerPaymentRequest(
                requestId, PlayerPaymentTestFixtures.PAYER_ID,
                PlayerPaymentTestFixtures.RECIPIENT_ID, amount);
    }

    private static FakeBackend overflowBackend() {
        FakeBackend backend = new FakeBackend();
        backend.balances.put(PlayerPaymentCommit.walletAccount(
                PlayerPaymentTestFixtures.PAYER_ID), 2_000L);
        backend.balances.put(PlayerPaymentCommit.debtAccount(
                PlayerPaymentTestFixtures.RECIPIENT_ID), -50L);
        return backend;
    }

    private static EscrowTransaction created(EscrowTransaction completed) {
        return EscrowTransaction.create(
                completed.transactionId(),
                completed.parentTransactionId(),
                completed.requestKey(),
                completed.operation(),
                completed.participants(),
                completed.assetLots(),
                completed.timestamps().createdAt(),
                completed.configRevision(),
                completed.shopReference());
    }

    private static final class FakeBackend
            implements PlayerPaymentService.Backend {
        private final Map<LedgerAccountId, Long> balances =
                new HashMap<>();
        private final List<EscrowClaim> claims = new ArrayList<>();
        private EscrowTransaction transaction;
        private LedgerTransaction ledger;
        private int commitCount;
        private boolean failAfterTransaction;
        private boolean failEvidenceReads;

        @Override
        public Optional<EscrowTransaction> transaction(UUID transactionId) {
            if (failEvidenceReads) {
                throw new IllegalStateException(
                        "Simulated unreadable payment evidence");
            }
            return transaction != null
                    && transaction.transactionId().value()
                    .equals(transactionId)
                    ? Optional.of(transaction) : Optional.empty();
        }

        @Override
        public Optional<LedgerTransaction> ledgerTransaction(
                UUID transactionId
        ) {
            return ledger != null
                    && ledger.transactionId().equals(transactionId)
                    ? Optional.of(ledger) : Optional.empty();
        }

        @Override
        public List<EscrowClaim> claimsForTransaction(UUID transactionId) {
            return claims.stream().filter(claim ->
                    claim.transactionId().equals(transactionId)).toList();
        }

        @Override
        public long balance(LedgerAccountId account) {
            return balances.getOrDefault(account, 0L);
        }

        @Override
        public EscrowCommitResult commit(PlayerPaymentCommit commit) {
            commitCount++;
            transaction = commit.completedTransaction();
            if (failAfterTransaction) {
                throw new IllegalStateException(
                        "Simulated payment commit failure");
            }
            ledger = commit.ledgerTransaction();
            for (var leg : ledger.legs()) {
                balances.merge(leg.account(), leg.deltaMinor(),
                        Math::addExact);
            }
            commit.overflowClaim().ifPresent(claims::add);
            return EscrowCommitResult.applied(new JournalRecord(
                    commitCount, commit.transactionId(),
                    UUID.randomUUID(), new byte[]{1}));
        }
    }
}
