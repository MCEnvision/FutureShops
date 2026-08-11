package com.enviouse.futureshops.server.market.control;

import java.util.Objects;

public record MarketControlCommitResult(
        MarketControlState state,
        MarketControlAuditEntry auditEntry,
        boolean replayed
) {
    public MarketControlCommitResult {
        state = Objects.requireNonNull(state, "state");
        auditEntry = Objects.requireNonNull(auditEntry, "auditEntry");
        MarketControlRequestReceipt receipt = state.requestReceipts().get(
                auditEntry.requestId());
        if (receipt == null
                || !receipt.auditEntry().equals(auditEntry)) {
            throw new IllegalArgumentException(
                    "Market control commit result is inconsistent");
        }
    }
}
