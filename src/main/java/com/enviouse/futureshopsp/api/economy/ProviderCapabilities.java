package com.enviouse.futureshopsp.api.economy;

import java.util.Objects;

/** Immutable capability declaration for one provider instance. */
public record ProviderCapabilities(
        boolean balanceQuery,
        boolean precheck,
        boolean withdraw,
        boolean deposit,
        boolean receiptLookup,
        boolean idempotentRetry) {

    public static ProviderCapabilities none() {
        return new ProviderCapabilities(false, false, false, false, false, false);
    }

    public static ProviderCapabilities all() {
        return new ProviderCapabilities(true, true, true, true, true, true);
    }

    public boolean supports(EconomyCapability capability) {
        Objects.requireNonNull(capability, "capability");
        return switch (capability) {
            case BALANCE_QUERY -> balanceQuery;
            case PRECHECK -> precheck;
            case WITHDRAW -> withdraw;
            case DEPOSIT -> deposit;
            case RECEIPT_LOOKUP -> receiptLookup;
            case IDEMPOTENT_RETRY -> idempotentRetry;
        };
    }
}
