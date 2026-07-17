package com.enviouse.futureshops.server.economy.migration;

import com.enviouse.futureshops.server.economy.InternalBalanceSavedData;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InternalBalanceMigrationArchiveTest {
    @Test
    void sealAndArchiveMetadataRoundTrip() {
        UUID player = UUID.randomUUID();
        InternalBalanceSavedData data = new InternalBalanceSavedData();
        data.setBalance(player, 10L);
        String fingerprint = LegacyBalanceSnapshot.capture(
                data.snapshotBalances()).fingerprint();
        data.sealMigrationSource(fingerprint);
        data.markMigrationArchiveReadOnly(fingerprint);

        InternalBalanceSavedData loaded = InternalBalanceSavedData.load(
                data.save(new CompoundTag()));

        assertTrue(loaded.isMigrationSourceSealed());
        assertTrue(loaded.isMigrationArchiveReadOnly());
        assertEquals(fingerprint,
                loaded.migrationSnapshotFingerprint().orElseThrow());
        assertEquals(10L, loaded.getBalanceOrDefault(player, 0L));
        assertThrows(IllegalStateException.class,
                () -> loaded.setBalance(player, 20L));
        assertThrows(IllegalStateException.class,
                () -> loaded.getBalanceOrDefault(UUID.randomUUID(), 20L));
    }

    @Test
    void archiveRequiresMatchingSeal() {
        InternalBalanceSavedData data = new InternalBalanceSavedData();
        String fingerprint = "0".repeat(64);
        String other = "1".repeat(64);
        data.sealMigrationSource(fingerprint);

        assertThrows(IllegalStateException.class,
                () -> data.markMigrationArchiveReadOnly(other));
        assertThrows(IllegalStateException.class,
                () -> data.sealMigrationSource(other));
    }
}
