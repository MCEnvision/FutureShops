package com.enviouse.futureshops.catalog.offer;

import java.util.List;
import java.util.Objects;

public record AcquireOfferOption(
        String optionId,
        String label,
        boolean free,
        boolean moneyCostPresent,
        long moneyCostMinorUnits,
        List<OfferItemComponent> itemCosts,
        int outputMultiplier,
        OfferLimitPolicy limits,
        OfferSchedule schedule,
        String permissionNode
) {
    public AcquireOfferOption {
        optionId = Objects.requireNonNullElse(optionId, "").strip();
        label = Objects.requireNonNullElse(label, "").strip();
        itemCosts = List.copyOf(Objects.requireNonNull(itemCosts, "itemCosts"));
        limits = Objects.requireNonNull(limits, "limits");
        schedule = Objects.requireNonNull(schedule, "schedule");
        permissionNode = Objects.requireNonNullElse(permissionNode, "").strip();
    }

    public static AcquireOfferOption free(String optionId) {
        return new AcquireOfferOption(optionId, "Free", true,
                false, 0L, List.of(), 1, OfferLimitPolicy.defaults(),
                OfferSchedule.always(), "");
    }

    public static AcquireOfferOption money(
            String optionId,
            long moneyCostMinorUnits
    ) {
        return new AcquireOfferOption(optionId, "Money", false,
                true, moneyCostMinorUnits, List.of(), 1,
                OfferLimitPolicy.defaults(), OfferSchedule.always(), "");
    }

    public boolean hasItemCosts() {
        return !itemCosts.isEmpty();
    }

    public boolean compound() {
        return moneyCostPresent && hasItemCosts();
    }
}
