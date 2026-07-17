package com.enviouse.futureshops.server.escrow.admin.balance;

import com.enviouse.futureshops.server.economy.TransactionResult;

import java.util.Objects;

public record AdministrativeBalanceMutationResult(
        TransactionResult transactionResult,
        AdministrativeBalanceEvidence intentEvidence,
        AdministrativeBalanceEvidence outcomeEvidence,
        boolean replayed
) {
    public AdministrativeBalanceMutationResult {
        transactionResult = Objects.requireNonNull(
                transactionResult, "transactionResult");
        intentEvidence = Objects.requireNonNull(
                intentEvidence, "intentEvidence");
        outcomeEvidence = Objects.requireNonNull(
                outcomeEvidence, "outcomeEvidence");
    }
}
