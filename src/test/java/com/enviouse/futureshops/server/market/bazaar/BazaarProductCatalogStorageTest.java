package com.enviouse.futureshops.server.market.bazaar;

import com.enviouse.futureshops.config.BazaarConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BazaarProductCatalogStorageTest {
    private static final BazaarConfig.ProductDefaults DEFAULTS =
            new BazaarConfig.ProductDefaults(1, 1L);

    @TempDir
    Path directory;

    @Test
    void emptyAdminCatalogGetsAValidStarterFile() throws Exception {
        assertTrue(BazaarProductCatalogStorage.prepare(directory, true));

        Path starter = directory.resolve(
                BazaarProductCatalogStorage.DEFAULT_FILE_NAME);
        assertTrue(Files.isRegularFile(starter));
        BazaarProductCatalogSnapshot snapshot =
                BazaarProductDefinitionLoader.load(
                        directory, DEFAULTS, 1000,
                        value -> value.startsWith("minecraft:"));
        assertFalse(snapshot.definitions().isEmpty());
        assertTrue(snapshot.definitions().stream().anyMatch(
                definition -> definition.product().productId()
                        .equals("iron_ingot")));
    }

    @Test
    void existingAdminCatalogIsNeverOverwrittenOrMixedWithDefaults()
            throws Exception {
        Path custom = directory.resolve("custom.json");
        String original = """
                {
                  "schema": 1,
                  "id": "custom_iron",
                  "version": 1,
                  "item": "minecraft:iron_ingot"
                }
                """;
        Files.writeString(custom, original);

        assertFalse(BazaarProductCatalogStorage.prepare(directory, true));
        assertEquals(original, Files.readString(custom));
        assertFalse(Files.exists(directory.resolve(
                BazaarProductCatalogStorage.DEFAULT_FILE_NAME)));
    }

    @Test
    void playerModeCreatesStorageWithoutSeedingAdminProducts()
            throws Exception {
        assertFalse(BazaarProductCatalogStorage.prepare(directory, false));

        assertTrue(Files.isDirectory(directory));
        assertFalse(Files.exists(directory.resolve(
                BazaarProductCatalogStorage.DEFAULT_FILE_NAME)));
    }

    @Test
    void starterGenerationIsIdempotent() throws Exception {
        assertTrue(BazaarProductCatalogStorage.prepare(directory, true));
        String first = Files.readString(directory.resolve(
                BazaarProductCatalogStorage.DEFAULT_FILE_NAME));

        assertFalse(BazaarProductCatalogStorage.prepare(directory, true));
        assertEquals(first, Files.readString(directory.resolve(
                BazaarProductCatalogStorage.DEFAULT_FILE_NAME)));
    }
}
