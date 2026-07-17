package com.enviouse.futureshops.server.market.bazaar;

public enum BazaarSettlementKind {
    BUYER_ITEM_CLAIM(1),
    SELLER_MONEY_CLAIM(2),
    BUYER_CHANGE_CLAIM(3),
    BUYER_REFUND_CLAIM(4),
    SELLER_ITEM_REFUND_CLAIM(5),
    FEE_DESTINATION(6);

    private final int wireCode;

    BazaarSettlementKind(int wireCode) {
        this.wireCode = wireCode;
    }

    public int wireCode() {
        return wireCode;
    }

    public static BazaarSettlementKind fromWireCode(int wireCode) {
        for (BazaarSettlementKind value : values()) {
            if (value.wireCode == wireCode) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown Bazaar settlement kind");
    }
}
