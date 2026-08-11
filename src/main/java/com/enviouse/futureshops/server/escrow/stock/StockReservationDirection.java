package com.enviouse.futureshops.server.escrow.stock;

public enum StockReservationDirection {
    OUTBOUND(1),
    INBOUND(2);

    private final int wireId;

    StockReservationDirection(int wireId) {
        this.wireId = wireId;
    }

    public int wireId() {
        return wireId;
    }

    public static StockReservationDirection fromWireId(int wireId) {
        for (StockReservationDirection value : values()) {
            if (value.wireId == wireId) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown stock reservation direction");
    }
}
