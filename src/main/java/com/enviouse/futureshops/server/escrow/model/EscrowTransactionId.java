package com.enviouse.futureshops.server.escrow.model;

import java.util.Objects;
import java.util.UUID;

public record EscrowTransactionId(UUID value) {
    public EscrowTransactionId {
        Objects.requireNonNull(value, "value");
    }

    public static EscrowTransactionId random() {
        return new EscrowTransactionId(UUID.randomUUID());
    }

    public static EscrowTransactionId parse(String value) {
        return new EscrowTransactionId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
