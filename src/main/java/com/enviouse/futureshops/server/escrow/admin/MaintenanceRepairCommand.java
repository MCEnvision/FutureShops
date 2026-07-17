package com.enviouse.futureshops.server.escrow.admin;

import com.enviouse.futureshops.server.escrow.model.EscrowTransactionId;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record MaintenanceRepairCommand(UUID commandId,
                                       String actor,
                                       String reason,
                                       boolean confirmed,
                                       Instant createdAt,
                                       MaintenanceRepairTarget target,
                                       MaintenanceExpectedState expectedState,
                                       MaintenanceRepairPayload payload,
                                       EscrowAdministrativeRecord auditRecord) {
    public static final int MAX_ACTOR_LENGTH = 160;
    public static final int MAX_REASON_LENGTH = 1024;
    public static final int MAX_OUTCOME_LENGTH = 1024;

    public MaintenanceRepairCommand {
        Objects.requireNonNull(commandId, "commandId");
        if (commandId.getMostSignificantBits() == 0L
                && commandId.getLeastSignificantBits() == 0L) {
            throw new IllegalArgumentException("Invalid maintenance command ID");
        }
        actor = MaintenanceRepairText.require(actor, "actor", MAX_ACTOR_LENGTH);
        reason = MaintenanceRepairText.require(reason, "reason", MAX_REASON_LENGTH);
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(expectedState, "expectedState");
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(auditRecord, "auditRecord");
        MaintenanceRepairText.require(auditRecord.outcome(), "outcome", MAX_OUTCOME_LENGTH);
        requireTarget(payload.action(), target.type());
        Optional<EscrowTransactionId> expectedTransaction =
                target.type() == MaintenanceRepairTargetType.TRANSACTION
                        ? Optional.of(new EscrowTransactionId(target.targetId()))
                        : Optional.empty();
        if (!auditRecord.requestId().equals(commandId)
                || !auditRecord.actor().equals(actor)
                || auditRecord.action() != payload.action()
                || !auditRecord.transactionId().equals(expectedTransaction)
                || !auditRecord.reason().equals(reason)
                || !auditRecord.createdAt().equals(createdAt)
                || (!confirmed && auditRecord.successful())) {
            throw new IllegalArgumentException("Maintenance command and audit record disagree");
        }
    }

    public static MaintenanceRepairCommand create(UUID commandId,
                                                  String actor,
                                                  String reason,
                                                  boolean confirmed,
                                                  Instant createdAt,
                                                  MaintenanceRepairTarget target,
                                                  MaintenanceExpectedState expectedState,
                                                  MaintenanceRepairPayload payload,
                                                  boolean successful,
                                                  String outcome) {
        String normalizedActor = MaintenanceRepairText.require(actor, "actor",
                MAX_ACTOR_LENGTH);
        String normalizedReason = MaintenanceRepairText.require(reason, "reason",
                MAX_REASON_LENGTH);
        String normalizedOutcome = MaintenanceRepairText.require(outcome, "outcome",
                MAX_OUTCOME_LENGTH);
        Optional<EscrowTransactionId> transactionId =
                target.type() == MaintenanceRepairTargetType.TRANSACTION
                        ? Optional.of(new EscrowTransactionId(target.targetId()))
                        : Optional.empty();
        EscrowAdministrativeRecord audit = new EscrowAdministrativeRecord(commandId,
                normalizedActor, payload.action(), transactionId, normalizedReason,
                createdAt, successful, normalizedOutcome);
        return new MaintenanceRepairCommand(commandId, normalizedActor, normalizedReason,
                confirmed, createdAt, target, expectedState, payload, audit);
    }

    public boolean appliesAction() {
        return confirmed && auditRecord.successful();
    }

    public MaintenanceRepairCommand rejected(String outcome) {
        return create(commandId, actor, reason, confirmed, createdAt, target,
                expectedState, payload, false, outcome);
    }

    private static void requireTarget(EscrowAdministrativeAction action,
                                      MaintenanceRepairTargetType type) {
        boolean valid = switch (action) {
            case ENTER_MAINTENANCE, RESUME_WRITES ->
                    type == MaintenanceRepairTargetType.RUNTIME;
            case RETRY_TRANSACTION, FORCE_REFUND, FORCE_SETTLEMENT ->
                    type == MaintenanceRepairTargetType.TRANSACTION;
            case QUARANTINE_CLAIM, REPAIR_CLAIM ->
                    type == MaintenanceRepairTargetType.CLAIM;
            case RECONCILE_CUSTODY -> type == MaintenanceRepairTargetType.CUSTODY_LOT;
            case QUARANTINE_CUSTODY -> type == MaintenanceRepairTargetType.CUSTODY_LOT
                    || type == MaintenanceRepairTargetType.CUSTODY_BATCH;
        };
        if (!valid) {
            throw new IllegalArgumentException("Maintenance action target is invalid");
        }
    }
}
