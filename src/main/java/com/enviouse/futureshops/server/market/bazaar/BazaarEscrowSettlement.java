package com.enviouse.futureshops.server.market.bazaar;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record BazaarEscrowSettlement(
        UUID transactionId,
        UUID ownerId,
        Optional<UUID> orderId,
        BazaarSettlementKind kind,
        long moneyMinor,
        int itemQuantity,
        Optional<UUID> counterpartyId,
        Optional<UUID> fillId
) {
    private static final UUID ZERO = new UUID(0L, 0L);

    public BazaarEscrowSettlement {
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(ownerId, "ownerId");
        orderId = Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(kind, "kind");
        counterpartyId = Objects.requireNonNull(counterpartyId, "counterpartyId");
        fillId = Objects.requireNonNull(fillId, "fillId");
        if (ZERO.equals(transactionId) || ZERO.equals(ownerId)
                || orderId.filter(ZERO::equals).isPresent()
                || counterpartyId.filter(ZERO::equals).isPresent()
                || fillId.filter(ZERO::equals).isPresent()
                || moneyMinor < 0L || itemQuantity < 0
                || moneyMinor > 0L == (itemQuantity > 0)) {
            throw new IllegalArgumentException("Bazaar settlement is invalid");
        }
        boolean itemSettlement = kind == BazaarSettlementKind.BUYER_ITEM_CLAIM
                || kind == BazaarSettlementKind.SELLER_ITEM_REFUND_CLAIM;
        boolean fillSettlement = kind == BazaarSettlementKind.BUYER_ITEM_CLAIM
                || kind == BazaarSettlementKind.SELLER_MONEY_CLAIM
                || kind == BazaarSettlementKind.BUYER_CHANGE_CLAIM
                || kind == BazaarSettlementKind.FEE_DESTINATION;
        boolean feeSettlement = kind == BazaarSettlementKind.FEE_DESTINATION;
        if (itemSettlement != (itemQuantity > 0)
                || fillSettlement != fillId.isPresent()
                || feeSettlement != orderId.isEmpty()
                || feeSettlement && counterpartyId.isPresent()
                || !feeSettlement && orderId.isEmpty()
                || fillSettlement && !feeSettlement
                && counterpartyId.isEmpty()
                || !fillSettlement && counterpartyId.isPresent()) {
            throw new IllegalArgumentException(
                    "Bazaar settlement shape is invalid");
        }
    }
}
