package com.enviouse.futureshopsp.catalog;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ShopDefinitionLoaderStorageTest {
    @Test
    void loaderFallsBackWhenDefinitionFileCountExceedsBound(@TempDir Path configDirectory) throws Exception {
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
    void preparationRejectsSymlinkedFutureShopsDirectory(@TempDir Path configDirectory) throws Exception {
        Path outside = configDirectory.resolve("outside");
        Files.createDirectories(outside);
        Path redirected = configDirectory.resolve("futureshops");
        try {
            Files.createSymbolicLink(redirected, outside.getFileName());
        } catch (java.io.IOException | UnsupportedOperationException exception) {
            Assumptions.assumeTrue(false, "symbolic links are unavailable");
            return;
        }

        assertEquals(1, ShopDefinitionLoader.loadAll(configDirectory).size());
        assertFalse(Files.exists(outside.resolve("shops/admin.json")));
    }
}
