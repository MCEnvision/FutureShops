package com.enviouse.futureshops.server.escrow.ledger;

import java.util.Map;

public record LedgerApplyResult(boolean applied, boolean replayed,
                                Map<LedgerAccountId, Long> resultingBalances) {
    public LedgerApplyResult {
        resultingBalances = Map.copyOf(resultingBalances);
    }
}
