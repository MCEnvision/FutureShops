package com.enviouse.futureshops.server.market.bazaar;

public enum BazaarTimeInForce {
    GOOD_UNTIL_CANCELLED(1, true),
    GOOD_UNTIL_TIME(2, true),
    IMMEDIATE_OR_CANCEL(3, false),
    FILL_OR_KILL(4, false);

    private final int wireCode;
    private final boolean rests;

    BazaarTimeInForce(int wireCode, boolean rests) {
        this.wireCode = wireCode;
        this.rests = rests;
    }

    public int wireCode() {
        return wireCode;
    }

    public boolean rests() {
        return rests;
    }

    public static BazaarTimeInForce fromWireCode(int wireCode) {
        for (BazaarTimeInForce value : values()) {
            if (value.wireCode == wireCode) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown Bazaar time in force");
    }
}
