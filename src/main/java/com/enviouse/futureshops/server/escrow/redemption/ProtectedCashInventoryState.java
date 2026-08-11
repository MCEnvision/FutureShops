package com.enviouse.futureshops.server.escrow.redemption;

import com.enviouse.futureshops.money.InternalBillInventoryPlanner;
import com.enviouse.futureshops.money.ItemStackSnapshotCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ProtectedCashInventoryState {
    public static final int MAIN_SLOT_COUNT = 36;
    public static final int OFFHAND_SLOT_COUNT = 1;
    public static final int MAX_ENCODED_BYTES = 1_200_000;

    private static final int MAGIC = 0x46534349;
    private static final int SCHEMA = 1;
    private static final int OFFHAND_NBT_SLOT = 150;

    private final List<ItemStack> main;
    private final List<ItemStack> offhand;
    private final byte[] encoded;
    private final byte[] hash;

    private ProtectedCashInventoryState(List<ItemStack> main,
                                        List<ItemStack> offhand) {
        this.main = copy(main, MAIN_SLOT_COUNT, "main");
        this.offhand = copy(offhand, OFFHAND_SLOT_COUNT, "offhand");
        this.encoded = encodeSlots(this.main, this.offhand);
        this.hash = ProtectedCashRedemptionSupport.sha256(encoded);
    }

    public static ProtectedCashInventoryState capture(Inventory inventory) {
        Objects.requireNonNull(inventory, "inventory");
        return new ProtectedCashInventoryState(inventory.items,
                inventory.offhand);
    }

    public static ProtectedCashInventoryState fromPlayerInventoryTag(
            ListTag inventory
    ) {
        Objects.requireNonNull(inventory, "inventory");
        List<ItemStack> main = empty(MAIN_SLOT_COUNT);
        List<ItemStack> offhand = empty(OFFHAND_SLOT_COUNT);
        boolean[] seenMain = new boolean[MAIN_SLOT_COUNT];
        boolean seenOffhand = false;
        for (int index = 0; index < inventory.size(); index++) {
            CompoundTag entry = inventory.getCompound(index);
            if (!entry.contains("Slot", Tag.TAG_BYTE)) {
                throw new IllegalArgumentException(
                        "Protected cash inventory slot is missing");
            }
            int slot = entry.getByte("Slot") & 255;
            if (slot >= 0 && slot < MAIN_SLOT_COUNT) {
                if (seenMain[slot]) {
                    throw new IllegalArgumentException(
                            "Protected cash main inventory slot is duplicated");
                }
                seenMain[slot] = true;
                main.set(slot, canonicalStack(entry));
            } else if (slot == OFFHAND_NBT_SLOT) {
                if (seenOffhand) {
                    throw new IllegalArgumentException(
                            "Protected cash offhand slot is duplicated");
                }
                seenOffhand = true;
                offhand.set(0, canonicalStack(entry));
            }
        }
        return new ProtectedCashInventoryState(main, offhand);
    }

    public static ProtectedCashInventoryState decode(byte[] encoded) {
        if (encoded == null || encoded.length == 0
                || encoded.length > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException(
                    "Protected cash inventory state size is invalid");
        }
        ByteArrayInputStream bytes = new ByteArrayInputStream(encoded);
        try (DataInputStream input = new DataInputStream(bytes)) {
            if (input.readInt() != MAGIC || input.readInt() != SCHEMA
                    || input.readInt() != MAIN_SLOT_COUNT) {
                throw new IllegalArgumentException(
                        "Protected cash inventory state header is invalid");
            }
            List<ItemStack> main = readSlots(input, bytes, MAIN_SLOT_COUNT);
            if (input.readInt() != OFFHAND_SLOT_COUNT) {
                throw new IllegalArgumentException(
                        "Protected cash offhand count is invalid");
            }
            List<ItemStack> offhand = readSlots(input, bytes,
                    OFFHAND_SLOT_COUNT);
            if (bytes.available() != 0) {
                throw new IllegalArgumentException(
                        "Protected cash inventory state has trailing data");
            }
            ProtectedCashInventoryState state =
                    new ProtectedCashInventoryState(main, offhand);
            if (!Arrays.equals(encoded, state.encoded)) {
                throw new IllegalArgumentException(
                        "Protected cash inventory state is not canonical");
            }
            return state;
        } catch (EOFException exception) {
            throw new IllegalArgumentException(
                    "Protected cash inventory state is truncated", exception);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException(
                    "Protected cash inventory state is invalid", exception);
        }
    }

    public byte[] encode() {
        return encoded.clone();
    }

    public byte[] hash() {
        return hash.clone();
    }

    public ItemStack stack(InternalBillInventoryPlanner.SlotIdentity slot) {
        Objects.requireNonNull(slot, "slot");
        List<ItemStack> source = slot.container()
                == InternalBillInventoryPlanner.Container.MAIN
                ? main : offhand;
        if (slot.index() >= source.size()) {
            throw new IllegalArgumentException(
                    "Protected cash inventory slot is outside its container");
        }
        return source.get(slot.index()).copy();
    }

    public boolean matches(Inventory inventory) {
        ProtectedCashInventoryState current = capture(inventory);
        if (main.size() != current.main.size()
                || offhand.size() != current.offhand.size()) {
            return false;
        }
        for (int index = 0; index < main.size(); index++) {
            if (!ItemStackSnapshotCodec.sameIdentity(main.get(index),
                    current.main.get(index))) {
                return false;
            }
        }
        for (int index = 0; index < offhand.size(); index++) {
            if (!ItemStackSnapshotCodec.sameIdentity(offhand.get(index),
                    current.offhand.get(index))) {
                return false;
            }
        }
        return true;
    }

    public RemovalResult removeExact(
            Inventory inventory,
            ProtectedCashRedemptionReservation reservation,
            Instant occurredAt
    ) {
        List<RemovalPortion> portions = reservation.plan().portions().stream()
                .map(portion -> new RemovalPortion(portion.slot(),
                        portion.originalStackCount(),
                        portion.selectedCount(),
                        portion.exactStackSnapshot()))
                .toList();
        return removeExact(inventory, reservation.playerId(),
                reservation.transactionId(), reservation.reservationId(),
                reservation.inventoryBeforeHash(),
                ProtectedCashRedemptionSettlement
                        .inventoryMutationRequestKey(
                                reservation.transactionId(),
                                reservation.destinationAccount()),
                portions, occurredAt);
    }

    public RemovalResult removeExact(
            Inventory inventory,
            UUID playerId,
            UUID transactionId,
            UUID reservationId,
            byte[] expectedBeforeHash,
            String mutationRequestKey,
            List<RemovalPortion> portions,
            Instant occurredAt
    ) {
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(reservationId, "reservationId");
        Objects.requireNonNull(expectedBeforeHash, "expectedBeforeHash");
        Objects.requireNonNull(mutationRequestKey, "mutationRequestKey");
        portions = List.copyOf(Objects.requireNonNull(
                portions, "portions"));
        Objects.requireNonNull(occurredAt, "occurredAt");
        ProtectedCashInventoryState current = capture(inventory);
        if (!equals(current)
                || !ProtectedCashRedemptionSupport.equal(hash,
                expectedBeforeHash)) {
            throw new IllegalStateException(
                    "Protected cash inventory changed before removal");
        }
        List<ItemStack> afterMain = mutableCopy(main);
        List<ItemStack> afterOffhand = mutableCopy(offhand);
        List<ProtectedCashRedemptionSettlement.SlotMutation> mutations =
                new ArrayList<>();
        for (RemovalPortion portion : portions) {
            ItemStack before = stack(portion.slot());
            if (!Arrays.equals(ItemStackSnapshotCodec.encode(before),
                    portion.exactStackSnapshot())
                    || before.getCount() != portion.originalStackCount()) {
                throw new IllegalStateException(
                        "Protected cash planned slot changed before removal");
            }
            ItemStack after = before.copy();
            after.shrink(portion.selectedCount());
            if (after.isEmpty()) {
                after = ItemStack.EMPTY;
            }
            List<ItemStack> target = portion.slot().container()
                    == InternalBillInventoryPlanner.Container.MAIN
                    ? afterMain : afterOffhand;
            target.set(portion.slot().index(), after);
            mutations.add(new ProtectedCashRedemptionSettlement.SlotMutation(
                    portion.slot(), portion.selectedCount(),
                    portion.exactStackSnapshot(), after.isEmpty()
                    ? new byte[0] : ItemStackSnapshotCodec.encode(after)));
        }
        ProtectedCashInventoryState after = new ProtectedCashInventoryState(
                afterMain, afterOffhand);
        ProtectedCashRedemptionSettlement.InventoryMutationReceipt receipt =
                ProtectedCashRedemptionSettlement.InventoryMutationReceipt
                        .create(playerId, transactionId, reservationId,
                                mutationRequestKey,
                                mutations, hash, after.hash, occurredAt);
        replace(inventory, afterMain, afterOffhand);
        return new RemovalResult(after, receipt);
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ProtectedCashInventoryState other
                && Arrays.equals(encoded, other.encoded);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(encoded);
    }

    private static byte[] encodeSlots(List<ItemStack> main,
                                      List<ItemStack> offhand) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(MAGIC);
            output.writeInt(SCHEMA);
            output.writeInt(main.size());
            writeSlots(output, main);
            output.writeInt(offhand.size());
            writeSlots(output, offhand);
            output.flush();
            byte[] value = bytes.toByteArray();
            if (value.length > MAX_ENCODED_BYTES) {
                throw new IllegalArgumentException(
                        "Protected cash inventory state exceeds its limit");
            }
            return value;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to encode protected cash inventory state",
                    exception);
        }
    }

    private static void writeSlots(DataOutputStream output,
                                   List<ItemStack> slots) throws IOException {
        for (int index = 0; index < slots.size(); index++) {
            output.writeInt(index);
            ItemStack stack = slots.get(index);
            byte[] snapshot = stack.isEmpty() ? new byte[0]
                    : ItemStackSnapshotCodec.encode(stack);
            ProtectedCashRedemptionSupport.writeBytes(output, snapshot,
                    ItemStackSnapshotCodec.MAXIMUM_BYTES,
                    "Protected cash inventory slot");
        }
    }

    private static List<ItemStack> readSlots(DataInputStream input,
                                             ByteArrayInputStream bytes,
                                             int count) throws IOException {
        List<ItemStack> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            if (input.readInt() != index) {
                throw new IllegalArgumentException(
                        "Protected cash inventory slot order is invalid");
            }
            byte[] snapshot = ProtectedCashRedemptionSupport.readBytes(
                    input, bytes, ItemStackSnapshotCodec.MAXIMUM_BYTES,
                    "Protected cash inventory slot");
            result.add(snapshot.length == 0 ? ItemStack.EMPTY
                    : ItemStackSnapshotCodec.decode(snapshot));
        }
        return List.copyOf(result);
    }

    private static ItemStack canonicalStack(CompoundTag entry) {
        ItemStack stack = ItemStack.of(entry);
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        byte[] snapshot = ItemStackSnapshotCodec.encode(stack);
        return ItemStackSnapshotCodec.decode(snapshot);
    }

    private static List<ItemStack> copy(List<ItemStack> slots,
                                        int expected,
                                        String label) {
        Objects.requireNonNull(slots, label);
        if (slots.size() != expected || slots.stream().anyMatch(
                Objects::isNull)) {
            throw new IllegalArgumentException(
                    "Protected cash " + label + " inventory shape is invalid");
        }
        return List.copyOf(mutableCopy(slots));
    }

    private static List<ItemStack> mutableCopy(List<ItemStack> slots) {
        List<ItemStack> result = new ArrayList<>(slots.size());
        for (ItemStack stack : slots) {
            result.add(stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        }
        return result;
    }

    private static List<ItemStack> empty(int count) {
        List<ItemStack> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            result.add(ItemStack.EMPTY);
        }
        return result;
    }

    private static void replace(Inventory inventory,
                                List<ItemStack> main,
                                List<ItemStack> offhand) {
        for (int index = 0; index < MAIN_SLOT_COUNT; index++) {
            inventory.items.set(index, main.get(index).copy());
        }
        inventory.offhand.set(0, offhand.get(0).copy());
        inventory.setChanged();
    }

    public record RemovalResult(
            ProtectedCashInventoryState afterInventory,
            ProtectedCashRedemptionSettlement.InventoryMutationReceipt receipt
    ) {
        public RemovalResult {
            Objects.requireNonNull(afterInventory, "afterInventory");
            Objects.requireNonNull(receipt, "receipt");
        }
    }

    public record RemovalPortion(
            InternalBillInventoryPlanner.SlotIdentity slot,
            int originalStackCount,
            int selectedCount,
            byte[] exactStackSnapshot
    ) {
        public RemovalPortion {
            Objects.requireNonNull(slot, "slot");
            exactStackSnapshot = Objects.requireNonNull(
                    exactStackSnapshot, "exactStackSnapshot").clone();
            if (originalStackCount <= 0 || selectedCount <= 0
                    || selectedCount > originalStackCount
                    || exactStackSnapshot.length == 0) {
                throw new IllegalArgumentException(
                        "Inventory removal portion is invalid");
            }
        }

        @Override
        public byte[] exactStackSnapshot() {
            return exactStackSnapshot.clone();
        }
    }
}
