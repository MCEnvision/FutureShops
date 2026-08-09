package com.enviouse.futureshops.server.escrow.inventory;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class PlayerInventoryInsertionPlan {
    private final List<ItemStack> beforeSlots;
    private final List<ItemStack> afterSlots;
    private final List<PlayerInventorySlotChange> changes;
    private final byte[] beforeHash;
    private final byte[] afterHash;
    private final int requestedCount;
    private final int insertedCount;

    private PlayerInventoryInsertionPlan(
            List<ItemStack> beforeSlots,
            List<ItemStack> afterSlots,
            int requestedCount,
            int insertedCount
    ) {
        this.beforeSlots = PlayerInventoryHashes.copySlots(beforeSlots);
        this.afterSlots = PlayerInventoryHashes.copySlots(afterSlots);
        this.changes = changes(this.beforeSlots, this.afterSlots);
        this.beforeHash = PlayerInventoryHashes.hashInventory(
                this.beforeSlots);
        this.afterHash = PlayerInventoryHashes.hashInventory(
                this.afterSlots);
        this.requestedCount = requestedCount;
        this.insertedCount = insertedCount;
    }

    public static PlayerInventoryInsertionPlan plan(
            List<ItemStack> currentSlots,
            ItemStack incoming
    ) {
        Objects.requireNonNull(incoming, "incoming");
        if (incoming.isEmpty() || incoming.getCount() <= 0) {
            throw new IllegalArgumentException("Cash delivery stack is empty");
        }
        List<ItemStack> before = PlayerInventoryHashes.copySlots(currentSlots);
        List<ItemStack> after = new ArrayList<>(
                PlayerInventoryHashes.copySlots(currentSlots));
        int remaining = incoming.getCount();
        int maximum = Math.min(64, incoming.getMaxStackSize());
        if (maximum <= 0) {
            throw new IllegalArgumentException("Cash delivery stack cannot be stored");
        }
        for (int index = 0; index < after.size() && remaining > 0; index++) {
            ItemStack current = after.get(index);
            if (current.isEmpty()
                    || !ItemStack.isSameItemSameTags(current, incoming)) {
                continue;
            }
            int capacity = Math.min(64, current.getMaxStackSize())
                    - current.getCount();
            if (capacity <= 0) {
                continue;
            }
            int moved = Math.min(capacity, remaining);
            ItemStack changed = current.copy();
            changed.grow(moved);
            after.set(index, changed);
            remaining -= moved;
        }
        for (int index = 0; index < after.size() && remaining > 0; index++) {
            if (!after.get(index).isEmpty()) {
                continue;
            }
            int moved = Math.min(maximum, remaining);
            ItemStack changed = incoming.copy();
            changed.setCount(moved);
            after.set(index, changed);
            remaining -= moved;
        }
        return new PlayerInventoryInsertionPlan(before, after,
                incoming.getCount(),
                incoming.getCount() - remaining);
    }

    public static List<ItemStack> mainSlots(Inventory inventory) {
        Objects.requireNonNull(inventory, "inventory");
        return PlayerInventoryHashes.copySlots(inventory.items);
    }

    public boolean fullyFits() {
        return insertedCount == requestedCount;
    }

    public int requestedCount() {
        return requestedCount;
    }

    public int insertedCount() {
        return insertedCount;
    }

    public List<PlayerInventorySlotChange> changes() {
        return changes;
    }

    public byte[] beforeHash() {
        return beforeHash.clone();
    }

    public byte[] afterHash() {
        return afterHash.clone();
    }

    List<ItemStack> resultSlots() {
        return PlayerInventoryHashes.copySlots(afterSlots);
    }

    public boolean matchesBefore(Inventory inventory) {
        return matchesChangedSlots(mainSlots(inventory), true);
    }

    public boolean matchesAfter(Inventory inventory) {
        return matchesChangedSlots(mainSlots(inventory), false);
    }

    public void apply(Inventory inventory) {
        if (!fullyFits() || !matchesBefore(inventory)) {
            throw new IllegalStateException(
                    "Player inventory no longer matches the delivery plan");
        }
        replaceChanged(inventory, afterSlots);
    }

    public void restore(Inventory inventory) {
        if (!matchesAfter(inventory)) {
            throw new IllegalStateException(
                    "Player inventory cannot safely restore the delivery plan");
        }
        replaceChanged(inventory, beforeSlots);
    }

    private void replaceChanged(
            Inventory inventory,
            List<ItemStack> slots
    ) {
        for (PlayerInventorySlotChange change : changes) {
            int slot = change.slot();
            inventory.items.set(slot, slots.get(slot).copy());
        }
        inventory.setChanged();
    }

    private boolean matchesChangedSlots(
            List<ItemStack> slots,
            boolean before
    ) {
        for (PlayerInventorySlotChange change : changes) {
            byte[] expected = before
                    ? change.beforeHash() : change.afterHash();
            if (!PlayerInventoryHashes.equal(expected,
                    PlayerInventoryHashes.hashSlot(
                            slots.get(change.slot())))) {
                return false;
            }
        }
        return true;
    }

    private static List<PlayerInventorySlotChange> changes(
            List<ItemStack> before,
            List<ItemStack> after
    ) {
        List<PlayerInventorySlotChange> changes = new ArrayList<>();
        for (int index = 0; index < after.size(); index++) {
            byte[] beforeSlot = PlayerInventoryHashes.hashSlot(
                    before.get(index));
            byte[] afterSlot = PlayerInventoryHashes.hashSlot(
                    after.get(index));
            if (!PlayerInventoryHashes.equal(beforeSlot, afterSlot)) {
                changes.add(new PlayerInventorySlotChange(
                        index, beforeSlot, afterSlot));
            }
        }
        return List.copyOf(changes);
    }
}
