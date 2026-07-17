package com.enviouse.futureshops.money;

import com.enviouse.futureshops.server.escrow.runtime.AtmBillSelection;

import java.util.List;
import java.util.Objects;

public record AtmSelectionPlan(
        Failure failure,
        List<AtmBillSelection> selections,
        long amountMinorUnits,
        int billCount
) {
    public enum Failure {
        NONE,
        INVALID_AMOUNT,
        INVALID_PLAN
    }

    public AtmSelectionPlan {
        Objects.requireNonNull(failure, "failure");
        selections = List.copyOf(Objects.requireNonNull(
                selections, "selections"));
        if (failure == Failure.NONE) {
            if (selections.isEmpty()
                    || amountMinorUnits <= 0L
                    || billCount <= 0
                    || billCount > AtmCurrencyCatalog.MAXIMUM_BILLS) {
                throw new IllegalArgumentException(
                        "Successful ATM selection plan is invalid");
            }
        } else if (!selections.isEmpty()
                || amountMinorUnits != 0L
                || billCount != 0) {
            throw new IllegalArgumentException(
                    "Failed ATM selection plan has output");
        }
    }

    public static AtmSelectionPlan success(
            List<AtmBillSelection> selections,
            long amountMinorUnits,
            int billCount
    ) {
        return new AtmSelectionPlan(
                Failure.NONE, selections, amountMinorUnits, billCount);
    }

    public static AtmSelectionPlan failed(Failure failure) {
        if (failure == Failure.NONE) {
            throw new IllegalArgumentException(
                    "ATM selection failure is required");
        }
        return new AtmSelectionPlan(failure, List.of(), 0L, 0);
    }

    public boolean valid() {
        return failure == Failure.NONE;
    }
}
