package com.enviouse.futureshops.server.escrow.runtime;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerShopOfferMoneyQuoteTest {
    @Test
    void sellQuoteMultipliesConfiguredUnitPayoutByQuantity() {
        assertEquals(600L,
                ServerShopOfferService.sellMoneyTotal(100L, 6));
    }

    @Test
    void sellQuoteFailsClosedOnOverflow() {
        assertThrows(ArithmeticException.class,
                () -> ServerShopOfferService.sellMoneyTotal(
                        Long.MAX_VALUE, 2));
    }

    @Test
    void productionQuoteUsesSellPayoutBeforeAcquireMoneyRules()
            throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/enviouse/futureshops/server/escrow/runtime/ServerShopOfferService.java"));
        int method = source.indexOf("private static long quotedMoneyTotal(");
        int sellAction = source.indexOf(
                "request.action() == OfferAction.SELL_TO_SHOP", method);
        int sellPayout = source.indexOf(
                "option.sell().moneyPayoutMinorUnits()", sellAction);
        int acquireMoneyRule = source.indexOf(
                "if (!option.moneyRequired())", sellPayout);

        assertTrue(method >= 0);
        assertTrue(sellAction > method);
        assertTrue(sellPayout > sellAction);
        assertTrue(acquireMoneyRule > sellPayout);
    }
}
