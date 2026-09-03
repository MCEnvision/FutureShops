package com.enviouse.futureshopsp.server.economy;

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
    void absentSelectionUsesInternalAtStartup() {
        ProviderSelectionSnapshot snapshot = ProviderSelectionManager.resolveAtStartup(null);
        assertTrue(snapshot.resolved());
        assertEquals("internal", snapshot.activeProviderId());
        assertEquals("internal", snapshot.stagedProviderId());
        assertFalse(snapshot.restartRequired());
        assertEquals("", snapshot.diagnostic());
    }

    @Test
    void reloadStagesWithoutChangingActiveProvider() {
        ProviderSelectionManager.resolveAtStartup("internal");
        ProviderSelectionSnapshot staged = ProviderSelectionManager.stageReload("pixelmon");
        assertEquals("internal", staged.activeProviderId());
        assertEquals("pixelmon", staged.stagedProviderId());
        assertTrue(staged.restartRequired());

        ProviderSelectionSnapshot restarted = ProviderSelectionManager.resolveAtStartup("pixelmon");
        assertEquals("pixelmon", restarted.activeProviderId());
        assertEquals("pixelmon", restarted.stagedProviderId());
        assertFalse(restarted.restartRequired());
    }

    @Test
    void invalidAndUnknownSelectionsDoNotFallback() {
        ProviderSelectionSnapshot invalid = ProviderSelectionManager.resolveAtStartup("Pixelmon");
        assertEquals("Pixelmon", invalid.activeProviderId());
        assertEquals("configured provider identifier is invalid", invalid.diagnostic());

        ProviderSelectionSnapshot unknown = ProviderSelectionManager.stageReload("missing_provider");
        assertEquals("missing_provider", unknown.stagedProviderId());
        assertTrue(unknown.restartRequired());
    }
}
