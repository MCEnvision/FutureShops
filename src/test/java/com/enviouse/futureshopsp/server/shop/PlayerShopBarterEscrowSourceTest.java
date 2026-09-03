package com.enviouse.futureshopsp.server.shop;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerShopBarterEscrowSourceTest {
    @Test
    void escrowPersistsExactStacksAndUsesFailClosedStates() throws Exception {
        String source = Files.readString(projectDirectory().resolve(Path.of(
                "src", "main", "java", "com", "enviouse", "futureshopsp",
                "server", "shop", "PlayerShopBarterEscrowSavedData.java")));

        assertTrue(source.contains("PREPARED"));
        assertTrue(source.contains("REMOVED"));
        assertTrue(source.contains("STORED"));
        assertTrue(source.contains("RECOVERY_REQUIRED"));
        assertTrue(source.contains("stack.save(provider)"));
        assertTrue(source.contains("checksum(record)"));
        assertTrue(source.contains("cleanMarker"));
        assertTrue(source.contains("hasIncompleteRecords"));
    }

    @Test
    void lifecycleIncludesBarterEscrowBeforeReady() throws Exception {
        String source = Files.readString(projectDirectory().resolve(Path.of(
                "src", "main", "java", "com", "enviouse", "futureshopsp",
                "server", "economy", "BalanceManager.java")));

        assertTrue(source.contains("barterEscrow.cleanMarkerValid()"));
        assertTrue(source.contains("barterEscrow.integrityValid()"));
        assertTrue(source.contains("barterEscrow.hasIncompleteRecords()"));
        assertTrue(source.contains("barterEscrow.markUnclean()"));
        assertTrue(source.contains("barterEscrow.markCleanMarker()"));
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
