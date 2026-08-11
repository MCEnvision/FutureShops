package com.enviouse.futureshops.server.market.bazaar;

public enum BazaarOperationType {
    CREATE(1),
    CANCEL(2),
    EXPIRE(3);

    private final int wireCode;

    BazaarOperationType(int wireCode) {
        this.wireCode = wireCode;
    }

    public int wireCode() {
        return wireCode;
    }

    public static BazaarOperationType fromWireCode(int wireCode) {
        for (BazaarOperationType value : values()) {
            if (value.wireCode == wireCode) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown Bazaar operation type");
    }
}
