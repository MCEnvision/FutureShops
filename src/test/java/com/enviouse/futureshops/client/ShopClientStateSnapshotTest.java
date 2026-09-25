package com.enviouse.futureshops.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShopClientStateSnapshotTest {
    @AfterEach
    void reset() {
        ShopClientState.reset();
    }

    @Test
    void olderSameShopSnapshotCannotOverwriteCurrentState() {
        UUID sessionId = UUID.randomUUID();
        ShopClientState.applyShopData("default", 500L, "Coins", 2,
                List.of(), List.of(), List.of(), List.of(), true, List.of(),
                false, List.of(), "internal", 5L, sessionId);
        ShopClientState.applyShopData("default", 100L, "Coins", 2,
                List.of(), List.of(), List.of(), List.of(), true, List.of(),
                false, List.of(), "internal", 4L, sessionId);

        assertEquals(5L, ShopClientState.getSnapshotRevision());
        assertEquals(sessionId, ShopClientState.getSessionId());
        assertEquals(500L, ShopClientState.getCurrentBalanceMinorUnits());
    }

    @Test
    void newSessionMayAcceptLowerRevision() {
        UUID firstSession = UUID.randomUUID();
        UUID secondSession = UUID.randomUUID();
        ShopClientState.applyShopData("default", 500L, "Coins", 2,
                List.of(), List.of(), List.of(), List.of(), true, List.of(),
                false, List.of(), "internal", 9L, firstSession);
        ShopClientState.applyShopData("default", 100L, "Coins", 2,
                List.of(), List.of(), List.of(), List.of(), true, List.of(),
                false, List.of(), "internal", 1L, secondSession);

        assertEquals(1L, ShopClientState.getSnapshotRevision());
        assertEquals(secondSession, ShopClientState.getSessionId());
        assertEquals(100L, ShopClientState.getCurrentBalanceMinorUnits());
    }
}
