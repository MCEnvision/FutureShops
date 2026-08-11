package com.enviouse.futureshops.server.market.bazaar.escrow;

import com.enviouse.futureshops.server.market.bazaar.BazaarOrder;
import com.enviouse.futureshops.server.market.bazaar.BazaarOrderSide;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record BazaarEscrowOrderBacking(
        UUID orderId,
        BazaarOrderSide side,
        Optional<BazaarBuyFundingEvidence> buyFunding,
        Optional<BazaarSellCustodyState> sellCustody
) {
    public BazaarEscrowOrderBacking {
        orderId = BazaarEscrowIds.requireId(orderId, "orderId");
        side = Objects.requireNonNull(side, "side");
        buyFunding = Objects.requireNonNull(buyFunding, "buyFunding");
        sellCustody = Objects.requireNonNull(sellCustody, "sellCustody");
        if (side == BazaarOrderSide.BUY
                != buyFunding.isPresent()
                || side == BazaarOrderSide.SELL
                != sellCustody.isPresent()) {
            throw new IllegalArgumentException(
                    "Bazaar order backing shape is invalid");
        }
        if (buyFunding.isPresent()
                && !buyFunding.orElseThrow().orderId().equals(orderId)) {
            throw new IllegalArgumentException(
                    "Bazaar buy backing order is invalid");
        }
        if (sellCustody.isPresent()
                && (!sellCustody.orElseThrow().custody().orderId()
                .equals(orderId)
                || sellCustody.orElseThrow().remainingQuantity() <= 0)) {
            throw new IllegalArgumentException(
                    "Bazaar sell backing order is invalid");
        }
    }

    public static BazaarEscrowOrderBacking buy(
            BazaarBuyFundingEvidence funding
    ) {
        return new BazaarEscrowOrderBacking(funding.orderId(),
                BazaarOrderSide.BUY, Optional.of(funding),
                Optional.empty());
    }

    public static BazaarEscrowOrderBacking sell(
            BazaarSellCustodyState custody
    ) {
        return new BazaarEscrowOrderBacking(custody.custody().orderId(),
                BazaarOrderSide.SELL, Optional.empty(),
                Optional.of(custody));
    }

    public void requireMatches(BazaarOrder order) {
        Objects.requireNonNull(order, "order");
        if (!orderId.equals(order.orderId()) || side != order.side()
                || order.state().terminal()
                || side == BazaarOrderSide.BUY
                && (!order.moneyHoldAccountId().filter(value ->
                value.equals(buyFunding.orElseThrow().holdAccountId()))
                .isPresent() || order.reservedMoneyMinor() <= 0L)
                || side == BazaarOrderSide.SELL
                && (!order.custodyLotId().filter(value -> value.equals(
                sellCustody.orElseThrow().custody().custodyLotId()))
                .isPresent() || order.reservedItemQuantity()
                != sellCustody.orElseThrow().remainingQuantity())) {
            throw new IllegalArgumentException(
                    "Bazaar order backing differs from the order");
        }
    }

    void requireMatchesView(BazaarEscrowOrderView order) {
        Objects.requireNonNull(order, "order");
        if (!orderId.equals(order.orderId()) || side != order.side()
                || order.state().terminal()
                || side == BazaarOrderSide.BUY
                && (!order.holdAccountId().filter(value -> value.equals(
                buyFunding.orElseThrow().holdAccountId())).isPresent()
                || order.reservedMoneyMinor() <= 0L)
                || side == BazaarOrderSide.SELL
                && (!order.custodyLotId().filter(value -> value.equals(
                sellCustody.orElseThrow().custody().custodyLotId()))
                .isPresent() || order.reservedItemQuantity()
                != sellCustody.orElseThrow().remainingQuantity())) {
            throw new IllegalArgumentException(
                    "Bazaar order backing differs from the order view");
        }
    }
}
