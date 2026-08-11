package com.enviouse.futureshops.server.market.bazaar;

public enum BazaarOrderSide {
    BUY(1),
    SELL(2);

    private final int wireCode;

    BazaarOrderSide(int wireCode) {
        this.wireCode = wireCode;
    }

    public int wireCode() {
        return wireCode;
    }

    public static BazaarOrderSide fromWireCode(int wireCode) {
        for (BazaarOrderSide value : values()) {
            if (value.wireCode == wireCode) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown Bazaar order side");
    }
}
