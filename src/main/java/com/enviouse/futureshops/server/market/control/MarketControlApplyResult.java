package com.enviouse.futureshops.server.market.control;

import java.util.Objects;
import java.util.Optional;

public record MarketControlApplyResult(
        MarketControlState state,
        MarketControlAuditEntry auditEntry,
        Optional<MarketControlMutation> mutation,
        boolean replayed
) {
    public MarketControlApplyResult {
        state = Objects.requireNonNull(state, "state");
        auditEntry = Objects.requireNonNull(auditEntry, "auditEntry");
        mutation = Objects.requireNonNull(mutation, "mutation");
        if (replayed == mutation.isPresent()) {
            throw new IllegalArgumentException(
                    "Market control apply result is inconsistent");
        }
    }
}
