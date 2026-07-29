package com.enviouse.futureshops.catalog;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class AdminShopConfigWriterSafetyTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void readRejectsOversizedCatalog() throws Exception {
        Path catalog = temporaryDirectory.resolve("admin.json");
        Files.write(catalog, new byte[8 * 1024 * 1024 + 1]);

        assertNull(AdminShopConfigWriter.readRoot(catalog));
    }

    @Test
    void readAndWriteRejectSymbolicLinkCatalog() throws Exception {
        Path target = temporaryDirectory.resolve("target.json");
        Files.writeString(target, "{}");
        Path catalog = temporaryDirectory.resolve("admin.json");
        try {
            Files.createSymbolicLink(catalog, target.getFileName());
        } catch (java.io.IOException | UnsupportedOperationException exception) {
            Assumptions.assumeTrue(false,
                    "Symbolic links are unavailable");
            return;
        }

        assertNull(AdminShopConfigWriter.readRoot(catalog));
        assertFalse(AdminShopConfigWriter.writeValidatedRoot(
                catalog, validLegacyRoot()));
    }

    private static JsonObject validLegacyRoot() {
        JsonObject root = new JsonObject();
        root.addProperty("shopId", "default");
        root.addProperty("displayName", "Server Shop");
        root.add("categories", new JsonArray());
        root.add("items", new JsonArray());
        root.add("promos", new JsonArray());
        root.add("barterRecipes", new JsonArray());
        return root;
    }
}
