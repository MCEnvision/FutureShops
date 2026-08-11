package com.enviouse.futureshops.server.escrow.item.runtime;

import java.util.List;
import java.util.Objects;

public record ItemInventoryQuarantineInspection(
        ItemInventoryJournalEntry entry,
        boolean resolved,
        List<ItemInventoryQuarantineAdministration> reviews
) {
    public ItemInventoryQuarantineInspection {
        Objects.requireNonNull(entry, "entry");
        reviews = List.copyOf(Objects.requireNonNull(reviews, "reviews"));
        if (entry.status() != ItemInventoryJournalStatus.QUARANTINED) {
            throw new IllegalArgumentException(
                    "Item inventory entry is not quarantined");
        }
    }
}
