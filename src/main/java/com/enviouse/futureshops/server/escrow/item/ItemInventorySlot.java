package com.enviouse.futureshops.server.escrow.item;

public record ItemInventorySlot(int serializedSlot)
        implements Comparable<ItemInventorySlot> {
    public static final int MAIN_SLOT_COUNT = 36;
    public static final int OFFHAND_SERIALIZED_SLOT = 150;
    public static final int ACCESSIBLE_SLOT_COUNT = MAIN_SLOT_COUNT + 1;

    public ItemInventorySlot {
        if ((serializedSlot < 0 || serializedSlot >= MAIN_SLOT_COUNT)
                && serializedSlot != OFFHAND_SERIALIZED_SLOT) {
            throw new IllegalArgumentException(
                    "Item inventory slot is not main inventory or offhand");
        }
    }

    public static ItemInventorySlot main(int index) {
        return new ItemInventorySlot(index);
    }

    public static ItemInventorySlot offhand() {
        return new ItemInventorySlot(OFFHAND_SERIALIZED_SLOT);
    }

    public boolean isOffhand() {
        return serializedSlot == OFFHAND_SERIALIZED_SLOT;
    }

    int logicalIndex() {
        return isOffhand() ? MAIN_SLOT_COUNT : serializedSlot;
    }

    static ItemInventorySlot fromLogicalIndex(int index) {
        if (index < 0 || index >= ACCESSIBLE_SLOT_COUNT) {
            throw new IllegalArgumentException(
                    "Item inventory logical slot is invalid");
        }
        return index == MAIN_SLOT_COUNT ? offhand() : main(index);
    }

    @Override
    public int compareTo(ItemInventorySlot other) {
        return Integer.compare(logicalIndex(), other.logicalIndex());
    }
}
