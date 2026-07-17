package com.enviouse.futureshops.server.escrow.mint;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ProtectedMintConservationReport(Map<ProtectedMintState, Long> unitsByState,
                                              Map<ProtectedMintState, Long> valueByState,
                                              long issuedUnits,
                                              long issuedValueMinorUnits,
                                              boolean conserved,
                                              List<String> violations) {
    public ProtectedMintConservationReport {
        unitsByState = Map.copyOf(Objects.requireNonNull(unitsByState, "unitsByState"));
        valueByState = Map.copyOf(Objects.requireNonNull(valueByState, "valueByState"));
        violations = List.copyOf(Objects.requireNonNull(violations, "violations"));
        if (issuedUnits < 0L || issuedValueMinorUnits < 0L
                || conserved != violations.isEmpty()) {
            throw new IllegalArgumentException("Protected mint conservation report is invalid");
        }
    }
}
