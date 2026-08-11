package com.enviouse.futureshops.server.shop;

import com.enviouse.futureshops.catalog.offer.OfferItemComponent;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BulkSellInventoryReservationTest {
    @Test
    void nonExactRequirementAcceptsTaggedAndDamagedStacks() {
        Map<BulkSellService.InventoryKey, Integer> inventory =
                new LinkedHashMap<>();
        inventory.put(new BulkSellService.InventoryKey(
                "minecraft:diamond_pickaxe", "{Damage:7}"), 1);

        boolean reserved = BulkSellService.reserveRequirements(
                List.of(new OfferItemComponent(
                        "input", "minecraft:diamond_pickaxe", 1, "")),
                inventory, 1);

        assertTrue(reserved);
        assertTrue(inventory.values().stream()
                .allMatch(count -> count == 0));
    }

    @Test
    void exactRequirementOnlyAcceptsItsExactData() {
        Map<BulkSellService.InventoryKey, Integer> inventory =
                new LinkedHashMap<>();
        inventory.put(new BulkSellService.InventoryKey(
                "minecraft:diamond_pickaxe", "{Damage:7}"), 1);

        boolean reserved = BulkSellService.reserveRequirements(
                List.of(new OfferItemComponent(
                        "input", "minecraft:diamond_pickaxe",
                        1, "{Damage:3}")),
                inventory, 1);

        assertFalse(reserved);
        assertTrue(inventory.values().stream()
                .allMatch(count -> count == 1));
    }

    @Test
    void exactRequirementsReserveBeforeWildcardRequirements() {
        Map<BulkSellService.InventoryKey, Integer> inventory =
                new LinkedHashMap<>();
        inventory.put(new BulkSellService.InventoryKey(
                "minecraft:diamond_pickaxe", "{Damage:3}"), 1);
        inventory.put(new BulkSellService.InventoryKey(
                "minecraft:diamond_pickaxe", "{Damage:7}"), 1);

        boolean reserved = BulkSellService.reserveRequirements(
                List.of(
                        new OfferItemComponent(
                                "wildcard", "minecraft:diamond_pickaxe",
                                1, ""),
                        new OfferItemComponent(
                                "exact", "minecraft:diamond_pickaxe",
                                1, "{Damage:3}")),
                inventory, 1);

        assertTrue(reserved);
        assertTrue(inventory.values().stream()
                .allMatch(count -> count == 0));
    }
}
