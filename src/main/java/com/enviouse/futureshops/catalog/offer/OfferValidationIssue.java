package com.enviouse.futureshops.catalog.offer;

import java.util.Objects;

public record OfferValidationIssue(
        Severity severity,
        String path,
        String code
) {
    public OfferValidationIssue {
        severity = Objects.requireNonNull(severity, "severity");
        path = Objects.requireNonNullElse(path, "").strip();
        code = Objects.requireNonNullElse(code, "").strip();
    }

    public enum Severity {
        ERROR,
        WARNING
    }
}
