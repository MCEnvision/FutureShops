package com.enviouse.futureshops.server.transaction;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/** Shared low-level helpers for authoritative shop transactions. */
public final class ShopTransactionUtil {
    /** Hard cap for a single buy transaction line (admin shop). */
    public static final int MAX_BUY_QUANTITY = 2304;
    /** Hard cap for a single sell transaction line — allows mass selling full inventory. */
    public static final int MAX_SELL_QUANTITY = 2304;
    /** Legacy alias — prefer the specific buy/sell constants. */
    public static final int MAX_QUANTITY = MAX_BUY_QUANTITY;

    private static final ConcurrentHashMap<UUID, ReentrantLock> PLAYER_LOCKS = new ConcurrentHashMap<>();

    private ShopTransactionUtil() {
    }

    public static ReentrantLock lockFor(UUID playerUUID) {
        return PLAYER_LOCKS.computeIfAbsent(playerUUID, ignored -> new ReentrantLock());
    }

    public static Item resolveItem(String itemId) {
        return ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(itemId));
    }

    public static List<ItemStack> snapshotInventorySlots(Inventory inventory) {
        List<ItemStack> result = new ArrayList<>(inventory.items.size() + inventory.offhand.size());
        for (ItemStack stack : inventory.items) {
            result.add(stack.copy());
        }
        for (ItemStack stack : inventory.offhand) {
            result.add(stack.copy());
        }
        return result;
    }

    public static boolean canFit(Inventory inventory, List<ItemStack> stacks) {
        List<ItemStack> simulation = snapshotInventorySlots(inventory);
        for (ItemStack stack : stacks) {
            ItemStack copy = stack.copy();
            insertIntoSlotCopies(simulation, copy);
            if (!copy.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public static boolean insertIntoInventory(Inventory inventory, List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            ItemStack copy = stack.copy();
            insertIntoLiveSlots(inventory.items, copy);
            insertIntoLiveSlots(inventory.offhand, copy);
            if (!copy.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public static int countItems(Inventory inventory, Item target) {
        return countItems(inventory, target, false, null);
    }

    /**
     * NBT-aware inventory count. When nbtAware is true, only items matching the required NBT tag are counted.
     */
    public static int countItems(Inventory inventory, Item target, boolean nbtAware, @Nullable CompoundTag requiredTag) {
        int total = 0;
        for (ItemStack stack : inventory.items) {
            if (NbtMatchUtil.matches(stack, target, nbtAware, requiredTag)) {
                total += stack.getCount();
            }
        }
        for (ItemStack stack : inventory.offhand) {
            if (NbtMatchUtil.matches(stack, target, nbtAware, requiredTag)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    public static boolean removeItems(Inventory inventory, Item target, int quantity) {
        return removeItems(inventory, target, quantity, false, null);
    }

    /**
     * NBT-aware item removal. When nbtAware is true, only items matching the required NBT tag are removed.
     */
    public static boolean removeItems(Inventory inventory, Item target, int quantity, boolean nbtAware, @Nullable CompoundTag requiredTag) {
        int remaining = quantity;
        remaining = shrinkMatchingStacks(inventory.items, target, remaining, nbtAware, requiredTag);
        remaining = shrinkMatchingStacks(inventory.offhand, target, remaining, nbtAware, requiredTag);
        return remaining == 0;
    }

    private static int shrinkMatchingStacks(NonNullList<ItemStack> slots, Item target, int remaining,
                                            boolean nbtAware, @Nullable CompoundTag requiredTag) {
        for (ItemStack stack : slots) {
            if (remaining <= 0) {
                break;
            }
            if (!NbtMatchUtil.matches(stack, target, nbtAware, requiredTag)) {
                continue;
            }
            int taken = Math.min(stack.getCount(), remaining);
            stack.shrink(taken);
            remaining -= taken;
        }
        return remaining;
    }

    private static void insertIntoLiveSlots(NonNullList<ItemStack> slots, ItemStack stackToInsert) {
        if (stackToInsert.isEmpty()) {
            return;
        }

        for (ItemStack slot : slots) {
            if (stackToInsert.isEmpty()) {
                return;
            }
            if (slot.isEmpty() || !ItemStack.isSameItemSameTags(slot, stackToInsert)) {
                continue;
            }

            int max = Math.min(slot.getMaxStackSize(), stackToInsert.getMaxStackSize());
            int room = max - slot.getCount();
            if (room <= 0) {
                continue;
            }

            int moved = Math.min(room, stackToInsert.getCount());
            slot.grow(moved);
            stackToInsert.shrink(moved);
        }

        for (int i = 0; i < slots.size() && !stackToInsert.isEmpty(); i++) {
            if (!slots.get(i).isEmpty()) {
                continue;
            }

            int moved = Math.min(stackToInsert.getCount(), stackToInsert.getMaxStackSize());
            ItemStack placed = stackToInsert.copy();
            placed.setCount(moved);
            slots.set(i, placed);
            stackToInsert.shrink(moved);
        }
    }

    private static void insertIntoSlotCopies(List<ItemStack> slots, ItemStack stackToInsert) {
        if (stackToInsert.isEmpty()) {
            return;
        }

        for (ItemStack slot : slots) {
            if (stackToInsert.isEmpty()) {
                return;
            }
            if (slot.isEmpty() || !ItemStack.isSameItemSameTags(slot, stackToInsert)) {
                continue;
            }

            int max = Math.min(slot.getMaxStackSize(), stackToInsert.getMaxStackSize());
            int room = max - slot.getCount();
            if (room <= 0) {
                continue;
            }

            int moved = Math.min(room, stackToInsert.getCount());
            slot.grow(moved);
            stackToInsert.shrink(moved);
        }

        for (int i = 0; i < slots.size() && !stackToInsert.isEmpty(); i++) {
            ItemStack slot = slots.get(i);
            if (!slot.isEmpty()) {
                continue;
            }

            int moved = Math.min(stackToInsert.getCount(), stackToInsert.getMaxStackSize());
            ItemStack placed = stackToInsert.copy();
            placed.setCount(moved);
            slots.set(i, placed);
            stackToInsert.shrink(moved);
        }
    }
}

