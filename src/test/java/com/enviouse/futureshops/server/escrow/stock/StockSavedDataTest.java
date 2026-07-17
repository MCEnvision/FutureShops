package com.enviouse.futureshops.server.escrow.stock;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StockSavedDataTest {
    private static final Instant NOW = Instant.parse("2026-07-17T17:00:00Z");
    private static final StockKey KEY = new StockKey(
            "server", "minecraft:diamond");

    @Test
    void savedSnapshotRoundTripsReservationsReceiptsAndReplayState() {
        StockSavedData data = new StockSavedData();
        UUID seedRequest = UUID.randomUUID();
        data.applyCommitted(new StockMutationCommand.Seed(seedRequest,
                definition(10L), NOW));
        UUID transactionId = UUID.randomUUID();
        UUID reserveRequest = UUID.randomUUID();
        StockMutationCommand.ReserveBatch reserve =
                new StockMutationCommand.ReserveBatch(reserveRequest,
                        transactionId, List.of(new StockReservationRequest(
                        KEY, StockReservationDirection.OUTBOUND, 3L, 0L)),
                        NOW.plusSeconds(1));
        data.applyCommitted(reserve);

        StockSavedData loaded = StockSavedData.load(
                data.save(new CompoundTag()));

        assertEquals(data.snapshot(), loaded.snapshot());
        assertEquals(7L, loaded.listing(KEY).availableQuantity());
        assertEquals(1, loaded.reservationsForTransaction(
                transactionId).size());
        assertTrue(loaded.preflightCommitted(new StockMutationCommand.ReserveBatch(
                reserveRequest, transactionId, reserve.reservations(),
                NOW.plusSeconds(20))).replayed());
        assertTrue(loaded.conservation().conserved());
    }

    @Test
    void corruptMissingWrongTypeAndNewerSnapshotsFailClosed() {
        CompoundTag newer = new CompoundTag();
        newer.putInt("schemaVersion", StockSavedData.CURRENT_VERSION + 1);
        assertThrows(IllegalStateException.class,
                () -> StockSavedData.load(newer));

        CompoundTag missing = new CompoundTag();
        missing.putInt("schemaVersion", StockSavedData.CURRENT_VERSION);
        assertThrows(IllegalStateException.class,
                () -> StockSavedData.load(missing));

        CompoundTag wrongVersionType = new CompoundTag();
        wrongVersionType.putString("schemaVersion", "one");
        assertThrows(IllegalStateException.class,
                () -> StockSavedData.load(wrongVersionType));

        CompoundTag wrongSnapshotType = new CompoundTag();
        wrongSnapshotType.putInt("schemaVersion",
                StockSavedData.CURRENT_VERSION);
        wrongSnapshotType.putString("snapshot", "bad");
        assertThrows(IllegalStateException.class,
                () -> StockSavedData.load(wrongSnapshotType));

        StockSavedData data = new StockSavedData();
        data.applyCommitted(new StockMutationCommand.Seed(UUID.randomUUID(),
                definition(10L), NOW));
        CompoundTag corrupt = data.save(new CompoundTag());
        byte[] encoded = corrupt.getByteArray("snapshot");
        encoded[encoded.length / 2] ^= 1;
        corrupt.putByteArray("snapshot", encoded);
        assertThrows(IllegalStateException.class,
                () -> StockSavedData.load(corrupt));
    }

    @Test
    void legacyEmptyTagMigratesToAnEmptyConservedStore() {
        StockSavedData loaded = StockSavedData.load(new CompoundTag());

        assertTrue(loaded.snapshot().listings().isEmpty());
        assertTrue(loaded.snapshot().reservations().isEmpty());
        assertTrue(loaded.snapshot().receipts().isEmpty());
        assertTrue(loaded.conservation().conserved());
    }

    private static StockDefinition definition(long quantity) {
        return new StockDefinition(KEY, StockPolicy.limited(quantity), fp('a'));
    }

    private static String fp(char value) {
        return String.valueOf(value).repeat(64);
    }
}
