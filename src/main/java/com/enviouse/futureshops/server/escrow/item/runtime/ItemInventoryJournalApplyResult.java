package com.enviouse.futureshops.server.escrow.item.runtime;

import java.util.Objects;

public record ItemInventoryJournalApplyResult(
        ItemInventoryJournalEntry entry,
        boolean replayed
) {
    public ItemInventoryJournalApplyResult {
        Objects.requireNonNull(entry, "entry");
    }
}
