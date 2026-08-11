package com.enviouse.futureshops.server.market.auction;

public enum AuctionListingState {
    DRAFT,
    HOLDING,
    ACTIVE,
    FROZEN,
    SOLD_PENDING,
    ENDED_SOLD,
    ENDED_UNSOLD,
    CANCEL_PENDING,
    CANCELLED,
    SETTLED,
    MANUAL_REVIEW;

    public boolean terminal() {
        return this == ENDED_SOLD || this == ENDED_UNSOLD || this == CANCELLED || this == SETTLED;
    }
}
