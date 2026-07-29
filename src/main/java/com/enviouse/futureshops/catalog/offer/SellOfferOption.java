package com.enviouse.futureshops.catalog.offer;

import java.util.List;
import java.util.Objects;

public record SellOfferOption(
        String optionId,
        String label,
        List<OfferItemComponent> itemInputs,
        long moneyPayoutMinorUnits,
        long capacity,
        OfferLimitPolicy limits,
        OfferSchedule schedule,
        String permissionNode
) {
    public SellOfferOption {
        optionId = Objects.requireNonNullElse(optionId, "").strip();
        label = Objects.requireNonNullElse(label, "").strip();
        itemInputs = List.copyOf(Objects.requireNonNull(itemInputs,
                "itemInputs"));
        limits = Objects.requireNonNull(limits, "limits");
        schedule = Objects.requireNonNull(schedule, "schedule");
        permissionNode = Objects.requireNonNullElse(permissionNode, "").strip();
    }
}
