package com.enviouse.futureshops.server.escrow.coordinator;

import java.util.Objects;

/** Local custody retained until every planned leg has authoritative receipts. */
public record CustodyPlan(String custodyId, long minorUnits) {
    public CustodyPlan {
        if (custodyId == null || custodyId.isBlank() || custodyId.length() > 128
                || custodyId.indexOf('\n') >= 0 || custodyId.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("custodyId must be a bounded single line");
        }
        if (minorUnits <= 0L) {
            throw new IllegalArgumentException("custody minorUnits must be positive");
        }
        Objects.requireNonNull(custodyId, "custodyId");
    }
}
