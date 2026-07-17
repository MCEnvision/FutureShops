package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.admin.EscrowAdministrativeAuditSavedData;
import com.enviouse.futureshops.server.escrow.admin.MaintenanceClaimRepairDisposition;
import com.enviouse.futureshops.server.escrow.admin.MaintenanceCustodyDisposition;
import com.enviouse.futureshops.server.escrow.admin.MaintenanceExpectedState;
import com.enviouse.futureshops.server.escrow.admin.MaintenanceRepairCommand;
import com.enviouse.futureshops.server.escrow.admin.MaintenanceRepairPayload;
import com.enviouse.futureshops.server.escrow.admin.MaintenanceRepairTarget;
import com.enviouse.futureshops.server.escrow.admin.MaintenanceStateFingerprint;
import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.claim.ClaimSavedData;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.custody.CustodyAdapterCapability;
import com.enviouse.futureshops.server.escrow.custody.CustodyAssetType;
import com.enviouse.futureshops.server.escrow.custody.CustodyBatchPlan;
import com.enviouse.futureshops.server.escrow.custody.CustodyBatchStatus;
import com.enviouse.futureshops.server.escrow.custody.CustodyEndpointEvidence;
import com.enviouse.futureshops.server.escrow.custody.CustodyLot;
import com.enviouse.futureshops.server.escrow.custody.CustodyOperation;
import com.enviouse.futureshops.server.escrow.custody.CustodyPreparedBatch;
import com.enviouse.futureshops.server.escrow.custody.CustodyProtectionTier;
import com.enviouse.futureshops.server.escrow.custody.CustodySavedData;
import com.enviouse.futureshops.server.escrow.custody.CustodyTransferEvidence;
import com.enviouse.futureshops.server.escrow.journal.JournalRecord;
import com.enviouse.futureshops.server.escrow.ledger.LedgerSavedData;
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
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintSavedData;
import com.enviouse.futureshops.server.escrow.store.EscrowTransactionSavedData;
import org.junit.jupiter.api.Test;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaintenanceRepairProcessorTest {
    private static final Instant BASE = Instant.parse("2026-07-17T12:00:00Z");

    @Test
    void failedOrUnconfirmedCommandsJournalOnlyTheirAudit() {
        Harness harness = harness(MaintenanceRuntimeMutationHandler.unavailable());
        MaintenanceRepairCommand command = command(UUID.randomUUID(),
                MaintenanceRepairTarget.transaction(UUID.randomUUID()),
                MaintenanceExpectedState.revision(99L),
                new MaintenanceRepairPayload.RetryReset(), false, false,
                BASE.plusSeconds(20));

        EscrowJournalEvent event = harness.applier().planMaintenanceRepair(command);

        assertEquals(EscrowPreflightResult.APPLY,
                harness.applier().preflight(command.commandId(), event));
        apply(harness.applier(), command, event, 1L);
        assertEquals(command.auditRecord(),
                harness.audit().getRecord(command.commandId()));
        assertEquals(EscrowPreflightResult.REPLAY,
                harness.applier().preflight(command.commandId(), event));
    }

    @Test
    void retryResetIsOneExactMutationAndReplayIsIdempotent() {
        Harness harness = harness(MaintenanceRuntimeMutationHandler.unavailable());
        EscrowTransaction recovery = recoveryTransaction(harness.transactions());
        MaintenanceRepairCommand command = command(UUID.randomUUID(),
                MaintenanceRepairTarget.transaction(recovery.transactionId().value()),
                MaintenanceExpectedState.revision(recovery.revision()),
                new MaintenanceRepairPayload.RetryReset(), true, true,
                BASE.plusSeconds(40));
        EscrowJournalEvent event = harness.applier().planMaintenanceRepair(command);

        assertEquals(EscrowPreflightResult.APPLY,
                harness.applier().preflight(command.commandId(), event));
        apply(harness.applier(), command, event, 2L);

        EscrowTransaction repaired = harness.transactions().getTransaction(
                recovery.transactionId());
        assertEquals(EscrowState.HELD, repaired.state());
        assertEquals(recovery.revision() + 1L, repaired.revision());
        assertFalse(repaired.retryMetadata().isScheduled());
        assertNotNull(harness.audit().getRecord(command.commandId()));

        apply(harness.applier(), command, event, 2L);
        assertEquals(repaired, harness.transactions().getTransaction(
                recovery.transactionId()));
        assertEquals(EscrowPreflightResult.REPLAY,
                harness.applier().preflight(command.commandId(), event));
    }

    @Test
    void optimisticRevisionAndFingerprintMismatchesFailBeforeAppend() {
        Harness harness = harness(MaintenanceRuntimeMutationHandler.unavailable());
        EscrowTransaction recovery = recoveryTransaction(harness.transactions());
        MaintenanceRepairCommand revisionMismatch = command(UUID.randomUUID(),
                MaintenanceRepairTarget.transaction(recovery.transactionId().value()),
                MaintenanceExpectedState.revision(recovery.revision() - 1L),
                new MaintenanceRepairPayload.RetryReset(), true, true,
                BASE.plusSeconds(40));

        assertThrows(EscrowRuntimeException.class,
                () -> harness.applier().planMaintenanceRepair(revisionMismatch));
        assertEquals(null, harness.audit().getRecord(revisionMismatch.commandId()));

        EscrowClaim claim = claim(UUID.randomUUID(), recovery.transactionId().value(),
                3L, 3L, ClaimStatus.PENDING, BASE.plusSeconds(5));
        harness.claims().createCommitted(claim);
        MaintenanceRepairCommand fingerprintMismatch = command(UUID.randomUUID(),
                MaintenanceRepairTarget.claim(claim.claimId()),
                MaintenanceExpectedState.fingerprint(bytes(31)),
                new MaintenanceRepairPayload.ClaimQuarantine(), true, true,
                BASE.plusSeconds(41));

        assertThrows(EscrowRuntimeException.class,
                () -> harness.applier().planMaintenanceRepair(fingerprintMismatch));
        assertEquals(null, harness.audit().getRecord(fingerprintMismatch.commandId()));
    }

    @Test
    void claimQuarantineAndReopenPreserveEveryOutstandingUnit() {
        Harness harness = harness(MaintenanceRuntimeMutationHandler.unavailable());
        UUID transactionId = UUID.randomUUID();
        EscrowClaim claim = claim(UUID.randomUUID(), transactionId,
                4L, 4L, ClaimStatus.PENDING, BASE);
        harness.claims().createCommitted(claim);

        MaintenanceRepairCommand quarantine = command(UUID.randomUUID(),
                MaintenanceRepairTarget.claim(claim.claimId()), expectedFingerprint(
                        harness.applier(), MaintenanceRepairTarget.claim(claim.claimId())),
                new MaintenanceRepairPayload.ClaimQuarantine(), true, true,
                BASE.plusSeconds(1));
        EscrowJournalEvent quarantineEvent = harness.applier()
                .planMaintenanceRepair(quarantine);
        apply(harness.applier(), quarantine, quarantineEvent, 3L);

        EscrowClaim quarantined = harness.claims().getClaim(claim.claimId());
        assertEquals(ClaimStatus.QUARANTINED, quarantined.status());
        assertEquals(4L, quarantined.remainingUnits());

        MaintenanceRepairCommand reopen = command(UUID.randomUUID(),
                MaintenanceRepairTarget.claim(claim.claimId()), expectedFingerprint(
                        harness.applier(), MaintenanceRepairTarget.claim(claim.claimId())),
                new MaintenanceRepairPayload.ClaimRepair(
                        MaintenanceClaimRepairDisposition.REOPEN_PENDING, 4L),
                true, true, BASE.plusSeconds(2));
        apply(harness.applier(), reopen,
                harness.applier().planMaintenanceRepair(reopen), 4L);

        EscrowClaim reopened = harness.claims().getClaim(claim.claimId());
        assertEquals(ClaimStatus.PENDING, reopened.status());
        assertEquals(4L, reopened.remainingUnits());
        assertEquals(claim.payload().length, reopened.payload().length);

        MaintenanceRepairCommand destructive = command(UUID.randomUUID(),
                MaintenanceRepairTarget.claim(claim.claimId()), expectedFingerprint(
                        harness.applier(), MaintenanceRepairTarget.claim(claim.claimId())),
                new MaintenanceRepairPayload.ClaimRepair(
                        MaintenanceClaimRepairDisposition.REOPEN_PARTIAL, 2L),
                true, true, BASE.plusSeconds(3));
        assertThrows(EscrowRuntimeException.class,
                () -> harness.applier().planMaintenanceRepair(destructive));
        assertEquals(4L, harness.claims().getClaim(claim.claimId()).remainingUnits());
    }

    @Test
    void custodyVerificationAndBatchQuarantineNeverInventTransferEvidence() {
        Harness harness = harness(MaintenanceRuntimeMutationHandler.unavailable());
        CustodyLot lot = walletLot(UUID.randomUUID(), "maintenance reserve");
        harness.custody().reserveCommitted(lot);
        MaintenanceRepairTarget lotTarget = MaintenanceRepairTarget.custodyLot(lot.lotId());
        MaintenanceRepairCommand verify = command(UUID.randomUUID(), lotTarget,
                MaintenanceExpectedState.revision(0L),
                new MaintenanceRepairPayload.CustodyReconcile(
                        MaintenanceStateFingerprint.of(lot.assetFingerprint()),
                        MaintenanceCustodyDisposition.CONFIRM_HELD),
                true, true, BASE.plusSeconds(10));
        apply(harness.applier(), verify,
                harness.applier().planMaintenanceRepair(verify), 5L);
        assertEquals(lot, harness.custody().getLot(lot.lotId()));
        assertTrue(harness.custody().conservation().conserved());

        MaintenanceRepairCommand releaseWithoutEvidence = command(UUID.randomUUID(), lotTarget,
                MaintenanceExpectedState.revision(0L),
                new MaintenanceRepairPayload.CustodyReconcile(
                        MaintenanceStateFingerprint.of(lot.assetFingerprint()),
                        MaintenanceCustodyDisposition.MARK_RELEASED),
                true, true, BASE.plusSeconds(11));
        assertThrows(EscrowRuntimeException.class,
                () -> harness.applier().planMaintenanceRepair(releaseWithoutEvidence));

        MaintenanceRepairCommand quarantineLot = command(UUID.randomUUID(), lotTarget,
                MaintenanceExpectedState.revision(0L),
                new MaintenanceRepairPayload.CustodyQuarantine(), true, true,
                BASE.plusSeconds(11));
        assertThrows(EscrowRuntimeException.class,
                () -> harness.applier().planMaintenanceRepair(quarantineLot));

        CustodyPreparedBatch batch = preparedBatch();
        harness.custody().applyBatchCommitted(batch);
        MaintenanceRepairTarget batchTarget = MaintenanceRepairTarget.custodyBatch(
                batch.batchId());
        MaintenanceRepairCommand quarantineBatch = command(UUID.randomUUID(), batchTarget,
                MaintenanceExpectedState.revision(0L),
                new MaintenanceRepairPayload.CustodyQuarantine(), true, true,
                BASE.plusSeconds(20));
        apply(harness.applier(), quarantineBatch,
                harness.applier().planMaintenanceRepair(quarantineBatch), 6L);

        assertEquals(CustodyBatchStatus.QUARANTINED,
                harness.custody().getPreparedBatch(batch.batchId()).status());
        assertTrue(harness.custody().conservation().conserved());
    }

    @Test
    void forceRefundTransitionsAndForceSettlementJournalsARejection() {
        Harness refundHarness = harness(MaintenanceRuntimeMutationHandler.unavailable());
        EscrowTransaction created = createdTransaction(UUID.randomUUID(), BASE);
        EscrowTransaction aborting = created.transitionTo(
                EscrowState.ABORTING, BASE.plusSeconds(1));
        materialize(refundHarness.transactions(), created, aborting);
        MaintenanceRepairCommand refund = command(UUID.randomUUID(),
                MaintenanceRepairTarget.transaction(aborting.transactionId().value()),
                MaintenanceExpectedState.revision(aborting.revision()),
                new MaintenanceRepairPayload.ForceRefund(),
                true, true, BASE.plusSeconds(2));
        apply(refundHarness.applier(), refund,
                refundHarness.applier().planMaintenanceRepair(refund), 7L);
        assertEquals(EscrowState.REFUND_PENDING,
                refundHarness.transactions().getTransaction(
                        aborting.transactionId()).state());

        Harness settlementHarness = harness(MaintenanceRuntimeMutationHandler.unavailable());
        EscrowTransaction settlementRecovery = claimsCreatedRecovery(
                settlementHarness.transactions());
        EscrowClaim settlementClaim = claim(UUID.randomUUID(),
                settlementRecovery.transactionId().value(), 1L, 1L,
                ClaimStatus.PENDING, BASE.plusSeconds(7));
        settlementHarness.claims().createCommitted(settlementClaim);
        MaintenanceRepairCommand settlement = command(UUID.randomUUID(),
                MaintenanceRepairTarget.transaction(
                        settlementRecovery.transactionId().value()),
                MaintenanceExpectedState.revision(settlementRecovery.revision()),
                new MaintenanceRepairPayload.ForceSettlement(),
                true, true, BASE.plusSeconds(20));
        EscrowJournalEvent settlementEvent = settlementHarness.applier()
                .planMaintenanceRepair(settlement);
        MaintenanceRepairJournalEntry planned = MaintenanceRepairJournalCodec.decode(
                settlementEvent.body());

        assertFalse(planned.command().appliesAction());
        assertFalse(planned.command().auditRecord().successful());
        assertEquals(MaintenanceRepairProcessor.FORCE_SETTLEMENT_REJECTION,
                planned.command().auditRecord().outcome());
        assertTrue(planned.effect() instanceof MaintenanceRepairJournalEntry.AuditOnly);

        apply(settlementHarness.applier(), settlement, settlementEvent, 8L);
        assertEquals(EscrowState.RECOVERY_REQUIRED,
                settlementHarness.transactions().getTransaction(
                        settlementRecovery.transactionId()).state());
        assertEquals(planned.command().auditRecord(), settlementHarness.audit()
                .getRecord(settlement.commandId()));
        assertEquals(EscrowPreflightResult.REPLAY,
                settlementHarness.applier().preflight(
                        settlement.commandId(), settlementEvent));
    }

    @Test
    void resumeRequiresTheInjectedGlobalVerificationAndReplaysExactly() {
        MaintenanceStateFingerprint current = fingerprint(40);
        MaintenanceStateFingerprint verified = fingerprint(80);
        VerifiedRuntimeHandler handler = new VerifiedRuntimeHandler(
                new MaintenanceRuntimeSnapshot(7L, current), 91L, verified);
        Harness harness = harness(handler);
        MaintenanceRepairCommand command = command(UUID.randomUUID(),
                MaintenanceRepairTarget.runtime(), MaintenanceExpectedState.revision(7L),
                new MaintenanceRepairPayload.VerifyAndResume(91L, verified),
                true, true, BASE.plusSeconds(30));
        EscrowJournalEvent event = harness.applier().planMaintenanceRepair(command);

        apply(harness.applier(), command, event, 9L);
        assertTrue(handler.wasApplied(handler.plannedResult()));
        assertNotNull(harness.audit().getRecord(command.commandId()));
        apply(harness.applier(), command, event, 9L);

        MaintenanceRepairCommand unverified = command(UUID.randomUUID(),
                MaintenanceRepairTarget.runtime(), MaintenanceExpectedState.revision(7L),
                new MaintenanceRepairPayload.VerifyAndResume(92L, verified),
                true, true, BASE.plusSeconds(31));
        assertThrows(EscrowRuntimeException.class,
                () -> harness.applier().planMaintenanceRepair(unverified));
    }

    @Test
    void auditWithoutItsDomainMutationIsDetectedAsCorruption() {
        Harness harness = harness(MaintenanceRuntimeMutationHandler.unavailable());
        EscrowTransaction recovery = recoveryTransaction(harness.transactions());
        MaintenanceRepairCommand command = command(UUID.randomUUID(),
                MaintenanceRepairTarget.transaction(recovery.transactionId().value()),
                MaintenanceExpectedState.revision(recovery.revision()),
                new MaintenanceRepairPayload.RetryReset(), true, true,
                BASE.plusSeconds(40));
        EscrowJournalEvent event = harness.applier().planMaintenanceRepair(command);
        harness.audit().append(command.auditRecord());

        assertThrows(EscrowRuntimeException.class,
                () -> harness.applier().preflight(command.commandId(), event));
        assertEquals(EscrowState.RECOVERY_REQUIRED,
                harness.transactions().getTransaction(recovery.transactionId()).state());
    }

    private static Harness harness(MaintenanceRuntimeMutationHandler runtimeHandler) {
        EscrowTransactionSavedData transactions = new EscrowTransactionSavedData();
        ClaimSavedData claims = new ClaimSavedData();
        EscrowAdministrativeAuditSavedData audit =
                new EscrowAdministrativeAuditSavedData();
        CustodySavedData custody = new CustodySavedData();
        EscrowSavedDataMutationApplier applier = new EscrowSavedDataMutationApplier(
                transactions, new LedgerSavedData(), claims, audit, custody,
                new ProtectedMintSavedData(), runtimeHandler);
        return new Harness(applier, transactions, claims, audit, custody);
    }

    private static MaintenanceExpectedState expectedFingerprint(
            EscrowSavedDataMutationApplier applier,
            MaintenanceRepairTarget target
    ) {
        return MaintenanceExpectedState.fingerprint(
                applier.maintenanceFingerprint(target).bytes());
    }

    private static void apply(EscrowSavedDataMutationApplier applier,
                              MaintenanceRepairCommand command,
                              EscrowJournalEvent event,
                              long sequence) {
        applier.apply(new JournalRecord(sequence, command.commandId(),
                EscrowStepIds.forEvent(command.commandId(), event),
                EscrowJournalEventCodec.encode(event)), event);
    }

    private static MaintenanceRepairCommand command(
            UUID commandId,
            MaintenanceRepairTarget target,
            MaintenanceExpectedState expected,
            MaintenanceRepairPayload payload,
            boolean confirmed,
            boolean successful,
            Instant createdAt
    ) {
        return MaintenanceRepairCommand.create(commandId, "console",
                "Verified repair", confirmed, createdAt, target, expected, payload,
                successful, successful ? "Applied" : "Rejected");
    }

    private static EscrowTransaction recoveryTransaction(
            EscrowTransactionSavedData transactions
    ) {
        EscrowTransaction held = heldTransaction(transactions);
        EscrowError error = new EscrowError("TEMPORARY_FAILURE",
                "Inventory was temporarily unavailable", true,
                BASE.plusSeconds(4), Map.of());
        EscrowTransaction recovery = held.requireRecovery(error, 5,
                BASE.plusSeconds(30), BASE.plusSeconds(4));
        transactions.applyCommitted(recovery);
        return recovery;
    }

    private static EscrowTransaction heldTransaction(
            EscrowTransactionSavedData transactions
    ) {
        EscrowTransaction created = createdTransaction(UUID.randomUUID(), BASE);
        EscrowTransaction validated = created.transitionTo(
                EscrowState.VALIDATED, BASE.plusSeconds(1));
        EscrowTransaction holding = validated.transitionTo(
                EscrowState.HOLDING, BASE.plusSeconds(2));
        EscrowTransaction held = holding.transitionTo(
                EscrowState.HELD, BASE.plusSeconds(3));
        materialize(transactions, created, validated, holding, held);
        return held;
    }

    private static EscrowTransaction claimsCreatedRecovery(
            EscrowTransactionSavedData transactions
    ) {
        EscrowTransaction created = createdTransaction(UUID.randomUUID(), BASE);
        EscrowTransaction validated = created.transitionTo(
                EscrowState.VALIDATED, BASE.plusSeconds(1));
        EscrowTransaction holding = validated.transitionTo(
                EscrowState.HOLDING, BASE.plusSeconds(2));
        EscrowTransaction held = holding.transitionTo(
                EscrowState.HELD, BASE.plusSeconds(3));
        EscrowTransaction decided = held.transitionTo(
                EscrowState.COMMIT_DECIDED, BASE.plusSeconds(4));
        EscrowTransaction committed = decided.transitionTo(
                EscrowState.COMMITTED, BASE.plusSeconds(5));
        EscrowTransaction claimsCreated = committed.transitionTo(
                EscrowState.CLAIMS_CREATED, BASE.plusSeconds(6));
        EscrowError error = new EscrowError("CLAIM_INDEX_FAILURE",
                "Claim indexing was interrupted", true,
                BASE.plusSeconds(7), Map.of());
        EscrowTransaction recovery = claimsCreated.requireRecovery(error, 5,
                BASE.plusSeconds(30), BASE.plusSeconds(7));
        materialize(transactions, created, validated, holding, held, decided,
                committed, claimsCreated, recovery);
        return recovery;
    }

    private static EscrowTransaction createdTransaction(UUID transactionId, Instant createdAt) {
        EscrowParty player = EscrowParty.player(UUID.randomUUID());
        EscrowParty system = EscrowParty.system("escrow system");
        return EscrowTransaction.create(new EscrowTransactionId(transactionId),
                Optional.empty(), new EscrowRequestKey("request " + transactionId),
                EscrowOperation.ATM_WITHDRAWAL,
                Set.of(new EscrowParticipant(player, Set.of(
                                EscrowParticipantRole.INITIATOR,
                                EscrowParticipantRole.PAYER)),
                        new EscrowParticipant(system, Set.of(
                                EscrowParticipantRole.BENEFICIARY))),
                List.of(new EscrowAssetLot(UUID.randomUUID(),
                        EscrowAssetLotType.WALLET_MONEY,
                        EscrowProtectionLevel.PROTECTED, player, system, 1L,
                        Optional.of(new MoneyAmount("futureshops:credits", 25L)),
                        new byte[0], Map.of())), createdAt, 1L, Optional.empty());
    }

    private static void materialize(EscrowTransactionSavedData transactions,
                                    EscrowTransaction... states) {
        for (EscrowTransaction state : states) {
            transactions.applyCommitted(state);
        }
    }

    private static EscrowClaim claim(UUID claimId,
                                     UUID transactionId,
                                     long original,
                                     long remaining,
                                     ClaimStatus status,
                                     Instant now) {
        return new EscrowClaim(claimId, transactionId, UUID.randomUUID(),
                "claim source " + claimId, ClaimKind.ITEM, original, remaining,
                new byte[]{1, 2, 3}, status, "Claimed item", now, now);
    }

    private static CustodyLot walletLot(UUID transactionId, String requestKey) {
        CustodyTransferEvidence evidence = evidence(requestKey);
        return CustodyLot.held(UUID.randomUUID(), transactionId, requestKey,
                CustodyAssetType.WALLET_RESERVE, CustodyProtectionTier.PROTECTED,
                25L, CustodyLot.BUILT_IN_CURRENCY_PROVIDER, List.of(), List.of(),
                evidence, BASE);
    }

    private static CustodyPreparedBatch preparedBatch() {
        CustodyLot lot = walletLot(UUID.randomUUID(), "batch reserve");
        CustodyBatchPlan plan = CustodyBatchPlan.create(
                CustodyOperation.RESERVE, "batch reserve", List.of(lot));
        return CustodyPreparedBatch.prepare(plan, "batch token",
                Map.of(lot.lotId(), lot.holdEvidence()), BASE.plusSeconds(5));
    }

    private static CustodyTransferEvidence evidence(String token) {
        CustodyEndpointEvidence source = CustodyEndpointEvidence.captured("wallet",
                CustodyAdapterCapability.TRANSACTIONAL_PROTECTED, "player", "wallet",
                new byte[]{1}, new byte[]{2}, token + " source");
        CustodyEndpointEvidence destination = CustodyEndpointEvidence.captured("vault",
                CustodyAdapterCapability.TRANSACTIONAL_PROTECTED, "escrow", "vault",
                new byte[]{3}, new byte[]{4}, token + " destination");
        return new CustodyTransferEvidence(source, destination);
    }

    private static MaintenanceStateFingerprint fingerprint(int seed) {
        return MaintenanceStateFingerprint.of(bytes(seed));
    }

    private static byte[] bytes(int seed) {
        byte[] value = new byte[MaintenanceStateFingerprint.BYTE_LENGTH];
        for (int index = 0; index < value.length; index++) {
            value[index] = (byte) (seed + index);
        }
        return value;
    }

    private record Harness(EscrowSavedDataMutationApplier applier,
                           EscrowTransactionSavedData transactions,
                           ClaimSavedData claims,
                           EscrowAdministrativeAuditSavedData audit,
                           CustodySavedData custody) {
    }

    private static final class VerifiedRuntimeHandler
            implements MaintenanceRuntimeMutationHandler {
        private final MaintenanceRuntimeSnapshot snapshot;
        private final long verifiedSequence;
        private final MaintenanceStateFingerprint verifiedFingerprint;
        private MaintenanceRuntimeSnapshot current;
        private MaintenanceRuntimeSnapshot plannedResult;

        private VerifiedRuntimeHandler(MaintenanceRuntimeSnapshot snapshot,
                                       long verifiedSequence,
                                       MaintenanceStateFingerprint verifiedFingerprint) {
            this.snapshot = snapshot;
            this.current = snapshot;
            this.verifiedSequence = verifiedSequence;
            this.verifiedFingerprint = verifiedFingerprint;
        }

        @Override
        public MaintenanceRuntimeSnapshot snapshot() {
            return current;
        }

        @Override
        public MaintenanceRuntimeSnapshot plan(MaintenanceRepairCommand command) {
            if (!(command.payload() instanceof MaintenanceRepairPayload.VerifyAndResume resume)
                    || resume.verifiedJournalSequence() != verifiedSequence
                    || !MessageDigest.isEqual(resume.verificationFingerprint().bytes(),
                    verifiedFingerprint.bytes())) {
                throw new EscrowRuntimeException(
                        "Runtime global verification does not match");
            }
            plannedResult = new MaintenanceRuntimeSnapshot(
                    Math.addExact(current.revision(), 1L), fingerprint(100));
            return plannedResult;
        }

        @Override
        public void apply(MaintenanceRepairCommand command,
                          MaintenanceRuntimeSnapshot result) {
            if (result.revision() != Math.addExact(current.revision(), 1L)) {
                throw new EscrowRuntimeException("Runtime result revision does not match");
            }
            current = result;
        }

        @Override
        public boolean isCurrent(MaintenanceRuntimeSnapshot result) {
            return current.equals(result);
        }

        @Override
        public boolean wasApplied(MaintenanceRuntimeSnapshot result) {
            return current.revision() > result.revision() || current.equals(result);
        }

        private MaintenanceRuntimeSnapshot plannedResult() {
            return plannedResult;
        }
    }
}
