package com.enviouse.futureshops.server.escrow.runtime;

public record AtmBillSelection(int denominationIndex,
                               long denominationMinorUnits,
                               int billCount) {
    public AtmBillSelection {
        if (denominationIndex < -1
                || denominationMinorUnits <= 0L
                || billCount <= 0
                || billCount > 4096) {
            throw new IllegalArgumentException(
                    "ATM bill selection is invalid");
        }
        Math.multiplyExact(denominationMinorUnits, (long) billCount);
    }
}
