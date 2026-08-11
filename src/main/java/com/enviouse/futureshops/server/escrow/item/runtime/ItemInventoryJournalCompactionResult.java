package com.enviouse.futureshops.server.escrow.item.runtime;

public record ItemInventoryJournalCompactionResult(
        int compactedEntries,
        boolean replayed
) {
    public ItemInventoryJournalCompactionResult {
        if (compactedEntries < 0
                || replayed && compactedEntries != 0) {
            throw new IllegalArgumentException(
                    "Item inventory compaction result is invalid");
        }
    }
}
