package com.enviouse.futureshops.server.market.bazaar;

import com.enviouse.futureshops.config.BazaarConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BazaarProductCatalogRegistryTest {
    @TempDir
    Path directory;

    @Test
    void rejectedReloadKeepsLastValidSnapshot() throws Exception {
        Path file = directory.resolve("iron.json");
        Files.writeString(file, """
                {"id":"iron","version":1,"item":"minecraft:iron_ingot"}
                """);
        BazaarProductCatalogRegistry registry =
                new BazaarProductCatalogRegistry();

        BazaarProductCatalogRegistry.ReloadResult accepted = registry.reload(
                directory, new BazaarConfig.ProductDefaults(1, 1L),
                1000, value -> true);
        String trusted = accepted.snapshot().fingerprint();
        Files.writeString(file, "{not json");
        BazaarProductCatalogRegistry.ReloadResult rejected = registry.reload(
                directory, new BazaarConfig.ProductDefaults(1, 1L),
                1000, value -> true);

        assertTrue(accepted.accepted());
        assertTrue(accepted.changed());
        assertFalse(rejected.accepted());
        assertFalse(rejected.changed());
        assertEquals(trusted, rejected.snapshot().fingerprint());
        assertEquals(trusted, registry.current().fingerprint());
        assertTrue(registry.lastRejection().isPresent());
    }

    @Test
    void identicalReloadIsAcceptedWithoutChange() throws Exception {
        Files.writeString(directory.resolve("iron.json"), """
                {"id":"iron","version":1,"item":"minecraft:iron_ingot"}
                """);
        BazaarProductCatalogRegistry registry =
                new BazaarProductCatalogRegistry();
        registry.reload(directory,
                new BazaarConfig.ProductDefaults(1, 1L), 1000,
                value -> true);

        BazaarProductCatalogRegistry.ReloadResult second = registry.reload(
                directory, new BazaarConfig.ProductDefaults(1, 1L),
                1000, value -> true);

        assertTrue(second.accepted());
        assertFalse(second.changed());
        assertTrue(second.rejection().isEmpty());
    }
}
