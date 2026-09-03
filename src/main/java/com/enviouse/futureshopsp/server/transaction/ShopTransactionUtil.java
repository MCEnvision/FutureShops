package com.enviouse.futureshopsp.server.transaction;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;

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
        return BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(itemId)).orElse(null);
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
        if (!canFit(inventory, stacks)) {
            return false;
        }
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
    public static int countItems(Inventory inventory, Item target, boolean nbtAware,  DataComponentPatch requiredTag) {
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

    public static List<ItemStack> snapshotMatchingItems(Inventory inventory, Item target, int quantity,
                                                         boolean nbtAware, DataComponentPatch requiredTag) {
        if (quantity <= 0 || countItems(inventory, target, nbtAware, requiredTag) < quantity) {
            return List.of();
        }
        List<ItemStack> result = new ArrayList<>();
        int remaining = quantity;
        remaining = snapshotFromSlots(inventory.items, target, remaining, nbtAware, requiredTag, result);
        snapshotFromSlots(inventory.offhand, target, remaining, nbtAware, requiredTag, result);
        return result;
    }

    private static int snapshotFromSlots(NonNullList<ItemStack> slots, Item target, int remaining,
                                          boolean nbtAware, DataComponentPatch requiredTag,
                                          List<ItemStack> result) {
        for (ItemStack stack : slots) {
            if (remaining <= 0) {
                break;
            }
            if (!NbtMatchUtil.matches(stack, target, nbtAware, requiredTag)) {
                continue;
            }
            int copied = Math.min(stack.getCount(), remaining);
            ItemStack copy = stack.copy();
            copy.setCount(copied);
            result.add(copy);
            remaining -= copied;
        }
        return remaining;
    }

    public static boolean removeItems(Inventory inventory, Item target, int quantity) {
        return removeItems(inventory, target, quantity, false, null);
    }

    /**
     * NBT-aware item removal. When nbtAware is true, only items matching the required NBT tag are removed.
     */
    public static boolean removeItems(Inventory inventory, Item target, int quantity, boolean nbtAware,  DataComponentPatch requiredTag) {
        int remaining = quantity;
        remaining = shrinkMatchingStacks(inventory.items, target, remaining, nbtAware, requiredTag);
        remaining = shrinkMatchingStacks(inventory.offhand, target, remaining, nbtAware, requiredTag);
        return remaining == 0;
    }

    private static int shrinkMatchingStacks(NonNullList<ItemStack> slots, Item target, int remaining,
                                            boolean nbtAware,  DataComponentPatch requiredTag) {
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

    /**
     * NBT-preserving variant of {@link #removeItems}. Removes up to {@code quantity} items
     * matching the target (+ optional NBT filter) from the player's inventory AND returns
     * split copies of the actual stacks that were consumed, NBT intact. Used by the barter
     * payment path so the owner's storage receives the exact enchanted/tagged items the
     * buyer paid with rather than freshly-minted plain stacks.
     *
     * <p>If the full quantity cannot be matched, no items are removed and an empty list is
     * returned — call sites should treat an empty result identically to the old
     * {@code removeItems() == false} branch.
     */
    public static List<ItemStack> collectAndRemoveItems(Inventory inventory, Item target, int quantity,
                                                        boolean nbtAware,  DataComponentPatch requiredTag) {
        if (quantity <= 0) return new ArrayList<>();
        int available = countItems(inventory, target, nbtAware, requiredTag);
        if (available < quantity) return new ArrayList<>();

        List<ItemStack> collected = new ArrayList<>();
        int remaining = quantity;
        remaining = collectFromSlots(inventory.items, target, remaining, nbtAware, requiredTag, collected);
        remaining = collectFromSlots(inventory.offhand, target, remaining, nbtAware, requiredTag, collected);
        // Guarded by the countItems pre-check above, but defensively roll back if anything slipped.
        if (remaining != 0) {
            insertIntoInventory(inventory, collected);
            return new ArrayList<>();
        }
        return collected;
    }

    private static int collectFromSlots(NonNullList<ItemStack> slots, Item target, int remaining,
                                        boolean nbtAware,  DataComponentPatch requiredTag,
                                        List<ItemStack> collected) {
        for (ItemStack stack : slots) {
            if (remaining <= 0) break;
            if (!NbtMatchUtil.matches(stack, target, nbtAware, requiredTag)) continue;
            int taken = Math.min(stack.getCount(), remaining);
            ItemStack portion = stack.copy();
            portion.setCount(taken);
            collected.add(portion);
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
            if (slot.isEmpty() || !ItemStack.isSameItemSameComponents(slot, stackToInsert)) {
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
            if (slot.isEmpty() || !ItemStack.isSameItemSameComponents(slot, stackToInsert)) {
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
