package com.enviouse.futureshops.server.escrow.item.runtime;

import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationDirection;
import net.minecraft.world.item.ItemStack;

@FunctionalInterface
public interface ItemInventoryStackPolicy {
    boolean supports(
            ItemStack stack,
            ItemInventoryMutationDirection direction
    );

    static ItemInventoryStackPolicy strictDefault() {
        return StrictItemInventoryStackPolicy.INSTANCE;
    }
}
