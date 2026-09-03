package com.enviouse.futureshopsp.api.economy;

import java.util.Objects;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

/** Stable server owned identity for one logical mutation request. */
public record RequestId(UUID value) {
    public RequestId {
        Objects.requireNonNull(value, "value");
    }

    public static RequestId random() {
        return new RequestId(UUID.randomUUID());
    }

    /** Derives a stable child identity for a logical leg without reusing the parent identity. */
    public RequestId child(String role) {
        if (role == null || role.isBlank() || role.length() > 64
                || role.indexOf('\n') >= 0 || role.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("role must be a bounded single line");
        }
        return new RequestId(UUID.nameUUIDFromBytes((value + ":" + role)
                .getBytes(StandardCharsets.UTF_8)));
    }
}
