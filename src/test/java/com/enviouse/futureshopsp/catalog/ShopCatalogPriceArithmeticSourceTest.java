package com.enviouse.futureshopsp.catalog;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopCatalogPriceArithmeticSourceTest {
    @Test
    void catalogLineCostsUseCheckedMultiplication() throws Exception {
        String source = Files.readString(projectDirectory().resolve(Path.of(
                "src", "main", "java", "com", "enviouse", "futureshopsp",
                "catalog", "ShopCatalog.java")));

        assertTrue(source.contains("return unit <= 0L ? 0L : checkedMultiply(unit, quantity);"));
        assertTrue(source.contains("return checkedMultiply(basePrice, payable);"));
        assertTrue(source.contains("return checkedMultiply(discounted, quantity);"));
        assertFalse(source.contains("return unit <= 0L ? 0L : unit * quantity;"));
        assertFalse(source.contains("return basePrice * payable;"));
        assertFalse(source.contains("return discounted * quantity;"));
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
