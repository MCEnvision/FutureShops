package com.enviouse.futureshops.server.transaction;

import com.enviouse.futureshops.data.TransactionHistoryEntry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionHistorySavedDataTest {
    @Test
    void dedicatedMarkerSurvivesSaveLoadWithoutChangingItemSnbt() {
        UUID playerId = UUID.randomUUID();
        String marker = "player.payment.sent." + UUID.randomUUID();
        String itemSnbt = "{Damage:1}";
        TransactionHistoryEntry entry = new TransactionHistoryEntry(
                1234L, "PAY_SENT", "futureshops:wallet", 1,
                250L, "Payment history test", itemSnbt);
        TransactionHistorySavedData before =
                new TransactionHistorySavedData();

        assertTrue(before.appendIfAbsent(playerId, marker, entry));
        CompoundTag serialized = before.save(new CompoundTag());
        TransactionHistorySavedData after =
                TransactionHistorySavedData.load(serialized);

        assertFalse(after.appendIfAbsent(playerId, marker, entry));
        assertEquals(entry, after.getPage(playerId, 1, 20).get(0));
        ListTag players = serialized.getList(
                "players", Tag.TAG_COMPOUND);
        assertEquals(itemSnbt, players.getCompound(0)
                .getList("entries", Tag.TAG_COMPOUND)
                .getCompound(0).getString("nbt"));
        assertEquals(marker, serialized.getList(
                        "idempotency_markers", Tag.TAG_COMPOUND)
                .getCompound(0).getList("markers", Tag.TAG_STRING)
                .getString(0));
    }
}
