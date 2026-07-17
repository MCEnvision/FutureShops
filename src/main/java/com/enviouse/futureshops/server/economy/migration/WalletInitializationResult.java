package com.enviouse.futureshops.server.economy.migration;

import java.util.Objects;

public record WalletInitializationResult(WalletInitializationDisposition disposition,
                                         String detail) {
    private static final int MAXIMUM_DETAIL_LENGTH = 256;

    public WalletInitializationResult {
        Objects.requireNonNull(disposition, "disposition");
        detail = Objects.requireNonNull(detail, "detail").trim();
        if (detail.length() > MAXIMUM_DETAIL_LENGTH) {
            throw new IllegalArgumentException("Wallet initialization detail is too long");
        }
    }

    public static WalletInitializationResult applied() {
        return new WalletInitializationResult(
                WalletInitializationDisposition.APPLIED, "");
    }

    public static WalletInitializationResult replayed() {
        return new WalletInitializationResult(
                WalletInitializationDisposition.REPLAYED, "");
    }

    public static WalletInitializationResult alreadyInitialized(
            String detail
    ) {
        return new WalletInitializationResult(
                WalletInitializationDisposition.ALREADY_INITIALIZED, detail);
    }

    public static WalletInitializationResult retryLater(String detail) {
        return new WalletInitializationResult(
                WalletInitializationDisposition.RETRY_LATER, detail);
    }

    public static WalletInitializationResult conflict(String detail) {
        return new WalletInitializationResult(
                WalletInitializationDisposition.CONFLICT, detail);
    }
}
