package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.admin.EscrowAdministrativeAction;
import com.enviouse.futureshops.server.escrow.admin.EscrowAdministrativeAuditSavedData;
import com.enviouse.futureshops.server.escrow.admin.EscrowAdministrativeRecord;
import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.claim.ClaimSavedData;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.custody.CustodyAdapterCapability;
import com.enviouse.futureshops.server.escrow.custody.CustodyAssetType;
import com.enviouse.futureshops.server.escrow.custody.CustodyBatchCommit;
import com.enviouse.futureshops.server.escrow.custody.CustodyBatchCommitCodec;
import com.enviouse.futureshops.server.escrow.custody.CustodyBatchPlan;
import com.enviouse.futureshops.server.escrow.custody.CustodyBatchStatus;
import com.enviouse.futureshops.server.escrow.custody.CustodyEndpointEvidence;
import com.enviouse.futureshops.server.escrow.custody.CustodyLot;
import com.enviouse.futureshops.server.escrow.custody.CustodyMutation;
import com.enviouse.futureshops.server.escrow.custody.CustodyMutationCodec;
import com.enviouse.futureshops.server.escrow.custody.CustodyOperation;
import com.enviouse.futureshops.server.escrow.custody.CustodyPreparedOperation;
import com.enviouse.futureshops.server.escrow.custody.CustodyPreparedBatch;
import com.enviouse.futureshops.server.escrow.custody.CustodyPreparedOperationCodec;
import com.enviouse.futureshops.server.escrow.custody.CustodyProtectionTier;
import com.enviouse.futureshops.server.escrow.custody.CustodySavedData;
import com.enviouse.futureshops.server.escrow.custody.CustodyTransferEvidence;
import com.enviouse.futureshops.server.escrow.journal.JournalReplayBatch;
import com.enviouse.futureshops.server.escrow.journal.WriteAheadJournal;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountType;
import com.enviouse.futureshops.server.escrow.ledger.LedgerLeg;
import com.enviouse.futureshops.server.escrow.ledger.LedgerSavedData;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintBatch;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintEventCodec;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintJournalEvent;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintSavedData;
import com.enviouse.futureshops.server.escrow.store.EscrowTransactionSavedData;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EscrowRuntimeCoordinatorTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void exposesConstantTimeActiveJournalMetrics() {
        Path path = temporaryDirectory.resolve("metrics.wal");
        EscrowRuntimeCoordinator coordinator = new EscrowRuntimeCoordinator(
                path, new EscrowRuntimeSavedData(), (record, event) -> {
                });

        assertEquals(EscrowRuntimeState.READY, coordinator.start());
        EscrowJournalMetrics initial = coordinator.journalMetrics();
        assertEquals(1L, initial.recordCount());
        assertTrue(initial.sizeBytes() > 0L);

        coordinator.commit(UUID.randomUUID(), new EscrowJournalEvent(
                EscrowJournalEventType.ADMIN_AUDIT, new byte[]{1}));
        EscrowJournalMetrics changed = coordinator.journalMetrics();
        assertEquals(2L, changed.recordCount());
        assertTrue(changed.sizeBytes() > initial.sizeBytes());
        coordinator.close();
    }

    @Test
    void failedApplyLeavesForcedRecordForReplay() throws Exception {
        Path path = temporaryDirectory.resolve("crash.wal");
        EscrowRuntimeSavedData cursor = new EscrowRuntimeSavedData();
        EscrowJournalEvent mutation = new EscrowJournalEvent(
                EscrowJournalEventType.CLAIM_CREATE, new byte[]{4, 2});
        UUID transactionId = UUID.randomUUID();

        EscrowRuntimeCoordinator first = new EscrowRuntimeCoordinator(
                path, cursor, (record, event) -> {
                    throw new IllegalStateException("simulated apply crash");
                });
        assertEquals(EscrowRuntimeState.READY, first.start());
        assertThrows(EscrowRuntimeException.class, () -> first.commit(transactionId, mutation));
        assertEquals(EscrowRuntimeState.MAINTENANCE, first.state());
        assertEquals(1L, cursor.lastAppliedSequence());
        first.close();

        try (WriteAheadJournal journal = WriteAheadJournal.open(path)) {
            JournalReplayBatch replay = journal.replayBatch(
                    0L, 1L, 10, WriteAheadJournal.MAX_REPLAY_BATCH_BYTES);
            assertEquals(2, replay.records().size());
            assertEquals(transactionId, replay.records().get(1).transactionId());
            assertEquals(mutation, EscrowJournalEventCodec.decode(replay.records().get(1).payload()));
        }

        AtomicInteger applied = new AtomicInteger();
        EscrowRuntimeCoordinator second = new EscrowRuntimeCoordinator(
                path, cursor, (record, event) -> applied.incrementAndGet());
        assertEquals(EscrowRuntimeState.READY, second.start(10));
        assertEquals(1, applied.get());
        assertEquals(2L, cursor.lastAppliedSequence());
        second.close();
    }

    @Test
    void expectedPreflightRejectionDoesNotPoisonJournalOrMaintenance() throws Exception {
        Path path = temporaryDirectory.resolve("preflight-rejection.wal");
        EscrowRuntimeSavedData cursor = new EscrowRuntimeSavedData();
        EscrowMutationApplier rejecting = new EscrowMutationApplier() {
            @Override
            public EscrowPreflightResult preflight(UUID transactionId,
                                                   EscrowJournalEvent event) {
                throw new IllegalArgumentException("expected stale request");
            }

            @Override
            public void apply(com.enviouse.futureshops.server.escrow.journal.JournalRecord record,
                              EscrowJournalEvent event) {
                throw new AssertionError("Rejected mutation must not be applied");
            }
        };
        EscrowRuntimeCoordinator coordinator = new EscrowRuntimeCoordinator(path, cursor, rejecting);
        assertEquals(EscrowRuntimeState.READY, coordinator.start());
        assertThrows(IllegalArgumentException.class, () -> coordinator.commit(
                UUID.randomUUID(), new EscrowJournalEvent(
                        EscrowJournalEventType.LEDGER_APPLY, new byte[]{1})));
        assertEquals(EscrowRuntimeState.READY, coordinator.state());
        assertEquals(1L, cursor.lastAppliedSequence());
        coordinator.close();

        try (WriteAheadJournal journal = WriteAheadJournal.open(path)) {
            assertEquals(1, journal.replayBatch(
                    0L, 1L, 10, WriteAheadJournal.MAX_REPLAY_BATCH_BYTES).records().size());
        }
    }

    @Test
    void exactReplayDoesNotAppendAnotherJournalRecord() throws Exception {
        Path path = temporaryDirectory.resolve("exact-replay.wal");
        EscrowRuntimeSavedData cursor = new EscrowRuntimeSavedData();
        LedgerSavedData ledger = new LedgerSavedData();
        LedgerTransaction transaction = creditWallet(75L, "one logical credit");
        EscrowJournalEvent event = new EscrowJournalEvent(
                EscrowJournalEventType.LEDGER_APPLY,
                LedgerJournalCodec.encode(transaction));
        EscrowRuntimeCoordinator coordinator = new EscrowRuntimeCoordinator(path, cursor,
                new EscrowSavedDataMutationApplier(
                        new EscrowTransactionSavedData(), ledger, new ClaimSavedData()));
        assertEquals(EscrowRuntimeState.READY, coordinator.start());

        EscrowCommitResult first = coordinator.commit(transaction.transactionId(), event);
        long bytesAfterFirst = Files.size(path);
        long sequenceAfterFirst = cursor.lastAppliedSequence();
        EscrowCommitResult replay = coordinator.commit(transaction.transactionId(), event);

        assertTrue(!first.replayed());
        assertTrue(first.record().isPresent());
        assertTrue(replay.replayed());
        assertTrue(replay.record().isEmpty());
        assertEquals(bytesAfterFirst, Files.size(path));
        assertEquals(sequenceAfterFirst, cursor.lastAppliedSequence());
        coordinator.close();
    }

    @Test
    void insufficientLedgerDebitIsRejectedBeforeJournalAppend() throws Exception {
        Path path = temporaryDirectory.resolve("ledger-preflight.wal");
        EscrowRuntimeSavedData cursor = new EscrowRuntimeSavedData();
        LedgerTransaction transaction = new LedgerTransaction(
                UUID.randomUUID(), "unfunded debit", "hold", List.of(
                new LedgerLeg(new LedgerAccountId(
                        LedgerAccountType.PLAYER_WALLET, "empty"), -10L),
                new LedgerLeg(new LedgerAccountId(
                        LedgerAccountType.TRANSACTION_ESCROW, "transaction"), 10L)));
        EscrowRuntimeCoordinator coordinator = new EscrowRuntimeCoordinator(path, cursor,
                new EscrowSavedDataMutationApplier(
                        new EscrowTransactionSavedData(), new LedgerSavedData(),
                        new ClaimSavedData()));
        assertEquals(EscrowRuntimeState.READY, coordinator.start());
        long lineageBytes = Files.size(path);

        assertThrows(RuntimeException.class, () -> coordinator.commit(
                transaction.transactionId(), new EscrowJournalEvent(
                        EscrowJournalEventType.LEDGER_APPLY,
                        LedgerJournalCodec.encode(transaction))));
        assertEquals(EscrowRuntimeState.READY, coordinator.state());
        assertEquals(1L, cursor.lastAppliedSequence());
        assertEquals(lineageBytes, Files.size(path));
        coordinator.close();
    }

    @Test
    void replaysOldRecordsWhenCursorIsAheadOfMaterializedLedger() {
        Path path = temporaryDirectory.resolve("stale-materialization.wal");
        EscrowRuntimeSavedData cursor = new EscrowRuntimeSavedData();
        LedgerSavedData firstLedger = new LedgerSavedData();
        LedgerTransaction transaction = creditWallet(75L, "stale checkpoint");
        EscrowJournalEvent event = new EscrowJournalEvent(
                EscrowJournalEventType.LEDGER_APPLY, LedgerJournalCodec.encode(transaction));
        LedgerAccountId wallet = transaction.legs().get(1).account();

        EscrowRuntimeCoordinator first = new EscrowRuntimeCoordinator(path, cursor,
                new EscrowSavedDataMutationApplier(
                        new EscrowTransactionSavedData(), firstLedger, new ClaimSavedData()));
        assertEquals(EscrowRuntimeState.READY, first.start());
        first.commit(transaction.transactionId(), event);
        assertEquals(75L, firstLedger.balance(wallet));
        assertEquals(2L, cursor.lastAppliedSequence());
        first.close();

        LedgerSavedData staleLedger = new LedgerSavedData();
        EscrowRuntimeCoordinator recovered = new EscrowRuntimeCoordinator(path, cursor,
                new EscrowSavedDataMutationApplier(
                        new EscrowTransactionSavedData(), staleLedger, new ClaimSavedData()));
        assertEquals(EscrowRuntimeState.RECOVERING, recovered.start(1));
        assertEquals(0L, staleLedger.balance(wallet));
        assertEquals(1, recovered.pendingRecoveryRecords());
        assertEquals(1, recovered.recoverBatch(1));
        assertEquals(EscrowRuntimeState.READY, recovered.state());
        assertEquals(75L, staleLedger.balance(wallet));
        assertEquals(2L, cursor.lastAppliedSequence());
        recovered.close();
    }

    @Test
    void moneySettlementReplaysAfterLedgerAppliedBeforeClaim() {
        Path path = temporaryDirectory.resolve("money-claim.wal");
        EscrowRuntimeSavedData cursor = new EscrowRuntimeSavedData();
        LedgerSavedData ledger = new LedgerSavedData();
        ClaimSavedData claims = new ClaimSavedData();
        UUID ownerId = UUID.randomUUID();
        UUID claimId = UUID.randomUUID();
        UUID escrowTransactionId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-16T12:00:00Z");
        EscrowClaim claim = new EscrowClaim(
                claimId, escrowTransactionId, ownerId, "source " + claimId, ClaimKind.MONEY,
                100L, 100L, new byte[0], ClaimStatus.PENDING, "Money", now, now);
        claims.createCommitted(claim);
        LedgerAccountId claimAccount = new LedgerAccountId(
                LedgerAccountType.PLAYER_CLAIM, claimId.toString());
        LedgerAccountId walletAccount = new LedgerAccountId(
                LedgerAccountType.PLAYER_WALLET, ownerId.toString());
        ledger.applyCommitted(new LedgerTransaction(
                UUID.randomUUID(), "seed claim", "seed", List.of(
                new LedgerLeg(LedgerAccountId.system(LedgerAccountType.ADMIN_SOURCE), -100L),
                new LedgerLeg(claimAccount, 100L))));
        UUID requestId = UUID.randomUUID();
        MoneyClaimSettlement settlement = MoneyClaimSettlement.create(
                requestId, ownerId, claimId, 0L, 0L, 0L,
                100L, 100L, 1L, now.plusSeconds(1));
        LedgerTransaction settlementLedger = settlement.ledgerTransaction();
        EscrowJournalEvent event = new EscrowJournalEvent(
                EscrowJournalEventType.MONEY_CLAIM_SETTLEMENT,
                MoneyClaimSettlementCodec.encode(settlement));

        EscrowRuntimeCoordinator first = new EscrowRuntimeCoordinator(path, cursor,
                (record, mutation) -> {
                    MoneyClaimSettlement decoded = MoneyClaimSettlementCodec.decode(mutation.body());
                    ledger.applyCommitted(decoded.ledgerTransaction());
                    throw new IllegalStateException("simulated crash after ledger");
                });
        assertEquals(EscrowRuntimeState.READY, first.start());
        assertThrows(EscrowRuntimeException.class,
                () -> first.commit(settlement.requestId(), event));
        assertEquals(100L, ledger.balance(walletAccount));
        assertEquals(100L, claims.getClaim(claimId).remainingUnits());
        assertEquals(1L, cursor.lastAppliedSequence());
        first.close();

        EscrowRuntimeCoordinator recovered = new EscrowRuntimeCoordinator(path, cursor,
                new EscrowSavedDataMutationApplier(
                        new EscrowTransactionSavedData(), ledger, claims));
        assertEquals(EscrowRuntimeState.READY, recovered.start(10));
        assertEquals(100L, ledger.balance(walletAccount));
        EscrowClaim completed = claims.getClaim(claimId);
        assertNotNull(completed);
        assertEquals(0L, completed.remainingUnits());
        assertEquals(ClaimStatus.COMPLETED, completed.status());
        assertEquals(2L, cursor.lastAppliedSequence());
        recovered.close();
    }

    @Test
    void rejectsJournalWithoutLineageAtSequenceOne() throws Exception {
        Path path = temporaryDirectory.resolve("missing-lineage.wal");
        UUID transactionId = UUID.randomUUID();
        EscrowJournalEvent event = new EscrowJournalEvent(
                EscrowJournalEventType.LEDGER_APPLY, new byte[]{9});
        try (WriteAheadJournal journal = WriteAheadJournal.open(path)) {
            journal.append(transactionId, EscrowStepIds.forEvent(transactionId, event),
                    EscrowJournalEventCodec.encode(event));
        }

        EscrowRuntimeCoordinator coordinator = new EscrowRuntimeCoordinator(
                path, new EscrowRuntimeSavedData(), (record, mutation) -> {
                });
        assertEquals(EscrowRuntimeState.MAINTENANCE, coordinator.start());
        assertTrue(coordinator.failure().isPresent());
        coordinator.close();
    }

    @Test
    void missingJournalWithPersistedCursorFailsClosed() {
        Path path = temporaryDirectory.resolve("deleted-journal.wal");
        EscrowRuntimeSavedData cursor = new EscrowRuntimeSavedData();
        UUID lineageId = UUID.randomUUID();
        cursor.establishLineage(lineageId, 1L);

        EscrowRuntimeCoordinator coordinator = new EscrowRuntimeCoordinator(
                path, cursor, (record, mutation) -> {
                });
        assertEquals(EscrowRuntimeState.MAINTENANCE, coordinator.start());
        assertTrue(coordinator.failure().isPresent());
        coordinator.close();
    }

    @Test
    void missingJournalWithMaterializedStateFailsClosed() {
        Path path = temporaryDirectory.resolve("orphaned-materialization.wal");
        EscrowRuntimeCoordinator coordinator = new EscrowRuntimeCoordinator(
                path, new EscrowRuntimeSavedData(), (record, mutation) -> {
                }, () -> true);

        assertEquals(EscrowRuntimeState.MAINTENANCE, coordinator.start());
        assertTrue(coordinator.failure().isPresent());
        coordinator.close();
    }

    @Test
    void stepIdsAreStableAndPayloadSensitive() {
        UUID transactionId = UUID.randomUUID();
        EscrowJournalEvent first = new EscrowJournalEvent(
                EscrowJournalEventType.CLAIM_CREATE, new byte[]{1, 2});
        EscrowJournalEvent second = new EscrowJournalEvent(
                EscrowJournalEventType.CLAIM_CREATE, new byte[]{1, 3});

        assertEquals(EscrowStepIds.forEvent(transactionId, first),
                EscrowStepIds.forEvent(transactionId, first));
        assertTrue(!EscrowStepIds.forEvent(transactionId, first)
                .equals(EscrowStepIds.forEvent(transactionId, second)));
    }

    @Test
    void administrativeAuditIsJournaledAndRebuiltFromAnOldCursor() {
        Path path = temporaryDirectory.resolve("administrative-audit.wal");
        EscrowRuntimeSavedData cursor = new EscrowRuntimeSavedData();
        EscrowAdministrativeAuditSavedData firstAudit = new EscrowAdministrativeAuditSavedData();
        EscrowAdministrativeRecord record = new EscrowAdministrativeRecord(
                UUID.randomUUID(), "console", EscrowAdministrativeAction.ENTER_MAINTENANCE,
                Optional.empty(), "Storage inspection",
                Instant.parse("2026-07-16T12:00:00.123456789Z"),
                true, "Maintenance requested");
        EscrowJournalEvent event = new EscrowJournalEvent(
                EscrowJournalEventType.ADMIN_AUDIT,
                AdministrativeAuditJournalCodec.encode(record));

        EscrowRuntimeCoordinator first = new EscrowRuntimeCoordinator(path, cursor,
                new EscrowSavedDataMutationApplier(
                        new EscrowTransactionSavedData(), new LedgerSavedData(),
                        new ClaimSavedData(), firstAudit));
        assertEquals(EscrowRuntimeState.READY, first.start());
        first.commit(record.requestId(), event);
        assertEquals(record, firstAudit.getRecord(record.requestId()));
        assertEquals(2L, cursor.lastAppliedSequence());
        first.close();

        EscrowAdministrativeAuditSavedData checkpoint =
                EscrowAdministrativeAuditSavedData.load(firstAudit.save(new CompoundTag()));
        EscrowRuntimeCoordinator checkpointReplay = new EscrowRuntimeCoordinator(path, cursor,
                new EscrowSavedDataMutationApplier(
                        new EscrowTransactionSavedData(), new LedgerSavedData(),
                        new ClaimSavedData(), checkpoint));
        assertEquals(EscrowRuntimeState.READY, checkpointReplay.start(10));
        assertEquals(record, checkpoint.getRecord(record.requestId()));
        checkpointReplay.close();

        EscrowAdministrativeAuditSavedData staleAudit = new EscrowAdministrativeAuditSavedData();
        EscrowRuntimeCoordinator recovered = new EscrowRuntimeCoordinator(path, cursor,
                new EscrowSavedDataMutationApplier(
                        new EscrowTransactionSavedData(), new LedgerSavedData(),
                        new ClaimSavedData(), staleAudit));
        assertEquals(EscrowRuntimeState.READY, recovered.start(10));
        assertEquals(record, staleAudit.getRecord(record.requestId()));
        recovered.close();
    }

    @Test
    void claimNanosecondsSurviveCheckpointAndFullReplay() {
        Path path = temporaryDirectory.resolve("claim-nanoseconds.wal");
        EscrowRuntimeSavedData cursor = new EscrowRuntimeSavedData();
        ClaimSavedData claims = new ClaimSavedData();
        Instant now = Instant.parse("2026-07-16T12:00:00.123456789Z");
        EscrowClaim claim = new EscrowClaim(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "source " + UUID.randomUUID(), ClaimKind.ITEM,
                1L, 1L, new byte[]{7}, ClaimStatus.PENDING, "Item", now, now);
        EscrowJournalEvent event = new EscrowJournalEvent(
                EscrowJournalEventType.CLAIM_CREATE, ClaimJournalCodec.encodeClaim(claim));

        EscrowRuntimeCoordinator first = new EscrowRuntimeCoordinator(path, cursor,
                new EscrowSavedDataMutationApplier(
                        new EscrowTransactionSavedData(), new LedgerSavedData(), claims));
        assertEquals(EscrowRuntimeState.READY, first.start());
        first.commit(claim.transactionId(), event);
        first.close();

        ClaimSavedData checkpoint = ClaimSavedData.load(claims.save(new CompoundTag()));
        assertEquals(now, checkpoint.getClaim(claim.claimId()).createdAt());
        EscrowRuntimeCoordinator recovered = new EscrowRuntimeCoordinator(path, cursor,
                new EscrowSavedDataMutationApplier(
                        new EscrowTransactionSavedData(), new LedgerSavedData(), checkpoint));
        assertEquals(EscrowRuntimeState.READY, recovered.start(10));
        assertEquals(now, checkpoint.getClaim(claim.claimId()).createdAt());
        recovered.close();
    }

    @Test
    void claimQuarantineIsJournaledAndIdempotentOnReplay() {
        Path path = temporaryDirectory.resolve("claim-quarantine.wal");
        EscrowRuntimeSavedData cursor = new EscrowRuntimeSavedData();
        ClaimSavedData claims = new ClaimSavedData();
        Instant createdAt = Instant.parse("2026-07-16T12:00:00.123456789Z");
        UUID transactionId = UUID.randomUUID();
        EscrowClaim claim = new EscrowClaim(
                UUID.randomUUID(), transactionId, UUID.randomUUID(),
                "source " + UUID.randomUUID(), ClaimKind.ITEM,
                1L, 1L, new byte[]{7}, ClaimStatus.PENDING, "Item",
                createdAt, createdAt);
        claims.createCommitted(claim);
        ClaimQuarantineCommit quarantine = ClaimQuarantineCommit.create(
                claim.ownerId(), claim.claimId(), transactionId,
                createdAt.plusSeconds(2).plusNanos(11), "MISSING_ITEM_REGISTRY_ENTRY");
        EscrowJournalEvent event = new EscrowJournalEvent(
                EscrowJournalEventType.CLAIM_QUARANTINE,
                ClaimJournalCodec.encodeQuarantine(quarantine));

        EscrowRuntimeCoordinator first = new EscrowRuntimeCoordinator(path, cursor,
                new EscrowSavedDataMutationApplier(
                        new EscrowTransactionSavedData(), new LedgerSavedData(), claims));
        assertEquals(EscrowRuntimeState.READY, first.start());
        first.commit(transactionId, event);
        assertEquals(ClaimStatus.QUARANTINED, claims.getClaim(claim.claimId()).status());
        assertEquals(quarantine.quarantinedAt(), claims.getClaim(claim.claimId()).updatedAt());
        first.close();

        EscrowRuntimeCoordinator replay = new EscrowRuntimeCoordinator(path, cursor,
                new EscrowSavedDataMutationApplier(
                        new EscrowTransactionSavedData(), new LedgerSavedData(), claims));
        assertEquals(EscrowRuntimeState.READY, replay.start(10));
        assertEquals(ClaimStatus.QUARANTINED, claims.getClaim(claim.claimId()).status());
        assertEquals(quarantine.quarantinedAt(), claims.getClaim(claim.claimId()).updatedAt());
        replay.close();
    }

    @Test
    void custodyPrepareSurvivesCrashAndMutationResolvesAfterReplay() {
        Path path = temporaryDirectory.resolve("custody.wal");
        EscrowRuntimeSavedData cursor = new EscrowRuntimeSavedData();
        CustodySavedData firstCustody = new CustodySavedData();
        UUID transactionId = UUID.randomUUID();
        CustodyTransferEvidence evidence = new CustodyTransferEvidence(
                CustodyEndpointEvidence.captured(
                        "wallet", CustodyAdapterCapability.TRANSACTIONAL_PROTECTED,
                        "player", "available", new byte[]{1}, new byte[]{2}, "source mutation"),
                CustodyEndpointEvidence.captured(
                        "escrow", CustodyAdapterCapability.TRANSACTIONAL_PROTECTED,
                        transactionId.toString(), "held", new byte[]{3}, new byte[]{4},
                        "destination mutation"));
        CustodyLot lot = CustodyLot.held(
                UUID.randomUUID(), transactionId, "reserve custody",
                CustodyAssetType.WALLET_RESERVE, CustodyProtectionTier.PROTECTED, 90L,
                CustodyLot.BUILT_IN_CURRENCY_PROVIDER, List.of(), List.of(), evidence,
                Instant.parse("2026-07-16T12:00:00Z"));
        CustodyMutation mutation = CustodyMutation.reserve(lot);
        CustodyPreparedOperation prepared = CustodyPreparedOperation.prepare(
                CustodyOperation.RESERVE, lot.reserveRequestKey(), lot,
                evidence.source().adapterId(), evidence.source().capability(),
                "simulation token", evidence, lot.createdAt());
        EscrowJournalEvent prepareEvent = new EscrowJournalEvent(
                EscrowJournalEventType.CUSTODY_PREPARE,
                CustodyPreparedOperationCodec.encode(prepared));
        EscrowJournalEvent mutationEvent = new EscrowJournalEvent(
                EscrowJournalEventType.CUSTODY_MUTATION,
                CustodyMutationCodec.encode(mutation));

        EscrowRuntimeCoordinator first = new EscrowRuntimeCoordinator(path, cursor,
                new EscrowSavedDataMutationApplier(
                        new EscrowTransactionSavedData(), new LedgerSavedData(),
                        new ClaimSavedData(), new EscrowAdministrativeAuditSavedData(),
                        firstCustody));
        assertEquals(EscrowRuntimeState.READY, first.start());
        first.commit(transactionId, prepareEvent);
        assertEquals(1, firstCustody.unresolvedPreparedOperations(10).size());
        assertEquals(2L, cursor.lastAppliedSequence());
        first.close();

        CustodySavedData staleCustody = new CustodySavedData();
        EscrowSavedDataMutationApplier recoveredApplier = new EscrowSavedDataMutationApplier(
                new EscrowTransactionSavedData(), new LedgerSavedData(),
                new ClaimSavedData(), new EscrowAdministrativeAuditSavedData(),
                staleCustody);
        EscrowRuntimeCoordinator recovered = new EscrowRuntimeCoordinator(path, cursor,
                recoveredApplier);
        assertEquals(EscrowRuntimeState.READY, recovered.start(10));
        assertEquals(1, staleCustody.unresolvedPreparedOperations(10).size());
        staleCustody.validatePreparedProof(mutation);
        recovered.commit(transactionId, mutationEvent);
        assertEquals(lot, staleCustody.getLot(lot.lotId()));
        assertTrue(staleCustody.unresolvedPreparedOperations(10).isEmpty());
        recovered.close();

        CustodySavedData rebuiltCustody = new CustodySavedData();
        EscrowRuntimeCoordinator rebuilt = new EscrowRuntimeCoordinator(path, cursor,
                new EscrowSavedDataMutationApplier(
                        new EscrowTransactionSavedData(), new LedgerSavedData(),
                        new ClaimSavedData(), new EscrowAdministrativeAuditSavedData(),
                        rebuiltCustody));
        assertEquals(EscrowRuntimeState.READY, rebuilt.start(10));
        assertEquals(lot, rebuiltCustody.getLot(lot.lotId()));
        assertTrue(rebuiltCustody.unresolvedPreparedOperations(10).isEmpty());
        rebuilt.close();
    }

    @Test
    void atomicCustodyBatchRecoversFromTheApplyingCrashBoundary() {
        Path path = temporaryDirectory.resolve("custody-batch.wal");
        EscrowRuntimeSavedData cursor = new EscrowRuntimeSavedData();
        UUID transactionId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-16T12:00:00.123456789Z");
        CustodyTransferEvidence firstEvidence = walletEvidence(
                transactionId, "first wallet mutation");
        CustodyTransferEvidence secondEvidence = walletEvidence(
                transactionId, "second wallet mutation");
        CustodyLot firstLot = CustodyLot.held(UUID.randomUUID(), transactionId,
                "atomic first", CustodyAssetType.WALLET_RESERVE,
                CustodyProtectionTier.PROTECTED, 40L,
                CustodyLot.BUILT_IN_CURRENCY_PROVIDER, List.of(), List.of(), firstEvidence, now);
        CustodyLot secondLot = CustodyLot.held(UUID.randomUUID(), transactionId,
                "atomic second", CustodyAssetType.WALLET_RESERVE,
                CustodyProtectionTier.PROTECTED, 60L,
                CustodyLot.BUILT_IN_CURRENCY_PROVIDER, List.of(), List.of(), secondEvidence, now);
        CustodyBatchPlan plan = CustodyBatchPlan.create(CustodyOperation.RESERVE,
                "atomic wallet batch", List.of(firstLot, secondLot));
        Map<UUID, CustodyTransferEvidence> evidence = Map.of(
                firstLot.lotId(), firstEvidence, secondLot.lotId(), secondEvidence);
        CustodyPreparedBatch prepared = CustodyPreparedBatch.prepare(
                plan, "atomic wallet token", evidence, now);
        CustodyPreparedBatch applying = prepared.markApplying(0L, now.plusNanos(1));
        CustodyPreparedBatch applied = applying.markApplied(1L, evidence, now.plusNanos(2));
        CustodyBatchCommit appliedCommit = CustodyBatchCommit.applied(applied, List.of(
                CustodyMutation.reserve(firstLot), CustodyMutation.reserve(secondLot)));
        CustodySavedData firstCustody = new CustodySavedData();

        EscrowRuntimeCoordinator first = new EscrowRuntimeCoordinator(path, cursor,
                new EscrowSavedDataMutationApplier(
                        new EscrowTransactionSavedData(), new LedgerSavedData(),
                        new ClaimSavedData(), new EscrowAdministrativeAuditSavedData(),
                        firstCustody));
        assertEquals(EscrowRuntimeState.READY, first.start());
        first.commit(transactionId, custodyBatchEvent(CustodyBatchCommit.state(prepared)));
        first.commit(transactionId, custodyBatchEvent(CustodyBatchCommit.state(applying)));
        assertEquals(CustodyBatchStatus.APPLYING,
                firstCustody.unresolvedPreparedBatches(1).get(0).status());
        first.close();

        CustodySavedData recoveredCustody = new CustodySavedData();
        EscrowRuntimeCoordinator recovered = new EscrowRuntimeCoordinator(path, cursor,
                new EscrowSavedDataMutationApplier(
                        new EscrowTransactionSavedData(), new LedgerSavedData(),
                        new ClaimSavedData(), new EscrowAdministrativeAuditSavedData(),
                        recoveredCustody));
        assertEquals(EscrowRuntimeState.READY, recovered.start(10));
        assertEquals(CustodyBatchStatus.APPLYING,
                recoveredCustody.unresolvedPreparedBatches(1).get(0).status());
        recovered.commit(transactionId, custodyBatchEvent(appliedCommit));
        assertEquals(firstLot, recoveredCustody.getLot(firstLot.lotId()));
        assertEquals(secondLot, recoveredCustody.getLot(secondLot.lotId()));
        assertTrue(recoveredCustody.unresolvedPreparedBatches(1).isEmpty());
        recovered.close();

        CustodySavedData rebuiltCustody = new CustodySavedData();
        EscrowRuntimeCoordinator rebuilt = new EscrowRuntimeCoordinator(path, cursor,
                new EscrowSavedDataMutationApplier(
                        new EscrowTransactionSavedData(), new LedgerSavedData(),
                        new ClaimSavedData(), new EscrowAdministrativeAuditSavedData(),
                        rebuiltCustody));
        assertEquals(EscrowRuntimeState.READY, rebuilt.start(10));
        assertEquals(firstLot, rebuiltCustody.getLot(firstLot.lotId()));
        assertEquals(secondLot, rebuiltCustody.getLot(secondLot.lotId()));
        assertTrue(rebuiltCustody.unresolvedPreparedBatches(1).isEmpty());
        rebuilt.close();
    }

    @Test
    void protectedMintEventIsJournaledAndRebuiltFromAnOldCursor() {
        Path path = temporaryDirectory.resolve("protected-mint.wal");
        EscrowRuntimeSavedData cursor = new EscrowRuntimeSavedData();
        ProtectedMintSavedData firstMints = new ProtectedMintSavedData();
        UUID batchId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-16T12:00:00.123456789Z");
        ProtectedMintBatch batch = ProtectedMintBatch.plan(
                batchId, transactionId, "mint authorize", 100L, 1,
                "server identity", now,
                (mintId, transaction, denomination, authorizedCount, server, authorizedAt) ->
                        "checksum " + mintId + " " + transaction);
        ProtectedMintJournalEvent mintEvent = ProtectedMintJournalEvent.authorize(batch);
        EscrowJournalEvent event = new EscrowJournalEvent(
                EscrowJournalEventType.PROTECTED_MINT,
                ProtectedMintEventCodec.encode(mintEvent));

        EscrowRuntimeCoordinator first = new EscrowRuntimeCoordinator(path, cursor,
                new EscrowSavedDataMutationApplier(
                        new EscrowTransactionSavedData(), new LedgerSavedData(),
                        new ClaimSavedData(), new EscrowAdministrativeAuditSavedData(),
                        new CustodySavedData(), firstMints));
        assertEquals(EscrowRuntimeState.READY, first.start());
        first.commit(transactionId, event);
        assertEquals(batch, firstMints.getBatch(batchId));
        first.close();

        ProtectedMintSavedData staleMints = new ProtectedMintSavedData();
        EscrowRuntimeCoordinator recovered = new EscrowRuntimeCoordinator(path, cursor,
                new EscrowSavedDataMutationApplier(
                        new EscrowTransactionSavedData(), new LedgerSavedData(),
                        new ClaimSavedData(), new EscrowAdministrativeAuditSavedData(),
                        new CustodySavedData(), staleMints));
        assertEquals(EscrowRuntimeState.READY, recovered.start(10));
        assertEquals(batch, staleMints.getBatch(batchId));
        recovered.close();
    }

    private static LedgerTransaction creditWallet(long units, String key) {
        return new LedgerTransaction(UUID.randomUUID(), key, "credit", List.of(
                new LedgerLeg(LedgerAccountId.system(LedgerAccountType.ADMIN_SOURCE), -units),
                new LedgerLeg(new LedgerAccountId(LedgerAccountType.PLAYER_WALLET, "player"), units)));
    }

    private static EscrowJournalEvent custodyBatchEvent(CustodyBatchCommit commit) {
        return new EscrowJournalEvent(EscrowJournalEventType.CUSTODY_BATCH,
                CustodyBatchCommitCodec.encode(commit));
    }

    private static CustodyTransferEvidence walletEvidence(UUID transactionId, String token) {
        return new CustodyTransferEvidence(
                CustodyEndpointEvidence.captured(
                        "wallet", CustodyAdapterCapability.TRANSACTIONAL_PROTECTED,
                        "player", "wallet", new byte[]{1}, new byte[]{2}, token),
                CustodyEndpointEvidence.captured(
                        "escrow", CustodyAdapterCapability.TRANSACTIONAL_PROTECTED,
                        transactionId.toString(), "held", new byte[]{3}, new byte[]{4},
                        token + " destination"));
    }
}
