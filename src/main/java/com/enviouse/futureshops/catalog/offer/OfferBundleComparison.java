package com.enviouse.futureshops.catalog.offer;

import java.util.Objects;

public record OfferBundleComparison(
        String componentId,
        String listingId,
        String optionId
) {
    public OfferBundleComparison {
        componentId = Objects.requireNonNullElse(componentId, "").strip();
        listingId = Objects.requireNonNullElse(listingId, "").strip();
        optionId = Objects.requireNonNullElse(optionId, "").strip();
    }
}
