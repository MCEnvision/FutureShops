package com.enviouse.futureshopsp.server.transaction;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopSellInputSafetySourceTest {
    @Test
    void malformedCatalogNbtIsRejectedBeforeInventoryRemoval() throws Exception {
        String source = Files.readString(projectDirectory().resolve(Path.of(
                "src", "main", "java", "com", "enviouse", "futureshopsp",
                "server", "transaction", "ShopSellService.java")));

        int parse = source.indexOf("NbtMatchUtil.snbtToPatchMigrating");
        int removal = source.indexOf("ShopTransactionUtil.removeItems");
        assertTrue(parse >= 0);
        assertTrue(source.contains("Invalid SNBT for sell listing"));
        assertTrue(source.contains("ShopResultCode.INVALID_ITEM"));
        assertTrue(parse < removal);
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
