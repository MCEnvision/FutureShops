package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;

@FunctionalInterface
public interface EscrowRecoveryHandler {
    EscrowRecoveryAttempt recover(EscrowTransaction transaction);
}
