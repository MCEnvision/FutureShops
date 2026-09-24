package com.enviouse.futureshops.api.economy;

import java.util.Objects;

/** Immutable provider owned currency metadata for API v1. */
public record CurrencyV1(String id, String singularName, String pluralName, int precision) {
    public CurrencyV1 {
        id = requireToken(id, "id", 128);
        singularName = requireToken(singularName, "singularName", 64);
        pluralName = requireToken(pluralName, "pluralName", 64);
        if (precision < 0 || precision > 6) {
            throw new IllegalArgumentException("precision is outside the supported range");
        }
    }

    public CurrencyMetadata metadata() {
        return new CurrencyMetadata(singularName, pluralName, precision);
    }

    private static String requireToken(String value, String field, int maxLength) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > maxLength
                || normalized.indexOf('\n') >= 0 || normalized.indexOf('\r') >= 0) {
            throw new IllegalArgumentException(field + " must be a bounded single line");
        }
        return normalized;
    }
}
