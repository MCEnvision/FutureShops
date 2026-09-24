package com.enviouse.futureshops.server.escrow.coordinator;

import java.util.Objects;
import java.util.UUID;

/** Stable identity for a liability created by a known partial settlement. */
public record ClaimId(UUID value) {
    public ClaimId {
        value = Objects.requireNonNull(value, "value");
        if (value.equals(new UUID(0L, 0L))) {
            throw new IllegalArgumentException("claim id must not be zero");
        }
    }
}
