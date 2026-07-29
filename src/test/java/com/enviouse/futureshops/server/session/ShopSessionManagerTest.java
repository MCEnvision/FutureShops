package com.enviouse.futureshops.server.session;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopSessionManagerTest {
    @AfterEach
    void clearSessions() {
        ShopSessionManager.clear();
    }

    @Test
    void failedInitialSyncRollsBackTheNewSession() {
        UUID playerId = UUID.randomUUID();

        assertThrows(IllegalStateException.class, () ->
                ShopSessionManager.openWithRollback(
                        playerId, "default", () -> {
                            throw new IllegalStateException("sync failed");
                        }));

        assertFalse(ShopSessionManager.get(playerId).isPresent());
    }

    @Test
    void successfulInitialSyncKeepsTheNewSession() {
        UUID playerId = UUID.randomUUID();

        ShopSessionManager.openWithRollback(
                playerId, "default", () -> {
                });

        assertTrue(ShopSessionManager.get(playerId).isPresent());
    }
}
