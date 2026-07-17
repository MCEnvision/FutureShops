package com.enviouse.futureshops.server.escrow.mint;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtectedMintSavedDataTest {
    @Test
    void atomicIssueBatchAndReceiptSurviveNbtRoundTrip() {
        ProtectedMintBatch issued = ProtectedMintBatch.issue(
                ProtectedMintTestFixtures.MINT_TRANSACTION, "mint.issue.saved",
                50L, 32, ProtectedMintTestFixtures.SERVER,
                ProtectedMintTestFixtures.CREATED,
                ProtectedMintTestFixtures.EVIDENCE);
        ProtectedMintJournalEvent event = ProtectedMintJournalEvent.issue(issued);
        ProtectedMintSavedData data = new ProtectedMintSavedData();

        assertFalse(data.applyCommitted(event).replayed());
        ProtectedMintSavedData loaded = ProtectedMintSavedData.load(
                data.save(new CompoundTag()));

        assertEquals(issued, loaded.getBatch(issued.batchId()));
        assertEquals(ProtectedMintOperation.ISSUE,
                loaded.receiptForRequest(issued.authorizeRequestKey()).operation());
        assertTrue(loaded.applyCommitted(event).replayed());
        assertTrue(loaded.conservation().conserved());
    }

    @Test
    void savedPlanRecoversSameAuthorizedBatchBeforeMaterialization() {
        ProtectedMintSavedData data = new ProtectedMintSavedData();
        assertFalse(data.hasMaterializedState());
        ProtectedMintBatch batch = ProtectedMintTestFixtures.batch();
        data.authorizeCommitted(batch);

        CompoundTag saved = data.save(new CompoundTag());
        ProtectedMintSavedData loaded = ProtectedMintSavedData.load(saved);
        assertTrue(loaded.hasMaterializedState());
        assertEquals(batch, loaded.getBatch(batch.batchId()));
        assertEquals(10, loaded.getBatch(batch.batchId()).authorizedQuantity());

        ProtectedMintJournalEvent materialize = ProtectedMintJournalEvent.materialize(
                batch.transactionId(), batch.batchId(), "mint.materialize.saved",
                batch.authorizedCount(), batch.authorizedAt().plusSeconds(1));
        assertFalse(loaded.applyCommitted(materialize).replayed());
        CompoundTag materialized = loaded.save(new CompoundTag());
        ProtectedMintSavedData recovered = ProtectedMintSavedData.load(materialized);
        assertTrue(recovered.getBatch(batch.batchId()).isFullyMaterialized());
        assertEquals(10, recovered.getBatch(batch.batchId()).availableQuantity());
        assertTrue(recovered.applyCommitted(materialize).replayed());
        assertTrue(recovered.conservation().conserved());
    }

    @Test
    void reservationSpentRefundAndReplacementQuantitiesSurviveRoundTrip() {
        ProtectedMintSavedData data = new ProtectedMintSavedData();
        ProtectedMintBatch batch = ProtectedMintTestFixtures.batch();
        data.authorizeCommitted(batch);
        data.materializeCommitted(batch.transactionId(), batch.batchId(),
                "mint.materialize.roundtrip", 10, batch.authorizedAt().plusSeconds(1));
        data.reserveCommitted(ProtectedMintTestFixtures.HOLD_TRANSACTION, batch.batchId(),
                "mint.reserve.spent", 2, batch.authorizedAt().plusSeconds(2));
        data.commitCommitted(ProtectedMintTestFixtures.HOLD_TRANSACTION, batch.batchId(),
                "mint.commit.spent", 2, batch.authorizedAt().plusSeconds(3));
        data.reserveCommitted(ProtectedMintTestFixtures.SECOND_HOLD_TRANSACTION,
                batch.batchId(), "mint.reserve.refunded", 3,
                batch.authorizedAt().plusSeconds(4));
        ProtectedMintBatch replacement = ProtectedMintBatch.replacement(
                UUID.fromString("10000000-0000-0000-0000-000000000099"),
                ProtectedMintTestFixtures.SECOND_HOLD_TRANSACTION,
                "mint.refund.roundtrip", data.getBatch(batch.batchId()), 2,
                ProtectedMintTestFixtures.SERVER, batch.authorizedAt().plusSeconds(5),
                ProtectedMintTestFixtures.EVIDENCE);
        data.refundCommitted(ProtectedMintTestFixtures.SECOND_HOLD_TRANSACTION,
                batch.batchId(), "mint.refund.roundtrip", ProtectedMintState.RESERVED,
                2, replacement, batch.authorizedAt().plusSeconds(5));

        ProtectedMintSavedData loaded = ProtectedMintSavedData.load(
                data.save(new CompoundTag()));
        ProtectedMintBatch source = loaded.getBatch(batch.batchId());
        assertEquals(5, source.availableQuantity());
        assertEquals(2, source.spentFor(ProtectedMintTestFixtures.HOLD_TRANSACTION));
        assertEquals(1, source.reservedFor(
                ProtectedMintTestFixtures.SECOND_HOLD_TRANSACTION));
        assertEquals(2, source.refundedQuantity());
        assertEquals(2, loaded.getBatch(replacement.batchId()).authorizedQuantity());
        assertTrue(loaded.conservation().conserved());
    }

    @Test
    void newerMissingWrongTypeAndCorruptStateFailClosed() {
        CompoundTag newer = new CompoundTag();
        newer.putInt("schemaVersion", ProtectedMintSavedData.CURRENT_VERSION + 1);
        assertThrows(IllegalStateException.class, () -> ProtectedMintSavedData.load(newer));

        CompoundTag missing = new CompoundTag();
        missing.putInt("schemaVersion", ProtectedMintSavedData.CURRENT_VERSION);
        assertThrows(IllegalStateException.class, () -> ProtectedMintSavedData.load(missing));

        CompoundTag wrongType = new CompoundTag();
        wrongType.putString("schemaVersion", "one");
        assertThrows(IllegalStateException.class, () -> ProtectedMintSavedData.load(wrongType));

        ProtectedMintSavedData data = new ProtectedMintSavedData();
        data.authorizeCommitted(ProtectedMintTestFixtures.batch());
        CompoundTag corrupt = data.save(new CompoundTag());
        ListTag batches = corrupt.getList("batches", Tag.TAG_COMPOUND);
        batches.getCompound(0).putInt("authorized", 9);
        assertThrows(IllegalStateException.class, () -> ProtectedMintSavedData.load(corrupt));

        CompoundTag wrongList = new CompoundTag();
        wrongList.putInt("schemaVersion", ProtectedMintSavedData.CURRENT_VERSION);
        ListTag values = new ListTag();
        values.add(StringTag.valueOf("bad"));
        wrongList.put("batches", values);
        wrongList.put("receipts", new ListTag());
        assertThrows(IllegalStateException.class, () -> ProtectedMintSavedData.load(wrongList));
    }
}
