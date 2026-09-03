package com.enviouse.futureshopsp.server.transaction;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionEventPriceSafetySourceTest {
    @Test
    void eventPriceOverridesAreValidatedBeforeMoneyMutation() throws Exception {
        String sellSource = Files.readString(projectDirectory().resolve(Path.of(
                "src", "main", "java", "com", "enviouse", "futureshopsp",
                "server", "transaction", "ShopSellService.java")));
        String playerShopSource = Files.readString(projectDirectory().resolve(Path.of(
                "src", "main", "java", "com", "enviouse", "futureshopsp",
                "server", "shop", "PlayerShopBlockService.java")));

        assertTrue(sellSource.contains("totalValue = preEvent.getPriceMinor();\n            if (totalValue <= 0L)"));
        assertTrue(playerShopSource.contains("cost = preEvent.getPriceMinor();\n        }\n        if (cost < 0L)"));
        assertTrue(playerShopSource.contains("total = pre.getPriceMinor();\n            }\n            if (total <= 0L)"));
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
