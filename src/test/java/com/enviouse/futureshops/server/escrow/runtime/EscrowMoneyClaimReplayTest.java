package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.claim.ClaimAttemptResult;
import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountType;
import com.enviouse.futureshops.server.escrow.ledger.LedgerLeg;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EscrowMoneyClaimReplayTest {
    @Test
    void exactLedgerAndAttemptAreBothRequiredForReplay() {
        Evidence exact = Evidence.exact();

        EscrowMoneyClaimService.CollectionResult replay =
                EscrowMoneyClaimService.resolveReplay(
                        exact, exact.ownerId, exact.claimId,
                        exact.requestId).orElseThrow();

        assertEquals(EscrowMoneyClaimService.Status.SUCCESS,
                replay.status());
        assertEquals(150L, replay.collectedMinorUnits());
        assertTrue(replay.replayed());

        Evidence ledgerOnly = Evidence.exact();
        ledgerOnly.attempt = null;
        assertEquals(EscrowMoneyClaimService.Status.RECOVERY_REQUIRED,
                EscrowMoneyClaimService.resolveReplay(
                        ledgerOnly, ledgerOnly.ownerId,
                        ledgerOnly.claimId, ledgerOnly.requestId)
                        .orElseThrow().status());

        Evidence attemptOnly = Evidence.exact();
        attemptOnly.ledger = null;
        assertEquals(EscrowMoneyClaimService.Status.RECOVERY_REQUIRED,
                EscrowMoneyClaimService.resolveReplay(
                        attemptOnly, attemptOnly.ownerId,
                        attemptOnly.claimId, attemptOnly.requestId)
                        .orElseThrow().status());
    }

    @Test
    void conflictingCompleteEvidenceCannotReplay() {
        Evidence evidence = Evidence.exact();
        evidence.attempt = new ClaimAttemptResult(
                evidence.claimId,
                MoneyClaimSettlement.requestKey(
                        evidence.requestId, evidence.claimId),
                149L, 51L, ClaimStatus.PARTIALLY_DELIVERED,
                PlayerPaymentTestFixtures.NOW.plusSeconds(1), false);

        assertEquals(EscrowMoneyClaimService.Status.REQUEST_CONFLICT,
                EscrowMoneyClaimService.resolveReplay(
                        evidence, evidence.ownerId,
                        evidence.claimId, evidence.requestId)
                        .orElseThrow().status());
    }

    @Test
    void absenceOfBothReceiptsIsAFreshRequest() {
        Evidence evidence = Evidence.exact();
        evidence.ledger = null;
        evidence.attempt = null;

        assertTrue(EscrowMoneyClaimService.resolveReplay(
                evidence, evidence.ownerId,
                evidence.claimId, evidence.requestId).isEmpty());
    }

    @Test
    void requestBoundToAnotherClaimIsAConflictNotPartialRecovery() {
        Evidence evidence = Evidence.exact();
        UUID foreignClaim = UUID.randomUUID();
        String foreignKey = MoneyClaimSettlement.requestKey(
                evidence.requestId, foreignClaim);
        evidence.ledger = new LedgerTransaction(
                evidence.requestId, foreignKey,
                MoneyClaimSettlement.LEDGER_REASON, List.of(
                new LedgerLeg(new LedgerAccountId(
                        LedgerAccountType.PLAYER_CLAIM,
                        foreignClaim.toString()), -1L),
                new LedgerLeg(PlayerPaymentCommit.walletAccount(
                        evidence.ownerId), 1L)));
        evidence.attempt = null;

        assertEquals(EscrowMoneyClaimService.Status.REQUEST_CONFLICT,
                EscrowMoneyClaimService.resolveReplay(
                        evidence, evidence.ownerId,
                        evidence.claimId, evidence.requestId)
                        .orElseThrow().status());
    }

    @Test
    void monetaryRefundUsesTheSameIdempotentReplayPath() {
        Evidence evidence = Evidence.exact(
                ClaimKind.REFUND, new byte[0]);

        EscrowMoneyClaimService.CollectionResult replay =
                EscrowMoneyClaimService.resolveReplay(
                        evidence, evidence.ownerId,
                        evidence.claimId, evidence.requestId)
                        .orElseThrow();

        assertEquals(EscrowMoneyClaimService.Status.SUCCESS,
                replay.status());
        assertEquals(150L, replay.collectedMinorUnits());
        assertTrue(replay.replayed());
    }

    @Test
    void itemRefundAndInternalMoneyCannotReplayAsWalletMoney() {
        Evidence itemRefund = Evidence.exact(
                ClaimKind.REFUND, new byte[]{1});
        Evidence internal = Evidence.exact(
                ClaimKind.INTERNAL_ESCROW_MONEY, new byte[0]);

        assertEquals(EscrowMoneyClaimService.Status.REQUEST_CONFLICT,
                EscrowMoneyClaimService.resolveReplay(
                        itemRefund, itemRefund.ownerId,
                        itemRefund.claimId, itemRefund.requestId)
                        .orElseThrow().status());
        assertEquals(EscrowMoneyClaimService.Status.REQUEST_CONFLICT,
                EscrowMoneyClaimService.resolveReplay(
                        internal, internal.ownerId,
                        internal.claimId, internal.requestId)
                        .orElseThrow().status());
    }

    private static final class Evidence
            implements EscrowMoneyClaimService.EvidenceBackend {
        private final UUID ownerId = UUID.randomUUID();
        private final UUID claimId = UUID.randomUUID();
        private final UUID requestId = UUID.randomUUID();
        private final Map<LedgerAccountId, Long> balances =
                new HashMap<>();
        private LedgerTransaction ledger;
        private ClaimAttemptResult attempt;
        private EscrowClaim claim;

        private static Evidence exact() {
            return exact(ClaimKind.MONEY, new byte[0]);
        }

        private static Evidence exact(
                ClaimKind kind,
                byte[] payload
        ) {
            Evidence evidence = new Evidence();
            EscrowClaim pending = new EscrowClaim(
                    evidence.claimId, UUID.randomUUID(), evidence.ownerId,
                    "money.claim.replay." + evidence.claimId,
                    kind, 200L, 200L, payload,
                    ClaimStatus.PENDING, "Money claim replay",
                    PlayerPaymentTestFixtures.NOW,
                    PlayerPaymentTestFixtures.NOW);
            MoneyClaimSettlement settlement =
                    MoneyClaimSettlement.create(
                            evidence.requestId, evidence.ownerId,
                            evidence.claimId, 0L, -50L, 0L,
                            200L, 100L, 1L,
                            PlayerPaymentTestFixtures.NOW.plusSeconds(1));
            evidence.ledger = settlement.ledgerTransaction();
            evidence.claim = pending.deliver(
                    settlement.deliveredUnits(),
                    settlement.delivery().deliveredAt());
            evidence.attempt = new ClaimAttemptResult(
                    evidence.claimId,
                    settlement.delivery().requestKey(),
                    settlement.deliveredUnits(),
                    evidence.claim.remainingUnits(),
                    evidence.claim.status(),
                    settlement.delivery().deliveredAt(), false);
            evidence.balances.put(
                    PlayerPaymentCommit.walletAccount(evidence.ownerId),
                    100L);
            return evidence;
        }

        @Override
        public Optional<LedgerTransaction> ledgerTransaction(
                UUID requested
        ) {
            return ledger != null && requestId.equals(requested)
                    ? Optional.of(ledger) : Optional.empty();
        }

        @Override
        public Optional<ClaimAttemptResult> claimAttempt(String requestKey) {
            return attempt != null && attempt.requestKey().equals(requestKey)
                    ? Optional.of(attempt) : Optional.empty();
        }

        @Override
        public Optional<EscrowClaim> claim(UUID requested) {
            return claim != null && claimId.equals(requested)
                    ? Optional.of(claim) : Optional.empty();
        }

        @Override
        public long balance(LedgerAccountId account) {
            return balances.getOrDefault(account, 0L);
        }
    }
}
