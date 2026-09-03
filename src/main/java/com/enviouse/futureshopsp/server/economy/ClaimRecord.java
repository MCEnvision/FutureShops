package com.enviouse.futureshopsp.server.economy;

import com.enviouse.futureshopsp.api.economy.RequestId;

import java.util.Objects;
import java.util.UUID;

/** Immutable claim for an item or confirmed owner proceeds. */
public record ClaimRecord(RequestId requestId, UUID claimant, long amountMinorUnits,
                          String description, ClaimState state) {
    public ClaimRecord {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(claimant, "claimant");
        Objects.requireNonNull(state, "state");
        description = description == null ? "" : description.trim();
        if (description.length() > 256 || description.indexOf('\n') >= 0 || description.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("description must be a bounded single line");
        }
        if (amountMinorUnits < 0L) {
            throw new IllegalArgumentException("claim amount must not be negative");
        }
    }
}
