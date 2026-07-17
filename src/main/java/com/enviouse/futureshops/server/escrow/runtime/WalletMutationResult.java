package com.enviouse.futureshops.server.escrow.runtime;

import java.util.Objects;
import java.util.OptionalLong;

public record WalletMutationResult(WalletMutationStatus status,
                                   long primaryBalance,
                                   OptionalLong secondaryBalance) {
    public WalletMutationResult {
        Objects.requireNonNull(status, "status");
        secondaryBalance = Objects.requireNonNull(
                secondaryBalance, "secondaryBalance");
    }

    public boolean success() {
        return status == WalletMutationStatus.APPLIED
                || status == WalletMutationStatus.REPLAYED;
    }

    public boolean replayed() {
        return status == WalletMutationStatus.REPLAYED;
    }

    public static WalletMutationResult single(WalletMutationStatus status,
                                              long balance) {
        return new WalletMutationResult(status, balance, OptionalLong.empty());
    }

    public static WalletMutationResult pair(WalletMutationStatus status,
                                            long primaryBalance,
                                            long secondaryBalance) {
        return new WalletMutationResult(status, primaryBalance,
                OptionalLong.of(secondaryBalance));
    }
}
