package com.enviouse.futureshops.catalog.offer;

import java.util.List;
import java.util.Objects;

public record OfferValidationResult(
        List<OfferValidationIssue> issues
) {
    public OfferValidationResult {
        issues = List.copyOf(Objects.requireNonNull(issues, "issues"));
    }

    public boolean valid() {
        return issues.stream().noneMatch(issue ->
                issue.severity() == OfferValidationIssue.Severity.ERROR);
    }
}
