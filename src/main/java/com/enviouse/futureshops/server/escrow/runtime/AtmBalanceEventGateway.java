package com.enviouse.futureshops.server.escrow.runtime;

import java.util.UUID;

interface AtmBalanceEventGateway {
    boolean beforeDebit(UUID playerId, long amountMinorUnits,
                        long balanceBefore);

    void afterDebit(UUID playerId, long amountMinorUnits,
                    long balanceAfter);
}
