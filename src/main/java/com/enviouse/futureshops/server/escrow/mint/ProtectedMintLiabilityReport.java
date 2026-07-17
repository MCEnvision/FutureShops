package com.enviouse.futureshops.server.escrow.mint;

import java.util.Map;
import java.util.Objects;

public record ProtectedMintLiabilityReport(long outstandingUnits,
                                           long outstandingValueMinorUnits,
                                           Map<Long, Long> unitsByDenomination,
                                           Map<ProtectedMintState, Long> unitsByState) {
    public ProtectedMintLiabilityReport {
        unitsByDenomination = Map.copyOf(Objects.requireNonNull(unitsByDenomination,
                "unitsByDenomination"));
        unitsByState = Map.copyOf(Objects.requireNonNull(unitsByState, "unitsByState"));
        if (outstandingUnits < 0L || outstandingValueMinorUnits < 0L) {
            throw new IllegalArgumentException("Protected mint liability is invalid");
        }
    }
}
