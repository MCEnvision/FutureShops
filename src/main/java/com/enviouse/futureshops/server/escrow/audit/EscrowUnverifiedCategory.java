package com.enviouse.futureshops.server.escrow.audit;

import java.util.Objects;
import java.util.OptionalLong;

public record EscrowUnverifiedCategory(String category,
                                       OptionalLong units,
                                       String unitLabel,
                                       String reason) {
    public EscrowUnverifiedCategory {
        category = requireText(category, "category");
        units = Objects.requireNonNull(units, "units");
        unitLabel = requireText(unitLabel, "unitLabel");
        reason = requireText(reason, "reason");
    }

    private static String requireText(String value, String label) {
        String normalized = Objects.requireNonNull(value, label).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Escrow unverified " + label + " is empty");
        }
        return normalized;
    }
}
