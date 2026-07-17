package com.enviouse.futureshops.server.market.bazaar;

public enum BazaarOrderType {
    LIMIT(1),
    INSTANT(2);

    private final int wireCode;

    BazaarOrderType(int wireCode) {
        this.wireCode = wireCode;
    }

    public int wireCode() {
        return wireCode;
    }

    public static BazaarOrderType fromWireCode(int wireCode) {
        for (BazaarOrderType value : values()) {
            if (value.wireCode == wireCode) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown Bazaar order type");
    }
}
