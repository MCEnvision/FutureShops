package com.enviouse.futureshops.server.escrow.item;

import com.enviouse.futureshops.money.ItemStackSnapshotCodec;
import net.minecraft.world.item.ItemStack;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class ItemInventoryBatchPlanner {
    private static final int MAX_ENTRIES = 64;
    private static final int MAX_BATCH_SNAPSHOT_BYTES = 4 * 1024 * 1024;
    private static final int MAX_EXACT_TEMPLATE_BYTES = 4 * 1024 * 1024;

    private ItemInventoryBatchPlanner() {
    }

    public static ItemInventoryMutationPlan plan(
            ItemInventoryState current,
            List<ItemInventoryBatchEntry> requestedEntries
    ) {
        Objects.requireNonNull(current, "current");
        CanonicalBatch batch = canonicalBatch(requestedEntries);
        ItemInventoryMutationDirection direction = batch.direction();
        List<ItemInventoryBatchEntry> entries = batch.entries();
        byte[] fingerprint = fingerprint(direction, entries);
        List<ItemStack> trial = current.mutableCopy();
        boolean exactMatching = entries.stream().anyMatch(entry ->
                entry.matcher().mode() == ItemMatchMode.EXACT);
        List<SlotEvidence> evidence = captureEvidence(
                trial, exactMatching);
        List<ItemInventoryAllocation> allocations = new ArrayList<>();
        AllocationBudget allocationBudget = new AllocationBudget();
        Map<UUID, Integer> fulfilled = new LinkedHashMap<>();
        boolean complete = true;
        for (ItemInventoryBatchEntry entry : entries) {
            int count = direction == ItemInventoryMutationDirection.INSERT
                    ? insert(trial, evidence, entry, allocations,
                    allocationBudget, exactMatching)
                    : extract(trial, evidence, entry, allocations,
                    allocationBudget);
            fulfilled.put(entry.entryId(), count);
            complete &= count == entry.count();
        }
        if (!complete) {
            return new ItemInventoryMutationPlan(
                    direction == ItemInventoryMutationDirection.INSERT
                            ? ItemInventoryPlanStatus.INSUFFICIENT_CAPACITY
                            : ItemInventoryPlanStatus.INSUFFICIENT_ITEMS,
                    direction, current, current, entries, List.of(),
                    List.of(), fulfilled, fingerprint);
        }
        ItemInventoryState after = ItemInventoryState.fromAccessibleSlots(
                trial);
        List<ItemInventorySlotChange> changes = changes(current, after);
        return new ItemInventoryMutationPlan(
                ItemInventoryPlanStatus.APPLICABLE, direction, current, after,
                entries, allocations, changes, fulfilled, fingerprint);
    }

    public static byte[] fingerprint(
            List<ItemInventoryBatchEntry> requestedEntries
    ) {
        CanonicalBatch batch = canonicalBatch(requestedEntries);
        return fingerprint(batch.direction(), batch.entries());
    }

    private static CanonicalBatch canonicalBatch(
            List<ItemInventoryBatchEntry> requestedEntries
    ) {
        Objects.requireNonNull(requestedEntries, "requestedEntries");
        if (requestedEntries.isEmpty()
                || requestedEntries.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException(
                    "Item inventory batch entry count is invalid");
        }
        ItemInventoryMutationDirection direction = Objects.requireNonNull(
                requestedEntries.get(0), "entry").direction();
        Set<UUID> identities = new HashSet<>();
        List<ItemInventoryBatchEntry> entries = new ArrayList<>(
                requestedEntries.size());
        long snapshotBytes = 0L;
        long exactTemplateBytes = 0L;
        for (ItemInventoryBatchEntry entry : requestedEntries) {
            Objects.requireNonNull(entry, "entry");
            if (entry.direction() != direction
                    || !identities.add(entry.entryId())) {
                throw new IllegalArgumentException(
                        "Item inventory batch entries conflict");
            }
            snapshotBytes = Math.addExact(snapshotBytes,
                    entry.insertionSnapshot().length);
            if (snapshotBytes > MAX_BATCH_SNAPSHOT_BYTES) {
                throw new IllegalArgumentException(
                        "Item inventory batch snapshots exceed their limit");
            }
            if (entry.matcher().mode() == ItemMatchMode.EXACT) {
                exactTemplateBytes = Math.addExact(exactTemplateBytes,
                        entry.matcher().exactSnapshotBytes());
                if (exactTemplateBytes > MAX_EXACT_TEMPLATE_BYTES) {
                    throw new IllegalArgumentException(
                            "Item inventory exact templates exceed their limit");
                }
            }
            entries.add(entry);
        }
        entries.sort(entryComparator(direction));
        return new CanonicalBatch(direction, List.copyOf(entries));
    }

    private static int insert(
            List<ItemStack> slots,
            List<SlotEvidence> evidence,
            ItemInventoryBatchEntry entry,
            List<ItemInventoryAllocation> allocations,
            AllocationBudget allocationBudget,
            boolean exactMatching
    ) {
        ItemStack incoming = ItemStackSnapshotCodec.decode(
                entry.insertionSnapshot());
        SlotEvidence incomingEvidence = SlotEvidence.capture(
                incoming, exactMatching);
        if (incomingEvidence.persistentCapabilities()) {
            return insertAtomic(slots, evidence, entry, allocations,
                    allocationBudget, incoming, incomingEvidence);
        }
        int remaining = incoming.getCount();
        for (int index = 0; index < slots.size() && remaining > 0; index++) {
            ItemStack current = slots.get(index);
            SlotEvidence currentEvidence = evidence.get(index);
            if (current.isEmpty() || currentEvidence == null
                    || !currentEvidence.matches(entry.matcher())) {
                continue;
            }
            int capacity = Math.min(current.getMaxStackSize(), 127)
                    - current.getCount();
            if (capacity <= 0) {
                continue;
            }
            int moved = Math.min(capacity, remaining);
            ItemStack changed = current.copy();
            changed.grow(moved);
            slots.set(index, changed);
            appendAllocation(allocations, allocationBudget,
                    entry, index, incoming, moved);
            remaining -= moved;
        }
        for (int index = 0; index < slots.size() && remaining > 0; index++) {
            if (!slots.get(index).isEmpty()) {
                continue;
            }
            int maximum = Math.min(incoming.getMaxStackSize(), 127);
            if (maximum <= 0) {
                throw new IllegalArgumentException(
                        "Item insertion stack cannot be stored");
            }
            int moved = Math.min(maximum, remaining);
            ItemStack changed = ItemStackSnapshotEvidence.exactPortion(
                    incoming, moved);
            slots.set(index, changed);
            evidence.set(index, incomingEvidence);
            appendAllocation(allocations, allocationBudget,
                    entry, index, incoming, moved);
            remaining -= moved;
        }
        return Math.subtractExact(entry.count(), remaining);
    }

    private static int extract(
            List<ItemStack> slots,
            List<SlotEvidence> evidence,
            ItemInventoryBatchEntry entry,
            List<ItemInventoryAllocation> allocations,
            AllocationBudget allocationBudget
    ) {
        List<Integer> ordinarySlots = new ArrayList<>();
        List<Integer> atomicSlots = new ArrayList<>();
        int ordinaryUnits = 0;
        int totalMatchingUnits = 0;
        for (int index = 0; index < slots.size(); index++) {
            ItemStack current = slots.get(index);
            SlotEvidence currentEvidence = evidence.get(index);
            if (currentEvidence == null
                    || !currentEvidence.matches(entry.matcher())) {
                continue;
            }
            totalMatchingUnits = Math.addExact(totalMatchingUnits,
                    current.getCount());
            if (currentEvidence.persistentCapabilities()) {
                atomicSlots.add(index);
            } else {
                ordinarySlots.add(index);
                ordinaryUnits = Math.addExact(ordinaryUnits,
                        current.getCount());
            }
        }
        if (entry.count() > totalMatchingUnits) {
            return 0;
        }
        boolean[] selectedAtomic = selectAtomicSlots(
                slots, atomicSlots, entry.count(), ordinaryUnits);
        if (selectedAtomic == null) {
            return 0;
        }
        int atomicUnits = 0;
        for (int index = 0; index < selectedAtomic.length; index++) {
            if (selectedAtomic[index]) {
                atomicUnits = Math.addExact(atomicUnits,
                        slots.get(atomicSlots.get(index)).getCount());
            }
        }
        int remainingOrdinary = Math.subtractExact(
                entry.count(), atomicUnits);
        for (int index : ordinarySlots) {
            if (remainingOrdinary == 0) {
                break;
            }
            ItemStack current = slots.get(index);
            int moved = Math.min(current.getCount(), remainingOrdinary);
            appendAllocation(allocations, allocationBudget,
                    entry, index, current, moved);
            if (moved == current.getCount()) {
                slots.set(index, ItemStack.EMPTY);
                evidence.set(index, null);
            } else {
                ItemStack changed = current.copy();
                changed.shrink(moved);
                slots.set(index, changed);
            }
            remainingOrdinary -= moved;
        }
        for (int index = 0; index < selectedAtomic.length; index++) {
            if (!selectedAtomic[index]) {
                continue;
            }
            int slotIndex = atomicSlots.get(index);
            ItemStack current = slots.get(slotIndex);
            appendAllocation(allocations, allocationBudget,
                    entry, slotIndex, current, current.getCount());
            slots.set(slotIndex, ItemStack.EMPTY);
            evidence.set(slotIndex, null);
        }
        return entry.count();
    }

    private static int insertAtomic(
            List<ItemStack> slots,
            List<SlotEvidence> evidence,
            ItemInventoryBatchEntry entry,
            List<ItemInventoryAllocation> allocations,
            AllocationBudget budget,
            ItemStack incoming,
            SlotEvidence incomingEvidence
    ) {
        if (incoming.getCount() > Math.min(incoming.getMaxStackSize(), 127)) {
            return 0;
        }
        for (int index = 0; index < slots.size(); index++) {
            if (!slots.get(index).isEmpty()) {
                continue;
            }
            appendAllocation(allocations, budget, entry, index,
                    incoming, incoming.getCount());
            slots.set(index, incoming.copy());
            evidence.set(index, incomingEvidence);
            return incoming.getCount();
        }
        return 0;
    }

    private static boolean[] selectAtomicSlots(
            List<ItemStack> slots,
            List<Integer> atomicSlots,
            int requestedUnits,
            int ordinaryUnits
    ) {
        boolean[] reachable = new boolean[requestedUnits + 1];
        int[] previousSum = new int[requestedUnits + 1];
        int[] previousSlot = new int[requestedUnits + 1];
        java.util.Arrays.fill(previousSum, -1);
        java.util.Arrays.fill(previousSlot, -1);
        reachable[0] = true;
        for (int slot = 0; slot < atomicSlots.size(); slot++) {
            int units = slots.get(atomicSlots.get(slot)).getCount();
            if (units > requestedUnits) {
                continue;
            }
            for (int sum = requestedUnits; sum >= units; sum--) {
                if (!reachable[sum] && reachable[sum - units]) {
                    reachable[sum] = true;
                    previousSum[sum] = sum - units;
                    previousSlot[sum] = slot;
                }
            }
        }
        int minimumAtomic = Math.max(0,
                Math.subtractExact(requestedUnits, ordinaryUnits));
        int selectedUnits = -1;
        for (int sum = minimumAtomic; sum <= requestedUnits; sum++) {
            if (reachable[sum]
                    && requestedUnits - sum <= ordinaryUnits) {
                selectedUnits = sum;
                break;
            }
        }
        if (selectedUnits < 0) {
            return null;
        }
        boolean[] selected = new boolean[atomicSlots.size()];
        while (selectedUnits > 0) {
            int slot = previousSlot[selectedUnits];
            if (slot < 0) {
                throw new IllegalStateException(
                        "Item atomic allocation selection is incomplete");
            }
            selected[slot] = true;
            selectedUnits = previousSum[selectedUnits];
        }
        return selected;
    }

    private static List<SlotEvidence> captureEvidence(
            List<ItemStack> slots,
            boolean exactMatching
    ) {
        List<SlotEvidence> evidence = new ArrayList<>(slots.size());
        for (ItemStack stack : slots) {
            evidence.add(stack.isEmpty()
                    ? null
                    : SlotEvidence.capture(stack, exactMatching));
        }
        return evidence;
    }

    private record SlotEvidence(
            String registryItemId,
            String exactFingerprint,
            boolean persistentCapabilities
    ) {
        private static SlotEvidence capture(
                ItemStack stack,
                boolean exactMatching
        ) {
            return new SlotEvidence(
                    ItemStackSnapshotEvidence.registryId(stack),
                    exactMatching
                            ? ItemInputMatcher.exactFingerprint(stack)
                            : "",
                    ItemStackSnapshotEvidence.hasPersistentCapabilities(
                            stack));
        }

        private boolean matches(ItemInputMatcher matcher) {
            return matcher.matchesEvidence(
                    registryItemId, exactFingerprint);
        }
    }

    private static ItemInventoryAllocation allocation(
            ItemInventoryBatchEntry entry,
            int logicalSlot,
            ItemStack exactSource,
            int count
    ) {
        ItemStack portion = ItemStackSnapshotEvidence.exactPortion(
                exactSource, count);
        return new ItemInventoryAllocation(entry.entryId(),
                ItemInventorySlot.fromLogicalIndex(logicalSlot), count,
                ItemStackSnapshotCodec.encode(portion));
    }

    private static void appendAllocation(
            List<ItemInventoryAllocation> allocations,
            AllocationBudget budget,
            ItemInventoryBatchEntry entry,
            int logicalSlot,
            ItemStack exactSource,
            int count
    ) {
        budget.requireAnotherAllocation();
        ItemInventoryAllocation allocation = allocation(
                entry, logicalSlot, exactSource, count);
        budget.accept(allocation.actualStackSnapshot().length);
        allocations.add(allocation);
    }

    private static final class AllocationBudget {
        private static final long MAX_ALLOCATION_BYTES =
                ItemInventoryMutationReceiptCodec.MAX_ENCODED_BYTES
                        - 58L
                        - ItemInventoryMutationTokenCodec.MAX_ENCODED_BYTES;

        private int count;
        private long encodedBytes;

        private void requireAnotherAllocation() {
            if (count >= ItemInventoryMutationReceipt.MAX_ALLOCATIONS) {
                throw new IllegalArgumentException(
                        "Item inventory allocation count exceeds its limit");
            }
        }

        private void accept(int snapshotBytes) {
            long next = Math.addExact(encodedBytes,
                    Math.addExact(28L, snapshotBytes));
            if (next > MAX_ALLOCATION_BYTES) {
                throw new IllegalArgumentException(
                        "Item inventory allocations exceed their byte limit");
            }
            encodedBytes = next;
            count++;
        }
    }

    private static List<ItemInventorySlotChange> changes(
            ItemInventoryState before,
            ItemInventoryState after
    ) {
        List<ItemInventorySlotChange> changes = new ArrayList<>();
        for (int index = 0;
             index < ItemInventorySlot.ACCESSIBLE_SLOT_COUNT;
             index++) {
            ItemInventorySlot slot = ItemInventorySlot.fromLogicalIndex(index);
            byte[] beforeHash = before.slotHash(slot);
            byte[] afterHash = after.slotHash(slot);
            if (!ItemInventoryHashes.equal(beforeHash, afterHash)) {
                changes.add(new ItemInventorySlotChange(slot, beforeHash,
                        afterHash));
            }
        }
        return List.copyOf(changes);
    }

    private static Comparator<ItemInventoryBatchEntry> entryComparator(
            ItemInventoryMutationDirection direction
    ) {
        Comparator<ItemInventoryBatchEntry> identity = Comparator.comparing(
                entry -> entry.entryId().toString());
        if (direction == ItemInventoryMutationDirection.EXTRACT) {
            return Comparator.comparingInt(
                            (ItemInventoryBatchEntry entry) ->
                                    entry.matcher().mode()
                                            == ItemMatchMode.EXACT ? 0 : 1)
                    .thenComparing(identity);
        }
        return identity;
    }

    private static byte[] fingerprint(
            ItemInventoryMutationDirection direction,
            List<ItemInventoryBatchEntry> entries
    ) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(0x49424154);
            output.writeInt(1);
            output.writeByte(direction.wireCode());
            output.writeInt(entries.size());
            for (ItemInventoryBatchEntry entry : entries) {
                output.writeLong(entry.entryId().getMostSignificantBits());
                output.writeLong(entry.entryId().getLeastSignificantBits());
                output.writeInt(entry.count());
                writeString(output, entry.matcher().registryItemId());
                output.writeByte(entry.matcher().mode().fingerprintCode());
                writeString(output, entry.matcher().fingerprint());
                byte[] insertion = entry.insertionSnapshot();
                output.writeInt(insertion.length);
                output.write(insertion);
            }
            output.flush();
            return ItemInventoryHashes.sha256(bytes.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to fingerprint item inventory batch", exception);
        }
    }

    private static void writeString(DataOutputStream output, String value)
            throws IOException {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(encoded.length);
        output.write(encoded);
    }

    private record CanonicalBatch(
            ItemInventoryMutationDirection direction,
            List<ItemInventoryBatchEntry> entries
    ) {
    }
}
