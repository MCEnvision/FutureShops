package com.enviouse.futureshops.server.escrow.runtime;

@FunctionalInterface
interface AtmWithdrawalApplyFaultInjector {
    AtmWithdrawalApplyFaultInjector NONE = step -> {
    };

    void afterMutation(int step);
}
