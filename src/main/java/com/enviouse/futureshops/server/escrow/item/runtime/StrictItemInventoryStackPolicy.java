package com.enviouse.futureshops.server.escrow.item.runtime;

import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationDirection;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.CapabilityProvider;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

enum StrictItemInventoryStackPolicy implements ItemInventoryStackPolicy {
    INSTANCE;

    private static final Method GET_CAPABILITIES = capabilityAccessor();

    @Override
    public boolean supports(
            ItemStack stack,
            ItemInventoryMutationDirection direction
    ) {
        if (stack == null || stack.isEmpty() || direction == null) {
            return stack != null && stack.isEmpty() && direction != null;
        }
        try {
            CompoundTag saved = stack.save(new CompoundTag());
            if (hasPersistentCapabilities(saved)
                    || hasNestedInventory(saved)
                    || hasCapabilityProvider(stack)) {
                return false;
            }
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean hasPersistentCapabilities(CompoundTag saved) {
        return saved.contains("ForgeCaps", Tag.TAG_COMPOUND);
    }

    private static boolean hasNestedInventory(CompoundTag saved) {
        CompoundTag itemTag = saved.contains("tag", Tag.TAG_COMPOUND)
                ? saved.getCompound("tag") : new CompoundTag();
        if (nonemptyList(itemTag, "Items")
                || nonemptyList(itemTag, "Inventory")) {
            return true;
        }
        if (!itemTag.contains("BlockEntityTag", Tag.TAG_COMPOUND)) {
            return false;
        }
        CompoundTag blockEntity = itemTag.getCompound("BlockEntityTag");
        return nonemptyList(blockEntity, "Items")
                || nonemptyList(blockEntity, "Inventory");
    }

    private static boolean nonemptyList(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_LIST)) {
            return false;
        }
        ListTag values = tag.getList(key, Tag.TAG_COMPOUND);
        return !values.isEmpty();
    }

    private static boolean hasCapabilityProvider(ItemStack stack) {
        try {
            return GET_CAPABILITIES.invoke(stack) != null;
        } catch (IllegalAccessException | InvocationTargetException
                 | RuntimeException exception) {
            return true;
        }
    }

    private static Method capabilityAccessor() {
        try {
            Method method = CapabilityProvider.class.getDeclaredMethod(
                    "getCapabilities");
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
