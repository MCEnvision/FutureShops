package com.enviouse.futureshopsp.server.shop;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketplaceAnalyticsArithmeticSourceTest {
    @Test
    void dashboardAndProductAggregatesUseCheckedWideArithmetic() throws Exception {
        String source = Files.readString(projectDirectory().resolve(
                "src/main/java/com/enviouse/futureshopsp/server/shop/MarketplaceAnalyticsService.java"));

        assertTrue(source.contains("revenue = Math.addExact(revenue, settlement.lifetimeMinor());"));
        assertTrue(source.contains("pending = Math.addExact(pending, settlement.pendingMinor());"));
        assertTrue(source.contains("totalStock = Math.addExact(totalStock, shopTotalStock);"));
        assertTrue(source.contains("Map<String, long[]> productTotals"));
        assertTrue(source.contains("totals[1] = Math.addExact(totals[1], Math.max(1L, entry.quantity()));"));
        assertTrue(source.contains("topItemTradeCount = Math.toIntExact(totals[0]);"));
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
