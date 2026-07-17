package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.admin.EscrowAdministrativeAction;
import com.enviouse.futureshops.server.escrow.admin.MaintenanceRepairCommand;
import com.enviouse.futureshops.server.escrow.admin.MaintenanceRepairTargetType;
import com.enviouse.futureshops.server.escrow.admin.MaintenanceStateFingerprint;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.custody.CustodyBatchCommit;
import com.enviouse.futureshops.server.escrow.custody.CustodyBatchStatus;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;

import java.util.Objects;
import java.util.UUID;

public record MaintenanceRepairJournalEntry(MaintenanceRepairCommand command,
                                            Effect effect) {
    public MaintenanceRepairJournalEntry {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(effect, "effect");
        requireEffectMatchesCommand(command, effect);
    }

    public sealed interface Effect permits AuditOnly, RuntimeState,
            TransactionState, ClaimState, CustodyLotVerification, CustodyBatchState {
    }

    public record AuditOnly() implements Effect {
    }

    public record RuntimeState(MaintenanceRuntimeSnapshot result) implements Effect {
        public RuntimeState {
            Objects.requireNonNull(result, "result");
        }
    }

    public record TransactionState(EscrowTransaction transaction) implements Effect {
        public TransactionState {
            Objects.requireNonNull(transaction, "transaction");
        }
    }

    public record ClaimState(EscrowClaim claim) implements Effect {
        public ClaimState {
            Objects.requireNonNull(claim, "claim");
        }
    }

    public record CustodyLotVerification(UUID lotId,
                                         long revision,
                                         MaintenanceStateFingerprint stateFingerprint)
            implements Effect {
        public CustodyLotVerification {
            Objects.requireNonNull(lotId, "lotId");
            Objects.requireNonNull(stateFingerprint, "stateFingerprint");
            if (revision < 0L) {
                throw new IllegalArgumentException(
                        "Invalid maintenance custody lot revision");
            }
        }
    }

    public record CustodyBatchState(CustodyBatchCommit commit) implements Effect {
        public CustodyBatchState {
            Objects.requireNonNull(commit, "commit");
            if (commit.batch().status() != CustodyBatchStatus.QUARANTINED
                    || !commit.mutations().isEmpty()) {
                throw new IllegalArgumentException(
                        "Maintenance custody batch effect must quarantine without mutations");
            }
        }
    }

    private static void requireEffectMatchesCommand(MaintenanceRepairCommand command,
                                                    Effect effect) {
        if (effect instanceof AuditOnly) {
            if (command.appliesAction()) {
                throw new IllegalArgumentException(
                        "Successful maintenance command requires an exact effect");
            }
            return;
        }
        if (!command.appliesAction()) {
            throw new IllegalArgumentException(
                    "Failed maintenance command cannot contain a domain effect");
        }
        EscrowAdministrativeAction action = command.payload().action();
        UUID targetId = command.target().targetId();
        if (effect instanceof RuntimeState) {
            if (command.target().type() != MaintenanceRepairTargetType.RUNTIME
                    || action != EscrowAdministrativeAction.ENTER_MAINTENANCE
                    && action != EscrowAdministrativeAction.RESUME_WRITES) {
                throw new IllegalArgumentException(
                        "Maintenance runtime effect does not match its command");
            }
            return;
        }
        if (effect instanceof TransactionState state) {
            if (command.target().type() != MaintenanceRepairTargetType.TRANSACTION
                    || !state.transaction().transactionId().value().equals(targetId)
                    || action != EscrowAdministrativeAction.RETRY_TRANSACTION
                    && action != EscrowAdministrativeAction.FORCE_REFUND
                    && action != EscrowAdministrativeAction.FORCE_SETTLEMENT) {
                throw new IllegalArgumentException(
                        "Maintenance transaction effect does not match its command");
            }
            return;
        }
        if (effect instanceof ClaimState state) {
            if (command.target().type() != MaintenanceRepairTargetType.CLAIM
                    || !state.claim().claimId().equals(targetId)
                    || action != EscrowAdministrativeAction.QUARANTINE_CLAIM
                    && action != EscrowAdministrativeAction.REPAIR_CLAIM) {
                throw new IllegalArgumentException(
                        "Maintenance claim effect does not match its command");
            }
            return;
        }
        if (effect instanceof CustodyLotVerification verification) {
            if (command.target().type() != MaintenanceRepairTargetType.CUSTODY_LOT
                    || !verification.lotId().equals(targetId)
                    || action != EscrowAdministrativeAction.RECONCILE_CUSTODY) {
                throw new IllegalArgumentException(
                        "Maintenance custody verification does not match its command");
            }
            return;
        }
        if (effect instanceof CustodyBatchState state) {
            if (command.target().type() != MaintenanceRepairTargetType.CUSTODY_BATCH
                    || !state.commit().batch().batchId().equals(targetId)
                    || action != EscrowAdministrativeAction.QUARANTINE_CUSTODY) {
                throw new IllegalArgumentException(
                        "Maintenance custody batch effect does not match its command");
            }
            return;
        }
        throw new IllegalArgumentException("Unknown maintenance repair effect");
    }
}
