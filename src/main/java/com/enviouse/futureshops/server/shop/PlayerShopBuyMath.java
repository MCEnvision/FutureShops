package com.enviouse.futureshops.server.shop;

import com.enviouse.futureshops.server.transaction.ShopTransactionUtil;

public final class PlayerShopBuyMath {
    public static final int MAX_DERIVED_STACKS = ShopTransactionUtil.MAX_BUY_QUANTITY;

    private PlayerShopBuyMath() {
    }

    public static int requireQuantity(int quantity) {
        if (!ShopTransactionUtil.isValidBuyQuantity(quantity)) {
            throw new IllegalArgumentException("quantity is outside the buy limit");
        }
        return quantity;
    }

    public static int checkedItemTotal(int perUnit, int quantity) {
        requireQuantity(quantity);
        if (perUnit <= 0) {
            throw new IllegalArgumentException("item count must be positive");
        }
        return Math.multiplyExact(perUnit, quantity);
    }

    public static int checkedBarterTotal(int perUnit, int quantity) {
        requireQuantity(quantity);
        if (perUnit < 0) {
            throw new IllegalArgumentException("barter count must not be negative");
        }
        return Math.multiplyExact(perUnit, quantity);
    }

    public static int checkedStackCount(int itemCount, int maxStackSize) {
        if (itemCount < 0) {
            throw new IllegalArgumentException("item count must not be negative");
        }
        if (maxStackSize <= 0) {
            throw new IllegalArgumentException("stack size must be positive");
        }
        if (itemCount == 0) {
            return 0;
        }
        int stackCount = Math.addExact((itemCount - 1) / maxStackSize, 1);
        if (stackCount > MAX_DERIVED_STACKS) {
            throw new ArithmeticException("stack count exceeds the buy limit");
        }
        return stackCount;
    }

    public static int checkedAggregateItemCount(int current, int addition) {
        if (current < 0 || addition < 0) {
            throw new IllegalArgumentException("item totals must not be negative");
        }
        return Math.addExact(current, addition);
    }

    public static int checkedAggregateStackCount(int current, int addition) {
        if (current < 0 || addition < 0) {
            throw new IllegalArgumentException("stack totals must not be negative");
        }
        int total = Math.addExact(current, addition);
        if (total > MAX_DERIVED_STACKS) {
            throw new ArithmeticException("stack total exceeds the buy limit");
        }
        return total;
    }

    public static long checkedPriceTotal(
            long basePrice,
            long effectiveUnitPrice,
            int quantity,
            boolean promoActive,
            String promoType,
            int buyX,
            int buyY
    ) {
        requireQuantity(quantity);
        if (basePrice < 0L || effectiveUnitPrice < 0L) {
            throw new IllegalArgumentException("price must not be negative");
        }
        if (promoActive && "BUY_X_GET_Y".equals(promoType) && buyX > 0 && buyY > 0) {
            long groupSize = Math.addExact((long) buyX, (long) buyY);
            long fullGroups = quantity / groupSize;
            long remainder = quantity % groupSize;
            long groupUnits = Math.multiplyExact(fullGroups, (long) buyX);
            long payableUnits = Math.addExact(groupUnits, Math.min(remainder, (long) buyX));
            return Math.multiplyExact(basePrice, payableUnits);
        }
        return Math.multiplyExact(effectiveUnitPrice, (long) quantity);
    }

    public static long requireNonnegativePrice(long price) {
        if (price < 0L) {
            throw new IllegalArgumentException("price must not be negative");
        }
        return price;
    }

    public static long checkedSettlementTotal(long current, long addition) {
        if (current < 0L || addition < 0L) {
            throw new IllegalArgumentException("settlement values must not be negative");
        }
        return Math.addExact(current, addition);
    }
}
