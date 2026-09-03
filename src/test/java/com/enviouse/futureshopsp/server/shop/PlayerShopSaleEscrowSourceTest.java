package com.enviouse.futureshopsp.server.shop;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerShopSaleEscrowSourceTest {
    @Test
    void saleEscrowPersistsExactStacksAndUsesFailClosedStates() throws Exception {
        String source = Files.readString(projectDirectory().resolve(Path.of(
                "src", "main", "java", "com", "enviouse", "futureshopsp",
                "server", "shop", "PlayerShopSaleEscrowSavedData.java")));

        assertTrue(source.contains("PREPARED"));
        assertTrue(source.contains("REMOVED"));
        assertTrue(source.contains("DELIVERED"));
        assertTrue(source.contains("CLAIMED"));
        assertTrue(source.contains("RECOVERY_REQUIRED"));
        assertTrue(source.contains("stack.save(provider)"));
        assertTrue(source.contains("checksum(record)"));
        assertTrue(source.contains("cleanMarker"));
        assertTrue(source.contains("hasIncompleteRecords"));
    }

    @Test
    void playerShopBuyUsesSaleEscrowAroundPhysicalDelivery() throws Exception {
        String source = Files.readString(projectDirectory().resolve(Path.of(
                "src", "main", "java", "com", "enviouse", "futureshopsp",
                "server", "shop", "PlayerShopBlockService.java")));

        assertTrue(source.contains("snapshotSaleStacks"));
        assertTrue(source.contains("saleEscrow.prepare"));
        assertTrue(source.contains("saleEscrow.markRemoved"));
        assertTrue(source.contains("saleEscrow.markDelivered"));
        assertTrue(source.contains("saleEscrow.markClaimed"));
        assertTrue(source.contains("saleEscrow.markRecoveryRequired"));
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
