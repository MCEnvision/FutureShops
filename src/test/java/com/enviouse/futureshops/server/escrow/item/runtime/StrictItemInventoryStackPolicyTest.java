package com.enviouse.futureshops.server.escrow.item.runtime;

import com.enviouse.futureshops.MinecraftTestBootstrap;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationDirection;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrictItemInventoryStackPolicyTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void deeplyNestedForeignNbtFailsClosedWithoutEscapingAnError() {
        ItemStack stack = new ItemStack(Items.STONE, 1);
        CompoundTag current = stack.getOrCreateTag();
        for (int depth = 0; depth < 10_000; depth++) {
            CompoundTag child = new CompoundTag();
            child.putString("foreign_mod", "sentinel");
            current.put("child", child);
            current = child;
        }

        boolean supported = assertDoesNotThrow(
                () -> StrictItemInventoryStackPolicy.INSTANCE.supports(
                        stack, ItemInventoryMutationDirection.EXTRACT));

        assertFalse(supported);
        assertTrue(stack.getTag().contains("child"));
    }

    @Test
    void validStackRemainsSupported() {
        assertTrue(StrictItemInventoryStackPolicy.INSTANCE.supports(
                new ItemStack(Items.STONE, 1),
                ItemInventoryMutationDirection.EXTRACT));
    }

    @Test
    void nestedInventoryRemainsUnsupported() {
        ItemStack stack = new ItemStack(Items.CHEST, 1);
        ListTag nestedItems = new ListTag();
        nestedItems.add(new CompoundTag());
        stack.getOrCreateTag().put("Items", nestedItems);

        assertFalse(StrictItemInventoryStackPolicy.INSTANCE.supports(
                stack, ItemInventoryMutationDirection.EXTRACT));
    }
}
