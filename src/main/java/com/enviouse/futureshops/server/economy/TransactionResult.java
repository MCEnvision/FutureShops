package com.enviouse.futureshops.server.economy;

import com.enviouse.futureshops.server.shop.ShopResultCode;

/**
 * Outcome record for every {@link EconomyProvider} operation.
 *
 * <p>The {@link #errorCode()} field is typed as {@link ShopResultCode} so the whole
 * transaction surface (economy + shop packet layer) shares one exhaustive enum.
 * This mod owns both sides — the economy is not a cross-mod API — so leaking the
 * enum into the provider contract is fine: if someone later writes an external
 * {@code EconomyProvider}, they'll get compile-time guidance on which codes to emit
 * (and can always fall back to {@link ShopResultCode#SERVER_ERROR} for opaque errors).
 */
public record TransactionResult(boolean success, long resultingBalance, ShopResultCode errorCode) {
    public static TransactionResult ok(long resultingBalance) {
        return new TransactionResult(true, resultingBalance, ShopResultCode.OK);
    }

    public static TransactionResult error(ShopResultCode errorCode, long currentBalance) {
        return new TransactionResult(false, currentBalance, errorCode);
    }
}

