package com.enviouse.futureshops.config;

import com.enviouse.futureshops.ClientConfig;
import com.enviouse.futureshops.Config;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FutureShopsConfigPathsTest {
    @TempDir
    Path directory;

    @Test
    void everyRegisteredConfigUsesTheFutureShopsDirectory() {
        for (String fileName : List.of(
                Config.FILE_NAME,
                ClientConfig.FILE_NAME,
                EscrowConfig.FILE_NAME,
                AuctionHouseConfig.FILE_NAME,
                BazaarConfig.FILE_NAME)) {
            assertEquals("futureshops/" + fileName,
                    FutureShopsConfigPaths.registeredFile(fileName));
        }

        assertThrows(IllegalArgumentException.class,
                () -> FutureShopsConfigPaths.registeredFile("../outside.toml"));
        assertThrows(IllegalArgumentException.class,
                () -> FutureShopsConfigPaths.registeredFile("other/config.toml"));
        assertThrows(IllegalArgumentException.class,
                () -> FutureShopsConfigPaths.registeredFile("other\\config.toml"));
    }

    @Test
    void migratesActiveFilesAndForgeBackupsWithoutChangingContent()
            throws Exception {
        Path config = directory.resolve("config");
        Files.createDirectories(config);
        Map<String, String> legacyFiles = new LinkedHashMap<>();
        legacyFiles.put(Config.FILE_NAME, "common exact content\n");
        legacyFiles.put(ClientConfig.FILE_NAME, "client exact content\n");
        legacyFiles.put(EscrowConfig.FILE_NAME, "escrow exact content\n");
        legacyFiles.put(AuctionHouseConfig.FILE_NAME, "auction exact content\n");
        legacyFiles.put(BazaarConfig.FILE_NAME, "bazaar exact content\n");
        legacyFiles.put("futureshops-common-1.toml.bak", "common backup\n");
        legacyFiles.put("futureshops-auction-house-5.toml.bak",
                "auction backup\n");
        for (Map.Entry<String, String> entry : legacyFiles.entrySet()) {
            Files.writeString(config.resolve(entry.getKey()), entry.getValue());
        }
        Files.writeString(config.resolve("fml.toml"), "forge owned\n");
        Files.writeString(config.resolve("another-mod.toml"), "unrelated\n");

        FutureShopsConfigPaths.prepareAndMigrateLegacyFiles(config);

        Path nested = config.resolve(FutureShopsConfigPaths.DIRECTORY_NAME);
        for (Map.Entry<String, String> entry : legacyFiles.entrySet()) {
            assertFalse(Files.exists(config.resolve(entry.getKey())));
            assertEquals(entry.getValue(),
                    Files.readString(nested.resolve(entry.getKey())));
        }
        assertEquals("forge owned\n", Files.readString(config.resolve("fml.toml")));
        assertEquals("unrelated\n",
                Files.readString(config.resolve("another-mod.toml")));
        assertFalse(Files.exists(nested.resolve("migration-backups")));

        FutureShopsConfigPaths.prepareAndMigrateLegacyFiles(config);

        assertFalse(Files.exists(nested.resolve("migration-backups")));
        for (Map.Entry<String, String> entry : legacyFiles.entrySet()) {
            assertEquals(entry.getValue(),
                    Files.readString(nested.resolve(entry.getKey())));
        }
    }

    @Test
    void keepsNestedConfigAndArchivesAConflictingLegacyFile()
            throws Exception {
        Path config = directory.resolve("config");
        Path nested = config.resolve(FutureShopsConfigPaths.DIRECTORY_NAME);
        Files.createDirectories(nested);
        Files.writeString(nested.resolve(Config.FILE_NAME), "nested\n");
        Files.writeString(config.resolve(Config.FILE_NAME), "legacy\n");

        FutureShopsConfigPaths.prepareAndMigrateLegacyFiles(config);

        assertEquals("nested\n",
                Files.readString(nested.resolve(Config.FILE_NAME)));
        assertFalse(Files.exists(config.resolve(Config.FILE_NAME)));
        Path archived = nested.resolve("migration-backups")
                .resolve(Config.FILE_NAME + ".legacy");
        assertEquals("legacy\n", Files.readString(archived));

        FutureShopsConfigPaths.prepareAndMigrateLegacyFiles(config);

        assertEquals("legacy\n", Files.readString(archived));
        assertFalse(Files.exists(archived.resolveSibling(
                Config.FILE_NAME + ".legacy.1")));

        Files.writeString(config.resolve(Config.FILE_NAME), "second legacy\n");
        FutureShopsConfigPaths.prepareAndMigrateLegacyFiles(config);

        assertEquals("nested\n",
                Files.readString(nested.resolve(Config.FILE_NAME)));
        assertEquals("legacy\n", Files.readString(archived));
        assertEquals("second legacy\n", Files.readString(
                archived.resolveSibling(Config.FILE_NAME + ".legacy.1")));
    }

    @Test
    void rejectsAnUnsafeLegacyConfigPath() throws Exception {
        Path config = directory.resolve("config");
        Files.createDirectories(config.resolve(Config.FILE_NAME));

        assertThrows(IllegalStateException.class,
                () -> FutureShopsConfigPaths.prepareAndMigrateLegacyFiles(config));
        assertTrue(Files.isDirectory(config.resolve(Config.FILE_NAME)));
    }

    @Test
    void rejectsASymbolicLinkUsingALegacyConfigName() throws Exception {
        Path config = directory.resolve("config");
        Files.createDirectories(config);
        Path target = directory.resolve("outside.toml");
        Files.writeString(target, "outside\n");
        try {
            Files.createSymbolicLink(config.resolve(Config.FILE_NAME), target);
        } catch (IOException | UnsupportedOperationException exception) {
            Assumptions.abort("Symbolic links are unavailable");
        }

        assertThrows(IllegalStateException.class,
                () -> FutureShopsConfigPaths.prepareAndMigrateLegacyFiles(config));
        assertEquals("outside\n", Files.readString(target));
        assertTrue(Files.isSymbolicLink(config.resolve(Config.FILE_NAME)));
    }
}
