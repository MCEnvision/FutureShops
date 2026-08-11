package com.enviouse.futureshops.catalog.offer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

public final class OfferComponentNormalizer {
    private OfferComponentNormalizer() {
    }

    public static List<OfferItemComponent> normalize(
            List<OfferItemComponent> components
    ) {
        LinkedHashMap<Key, OfferItemComponent> normalized =
                new LinkedHashMap<>();
        for (OfferItemComponent component : Objects.requireNonNull(components,
                "components")) {
            Key key = new Key(component.itemId(), component.exactNbt());
            OfferItemComponent existing = normalized.get(key);
            if (existing == null) {
                normalized.put(key, component);
                continue;
            }
            normalized.put(key, new OfferItemComponent(
                    existing.componentId(), existing.itemId(),
                    Math.addExact(existing.count(), component.count()),
                    existing.exactNbt()));
        }
        return List.copyOf(new ArrayList<>(normalized.values()));
    }

    private record Key(String itemId, String exactNbt) {
    }
}
