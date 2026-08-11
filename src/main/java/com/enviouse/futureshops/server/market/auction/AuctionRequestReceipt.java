package com.enviouse.futureshops.server.market.auction;

import java.util.Locale;
import java.util.Objects;

public record AuctionRequestReceipt(String fingerprint, AuctionOperationResult result) {
    public AuctionRequestReceipt {
        fingerprint = Objects.requireNonNull(fingerprint, "fingerprint").toLowerCase(Locale.ROOT);
        Objects.requireNonNull(result, "result");
        if (fingerprint.length() != 64
            || !fingerprint.chars().allMatch(character -> Character.digit(character, 16) >= 0)
            || result.replayed()) {
            throw new IllegalArgumentException("Auction request receipt is invalid.");
        }
    }
}
