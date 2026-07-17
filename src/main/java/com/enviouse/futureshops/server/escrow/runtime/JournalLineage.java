package com.enviouse.futureshops.server.escrow.runtime;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record JournalLineage(UUID lineageId, Instant createdAt) {
    public JournalLineage {
        Objects.requireNonNull(lineageId, "lineageId");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
