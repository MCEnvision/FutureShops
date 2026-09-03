package com.enviouse.futureshopsp.api.economy;

import java.util.Objects;
import java.util.UUID;

/** Stable server owned identity for one logical mutation request. */
public record RequestId(UUID value) {
    public RequestId {
        Objects.requireNonNull(value, "value");
    }

    public static RequestId random() {
        return new RequestId(UUID.randomUUID());
    }
}
