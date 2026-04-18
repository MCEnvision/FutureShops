package com.enviouse.futureshops.server.transaction;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Utility for NBT-aware item matching.
 * When nbtAware is true, both item type and NBT tag must match.
 * When nbtAware is false, only item type is checked (original behavior).
 */
public final class NbtMatchUtil {
    private NbtMatchUtil() {}

    /**
     * Check whether a stack matches the expected item, optionally with NBT comparison.
     */
    public static boolean matches(ItemStack stack, Item item, boolean nbtAware, @Nullable CompoundTag requiredTag) {
        if (stack.isEmpty() || stack.getItem() != item) {
            return false;
        }
        if (!nbtAware) {
            return true;
        }
        return tagsEqual(stack.getTag(), requiredTag);
    }

    /**
     * Null-safe NBT tag equality check.
     * Two null tags are considered equal (both have no NBT).
     */
    public static boolean tagsEqual(@Nullable CompoundTag a, @Nullable CompoundTag b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return Objects.equals(a, b);
    }
}

