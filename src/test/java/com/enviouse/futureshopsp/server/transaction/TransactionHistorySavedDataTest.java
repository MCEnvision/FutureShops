package com.enviouse.futureshopsp.server.transaction;

import com.enviouse.futureshopsp.data.TransactionHistoryEntry;
import org.junit.jupiter.api.Test;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void nullFiltersAreSafeAndOversizedSearchIsRejected() {
        UUID playerId = UUID.randomUUID();
        TransactionHistorySavedData data = new TransactionHistorySavedData();
        data.append(playerId, new TransactionHistoryEntry(
                1234L, "BUY", "minecraft:stone", 1, 1L, "test"));

        assertTrue(data.getPage(playerId, 1, 20, null, "", null, null).size() == 1);
        assertTrue(data.getPage(playerId, 1, 20, null, "x".repeat(257), null, null).isEmpty());
        assertTrue(data.getTotalPages(playerId, 20, null, "x".repeat(257), null, null) == 1);
    }

    @Test
    void malformedPersistedHistoryDoesNotPartiallyLoad() {
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000501");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000502");
        TransactionHistorySavedData data = new TransactionHistorySavedData();
        data.append(first, new TransactionHistoryEntry(1234L, "BUY", "minecraft:stone", 1, 1L, "test"));
        data.append(second, new TransactionHistoryEntry(1235L, "SELL", "minecraft:dirt", 1, 2L, "test"));

        CompoundTag saved = data.save(new CompoundTag(), null);
        ListTag players = saved.getList("players", 10);
        CompoundTag malformedPlayer = (CompoundTag) players.get(1);
        ListTag entries = malformedPlayer.getList("entries", 10);
        ((CompoundTag) entries.get(0)).putString("qty", "not an integer");

        TransactionHistorySavedData recovered = TransactionHistorySavedData.load(saved, null);
        assertFalse(recovered.integrityValid());
        assertTrue(recovered.snapshotEntriesByPlayer().isEmpty());
    }

    @Test
    void wrongPlayersTagTypeBlocksHistoryRead() {
        CompoundTag saved = new CompoundTag();
        saved.putString("players", "not a list");

        TransactionHistorySavedData recovered = TransactionHistorySavedData.load(saved, null);
        assertFalse(recovered.integrityValid());
        assertTrue(recovered.snapshotEntriesByPlayer().isEmpty());
    }
}
