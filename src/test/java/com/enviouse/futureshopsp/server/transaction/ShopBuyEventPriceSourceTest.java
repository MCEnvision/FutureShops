package com.enviouse.futureshopsp.server.transaction;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopBuyEventPriceSourceTest {
    @Test
    void buyEventPriceIsValidatedAndIncludedInTheDebitTotal() throws Exception {
        String source = Files.readString(projectDirectory().resolve(Path.of(
                "src", "main", "java", "com", "enviouse", "futureshopsp",
                "server", "transaction", "ShopBuyService.java")));

        assertTrue(source.contains("long adjustedCost = preEvent.getPriceMinor();"));
        assertTrue(source.contains("if (adjustedCost <= 0L)"));
        assertTrue(source.contains("adjustedTotalCost = Math.addExact(adjustedTotalCost, adjustedCost);"));
        assertTrue(source.contains("totalCost = adjustedTotalCost;"));
        assertTrue(source.indexOf("totalCost = adjustedTotalCost;") < source.indexOf("coordinator.preflight(debitRequest)"));
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
