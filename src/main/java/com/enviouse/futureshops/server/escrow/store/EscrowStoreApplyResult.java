package com.enviouse.futureshops.server.escrow.store;

import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;

import java.util.Objects;

public record EscrowStoreApplyResult(
        EscrowTransaction transaction,
        boolean applied,
        boolean replayed
) {
    public EscrowStoreApplyResult {
        Objects.requireNonNull(transaction, "transaction");
        if (applied == replayed) {
            throw new IllegalArgumentException("Escrow store result must be applied or replayed");
        }
    }
}
