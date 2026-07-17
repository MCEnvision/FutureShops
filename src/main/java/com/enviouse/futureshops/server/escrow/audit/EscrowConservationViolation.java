package com.enviouse.futureshops.server.escrow.audit;

import java.util.Objects;
import java.util.OptionalLong;

public record EscrowConservationViolation(EscrowConservationViolationCode code,
                                          String subject,
                                          OptionalLong expected,
                                          OptionalLong actual,
                                          String detail) {
    public EscrowConservationViolation {
        Objects.requireNonNull(code, "code");
        subject = requireText(subject, "subject");
        expected = Objects.requireNonNull(expected, "expected");
        actual = Objects.requireNonNull(actual, "actual");
        detail = requireText(detail, "detail");
    }

    private static String requireText(String value, String label) {
        String normalized = Objects.requireNonNull(value, label).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Escrow conservation " + label + " is empty");
        }
        return normalized;
    }
}
