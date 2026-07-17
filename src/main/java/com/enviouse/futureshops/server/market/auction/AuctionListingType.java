package com.enviouse.futureshops.server.market.auction;

public enum AuctionListingType {
    BUY_NOW(1),
    TIMED_AUCTION(2),
    AUCTION_WITH_BUYOUT(3);

    private final int wireCode;

    AuctionListingType(int wireCode) {
        this.wireCode = wireCode;
    }

    public int wireCode() {
        return wireCode;
    }

    public boolean acceptsBids() {
        return this != BUY_NOW;
    }

    public boolean hasBuyout() {
        return this != TIMED_AUCTION;
    }
}
