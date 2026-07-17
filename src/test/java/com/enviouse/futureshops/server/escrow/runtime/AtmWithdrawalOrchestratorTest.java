package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.Config;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.model.EscrowState;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtmWithdrawalOrchestratorTest {
    private static final UUID REQUEST_ID = UUID.fromString(
            "77000000-0000-0000-0000-000000000001");
    private static final UUID PLAYER_ID = UUID.fromString(
            "78000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse(
            "2026-07-18T16:00:00.123456789Z");
    private static final Clock CLOCK = Clock.fixed(
            NOW.plusSeconds(5), ZoneOffset.UTC);
    private static final String SIGNATURE = "b".repeat(64);
    private static final List<Integer> COUNTS = List.of(2, 4);

    @Test
    void newRequestCommitsOnceCompletesAndReplaysWithoutEvents() {
        withMintConfiguration(() -> {
            FakeBackend backend = new FakeBackend(10_000L);
            FakeEvents events = new FakeEvents();
            AtmWithdrawalOrchestrator orchestrator = orchestrator(
                    backend, events);
            AtomicInteger preparations = new AtomicInteger();

            AtmWithdrawalOutcome first = orchestrator.submit(
                    REQUEST_ID, PLAYER_ID, SIGNATURE, COUNTS, () -> {
                        preparations.incrementAndGet();
                        return protectedPlan();
                    });

            assertEquals(AtmWithdrawalStatus.CLAIMED, first.status());
            assertFalse(first.replayed());
            assertEquals(9600L, first.balanceMinorUnits());
            assertEquals(400L, first.amountMinorUnits());
            assertEquals(6, first.claimedBillCount());
            assertEquals(EscrowState.COMPLETED,
                    backend.transaction(REQUEST_ID).orElseThrow().state());
            assertEquals(1, events.preCount);
            assertEquals(1, events.postCount);
            assertEquals(1, preparations.get());

            AtmWithdrawalOutcome replay = orchestrator.submit(
                    REQUEST_ID, PLAYER_ID, SIGNATURE, COUNTS, () -> {
                        preparations.incrementAndGet();
                        return protectedPlan();
                    });

            assertEquals(AtmWithdrawalStatus.CLAIMED, replay.status());
            assertTrue(replay.replayed());
            assertEquals(9600L, replay.balanceMinorUnits());
            assertEquals(1, events.preCount);
            assertEquals(1, events.postCount);
            assertEquals(1, preparations.get());
        });
    }

    @Test
    void completedReplayIgnoresLaterCurrencyChanges() {
        withMintConfiguration(() -> {
            FakeBackend backend = new FakeBackend(10_000L);
            FakeEvents events = new FakeEvents();
            AtomicReference<String> current = new AtomicReference<>(
                    SIGNATURE);
            AtmWithdrawalOrchestrator orchestrator = orchestrator(
                    backend, events, current::get);
            AtmWithdrawalOutcome first = orchestrator.submit(
                    REQUEST_ID, PLAYER_ID, SIGNATURE, COUNTS,
                    AtmWithdrawalOrchestratorTest::protectedPlan);
            current.set("c".repeat(64));

            AtmWithdrawalOutcome replay = orchestrator.submit(
                    REQUEST_ID, PLAYER_ID, SIGNATURE, COUNTS,
                    () -> {
                        throw new AssertionError();
                    });

            assertEquals(AtmWithdrawalStatus.CLAIMED, first.status());
            assertEquals(AtmWithdrawalStatus.CLAIMED, replay.status());
            assertTrue(replay.replayed());
            assertEquals(1, events.preCount);
            assertEquals(1, events.postCount);
            assertEquals(9600L, backend.balance);
        });
    }

    @Test
    void changedRetryConflictsBeforeConfigFundsOrEvents() {
        withMintConfiguration(() -> {
            FakeBackend backend = new FakeBackend(10_000L);
            FakeEvents events = new FakeEvents();
            AtmWithdrawalOrchestrator orchestrator = orchestrator(
                    backend, events);
            orchestrator.submit(REQUEST_ID, PLAYER_ID, SIGNATURE,
                    COUNTS, AtmWithdrawalOrchestratorTest::protectedPlan);
            backend.balanceReadable = false;
            AtomicInteger preparations = new AtomicInteger();

            AtmWithdrawalOutcome changedCounts = orchestrator.submit(
                    REQUEST_ID, PLAYER_ID, SIGNATURE, List.of(3, 3), () -> {
                        preparations.incrementAndGet();
                        return protectedPlan();
                    });
            AtmWithdrawalOutcome changedSignature = orchestrator.submit(
                    REQUEST_ID, PLAYER_ID, "c".repeat(64), COUNTS, () -> {
                        preparations.incrementAndGet();
                        return protectedPlan();
                    });

            assertEquals(AtmWithdrawalStatus.CONFLICT,
                    changedCounts.status());
            assertEquals(AtmWithdrawalStatus.CONFLICT,
                    changedSignature.status());
            assertFalse(changedCounts.balanceKnown());
            assertEquals(0, preparations.get());
            assertEquals(1, events.preCount);
            assertEquals(1, events.postCount);
        });
    }

    @Test
    void migrationAndRuntimeGatesLeaveNoDurableState() {
        FakeBackend backend = new FakeBackend(10_000L);
        FakeEvents events = new FakeEvents();
        AtmWithdrawalOrchestrator orchestrator = orchestrator(
                backend, events);
        backend.migrationComplete = false;

        AtmWithdrawalOutcome migration = orchestrator.submit(
                REQUEST_ID, PLAYER_ID, SIGNATURE, COUNTS,
                () -> {
                    throw new AssertionError();
                });
        assertEquals(AtmWithdrawalStatus.MIGRATION_PENDING,
                migration.status());

        backend.migrationComplete = true;
        backend.runtimeState = EscrowRuntimeState.RECOVERING;
        AtmWithdrawalOutcome recovery = orchestrator.submit(
                REQUEST_ID, PLAYER_ID, SIGNATURE, COUNTS,
                () -> {
                    throw new AssertionError();
                });
        assertEquals(AtmWithdrawalStatus.RECOVERY_PENDING,
                recovery.status());
        assertTrue(backend.transactions.isEmpty());
        assertEquals(0, events.preCount);
    }

    @Test
    void invalidPlanInsufficientFundsAndCancellationNeverCreateEscrow() {
        withMintConfiguration(() -> {
            FakeBackend invalidBackend = new FakeBackend(10_000L);
            FakeEvents invalidEvents = new FakeEvents();
            AtmWithdrawalOutcome invalid = orchestrator(
                    invalidBackend, invalidEvents).submit(
                    REQUEST_ID, PLAYER_ID, SIGNATURE, COUNTS,
                    () -> {
                        throw new AtmPreparationException(
                                AtmWithdrawalStatus.CURRENCY_CHANGED,
                                "changed");
                    });
            assertEquals(AtmWithdrawalStatus.CURRENCY_CHANGED,
                    invalid.status());
            assertTrue(invalidBackend.transactions.isEmpty());

            FakeBackend poorBackend = new FakeBackend(399L);
            FakeEvents poorEvents = new FakeEvents();
            AtmWithdrawalOutcome poor = orchestrator(
                    poorBackend, poorEvents).submit(
                    REQUEST_ID, PLAYER_ID, SIGNATURE, COUNTS,
                    AtmWithdrawalOrchestratorTest::protectedPlan);
            assertEquals(AtmWithdrawalStatus.INSUFFICIENT_FUNDS,
                    poor.status());
            assertTrue(poorBackend.transactions.isEmpty());
            assertEquals(0, poorEvents.preCount);

            FakeBackend cancelledBackend = new FakeBackend(10_000L);
            FakeEvents cancelledEvents = new FakeEvents();
            cancelledEvents.cancel = true;
            AtmWithdrawalOutcome cancelled = orchestrator(
                    cancelledBackend, cancelledEvents).submit(
                    REQUEST_ID, PLAYER_ID, SIGNATURE, COUNTS,
                    AtmWithdrawalOrchestratorTest::protectedPlan);
            assertEquals(AtmWithdrawalStatus.CANCELLED,
                    cancelled.status());
            assertTrue(cancelledBackend.transactions.isEmpty());
            assertEquals(1, cancelledEvents.preCount);
            assertEquals(0, cancelledEvents.postCount);
        });
    }

    @Test
    void currencyChangedByPreEventNeverCreatesEscrowOrDebits() {
        withMintConfiguration(() -> {
            FakeBackend backend = new FakeBackend(10_000L);
            FakeEvents events = new FakeEvents();
            AtomicReference<String> current = new AtomicReference<>(
                    SIGNATURE);
            events.beforeAction = () -> current.set("c".repeat(64));
            AtmWithdrawalOrchestrator orchestrator = orchestrator(
                    backend, events, current::get);

            AtmWithdrawalOutcome result = orchestrator.submit(
                    REQUEST_ID, PLAYER_ID, SIGNATURE, COUNTS,
                    AtmWithdrawalOrchestratorTest::protectedPlan);

            assertEquals(AtmWithdrawalStatus.CURRENCY_CHANGED,
                    result.status());
            assertEquals(10_000L, backend.balance);
            assertTrue(backend.transactions.isEmpty());
            assertTrue(backend.claims.isEmpty());
            assertEquals(1, events.preCount);
            assertEquals(0, events.postCount);
        });
    }

    @Test
    void sameSignatureReloadBetweenPreparationAndCommitIsRejected() {
        withMintConfiguration(() -> {
            FakeBackend backend = new FakeBackend(10_000L);
            FakeEvents events = new FakeEvents();
            TestCurrencyGate gate = new TestCurrencyGate(SIGNATURE);
            events.beforeAction = () -> gate.reload(SIGNATURE);
            AtmWithdrawalOrchestrator orchestrator =
                    new AtmWithdrawalOrchestrator(
                            backend, events, CLOCK, gate::acquire);

            AtmWithdrawalOutcome result = orchestrator.submit(
                    REQUEST_ID, PLAYER_ID, SIGNATURE, COUNTS,
                    AtmWithdrawalOrchestratorTest::protectedPlan);

            assertEquals(AtmWithdrawalStatus.CURRENCY_CHANGED,
                    result.status());
            assertEquals(10_000L, backend.balance);
            assertTrue(backend.transactions.isEmpty());
            assertTrue(backend.claims.isEmpty());
            assertEquals(1, events.preCount);
            assertEquals(0, events.postCount);
        });
    }

    @Test
    void finalCurrencyCheckRunsImmediatelyBeforeHeldPersistence() {
        withMintConfiguration(() -> {
            FakeBackend backend = new FakeBackend(10_000L);
            FakeEvents events = new FakeEvents();
            AtomicInteger reads = new AtomicInteger();
            AtmWithdrawalOrchestrator orchestrator = orchestrator(
                    backend, events, () -> reads.incrementAndGet() == 1
                            ? SIGNATURE : "d".repeat(64));

            AtmWithdrawalOutcome result = orchestrator.submit(
                    REQUEST_ID, PLAYER_ID, SIGNATURE, COUNTS,
                    AtmWithdrawalOrchestratorTest::protectedPlan);

            assertEquals(AtmWithdrawalStatus.CURRENCY_CHANGED,
                    result.status());
            assertEquals(2, reads.get());
            assertEquals(10_000L, backend.balance);
            assertTrue(backend.transactions.isEmpty());
            assertTrue(backend.claims.isEmpty());
            assertEquals(1, events.preCount);
            assertEquals(0, events.postCount);
        });
    }

    @Test
    void reloadDuringHeldPersistenceWaitsThroughCompositeCommit()
            throws Exception {
        withMintConfiguration(() -> {
            FakeBackend backend = new FakeBackend(10_000L);
            FakeEvents events = new FakeEvents();
            TestCurrencyGate gate = new TestCurrencyGate(SIGNATURE);
            AtomicBoolean started = new AtomicBoolean();
            AtomicBoolean reloadCompleted = new AtomicBoolean();
            AtomicReference<Thread> reloadThread = new AtomicReference<>();
            backend.afterTransactionCommit = state -> {
                if (state != EscrowState.HOLDING
                        || !started.compareAndSet(false, true)) {
                    return;
                }
                Thread writer = startReload(
                        gate, reloadCompleted, "c".repeat(64));
                reloadThread.set(writer);
                assertTrue(gate.awaitQueued(writer));
                assertFalse(reloadCompleted.get());
            };
            backend.beforeComposite = () -> {
                Thread writer = reloadThread.get();
                assertTrue(writer != null && gate.isQueued(writer));
                assertFalse(reloadCompleted.get());
            };

            AtmWithdrawalOrchestrator orchestrator =
                    new AtmWithdrawalOrchestrator(
                            backend, events, CLOCK, gate::acquire);
            AtmWithdrawalOutcome result = orchestrator.submit(
                    REQUEST_ID, PLAYER_ID, SIGNATURE, COUNTS,
                    AtmWithdrawalOrchestratorTest::protectedPlan);
            join(reloadThread.get());

            assertEquals(AtmWithdrawalStatus.CLAIMED,
                    result.status());
            assertTrue(reloadCompleted.get());
            assertEquals("c".repeat(64), gate.signature());
            assertEquals(9600L, backend.balance);
            assertFalse(backend.claims(REQUEST_ID).isEmpty());
            AtmWithdrawalOutcome replay = orchestrator.submit(
                    REQUEST_ID, PLAYER_ID, SIGNATURE, COUNTS,
                    () -> {
                        throw new AssertionError();
                    });
            assertEquals(AtmWithdrawalStatus.CLAIMED, replay.status());
            assertTrue(replay.replayed());
            assertEquals(9600L, backend.balance);
        });
    }

    @Test
    void reloadImmediatelyBeforeCompositeWaitsForDecision()
            throws Exception {
        withMintConfiguration(() -> {
            FakeBackend backend = new FakeBackend(10_000L);
            TestCurrencyGate gate = new TestCurrencyGate(SIGNATURE);
            AtomicBoolean reloadCompleted = new AtomicBoolean();
            AtomicReference<Thread> reloadThread = new AtomicReference<>();
            backend.beforeComposite = () -> {
                Thread writer = startReload(
                        gate, reloadCompleted, "d".repeat(64));
                reloadThread.set(writer);
                assertTrue(gate.awaitQueued(writer));
                assertFalse(reloadCompleted.get());
            };

            AtmWithdrawalOutcome result =
                    new AtmWithdrawalOrchestrator(
                            backend, new FakeEvents(), CLOCK,
                            gate::acquire).submit(
                            REQUEST_ID, PLAYER_ID, SIGNATURE, COUNTS,
                            AtmWithdrawalOrchestratorTest::protectedPlan);
            join(reloadThread.get());

            assertEquals(AtmWithdrawalStatus.CLAIMED,
                    result.status());
            assertTrue(reloadCompleted.get());
            assertEquals("d".repeat(64), gate.signature());
            assertEquals(9600L, backend.balance);
            assertFalse(backend.claims(REQUEST_ID).isEmpty());
        });
    }

    @Test
    void knownCompositeFailureDurablyAbortsWithoutDebiting() {
        withMintConfiguration(() -> {
            FakeBackend backend = new FakeBackend(10_000L);
            backend.failCompositeBeforeMutation = true;
            FakeEvents events = new FakeEvents();

            AtmWithdrawalOutcome result = orchestrator(
                    backend, events).submit(
                    REQUEST_ID, PLAYER_ID, SIGNATURE, COUNTS,
                    AtmWithdrawalOrchestratorTest::protectedPlan);
            AtmWithdrawalOutcome replay = orchestrator(
                    backend, events).submit(
                    REQUEST_ID, PLAYER_ID, SIGNATURE, COUNTS,
                    () -> {
                        throw new AssertionError();
                    });

            assertEquals(AtmWithdrawalStatus.SERVER_ERROR,
                    result.status());
            assertEquals(AtmWithdrawalStatus.CANCELLED,
                    replay.status());
            assertTrue(replay.replayed());
            assertFalse(result.retryable());
            assertEquals(10_000L, backend.balance);
            assertEquals(EscrowState.REFUNDED,
                    backend.transaction(REQUEST_ID).orElseThrow().state());
            assertTrue(backend.claims(REQUEST_ID).isEmpty());
            assertEquals(1, events.preCount);
            assertEquals(0, events.postCount);
        });
    }

    @Test
    void postdecisionProgressFailureReturnsRecoveryWithoutSecondDebit() {
        withMintConfiguration(() -> {
            FakeBackend backend = new FakeBackend(10_000L);
            backend.failState = EscrowState.COMMITTED;
            FakeEvents events = new FakeEvents();
            AtmWithdrawalOrchestrator orchestrator = orchestrator(
                    backend, events);

            AtmWithdrawalOutcome first = orchestrator.submit(
                    REQUEST_ID, PLAYER_ID, SIGNATURE, COUNTS,
                    AtmWithdrawalOrchestratorTest::protectedPlan);
            AtmWithdrawalOutcome retry = orchestrator.submit(
                    REQUEST_ID, PLAYER_ID, SIGNATURE, COUNTS,
                    () -> {
                        throw new AssertionError();
                    });

            assertEquals(AtmWithdrawalStatus.RECOVERY_PENDING,
                    first.status());
            assertEquals(AtmWithdrawalStatus.RECOVERY_PENDING,
                    retry.status());
            assertTrue(retry.replayed());
            assertEquals(9600L, backend.balance);
            assertEquals(EscrowState.COMMIT_DECIDED,
                    backend.transaction(REQUEST_ID).orElseThrow().state());
            assertEquals(1, events.preCount);
            assertEquals(1, events.postCount);
        });
    }

    @Test
    void completedRetryReportsDeliveredAndPartialClaimCounts() {
        withMintConfiguration(() -> {
            FakeBackend backend = new FakeBackend(10_000L);
            AtmWithdrawalOrchestrator orchestrator = orchestrator(
                    backend, new FakeEvents());
            orchestrator.submit(REQUEST_ID, PLAYER_ID, SIGNATURE,
                    COUNTS, AtmWithdrawalOrchestratorTest::protectedPlan);
            List<EscrowClaim> pending = backend.claims(REQUEST_ID);
            int firstClaimBills = ProtectedCashClaimPayloadCodec.decode(
                    pending.get(0).payload()).billCount();
            backend.claims.put(REQUEST_ID, List.of(
                    pending.get(0).deliver(
                            pending.get(0).remainingUnits(),
                            NOW.plusSeconds(10)),
                    pending.get(1)));

            AtmWithdrawalOutcome partial = orchestrator.submit(
                    REQUEST_ID, PLAYER_ID, SIGNATURE, COUNTS,
                    () -> {
                        throw new AssertionError();
                    });
            assertEquals(AtmWithdrawalStatus.PARTIALLY_DELIVERED,
                    partial.status());
            assertEquals(firstClaimBills,
                    partial.deliveredBillCount());
            assertEquals(6 - firstClaimBills,
                    partial.claimedBillCount());

            backend.claims.put(REQUEST_ID,
                    backend.claims(REQUEST_ID).stream()
                            .map(claim -> claim.remainingUnits() == 0L
                                    ? claim
                                    : claim.deliver(
                                    claim.remainingUnits(),
                                    NOW.plusSeconds(20)))
                            .toList());
            AtmWithdrawalOutcome delivered = orchestrator.submit(
                    REQUEST_ID, PLAYER_ID, SIGNATURE, COUNTS,
                    () -> {
                        throw new AssertionError();
                    });
            assertEquals(AtmWithdrawalStatus.DELIVERED,
                    delivered.status());
            assertEquals(6, delivered.deliveredBillCount());
            assertEquals(0, delivered.claimedBillCount());
        });
    }

    @Test
    void partialPhysicalStackClaimFailsClosed() {
        withMintConfiguration(() -> {
            FakeBackend backend = new FakeBackend(10_000L);
            AtmWithdrawalOrchestrator orchestrator = orchestrator(
                    backend, new FakeEvents());
            orchestrator.submit(REQUEST_ID, PLAYER_ID, SIGNATURE,
                    COUNTS, AtmWithdrawalOrchestratorTest::protectedPlan);
            List<EscrowClaim> pending = backend.claims(REQUEST_ID);
            ProtectedCashClaimPayload payload =
                    ProtectedCashClaimPayloadCodec.decode(
                            pending.get(0).payload());
            backend.claims.put(REQUEST_ID, List.of(
                    pending.get(0).deliver(
                            payload.denominationMinorUnits(),
                            NOW.plusSeconds(10)), pending.get(1)));

            AtmWithdrawalOutcome result = orchestrator.submit(
                    REQUEST_ID, PLAYER_ID, SIGNATURE, COUNTS,
                    () -> {
                        throw new AssertionError();
                    });

            assertEquals(AtmWithdrawalStatus.SERVER_ERROR,
                    result.status());
        });
    }

    @Test
    void completedReplayWithMissingCashClaimsFailsClosed() {
        withMintConfiguration(() -> {
            FakeBackend backend = new FakeBackend(10_000L);
            AtmWithdrawalOrchestrator orchestrator = orchestrator(
                    backend, new FakeEvents());
            orchestrator.submit(REQUEST_ID, PLAYER_ID, SIGNATURE,
                    COUNTS, AtmWithdrawalOrchestratorTest::protectedPlan);
            backend.claims.put(REQUEST_ID, List.of());

            AtmWithdrawalOutcome result = orchestrator.submit(
                    REQUEST_ID, PLAYER_ID, SIGNATURE, COUNTS,
                    () -> {
                        throw new AssertionError();
                    });

            assertEquals(AtmWithdrawalStatus.SERVER_ERROR,
                    result.status());
        });
    }

    @Test
    void predecisionAndRefundedTransactionsRejectCashClaims() {
        withMintConfiguration(() -> {
            AtmPreparedWithdrawal prepared = protectedPlan();
            List<EscrowClaim> cashClaims = prepared.protectedCommit()
                    .orElseThrow().cashClaims();

            FakeBackend heldBackend = new FakeBackend(10_000L);
            heldBackend.transactions.put(
                    REQUEST_ID, prepared.heldTransaction());
            heldBackend.claims.put(REQUEST_ID, cashClaims);
            AtmWithdrawalOutcome held = orchestrator(
                    heldBackend, new FakeEvents()).submit(
                    REQUEST_ID, PLAYER_ID, SIGNATURE, COUNTS,
                    () -> {
                        throw new AssertionError();
                    });
            assertEquals(AtmWithdrawalStatus.SERVER_ERROR, held.status());

            FakeBackend refundedBackend = new FakeBackend(10_000L);
            refundedBackend.failCompositeBeforeMutation = true;
            AtmWithdrawalOrchestrator refundedOrchestrator = orchestrator(
                    refundedBackend, new FakeEvents());
            refundedOrchestrator.submit(
                    REQUEST_ID, PLAYER_ID, SIGNATURE, COUNTS,
                    AtmWithdrawalOrchestratorTest::protectedPlan);
            refundedBackend.claims.put(REQUEST_ID, cashClaims);
            AtmWithdrawalOutcome refunded = refundedOrchestrator.submit(
                    REQUEST_ID, PLAYER_ID, SIGNATURE, COUNTS,
                    () -> {
                        throw new AssertionError();
                    });
            assertEquals(AtmWithdrawalStatus.SERVER_ERROR,
                    refunded.status());
        });
    }

    @Test
    void completedReplayWithUnderValuedCashClaimsFailsClosed() {
        withMintConfiguration(() -> {
            FakeBackend backend = new FakeBackend(10_000L);
            AtmWithdrawalOrchestrator orchestrator = orchestrator(
                    backend, new FakeEvents());
            orchestrator.submit(REQUEST_ID, PLAYER_ID, SIGNATURE,
                    COUNTS, AtmWithdrawalOrchestratorTest::protectedPlan);
            List<EscrowClaim> pending = backend.claims(REQUEST_ID);
            ProtectedCashClaimPayload firstPayload =
                    ProtectedCashClaimPayloadCodec.decode(
                            pending.get(0).payload());
            backend.claims.put(REQUEST_ID, List.of(
                    reshapedCashClaim(pending.get(0),
                            firstPayload.denominationMinorUnits() / 2L,
                            firstPayload.billCount()),
                    pending.get(1)));

            AtmWithdrawalOutcome result = orchestrator.submit(
                    REQUEST_ID, PLAYER_ID, SIGNATURE, COUNTS,
                    () -> {
                        throw new AssertionError();
                    });

            assertEquals(AtmWithdrawalStatus.SERVER_ERROR,
                    result.status());
        });
    }

    @Test
    void completedReplayWithWrongCashBillTotalFailsClosed() {
        withMintConfiguration(() -> {
            FakeBackend backend = new FakeBackend(10_000L);
            AtmWithdrawalOrchestrator orchestrator = orchestrator(
                    backend, new FakeEvents());
            orchestrator.submit(REQUEST_ID, PLAYER_ID, SIGNATURE,
                    COUNTS, AtmWithdrawalOrchestratorTest::protectedPlan);
            List<EscrowClaim> pending = backend.claims(REQUEST_ID);
            ProtectedCashClaimPayload firstPayload =
                    ProtectedCashClaimPayloadCodec.decode(
                            pending.get(0).payload());
            backend.claims.put(REQUEST_ID, List.of(
                    reshapedCashClaim(pending.get(0),
                            Math.multiplyExact(
                                    firstPayload.denominationMinorUnits(),
                                    2L),
                            firstPayload.billCount() / 2),
                    pending.get(1)));

            AtmWithdrawalOutcome result = orchestrator.submit(
                    REQUEST_ID, PLAYER_ID, SIGNATURE, COUNTS,
                    () -> {
                        throw new AssertionError();
                    });

            assertEquals(AtmWithdrawalStatus.SERVER_ERROR,
                    result.status());
        });
    }

    @Test
    void quarantinedCashClaimIsNeverReportedAsClaimable() {
        withMintConfiguration(() -> {
            FakeBackend backend = new FakeBackend(10_000L);
            AtmWithdrawalOrchestrator orchestrator = orchestrator(
                    backend, new FakeEvents());
            orchestrator.submit(REQUEST_ID, PLAYER_ID, SIGNATURE,
                    COUNTS, AtmWithdrawalOrchestratorTest::protectedPlan);
            List<EscrowClaim> pending = backend.claims(REQUEST_ID);
            backend.claims.put(REQUEST_ID, List.of(
                    pending.get(0).quarantine(NOW.plusSeconds(10)),
                    pending.get(1)));

            AtmWithdrawalOutcome result = orchestrator.submit(
                    REQUEST_ID, PLAYER_ID, SIGNATURE, COUNTS,
                    () -> {
                        throw new AssertionError();
                    });

            assertEquals(AtmWithdrawalStatus.MANUAL_REVIEW,
                    result.status());
            assertFalse(result.retryable());
            assertTrue(result.replayed());
        });
    }

    private static AtmWithdrawalOrchestrator orchestrator(
            FakeBackend backend,
            FakeEvents events
    ) {
        return orchestrator(backend, events, () -> SIGNATURE);
    }

    private static AtmWithdrawalOrchestrator orchestrator(
            FakeBackend backend,
            FakeEvents events,
            Supplier<String> currentCurrencySignature
    ) {
        return new AtmWithdrawalOrchestrator(
                backend, events, CLOCK,
                () -> new TestLease(
                        0L, currentCurrencySignature.get(), () -> {
                }));
    }

    private static Thread startReload(
            TestCurrencyGate gate,
            AtomicBoolean completed,
            String signature
    ) {
        Thread writer = new Thread(() -> {
            gate.reload(signature);
            completed.set(true);
        });
        writer.start();
        return writer;
    }

    private static void join(Thread thread) {
        assertTrue(thread != null);
        try {
            thread.join(TimeUnit.SECONDS.toMillis(5L));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
        assertFalse(thread.isAlive());
    }

    private static AtmPreparedWithdrawal protectedPlan() {
        ProtectedAtmWithdrawalRequest request =
                new ProtectedAtmWithdrawalRequest(
                        REQUEST_ID, PLAYER_ID, "futureshops", SIGNATURE,
                        List.of(
                                new AtmBillSelection(0, 100L, 2),
                                new AtmBillSelection(1, 50L, 4)),
                        NOW);
        return AtmPreparedWithdrawal.protectedPlan(
                ProtectedAtmWithdrawalPlan.create(request));
    }

    private static EscrowClaim reshapedCashClaim(
            EscrowClaim claim,
            long denomination,
            int billCount
    ) {
        ProtectedCashClaimPayload current =
                ProtectedCashClaimPayloadCodec.decode(claim.payload());
        ProtectedCashClaimPayload replacement =
                new ProtectedCashClaimPayload(
                        current.batchId(), denomination,
                        current.authorizedCount(), current.portionIndex(),
                        current.portionCount(), billCount,
                        current.serverIdentityEvidence(),
                        current.checksumEvidence());
        long units = Math.multiplyExact(denomination, (long) billCount);
        return new EscrowClaim(claim.claimId(), claim.transactionId(),
                claim.ownerId(), claim.sourceKey(), claim.kind(),
                units, units,
                ProtectedCashClaimPayloadCodec.encode(replacement),
                claim.status(), claim.label(), claim.createdAt(),
                claim.updatedAt());
    }

    private static void withMintConfiguration(Runnable action) {
        String priorServerId = Config.moneyMintServerId;
        String priorSalt = Config.moneyChecksumSalt;
        Config.moneyMintServerId = "orchestrator test server";
        Config.moneyChecksumSalt = "orchestrator test salt";
        try {
            action.run();
        } finally {
            Config.moneyMintServerId = priorServerId;
            Config.moneyChecksumSalt = priorSalt;
        }
    }

    private static final class FakeEvents
            implements AtmBalanceEventGateway {
        private int preCount;
        private int postCount;
        private boolean cancel;
        private Runnable beforeAction = () -> {
        };

        @Override
        public boolean beforeDebit(UUID playerId, long amount,
                                   long balanceBefore) {
            preCount++;
            beforeAction.run();
            return cancel;
        }

        @Override
        public void afterDebit(UUID playerId, long amount,
                               long balanceAfter) {
            postCount++;
        }
    }

    private static final class FakeBackend
            implements AtmWithdrawalBackend {
        private final Map<UUID, EscrowTransaction> transactions =
                new HashMap<>();
        private final Map<UUID, List<EscrowClaim>> claims =
                new HashMap<>();
        private long balance;
        private boolean balanceReadable = true;
        private boolean migrationComplete = true;
        private EscrowRuntimeState runtimeState = EscrowRuntimeState.READY;
        private boolean failCompositeBeforeMutation;
        private EscrowState failState;
        private boolean compositeApplied;
        private Consumer<EscrowState> afterTransactionCommit = state -> {
        };
        private Runnable beforeComposite = () -> {
        };

        private FakeBackend(long balance) {
            this.balance = balance;
        }

        @Override
        public Optional<EscrowTransaction> transaction(UUID requestId) {
            return Optional.ofNullable(transactions.get(requestId));
        }

        @Override
        public List<EscrowClaim> claims(UUID requestId) {
            return claims.getOrDefault(requestId, List.of());
        }

        @Override
        public long balance(UUID playerId) {
            if (!balanceReadable) {
                throw new IllegalStateException("Balance is unavailable");
            }
            return balance;
        }

        @Override
        public boolean migrationComplete() {
            return migrationComplete;
        }

        @Override
        public EscrowRuntimeState runtimeState() {
            return runtimeState;
        }

        @Override
        public EscrowCommitResult commitTransaction(
                EscrowTransaction transaction
        ) {
            if (transaction.state() == failState) {
                throw new IllegalStateException(
                        "Simulated state commit failure");
            }
            UUID id = transaction.transactionId().value();
            EscrowTransaction prior = transactions.get(id);
            if (transaction.equals(prior)) {
                return EscrowCommitResult.replay();
            }
            transactions.put(id, transaction);
            afterTransactionCommit.accept(transaction.state());
            return EscrowCommitResult.applied(new com.enviouse.futureshops
                    .server.escrow.journal.JournalRecord(
                    Math.max(1L, transaction.revision() + 1L), id,
                    UUID.nameUUIDFromBytes((id + "." + transaction.revision())
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                    new byte[]{1}));
        }

        @Override
        public EscrowCommitResult commitProtected(
                AtmWithdrawalCommit commit
        ) {
            beforeComposite.run();
            if (failCompositeBeforeMutation) {
                throw new IllegalStateException(
                        "Simulated composite preflight failure");
            }
            if (compositeApplied) {
                return EscrowCommitResult.replay();
            }
            compositeApplied = true;
            transactions.put(commit.transactionId(),
                    commit.committedTransaction());
            balance = Math.subtractExact(
                    balance, commit.amountMinorUnits());
            claims.put(commit.transactionId(),
                    new ArrayList<>(commit.cashClaims()));
            return EscrowCommitResult.applied(new com.enviouse.futureshops
                    .server.escrow.journal.JournalRecord(
                    5L, commit.transactionId(),
                    UUID.nameUUIDFromBytes("composite".getBytes(
                            java.nio.charset.StandardCharsets.UTF_8)),
                    new byte[]{1}));
        }

        @Override
        public EscrowCommitResult commitForeign(
                ForeignAtmWithdrawalCommit commit
        ) {
            throw new AssertionError("Foreign route was not expected");
        }
    }

    private record TestLease(
            long generation,
            String currencySignature,
            Runnable closeAction
    ) implements AtmCurrencyConfigurationLease {
        @Override
        public void close() {
            closeAction.run();
        }
    }

    private static final class TestCurrencyGate {
        private final ReentrantReadWriteLock lock =
                new ReentrantReadWriteLock(true);
        private String signature;
        private long generation = 1L;

        private TestCurrencyGate(String signature) {
            this.signature = signature;
        }

        private AtmCurrencyConfigurationLease acquire() {
            lock.readLock().lock();
            return new TestLease(
                    generation, signature, lock.readLock()::unlock);
        }

        private void reload(String replacement) {
            lock.writeLock().lock();
            try {
                signature = replacement;
                generation = Math.addExact(generation, 1L);
            } finally {
                lock.writeLock().unlock();
            }
        }

        private String signature() {
            lock.readLock().lock();
            try {
                return signature;
            } finally {
                lock.readLock().unlock();
            }
        }

        private boolean isQueued(Thread thread) {
            return lock.hasQueuedThread(thread);
        }

        private boolean awaitQueued(Thread thread) {
            long deadline = System.nanoTime()
                    + TimeUnit.SECONDS.toNanos(5L);
            while (!isQueued(thread)
                    && System.nanoTime() - deadline < 0L) {
                Thread.onSpinWait();
            }
            return isQueued(thread);
        }
    }
}
