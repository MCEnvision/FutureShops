package com.enviouse.futureshops.server.market.bazaar;

public enum BazaarLifecycleType {
    SET_EFFECTIVE_RULES(1),
    REGISTER_PRODUCT(2),
    SET_PRODUCT_STATUS(3),
    SET_REFERENCE_PRICE(4);

    private final int wireCode;

    BazaarLifecycleType(int wireCode) {
        this.wireCode = wireCode;
    }

    public int wireCode() {
        return wireCode;
    }

    public static BazaarLifecycleType fromWireCode(int wireCode) {
        for (BazaarLifecycleType value : values()) {
            if (value.wireCode == wireCode) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown Bazaar lifecycle type");
    }
}
