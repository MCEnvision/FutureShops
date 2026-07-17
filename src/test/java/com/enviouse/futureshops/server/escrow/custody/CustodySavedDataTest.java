package com.enviouse.futureshops.server.escrow.custody;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustodySavedDataTest {
    @Test
    void saveAndLoadPreserveReceiptsProvenanceAndIdempotency() {
        CustodySavedData data = new CustodySavedData();
        assertEquals(false, data.hasMaterializedState());
        CustodyLot protectedCash = CustodyTestFixtures.protectedCurrencyLot("saved reserve", 20L, 3);
        data.reserveCommitted(protectedCash);
        assertTrue(data.hasMaterializedState());
        CustodyTransferEvidence releaseEvidence = CustodyTestFixtures.terminalEvidence("saved release");
        data.releaseCommitted(protectedCash.lotId(), "saved release", releaseEvidence,
                CustodyTestFixtures.NOW.plusSeconds(1));

        CustodySavedData loaded = CustodySavedData.load(data.save(new CompoundTag()));
        CustodyLot restored = loaded.getLot(protectedCash.lotId());

        assertEquals(CustodyLotState.RELEASED, restored.state());
        assertEquals(protectedCash.protectedProvenance(), restored.protectedProvenance());
        assertArrayEquals(protectedCash.assetFingerprint(), restored.assetFingerprint());
        assertTrue(loaded.releaseCommitted(protectedCash.lotId(), "saved release", releaseEvidence,
                CustodyTestFixtures.NOW.plusSeconds(1)).replayed());
        assertTrue(loaded.conservation().conserved());
    }

    @Test
    void newerSchemaFailsClosed() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schemaVersion", Integer.MAX_VALUE);

        assertThrows(IllegalStateException.class, () -> CustodySavedData.load(tag));
    }

    @Test
    void negativeSchemaAndMalformedCurrentListsFailClosed() {
        CompoundTag negative = new CompoundTag();
        negative.putInt("schemaVersion", -1);

        CompoundTag missing = new CompoundTag();
        missing.putInt("schemaVersion", 3);

        CompoundTag wrongType = new CompoundTag();
        wrongType.putInt("schemaVersion", 3);
        wrongType.putString("lots", "not a list");
        wrongType.put("receipts", new ListTag());
        wrongType.put("prepared", new ListTag());
        wrongType.put("batches", new ListTag());

        CompoundTag wrongElementType = new CompoundTag();
        wrongElementType.putInt("schemaVersion", 3);
        ListTag strings = new ListTag();
        strings.add(StringTag.valueOf("not a compound"));
        wrongElementType.put("lots", strings);
        wrongElementType.put("receipts", new ListTag());
        wrongElementType.put("prepared", new ListTag());
        wrongElementType.put("batches", new ListTag());

        assertThrows(IllegalStateException.class, () -> CustodySavedData.load(negative));
        assertThrows(IllegalStateException.class, () -> CustodySavedData.load(missing));
        assertThrows(IllegalStateException.class, () -> CustodySavedData.load(wrongType));
        assertThrows(IllegalStateException.class, () -> CustodySavedData.load(wrongElementType));
    }

    @Test
    void tamperedPersistedSnapshotFailsClosed() {
        CustodySavedData data = new CustodySavedData();
        CustodyLot lot = CustodyTestFixtures.itemLot("tamper reserve", 2);
        data.reserveCommitted(lot);
        CompoundTag tag = data.save(new CompoundTag());
        ListTag lots = tag.getList("lots", Tag.TAG_COMPOUND);
        CompoundTag savedLot = lots.getCompound(0);
        ListTag snapshots = savedLot.getList("snapshots", Tag.TAG_COMPOUND);
        snapshots.getCompound(0).putByteArray("nbt", new byte[]{10, 0, 99});

        assertThrows(IllegalStateException.class, () -> CustodySavedData.load(tag));
    }

    @Test
    void schemaTwoPreparedIntentMigratesToOneAtomicBatch() {
        CustodyLot lot = CustodyTestFixtures.itemLot("legacy prepared", 2);
        CustodyPreparedOperation prepared = CustodyPreparedOperation.prepare(
                CustodyOperation.RESERVE, lot.reserveRequestKey(), lot,
                lot.holdEvidence().source().adapterId(),
                lot.holdEvidence().source().capability(), "legacy token",
                lot.holdEvidence(), CustodyTestFixtures.NOW);
        CustodySavedData data = new CustodySavedData();
        data.prepareCommitted(prepared);
        CompoundTag legacy = data.save(new CompoundTag());
        legacy.putInt("schemaVersion", 2);
        legacy.remove("batches");

        CustodySavedData migrated = CustodySavedData.load(legacy);

        assertEquals(1, migrated.unresolvedPreparedBatches(10).size());
        assertEquals(prepared, migrated.unresolvedPreparedOperations(10).get(0));
    }

    @Test
    void corruptedPersistedBatchPayloadFailsClosed() {
        CustodyLot lot = CustodyTestFixtures.itemLot("corrupt batch", 1);
        CustodyBatchPlan plan = CustodyBatchPlan.create(CustodyOperation.RESERVE,
                "corrupt batch", java.util.List.of(lot));
        CustodyPreparedBatch batch = CustodyPreparedBatch.prepare(plan,
                "corrupt token", Map.of(lot.lotId(), lot.holdEvidence()),
                CustodyTestFixtures.NOW);
        CustodySavedData data = new CustodySavedData();
        data.applyBatchCommit(CustodyBatchCommit.state(batch));
        CompoundTag persisted = data.save(new CompoundTag());
        CompoundTag entry = persisted.getList("batches", Tag.TAG_COMPOUND).getCompound(0);
        byte[] payload = entry.getByteArray("payload");
        payload[0] ^= 1;
        entry.putByteArray("payload", payload);

        assertThrows(IllegalStateException.class, () -> CustodySavedData.load(persisted));
    }
}
