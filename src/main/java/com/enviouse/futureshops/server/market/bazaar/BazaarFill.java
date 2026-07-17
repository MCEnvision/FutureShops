package com.enviouse.futureshops.server.market.bazaar;

import java.util.Objects;
import java.util.UUID;

public record BazaarFill(
        UUID fillId,
        UUID buyOrderId,
        UUID sellOrderId,
        UUID makerOrderId,
        UUID takerOrderId,
        String productId,
        long productVersion,
        int quantity,
        long priceMinor,
        long grossMinor,
        long buyerFeeMinor,
        long sellerFeeMinor,
        long buyerPriceImprovementMinor,
        long sequence,
        long filledAtMillis,
        UUID settlementTransactionId
) {
    public BazaarFill {
        Objects.requireNonNull(fillId, "fillId");
        Objects.requireNonNull(buyOrderId, "buyOrderId");
        Objects.requireNonNull(sellOrderId, "sellOrderId");
        Objects.requireNonNull(makerOrderId, "makerOrderId");
        Objects.requireNonNull(takerOrderId, "takerOrderId");
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(settlementTransactionId, "settlementTransactionId");
        if (productId.isBlank() || productVersion <= 0L || quantity <= 0
                || priceMinor <= 0L || grossMinor != Math.multiplyExact(priceMinor, quantity)
                || buyerFeeMinor < 0L || sellerFeeMinor < 0L
                || buyerPriceImprovementMinor < 0L || sequence <= 0L
                || sequence == Long.MAX_VALUE || filledAtMillis < 0L) {
            throw new IllegalArgumentException("Bazaar fill is invalid");
        }
    }
}
