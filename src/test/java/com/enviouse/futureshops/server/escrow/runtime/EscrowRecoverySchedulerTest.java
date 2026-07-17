package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.model.EscrowAssetLot;
import com.enviouse.futureshops.server.escrow.model.EscrowAssetLotType;
import com.enviouse.futureshops.server.escrow.model.EscrowError;
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
import com.enviouse.futureshops.server.escrow.store.EscrowTransactionSavedData;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EscrowRecoverySchedulerTest {
    private static final Instant NOW = Instant.parse("2026-07-16T12:00:00Z");

    @Test
    void processesReadyWorkBeforeEnumerationFinishes() {
        EscrowTransactionSavedData transactions = new EscrowTransactionSavedData();
        transactions.applyCommitted(created(EscrowOperation.ATM_WITHDRAWAL));
        transactions.applyCommitted(created(EscrowOperation.ATM_WITHDRAWAL));
        EscrowRecoveryScheduler scheduler = new EscrowRecoveryScheduler(
                transactions, Clock.fixed(NOW, ZoneOffset.UTC));
        AtomicInteger invoked = new AtomicInteger();
        scheduler.register(EscrowOperation.ATM_WITHDRAWAL, transaction -> {
            invoked.incrementAndGet();
            return EscrowRecoveryAttempt.resolved("No durable hold was created");
        });

        assertEquals(1, scheduler.enumerateBatch(1));
        assertTrue(!scheduler.enumerationComplete());
        assertEquals(1, scheduler.processBatch(1).handlersInvoked());
        assertEquals(1, invoked.get());
    }

    @Test
    void scheduledRetrySleepsUntilItsExactTime() {
        MutableClock clock = new MutableClock(NOW);
        EscrowTransactionSavedData transactions = new EscrowTransactionSavedData();
        persistRecoveryRequired(transactions, EscrowOperation.ATM_WITHDRAWAL,
                NOW.plusSeconds(30), 3);
        EscrowRecoveryScheduler scheduler = new EscrowRecoveryScheduler(transactions, clock);
        AtomicInteger invoked = new AtomicInteger();
        scheduler.register(EscrowOperation.ATM_WITHDRAWAL, transaction -> {
            invoked.incrementAndGet();
            return EscrowRecoveryAttempt.stable("External inspection is complete");
        });

        scheduler.enumerateBatch(10);
        assertTrue(scheduler.hasScheduledWork());
        assertTrue(!scheduler.hasRunnableWork());
        assertEquals(0, scheduler.processBatch(10).handlersInvoked());
        clock.set(NOW.plusSeconds(30));
        assertTrue(scheduler.hasRunnableWork());
        assertEquals(1, scheduler.processBatch(10).handlersInvoked());
        assertEquals(1, invoked.get());
        assertEquals(EscrowRecoveryWorkStatus.STABLE,
                scheduler.pending(10).get(0).status());
    }

    @Test
    void exhaustedRetryParksForManualReview() {
        MutableClock clock = new MutableClock(NOW.plusSeconds(30));
        EscrowTransactionSavedData transactions = new EscrowTransactionSavedData();
        persistRecoveryRequired(transactions, EscrowOperation.ATM_WITHDRAWAL,
                NOW.plusSeconds(20), 1);
        EscrowRecoveryScheduler scheduler = new EscrowRecoveryScheduler(transactions, clock);
        scheduler.register(EscrowOperation.ATM_WITHDRAWAL, transaction ->
                EscrowRecoveryAttempt.retryLater(
                        NOW.plusSeconds(60), "Adapter remains unavailable"));

        scheduler.enumerateBatch(10);
        scheduler.processBatch(10);

        EscrowRecoveryWork work = scheduler.pending(10).get(0);
        assertEquals(EscrowRecoveryWorkStatus.MANUAL_REVIEW, work.status());
        assertTrue(!work.blocksRuntime());
        assertTrue(!scheduler.hasRunnableWork());
    }

    @Test
    void longLivedMarketHoldIsStableButUnknownUnsafeWorkBlocks() {
        EscrowTransactionSavedData stableTransactions = new EscrowTransactionSavedData();
        persistHeld(stableTransactions, EscrowOperation.BAZAAR_BUY_ORDER);
        EscrowRecoveryScheduler stable = new EscrowRecoveryScheduler(
                stableTransactions, Clock.fixed(NOW, ZoneOffset.UTC));
        stable.enumerateBatch(10);
        assertEquals(EscrowRecoveryWorkStatus.STABLE, stable.pending(10).get(0).status());
        assertTrue(!stable.hasBlockingWork());

        EscrowTransactionSavedData unsafeTransactions = new EscrowTransactionSavedData();
        unsafeTransactions.applyCommitted(created(EscrowOperation.ATM_WITHDRAWAL));
        EscrowRecoveryScheduler unsafe = new EscrowRecoveryScheduler(
                unsafeTransactions, Clock.fixed(NOW, ZoneOffset.UTC));
        unsafe.enumerateBatch(10);
        assertTrue(unsafe.hasBlockingWork());
        EscrowRecoveryWork blocked = unsafe.pending(10).get(0);
        assertEquals(EscrowRecoveryWorkStatus.BLOCKED, blocked.status());
        assertTrue(blocked.blocksRuntime());
        assertEquals(0, unsafe.processBatch(10).examined());
        unsafe.register(EscrowOperation.ATM_WITHDRAWAL,
                transaction -> EscrowRecoveryAttempt.resolved("Inspection found no hold"));
        EscrowRecoveryBatchResult activated = unsafe.processBatch(1);
        assertEquals(1, activated.examined());
        assertEquals(0, activated.handlersInvoked());
        assertEquals(1, unsafe.processBatch(1).handlersInvoked());
        assertTrue(!unsafe.hasBlockingWork());
    }

    @Test
    void countersMatchPendingAcrossEveryClassifiedStatus() {
        EscrowTransactionSavedData transactions = new EscrowTransactionSavedData();
        transactions.applyCommitted(created(EscrowOperation.ATM_WITHDRAWAL));
        transactions.applyCommitted(created(EscrowOperation.CURRENCY_DEPOSIT));
        persistRecoveryRequired(transactions, EscrowOperation.ATM_WITHDRAWAL,
                NOW.plusSeconds(30), 3);
        persistManualReview(transactions, EscrowOperation.CLAIM);
        persistHeld(transactions, EscrowOperation.BAZAAR_BUY_ORDER);
        EscrowRecoveryScheduler scheduler = new EscrowRecoveryScheduler(
                transactions, Clock.fixed(NOW, ZoneOffset.UTC));
        scheduler.register(EscrowOperation.ATM_WITHDRAWAL,
                transaction -> EscrowRecoveryAttempt.stable("Inspection completed"));

        scheduler.enumerateBatch(10_000);

        assertStatusCountersMatch(scheduler);
        assertEquals(Map.of(
                        EscrowRecoveryWorkStatus.READY, 1,
                        EscrowRecoveryWorkStatus.SCHEDULED, 1,
                        EscrowRecoveryWorkStatus.BLOCKED, 1,
                        EscrowRecoveryWorkStatus.MANUAL_REVIEW, 1,
                        EscrowRecoveryWorkStatus.STABLE, 1),
                reflectedStatusCounts(scheduler));
    }

    @Test
    void replacementRemovalAndStaleScheduleKeepCountersExact() {
        MutableClock clock = new MutableClock(NOW);
        EscrowTransactionSavedData transactions = new EscrowTransactionSavedData();
        EscrowTransaction first = persistRecoveryRequired(
                transactions, EscrowOperation.ATM_WITHDRAWAL,
                NOW.plusSeconds(30), 3);
        EscrowRecoveryScheduler scheduler = new EscrowRecoveryScheduler(transactions, clock);
        AtomicInteger attempts = new AtomicInteger();
        scheduler.register(EscrowOperation.ATM_WITHDRAWAL, transaction ->
                attempts.incrementAndGet() == 1
                        ? EscrowRecoveryAttempt.stable("First inspection is stable")
                        : EscrowRecoveryAttempt.resolved("Second inspection is resolved"));
        scheduler.enumerateBatch(10_000);
        assertOnlyStatus(scheduler, EscrowRecoveryWorkStatus.SCHEDULED, 1);

        EscrowTransaction resumed = first.transitionTo(
                EscrowState.HELD, NOW.plusSeconds(10));
        transactions.applyCommitted(resumed);
        EscrowError replacementError = retryableError(NOW.plusSeconds(11));
        EscrowTransaction replacement = resumed.requireRecovery(
                replacementError, 3, NOW.plusSeconds(60), NOW.plusSeconds(11));
        transactions.applyCommitted(replacement);
        scheduler.enqueue(replacement);
        assertOnlyStatus(scheduler, EscrowRecoveryWorkStatus.SCHEDULED, 1);

        clock.set(NOW.plusSeconds(30));
        assertFalse(scheduler.hasRunnableWork());
        assertOnlyStatus(scheduler, EscrowRecoveryWorkStatus.SCHEDULED, 1);

        clock.set(NOW.plusSeconds(60));
        assertTrue(scheduler.hasRunnableWork());
        assertOnlyStatus(scheduler, EscrowRecoveryWorkStatus.READY, 1);
        scheduler.processBatch(1);
        assertOnlyStatus(scheduler, EscrowRecoveryWorkStatus.STABLE, 1);

        scheduler.enqueue(replacement);
        assertOnlyStatus(scheduler, EscrowRecoveryWorkStatus.READY, 1);
        scheduler.processBatch(1);
        assertStatusCountersMatch(scheduler);
        assertEquals(0, scheduler.pendingCount());
        assertEquals(Map.of(), reflectedStatusCounts(scheduler));
    }

    @Test
    void registeringHandlerActivatesBlockedWorkWithoutCounterDrift() {
        EscrowTransactionSavedData transactions = new EscrowTransactionSavedData();
        transactions.applyCommitted(created(EscrowOperation.CURRENCY_DEPOSIT));
        EscrowRecoveryScheduler scheduler = new EscrowRecoveryScheduler(
                transactions, Clock.fixed(NOW, ZoneOffset.UTC));
        scheduler.enumerateBatch(10_000);
        assertOnlyStatus(scheduler, EscrowRecoveryWorkStatus.BLOCKED, 1);
        assertFalse(scheduler.hasRunnableWork());

        scheduler.register(EscrowOperation.CURRENCY_DEPOSIT,
                transaction -> EscrowRecoveryAttempt.resolved("Deposit has no durable hold"));
        assertTrue(scheduler.hasRunnableWork());
        assertStatusCountersMatch(scheduler);

        EscrowRecoveryBatchResult activated = scheduler.processBatch(1);
        assertEquals(1, activated.examined());
        assertEquals(0, activated.handlersInvoked());
        assertOnlyStatus(scheduler, EscrowRecoveryWorkStatus.READY, 1);
        scheduler.processBatch(1);
        assertStatusCountersMatch(scheduler);
        assertEquals(0, scheduler.pendingCount());
    }

    @Test
    void handlerManualReviewDispositionUpdatesCountersExactly() {
        EscrowTransactionSavedData transactions = new EscrowTransactionSavedData();
        transactions.applyCommitted(created(EscrowOperation.ATM_WITHDRAWAL));
        EscrowRecoveryScheduler scheduler = new EscrowRecoveryScheduler(
                transactions, Clock.fixed(NOW, ZoneOffset.UTC));
        scheduler.register(EscrowOperation.ATM_WITHDRAWAL,
                transaction -> EscrowRecoveryAttempt.manualReview(
                        "Operator inspection is required"));
        scheduler.enumerateBatch(10_000);
        assertOnlyStatus(scheduler, EscrowRecoveryWorkStatus.READY, 1);

        scheduler.processBatch(1);

        assertOnlyStatus(scheduler, EscrowRecoveryWorkStatus.MANUAL_REVIEW, 1);
    }

    @Test
    void terminalReplacementRemovesReadyWorkAndItsCounter() {
        EscrowTransactionSavedData transactions = new EscrowTransactionSavedData();
        EscrowTransaction transaction = created(EscrowOperation.ATM_WITHDRAWAL);
        transactions.applyCommitted(transaction);
        EscrowRecoveryScheduler scheduler = new EscrowRecoveryScheduler(
                transactions, Clock.fixed(NOW, ZoneOffset.UTC));
        scheduler.register(EscrowOperation.ATM_WITHDRAWAL,
                value -> EscrowRecoveryAttempt.resolved("Already terminal"));
        scheduler.enumerateBatch(10_000);
        assertOnlyStatus(scheduler, EscrowRecoveryWorkStatus.READY, 1);

        transaction = transaction.transitionTo(EscrowState.ABORTING, NOW.plusSeconds(1));
        transactions.applyCommitted(transaction);
        transaction = transaction.transitionTo(
                EscrowState.REFUND_PENDING, NOW.plusSeconds(2));
        transactions.applyCommitted(transaction);
        transaction = transaction.transitionTo(EscrowState.REFUNDED, NOW.plusSeconds(3));
        transactions.applyCommitted(transaction);
        scheduler.enqueue(transaction);

        assertStatusCountersMatch(scheduler);
        assertEquals(0, scheduler.pendingCount());
        assertEquals(0, scheduler.processBatch(10_000).examined());
        assertEquals(Map.of(), reflectedStatusCounts(scheduler));
    }

    @Test
    void largeStateQueriesUseCountersWithoutScanningWorkMap() throws IOException {
        EscrowTransactionSavedData transactions = new EscrowTransactionSavedData();
        for (int index = 0; index < 10_000; index++) {
            transactions.applyCommitted(created(EscrowOperation.CURRENCY_DEPOSIT));
        }
        EscrowRecoveryScheduler scheduler = new EscrowRecoveryScheduler(
                transactions, Clock.fixed(NOW, ZoneOffset.UTC));

        assertEquals(10_000, scheduler.enumerateBatch(10_000));
        assertEquals(0, scheduler.enumerateBatch(10_000));
        assertOnlyStatus(scheduler, EscrowRecoveryWorkStatus.BLOCKED, 10_000);

        String source = Files.readString(Path.of(
                "src/main/java/com/enviouse/futureshops/server/escrow/runtime/EscrowRecoveryScheduler.java"));
        for (String method : List.of(
                "hasPendingOrEnumeration", "hasBlockingWork", "hasRunnableWork",
                "hasScheduledWork", "hasManualReviewWork", "pendingCount")) {
            String body = methodBody(source, method);
            assertFalse(body.contains("work.entrySet("));
            assertFalse(body.contains("work.values("));
            assertFalse(body.contains("work.forEach("));
            assertFalse(body.contains("pending("));
            if (!method.equals("pendingCount")) {
                assertFalse(body.contains("work."));
            }
        }
        assertTrue(methodBody(source, "hasBlockingWork").contains("statusCount("));
        assertTrue(methodBody(source, "hasScheduledWork").contains("statusCount("));
        assertTrue(methodBody(source, "hasManualReviewWork").contains("statusCount("));
        assertTrue(methodBody(source, "pendingCount").contains("work.size()"));
    }

    private static EscrowTransaction persistRecoveryRequired(
            EscrowTransactionSavedData transactions,
            EscrowOperation operation,
            Instant nextAttemptAt,
            int maxAttempts) {
        EscrowTransaction held = persistHeld(transactions, operation);
        EscrowError error = retryableError(NOW.plusSeconds(4));
        EscrowTransaction recovery = held.requireRecovery(
                error, maxAttempts, nextAttemptAt, NOW.plusSeconds(4));
        transactions.applyCommitted(recovery);
        return recovery;
    }

    private static EscrowTransaction persistManualReview(
            EscrowTransactionSavedData transactions,
            EscrowOperation operation
    ) {
        EscrowTransaction recovery = persistRecoveryRequired(
                transactions, operation, NOW.plusSeconds(30), 3);
        EscrowTransaction manual = recovery.transitionTo(
                EscrowState.MANUAL_REVIEW, NOW.plusSeconds(5));
        transactions.applyCommitted(manual);
        return manual;
    }

    private static EscrowError retryableError(Instant occurredAt) {
        return new EscrowError(
                "ADAPTER_RETRY", "Adapter is unavailable", true,
                occurredAt, Map.of("adapter", "test"));
    }

    private static EscrowTransaction persistHeld(EscrowTransactionSavedData transactions,
                                                 EscrowOperation operation) {
        EscrowTransaction transaction = created(operation);
        transactions.applyCommitted(transaction);
        transaction = transaction.transitionTo(EscrowState.VALIDATED, NOW.plusSeconds(1));
        transactions.applyCommitted(transaction);
        transaction = transaction.transitionTo(EscrowState.HOLDING, NOW.plusSeconds(2));
        transactions.applyCommitted(transaction);
        transaction = transaction.transitionTo(EscrowState.HELD, NOW.plusSeconds(3));
        transactions.applyCommitted(transaction);
        return transaction;
    }

    private static EscrowTransaction created(EscrowOperation operation) {
        UUID transactionId = UUID.randomUUID();
        EscrowParty player = EscrowParty.player(UUID.randomUUID());
        EscrowParty module = EscrowParty.module("test module");
        return EscrowTransaction.create(
                new EscrowTransactionId(transactionId),
                Optional.empty(),
                new EscrowRequestKey("request " + transactionId),
                operation,
                Set.of(
                        new EscrowParticipant(player, Set.of(
                                EscrowParticipantRole.INITIATOR,
                                EscrowParticipantRole.PAYER)),
                        new EscrowParticipant(module, Set.of(
                                EscrowParticipantRole.BENEFICIARY))),
                List.of(new EscrowAssetLot(
                        UUID.randomUUID(), EscrowAssetLotType.WALLET_MONEY,
                        EscrowProtectionLevel.PROTECTED, player, module, 1L,
                        Optional.of(new MoneyAmount("futureshops:credits", 100L)),
                        new byte[0], Map.of())),
                NOW,
                1L,
                Optional.empty());
    }

    private static void assertOnlyStatus(EscrowRecoveryScheduler scheduler,
                                         EscrowRecoveryWorkStatus status,
                                         int count) {
        assertStatusCountersMatch(scheduler);
        assertEquals(Map.of(status, count), reflectedStatusCounts(scheduler));
    }

    private static void assertStatusCountersMatch(EscrowRecoveryScheduler scheduler) {
        boolean runnable = scheduler.hasRunnableWork();
        List<EscrowRecoveryWork> pending = scheduler.pending(10_000);
        Map<EscrowRecoveryWorkStatus, Integer> expected =
                new EnumMap<>(EscrowRecoveryWorkStatus.class);
        for (EscrowRecoveryWork value : pending) {
            expected.merge(value.status(), 1, Math::addExact);
        }
        assertEquals(pending.size(), scheduler.pendingCount());
        assertEquals(expected, reflectedStatusCounts(scheduler));
        assertEquals(expected.getOrDefault(EscrowRecoveryWorkStatus.BLOCKED, 0) > 0,
                scheduler.hasBlockingWork());
        assertEquals(expected.getOrDefault(EscrowRecoveryWorkStatus.SCHEDULED, 0) > 0,
                scheduler.hasScheduledWork());
        assertEquals(expected.getOrDefault(EscrowRecoveryWorkStatus.MANUAL_REVIEW, 0) > 0,
                scheduler.hasManualReviewWork());
        boolean expectedRunnable = expected.getOrDefault(
                EscrowRecoveryWorkStatus.READY, 0) > 0
                || pending.stream().anyMatch(value ->
                value.status() == EscrowRecoveryWorkStatus.BLOCKED
                        && value.handlerRegistered());
        assertEquals(expectedRunnable, runnable);
        boolean expectedPendingOrEnumeration = !scheduler.enumerationComplete()
                || expected.getOrDefault(EscrowRecoveryWorkStatus.READY, 0) > 0;
        assertEquals(expectedPendingOrEnumeration, scheduler.hasPendingOrEnumeration());
    }

    @SuppressWarnings("unchecked")
    private static Map<EscrowRecoveryWorkStatus, Integer> reflectedStatusCounts(
            EscrowRecoveryScheduler scheduler
    ) {
        try {
            Field field = EscrowRecoveryScheduler.class.getDeclaredField("statusCounts");
            field.setAccessible(true);
            return Map.copyOf((Map<EscrowRecoveryWorkStatus, Integer>) field.get(scheduler));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static String methodBody(String source, String methodName) {
        int method = source.indexOf(" " + methodName + "(");
        if (method < 0) {
            throw new AssertionError("Missing method " + methodName);
        }
        int open = source.indexOf('{', method);
        int depth = 0;
        for (int index = open; index < source.length(); index++) {
            char value = source.charAt(index);
            if (value == '{') {
                depth++;
            } else if (value == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(open, index + 1);
                }
            }
        }
        throw new AssertionError("Unclosed method " + methodName);
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        private void set(Instant value) {
            now = value;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            if (!ZoneOffset.UTC.equals(zone)) {
                throw new IllegalArgumentException("Only UTC is supported");
            }
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
