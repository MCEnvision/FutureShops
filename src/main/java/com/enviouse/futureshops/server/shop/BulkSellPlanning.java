package com.enviouse.futureshops.server.shop;

import java.math.BigInteger;
import java.util.Objects;
import java.util.function.IntPredicate;

final class BulkSellPlanning {
    private BulkSellPlanning() {
    }

    static int compare(
            long leftPayout,
            long leftInputCount,
            double leftDistance,
            String leftIdentity,
            long rightPayout,
            long rightInputCount,
            double rightDistance,
            String rightIdentity
    ) {
        if (leftPayout < 1L || rightPayout < 1L
                || leftInputCount < 1L || rightInputCount < 1L) {
            throw new IllegalArgumentException(
                    "Bulk sell candidate value is invalid");
        }
        int valueDensity = BigInteger.valueOf(rightPayout)
                .multiply(BigInteger.valueOf(leftInputCount))
                .compareTo(BigInteger.valueOf(leftPayout)
                        .multiply(BigInteger.valueOf(rightInputCount)));
        if (valueDensity != 0) {
            return valueDensity;
        }
        int payout = Long.compare(rightPayout, leftPayout);
        if (payout != 0) {
            return payout;
        }
        int distance = Double.compare(leftDistance, rightDistance);
        return distance != 0
                ? distance
                : Objects.requireNonNull(leftIdentity, "leftIdentity")
                .compareTo(Objects.requireNonNull(
                        rightIdentity, "rightIdentity"));
    }

    static int maximumExecutableQuantity(
            int inventoryQuantity,
            IntPredicate canExecute
    ) {
        if (inventoryQuantity < 1) {
            return 0;
        }
        IntPredicate check = Objects.requireNonNull(
                canExecute, "canExecute");
        if (check.test(inventoryQuantity)) {
            return inventoryQuantity;
        }
        int low = 1;
        int high = inventoryQuantity - 1;
        int accepted = 0;
        while (low <= high) {
            int middle = low + (high - low) / 2;
            if (check.test(middle)) {
                accepted = middle;
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        return accepted;
    }
}
