package com.enviouse.futureshops.server.escrow.ledger;

import java.util.Objects;

public record LedgerLeg(LedgerAccountId account, long deltaMinor) {
    public LedgerLeg {
        Objects.requireNonNull(account, "account");
        if (deltaMinor == 0L) {
            throw new IllegalArgumentException("Ledger leg cannot be zero");
        }
    }
}
