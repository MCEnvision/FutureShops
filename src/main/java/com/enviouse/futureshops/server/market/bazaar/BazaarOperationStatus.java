package com.enviouse.futureshops.server.market.bazaar;

public enum BazaarOperationStatus {
    APPLIED(1),
    REQUEST_CONFLICT(2),
    PRODUCT_MISSING(3),
    PRODUCT_VERSION_CHANGED(4),
    RULE_REVISION_CHANGED(5),
    PRODUCT_UNAVAILABLE(6),
    ORDER_MISSING(7),
    ORDER_NOT_OPEN(8),
    OWNER_MISMATCH(9),
    REVISION_CHANGED(10),
    INVALID_ORDER(11),
    LIMIT_EXCEEDED(12),
    PRICE_OUTSIDE_BAND(13),
    MATCH_BUDGET_EXCEEDED(14),
    RETENTION_LIMIT_REACHED(15),
    FILL_OR_KILL_UNAVAILABLE(16),
    SELF_TRADE_CANCELLED(17),
    EXPIRED(18),
    ARITHMETIC_OVERFLOW(19);

    private final int wireCode;

    BazaarOperationStatus(int wireCode) {
        this.wireCode = wireCode;
    }

    public int wireCode() {
        return wireCode;
    }

    public static BazaarOperationStatus fromWireCode(int wireCode) {
        for (BazaarOperationStatus value : values()) {
            if (value.wireCode == wireCode) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown Bazaar operation status");
    }
}
