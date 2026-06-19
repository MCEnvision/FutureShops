package com.enviouse.futureshopsp.compat.rs2;

import com.enviouse.futureshopsp.server.shop.ExternalStorageAdapter;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

/**
 * STUB. The Refined Storage 1.20.1 (RS1) reflection-based integration is gone; RS for 1.21.1 is the
 * Refined Storage 2 rewrite (different API). Re-implemented as an isolated task; until then this
 * adapter never claims any block entity, so a server without—or with—RS loads cleanly and the
 * generic Capabilities.ItemHandler.BLOCK adapter handles vanilla/most-modded containers.
 */
public final class RefinedStorage2StorageAdapter implements ExternalStorageAdapter {
    public static final RefinedStorage2StorageAdapter INSTANCE = new RefinedStorage2StorageAdapter();

    private RefinedStorage2StorageAdapter() {
    }

    @Override
    public boolean canHandle(BlockEntity blockEntity) {
        return false;
    }

    @Override
    public int countItem(BlockEntity blockEntity, Item item) {
        return 0;
    }

    @Override
    public boolean canExtract(BlockEntity blockEntity, Item item, int count) {
        return false;
    }

    @Override
    public List<ItemStack> extract(BlockEntity blockEntity, Item item, int count) {
        return List.of();
    }

    @Override
    public boolean canInsert(BlockEntity blockEntity, List<ItemStack> stacks) {
        return false;
    }

    @Override
    public boolean insert(BlockEntity blockEntity, List<ItemStack> stacks) {
        return false;
    }
}
