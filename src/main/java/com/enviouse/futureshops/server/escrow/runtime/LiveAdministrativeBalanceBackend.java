package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.economy.BalanceManager;
import com.enviouse.futureshops.server.economy.TransactionResult;
import com.enviouse.futureshops.server.escrow.admin.EscrowAdministrativeRecord;
import com.enviouse.futureshops.server.escrow.admin.balance.AdministrativeBalanceBackend;
import com.enviouse.futureshops.server.escrow.admin.balance.AdministrativeBalanceConfirmation;
import com.enviouse.futureshops.server.escrow.admin.balance.AdministrativeBalanceMutation;
import com.enviouse.futureshops.server.escrow.admin.balance.AdministrativeBalanceOperation;

import java.util.Optional;
import java.util.UUID;

public final class LiveAdministrativeBalanceBackend
        implements AdministrativeBalanceBackend {
    @Override
    public long balance(UUID playerId) {
        return BalanceManager.getBalance(playerId);
    }

    @Override
    public TransactionResult apply(
            AdministrativeBalanceMutation mutation
    ) {
        return switch (mutation.operation()) {
            case CREDIT -> BalanceManager.deposit(
                    mutation.requestId(), mutation.targetPlayerId(),
                    mutation.amountMinor(), walletReason(mutation));
            case DEBIT -> BalanceManager.withdraw(
                    mutation.requestId(), mutation.targetPlayerId(),
                    mutation.amountMinor(), walletReason(mutation));
            case SET, RESET -> BalanceManager.setBalance(
                    mutation.requestId(), mutation.targetPlayerId(),
                    mutation.amountMinor(), mutation.allowNegative(),
                    walletReason(mutation));
            case TRANSFER -> BalanceManager.transfer(
                    mutation.requestId(), mutation.targetPlayerId(),
                    mutation.counterpartyPlayerId().orElseThrow(),
                    mutation.amountMinor(), walletReason(mutation));
        };
    }

    @Override
    public Optional<EscrowAdministrativeRecord> auditRecord(
            UUID evidenceId
    ) {
        return EscrowRuntimeManager.requireReady()
                .administrativeAuditRecord(evidenceId);
    }

    @Override
    public boolean commitAudit(EscrowAdministrativeRecord record) {
        return EscrowRuntimeManager.requireReady()
                .commitAdministrativeAudit(record).replayed();
    }

    private static String walletReason(
            AdministrativeBalanceMutation mutation
    ) {
        if (mutation.confirmation()
                == AdministrativeBalanceConfirmation.EXPLICIT_COMMAND) {
            return "ADMIN";
        }
        if (mutation.operation() == AdministrativeBalanceOperation.DEBIT
                && mutation.allowNegative()) {
            return "ADMIN";
        }
        return switch (mutation.operation()) {
            case CREDIT -> "DEPOSIT";
            case DEBIT -> "WITHDRAW";
            case SET, RESET -> "ADMIN";
            case TRANSFER -> "TRANSFER";
        };
    }
}
