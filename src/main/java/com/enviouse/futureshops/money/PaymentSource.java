package com.enviouse.futureshops.money;

import java.util.Locale;
import java.util.Optional;

public enum PaymentSource {
    WALLET,
    PHYSICAL;

    public String wire() {
        return name();
    }

    public static Optional<PaymentSource> fromWire(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
