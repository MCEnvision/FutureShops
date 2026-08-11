package com.enviouse.futureshops.server.market.auction.escrow;

public final class AuctionEscrowLifecycleConflictException
        extends RuntimeException {
    public AuctionEscrowLifecycleConflictException(String message) {
        super(message);
    }

    public AuctionEscrowLifecycleConflictException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}
