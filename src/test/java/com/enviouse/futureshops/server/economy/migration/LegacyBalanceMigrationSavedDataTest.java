package com.enviouse.futureshops.server.economy.migration;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LegacyBalanceMigrationSavedDataTest {
    private static final UUID PLAYER = UUID.fromString(
            "00000000-0000-0000-0000-000000000001");

    @Test
    void snapshotAndCursorRoundTrip() {
        LegacyBalanceMigrationSavedData data =
                new LegacyBalanceMigrationSavedData();
        LegacyBalanceSnapshot snapshot = LegacyBalanceSnapshot.capture(
                Map.of(PLAYER, 0L));
        data.initializeSnapshot(snapshot);
        data.markSnapshotDurable();
        LegacyBalanceEntry entry = snapshot.entries().get(0);
        data.advance(entry, WalletInitializationIds.legacyBalance(PLAYER));

        LegacyBalanceMigrationSavedData loaded =
                LegacyBalanceMigrationSavedData.load(
                        data.save(new CompoundTag()));

        assertEquals(LegacyBalanceMigrationStage.IMPORTING, loaded.stage());
        assertEquals(1, loaded.nextEntryIndex());
        assertEquals(snapshot, loaded.snapshot());
    }

    @Test
    void malformedCursorReceiptFailsClosed() {
        LegacyBalanceMigrationSavedData data =
                new LegacyBalanceMigrationSavedData();
        LegacyBalanceSnapshot snapshot = LegacyBalanceSnapshot.capture(
                Map.of(PLAYER, 10L));
        data.initializeSnapshot(snapshot);
        data.markSnapshotDurable();
        data.advance(snapshot.entries().get(0),
                WalletInitializationIds.legacyBalance(PLAYER));
        CompoundTag tag = data.save(new CompoundTag());
        tag.putUUID("lastCompletedRequest", UUID.randomUUID());

        assertThrows(IllegalStateException.class,
                () -> LegacyBalanceMigrationSavedData.load(tag));
    }

    @Test
    void newerSchemaFailsClosed() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schemaVersion", Integer.MAX_VALUE);

        assertThrows(IllegalStateException.class,
                () -> LegacyBalanceMigrationSavedData.load(tag));
    }
}
