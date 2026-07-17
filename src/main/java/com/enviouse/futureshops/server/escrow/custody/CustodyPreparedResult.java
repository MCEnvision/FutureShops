package com.enviouse.futureshops.server.escrow.custody;

import java.util.Objects;

public record CustodyPreparedResult(
        CustodyPreparedOperation intent,
        boolean replayed
) {
    public CustodyPreparedResult {
        Objects.requireNonNull(intent, "intent");
    }
}
