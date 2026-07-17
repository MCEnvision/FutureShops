package com.enviouse.futureshops.client.market;

public enum MarketModuleAvailability {
    ENABLED,
    CLAIMS_ONLY,
    DISABLED,
    HIDDEN;

    public boolean visible() {
        return this != HIDDEN;
    }

    public boolean allowsBrowse() {
        return this == ENABLED;
    }

    public boolean allowsClaims() {
        return this == ENABLED || this == CLAIMS_ONLY;
    }
}
