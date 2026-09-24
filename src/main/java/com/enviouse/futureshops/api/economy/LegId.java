package com.enviouse.futureshops.api.economy;

import java.util.Objects;
import java.util.UUID;

/** Stable identity for one value leg within a logical economy operation. */
public record LegId(UUID value) {
    public LegId {
        Objects.requireNonNull(value, "value");
        if (value.equals(new UUID(0L, 0L))) {
            throw new IllegalArgumentException("leg id must not be zero");
        }
    }

    public static LegId random() {
        return new LegId(UUID.randomUUID());
    }

    public RequestId requestId() {
        return new RequestId(value);
    }
}
