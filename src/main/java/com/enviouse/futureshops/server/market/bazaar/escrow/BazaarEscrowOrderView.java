package com.enviouse.futureshops.server.market.bazaar.escrow;

import com.enviouse.futureshops.server.market.bazaar.BazaarOrder;
import com.enviouse.futureshops.server.market.bazaar.BazaarOrderSide;
import com.enviouse.futureshops.server.market.bazaar.BazaarOrderState;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record BazaarEscrowOrderView(
        UUID orderId,
        UUID ownerId,
        String productId,
        long productVersion,
        BazaarOrderSide side,
        BazaarOrderState state,
        long revision,
        int originalQuantity,
        int remainingQuantity,
        int filledQuantity,
        long reservedMoneyMinor,
        int reservedItemQuantity,
        Optional<UUID> holdAccountId,
        Optional<UUID> custodyLotId
) {
    public BazaarEscrowOrderView {
        orderId = BazaarEscrowIds.requireId(orderId, "orderId");
        ownerId = BazaarEscrowIds.requireId(ownerId, "ownerId");
        productId = Objects.requireNonNull(productId, "productId");
        side = Objects.requireNonNull(side, "side");
        state = Objects.requireNonNull(state, "state");
        holdAccountId = Objects.requireNonNull(holdAccountId,
                "holdAccountId");
        custodyLotId = Objects.requireNonNull(custodyLotId,
                "custodyLotId");
        holdAccountId.ifPresent(value -> BazaarEscrowIds.requireId(value,
                "holdAccountId"));
        custodyLotId.ifPresent(value -> BazaarEscrowIds.requireId(value,
                "custodyLotId"));
        if (productId.isBlank() || productId.length() > 96
                || productVersion <= 0L || revision < 0L
                || originalQuantity <= 0 || remainingQuantity < 0
                || filledQuantity < 0
                || Math.addExact(remainingQuantity, filledQuantity)
                != originalQuantity || reservedMoneyMinor < 0L
                || reservedItemQuantity < 0
                || side == BazaarOrderSide.BUY
                != holdAccountId.isPresent()
                || side == BazaarOrderSide.SELL
                != custodyLotId.isPresent()
                || side == BazaarOrderSide.BUY
                && reservedItemQuantity != 0
                || side == BazaarOrderSide.SELL
                && reservedMoneyMinor != 0L
                || state.terminal()
                && (reservedMoneyMinor != 0L
                || reservedItemQuantity != 0)) {
            throw new IllegalArgumentException(
                    "Bazaar escrow order view is invalid");
        }
    }

    public static BazaarEscrowOrderView from(BazaarOrder order) {
        Objects.requireNonNull(order, "order");
        return new BazaarEscrowOrderView(order.orderId(), order.ownerId(),
                order.productId(), order.productVersion(), order.side(),
                order.state(), order.revision(), order.originalQuantity(),
                order.remainingQuantity(), order.filledQuantity(),
                order.reservedMoneyMinor(), order.reservedItemQuantity(),
                order.moneyHoldAccountId(), order.custodyLotId());
    }
}
