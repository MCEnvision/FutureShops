package com.enviouse.futureshops.server.escrow.stock;

import java.util.List;
import java.util.Objects;

public record StockConservationReport(
        boolean conserved,
        long finiteAvailableQuantity,
        long backedHeldQuantity,
        long unlimitedHeldQuantity,
        long committedQuantity,
        long releasedQuantity,
        List<String> violations
) {
    public StockConservationReport {
        if (finiteAvailableQuantity < 0L || backedHeldQuantity < 0L
                || unlimitedHeldQuantity < 0L || committedQuantity < 0L
                || releasedQuantity < 0L) {
            throw new IllegalArgumentException("Stock conservation totals cannot be negative");
        }
        violations = List.copyOf(Objects.requireNonNull(violations, "violations"));
        if (conserved != violations.isEmpty()) {
            throw new IllegalArgumentException("Stock conservation result is inconsistent");
        }
    }
}
