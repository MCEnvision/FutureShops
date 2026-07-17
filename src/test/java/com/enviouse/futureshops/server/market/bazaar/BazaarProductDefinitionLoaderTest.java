package com.enviouse.futureshops.server.market.bazaar;

import com.enviouse.futureshops.config.BazaarConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BazaarProductDefinitionLoaderTest {
    private static final BazaarConfig.ProductDefaults DEFAULTS =
            new BazaarConfig.ProductDefaults(1, 5L);

    @TempDir
    Path directory;

    @Test
    void loadsRichDefinitionsInCanonicalOrder() throws Exception {
        Files.writeString(directory.resolve("products.json"), """
                {
                  "schema": 1,
                  "products": [
                    {
                      "id": "iron",
                      "version": 1,
                      "item": "minecraft:iron_ingot",
                      "category": "ores",
                      "displayName": "Refined Iron",
                      "lotSize": 4,
                      "priceTickMinor": 10,
                      "minimumPriceMinor": 20,
                      "maximumPriceMinor": 10000,
                      "maximumQuantity": 400,
                      "allowedDimensions": ["minecraft:the_nether", "minecraft:overworld"],
                      "restrictions": {
                        "allowDamaged": false,
                        "allowNamed": false,
                        "allowEnchanted": false,
                        "allowContainers": false,
                        "allowCapabilities": false
                      }
                    },
                    {
                      "id": "named_gem",
                      "version": 2,
                      "item": "minecraft:diamond",
                      "identityPolicy": "exact",
                      "exactNbt": "{display:{Name:'{\"text\":\"Market Gem\"}'}}",
                      "category": "gems",
                      "status": "halted"
                    }
                  ]
                }
                """);

        BazaarProductCatalogSnapshot snapshot = load();

        assertEquals(List.of("iron", "named_gem"), snapshot.definitions()
                .stream().map(value -> value.product().productId()).toList());
        BazaarProductDefinition iron = snapshot.definitions().get(0);
        assertEquals(4, iron.product().lotSize());
        assertEquals(List.of("minecraft:overworld", "minecraft:the_nether"),
                iron.allowedDimensions());
        BazaarProductDefinition gem = snapshot.definitions().get(1);
        assertEquals(BazaarProductIdentityPolicy.EXACT,
                gem.identityPolicy());
        assertFalse(gem.product().exactIdentity().isEmpty());
        assertEquals(BazaarProductStatus.HALTED, gem.product().status());
        assertTrue(snapshot.fingerprint().matches("[0-9a-f]{64}"));
    }

    @Test
    void rejectsUnknownFieldsAndFractionalIntegers() throws Exception {
        Files.writeString(directory.resolve("bad.json"), """
                {
                  "id": "iron",
                  "version": 1.5,
                  "item": "minecraft:iron_ingot",
                  "surprise": true
                }
                """);

        IllegalArgumentException rejected = assertThrows(
                IllegalArgumentException.class, this::load);

        assertTrue(rejected.getMessage().contains("unknown field"));
    }

    @Test
    void rejectsDuplicateFieldsBeforeGsonCanOverwriteThem() throws Exception {
        Files.writeString(directory.resolve("bad.json"), """
                {
                  "id": "iron",
                  "id": "gold",
                  "version": 1,
                  "item": "minecraft:iron_ingot"
                }
                """);

        IllegalArgumentException rejected = assertThrows(
                IllegalArgumentException.class, this::load);

        assertTrue(rejected.getMessage().contains("duplicate field"));
    }

    @Test
    void rejectsMalformedUtf8() throws Exception {
        Files.write(directory.resolve("bad.json"),
                new byte[]{(byte) 0xC3, (byte) 0x28});

        IllegalArgumentException rejected = assertThrows(
                IllegalArgumentException.class, this::load);

        assertTrue(rejected.getMessage().contains("valid UTF8"));
    }

    @Test
    void rejectsConflictingActiveIdentities() throws Exception {
        Files.writeString(directory.resolve("bad.json"), """
                {
                  "products": [
                    {"id":"iron_one","version":1,"item":"minecraft:iron_ingot"},
                    {"id":"iron_two","version":1,"item":"minecraft:iron_ingot"}
                  ]
                }
                """);

        IllegalArgumentException rejected = assertThrows(
                IllegalArgumentException.class, this::load);

        assertTrue(rejected.getMessage().contains("conflicting identities"));
    }

    @Test
    void requiresHistoricalVersionsToBeRetired() throws Exception {
        Files.writeString(directory.resolve("bad.json"), """
                {
                  "products": [
                    {"id":"iron","version":1,"item":"minecraft:iron_ingot"},
                    {"id":"iron","version":2,"item":"minecraft:iron_ingot"}
                  ]
                }
                """);

        IllegalArgumentException rejected = assertThrows(
                IllegalArgumentException.class, this::load);

        assertTrue(rejected.getMessage().contains(
                "Historical Bazaar product versions must be retired"));
    }

    @Test
    void retiredHistorySurvivesRemovedItemRegistry() throws Exception {
        Files.writeString(directory.resolve("retired.json"), """
                {
                  "id":"old_material",
                  "version":1,
                  "item":"removedmod:old_material",
                  "status":"retired"
                }
                """);

        BazaarProductCatalogSnapshot snapshot = load();

        assertEquals(BazaarProductStatus.RETIRED,
                snapshot.definitions().get(0).product().status());
    }

    @Test
    void fingerprintDoesNotDependOnSourceFileName() throws Exception {
        String product = """
                {"id":"iron","version":1,"item":"minecraft:iron_ingot"}
                """;
        Files.writeString(directory.resolve("first.json"), product);
        String first = load().fingerprint();
        Files.move(directory.resolve("first.json"),
                directory.resolve("renamed.json"));

        assertEquals(first, load().fingerprint());
    }

    @Test
    void emptyAndMissingDirectoriesProduceTheSameSnapshot() throws Exception {
        BazaarProductCatalogSnapshot empty = load();
        BazaarProductCatalogSnapshot missing =
                BazaarProductDefinitionLoader.load(
                        directory.resolve("missing"), DEFAULTS, 1000,
                        value -> true);

        assertEquals(empty, missing);
    }

    @Test
    void parseCollectionIsAllOrNothing() {
        List<BazaarProductDefinition> destination = new ArrayList<>();
        String input = """
                {
                  "products": [
                    {"id":"iron","version":1,"item":"minecraft:iron_ingot"},
                    {"id":"gold","version":0,"item":"minecraft:gold_ingot"}
                  ]
                }
                """;

        assertThrows(IllegalArgumentException.class, () ->
                BazaarProductDefinitionLoader.parseFile(input, "inline.json",
                        DEFAULTS, 1000, value -> true, destination));
        assertTrue(destination.isEmpty());
    }

    private BazaarProductCatalogSnapshot load() throws Exception {
        return BazaarProductDefinitionLoader.load(directory, DEFAULTS,
                1000, value -> value.startsWith("minecraft:"));
    }
}
