package com.enviouse.futureshops.server.market.control;

public enum MarketModuleStatus {
    ENABLED(1),
    FROZEN(2),
    DRAINING(3),
    CANCEL_AND_REFUND(4);

    private final int wireTag;

    MarketModuleStatus(int wireTag) {
        this.wireTag = wireTag;
    }

    public int wireTag() {
        return wireTag;
    }

    public boolean acceptsNewValueOperations() {
        return this == ENABLED;
    }

    public boolean allowsExistingValueOperations() {
        return this == ENABLED || this == DRAINING;
    }

    public boolean timersPaused() {
        return this == FROZEN;
    }

    public boolean requiresCancellationAndRefund() {
        return this == CANCEL_AND_REFUND;
    }

    public static MarketModuleStatus fromWireTag(int tag) {
        for (MarketModuleStatus status : values()) {
            if (status.wireTag == tag) {
                return status;
            }
        }
        throw new IllegalArgumentException(
                "Market module status tag is invalid");
    }
}
