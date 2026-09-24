package com.enviouse.futureshops.api.economy;

import java.util.Objects;
import java.util.UUID;

/** Stable identity for one logical economy operation root. */
public record RootId(UUID value) {
    public RootId {
        Objects.requireNonNull(value, "value");
        if (value.equals(new UUID(0L, 0L))) {
            throw new IllegalArgumentException("root id must not be zero");
        }
    }

    public static RootId random() {
        return new RootId(UUID.randomUUID());
    }

    public RequestId requestId() {
        return new RequestId(value);
    }
}
