package com.enviouse.futureshops.server.escrow.admin;

public enum EscrowAdministrativeAction {
    ENTER_MAINTENANCE,
    RESUME_WRITES,
    RETRY_TRANSACTION,
    FORCE_REFUND,
    FORCE_SETTLEMENT,
    QUARANTINE_CLAIM,
    REPAIR_CLAIM,
    RECONCILE_CUSTODY,
    QUARANTINE_CUSTODY,
    BALANCE_MUTATION
}
