package com.enviouse.futureshops.server.shop;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Abstraction for external storage systems (Refined Storage, Applied Energistics, etc.).
 * Implementations are registered via {@link ExternalStorageRegistry} and queried during
 * player-shop stock resolution.
 */
public interface ExternalStorageAdapter {
    /**
     * Whether this adapter can handle the given block entity.
     */
    boolean canHandle(BlockEntity blockEntity);

    /**
     * Counts the number of the given item in this storage (type-only match).
     */
    int countItem(BlockEntity blockEntity, Item item);

    /**
     * Counts the number of the given item in this storage with optional NBT matching.
     * When {@code nbtAware} is true, only items whose NBT equals {@code requiredTag} are counted.
     */
    default int countItem(BlockEntity blockEntity, Item item, boolean nbtAware, @Nullable CompoundTag requiredTag) {
        // Default falls back to type-only for backward compat; implementations should override
        return countItem(blockEntity, item);
    }

    /**
     * Checks if the given quantity can be extracted (type-only match).
     */
    boolean canExtract(BlockEntity blockEntity, Item item, int count);

    /**
     * Checks if the given quantity can be extracted with optional NBT matching.
     */
    default boolean canExtract(BlockEntity blockEntity, Item item, int count, boolean nbtAware, @Nullable CompoundTag requiredTag) {
        return canExtract(blockEntity, item, count);
    }

    /**
     * Extracts the given quantity from storage (type-only match). Returns extracted stacks.
     */
    List<ItemStack> extract(BlockEntity blockEntity, Item item, int count);

    /**
     * Extracts the given quantity from storage with optional NBT matching.
     */
    default List<ItemStack> extract(BlockEntity blockEntity, Item item, int count, boolean nbtAware, @Nullable CompoundTag requiredTag) {
        return extract(blockEntity, item, count);
    }

    default List<ItemStack> previewExtract(BlockEntity blockEntity, Item item,
                                           int count, boolean nbtAware,
                                           @Nullable CompoundTag requiredTag) {
        return List.of();
    }

    /**
     * Checks if all stacks can be inserted.
     */
    boolean canInsert(BlockEntity blockEntity, List<ItemStack> stacks);

    /**
     * Inserts all stacks into storage.
     */
    boolean insert(BlockEntity blockEntity, List<ItemStack> stacks);
}
