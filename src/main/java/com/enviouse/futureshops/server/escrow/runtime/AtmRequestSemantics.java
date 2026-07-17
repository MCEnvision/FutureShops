package com.enviouse.futureshops.server.escrow.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public final class AtmRequestSemantics {
    public static final int MAXIMUM_SHAPE_LENGTH = 2048;

    private AtmRequestSemantics() {
    }

    public static String shape(List<AtmBillSelection> selections) {
        Objects.requireNonNull(selections, "selections");
        Map<Integer, Entry> entries = new TreeMap<>();
        for (AtmBillSelection selection : selections) {
            Objects.requireNonNull(selection, "selection");
            if (entries.put(selection.denominationIndex(), new Entry(
                    selection.denominationMinorUnits(),
                    selection.billCount())) != null) {
                throw new IllegalArgumentException(
                        "ATM request selection index is duplicated");
            }
        }
        return encode(entries);
    }

    public static String foreignShape(
            List<ForeignAtmStackSelection> stacks
    ) {
        Objects.requireNonNull(stacks, "stacks");
        Map<Integer, Entry> entries = new TreeMap<>();
        for (ForeignAtmStackSelection stack : stacks) {
            Objects.requireNonNull(stack, "stack");
            Entry prior = entries.get(stack.denominationIndex());
            if (prior == null) {
                entries.put(stack.denominationIndex(), new Entry(
                        stack.denominationMinorUnits(),
                        stack.stackCount()));
            } else {
                if (prior.valueMinorUnits()
                        != stack.denominationMinorUnits()) {
                    throw new IllegalArgumentException(
                            "Foreign ATM request denomination changed");
                }
                entries.put(stack.denominationIndex(), new Entry(
                        prior.valueMinorUnits(), Math.addExact(
                        prior.billCount(), stack.stackCount())));
            }
        }
        return encode(entries);
    }

    public static boolean matchesCounts(
            String shape,
            List<Integer> counts
    ) {
        Objects.requireNonNull(counts, "counts");
        Map<Integer, Entry> expected = decode(shape);
        Map<Integer, Integer> actual = new HashMap<>();
        int total = 0;
        for (int index = 0; index < counts.size(); index++) {
            Integer count = counts.get(index);
            if (count == null || count < 0
                    || count > ProtectedAtmWithdrawalRequest.MAXIMUM_BILLS) {
                return false;
            }
            total = Math.addExact(total, count);
            if (total > ProtectedAtmWithdrawalRequest.MAXIMUM_BILLS) {
                return false;
            }
            if (count > 0) {
                actual.put(index, count);
            }
        }
        if (actual.size() != expected.size()) {
            return false;
        }
        for (Map.Entry<Integer, Entry> entry : expected.entrySet()) {
            if (!Objects.equals(actual.get(entry.getKey()),
                    entry.getValue().billCount())) {
                return false;
            }
        }
        return true;
    }

    public static List<AtmBillSelection> selections(String shape) {
        List<AtmBillSelection> selections = new ArrayList<>();
        for (Map.Entry<Integer, Entry> entry : decode(shape).entrySet()) {
            selections.add(new AtmBillSelection(
                    entry.getKey(), entry.getValue().valueMinorUnits(),
                    entry.getValue().billCount()));
        }
        return List.copyOf(selections);
    }

    private static String encode(Map<Integer, Entry> entries) {
        if (entries.isEmpty()) {
            throw new IllegalArgumentException(
                    "ATM request shape is empty");
        }
        StringBuilder value = new StringBuilder("v1");
        for (Map.Entry<Integer, Entry> entry : entries.entrySet()) {
            value.append(',').append(entry.getKey())
                    .append(',').append(entry.getValue().valueMinorUnits())
                    .append(',').append(entry.getValue().billCount());
        }
        if (value.length() > MAXIMUM_SHAPE_LENGTH) {
            throw new IllegalArgumentException(
                    "ATM request shape exceeds its limit");
        }
        return value.toString();
    }

    private static Map<Integer, Entry> decode(String shape) {
        String value = Objects.requireNonNull(shape, "shape");
        if (value.isEmpty() || value.length() > MAXIMUM_SHAPE_LENGTH) {
            throw new IllegalArgumentException(
                    "ATM request shape size is invalid");
        }
        String[] parts = value.split(",", -1);
        if (parts.length < 4
                || (parts.length - 1) % 3 != 0
                || !parts[0].equals("v1")) {
            throw new IllegalArgumentException(
                    "ATM request shape format is invalid");
        }
        Map<Integer, Entry> entries = new TreeMap<>();
        int total = 0;
        try {
            for (int offset = 1; offset < parts.length; offset += 3) {
                int index = Integer.parseInt(parts[offset]);
                long denomination = Long.parseLong(parts[offset + 1]);
                int count = Integer.parseInt(parts[offset + 2]);
                AtmBillSelection selection = new AtmBillSelection(
                        index, denomination, count);
                total = Math.addExact(total, selection.billCount());
                if (total > ProtectedAtmWithdrawalRequest.MAXIMUM_BILLS
                        || entries.put(index, new Entry(
                        denomination, count)) != null) {
                    throw new IllegalArgumentException(
                            "ATM request shape entries are invalid");
                }
            }
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "ATM request shape number is invalid", exception);
        }
        return Map.copyOf(entries);
    }

    private record Entry(long valueMinorUnits, int billCount) {
    }
}
