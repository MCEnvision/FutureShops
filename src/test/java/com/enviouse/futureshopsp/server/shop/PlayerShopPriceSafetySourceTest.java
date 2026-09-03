package com.enviouse.futureshopsp.server.shop;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerShopPriceSafetySourceTest {
    @Test
    void priceIncreaseRejectsCheckedArithmeticOverflow() throws Exception {
        String source = Files.readString(projectDirectory().resolve(Path.of(
                "src", "main", "java", "com", "enviouse", "futureshopsp",
                "server", "shop", "PlayerShopBlockService.java")));

        int action = source.indexOf("case \"PRICE_UP\"");
        int checkedAdd = source.indexOf("Math.addExact(listing.moneyPriceMinor(), Math.max(1, amount))", action);
        int overflowResult = source.indexOf("ShopResultCode.MAX_BALANCE_EXCEEDED", action);
        assertTrue(action >= 0);
        assertTrue(checkedAdd > action);
        assertTrue(overflowResult > checkedAdd);
        assertTrue(source.indexOf("catch (ArithmeticException", checkedAdd) > checkedAdd);
    }

    private static Path projectDirectory() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null) {
            if (Files.isDirectory(candidate.resolve(Path.of("src", "main", "java")))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("FutureShops source directory is unavailable");
    }
}
