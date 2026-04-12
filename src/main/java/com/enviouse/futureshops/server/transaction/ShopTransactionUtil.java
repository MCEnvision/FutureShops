package com.enviouse.futureshops.server.transaction;

import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/** Shared low-level helpers for authoritative shop transactions. */
public final class ShopTransactionUtil {
    public static final int MAX_QUANTITY = 64;

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
        int total = 0;
        for (ItemStack stack : inventory.items) {
            if (stack.getItem() == target) {
                total += stack.getCount();
            }
        }
        for (ItemStack stack : inventory.offhand) {
            if (stack.getItem() == target) {
                total += stack.getCount();
            }
        }
        return total;
    }

    public static boolean removeItems(Inventory inventory, Item target, int quantity) {
        int remaining = quantity;
        remaining = shrinkMatchingStacks(inventory.items, target, remaining);
        remaining = shrinkMatchingStacks(inventory.offhand, target, remaining);
        return remaining == 0;
    }

    private static int shrinkMatchingStacks(NonNullList<ItemStack> slots, Item target, int remaining) {
        for (ItemStack stack : slots) {
            if (remaining <= 0) {
                break;
            }
            if (stack.getItem() != target) {
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

