package com.enviouse.futureshops.server.market.auction;

public enum AuctionTimeBasis {
    REAL_TIME(1),
    ONLINE_TIME(2);

    private final int wireCode;

    AuctionTimeBasis(int wireCode) {
        this.wireCode = wireCode;
    }

    public int wireCode() {
        return wireCode;
    }
}
