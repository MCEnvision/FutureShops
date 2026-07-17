package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.init.ModItems;
import com.enviouse.futureshops.money.CurrencyMath;
import com.enviouse.futureshops.money.InternalBillInventoryPlanner;
import com.enviouse.futureshops.money.ItemStackSnapshotCodec;
import com.enviouse.futureshops.money.PhysicalCurrencyAdapter;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ForeignCashDepositPlan(
        String providerId,
        String providerSignature,
        long amountMinorUnits,
        List<Portion> portions
) {
    public static final int MAX_PORTIONS = 37;
    public static final int MAX_TOTAL_SNAPSHOT_BYTES = 4_194_304;

    public ForeignCashDepositPlan {
        providerId = requireText(providerId, 256, "provider");
        providerSignature = requireText(providerSignature, 128,
                "provider signature");
        if (providerId.equals("futureshops")
                || !providerSignature.matches("[0-9a-f]{64}")
                || amountMinorUnits <= 0L) {
            throw new IllegalArgumentException(
                    "Foreign cash deposit provider is invalid");
        }
        portions = new ArrayList<>(Objects.requireNonNull(
                portions, "portions"));
        portions.forEach(value -> Objects.requireNonNull(value, "portion"));
        portions.sort(Comparator.comparing(Portion::slot));
        if (portions.isEmpty() || portions.size() > MAX_PORTIONS) {
            throw new IllegalArgumentException(
                    "Foreign cash deposit portion count is invalid");
        }
        long value = 0L;
        long snapshotBytes = 0L;
        InternalBillInventoryPlanner.SlotIdentity previous = null;
        for (Portion portion : portions) {
            if (previous != null && previous.compareTo(portion.slot()) >= 0) {
                throw new IllegalArgumentException(
                        "Foreign cash deposit slots are duplicated");
            }
            previous = portion.slot();
            value = Math.addExact(value, portion.valueMinorUnits());
            snapshotBytes = Math.addExact(snapshotBytes,
                    portion.exactStackSnapshot().length);
        }
        if (value != amountMinorUnits
                || snapshotBytes > MAX_TOTAL_SNAPSHOT_BYTES) {
            throw new IllegalArgumentException(
                    "Foreign cash deposit value is invalid");
        }
        portions = List.copyOf(portions);
    }

    static ForeignCashDepositPlan select(
            PhysicalCurrencyAdapter adapter,
            List<ItemStack> main,
            List<ItemStack> offhand,
            long requestedMinorUnits,
            InternalBillInventoryPlanner.SlotIdentity onlySlot
    ) {
        Objects.requireNonNull(adapter, "adapter");
        Objects.requireNonNull(main, "main");
        Objects.requireNonNull(offhand, "offhand");
        if (adapter.isInternal() || requestedMinorUnits <= 0L) {
            throw new IllegalArgumentException(
                    "Foreign cash deposit selection is invalid");
        }
        List<Candidate> candidates = new ArrayList<>();
        collect(adapter, main, InternalBillInventoryPlanner.Container.MAIN,
                onlySlot, candidates);
        collect(adapter, offhand,
                InternalBillInventoryPlanner.Container.OFFHAND,
                onlySlot, candidates);
        Map<Long, Integer> availableByValue = new LinkedHashMap<>();
        candidates.stream().map(Candidate::unitValueMinorUnits).distinct()
                .sorted(Comparator.reverseOrder())
                .forEach(value -> availableByValue.put(value, 0));
        for (Candidate candidate : candidates) {
            availableByValue.compute(candidate.unitValueMinorUnits(),
                    (ignored, count) -> Math.addExact(count,
                            candidate.originalCount()));
        }
        long[] values = new long[availableByValue.size()];
        int[] available = new int[availableByValue.size()];
        int valueIndex = 0;
        for (Map.Entry<Long, Integer> entry :
                availableByValue.entrySet()) {
            values[valueIndex] = entry.getKey();
            available[valueIndex] = entry.getValue();
            valueIndex++;
        }
        long[] selected = CurrencyMath.exactBoundedCounts(
                requestedMinorUnits, values, available);
        if (selected == null) {
            throw new NoExactSelectionException();
        }
        List<Portion> portions = new ArrayList<>();
        for (int index = 0; index < selected.length; index++) {
            int remaining = Math.toIntExact(selected[index]);
            for (Candidate candidate : candidates) {
                if (remaining == 0) {
                    break;
                }
                if (candidate.unitValueMinorUnits() != values[index]) {
                    continue;
                }
                int count = Math.min(remaining,
                        candidate.originalCount());
                portions.add(candidate.portion(count));
                remaining -= count;
            }
            if (remaining != 0) {
                throw new IllegalStateException(
                        "Foreign cash deposit allocation is incomplete");
            }
        }
        return new ForeignCashDepositPlan(adapter.id(),
                adapter.depositConfigurationSignature(),
                requestedMinorUnits, portions);
    }

    static long availableValue(
            PhysicalCurrencyAdapter adapter,
            List<ItemStack> main,
            List<ItemStack> offhand,
            InternalBillInventoryPlanner.SlotIdentity onlySlot
    ) {
        List<Candidate> candidates = new ArrayList<>();
        collect(adapter, main, InternalBillInventoryPlanner.Container.MAIN,
                onlySlot, candidates);
        collect(adapter, offhand,
                InternalBillInventoryPlanner.Container.OFFHAND,
                onlySlot, candidates);
        long result = 0L;
        for (Candidate candidate : candidates) {
            result = Math.addExact(result, Math.multiplyExact(
                    candidate.unitValueMinorUnits(),
                    (long) candidate.originalCount()));
        }
        return result;
    }

    private static void collect(
            PhysicalCurrencyAdapter adapter,
            List<ItemStack> stacks,
            InternalBillInventoryPlanner.Container container,
            InternalBillInventoryPlanner.SlotIdentity onlySlot,
            List<Candidate> result
    ) {
        for (int index = 0; index < stacks.size(); index++) {
            InternalBillInventoryPlanner.SlotIdentity slot =
                    new InternalBillInventoryPlanner.SlotIdentity(
                            container, index);
            if (onlySlot != null && !onlySlot.equals(slot)) {
                continue;
            }
            ItemStack stack = stacks.get(index);
            long unit = adapter.unitValueMinor(stack);
            if (unit <= 0L || stack.isEmpty()) {
                continue;
            }
            if (stack.getItem() == ModItems.MONEY_ITEM.get()) {
                throw new IllegalArgumentException(
                        "FutureShops bills cannot use foreign cash custody");
            }
            String registryId = String.valueOf(
                    ForgeRegistries.ITEMS.getKey(stack.getItem()));
            result.add(new Candidate(slot, registryId, unit,
                    stack.getCount(), ItemStackSnapshotCodec.encode(stack)));
        }
    }

    private static String requireText(String value, int maximum,
                                      String label) {
        String normalized = Objects.requireNonNull(value, label).strip();
        if (normalized.isEmpty() || normalized.length() > maximum) {
            throw new IllegalArgumentException(
                    "Foreign cash deposit " + label + " is invalid");
        }
        return normalized;
    }

    public record Portion(
            InternalBillInventoryPlanner.SlotIdentity slot,
            String registryId,
            long unitValueMinorUnits,
            int originalStackCount,
            int selectedCount,
            byte[] exactStackSnapshot
    ) {
        public Portion {
            Objects.requireNonNull(slot, "slot");
            registryId = requireText(registryId, 256, "registry id");
            exactStackSnapshot = Objects.requireNonNull(
                    exactStackSnapshot, "exactStackSnapshot").clone();
            if (unitValueMinorUnits <= 0L || originalStackCount <= 0
                    || selectedCount <= 0
                    || selectedCount > originalStackCount
                    || exactStackSnapshot.length == 0) {
                throw new IllegalArgumentException(
                        "Foreign cash deposit portion is invalid");
            }
            ItemStack decoded = ItemStackSnapshotCodec.decode(
                    exactStackSnapshot);
            if (decoded.getCount() != originalStackCount
                    || decoded.getItem() == ModItems.MONEY_ITEM.get()
                    || !registryId.equals(String.valueOf(
                    ForgeRegistries.ITEMS.getKey(decoded.getItem())))) {
                throw new IllegalArgumentException(
                        "Foreign cash deposit snapshot is invalid");
            }
        }

        @Override
        public byte[] exactStackSnapshot() {
            return exactStackSnapshot.clone();
        }

        public long valueMinorUnits() {
            return Math.multiplyExact(unitValueMinorUnits,
                    (long) selectedCount);
        }

        @Override
        public boolean equals(Object object) {
            return object instanceof Portion other
                    && slot.equals(other.slot)
                    && registryId.equals(other.registryId)
                    && unitValueMinorUnits == other.unitValueMinorUnits
                    && originalStackCount == other.originalStackCount
                    && selectedCount == other.selectedCount
                    && Arrays.equals(exactStackSnapshot,
                    other.exactStackSnapshot);
        }

        @Override
        public int hashCode() {
            return 31 * Objects.hash(slot, registryId,
                    unitValueMinorUnits, originalStackCount, selectedCount)
                    + Arrays.hashCode(exactStackSnapshot);
        }
    }

    private record Candidate(
            InternalBillInventoryPlanner.SlotIdentity slot,
            String registryId,
            long unitValueMinorUnits,
            int originalCount,
            byte[] snapshot
    ) {
        private Portion portion(int count) {
            return new Portion(slot, registryId, unitValueMinorUnits,
                    originalCount, count, snapshot);
        }
    }

    static final class NoExactSelectionException
            extends IllegalArgumentException {
        private NoExactSelectionException() {
            super("Foreign cash deposit has no exact selection");
        }
    }
}
