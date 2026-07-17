package com.enviouse.futureshops.server.escrow.audit;

import java.util.Objects;
import java.util.OptionalLong;

public record EscrowLiabilityComparison(String liability,
                                        OptionalLong ledgerMinorUnits,
                                        OptionalLong authoritativeMinorUnits,
                                        boolean matches) {
    public EscrowLiabilityComparison {
        liability = Objects.requireNonNull(liability, "liability").strip();
        ledgerMinorUnits = Objects.requireNonNull(
                ledgerMinorUnits, "ledgerMinorUnits");
        authoritativeMinorUnits = Objects.requireNonNull(
                authoritativeMinorUnits, "authoritativeMinorUnits");
        boolean exactMatch = ledgerMinorUnits.isPresent()
                && authoritativeMinorUnits.isPresent()
                && ledgerMinorUnits.getAsLong() == authoritativeMinorUnits.getAsLong();
        if (liability.isEmpty() || matches != exactMatch) {
            throw new IllegalArgumentException("Escrow liability comparison is invalid");
        }
    }
}
