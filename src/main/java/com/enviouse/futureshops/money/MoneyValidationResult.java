package com.enviouse.futureshops.money;

public record MoneyValidationResult(boolean valid, long denominationMinorUnits, int authorizedCount,
                                   String mintId, String errorCode) {
    public static MoneyValidationResult ok(long denominationMinorUnits, int authorizedCount, String mintId) {
        return new MoneyValidationResult(true, denominationMinorUnits, authorizedCount, mintId, "");
    }

    public static MoneyValidationResult error(String errorCode) {
        return new MoneyValidationResult(false, 0L, 0, "", errorCode);
    }
}
