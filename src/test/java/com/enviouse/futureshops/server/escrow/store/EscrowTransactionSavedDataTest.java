package com.enviouse.futureshops.server.escrow.store;

import com.enviouse.futureshops.server.escrow.model.EscrowState;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EscrowTransactionSavedDataTest {
    @Test
    void saveAndLoadPreserveLatestRevisionAndRequestIndex() {
        EscrowTransactionSavedData data = new EscrowTransactionSavedData();
        org.junit.jupiter.api.Assertions.assertFalse(data.hasMaterializedState());
        EscrowTransaction created = EscrowTransactionFixtures.created("saved data lookup");
        EscrowTransaction validated = created.transitionTo(
                EscrowState.VALIDATED, EscrowTransactionFixtures.CREATED_AT.plusSeconds(1));
        data.applyCommitted(created);
        data.applyCommitted(validated);

        EscrowTransactionSavedData loaded = EscrowTransactionSavedData.load(
                data.save(new CompoundTag()));

        assertEquals(validated, loaded.getTransaction(created.transactionId()));
        assertEquals(validated, loaded.getByRequestKey(created.requestKey()));
        assertEquals(1, loaded.snapshotTransactions().size());
        assertTrue(loaded.hasMaterializedState());
        assertTrue(loaded.applyCommitted(created).replayed());
    }

    @Test
    void loadRejectsNewerSchemaAndMissingCurrentRecords() {
        CompoundTag newer = new CompoundTag();
        newer.putInt("schemaVersion", Integer.MAX_VALUE);
        CompoundTag missing = new CompoundTag();
        missing.putInt("schemaVersion", 1);

        assertThrows(IllegalStateException.class, () -> EscrowTransactionSavedData.load(newer));
        assertThrows(IllegalStateException.class, () -> EscrowTransactionSavedData.load(missing));
    }

    @Test
    void loadRejectsDuplicateRequestKeys() {
        EscrowTransaction first = EscrowTransactionFixtures.created("saved duplicate request");
        EscrowTransaction second = EscrowTransactionFixtures.created(
                UUID.fromString("0077e9b2-565c-4db4-89b9-27776c11c175"),
                "saved duplicate request");
        CompoundTag tag = savedDataTag(first, second);

        assertThrows(IllegalStateException.class, () -> EscrowTransactionSavedData.load(tag));
    }

    @Test
    void loadAndRuntimeApplyEnforceRecordLimit() {
        EscrowTransaction first = EscrowTransactionFixtures.created("saved capacity one");
        EscrowTransaction second = EscrowTransactionFixtures.created(
                UUID.fromString("88874933-9cf7-4401-ad5f-fe52e0a952fb"),
                "saved capacity two");
        CompoundTag tag = savedDataTag(first, second);
        EscrowTransactionSavedData data = new EscrowTransactionSavedData(1);
        data.applyCommitted(first);

        assertThrows(IllegalStateException.class, () -> EscrowTransactionSavedData.load(tag, 1));
        assertThrows(IllegalStateException.class, () -> data.applyCommitted(second));
    }

    private static CompoundTag savedDataTag(EscrowTransaction... transactions) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schemaVersion", 1);
        ListTag records = new ListTag();
        for (EscrowTransaction transaction : transactions) {
            records.add(EscrowTransactionNbtCodec.encode(transaction));
        }
        tag.put("transactions", records);
        return tag;
    }
}
