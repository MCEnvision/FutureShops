package com.enviouse.futureshops.server.escrow.admin;

import java.util.Objects;

public sealed interface MaintenanceRepairPayload permits
        MaintenanceRepairPayload.EnterMaintenance,
        MaintenanceRepairPayload.RetryReset,
        MaintenanceRepairPayload.ForceRefund,
        MaintenanceRepairPayload.ForceSettlement,
        MaintenanceRepairPayload.ClaimQuarantine,
        MaintenanceRepairPayload.ClaimRepair,
        MaintenanceRepairPayload.CustodyReconcile,
        MaintenanceRepairPayload.CustodyQuarantine,
        MaintenanceRepairPayload.VerifyAndResume {
    int MAX_INCIDENT_REFERENCE_LENGTH = 128;

    EscrowAdministrativeAction action();

    record EnterMaintenance(String incidentReference) implements MaintenanceRepairPayload {
        public EnterMaintenance {
            incidentReference = MaintenanceRepairText.require(incidentReference,
                    "incident reference", MAX_INCIDENT_REFERENCE_LENGTH);
        }

        @Override
        public EscrowAdministrativeAction action() {
            return EscrowAdministrativeAction.ENTER_MAINTENANCE;
        }
    }

    record RetryReset() implements MaintenanceRepairPayload {
        @Override
        public EscrowAdministrativeAction action() {
            return EscrowAdministrativeAction.RETRY_TRANSACTION;
        }
    }

    record ForceRefund() implements MaintenanceRepairPayload {
        @Override
        public EscrowAdministrativeAction action() {
            return EscrowAdministrativeAction.FORCE_REFUND;
        }
    }

    record ForceSettlement() implements MaintenanceRepairPayload {
        @Override
        public EscrowAdministrativeAction action() {
            return EscrowAdministrativeAction.FORCE_SETTLEMENT;
        }
    }

    record ClaimQuarantine() implements MaintenanceRepairPayload {
        @Override
        public EscrowAdministrativeAction action() {
            return EscrowAdministrativeAction.QUARANTINE_CLAIM;
        }
    }

    record ClaimRepair(MaintenanceClaimRepairDisposition disposition,
                       long resultingRemainingUnits) implements MaintenanceRepairPayload {
        public ClaimRepair {
            Objects.requireNonNull(disposition, "disposition");
            if (disposition == MaintenanceClaimRepairDisposition.COMPLETE
                    ? resultingRemainingUnits != 0L : resultingRemainingUnits <= 0L) {
                throw new IllegalArgumentException("Invalid maintenance claim repair quantity");
            }
        }

        @Override
        public EscrowAdministrativeAction action() {
            return EscrowAdministrativeAction.REPAIR_CLAIM;
        }
    }

    record CustodyReconcile(MaintenanceStateFingerprint observedFingerprint,
                            MaintenanceCustodyDisposition disposition)
            implements MaintenanceRepairPayload {
        public CustodyReconcile {
            Objects.requireNonNull(observedFingerprint, "observedFingerprint");
            Objects.requireNonNull(disposition, "disposition");
        }

        @Override
        public EscrowAdministrativeAction action() {
            return EscrowAdministrativeAction.RECONCILE_CUSTODY;
        }
    }

    record CustodyQuarantine() implements MaintenanceRepairPayload {
        @Override
        public EscrowAdministrativeAction action() {
            return EscrowAdministrativeAction.QUARANTINE_CUSTODY;
        }
    }

    record VerifyAndResume(long verifiedJournalSequence,
                           MaintenanceStateFingerprint verificationFingerprint)
            implements MaintenanceRepairPayload {
        public VerifyAndResume {
            Objects.requireNonNull(verificationFingerprint, "verificationFingerprint");
            if (verifiedJournalSequence < 0L) {
                throw new IllegalArgumentException("Invalid verified journal sequence");
            }
        }

        @Override
        public EscrowAdministrativeAction action() {
            return EscrowAdministrativeAction.RESUME_WRITES;
        }
    }
}
