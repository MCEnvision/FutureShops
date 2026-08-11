package com.enviouse.futureshops.server.market.bazaar;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BazaarFeeMathTest {
    @Test
    void fragmentedFeesEqualOneCumulativeFill() {
        BazaarOrder order = buyOrder(10_000L, 30, 37);
        BazaarOrder.FillApplication first = order.applyFill(9_000L, 7, true);
        BazaarOrder.FillApplication second = first.order().applyFill(9_000L, 11, true);
        BazaarOrder.FillApplication third = second.order().applyFill(9_000L, 12, true);

        assertEquals(BazaarFeeMath.cumulativeFee(270_000L, 37),
                third.order().accruedFeeMinor());
        assertEquals(0, third.order().remainingQuantity());
        assertEquals(BazaarOrderState.FILLED, third.order().state());
    }

    @Test
    void feeMathRejectsInvalidAndOverflowingValues() {
        assertThrows(IllegalArgumentException.class,
                () -> BazaarFeeMath.cumulativeFee(-1L, 1));
        assertThrows(IllegalArgumentException.class,
                () -> BazaarFeeMath.cumulativeFee(1L, 10_001));
        assertEquals(Long.MAX_VALUE,
                BazaarFeeMath.cumulativeFee(Long.MAX_VALUE, 10_000));
    }

    private static BazaarOrder buyOrder(long limitPrice, int quantity, int makerFee) {
        BazaarRuleSnapshot rules = new BazaarRuleSnapshot(makerFee, 50, 1000,
                1_000_000_000L, 32, 8, 10_000_000_000L,
                BazaarSelfTradePolicy.CANCEL_TAKER,
                BazaarExecutionPricePolicy.MAKER, false, 5000, 0L, 1L);
        long notional = Math.multiplyExact(limitPrice, quantity);
        long reserve = Math.addExact(notional,
                BazaarFeeMath.cumulativeFee(notional, 50));
        return new BazaarOrder(id(1), id(2), id(3), Optional.of(id(4)),
                Optional.empty(), "iron", 1L, BazaarOrderSide.BUY,
                BazaarOrderType.LIMIT, BazaarTimeInForce.GOOD_UNTIL_CANCELLED,
                BazaarOrderState.OPEN, 0L, limitPrice, quantity, quantity, 0,
                0L, 0L, 0L, reserve, 0, 1L, 0L, 0L, rules);
    }

    private static UUID id(long value) {
        return new UUID(0L, value);
    }
}
