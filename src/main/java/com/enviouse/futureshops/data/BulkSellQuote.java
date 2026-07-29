package com.enviouse.futureshops.data;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record BulkSellQuote(
        UUID quoteId,
        BulkSellTarget target,
        String shopId,
        long expiresAtEpochMillis,
        String currencyName,
        int currencyDecimals,
        boolean selectEligibleByDefault,
        List<Line> lines
) {
    public static final int MAX_LINES = 128;
    public static final int MAX_COMPONENTS = 36;
    public static final int MAX_TEXT_LENGTH = 160;
    private static final UUID ZERO_UUID = new UUID(0L, 0L);

    public BulkSellQuote {
        quoteId = Objects.requireNonNull(quoteId, "quoteId");
        target = Objects.requireNonNull(target, "target");
        shopId = text(shopId, "shopId");
        currencyName = text(currencyName, "currencyName");
        lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
        if (ZERO_UUID.equals(quoteId)
                || expiresAtEpochMillis <= 0L
                || currencyDecimals < 0 || currencyDecimals > 6
                || lines.size() > MAX_LINES) {
            throw new IllegalArgumentException("Bulk sell quote is invalid");
        }
        Set<String> lineIds = new HashSet<>();
        long eligibleTotal = 0L;
        for (Line line : lines) {
            if (!lineIds.add(line.lineId())) {
                throw new IllegalArgumentException(
                        "Bulk sell quote line identifiers are duplicated");
            }
            if (line.eligible()) {
                eligibleTotal = Math.addExact(
                        eligibleTotal,
                        line.totalPayoutMinorUnits());
            }
        }
    }

    public long selectedTotal() {
        long total = 0L;
        for (Line line : lines) {
            if (line.eligible()) {
                total = Math.addExact(total, line.totalPayoutMinorUnits());
            }
        }
        return total;
    }

    public record Line(
            String lineId,
            String destination,
            List<Component> inputs,
            int quantity,
            long unitPayoutMinorUnits,
            long totalPayoutMinorUnits,
            boolean eligible,
            String reasonKey
    ) {
        public Line {
            lineId = text(lineId, "lineId");
            destination = text(destination, "destination");
            inputs = List.copyOf(Objects.requireNonNull(inputs, "inputs"));
            reasonKey = text(reasonKey, "reasonKey");
            if (inputs.isEmpty() || inputs.size() > MAX_COMPONENTS
                    || quantity < 1 || quantity > 2304
                    || unitPayoutMinorUnits < 0L
                    || totalPayoutMinorUnits < 0L
                    || eligible != (unitPayoutMinorUnits > 0L
                    && totalPayoutMinorUnits > 0L)
                    || eligible
                    && totalPayoutMinorUnits
                    != Math.multiplyExact(unitPayoutMinorUnits,
                    (long) quantity)) {
                throw new IllegalArgumentException(
                        "Bulk sell quote line is invalid");
            }
        }
    }

    public record Component(
            String itemId,
            int count,
            String exactNbt
    ) {
        public Component {
            itemId = text(itemId, "itemId");
            exactNbt = Objects.requireNonNullElse(
                    exactNbt, "").strip();
            if (count < 1 || count > 2304
                    || exactNbt.length() > 65_536) {
                throw new IllegalArgumentException(
                        "Bulk sell component is invalid");
            }
        }
    }

    private static String text(String value, String field) {
        String candidate = Objects.requireNonNull(value, field).strip();
        if (candidate.isEmpty()
                || candidate.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException(
                    "Bulk sell text is invalid");
        }
        return candidate;
    }
}
