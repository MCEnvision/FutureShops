package com.enviouse.futureshops.catalog.offer;

import java.util.List;
import java.util.Objects;

public record ServerShopOfferListing(
        String listingId,
        long revision,
        String displayName,
        String description,
        String categoryId,
        String iconItemId,
        String iconNbt,
        boolean active,
        long expiresAtEpoch,
        String permissionNode,
        List<OfferItemComponent> outputs,
        List<AcquireOfferOption> acquireOptions,
        List<SellOfferOption> sellOptions,
        OfferStockPolicy stockPolicy,
        OfferLimitPolicy limits,
        OfferSchedule schedule,
        List<OfferBundleComparison> bundleComparisons
) {
    public ServerShopOfferListing {
        listingId = Objects.requireNonNullElse(listingId, "").strip();
        displayName = Objects.requireNonNullElse(displayName, "").strip();
        description = Objects.requireNonNullElse(description, "").strip();
        categoryId = Objects.requireNonNullElse(categoryId, "all").strip();
        iconItemId = Objects.requireNonNullElse(iconItemId, "").strip();
        iconNbt = Objects.requireNonNullElse(iconNbt, "").strip();
        permissionNode = Objects.requireNonNullElse(permissionNode, "").strip();
        outputs = List.copyOf(Objects.requireNonNull(outputs, "outputs"));
        acquireOptions = List.copyOf(Objects.requireNonNull(acquireOptions,
                "acquireOptions"));
        sellOptions = List.copyOf(Objects.requireNonNull(sellOptions,
                "sellOptions"));
        stockPolicy = Objects.requireNonNull(stockPolicy, "stockPolicy");
        limits = Objects.requireNonNull(limits, "limits");
        schedule = Objects.requireNonNull(schedule, "schedule");
        bundleComparisons = List.copyOf(Objects.requireNonNull(
                bundleComparisons, "bundleComparisons"));
    }

    public boolean sellOnly() {
        return acquireOptions.isEmpty() && !sellOptions.isEmpty();
    }

    public boolean bundle() {
        if (outputs.size() > 1) {
            return true;
        }
        return sellOptions.stream().anyMatch(option ->
                option.itemInputs().size() > 1);
    }

    public ServerShopOfferListing withRevision(long newRevision) {
        return new ServerShopOfferListing(listingId, newRevision,
                displayName, description, categoryId, iconItemId, iconNbt,
                active, expiresAtEpoch, permissionNode, outputs,
                acquireOptions, sellOptions, stockPolicy, limits, schedule,
                bundleComparisons);
    }
}
