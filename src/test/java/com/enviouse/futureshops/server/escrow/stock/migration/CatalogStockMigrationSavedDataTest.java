package com.enviouse.futureshops.server.escrow.stock.migration;

import com.enviouse.futureshops.server.escrow.stock.StockKey;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CatalogStockMigrationSavedDataTest {
    @Test
    void snapshotCursorChecksumAndSequenceRoundTrip() {
        CatalogStockSeedEntry entry = entry();
        CatalogStockSeedSnapshot snapshot =
                CatalogStockSeedSnapshot.capture(List.of(entry));
        CatalogStockMigrationSavedData data =
                new CatalogStockMigrationSavedData();
        data.initializeSnapshot(snapshot);
        data.markSnapshotDurable();
        data.advance(entry,
                CatalogStockMigrationIds.entryCompletion(snapshot, entry));
        data.markImportsComplete();
        data.markVerified(7L);

        CatalogStockMigrationSavedData loaded =
                CatalogStockMigrationSavedData.load(
                        data.save(new CompoundTag()));

        assertEquals(CatalogStockMigrationStage.VERIFIED,
                loaded.stage());
        assertEquals(snapshot, loaded.snapshot());
        assertEquals(1, loaded.nextEntryIndex());
        assertEquals(7L, loaded.completionSequence());
    }

    @Test
    void malformedCursorReceiptFailsClosed() {
        CatalogStockSeedEntry entry = entry();
        CatalogStockSeedSnapshot snapshot =
                CatalogStockSeedSnapshot.capture(List.of(entry));
        CatalogStockMigrationSavedData data =
                new CatalogStockMigrationSavedData();
        data.initializeSnapshot(snapshot);
        data.markSnapshotDurable();
        data.advance(entry,
                CatalogStockMigrationIds.entryCompletion(snapshot, entry));
        CompoundTag encoded = data.save(new CompoundTag());
        encoded.putUUID("lastCompletedRequest", UUID.randomUUID());

        assertThrows(IllegalStateException.class,
                () -> CatalogStockMigrationSavedData.load(encoded));
    }

    @Test
    void corruptFingerprintAndNewerSchemaFailClosed() {
        CatalogStockMigrationSavedData data =
                new CatalogStockMigrationSavedData();
        data.initializeSnapshot(CatalogStockSeedSnapshot.capture(
                List.of(entry())));
        CompoundTag corrupt = data.save(new CompoundTag());
        corrupt.putString("snapshotFingerprint", "0".repeat(64));
        assertThrows(IllegalStateException.class,
                () -> CatalogStockMigrationSavedData.load(corrupt));

        CompoundTag newer = new CompoundTag();
        newer.putInt("schemaVersion", Integer.MAX_VALUE);
        assertThrows(IllegalStateException.class,
                () -> CatalogStockMigrationSavedData.load(newer));
    }

    private static CatalogStockSeedEntry entry() {
        return new CatalogStockSeedEntry(
                new StockKey("default", "minecraft:diamond"),
                false, 10L, 6L, "a".repeat(64));
    }
}
