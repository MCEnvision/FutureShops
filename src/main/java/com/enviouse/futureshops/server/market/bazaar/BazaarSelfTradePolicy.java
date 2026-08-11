package com.enviouse.futureshops.server.market.bazaar;

public enum BazaarSelfTradePolicy {
    CANCEL_TAKER(1),
    CANCEL_MAKER(2),
    SKIP_SELF(3);

    private final int wireCode;

    BazaarSelfTradePolicy(int wireCode) {
        this.wireCode = wireCode;
    }

    public int wireCode() {
        return wireCode;
    }

    public static BazaarSelfTradePolicy fromWireCode(int wireCode) {
        for (BazaarSelfTradePolicy value : values()) {
            if (value.wireCode == wireCode) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown Bazaar self trade policy");
    }
}
