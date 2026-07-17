package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountType;
import com.enviouse.futureshops.server.escrow.ledger.LedgerLeg;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;
import com.enviouse.futureshops.server.escrow.model.EscrowAssetLot;
import com.enviouse.futureshops.server.escrow.model.EscrowAssetLotType;
import com.enviouse.futureshops.server.escrow.model.EscrowOperation;
import com.enviouse.futureshops.server.escrow.model.EscrowParticipant;
import com.enviouse.futureshops.server.escrow.model.EscrowParticipantRole;
import com.enviouse.futureshops.server.escrow.model.EscrowParty;
import com.enviouse.futureshops.server.escrow.model.EscrowProtectionLevel;
import com.enviouse.futureshops.server.escrow.model.EscrowRequestKey;
import com.enviouse.futureshops.server.escrow.model.EscrowState;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import com.enviouse.futureshops.server.escrow.model.EscrowTransactionId;
import com.enviouse.futureshops.server.escrow.model.MoneyAmount;
import com.enviouse.futureshops.server.security.ServerRequestSecurityManager;
import com.enviouse.futureshops.money.CurrencyManager;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EscrowCashDepositServiceTest {
    private static final UUID REQUEST_ID = UUID.fromString(
            "81000000-0000-0000-0000-000000000001");
    private static final UUID TRANSACTION_ID = UUID.fromString(
            "82000000-0000-0000-0000-000000000001");
    private static final UUID PLAYER_ID = UUID.fromString(
            "83000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse(
            "2026-07-17T20:00:00Z");
    private static final String REQUEST_KEY =
            "cash.deposit.test.81000000";
    private static final String CURRENCY_SIGNATURE = "a".repeat(64);

    @Test
    void replayClassifierSeparatesAbsentConflictRecoveryAndTerminals() {
        EscrowTransaction held = heldTransaction();

        assertEquals(EscrowCashDepositService.ReplayDisposition.ABSENT,
                EscrowCashDepositService.classifyReplay(
                        Optional.empty(), REQUEST_KEY));
        assertEquals(
                EscrowCashDepositService.ReplayDisposition.RECOVERY_REQUIRED,
                EscrowCashDepositService.classifyReplay(
                        Optional.of(held), REQUEST_KEY));
        assertEquals(
                EscrowCashDepositService.ReplayDisposition.REQUEST_CONFLICT,
                EscrowCashDepositService.classifyReplay(
                        Optional.of(held), REQUEST_KEY + ".changed"));
        assertEquals(EscrowCashDepositService.ReplayDisposition.COMPLETED,
                EscrowCashDepositService.classifyReplay(
                        Optional.of(completed(held)), REQUEST_KEY));
        assertEquals(EscrowCashDepositService.ReplayDisposition.CANCELLED,
                EscrowCashDepositService.classifyReplay(
                        Optional.of(refunded(held)), REQUEST_KEY));
        assertEquals(
                EscrowCashDepositService.ReplayDisposition.REQUEST_CONFLICT,
                EscrowCashDepositService.classifyReplay(
                        Optional.of(refunded(held)),
                        REQUEST_KEY + ".changed"));
    }

    @Test
    void canonicalRequestKeyBindsTheExactCurrencyCatalogSignature() {
        var first = new EscrowCashDepositService.DepositRequest(
                REQUEST_ID, "a".repeat(64),
                EscrowCashDepositService.Source.INVENTORY,
                OptionalLong.of(100L));
        var changed = new EscrowCashDepositService.DepositRequest(
                REQUEST_ID, "b".repeat(64),
                EscrowCashDepositService.Source.INVENTORY,
                OptionalLong.of(100L));

        assertFalse(EscrowCashDepositService.requestKey(
                PLAYER_ID, first).equals(
                EscrowCashDepositService.requestKey(
                        PLAYER_ID, changed)));
        assertThrows(IllegalArgumentException.class,
                () -> new EscrowCashDepositService.DepositRequest(
                        REQUEST_ID, "stale",
                        EscrowCashDepositService.Source.INVENTORY,
                        OptionalLong.of(100L)));
    }

    @Test
    void protectedDepositReloadWaitsThroughTheCompositeCommitWindow()
            throws Exception {
        assertConfigurationReloadWaitsForDepositWindow("protected");
    }

    @Test
    void foreignDepositReloadWaitsThroughTheCompositeCommitWindow()
            throws Exception {
        assertConfigurationReloadWaitsForDepositWindow("foreign");
    }

    @Test
    void matchingCompletedReplayReconstructsConservedTerminalResult() {
        EscrowTransaction transaction = completed(heldTransaction());
        LedgerTransaction ledger = new LedgerTransaction(
                TRANSACTION_ID, "cash.deposit.test.ledger", "deposit",
                List.of(
                        new LedgerLeg(new LedgerAccountId(
                                LedgerAccountType.FOREIGN_CURRENCY_SOURCE,
                                "source"), -100L),
                        new LedgerLeg(new LedgerAccountId(
                                LedgerAccountType.PLAYER_WALLET,
                                PLAYER_ID.toString()), 70L),
                        new LedgerLeg(new LedgerAccountId(
                                LedgerAccountType.PLAYER_CLAIM,
                                PLAYER_ID.toString()), 30L)));
        EscrowClaim claim = new EscrowClaim(
                UUID.fromString("84000000-0000-0000-0000-000000000001"),
                TRANSACTION_ID, PLAYER_ID, "cash.deposit.test.claim",
                ClaimKind.MONEY, 30L, 30L, new byte[0],
                ClaimStatus.PENDING, "Deposit overflow", NOW, NOW);
        var request = new EscrowCashDepositService.DepositRequest(
                REQUEST_ID, CURRENCY_SIGNATURE,
                EscrowCashDepositService.Source.INVENTORY,
                OptionalLong.of(100L));

        var result = EscrowCashDepositService.completedReplay(
                request, TRANSACTION_ID, transaction, ledger,
                List.of(claim), PLAYER_ID, 570L, true);

        assertEquals(EscrowCashDepositService.Status.SUCCESS,
                result.status());
        assertEquals(Optional.of(TRANSACTION_ID), result.transactionId());
        assertEquals(100L, result.depositedMinorUnits());
        assertEquals(2, result.itemsConsumed());
        assertEquals(70L, result.walletCreditMinorUnits());
        assertEquals(30L, result.overflowClaimMinorUnits());
        assertEquals(570L, result.resultingBalanceMinorUnits());
        assertTrue(result.cleanupPending());
        assertTrue(result.replayed());
    }

    @Test
    void completedReplayRejectsMissingOrConflictingEvidence() {
        EscrowTransaction transaction = completed(heldTransaction());
        LedgerTransaction insufficient = new LedgerTransaction(
                TRANSACTION_ID, "cash.deposit.test.bad.ledger", "deposit",
                List.of(
                        new LedgerLeg(new LedgerAccountId(
                                LedgerAccountType.FOREIGN_CURRENCY_SOURCE,
                                "source"), -99L),
                        new LedgerLeg(new LedgerAccountId(
                                LedgerAccountType.PLAYER_WALLET,
                                PLAYER_ID.toString()), 99L)));
        var request = new EscrowCashDepositService.DepositRequest(
                REQUEST_ID, CURRENCY_SIGNATURE,
                EscrowCashDepositService.Source.INVENTORY,
                OptionalLong.of(100L));

        assertThrows(EscrowRuntimeException.class,
                () -> EscrowCashDepositService.completedReplay(
                        request, TRANSACTION_ID, transaction, insufficient,
                        List.of(), PLAYER_ID, 599L, false));
    }

    @Test
    void zeroIdentifiersAndTerminalFailureShapesFailClosed() {
        UUID zero = new UUID(0L, 0L);

        assertThrows(IllegalArgumentException.class,
                () -> new EscrowCashDepositService.DepositRequest(
                        zero, CURRENCY_SIGNATURE,
                        EscrowCashDepositService.Source.INVENTORY,
                        OptionalLong.empty()));
        assertThrows(IllegalArgumentException.class,
                () -> new EscrowCashDepositService.DepositResult(
                        EscrowCashDepositService.Status.CANCELLED,
                        REQUEST_ID, Optional.empty(), 0L, 0,
                        0L, 0L, 0L, false, false, Optional.empty(), 0L));
        assertThrows(IllegalArgumentException.class,
                () -> new EscrowCashDepositService.DepositResult(
                        EscrowCashDepositService.Status.REQUEST_CONFLICT,
                        REQUEST_ID, Optional.of(zero), 0L, 0,
                        0L, 0L, 0L, false, false, Optional.empty(), 0L));

        var cancelled = new EscrowCashDepositService.DepositResult(
                EscrowCashDepositService.Status.CANCELLED,
                REQUEST_ID, Optional.of(TRANSACTION_ID), 0L, 0,
                0L, 0L, 0L, false, false, Optional.empty(), 0L);
        assertFalse(cancelled.successful());
        assertFalse(cancelled.replayed());
    }

    @Test
    void rateLimitResultsCarryBoundedExactRetryTiming() {
        var rateLimited = new EscrowCashDepositService.DepositResult(
                EscrowCashDepositService.Status.RATE_LIMITED,
                REQUEST_ID, Optional.empty(), 0L, 0, 0L, 0L,
                0L, false, false, Optional.empty(), 1_001L);

        assertTrue(rateLimited.retryable());
        assertEquals(1_001L, rateLimited.retryAfterMillis());
        assertEquals(2L, rateLimited.retryAfterSeconds());
        assertThrows(IllegalArgumentException.class,
                () -> new EscrowCashDepositService.DepositResult(
                        EscrowCashDepositService.Status.RATE_LIMITED,
                        REQUEST_ID, Optional.empty(), 0L, 0, 0L, 0L,
                        0L, false, false, Optional.empty(), 0L));
        assertThrows(IllegalArgumentException.class,
                () -> new EscrowCashDepositService.DepositResult(
                        EscrowCashDepositService.Status.NO_CURRENCY,
                        REQUEST_ID, Optional.empty(), 0L, 0, 0L, 0L,
                        0L, false, false, Optional.empty(), 1L));
        assertThrows(IllegalArgumentException.class,
                () -> new EscrowCashDepositService.DepositResult(
                        EscrowCashDepositService.Status.RATE_LIMITED,
                        REQUEST_ID, Optional.empty(), 0L, 0, 0L, 0L,
                        0L, false, false, Optional.empty(),
                        Math.addExact(
                                EscrowCashDepositService
                                        .MAX_RETRY_AFTER_MILLIS,
                                1L)));
    }

    @Test
    void gateRejectionRoundsUpAndCapsRetryTiming() {
        var request = new EscrowCashDepositService.DepositRequest(
                REQUEST_ID, CURRENCY_SIGNATURE,
                EscrowCashDepositService.Source.INVENTORY,
                OptionalLong.empty());
        var rounded = new ServerRequestSecurityManager.GateDecision(
                false,
                ServerRequestSecurityManager.GateStatus.RATE_LIMITED,
                Duration.ofNanos(1_000_001L));
        var capped = new ServerRequestSecurityManager.GateDecision(
                false,
                ServerRequestSecurityManager.GateStatus.RATE_LIMITED,
                Duration.ofSeconds(Long.MAX_VALUE));
        var unavailable = new ServerRequestSecurityManager.GateDecision(
                false, ServerRequestSecurityManager.GateStatus.UNAVAILABLE,
                Duration.ZERO);

        assertEquals(2L, EscrowCashDepositService.gateFailure(
                request, rounded).retryAfterMillis());
        assertEquals(EscrowCashDepositService.MAX_RETRY_AFTER_MILLIS,
                EscrowCashDepositService.gateFailure(
                        request, capped).retryAfterMillis());
        assertEquals(EscrowCashDepositService.Status.ESCROW_UNAVAILABLE,
                EscrowCashDepositService.gateFailure(
                        request, unavailable).status());
    }

    @Test
    void itemCountPreflightUsesExactBoundedIntAccumulation() {
        assertEquals(EscrowCashDepositService.MAX_ITEMS_CONSUMED,
                EscrowCashDepositService.boundedItemCount(
                        List.of(100_000, 31_072), Integer::intValue)
                        .orElseThrow());
        assertTrue(EscrowCashDepositService.boundedItemCount(
                List.of(100_000, 31_073), Integer::intValue).isEmpty());
        assertTrue(EscrowCashDepositService.boundedItemCount(
                List.of(Integer.MAX_VALUE, 1), Integer::intValue).isEmpty());
        assertTrue(EscrowCashDepositService.boundedItemCount(
                List.of(1, 0), Integer::intValue).isEmpty());
    }

    @Test
    void resolvedCancellationCarriesTheDurableTransactionIdentity() {
        RuntimeException original = new RuntimeException("failure");
        CashDepositCancellationCompletedException resolved =
                new CashDepositCancellationCompletedException(
                        TRANSACTION_ID, original);

        assertEquals(TRANSACTION_ID, resolved.transactionId());
        assertEquals(original, resolved.getCause());
        assertThrows(IllegalArgumentException.class,
                () -> new CashDepositCancellationCompletedException(
                        new UUID(0L, 0L), original));
    }

    @Test
    void completedStateWinsPostCommitFailuresOrRequiresRecovery() {
        var request = new EscrowCashDepositService.DepositRequest(
                REQUEST_ID, CURRENCY_SIGNATURE,
                EscrowCashDepositService.Source.INVENTORY,
                OptionalLong.of(100L));
        var committedSuccess = new EscrowCashDepositService.DepositResult(
                EscrowCashDepositService.Status.SUCCESS, REQUEST_ID,
                Optional.of(TRANSACTION_ID), 100L, 2, 70L, 30L,
                570L, false, true, Optional.empty(), 0L);

        var resolved = EscrowCashDepositService
                .resolveFailureDisposition(request, TRANSACTION_ID,
                        EscrowCashDepositService.ReplayDisposition.COMPLETED,
                        EscrowCashDepositService.Status.INVALID_CURRENCY,
                        Optional.empty(), () -> committedSuccess);
        var unavailableEvidence = EscrowCashDepositService
                .resolveFailureDisposition(request, TRANSACTION_ID,
                        EscrowCashDepositService.ReplayDisposition.COMPLETED,
                        EscrowCashDepositService.Status.INVALID_CURRENCY,
                        Optional.empty(), () -> {
                            throw new IllegalStateException(
                                    "balance unavailable");
                        });
        var cancelled = EscrowCashDepositService
                .resolveFailureDisposition(request, TRANSACTION_ID,
                        EscrowCashDepositService.ReplayDisposition.CANCELLED,
                        EscrowCashDepositService.Status.RECOVERY_REQUIRED,
                        Optional.of(TRANSACTION_ID), () -> committedSuccess);

        assertSame(committedSuccess, resolved);
        assertEquals(EscrowCashDepositService.Status.RECOVERY_REQUIRED,
                unavailableEvidence.status());
        assertEquals(Optional.of(TRANSACTION_ID),
                unavailableEvidence.transactionId());
        assertEquals(EscrowCashDepositService.Status.CANCELLED,
                cancelled.status());
    }

    private static EscrowTransaction heldTransaction() {
        EscrowParty player = EscrowParty.player(PLAYER_ID);
        EscrowParty system = EscrowParty.system("cash_deposit_test");
        Set<EscrowParticipant> participants = Set.of(
                new EscrowParticipant(player, Set.of(
                        EscrowParticipantRole.INITIATOR,
                        EscrowParticipantRole.PAYER)),
                new EscrowParticipant(system, Set.of(
                        EscrowParticipantRole.BENEFICIARY,
                        EscrowParticipantRole.CUSTODIAN)));
        EscrowAssetLot asset = new EscrowAssetLot(
                UUID.fromString("85000000-0000-0000-0000-000000000001"),
                EscrowAssetLotType.PROTECTED_PHYSICAL_CURRENCY,
                EscrowProtectionLevel.PROTECTED, player, system, 2L,
                Optional.of(new MoneyAmount("futureshops:credits", 100L)),
                "protected cash".getBytes(StandardCharsets.UTF_8),
                Map.of());
        return EscrowTransaction.create(
                        new EscrowTransactionId(TRANSACTION_ID),
                        Optional.empty(), new EscrowRequestKey(REQUEST_KEY),
                        EscrowOperation.CURRENCY_DEPOSIT, participants,
                        List.of(asset), NOW, 1L, Optional.empty())
                .transitionTo(EscrowState.VALIDATED, NOW.plusSeconds(1))
                .transitionTo(EscrowState.HOLDING, NOW.plusSeconds(2))
                .transitionTo(EscrowState.HELD, NOW.plusSeconds(3));
    }

    private static void assertConfigurationReloadWaitsForDepositWindow(
            String route
    ) throws Exception {
        CountDownLatch writerStarted = new CountDownLatch(1);
        CountDownLatch writerEntered = new CountDownLatch(1);
        Thread writer = new Thread(() -> {
            writerStarted.countDown();
            CurrencyManager.withConfigurationWriteLock(
                    writerEntered::countDown);
        });

        String completedRoute = EscrowCashDepositService
                .withConfigurationReadLease(() -> {
                    writer.start();
                    assertTrue(await(writerStarted, 5_000L));
                    assertFalse(await(writerEntered, 100L));
                    return route;
                });

        assertEquals(route, completedRoute);
        assertTrue(writerEntered.await(5L, TimeUnit.SECONDS));
        writer.join(TimeUnit.SECONDS.toMillis(5L));
        assertFalse(writer.isAlive());
    }

    private static boolean await(
            CountDownLatch latch,
            long timeoutMillis
    ) {
        try {
            return latch.await(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private static EscrowTransaction completed(
            EscrowTransaction held
    ) {
        return held.transitionTo(EscrowState.COMMIT_DECIDED,
                        NOW.plusSeconds(4))
                .transitionTo(EscrowState.COMMITTED, NOW.plusSeconds(5))
                .transitionTo(EscrowState.CLAIMS_CREATED,
                        NOW.plusSeconds(6))
                .transitionTo(EscrowState.COMPLETED, NOW.plusSeconds(7));
    }

    private static EscrowTransaction refunded(
            EscrowTransaction held
    ) {
        return held.transitionTo(EscrowState.ABORTING, NOW.plusSeconds(4))
                .transitionTo(EscrowState.REFUND_PENDING,
                        NOW.plusSeconds(5))
                .transitionTo(EscrowState.REFUNDED, NOW.plusSeconds(6));
    }
}
