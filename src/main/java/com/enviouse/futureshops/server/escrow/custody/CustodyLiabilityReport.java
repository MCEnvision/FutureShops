package com.enviouse.futureshops.server.escrow.custody;

import java.util.Map;
import java.util.Objects;

public record CustodyLiabilityReport(
        Map<CustodyLiabilityKey, Long> outstandingByAsset,
        long walletReservedMinorUnits,
        long protectedCurrencyOutstandingMinorUnits,
        long foreignCurrencyOutstandingMinorUnits,
        long itemUnitsOutstanding,
        long heldLotCount
) {
    public CustodyLiabilityReport {
        Objects.requireNonNull(outstandingByAsset, "outstandingByAsset");
        outstandingByAsset = Map.copyOf(outstandingByAsset);
        if (walletReservedMinorUnits < 0L
                || protectedCurrencyOutstandingMinorUnits < 0L
                || foreignCurrencyOutstandingMinorUnits < 0L
                || itemUnitsOutstanding < 0L
                || heldLotCount < 0L) {
            throw new IllegalArgumentException("Custody liabilities cannot be negative");
        }
    }
}
