package com.enviouse.futureshops.server.escrow.stock;

public enum StockMutationType {
    SEED(1),
    RESERVE(2),
    COMMIT(3),
    RELEASE(4),
    REFRESH(5),
    ADMIN_RESET(6),
    RELOAD_RECONCILE(7),
    RESERVE_BATCH(8),
    COMMIT_BATCH(9),
    RELEASE_BATCH(10);

    private final int wireId;

    StockMutationType(int wireId) {
        this.wireId = wireId;
    }

    public int wireId() {
        return wireId;
    }

    public boolean batchOperation() {
        return this == RESERVE_BATCH || this == COMMIT_BATCH
                || this == RELEASE_BATCH;
    }

    public static StockMutationType fromWireId(int wireId) {
        for (StockMutationType value : values()) {
            if (value.wireId == wireId) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown stock mutation type");
    }
}
