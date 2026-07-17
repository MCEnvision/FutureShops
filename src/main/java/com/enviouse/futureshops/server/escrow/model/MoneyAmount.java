package com.enviouse.futureshops.server.escrow.model;

import java.util.Objects;

public record MoneyAmount(String currencyId, long minorUnits) implements Comparable<MoneyAmount> {
    public static final int MAX_CURRENCY_ID_LENGTH = 128;

    public MoneyAmount {
        Objects.requireNonNull(currencyId, "currencyId");
        currencyId = currencyId.strip();
        if (currencyId.isEmpty() || currencyId.length() > MAX_CURRENCY_ID_LENGTH) {
            throw new IllegalArgumentException("Invalid currency id");
        }
        if (minorUnits < 0L) {
            throw new IllegalArgumentException("Money minor units cannot be negative");
        }
    }

    public MoneyAmount add(MoneyAmount other) {
        requireSameCurrency(other);
        return new MoneyAmount(currencyId, Math.addExact(minorUnits, other.minorUnits));
    }

    public MoneyAmount subtract(MoneyAmount other) {
        requireSameCurrency(other);
        long result = Math.subtractExact(minorUnits, other.minorUnits);
        if (result < 0L) {
            throw new ArithmeticException("Money result cannot be negative");
        }
        return new MoneyAmount(currencyId, result);
    }

    public MoneyAmount multiply(long multiplier) {
        if (multiplier < 0L) {
            throw new IllegalArgumentException("Money multiplier cannot be negative");
        }
        return new MoneyAmount(currencyId, Math.multiplyExact(minorUnits, multiplier));
    }

    @Override
    public int compareTo(MoneyAmount other) {
        requireSameCurrency(other);
        return Long.compare(minorUnits, other.minorUnits);
    }

    private void requireSameCurrency(MoneyAmount other) {
        Objects.requireNonNull(other, "other");
        if (!currencyId.equals(other.currencyId)) {
            throw new IllegalArgumentException("Currency ids do not match");
        }
    }
}
