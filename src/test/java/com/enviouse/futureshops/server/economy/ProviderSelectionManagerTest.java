package com.enviouse.futureshops.server.economy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderSelectionManagerTest {
    @BeforeEach
    void reset() {
        ProviderSelectionManager.resetForTests();
    }

    @AfterEach
    void cleanup() {
        ProviderSelectionManager.resetForTests();
    }

    @Test
    void absentStartupSelectionUsesInternal() {
        ProviderSelectionSnapshot snapshot =
                ProviderSelectionManager.resolveAtStartup("");
        assertEquals("internal", snapshot.activeProviderId());
        assertEquals("internal", snapshot.stagedProviderId());
        assertTrue(snapshot.resolved());
        assertFalse(snapshot.restartRequired());
        assertEquals("", snapshot.diagnostic());
    }

    @Test
    void reloadStagesWithoutChangingActiveSelection() {
        ProviderSelectionManager.resolveAtStartup("internal");
        ProviderSelectionSnapshot snapshot =
                ProviderSelectionManager.stageReload("fixture");
        assertEquals("internal", snapshot.activeProviderId());
        assertEquals("fixture", snapshot.stagedProviderId());
        assertTrue(snapshot.restartRequired());
        assertEquals("", snapshot.diagnostic());
    }

    @Test
    void invalidSelectionRemainsVisibleAndDoesNotFallback() {
        ProviderSelectionSnapshot snapshot =
                ProviderSelectionManager.resolveAtStartup("Not Valid");
        assertEquals("not valid", snapshot.activeProviderId());
        assertEquals("configured provider identifier is invalid", snapshot.diagnostic());
    }
}
