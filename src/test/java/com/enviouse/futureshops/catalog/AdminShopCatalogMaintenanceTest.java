package com.enviouse.futureshops.catalog;

import com.enviouse.futureshops.Config;
import com.enviouse.futureshops.MinecraftTestBootstrap;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferJsonParser;
import com.enviouse.futureshops.network.ServerShopOfferNetworkCodec;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminShopCatalogMaintenanceTest {
    @TempDir
    Path temporaryDirectory;

    @BeforeAll
    static void initializeMinecraftRegistries() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void listingLimitsRetainSafeDefaultsAndHardCeilings() {
        assertEquals(512, Config.adminShopMaximumListings);
        assertEquals(10_000, ServerShopOfferJsonParser.MAX_LISTINGS);
        assertEquals(10_000, ServerShopOfferNetworkCodec.MAX_LISTINGS);
    }

    @Test
    void configuredLimitCanExceedLegacy512Ceiling() {
        int previous = Config.adminShopMaximumListings;
        try {
            Config.adminShopMaximumListings = 600;
            JsonObject root = root();
            JsonArray listings = root.getAsJsonArray("listings");
            for (int index = 0; index < 513; index++) {
                listings.add(validFreeListing("listing_" + index));
            }

            AdminShopCatalogMaintenance.ValidationReport report =
                    AdminShopCatalogMaintenance.validate(root);

            assertTrue(report.valid());
            assertEquals(513, report.listingCount());
            assertEquals(600, report.configuredMaximum());
        } finally {
            Config.adminShopMaximumListings = previous;
        }
    }

    @Test
    void configuredLimitReportsCatalogOverflow() {
        int previous = Config.adminShopMaximumListings;
        try {
            Config.adminShopMaximumListings = 1;
            JsonObject root = root();
            root.getAsJsonArray("listings")
                    .add(validFreeListing("first"));
            root.getAsJsonArray("listings")
                    .add(validFreeListing("second"));

            AdminShopCatalogMaintenance.ValidationReport report =
                    AdminShopCatalogMaintenance.validate(root);

            assertFalse(report.valid());
            assertTrue(report.issues().stream().anyMatch(issue ->
                    issue.code().equals(
                            "offer.catalog.too_many_listings")));
        } finally {
            Config.adminShopMaximumListings = previous;
        }
    }

    @Test
    void validationIdentifiesEveryMissingItemRole() {
        JsonObject root = root();
        root.getAsJsonArray("listings").add(JsonParser.parseString("""
                {
                  "id": "missing_everywhere",
                  "icon": {"itemId": "removed:icon"},
                  "outputs": [
                    {"id": "output", "itemId": "removed:output", "count": 1}
                  ],
                  "acquireOptions": [{
                    "id": "barter",
                    "paymentType": "items",
                    "itemCosts": [
                      {"id": "cost", "itemId": "removed:cost", "count": 1}
                    ]
                  }],
                  "sellOptions": [{
                    "id": "sell",
                    "inputs": [
                      {"id": "input", "itemId": "removed:sell", "count": 1}
                    ],
                    "moneyPayout": 100
                  }]
                }
                """).getAsJsonObject());

        AdminShopCatalogMaintenance.ValidationReport report =
                AdminShopCatalogMaintenance.validate(root);
        Set<String> missingPaths = report.issues().stream()
                .filter(issue -> issue.code().equals("offer.item.missing"))
                .map(AdminShopCatalogMaintenance.CatalogIssue::path)
                .collect(Collectors.toSet());

        assertTrue(missingPaths.contains(
                "listings.0.outputs.0.itemId"));
        assertTrue(missingPaths.contains(
                "listings.0.icon.itemId"));
        assertTrue(missingPaths.contains(
                "listings.0.acquireOptions.0.itemCosts.0.itemId"));
        assertTrue(missingPaths.contains(
                "listings.0.sellOptions.0.inputs.0.itemId"));
    }

    @Test
    void missingListingsArePartitionedAndPreservedInRecoveryFile()
            throws Exception {
        JsonArray listings = new JsonArray();
        listings.add(validFreeListing("valid"));
        JsonObject missing = validFreeListing("missing");
        missing.getAsJsonArray("outputs").get(0).getAsJsonObject()
                .addProperty("itemId", "removed:missing");
        listings.add(missing);

        AdminShopCatalogMaintenance.MissingItemPartition partition =
                AdminShopCatalogMaintenance.partitionMissing(listings);

        assertEquals(1, partition.retained().size());
        assertEquals(1, partition.quarantined().size());
        assertEquals("missing", partition.quarantined().get(0)
                .getAsJsonObject().get("id").getAsString());
        Path catalog = temporaryDirectory.resolve("shops")
                .resolve("admin.json");
        Path recovery = AdminShopCatalogMaintenance.writeRecoveryFile(
                catalog, partition.quarantined(), "test operator",
                "removed mod cleanup");
        JsonObject saved = JsonParser.parseString(
                Files.readString(recovery)).getAsJsonObject();
        assertTrue(recovery.startsWith(
                catalog.getParent().resolve("recovery")));
        assertEquals("admin.json", saved.get("source").getAsString());
        assertEquals("removed mod cleanup",
                saved.get("reason").getAsString());
        assertEquals("missing", saved.getAsJsonArray("listings")
                .get(0).getAsJsonObject().get("id").getAsString());

        AdminShopCatalogMaintenance.MissingItemPartition repeated =
                AdminShopCatalogMaintenance.partitionMissing(
                        partition.retained());
        assertEquals(0, repeated.quarantined().size());
    }

    @Test
    void recoveryWriterRefusesAnUnsafeRecoveryPath() throws Exception {
        Path catalog = temporaryDirectory.resolve("shops")
                .resolve("admin.json");
        Files.createDirectories(catalog.getParent());
        Files.writeString(catalog.getParent().resolve("recovery"),
                "not a directory");
        JsonArray listings = new JsonArray();
        listings.add(validFreeListing("missing"));

        assertThrows(java.io.IOException.class, () ->
                AdminShopCatalogMaintenance.writeRecoveryFile(
                        catalog, listings, "test operator",
                        "path safety test"));
    }

    private static JsonObject root() {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 2);
        root.addProperty("shopId", "default");
        root.addProperty("displayName", "Server Shop");
        root.add("categories", new JsonArray());
        root.add("listings", new JsonArray());
        return root;
    }

    private static JsonObject validFreeListing(String id) {
        return JsonParser.parseString("""
                {
                  "id": "%s",
                  "outputs": [
                    {"id": "output", "itemId": "minecraft:stone", "count": 1}
                  ],
                  "acquireOptions": [
                    {"id": "free", "paymentType": "free"}
                  ]
                }
                """.formatted(id)).getAsJsonObject();
    }
}
