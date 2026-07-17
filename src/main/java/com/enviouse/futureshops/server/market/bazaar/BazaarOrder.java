package com.enviouse.futureshops.server.market.bazaar;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record BazaarOrder(
        UUID orderId,
        UUID ownerId,
        UUID activationTransactionId,
        Optional<UUID> moneyHoldAccountId,
        Optional<UUID> custodyLotId,
        String productId,
        long productVersion,
        BazaarOrderSide side,
        BazaarOrderType type,
        BazaarTimeInForce timeInForce,
        BazaarOrderState state,
        long revision,
        long limitPriceMinor,
        int originalQuantity,
        int remainingQuantity,
        int filledQuantity,
        long makerGrossMinor,
        long takerGrossMinor,
        long accruedFeeMinor,
        long reservedMoneyMinor,
        int reservedItemQuantity,
        long acceptedSequence,
        long createdAtMillis,
        long expiresAtMillis,
        BazaarRuleSnapshot rules
) {
    private static final UUID ZERO = new UUID(0L, 0L);

    public BazaarOrder {
        requireId(orderId, "order");
        requireId(ownerId, "owner");
        requireId(activationTransactionId, "activation transaction");
        moneyHoldAccountId = Objects.requireNonNull(moneyHoldAccountId, "moneyHoldAccountId");
        custodyLotId = Objects.requireNonNull(custodyLotId, "custodyLotId");
        moneyHoldAccountId.ifPresent(id -> requireId(id, "money hold account"));
        custodyLotId.ifPresent(id -> requireId(id, "custody lot"));
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(timeInForce, "timeInForce");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(rules, "rules");
        if (productId.isBlank() || productId.length() > 96 || productVersion <= 0L
                || revision < 0L || revision == Long.MAX_VALUE
                || limitPriceMinor <= 0L || originalQuantity <= 0
                || remainingQuantity < 0 || filledQuantity < 0
                || Math.addExact(remainingQuantity, filledQuantity) != originalQuantity
                || makerGrossMinor < 0L || takerGrossMinor < 0L || accruedFeeMinor < 0L
                || reservedMoneyMinor < 0L || reservedItemQuantity < 0
                || acceptedSequence <= 0L
                || acceptedSequence == Long.MAX_VALUE
                || createdAtMillis < 0L
                || expiresAtMillis < 0L || expiresAtMillis > 0L && expiresAtMillis <= createdAtMillis) {
            throw new IllegalArgumentException("Bazaar order is invalid");
        }
        if ((side == BazaarOrderSide.BUY) != moneyHoldAccountId.isPresent()
                || (side == BazaarOrderSide.SELL) != custodyLotId.isPresent()) {
            throw new IllegalArgumentException("Bazaar order escrow identity is invalid");
        }
        if (side == BazaarOrderSide.BUY && reservedItemQuantity != 0
                || side == BazaarOrderSide.SELL && reservedMoneyMinor != 0L
                || side == BazaarOrderSide.SELL && !state.terminal()
                && reservedItemQuantity != remainingQuantity) {
            throw new IllegalArgumentException("Bazaar order reserve is invalid");
        }
        if (state == BazaarOrderState.FILLED && remainingQuantity != 0
                || remainingQuantity == 0 && state != BazaarOrderState.FILLED
                && state != BazaarOrderState.SETTLED
                || state.terminal() && reservedMoneyMinor != 0L
                || state.terminal() && reservedItemQuantity != 0) {
            throw new IllegalArgumentException("Bazaar terminal order is invalid");
        }
    }

    public boolean matchable() {
        return (state == BazaarOrderState.OPEN || state == BazaarOrderState.PARTIALLY_FILLED)
                && remainingQuantity > 0;
    }

    public boolean expiredAt(long nowMillis) {
        return expiresAtMillis > 0L && nowMillis >= expiresAtMillis;
    }

    public long totalGrossMinor() {
        return Math.addExact(makerGrossMinor, takerGrossMinor);
    }

    public long maximumFeeReserveMinor() {
        long maximumGross = Math.multiplyExact(limitPriceMinor, originalQuantity);
        int maximumRate = Math.max(rules.makerFeeBasisPoints(), rules.takerFeeBasisPoints());
        return BazaarFeeMath.cumulativeFee(maximumGross, maximumRate);
    }

    public long requiredRemainingMoneyReserve() {
        if (side != BazaarOrderSide.BUY || remainingQuantity == 0) {
            return 0L;
        }
        long remainingNotional = Math.multiplyExact(limitPriceMinor, remainingQuantity);
        long remainingFee = Math.subtractExact(maximumFeeReserveMinor(), accruedFeeMinor);
        return Math.addExact(remainingNotional, remainingFee);
    }

    public FillApplication applyFill(long executionPriceMinor, int quantity, boolean maker) {
        if (!matchable() || quantity <= 0 || quantity > remainingQuantity) {
            throw new IllegalArgumentException("Bazaar fill quantity is invalid");
        }
        long gross = Math.multiplyExact(executionPriceMinor, quantity);
        long priorRoleGross = maker ? makerGrossMinor : takerGrossMinor;
        int feeRate = maker ? rules.makerFeeBasisPoints() : rules.takerFeeBasisPoints();
        long roleFeeBefore = BazaarFeeMath.cumulativeFee(priorRoleGross, feeRate);
        long fee = BazaarFeeMath.incrementalFee(priorRoleGross, roleFeeBefore, gross, feeRate);
        int remaining = Math.subtractExact(remainingQuantity, quantity);
        int filled = Math.addExact(filledQuantity, quantity);
        long nextMakerGross = maker ? Math.addExact(makerGrossMinor, gross) : makerGrossMinor;
        long nextTakerGross = maker ? takerGrossMinor : Math.addExact(takerGrossMinor, gross);
        long nextAccruedFee = Math.addExact(accruedFeeMinor, fee);
        BazaarOrderState nextState = remaining == 0
                ? BazaarOrderState.FILLED : BazaarOrderState.PARTIALLY_FILLED;
        long nextMoneyReserve = reservedMoneyMinor;
        int nextItemReserve = reservedItemQuantity;
        if (side == BazaarOrderSide.BUY) {
            long maximumGross = Math.multiplyExact(limitPriceMinor, originalQuantity);
            int maximumRate = Math.max(rules.makerFeeBasisPoints(), rules.takerFeeBasisPoints());
            long maximumFee = BazaarFeeMath.cumulativeFee(maximumGross, maximumRate);
            long required = remaining == 0 ? 0L : Math.addExact(
                    Math.multiplyExact(limitPriceMinor, remaining),
                    Math.subtractExact(maximumFee, nextAccruedFee));
            long consumed = Math.addExact(gross, fee);
            long availableAfterConsumption = Math.subtractExact(reservedMoneyMinor, consumed);
            if (availableAfterConsumption < required) {
                throw new IllegalStateException("Bazaar buyer reserve is insufficient");
            }
            nextMoneyReserve = required;
        } else {
            nextItemReserve = remaining;
        }
        BazaarOrder next = new BazaarOrder(orderId, ownerId, activationTransactionId,
                moneyHoldAccountId, custodyLotId, productId, productVersion, side, type,
                timeInForce, nextState, Math.addExact(revision, 1L), limitPriceMinor,
                originalQuantity, remaining, filled, nextMakerGross, nextTakerGross,
                nextAccruedFee, nextMoneyReserve, nextItemReserve, acceptedSequence,
                createdAtMillis, expiresAtMillis, rules);
        long release = side == BazaarOrderSide.BUY
                ? Math.subtractExact(Math.subtractExact(reservedMoneyMinor, Math.addExact(gross, fee)), nextMoneyReserve)
                : 0L;
        return new FillApplication(next, gross, fee, release);
    }

    public TerminalApplication cancel(BazaarOrderState terminalState) {
        if (terminalState != BazaarOrderState.CANCELLED && terminalState != BazaarOrderState.EXPIRED) {
            throw new IllegalArgumentException("Bazaar terminal state is invalid");
        }
        if (!matchable() && state != BazaarOrderState.FROZEN) {
            throw new IllegalStateException("Bazaar order cannot be terminated");
        }
        long moneyRefund = reservedMoneyMinor;
        int itemRefund = reservedItemQuantity;
        BazaarOrder next = new BazaarOrder(orderId, ownerId, activationTransactionId,
                moneyHoldAccountId, custodyLotId, productId, productVersion, side, type,
                timeInForce, terminalState, Math.addExact(revision, 1L), limitPriceMinor,
                originalQuantity, remainingQuantity, filledQuantity, makerGrossMinor,
                takerGrossMinor, accruedFeeMinor, 0L, 0, acceptedSequence,
                createdAtMillis, expiresAtMillis, rules);
        return new TerminalApplication(next, moneyRefund, itemRefund);
    }

    public BazaarOrder freeze() {
        if (!matchable()) {
            throw new IllegalStateException("Bazaar order cannot be frozen");
        }
        return copyState(BazaarOrderState.FROZEN);
    }

    public BazaarOrder resume() {
        if (state != BazaarOrderState.FROZEN) {
            throw new IllegalStateException("Bazaar order is not frozen");
        }
        return copyState(filledQuantity == 0 ? BazaarOrderState.OPEN : BazaarOrderState.PARTIALLY_FILLED);
    }

    private BazaarOrder copyState(BazaarOrderState nextState) {
        return new BazaarOrder(orderId, ownerId, activationTransactionId,
                moneyHoldAccountId, custodyLotId, productId, productVersion, side, type,
                timeInForce, nextState, Math.addExact(revision, 1L), limitPriceMinor,
                originalQuantity, remainingQuantity, filledQuantity, makerGrossMinor,
                takerGrossMinor, accruedFeeMinor, reservedMoneyMinor, reservedItemQuantity,
                acceptedSequence, createdAtMillis, expiresAtMillis, rules);
    }

    private static void requireId(UUID id, String label) {
        if (id == null || ZERO.equals(id)) {
            throw new IllegalArgumentException("Bazaar " + label + " identifier is required");
        }
    }

    public record FillApplication(BazaarOrder order, long grossMinor,
                                  long feeMinor, long moneyReserveReleaseMinor) {
        public FillApplication {
            Objects.requireNonNull(order, "order");
            if (grossMinor <= 0L || feeMinor < 0L || moneyReserveReleaseMinor < 0L) {
                throw new IllegalArgumentException("Bazaar fill application is invalid");
            }
        }
    }

    public record TerminalApplication(BazaarOrder order, long moneyRefundMinor,
                                      int itemRefundQuantity) {
        public TerminalApplication {
            Objects.requireNonNull(order, "order");
            if (moneyRefundMinor < 0L || itemRefundQuantity < 0) {
                throw new IllegalArgumentException("Bazaar terminal application is invalid");
            }
        }
    }
}
