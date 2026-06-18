package com.enviouse.futureshopsp.server.shop;

import com.enviouse.futureshopsp.server.transaction.NbtMatchUtil;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Default adapter backed by NeoForge's {@code Capabilities.ItemHandler.BLOCK}. This handles vanilla
 * chests, barrels, hoppers, and any modded container that exposes an item handler capability.
 *
 * <p>1.21.1 port: {@code blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve()}
 * (LazyOptional) → {@code level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side)} (nullable),
 * queried on the BlockEntity's Level/pos. {@code IItemHandler} moved to {@code net.neoforged.neoforge.items}.
 */
public final class ForgeCapabilityStorageAdapter implements ExternalStorageAdapter {
    public static final ForgeCapabilityStorageAdapter INSTANCE = new ForgeCapabilityStorageAdapter();

    private ForgeCapabilityStorageAdapter() {
    }

    @Override
    public boolean canHandle(BlockEntity blockEntity) {
        return getHandler(blockEntity) != null;
    }

    @Override
    public int countItem(BlockEntity blockEntity, Item item) {
        return countItem(blockEntity, item, false, null);
    }

    @Override
    public int countItem(BlockEntity blockEntity, Item item, boolean nbtAware, @Nullable DataComponentPatch requiredPatch) {
        IItemHandler handler = getHandler(blockEntity);
        if (handler == null) return 0;
        int total = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (NbtMatchUtil.matches(stack, item, nbtAware, requiredPatch)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    @Override
    public boolean canExtract(BlockEntity blockEntity, Item item, int count) {
        return canExtract(blockEntity, item, count, false, null);
    }

    @Override
    public boolean canExtract(BlockEntity blockEntity, Item item, int count, boolean nbtAware, @Nullable DataComponentPatch requiredPatch) {
        IItemHandler handler = getHandler(blockEntity);
        if (handler == null) return false;
        int remaining = count;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack probe = handler.extractItem(i, remaining, true);
            if (probe.isEmpty() || !NbtMatchUtil.matches(probe, item, nbtAware, requiredPatch)) continue;
            remaining -= probe.getCount();
            if (remaining <= 0) return true;
        }
        return false;
    }

    @Override
    public List<ItemStack> extract(BlockEntity blockEntity, Item item, int count) {
        return extract(blockEntity, item, count, false, null);
    }

    @Override
    public List<ItemStack> extract(BlockEntity blockEntity, Item item, int count, boolean nbtAware, @Nullable DataComponentPatch requiredPatch) {
        IItemHandler handler = getHandler(blockEntity);
        if (handler == null) return List.of();
        List<ItemStack> result = new ArrayList<>();
        int remaining = count;
        for (int i = 0; i < handler.getSlots() && remaining > 0; i++) {
            ItemStack probe = handler.extractItem(i, remaining, true);
            if (probe.isEmpty() || !NbtMatchUtil.matches(probe, item, nbtAware, requiredPatch)) continue;
            ItemStack real = handler.extractItem(i, remaining, false);
            if (!real.isEmpty()) {
                remaining -= real.getCount();
                result.add(real);
            }
        }
        return remaining <= 0 ? result : List.of();
    }

    @Override
    public boolean canInsert(BlockEntity blockEntity, List<ItemStack> stacks) {
        IItemHandler handler = getHandler(blockEntity);
        if (handler == null) return false;
        for (ItemStack stack : stacks) {
            ItemStack remaining = stack.copy();
            for (int i = 0; i < handler.getSlots() && !remaining.isEmpty(); i++) {
                remaining = handler.insertItem(i, remaining, true);
            }
            if (!remaining.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public boolean insert(BlockEntity blockEntity, List<ItemStack> stacks) {
        IItemHandler handler = getHandler(blockEntity);
        if (handler == null) return false;
        for (ItemStack stack : stacks) {
            ItemStack remaining = stack.copy();
            for (int i = 0; i < handler.getSlots() && !remaining.isEmpty(); i++) {
                remaining = handler.insertItem(i, remaining, false);
            }
            if (!remaining.isEmpty()) return false;
        }
        return true;
    }

    @Nullable
    private IItemHandler getHandler(BlockEntity blockEntity) {
        if (blockEntity == null) return null;
        Level level = blockEntity.getLevel();
        if (level == null) return null;
        return level.getCapability(Capabilities.ItemHandler.BLOCK, blockEntity.getBlockPos(), null);
    }
}
