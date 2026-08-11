package com.enviouse.futureshops.server.escrow.admin.balance;

import com.enviouse.futureshops.server.economy.TransactionResult;
import com.enviouse.futureshops.server.escrow.admin.EscrowAdministrativeRecord;

import java.util.Optional;
import java.util.UUID;

public interface AdministrativeBalanceBackend {
    long balance(UUID playerId);

    TransactionResult apply(AdministrativeBalanceMutation mutation);

    Optional<EscrowAdministrativeRecord> auditRecord(UUID evidenceId);

    boolean commitAudit(EscrowAdministrativeRecord record);
}
