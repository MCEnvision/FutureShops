package com.enviouse.futureshops.server.shop;

import com.enviouse.futureshops.server.transaction.ShopTransactionUtil;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerShopBuyMathTest {
    @Test
    void quantityUsesOneSharedInclusiveBound() {
        assertEquals(1, PlayerShopBuyMath.requireQuantity(1));
        assertEquals(ShopTransactionUtil.MAX_BUY_QUANTITY,
                PlayerShopBuyMath.requireQuantity(ShopTransactionUtil.MAX_BUY_QUANTITY));
        assertThrows(IllegalArgumentException.class, () -> PlayerShopBuyMath.requireQuantity(0));
        assertThrows(IllegalArgumentException.class, () -> PlayerShopBuyMath.requireQuantity(-1));
        assertThrows(IllegalArgumentException.class,
                () -> PlayerShopBuyMath.requireQuantity(ShopTransactionUtil.MAX_BUY_QUANTITY + 1));
        assertThrows(IllegalArgumentException.class,
                () -> PlayerShopBuyMath.requireQuantity(Integer.MIN_VALUE));
        assertThrows(IllegalArgumentException.class,
                () -> PlayerShopBuyMath.requireQuantity(Integer.MAX_VALUE));
    }

    @Test
    void itemAndBarterProductsAreExact() {
        assertEquals(147_456, PlayerShopBuyMath.checkedItemTotal(
                64, ShopTransactionUtil.MAX_BUY_QUANTITY));
        assertEquals(0, PlayerShopBuyMath.checkedBarterTotal(
                0, ShopTransactionUtil.MAX_BUY_QUANTITY));
        assertThrows(ArithmeticException.class,
                () -> PlayerShopBuyMath.checkedItemTotal(Integer.MAX_VALUE, 2));
        assertThrows(ArithmeticException.class,
                () -> PlayerShopBuyMath.checkedBarterTotal(Integer.MAX_VALUE, 2));
        assertThrows(IllegalArgumentException.class,
                () -> PlayerShopBuyMath.checkedItemTotal(0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> PlayerShopBuyMath.checkedBarterTotal(-1, 1));
    }

    @Test
    void stackAndDeliveryTotalsHaveResourceBounds() {
        assertEquals(PlayerShopBuyMath.MAX_DERIVED_STACKS,
                PlayerShopBuyMath.checkedStackCount(147_456, 64));
        assertThrows(ArithmeticException.class,
                () -> PlayerShopBuyMath.checkedStackCount(147_457, 64));
        assertEquals(Integer.MAX_VALUE,
                PlayerShopBuyMath.checkedAggregateItemCount(Integer.MAX_VALUE - 1, 1));
        assertThrows(ArithmeticException.class,
                () -> PlayerShopBuyMath.checkedAggregateItemCount(Integer.MAX_VALUE, 1));
        assertEquals(PlayerShopBuyMath.MAX_DERIVED_STACKS,
                PlayerShopBuyMath.checkedAggregateStackCount(
                        PlayerShopBuyMath.MAX_DERIVED_STACKS - 1, 1));
        assertThrows(ArithmeticException.class,
                () -> PlayerShopBuyMath.checkedAggregateStackCount(
                        PlayerShopBuyMath.MAX_DERIVED_STACKS, 1));
    }

    @Test
    void ordinaryAndPromotionPricesAreExact() {
        assertEquals(23_040L, PlayerShopBuyMath.checkedPriceTotal(
                10L, 10L, ShopTransactionUtil.MAX_BUY_QUANTITY,
                false, "", 0, 0));
        assertEquals(40L, PlayerShopBuyMath.checkedPriceTotal(
                10L, 10L, 5, true, "BUY_X_GET_Y", 2, 1));
        assertEquals(5L, PlayerShopBuyMath.checkedPriceTotal(
                1L, 1L, 5, true, "BUY_X_GET_Y", Integer.MAX_VALUE, Integer.MAX_VALUE));
        long overflowingUnit = Long.MAX_VALUE / ShopTransactionUtil.MAX_BUY_QUANTITY + 1L;
        assertThrows(ArithmeticException.class,
                () -> PlayerShopBuyMath.checkedPriceTotal(
                        overflowingUnit, overflowingUnit, ShopTransactionUtil.MAX_BUY_QUANTITY,
                        false, "", 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> PlayerShopBuyMath.checkedPriceTotal(-1L, -1L, 1, false, "", 0, 0));
    }

    @Test
    void settlementAdditionFailsWithoutChangingStoredTotals() {
        UUID owner = UUID.randomUUID();
        PlayerShopSettlementSavedData data = new PlayerShopSettlementSavedData();
        assertTrue(data.canRecordSale(owner, 12L, Long.MAX_VALUE));
        assertTrue(data.recordSale(owner, 12L, Long.MAX_VALUE, "minecraft:stone", 1));
        assertFalse(data.canRecordSale(owner, 12L, 1L));
        assertFalse(data.recordSale(owner, 12L, 1L, "minecraft:stone", 1));
        assertFalse(data.recordSale(owner, 12L, -1L, "minecraft:stone", 1));
        PlayerShopSettlementSavedData.Snapshot snapshot = data.snapshot(owner, 12L, 1);
        assertEquals(Long.MAX_VALUE, snapshot.pendingMinor());
        assertEquals(Long.MAX_VALUE, snapshot.lifetimeMinor());
    }
}
