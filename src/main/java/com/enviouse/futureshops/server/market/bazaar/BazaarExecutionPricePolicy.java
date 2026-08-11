package com.enviouse.futureshops.server.market.bazaar;

public enum BazaarExecutionPricePolicy {
    MAKER(1),
    TAKER(2),
    MIDPOINT(3);

    private final int wireCode;

    BazaarExecutionPricePolicy(int wireCode) {
        this.wireCode = wireCode;
    }

    public int wireCode() {
        return wireCode;
    }

    public static BazaarExecutionPricePolicy fromWireCode(int wireCode) {
        for (BazaarExecutionPricePolicy value : values()) {
            if (value.wireCode == wireCode) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown Bazaar execution price policy");
    }
}
