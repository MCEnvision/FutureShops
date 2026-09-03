package com.enviouse.futureshopsp.server.transaction;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(EphemeralTestServerProvider.class)
class ShopTransactionUtilInventoryTest {
    @Test
    void failedInsertDoesNotPartiallyMutateInventory(MinecraftServer server) {
        Inventory inventory = new Inventory(null);
        inventory.items.set(0, new ItemStack(Items.DIAMOND, 63));
        for (int slot = 1; slot < inventory.items.size(); slot++) {
            inventory.items.set(slot, new ItemStack(Items.STONE, 64));
        }
        for (int slot = 0; slot < inventory.offhand.size(); slot++) {
            inventory.offhand.set(slot, new ItemStack(Items.STONE, 64));
        }

        assertTrue(ShopTransactionUtil.canFit(inventory, List.of(new ItemStack(Items.DIAMOND, 1))));
        assertFalse(ShopTransactionUtil.canFit(inventory, List.of(new ItemStack(Items.DIAMOND, 2))));
        assertFalse(ShopTransactionUtil.insertIntoInventory(inventory, List.of(new ItemStack(Items.DIAMOND, 2))));
        assertEquals(63, inventory.items.get(0).getCount());
    }
}
