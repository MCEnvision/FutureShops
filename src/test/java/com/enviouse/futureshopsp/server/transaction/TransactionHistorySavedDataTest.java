package com.enviouse.futureshopsp.server.transaction;

import com.enviouse.futureshopsp.data.TransactionHistoryEntry;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionHistorySavedDataTest {
    @Test
    void hostilePageValuesCannotOverflowTheSliceOffset() {
        UUID playerId = UUID.randomUUID();
        TransactionHistorySavedData data = new TransactionHistorySavedData();
        data.append(playerId, new TransactionHistoryEntry(
                1234L, "BUY", "minecraft:stone", 1, 1L, "test"));

        assertTrue(data.getPage(playerId, Integer.MAX_VALUE, Integer.MAX_VALUE).isEmpty());
    }
}
