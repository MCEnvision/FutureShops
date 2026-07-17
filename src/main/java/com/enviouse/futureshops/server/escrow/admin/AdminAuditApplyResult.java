package com.enviouse.futureshops.server.escrow.admin;

public record AdminAuditApplyResult(EscrowAdministrativeRecord record, boolean applied, boolean replayed) {
    public AdminAuditApplyResult {
        if (record == null || applied == replayed) {
            throw new IllegalArgumentException("Invalid administrative audit result");
        }
    }
}
