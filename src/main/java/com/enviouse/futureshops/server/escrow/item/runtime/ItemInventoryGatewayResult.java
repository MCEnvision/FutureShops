package com.enviouse.futureshops.server.escrow.item.runtime;

import java.util.Objects;

public record ItemInventoryGatewayResult(
        ItemInventoryJournalEntry entry,
        boolean replayed
) {
    public ItemInventoryGatewayResult {
        Objects.requireNonNull(entry, "entry");
    }
}
