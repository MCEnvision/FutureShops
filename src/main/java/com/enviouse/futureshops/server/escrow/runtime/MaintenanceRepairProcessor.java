package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.admin.EscrowAdministrativeRecord;
import com.enviouse.futureshops.server.escrow.admin.MaintenanceClaimRepairDisposition;
import com.enviouse.futureshops.server.escrow.admin.MaintenanceCustodyDisposition;
import com.enviouse.futureshops.server.escrow.admin.MaintenanceRepairCommand;
import com.enviouse.futureshops.server.escrow.admin.MaintenanceRepairPayload;
import com.enviouse.futureshops.server.escrow.admin.MaintenanceRepairTarget;
import com.enviouse.futureshops.server.escrow.admin.MaintenanceRepairTargetType;
import com.enviouse.futureshops.server.escrow.admin.MaintenanceStateFingerprint;
import com.enviouse.futureshops.server.escrow.admin.EscrowAdministrativeAuditSavedData;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.ClaimSavedData;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.custody.CustodyBatchCommit;
import com.enviouse.futureshops.server.escrow.custody.CustodyMutation;
import com.enviouse.futureshops.server.escrow.custody.CustodyMutationCodec;
import com.enviouse.futureshops.server.escrow.custody.CustodyPreparedBatch;
import com.enviouse.futureshops.server.escrow.custody.CustodyPreparedBatchCodec;
import com.enviouse.futureshops.server.escrow.custody.CustodySavedData;
import com.enviouse.futureshops.server.escrow.custody.CustodyLot;
import com.enviouse.futureshops.server.escrow.custody.CustodyLotState;
import com.enviouse.futureshops.server.escrow.model.EscrowState;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import com.enviouse.futureshops.server.escrow.model.EscrowTransactionId;
import com.enviouse.futureshops.server.escrow.store.EscrowTransactionByteCodec;
import com.enviouse.futureshops.server.escrow.store.EscrowTransactionSavedData;

import java.security.MessageDigest;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

public final class MaintenanceRepairProcessor {
    static final String FORCE_SETTLEMENT_REJECTION =
            "Rejected because an exact transaction settlement manifest is unavailable";

    private final EscrowTransactionSavedData transactions;
    private final ClaimSavedData claims;
    private final EscrowAdministrativeAuditSavedData administrativeAudit;
    private final CustodySavedData custody;
    private final MaintenanceRuntimeMutationHandler runtimeHandler;

    public MaintenanceRepairProcessor(
            EscrowTransactionSavedData transactions,
            ClaimSavedData claims,
            EscrowAdministrativeAuditSavedData administrativeAudit,
            CustodySavedData custody,
            MaintenanceRuntimeMutationHandler runtimeHandler
    ) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.claims = Objects.requireNonNull(claims, "claims");
        this.administrativeAudit = Objects.requireNonNull(
                administrativeAudit, "administrativeAudit");
        this.custody = Objects.requireNonNull(custody, "custody");
        this.runtimeHandler = Objects.requireNonNull(runtimeHandler, "runtimeHandler");
    }

    public synchronized EscrowJournalEvent planEvent(MaintenanceRepairCommand command) {
        command = normalizeCommand(Objects.requireNonNull(command, "command"));
        MaintenanceRepairJournalEntry entry = new MaintenanceRepairJournalEntry(
                command, planEffect(command));
        return new EscrowJournalEvent(EscrowJournalEventType.MAINTENANCE_REPAIR,
                MaintenanceRepairJournalCodec.encode(entry));
    }

    public synchronized MaintenanceStateFingerprint currentFingerprint(
            MaintenanceRepairTarget target
    ) {
        Objects.requireNonNull(target, "target");
        return switch (target.type()) {
            case RUNTIME -> runtimeHandler.snapshot().fingerprint();
            case TRANSACTION -> transactionFingerprint(requireTransaction(target.targetId()));
            case CLAIM -> claimFingerprint(requireClaim(target.targetId()));
            case CUSTODY_LOT -> custodyLotFingerprint(requireHeldLot(target.targetId()));
            case CUSTODY_BATCH -> custodyBatchFingerprint(requireBatch(target.targetId()));
        };
    }

    public synchronized long currentRevision(MaintenanceRepairTarget target) {
        Objects.requireNonNull(target, "target");
        return switch (target.type()) {
            case RUNTIME -> runtimeHandler.snapshot().revision();
            case TRANSACTION -> requireTransaction(target.targetId()).revision();
            case CLAIM -> throw new EscrowRuntimeException(
                    "Maintenance claim targets require a fingerprint");
            case CUSTODY_LOT -> requireHeldLot(target.targetId()).revision();
            case CUSTODY_BATCH -> requireBatch(target.targetId()).revision();
        };
    }

    synchronized EscrowPreflightResult preflight(UUID recordId,
                                                 MaintenanceRepairJournalEntry entry) {
        requireRecordIdentity(recordId, entry.command());
        EscrowAdministrativeRecord existing = administrativeAudit.getRecord(
                entry.command().commandId());
        if (existing != null) {
            requireAuditMatches(existing, entry.command().auditRecord());
            requireEffectApplied(entry);
            return EscrowPreflightResult.REPLAY;
        }
        administrativeAudit.preflightAppend(entry.command().auditRecord());
        MaintenanceRepairJournalEntry.Effect expected = planEffect(entry.command());
        if (!expected.equals(entry.effect())) {
            throw new EscrowRuntimeException(
                    "Maintenance repair effect does not match current state");
        }
        return EscrowPreflightResult.APPLY;
    }

    synchronized void apply(UUID recordId,
                            MaintenanceRepairJournalEntry entry,
                            Consumer<MaintenanceRepairJournalEntry.Effect> effectSink,
                            Consumer<EscrowAdministrativeRecord> auditSink) {
        Objects.requireNonNull(effectSink, "effectSink");
        Objects.requireNonNull(auditSink, "auditSink");
        requireRecordIdentity(recordId, entry.command());
        EscrowAdministrativeRecord existing = administrativeAudit.getRecord(
                entry.command().commandId());
        if (existing != null) {
            requireAuditMatches(existing, entry.command().auditRecord());
            requireEffectApplied(entry);
            return;
        }
        administrativeAudit.preflightAppend(entry.command().auditRecord());
        if (entry.effect() instanceof MaintenanceRepairJournalEntry.AuditOnly
                || entry.effect() instanceof MaintenanceRepairJournalEntry.CustodyLotVerification) {
            MaintenanceRepairJournalEntry.Effect expected = planEffect(entry.command());
            if (!expected.equals(entry.effect())) {
                throw new EscrowRuntimeException(
                        "Maintenance repair effect does not match current state");
            }
        } else if (entry.effect() instanceof MaintenanceRepairJournalEntry.RuntimeState) {
            MaintenanceRepairJournalEntry.RuntimeState runtimeState =
                    (MaintenanceRepairJournalEntry.RuntimeState) entry.effect();
            if (!runtimeHandler.isCurrent(runtimeState.result())) {
                runtimeHandler.apply(entry.command(), runtimeState.result());
                if (!runtimeHandler.isCurrent(runtimeState.result())) {
                    throw new EscrowRuntimeException(
                            "Maintenance runtime effect did not materialize");
                }
            }
        } else if (isEffectApplied(entry.effect())) {
            requirePostStateMatchesCommand(entry);
        } else {
            MaintenanceRepairJournalEntry.Effect expected = planEffect(entry.command());
            if (!expected.equals(entry.effect())) {
                throw new EscrowRuntimeException(
                        "Maintenance repair effect does not match current state");
            }
            effectSink.accept(entry.effect());
        }
        auditSink.accept(entry.command().auditRecord());
    }

    private MaintenanceRepairJournalEntry.Effect planEffect(
            MaintenanceRepairCommand command
    ) {
        if (!command.appliesAction()) {
            return new MaintenanceRepairJournalEntry.AuditOnly();
        }
        return switch (command.payload().action()) {
            case ENTER_MAINTENANCE, RESUME_WRITES -> planRuntime(command);
            case RETRY_TRANSACTION, FORCE_REFUND ->
                    planTransaction(command);
            case FORCE_SETTLEMENT -> throw new EscrowRuntimeException(
                    "Force settlement requires an exact transaction settlement manifest");
            case QUARANTINE_CLAIM, REPAIR_CLAIM -> planClaim(command);
            case RECONCILE_CUSTODY -> planCustodyReconciliation(command);
            case QUARANTINE_CUSTODY -> planCustodyQuarantine(command);
            case BALANCE_MUTATION -> throw new EscrowRuntimeException(
                    "Balance audit is not a maintenance repair");
        };
    }

    private MaintenanceRepairJournalEntry.Effect planRuntime(
            MaintenanceRepairCommand command
    ) {
        MaintenanceRuntimeSnapshot snapshot = runtimeHandler.snapshot();
        MaintenanceStateFingerprints.requireExpected(command.expectedState(),
                snapshot.revision(), snapshot.fingerprint());
        MaintenanceRuntimeSnapshot result = runtimeHandler.plan(command);
        if (result.revision() != Math.addExact(snapshot.revision(), 1L)) {
            throw new EscrowRuntimeException(
                    "Maintenance runtime revision does not advance exactly once");
        }
        return new MaintenanceRepairJournalEntry.RuntimeState(result);
    }

    private MaintenanceRepairJournalEntry.Effect planTransaction(
            MaintenanceRepairCommand command
    ) {
        EscrowTransaction current = requireTransaction(command.target().targetId());
        MaintenanceStateFingerprints.requireExpected(command.expectedState(),
                current.revision(), transactionFingerprint(current));
        EscrowTransaction replacement;
        if (command.payload() instanceof MaintenanceRepairPayload.RetryReset) {
            replacement = retryTransaction(current, command);
        } else if (command.payload() instanceof MaintenanceRepairPayload.ForceRefund) {
            replacement = forceRefund(current, command);
        } else {
            throw new EscrowRuntimeException(
                    "Unsupported maintenance transaction payload");
        }
        if (replacement.equals(current)) {
            throw new EscrowRuntimeException(
                    "Maintenance transaction repair has no state change");
        }
        if (transactions.preflightCommitted(replacement).replayed()) {
            throw new EscrowRuntimeException(
                    "Maintenance transaction repair unexpectedly replayed");
        }
        return new MaintenanceRepairJournalEntry.TransactionState(replacement);
    }

    private MaintenanceRepairJournalEntry.Effect planClaim(
            MaintenanceRepairCommand command
    ) {
        EscrowClaim current = requireClaim(command.target().targetId());
        MaintenanceStateFingerprints.requireExpected(command.expectedState(), -1L,
                claimFingerprint(current));
        EscrowClaim replacement;
        if (command.payload() instanceof MaintenanceRepairPayload.ClaimQuarantine) {
            replacement = current.quarantine(command.createdAt());
        } else if (command.payload() instanceof MaintenanceRepairPayload.ClaimRepair repair) {
            replacement = repairClaim(current, repair, command);
        } else {
            throw new EscrowRuntimeException("Unsupported maintenance claim payload");
        }
        if (replacement.equals(current)) {
            throw new EscrowRuntimeException(
                    "Maintenance claim repair has no state change");
        }
        if (claims.preflightMaintenanceReplace(replacement).replayed()) {
            throw new EscrowRuntimeException(
                    "Maintenance claim repair unexpectedly replayed");
        }
        return new MaintenanceRepairJournalEntry.ClaimState(replacement);
    }

    private MaintenanceRepairJournalEntry.Effect planCustodyReconciliation(
            MaintenanceRepairCommand command
    ) {
        CustodyLot current = requireHeldLot(command.target().targetId());
        MaintenanceStateFingerprint fingerprint = custodyLotFingerprint(current);
        MaintenanceStateFingerprints.requireExpected(command.expectedState(),
                current.revision(), fingerprint);
        MaintenanceRepairPayload.CustodyReconcile reconcile =
                (MaintenanceRepairPayload.CustodyReconcile) command.payload();
        if (reconcile.disposition() != MaintenanceCustodyDisposition.CONFIRM_HELD) {
            throw new EscrowRuntimeException(
                    "Custody release consume and quarantine require exact adapter evidence");
        }
        if (!MessageDigest.isEqual(reconcile.observedFingerprint().bytes(),
                current.assetFingerprint())) {
            throw new EscrowRuntimeException(
                    "Observed custody assets do not match the held lot");
        }
        return new MaintenanceRepairJournalEntry.CustodyLotVerification(
                current.lotId(), current.revision(), fingerprint);
    }

    private MaintenanceRepairJournalEntry.Effect planCustodyQuarantine(
            MaintenanceRepairCommand command
    ) {
        if (command.target().type() != MaintenanceRepairTargetType.CUSTODY_BATCH) {
            throw new EscrowRuntimeException(
                    "Custody lot quarantine requires exact adapter evidence");
        }
        CustodyPreparedBatch current = requireBatch(command.target().targetId());
        MaintenanceStateFingerprints.requireExpected(command.expectedState(),
                current.revision(), custodyBatchFingerprint(current));
        CustodyPreparedBatch quarantined = current.quarantine(current.revision(),
                command.createdAt(), command.reason());
        CustodyBatchCommit commit = CustodyBatchCommit.state(quarantined);
        if (custody.preflightBatchCommit(commit).replayed()) {
            throw new EscrowRuntimeException(
                    "Maintenance custody quarantine unexpectedly replayed");
        }
        return new MaintenanceRepairJournalEntry.CustodyBatchState(commit);
    }

    private EscrowTransaction retryTransaction(EscrowTransaction current,
                                               MaintenanceRepairCommand command) {
        if (current.state() != EscrowState.RECOVERY_REQUIRED) {
            throw new EscrowRuntimeException(
                    "Only recovery transactions can reset retry state");
        }
        return current.transitionTo(
                current.retryMetadata().resumeState().orElseThrow(), command.createdAt());
    }

    private EscrowTransaction forceRefund(EscrowTransaction current,
                                          MaintenanceRepairCommand command) {
        if (current.state() == EscrowState.ABORTING) {
            return current.transitionTo(EscrowState.REFUND_PENDING, command.createdAt());
        }
        if (current.state() == EscrowState.RECOVERY_REQUIRED
                && current.retryMetadata().resumeState().orElseThrow()
                == EscrowState.REFUND_PENDING) {
            return current.transitionTo(EscrowState.REFUND_PENDING, command.createdAt());
        }
        if (current.state() == EscrowState.MANUAL_REVIEW) {
            return current.resolveManualReviewTo(
                    EscrowState.REFUND_PENDING, command.createdAt());
        }
        throw new EscrowRuntimeException(
                "Force refund cannot bypass a commit decision or custody release");
    }

    private EscrowClaim repairClaim(EscrowClaim current,
                                    MaintenanceRepairPayload.ClaimRepair repair,
                                    MaintenanceRepairCommand command) {
        if (current.status() != ClaimStatus.QUARANTINED
                || repair.resultingRemainingUnits() != current.remainingUnits()) {
            throw new EscrowRuntimeException(
                    "Claim repair cannot change outstanding liability");
        }
        ClaimStatus status;
        if (repair.disposition() == MaintenanceClaimRepairDisposition.REOPEN_PENDING
                && current.remainingUnits() == current.originalUnits()) {
            status = ClaimStatus.PENDING;
        } else if (repair.disposition()
                == MaintenanceClaimRepairDisposition.REOPEN_PARTIAL
                && current.remainingUnits() > 0L
                && current.remainingUnits() < current.originalUnits()) {
            status = ClaimStatus.PARTIALLY_DELIVERED;
        } else {
            throw new EscrowRuntimeException(
                    "Claim repair disposition does not match conserved units");
        }
        return new EscrowClaim(current.claimId(), current.transactionId(),
                current.ownerId(), current.sourceKey(), current.kind(),
                current.originalUnits(), current.remainingUnits(), current.payload(),
                status, current.label(), current.createdAt(), command.createdAt());
    }

    private static MaintenanceRepairCommand normalizeCommand(
            MaintenanceRepairCommand command
    ) {
        if (command.appliesAction()
                && command.payload() instanceof MaintenanceRepairPayload.ForceSettlement) {
            return command.rejected(FORCE_SETTLEMENT_REJECTION);
        }
        return command;
    }

    private boolean isEffectApplied(MaintenanceRepairJournalEntry.Effect effect) {
        if (effect instanceof MaintenanceRepairJournalEntry.TransactionState value) {
            try {
                return transactions.preflightCommitted(value.transaction()).replayed();
            } catch (RuntimeException exception) {
                return false;
            }
        }
        if (effect instanceof MaintenanceRepairJournalEntry.ClaimState value) {
            return claims.maintenanceStateWasApplied(value.claim());
        }
        if (effect instanceof MaintenanceRepairJournalEntry.CustodyBatchState value) {
            return value.commit().batch().equals(custody.getPreparedBatch(
                    value.commit().batch().batchId()));
        }
        if (effect instanceof MaintenanceRepairJournalEntry.CustodyLotVerification value) {
            CustodyLot lot = custody.getLot(value.lotId());
            return lot != null && lot.state() == CustodyLotState.HELD
                    && lot.revision() == value.revision()
                    && value.stateFingerprint().equals(custodyLotFingerprint(lot));
        }
        if (effect instanceof MaintenanceRepairJournalEntry.RuntimeState) {
            return false;
        }
        return effect instanceof MaintenanceRepairJournalEntry.AuditOnly;
    }

    private void requireEffectApplied(MaintenanceRepairJournalEntry entry) {
        boolean applied = entry.effect() instanceof MaintenanceRepairJournalEntry.RuntimeState value
                ? runtimeHandler.wasApplied(value.result()) : isEffectApplied(entry.effect());
        if (!applied) {
            throw new EscrowRuntimeException(
                    "Maintenance audit exists without its exact domain effect");
        }
    }

    private void requirePostStateMatchesCommand(MaintenanceRepairJournalEntry entry) {
        if (entry.effect() instanceof MaintenanceRepairJournalEntry.TransactionState value) {
            if (entry.command().expectedState().kind()
                    == com.enviouse.futureshops.server.escrow.admin.MaintenanceExpectedStateKind.REVISION
                    && value.transaction().revision() != Math.addExact(
                    entry.command().expectedState().expectedRevision(), 1L)) {
                throw new EscrowRuntimeException(
                        "Maintenance transaction post state revision does not match");
            }
        }
    }

    private EscrowTransaction requireTransaction(UUID targetId) {
        EscrowTransaction transaction = transactions.getTransaction(
                new EscrowTransactionId(targetId));
        if (transaction == null) {
            throw new EscrowRuntimeException("Maintenance transaction does not exist");
        }
        return transaction;
    }

    private EscrowClaim requireClaim(UUID targetId) {
        EscrowClaim claim = claims.getClaim(targetId);
        if (claim == null) {
            throw new EscrowRuntimeException("Maintenance claim does not exist");
        }
        return claim;
    }

    private CustodyLot requireHeldLot(UUID targetId) {
        CustodyLot lot = custody.getLot(targetId);
        if (lot == null || lot.state() != CustodyLotState.HELD) {
            throw new EscrowRuntimeException(
                    "Maintenance custody lot is not held");
        }
        return lot;
    }

    private CustodyPreparedBatch requireBatch(UUID targetId) {
        CustodyPreparedBatch batch = custody.getPreparedBatch(targetId);
        if (batch == null) {
            throw new EscrowRuntimeException(
                    "Maintenance custody batch does not exist");
        }
        return batch;
    }

    private static MaintenanceStateFingerprint transactionFingerprint(
            EscrowTransaction transaction
    ) {
        return MaintenanceStateFingerprints.sha256(
                EscrowTransactionByteCodec.encode(transaction));
    }

    private static MaintenanceStateFingerprint claimFingerprint(EscrowClaim claim) {
        return MaintenanceStateFingerprints.sha256(ClaimJournalCodec.encodeClaim(claim));
    }

    private static MaintenanceStateFingerprint custodyLotFingerprint(CustodyLot lot) {
        if (lot.state() != CustodyLotState.HELD) {
            throw new EscrowRuntimeException(
                    "Only held custody lots can be maintenance verified");
        }
        return MaintenanceStateFingerprints.sha256(
                CustodyMutationCodec.encode(CustodyMutation.reserve(lot)));
    }

    private static MaintenanceStateFingerprint custodyBatchFingerprint(
            CustodyPreparedBatch batch
    ) {
        return MaintenanceStateFingerprints.sha256(
                CustodyPreparedBatchCodec.encode(batch));
    }

    private static void requireRecordIdentity(UUID recordId,
                                              MaintenanceRepairCommand command) {
        if (!Objects.requireNonNull(recordId, "recordId").equals(command.commandId())) {
            throw new EscrowRuntimeException(
                    "Maintenance journal identity does not match its command");
        }
    }

    private static void requireAuditMatches(EscrowAdministrativeRecord current,
                                            EscrowAdministrativeRecord expected) {
        if (!current.equals(expected)) {
            throw new EscrowRuntimeException(
                    "Maintenance command ID has a conflicting audit record");
        }
    }
}
