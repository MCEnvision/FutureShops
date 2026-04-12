package com.enviouse.futureshops.coin;

public record CoinValidationResult(boolean valid, long denominationMinorUnits, String errorCode) {
    public static CoinValidationResult ok(long denominationMinorUnits) {
        return new CoinValidationResult(true, denominationMinorUnits, "");
    }

    public static CoinValidationResult error(String errorCode) {
        return new CoinValidationResult(false, 0L, errorCode);
    }
}

