package com.enviouse.futureshops.catalog;

import com.enviouse.futureshops.MinecraftTestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopDefinitionLoaderStorageTest {
    @BeforeAll
    static void initializeMinecraftRegistries() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void preparesEditableAdminCatalogBeforeWorldStart(
            @TempDir Path configDirectory
    ) throws Exception {
        assertTrue(ShopDefinitionLoader.prepareStorage(configDirectory));

        Path admin = configDirectory.resolve(
                "futureshops/shops/admin.json");
        assertTrue(Files.isRegularFile(admin));
        assertTrue(ShopDefinitionLoader.validCandidate(
                Files.readString(admin), "admin.json"));
    }

    @Test
    void preparationNeverReplacesExistingAdminCatalog(
            @TempDir Path configDirectory
    ) throws Exception {
        Path admin = configDirectory.resolve(
                "futureshops/shops/admin.json");
        Files.createDirectories(admin.getParent());
        String custom = "{\"custom\":\"modpack catalog\"}";
        Files.writeString(admin, custom);

        assertTrue(ShopDefinitionLoader.prepareStorage(configDirectory));
        assertEquals(custom, Files.readString(admin));
    }

    @Test
    void preparationMigratesLegacyDefaultCatalog(
            @TempDir Path configDirectory
    ) throws Exception {
        Path shops = configDirectory.resolve("futureshops/shops");
        Files.createDirectories(shops);
        Path legacy = shops.resolve("default.json");
        String custom = "{\"custom\":\"legacy modpack catalog\"}";
        Files.writeString(legacy, custom);

        assertTrue(ShopDefinitionLoader.prepareStorage(configDirectory));
        assertFalse(Files.exists(legacy));
        assertEquals(custom,
                Files.readString(shops.resolve("admin.json")));
    }
}
