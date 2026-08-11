package com.enviouse.futureshops.server.escrow.admin.balance;

import com.enviouse.futureshops.server.economy.TransactionResult;
import com.enviouse.futureshops.server.escrow.admin.AdminAuditConflictException;
import com.enviouse.futureshops.server.escrow.admin.EscrowAdministrativeRecord;
import com.enviouse.futureshops.server.shop.ShopResultCode;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdministrativeBalanceMutationServiceTest {
    private static final Instant NOW =
            Instant.parse("2026-07-17T18:30:00.123456789Z");

    @Test
    void exactReplayReturnsTheStoredOutcomeAndAppliesOnce() {
        FakeBackend backend = new FakeBackend();
        UUID playerId = UUID.randomUUID();
        backend.balances.put(playerId, 100L);
        AdministrativeBalanceMutation mutation = mutation(
                UUID.randomUUID(), "operator one",
                AdministrativeBalanceOperation.CREDIT, playerId,
                Optional.empty(), 25L, false, "Balance correction",
                AdministrativeBalanceConfirmation.EXPLICIT_API);
        AdministrativeBalanceMutationService service = service(backend);

        AdministrativeBalanceMutationResult first =
                service.execute(mutation);
        AdministrativeBalanceMutationResult replay =
                service.execute(mutation);

        assertTrue(first.transactionResult().success());
        assertEquals(125L, first.transactionResult().resultingBalance());
        assertFalse(first.replayed());
        assertTrue(replay.replayed());
        assertEquals(first.transactionResult(), replay.transactionResult());
        assertEquals(first.intentEvidence(), replay.intentEvidence());
        assertEquals(first.outcomeEvidence(), replay.outcomeEvidence());
        assertEquals(1, backend.appliedMutationCount);
        assertEquals(2, backend.auditRecords.size());
        assertEquals(AdministrativeBalanceOperation.CREDIT,
                replay.outcomeEvidence().operation());
        assertEquals(25L, replay.outcomeEvidence().amountMinor());
    }

    @Test
    void concurrentSameRequestAppliesOnceAcrossServiceInstances()
            throws Exception {
        FakeBackend backend = new FakeBackend();
        UUID playerId = UUID.randomUUID();
        backend.balances.put(playerId, 10L);
        AdministrativeBalanceMutation mutation = mutation(
                UUID.randomUUID(), "operator one",
                AdministrativeBalanceOperation.CREDIT, playerId,
                Optional.empty(), 5L, false, "Concurrent correction",
                AdministrativeBalanceConfirmation.EXPLICIT_API);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch ready = new CountDownLatch(8);
        CountDownLatch start = new CountDownLatch(1);
        try {
            java.util.ArrayList<Future<AdministrativeBalanceMutationResult>>
                    futures = new java.util.ArrayList<>();
            for (int i = 0; i < 8; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return service(backend).execute(mutation);
                }));
            }
            assertTrue(ready.await(5L, TimeUnit.SECONDS));
            start.countDown();
            List<AdministrativeBalanceMutationResult> results =
                    new java.util.ArrayList<>();
            for (Future<AdministrativeBalanceMutationResult> future
                    : futures) {
                results.add(future.get(5L, TimeUnit.SECONDS));
            }
            assertEquals(1L, results.stream()
                    .filter(result -> !result.replayed()).count());
            assertEquals(7L, results.stream()
                    .filter(AdministrativeBalanceMutationResult::replayed)
                    .count());
            assertEquals(1, backend.appliedMutationCount);
            assertEquals(15L, backend.balance(playerId));
            assertEquals(2, backend.auditRecords.size());
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5L,
                    TimeUnit.SECONDS));
        }
    }

    @Test
    void reusedRequestRejectsChangedAmountActorReasonAndConfirmation() {
        FakeBackend backend = new FakeBackend();
        UUID playerId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        backend.balances.put(playerId, 200L);
        AdministrativeBalanceMutation original = mutation(
                requestId, "operator one",
                AdministrativeBalanceOperation.DEBIT, playerId,
                Optional.empty(), 30L, false, "First reason",
                AdministrativeBalanceConfirmation.EXPLICIT_API);
        AdministrativeBalanceMutationService service = service(backend);
        service.execute(original);

        assertThrows(AdminAuditConflictException.class,
                () -> service.execute(mutation(requestId, "operator one",
                        AdministrativeBalanceOperation.DEBIT, playerId,
                        Optional.empty(), 31L, false, "First reason",
                        AdministrativeBalanceConfirmation.EXPLICIT_API)));
        assertThrows(AdminAuditConflictException.class,
                () -> service.execute(mutation(requestId, "operator two",
                        AdministrativeBalanceOperation.DEBIT, playerId,
                        Optional.empty(), 30L, false, "First reason",
                        AdministrativeBalanceConfirmation.EXPLICIT_API)));
        assertThrows(AdminAuditConflictException.class,
                () -> service.execute(mutation(requestId, "operator one",
                        AdministrativeBalanceOperation.DEBIT, playerId,
                        Optional.empty(), 30L, false, "Second reason",
                        AdministrativeBalanceConfirmation.EXPLICIT_API)));
        assertThrows(AdminAuditConflictException.class,
                () -> service.execute(mutation(requestId, "operator one",
                        AdministrativeBalanceOperation.DEBIT, playerId,
                        Optional.empty(), 30L, false, "First reason",
                        AdministrativeBalanceConfirmation.UNCONFIRMED)));
        assertEquals(1, backend.appliedMutationCount);
        assertEquals(170L, backend.balance(playerId));
    }

    @Test
    void unconfirmedRequestFailsClosedAndIsDurablyReplayable() {
        FakeBackend backend = new FakeBackend();
        UUID playerId = UUID.randomUUID();
        backend.balances.put(playerId, 90L);
        AdministrativeBalanceMutation mutation = mutation(
                UUID.randomUUID(), "integration one",
                AdministrativeBalanceOperation.SET, playerId,
                Optional.empty(), 900L, false, "Awaiting approval",
                AdministrativeBalanceConfirmation.UNCONFIRMED);
        AdministrativeBalanceMutationService service = service(backend);

        AdministrativeBalanceMutationResult first =
                service.execute(mutation);
        AdministrativeBalanceMutationResult replay =
                service.execute(mutation);

        assertFalse(first.transactionResult().success());
        assertEquals(ShopResultCode.INVALID_REQUEST,
                first.transactionResult().errorCode());
        assertEquals(90L, backend.balance(playerId));
        assertEquals(0, backend.appliedMutationCount);
        assertEquals(2, backend.auditRecords.size());
        assertEquals(AdministrativeBalanceConfirmation.UNCONFIRMED,
                first.outcomeEvidence().confirmation());
        assertTrue(replay.replayed());
        assertEquals(first.transactionResult(), replay.transactionResult());
    }

    @Test
    void invalidAmountAndSelfTransferAreAuditedWithoutProviderMutation() {
        FakeBackend backend = new FakeBackend();
        UUID playerId = UUID.randomUUID();
        backend.balances.put(playerId, 90L);

        AdministrativeBalanceMutationResult invalidAmount =
                service(backend).execute(mutation(
                        UUID.randomUUID(), "integration one",
                        AdministrativeBalanceOperation.CREDIT, playerId,
                        Optional.empty(), 0L, false, "Invalid credit",
                        AdministrativeBalanceConfirmation.EXPLICIT_API));
        AdministrativeBalanceMutationResult invalidTarget =
                service(backend).execute(mutation(
                        UUID.randomUUID(), "integration one",
                        AdministrativeBalanceOperation.TRANSFER, playerId,
                        Optional.of(playerId), 5L, false,
                        "Invalid transfer",
                        AdministrativeBalanceConfirmation.EXPLICIT_API));

        assertEquals(ShopResultCode.INVALID_AMOUNT,
                invalidAmount.transactionResult().errorCode());
        assertEquals(ShopResultCode.INVALID_TARGET,
                invalidTarget.transactionResult().errorCode());
        assertEquals(0, backend.appliedMutationCount);
        assertEquals(90L, backend.balance(playerId));
        assertEquals(4, backend.auditRecords.size());
    }

    @Test
    void retryAfterOutcomeWriteFailureReplaysWalletFromDurableIntent() {
        FakeBackend backend = new FakeBackend();
        UUID playerId = UUID.randomUUID();
        backend.balances.put(playerId, 40L);
        AdministrativeBalanceMutation mutation = mutation(
                UUID.randomUUID(), "operator one",
                AdministrativeBalanceOperation.CREDIT, playerId,
                Optional.empty(), 10L, false, "Crash recovery",
                AdministrativeBalanceConfirmation.EXPLICIT_COMMAND);
        backend.failNextOutcomeCommit = true;

        assertThrows(IllegalStateException.class,
                () -> service(backend).execute(mutation));
        assertEquals(50L, backend.balance(playerId));
        assertEquals(1, backend.appliedMutationCount);
        assertEquals(1, backend.auditRecords.size());

        AdministrativeBalanceMutationResult recovered =
                service(backend).execute(mutation);

        assertTrue(recovered.transactionResult().success());
        assertTrue(recovered.replayed());
        assertEquals(40L, recovered.intentEvidence().balanceBefore());
        assertEquals(50L, recovered.outcomeEvidence().resultingBalance());
        assertEquals(1, backend.appliedMutationCount);
        assertEquals(2, backend.applyCallCount);
        assertEquals(2, backend.auditRecords.size());
    }

    @Test
    void transferEvidencePreservesBothSidesAndInsufficientDebitSkipsApply() {
        FakeBackend backend = new FakeBackend();
        UUID senderId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        backend.balances.put(senderId, 80L);
        backend.balances.put(recipientId, 15L);
        AdministrativeBalanceMutation transfer = mutation(
                UUID.randomUUID(), "integration one",
                AdministrativeBalanceOperation.TRANSFER, senderId,
                Optional.of(recipientId), 30L, false, "Award transfer",
                AdministrativeBalanceConfirmation.EXPLICIT_API);

        AdministrativeBalanceMutationResult result =
                service(backend).execute(transfer);

        assertEquals(80L, result.intentEvidence().balanceBefore());
        assertEquals(15L, result.intentEvidence()
                .counterpartyBalanceBefore().orElseThrow());
        assertEquals(50L, result.outcomeEvidence().resultingBalance());
        assertEquals(45L, result.outcomeEvidence()
                .counterpartyResultingBalance().orElseThrow());
        assertEquals(Optional.of(recipientId),
                result.outcomeEvidence().counterpartyPlayerId());

        AdministrativeBalanceMutation debit = mutation(
                UUID.randomUUID(), "operator one",
                AdministrativeBalanceOperation.DEBIT, senderId,
                Optional.empty(), 500L, false, "Rejected correction",
                AdministrativeBalanceConfirmation.EXPLICIT_COMMAND);
        AdministrativeBalanceMutationResult rejected =
                service(backend).execute(debit);
        assertFalse(rejected.transactionResult().success());
        assertEquals(ShopResultCode.INSUFFICIENT_FUNDS,
                rejected.transactionResult().errorCode());
        assertEquals(1, backend.appliedMutationCount);
    }

    private static AdministrativeBalanceMutationService service(
            FakeBackend backend
    ) {
        return new AdministrativeBalanceMutationService(backend,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static AdministrativeBalanceMutation mutation(
            UUID requestId,
            String actor,
            AdministrativeBalanceOperation operation,
            UUID targetPlayerId,
            Optional<UUID> counterpartyPlayerId,
            long amountMinor,
            boolean allowNegative,
            String reason,
            AdministrativeBalanceConfirmation confirmation
    ) {
        return new AdministrativeBalanceMutation(requestId, actor,
                operation, targetPlayerId, counterpartyPlayerId,
                amountMinor, allowNegative, reason, confirmation);
    }

    private static final class FakeBackend
            implements AdministrativeBalanceBackend {
        private final Map<UUID, Long> balances = new HashMap<>();
        private final Map<UUID, StoredMutation> mutations =
                new HashMap<>();
        private final Map<UUID, EscrowAdministrativeRecord> auditRecords =
                new LinkedHashMap<>();
        private int applyCallCount;
        private int appliedMutationCount;
        private boolean failNextOutcomeCommit;

        @Override
        public long balance(UUID playerId) {
            return balances.getOrDefault(playerId, 0L);
        }

        @Override
        public TransactionResult apply(
                AdministrativeBalanceMutation mutation
        ) {
            applyCallCount++;
            StoredMutation stored = mutations.get(mutation.requestId());
            if (stored != null) {
                if (!stored.fingerprint().equals(
                        mutation.semanticFingerprint())) {
                    throw new IllegalStateException(
                            "Mutation request was reused");
                }
                return stored.result();
            }
            long before = balance(mutation.targetPlayerId());
            TransactionResult result;
            switch (mutation.operation()) {
                case CREDIT -> {
                    long after = Math.addExact(before,
                            mutation.amountMinor());
                    balances.put(mutation.targetPlayerId(), after);
                    result = TransactionResult.ok(after);
                }
                case DEBIT -> {
                    if (!mutation.allowNegative()
                            && before < mutation.amountMinor()) {
                        result = TransactionResult.error(
                                ShopResultCode.INSUFFICIENT_FUNDS, before);
                    } else {
                        long after = Math.subtractExact(before,
                                mutation.amountMinor());
                        balances.put(mutation.targetPlayerId(), after);
                        result = TransactionResult.ok(after);
                    }
                }
                case SET, RESET -> {
                    if (!mutation.allowNegative()
                            && mutation.amountMinor() < 0L) {
                        result = TransactionResult.error(
                                ShopResultCode.INVALID_AMOUNT, before);
                    } else {
                        balances.put(mutation.targetPlayerId(),
                                mutation.amountMinor());
                        result = TransactionResult.ok(
                                mutation.amountMinor());
                    }
                }
                case TRANSFER -> {
                    UUID recipient = mutation.counterpartyPlayerId()
                            .orElseThrow();
                    if (before < mutation.amountMinor()) {
                        result = TransactionResult.error(
                                ShopResultCode.INSUFFICIENT_FUNDS, before);
                    } else {
                        long senderAfter = Math.subtractExact(before,
                                mutation.amountMinor());
                        long recipientAfter = Math.addExact(
                                balance(recipient), mutation.amountMinor());
                        balances.put(mutation.targetPlayerId(),
                                senderAfter);
                        balances.put(recipient, recipientAfter);
                        result = TransactionResult.ok(senderAfter);
                    }
                }
                default -> throw new IllegalStateException();
            }
            mutations.put(mutation.requestId(), new StoredMutation(
                    mutation.semanticFingerprint(), result));
            appliedMutationCount++;
            return result;
        }

        @Override
        public Optional<EscrowAdministrativeRecord> auditRecord(
                UUID evidenceId
        ) {
            return Optional.ofNullable(auditRecords.get(evidenceId));
        }

        @Override
        public boolean commitAudit(EscrowAdministrativeRecord record) {
            AdministrativeBalanceEvidence evidence =
                    AdministrativeBalanceEvidenceCodec.decode(
                            record.outcome());
            if (failNextOutcomeCommit
                    && evidence.phase()
                    == AdministrativeBalanceEvidencePhase.OUTCOME) {
                failNextOutcomeCommit = false;
                throw new IllegalStateException("Outcome write failed");
            }
            EscrowAdministrativeRecord existing =
                    auditRecords.get(record.requestId());
            if (existing != null) {
                if (!existing.equals(record)) {
                    throw new AdminAuditConflictException(
                            "Administrative request was reused");
                }
                return true;
            }
            auditRecords.put(record.requestId(), record);
            return false;
        }
    }

    private record StoredMutation(
            String fingerprint,
            TransactionResult result
    ) {
    }
}
