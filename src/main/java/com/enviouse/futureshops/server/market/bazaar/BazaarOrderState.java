package com.enviouse.futureshops.server.market.bazaar;

public enum BazaarOrderState {
    HOLDING(1, false),
    MATCHING(2, false),
    OPEN(3, false),
    PARTIALLY_FILLED(4, false),
    FILLED(5, true),
    CANCEL_PENDING(6, false),
    CANCELLED(7, true),
    EXPIRED(8, true),
    FROZEN(9, false),
    SETTLED(10, true),
    MANUAL_REVIEW(11, false);

    private final int wireCode;
    private final boolean terminal;

    BazaarOrderState(int wireCode, boolean terminal) {
        this.wireCode = wireCode;
        this.terminal = terminal;
    }

    public int wireCode() {
        return wireCode;
    }

    public boolean terminal() {
        return terminal;
    }

    public static BazaarOrderState fromWireCode(int wireCode) {
        for (BazaarOrderState value : values()) {
            if (value.wireCode == wireCode) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown Bazaar order state");
    }
}
