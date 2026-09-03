package com.enviouse.futureshopsp.api.economy;

import java.util.Objects;

/** Validated display metadata owned by the selected provider. */
public record CurrencyMetadata(String singularName, String pluralName, int decimalPlaces) {
    public CurrencyMetadata {
        singularName = validateName(singularName, "singularName");
        pluralName = validateName(pluralName, "pluralName");
        if (decimalPlaces < 0 || decimalPlaces > 6) {
            throw new IllegalArgumentException("decimalPlaces must be between 0 and 6");
        }
    }

    private static String validateName(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > 64 || normalized.indexOf('\n') >= 0 || normalized.indexOf('\r') >= 0) {
            throw new IllegalArgumentException(field + " must be a nonempty single line of at most 64 characters");
        }
        return normalized;
    }
}
