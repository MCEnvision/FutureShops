package com.enviouse.futureshops.server.escrow.ledger;

import java.util.Objects;

public record LedgerAccountId(LedgerAccountType type, String ownerKey) {
    public LedgerAccountId {
        Objects.requireNonNull(type, "type");
        ownerKey = Objects.requireNonNull(ownerKey, "ownerKey").trim();
        if (ownerKey.isEmpty() || ownerKey.length() > 128) {
            throw new IllegalArgumentException("Invalid owner key");
        }
    }

    public static LedgerAccountId system(LedgerAccountType type) {
        return new LedgerAccountId(type, "system");
    }
}
