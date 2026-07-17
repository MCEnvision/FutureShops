package com.enviouse.futureshops.server.escrow.runtime;

public record EscrowRecoveryBatchResult(int examined, int handlersInvoked,
                                        int resolved, int remaining) {
    public EscrowRecoveryBatchResult {
        if (examined < 0 || handlersInvoked < 0 || resolved < 0 || remaining < 0
                || handlersInvoked > examined || resolved > examined) {
            throw new IllegalArgumentException("Invalid escrow recovery batch result");
        }
    }
}
