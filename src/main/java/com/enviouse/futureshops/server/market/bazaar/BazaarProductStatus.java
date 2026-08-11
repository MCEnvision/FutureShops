package com.enviouse.futureshops.server.market.bazaar;

public enum BazaarProductStatus {
    ACTIVE(1, true),
    HALTED(2, false),
    RETIRED(3, false);

    private final int wireCode;
    private final boolean matches;

    BazaarProductStatus(int wireCode, boolean matches) {
        this.wireCode = wireCode;
        this.matches = matches;
    }

    public int wireCode() {
        return wireCode;
    }

    public boolean matches() {
        return matches;
    }

    public static BazaarProductStatus fromWireCode(int wireCode) {
        for (BazaarProductStatus value : values()) {
            if (value.wireCode == wireCode) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown Bazaar product status");
    }
}
