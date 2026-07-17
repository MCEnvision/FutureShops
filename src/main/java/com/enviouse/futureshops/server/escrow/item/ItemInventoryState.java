package com.enviouse.futureshops.server.escrow.item;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class ItemInventoryState {
    private final List<ItemStack> accessibleSlots;
    private final byte[] inventoryHash;

    private ItemInventoryState(List<ItemStack> accessibleSlots) {
        this.accessibleSlots = ItemInventoryHashes.copySlots(accessibleSlots);
        this.inventoryHash = ItemInventoryHashes.hashInventory(
                this.accessibleSlots);
    }

    public static ItemInventoryState of(
            List<ItemStack> mainInventory,
            ItemStack offhand
    ) {
        Objects.requireNonNull(mainInventory, "mainInventory");
        Objects.requireNonNull(offhand, "offhand");
        if (mainInventory.size() != ItemInventorySlot.MAIN_SLOT_COUNT
                || mainInventory.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(
                    "Main inventory must contain exactly 36 slots");
        }
        List<ItemStack> accessible = new ArrayList<>(
                ItemInventorySlot.ACCESSIBLE_SLOT_COUNT);
        accessible.addAll(mainInventory);
        accessible.add(offhand);
        return new ItemInventoryState(accessible);
    }

    public static ItemInventoryState fromCompartments(
            List<ItemStack> mainInventory,
            List<ItemStack> offhand,
            List<ItemStack> armor
    ) {
        Objects.requireNonNull(offhand, "offhand");
        Objects.requireNonNull(armor, "armor");
        if (offhand.size() != 1 || offhand.get(0) == null
                || armor.size() != 4
                || armor.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(
                    "Player inventory compartments are invalid");
        }
        return of(mainInventory, offhand.get(0));
    }

    public static ItemInventoryState capture(Inventory inventory) {
        Objects.requireNonNull(inventory, "inventory");
        return fromCompartments(inventory.items, inventory.offhand,
                inventory.armor);
    }

    public ItemStack stack(ItemInventorySlot slot) {
        Objects.requireNonNull(slot, "slot");
        ItemStack value = accessibleSlots.get(slot.logicalIndex());
        return value.isEmpty() ? ItemStack.EMPTY : value.copy();
    }

    public byte[] inventoryHash() {
        return inventoryHash.clone();
    }

    public byte[] slotHash(ItemInventorySlot slot) {
        return ItemInventoryHashes.hashSlot(
                accessibleSlots.get(Objects.requireNonNull(slot, "slot")
                        .logicalIndex()));
    }

    public static boolean stackMatchesHash(
            ItemStack stack,
            byte[] expectedHash
    ) {
        Objects.requireNonNull(stack, "stack");
        return ItemInventoryHashes.equal(
                ItemInventoryHashes.hashSlot(stack), expectedHash);
    }

    public boolean matchesInventoryHash(byte[] expectedHash) {
        return ItemInventoryHashes.equal(inventoryHash, expectedHash);
    }

    List<ItemStack> mutableCopy() {
        return new ArrayList<>(ItemInventoryHashes.copySlots(accessibleSlots));
    }

    static ItemInventoryState fromAccessibleSlots(List<ItemStack> slots) {
        return new ItemInventoryState(slots);
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof ItemInventoryState other)
                || !ItemInventoryHashes.equal(inventoryHash,
                other.inventoryHash)) {
            return false;
        }
        for (int index = 0; index < accessibleSlots.size(); index++) {
            ItemStack first = accessibleSlots.get(index);
            ItemStack second = other.accessibleSlots.get(index);
            if (first.isEmpty() != second.isEmpty()) {
                return false;
            }
            if (!first.isEmpty() && !Arrays.equals(
                    com.enviouse.futureshops.money.ItemStackSnapshotCodec
                            .encode(first),
                    com.enviouse.futureshops.money.ItemStackSnapshotCodec
                            .encode(second))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(inventoryHash);
    }
}
