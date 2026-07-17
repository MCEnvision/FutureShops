package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.money.AtmCurrencyCatalog;

import java.util.Objects;
import java.util.regex.Pattern;

public record AtmAccessSnapshot(
        AtmCurrencyCatalog catalog,
        boolean balanceKnown,
        long balanceMinorUnits,
        boolean serviceAvailable,
        String availabilityCode
) {
    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");

    public AtmAccessSnapshot {
        Objects.requireNonNull(catalog, "catalog");
        availabilityCode = Objects.requireNonNull(
                availabilityCode, "availabilityCode");
        if (!balanceKnown && balanceMinorUnits != 0L
                || !CODE.matcher(availabilityCode).matches()
                || serviceAvailable
                != availabilityCode.equals("AVAILABLE")
                || serviceAvailable && !balanceKnown) {
            throw new IllegalArgumentException(
                    "ATM access snapshot is invalid");
        }
    }
}
