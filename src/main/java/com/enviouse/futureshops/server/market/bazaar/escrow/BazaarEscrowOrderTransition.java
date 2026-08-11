package com.enviouse.futureshops.server.market.bazaar.escrow;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record BazaarEscrowOrderTransition(
        UUID orderId,
        Optional<BazaarEscrowOrderView> beforeOrder,
        Optional<BazaarEscrowOrderView> afterOrder,
        Optional<BazaarEscrowOrderBacking> beforeBacking,
        Optional<BazaarEscrowOrderBacking> afterBacking
) {
    public BazaarEscrowOrderTransition {
        orderId = BazaarEscrowIds.requireId(orderId, "orderId");
        beforeOrder = Objects.requireNonNull(beforeOrder, "beforeOrder");
        afterOrder = Objects.requireNonNull(afterOrder, "afterOrder");
        beforeBacking = Objects.requireNonNull(beforeBacking,
                "beforeBacking");
        afterBacking = Objects.requireNonNull(afterBacking,
                "afterBacking");
        if (beforeOrder.isEmpty() && afterOrder.isEmpty()
                || beforeOrder.isPresent() && !beforeOrder.orElseThrow()
                .orderId().equals(orderId)
                || afterOrder.isPresent() && !afterOrder.orElseThrow()
                .orderId().equals(orderId)
                || beforeBacking.isPresent()
                && !beforeBacking.orElseThrow().orderId().equals(orderId)
                || afterBacking.isPresent()
                && !afterBacking.orElseThrow().orderId().equals(orderId)
                || beforeOrder.isPresent() != beforeBacking.isPresent()
                || afterBacking.isPresent()
                != afterOrder.filter(value -> !value.state().terminal())
                .isPresent()) {
            throw new IllegalArgumentException(
                    "Bazaar escrow order transition shape is invalid");
        }
        if (beforeOrder.isPresent()) {
            beforeBacking.orElseThrow().requireMatchesView(
                    beforeOrder.orElseThrow());
        }
        if (afterOrder.isPresent()
                && !afterOrder.orElseThrow().state().terminal()) {
            afterBacking.orElseThrow().requireMatchesView(
                    afterOrder.orElseThrow());
        }
        if (beforeOrder.isPresent() && afterOrder.isPresent()) {
            BazaarEscrowOrderView before = beforeOrder.orElseThrow();
            BazaarEscrowOrderView after = afterOrder.orElseThrow();
            if (!before.ownerId().equals(after.ownerId())
                    || !before.productId().equals(after.productId())
                    || before.productVersion() != after.productVersion()
                    || before.side() != after.side()
                    || after.revision() <= before.revision()
                    || after.originalQuantity()
                    != before.originalQuantity()
                    || after.filledQuantity() < before.filledQuantity()
                    || after.remainingQuantity() > before.remainingQuantity()
                    || after.reservedMoneyMinor()
                    > before.reservedMoneyMinor()
                    || after.reservedItemQuantity()
                    > before.reservedItemQuantity()) {
                throw new IllegalArgumentException(
                        "Bazaar escrow order transition is invalid");
            }
        }
    }
}
