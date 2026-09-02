package com.enviouse.futureshops.catalog;

import com.enviouse.futureshops.MinecraftTestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.IntStream;

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

    @Test
    void preparationRejectsSymlinkedFutureShopsDirectory(
            @TempDir Path configDirectory
    ) throws Exception {
        Path outside = configDirectory.resolve("outside");
        Files.createDirectories(outside);
        Path redirected = configDirectory.resolve("futureshops");
        try {
            Files.createSymbolicLink(redirected, outside.getFileName());
        } catch (java.io.IOException | UnsupportedOperationException exception) {
            Assumptions.assumeTrue(false, "symbolic links are unavailable");
            return;
        }

        assertFalse(ShopDefinitionLoader.prepareStorage(configDirectory));
        assertFalse(Files.exists(outside.resolve("shops/admin.json")));
    }

    @Test
    void preparationRejectsSymlinkedShopsDirectory(
            @TempDir Path configDirectory
    ) throws Exception {
        Path futureshops = configDirectory.resolve("futureshops");
        Path outside = configDirectory.resolve("outside");
        Files.createDirectories(futureshops);
        Files.createDirectories(outside);
        Path redirected = futureshops.resolve("shops");
        try {
            Files.createSymbolicLink(redirected, outside.getFileName());
        } catch (java.io.IOException | UnsupportedOperationException exception) {
            Assumptions.assumeTrue(false, "symbolic links are unavailable");
            return;
        }

        assertFalse(ShopDefinitionLoader.prepareStorage(configDirectory));
        assertFalse(Files.exists(outside.resolve("admin.json")));
    }

    @Test
    void loaderFallsBackWhenDefinitionFileCountExceedsBound(
            @TempDir Path configDirectory
    ) throws Exception {
        Path shops = configDirectory.resolve("futureshops/shops");
        Files.createDirectories(shops);
        IntStream.range(0, 257).forEach(index -> {
            try {
                Files.writeString(shops.resolve("shop-" + index + ".json"), "{}");
            } catch (java.io.IOException exception) {
                throw new java.io.UncheckedIOException(exception);
            }
        });

        assertEquals(1, ShopDefinitionLoader.loadAll(configDirectory).size());
    }

    @Test
    void parserRejectsOversizedLegacyArraysAndNbt() {
        StringBuilder items = new StringBuilder("[");
        for (int index = 0; index < 10_001; index++) {
            if (index > 0) items.append(',');
            items.append("{\"itemId\":\"minecraft:stone\"}");
        }
        items.append(']');

        String oversizedItems = "{\"schemaVersion\":1,\"items\":"
                + items + "}";
        assertFalse(ShopDefinitionLoader.validCandidate(oversizedItems,
                "oversized.json"));

        String oversizedNbt = "{\"schemaVersion\":1,\"items\":[{"
                + "\"itemId\":\"minecraft:stone\",\"nbt\":\""
                + "x".repeat(65_537) + "\"}]}";
        assertFalse(ShopDefinitionLoader.validCandidate(oversizedNbt,
                "oversized-nbt.json"));
    }
}
