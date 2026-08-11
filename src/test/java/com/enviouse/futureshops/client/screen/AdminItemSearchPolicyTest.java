package com.enviouse.futureshops.client.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminItemSearchPolicyTest {
    private static final String ID = "minecraft:diamond";
    private static final String SEARCH_TEXT =
            "minecraft:diamond diamond";

    @Test
    void namespaceSearchMatchesWhileTheModNameIsStillPartial() {
        assertTrue(AdminItemSearchPolicy.matches(ID, SEARCH_TEXT, "@m"));
        assertTrue(AdminItemSearchPolicy.matches(ID, SEARCH_TEXT, "@mine"));
        assertTrue(AdminItemSearchPolicy.matches(ID, SEARCH_TEXT, "@MINECRAFT"));
    }

    @Test
    void namespaceSearchDoesNotMatchItemNamesOrOtherNamespaces() {
        assertFalse(AdminItemSearchPolicy.matches(ID, SEARCH_TEXT, "@diamond"));
        assertFalse(AdminItemSearchPolicy.matches(ID, SEARCH_TEXT, "@create"));
    }

    @Test
    void displayNameSearchSupportsCommonModAliases() {
        assertTrue(AdminItemSearchPolicy.matches(
                "ae2:certus_quartz_crystal", "certus quartz",
                "Applied Energistics 2", "@Applied"));
        assertTrue(AdminItemSearchPolicy.matches(
                "mcwdoors:oak_barn_door", "oak barn door",
                "Macaw's Doors", "@Macaw"));
        assertTrue(AdminItemSearchPolicy.matches(
                "ae2:certus_quartz_crystal", "certus quartz",
                "Applied Energistics 2", "@AppliedEnergistics"));
        assertFalse(AdminItemSearchPolicy.matches(
                "ae2:certus_quartz_crystal", "certus quartz",
                "Applied Energistics 2", "@Mekanism"));
        assertFalse(AdminItemSearchPolicy.matches(
                "ae2:certus_quartz_crystal", "certus quartz",
                "Applied Energistics 2", "@-"));
    }

    @Test
    void plainSearchStillMatchesIdsAndDisplayNames() {
        assertTrue(AdminItemSearchPolicy.matches(ID, SEARCH_TEXT, "diamond"));
        assertTrue(AdminItemSearchPolicy.matches(ID, SEARCH_TEXT, "MINECRAFT"));
        assertFalse(AdminItemSearchPolicy.matches(ID, SEARCH_TEXT, "emerald"));
    }

    @Test
    void blankQueriesIncludeTheWholeRegistry() {
        assertTrue(AdminItemSearchPolicy.matches(ID, SEARCH_TEXT, ""));
        assertTrue(AdminItemSearchPolicy.matches(ID, SEARCH_TEXT, "@"));
    }

    @Test
    void itemTagSearchIsPredictive() {
        String tags = "forge:ingots forge:ingots/iron minecraft:beacon_payment_items";
        assertTrue(AdminItemSearchPolicy.matches(
                "minecraft:iron_ingot", "iron ingot", "Minecraft",
                tags, "#forge:ing"));
        assertTrue(AdminItemSearchPolicy.matches(
                "minecraft:iron_ingot", "iron ingot", "Minecraft",
                tags, "#beacon_payment"));
        assertTrue(AdminItemSearchPolicy.matches(
                "minecraft:iron_ingot", "iron ingot", "Minecraft",
                tags, "beacon_payment"));
        assertFalse(AdminItemSearchPolicy.matches(
                "minecraft:iron_ingot", "iron ingot", "Minecraft",
                tags, "#logs"));
    }

    @Test
    void modTagAndNameFiltersCanBeCombined() {
        assertTrue(AdminItemSearchPolicy.matches(
                "examplemod:steel_ingot", "steel ingot",
                "Example Mod", "forge:ingots forge:ingots/steel",
                "@example #forge:ing steel"));
        assertFalse(AdminItemSearchPolicy.matches(
                "examplemod:steel_ingot", "steel ingot",
                "Example Mod", "forge:ingots forge:ingots/steel",
                "@example #forge:gems steel"));
    }
}
