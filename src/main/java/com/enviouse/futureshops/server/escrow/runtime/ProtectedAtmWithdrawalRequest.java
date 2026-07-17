package com.enviouse.futureshops.server.escrow.runtime;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record ProtectedAtmWithdrawalRequest(
        UUID requestId,
        UUID playerId,
        String providerId,
        String currencySignature,
        List<AtmBillSelection> selections,
        Instant requestedAt
) {
    public static final int MAXIMUM_BILLS = 4096;

    public ProtectedAtmWithdrawalRequest {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(playerId, "playerId");
        providerId = requireText(providerId, 128, "providerId");
        currencySignature = requireText(
                currencySignature, 128, "currencySignature");
        selections = canonicalSelections(selections);
        Objects.requireNonNull(requestedAt, "requestedAt");
        if (selections.isEmpty()) {
            throw new IllegalArgumentException(
                    "Protected ATM request has no bills");
        }
        int totalBills = 0;
        long totalValue = 0L;
        for (AtmBillSelection selection : selections) {
            totalBills = Math.addExact(totalBills, selection.billCount());
            totalValue = Math.addExact(totalValue, Math.multiplyExact(
                    selection.denominationMinorUnits(),
                    (long) selection.billCount()));
        }
        if (totalBills > MAXIMUM_BILLS || totalValue <= 0L) {
            throw new IllegalArgumentException(
                    "Protected ATM request exceeds its limit");
        }
    }

    public long amountMinorUnits() {
        long total = 0L;
        for (AtmBillSelection selection : selections) {
            total = Math.addExact(total, Math.multiplyExact(
                    selection.denominationMinorUnits(),
                    (long) selection.billCount()));
        }
        return total;
    }

    public String fingerprint() {
        StringBuilder value = new StringBuilder(
                "futureshops.protected.atm.request.v1,")
                .append(requestId).append(',')
                .append(playerId).append(',')
                .append(providerId).append(',')
                .append(currencySignature);
        for (AtmBillSelection selection : selections) {
            value.append(',').append(selection.denominationIndex())
                    .append(',').append(selection.denominationMinorUnits())
                    .append(',').append(selection.billCount());
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public ProtectedAtmWithdrawalRequest at(Instant instant) {
        return new ProtectedAtmWithdrawalRequest(
                requestId, playerId, providerId, currencySignature,
                selections, instant);
    }

    private static List<AtmBillSelection> canonicalSelections(
            List<AtmBillSelection> values
    ) {
        List<AtmBillSelection> ordered = new ArrayList<>(
                Objects.requireNonNull(values, "selections"));
        ordered.forEach(value -> Objects.requireNonNull(value, "selection"));
        ordered.sort(Comparator
                .comparingInt(AtmBillSelection::denominationIndex)
                .thenComparingLong(AtmBillSelection::denominationMinorUnits));
        Set<Integer> indexes = new HashSet<>();
        for (AtmBillSelection selection : ordered) {
            if (!indexes.add(selection.denominationIndex())) {
                throw new IllegalArgumentException(
                        "Protected ATM denomination index is duplicated");
            }
        }
        return List.copyOf(ordered);
    }

    private static String requireText(String value, int maximumLength,
                                      String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty() || normalized.length() > maximumLength) {
            throw new IllegalArgumentException(
                    "Protected ATM request text is invalid");
        }
        return normalized;
    }
}
