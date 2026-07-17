package com.enviouse.futureshops.server.escrow.model;

import java.util.Objects;

public record EscrowRequestKey(String value) {
    public static final int MAX_LENGTH = 160;

    public EscrowRequestKey {
        Objects.requireNonNull(value, "value");
        value = value.strip();
        if (value.isEmpty() || value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Invalid escrow request key");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
