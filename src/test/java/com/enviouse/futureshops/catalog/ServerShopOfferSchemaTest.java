package com.enviouse.futureshops.catalog;

import com.enviouse.futureshops.MinecraftTestBootstrap;
import com.enviouse.futureshops.catalog.offer.AcquireOfferOption;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferValidator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerShopOfferSchemaTest {
    @BeforeAll
    static void initializeMinecraftRegistries() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void parsesVersionTwoFreeBundleAndSellOffer() {
        String json = """
                {
                  "schemaVersion": 2,
                  "shopId": "default",
                  "displayName": "Server Shop",
                  "categories": [
                    { "id": "all", "displayName": "All", "sortOrder": 0 }
                  ],
                  "listings": [
                    {
                      "id": "iron_bundle",
                      "displayName": "Iron Bundle",
                      "description": "Three tools.",
                      "categoryId": "all",
                      "outputs": [
                        { "id": "pickaxe", "itemId": "minecraft:iron_pickaxe", "count": 1 },
                        { "id": "sword", "itemId": "minecraft:iron_sword", "count": 1 },
                        { "id": "shovel", "itemId": "minecraft:iron_shovel", "count": 1 }
                      ],
                      "acquireOptions": [
                        { "id": "free", "paymentType": "free" },
                        { "id": "money", "paymentType": "money", "moneyCost": 1000 }
                      ],
                      "sellOptions": [
                        {
                          "id": "sell_tools",
                          "inputs": [
                            { "id": "pickaxe", "itemId": "minecraft:iron_pickaxe", "count": 1 },
                            { "id": "sword", "itemId": "minecraft:iron_sword", "count": 1 }
                          ],
                          "moneyPayout": 400
                        }
                      ],
                      "stock": { "type": "limited", "quantity": 20 }
                    }
                  ]
                }
                """;
        ShopDefinition definition =
                ShopDefinitionLoader.parseJson(json, "offers.json");

        assertEquals(2, definition.schemaVersion());
        assertEquals(1, definition.offers().size());
        assertEquals(3, definition.offers().get(0).outputs().size());
        assertEquals(2, definition.offers().get(0)
                .sellOptions().get(0).itemInputs().size());
        assertTrue(definition.offers().get(0).acquireOptions().stream()
                .anyMatch(AcquireOfferOption::free));
        assertTrue(ServerShopOfferValidator.validate(
                definition.offers().get(0)).valid());
        assertEquals("minecraft:iron_pickaxe",
                definition.items().get(0).itemId());
        assertFalse(definition.offers().get(0).revision() == 0L);
    }

    @Test
    void rejectsUnknownFutureSchema() {
        ShopDefinition definition = ShopDefinitionLoader.parseJson(
                "{\"schemaVersion\":3,\"shopId\":\"future\"}",
                "future.json");
        assertNull(definition);
    }

    @Test
    void legacySchemaCompilesPositivePricesAndLeavesZeroDisabled() {
        String json = """
                {
                  "shopId": "default",
                  "items": [
                    { "itemId": "minecraft:diamond", "buyPrice": 500, "sellPrice": 0 },
                    { "itemId": "minecraft:barrier", "buyPrice": 0, "sellPrice": 0 }
                  ]
                }
                """;
        ShopDefinition definition =
                ShopDefinitionLoader.parseJson(json, "legacy.json");
        assertEquals(1, definition.schemaVersion());
        assertEquals(1, definition.offers().size());
        assertEquals("minecraft:diamond",
                definition.offers().get(0).listingId());
        assertFalse(definition.offers().get(0).acquireOptions().get(0)
                .free());
    }

    @Test
    void versionTwoParserRejectsOversizedAndMalformedCollections() {
        StringBuilder components = new StringBuilder();
        for (int index = 0;
             index <= ServerShopOfferValidator.MAX_COMPONENTS; index++) {
            if (!components.isEmpty()) {
                components.append(',');
            }
            components.append("""
                    {"id":"c%s","itemId":"minecraft:stone","count":1}
                    """.formatted(index));
        }
        String oversized = """
                {
                  "schemaVersion": 2,
                  "shopId": "default",
                  "listings": [{
                    "id": "bounded",
                    "outputs": [%s],
                    "acquireOptions": [
                      {"id":"free","paymentType":"free"}
                    ]
                  }]
                }
                """.formatted(components);
        assertNull(ShopDefinitionLoader.parseJson(
                oversized, "oversized.json"));

        String malformed = """
                {
                  "schemaVersion": 2,
                  "shopId": "default",
                  "listings": [{
                    "id": "malformed",
                    "outputs": ["not an object"],
                    "acquireOptions": [
                      {"id":"free","paymentType":"free"}
                    ]
                  }]
                }
                """;
        assertNull(ShopDefinitionLoader.parseJson(
                malformed, "malformed.json"));
    }
}
