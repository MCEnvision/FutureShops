package com.enviouse.futureshops.server.market.bazaar;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Locale;

public final class BazaarProductCatalogStorage {
    public static final String DEFAULT_FILE_NAME = "default.json";

    private static final String DEFAULT_CATALOG = """
            {
              "schema": 1,
              "products": [
                {"id":"iron_ingot","version":1,"item":"minecraft:iron_ingot","category":"metals","displayName":"Iron Ingot"},
                {"id":"gold_ingot","version":1,"item":"minecraft:gold_ingot","category":"metals","displayName":"Gold Ingot"},
                {"id":"copper_ingot","version":1,"item":"minecraft:copper_ingot","category":"metals","displayName":"Copper Ingot"},
                {"id":"coal","version":1,"item":"minecraft:coal","category":"resources","displayName":"Coal"},
                {"id":"redstone","version":1,"item":"minecraft:redstone","category":"resources","displayName":"Redstone"},
                {"id":"lapis_lazuli","version":1,"item":"minecraft:lapis_lazuli","category":"resources","displayName":"Lapis Lazuli"},
                {"id":"quartz","version":1,"item":"minecraft:quartz","category":"resources","displayName":"Nether Quartz"},
                {"id":"diamond","version":1,"item":"minecraft:diamond","category":"gems","displayName":"Diamond"},
                {"id":"emerald","version":1,"item":"minecraft:emerald","category":"gems","displayName":"Emerald"},
                {"id":"wheat","version":1,"item":"minecraft:wheat","category":"farming","displayName":"Wheat"},
                {"id":"carrot","version":1,"item":"minecraft:carrot","category":"farming","displayName":"Carrot"},
                {"id":"potato","version":1,"item":"minecraft:potato","category":"farming","displayName":"Potato"}
              ]
            }
            """;

    private BazaarProductCatalogStorage() {
    }

    public static boolean prepare(Path directory, boolean seedDefaults)
            throws IOException {
        Files.createDirectories(directory);
        BasicFileAttributes attributes = Files.readAttributes(
                directory, BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isDirectory() || attributes.isSymbolicLink()) {
            throw new IOException(
                    "Bazaar product path must be a real directory");
        }
        if (!seedDefaults || containsJsonFile(directory)) {
            return false;
        }

        Path target = directory.resolve(DEFAULT_FILE_NAME);
        Path temporary = Files.createTempFile(
                directory, ".default-products-", ".tmp");
        try {
            Files.writeString(temporary, DEFAULT_CATALOG,
                    StandardCharsets.UTF_8);
            if (containsJsonFile(directory)) {
                return false;
            }
            try {
                Files.move(temporary, target,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                try {
                    Files.move(temporary, target);
                } catch (FileAlreadyExistsException race) {
                    return false;
                }
            } catch (FileAlreadyExistsException exception) {
                return false;
            }
            return true;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static boolean containsJsonFile(Path directory)
            throws IOException {
        try (var files = Files.list(directory)) {
            return files.anyMatch(path -> path.getFileName().toString()
                    .toLowerCase(Locale.ROOT).endsWith(".json"));
        }
    }
}
