package com.enviouse.futureshops.server.escrow.item.runtime;

import com.enviouse.futureshops.server.escrow.item.ItemInventorySlot;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryState;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public interface ItemInventoryAccess {
    UUID playerId();

    ItemInventoryState capture();

    void write(ItemInventorySlot slot, ItemStack stack);

    void flush();

    void savePlayerData();

    void forcePlayerData();
}
