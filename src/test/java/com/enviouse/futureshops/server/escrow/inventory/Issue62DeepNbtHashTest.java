package com.enviouse.futureshops.server.escrow.inventory;

import com.enviouse.futureshops.MinecraftTestBootstrap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Issue62DeepNbtHashTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void supportedForeignNbtDepthHashesDeterministically() {
        ItemStack first = foreignStack(PlayerInventoryHashes
                .MAX_CANONICAL_NBT_DEPTH / 2);
        ItemStack second = first.copy();

        assertArrayEquals(PlayerInventoryHashes.hashSlot(first),
                PlayerInventoryHashes.hashSlot(second));
        assertDoesNotThrow(() -> PlayerInventoryHashes.hashInventory(
                inventoryWith(first)));
    }

    @Test
    void excessiveForeignNbtDepthFailsClosedBeforeStackOverflow() {
        ItemStack foreign = foreignStack(PlayerInventoryHashes
                .MAX_CANONICAL_NBT_DEPTH + 1_000);
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> PlayerInventoryHashes.hashInventory(
                        inventoryWith(foreign)));

        assertEquals("Canonical NBT depth exceeds the supported limit",
                failure.getMessage());
    }

    @Test
    void excessiveForeignNbtDoesNotMutateInventoryPlanningInput() {
        List<ItemStack> inventory = inventoryWith(foreignStack(
                PlayerInventoryHashes.MAX_CANONICAL_NBT_DEPTH + 1_000));
        CompoundTag beforeTag = inventory.get(0).getTag();

        assertThrows(IllegalArgumentException.class,
                () -> PlayerInventoryInsertionPlan.plan(inventory,
                        new ItemStack(Items.EMERALD, 1)));

        assertSame(beforeTag, inventory.get(0).getTag());
        assertEquals(1, inventory.get(0).getCount());
    }

    private static ItemStack foreignStack(int depth) {
        ItemStack stack = new ItemStack(Items.STONE, 1);
        CompoundTag cursor = stack.getOrCreateTag();
        for (int index = 0; index < depth; index++) {
            CompoundTag child = new CompoundTag();
            cursor.put("foreign_mod", child);
            cursor = child;
        }
        return stack;
    }

    private static List<ItemStack> inventoryWith(ItemStack stack) {
        List<ItemStack> inventory = new ArrayList<>();
        for (int index = 0; index < PlayerInventoryHashes.MAIN_SLOT_COUNT;
             index++) {
            inventory.add(ItemStack.EMPTY);
        }
        inventory.set(0, stack);
        return inventory;
    }
}
