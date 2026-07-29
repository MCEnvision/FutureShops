package com.enviouse.futureshops.catalog.offer;

import java.util.Objects;

public record OfferItemComponent(
        String componentId,
        String itemId,
        int count,
        String exactNbt
) {
    public OfferItemComponent {
        componentId = Objects.requireNonNullElse(componentId, "").strip();
        itemId = Objects.requireNonNullElse(itemId, "").strip();
        exactNbt = Objects.requireNonNullElse(exactNbt, "").strip();
    }

    public boolean exactMatch() {
        return !exactNbt.isBlank();
    }
}
