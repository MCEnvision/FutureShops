package com.enviouse.futureshops.server.escrow.ledger;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LedgerSavedDataTest {
    private static final LedgerAccountId SOURCE = LedgerAccountId.system(
            LedgerAccountType.ADMIN_SOURCE);
    private static final LedgerAccountId FIRST = new LedgerAccountId(
            LedgerAccountType.PLAYER_WALLET, "player one");
    private static final LedgerAccountId SECOND = new LedgerAccountId(
            LedgerAccountType.PLAYER_WALLET, "player two");

    @Test
    void saveAndLoadPreserveCanonicalReceiptsBalancesAndIdempotency() {
        LedgerSavedData data = populatedLedger();
        CompoundTag saved = data.save(new CompoundTag());

        LedgerSavedData loaded = LedgerSavedData.load(saved);
        CompoundTag resaved = loaded.save(new CompoundTag());

        assertEquals(60L, loaded.balance(FIRST));
        assertEquals(40L, loaded.balance(SECOND));
        assertEquals(-100L, loaded.balance(SOURCE));
        assertEquals(saved, resaved);
        assertTrue(loaded.applyCommitted(seedTransaction()).replayed());
    }

    @Test
    void balancedTwoPlayerOwnershipTamperFailsClosed() {
        CompoundTag saved = populatedLedger().save(new CompoundTag());
        balance(saved, FIRST).putLong("balance", 61L);
        balance(saved, SECOND).putLong("balance", 39L);

        assertThrows(IllegalArgumentException.class, () -> LedgerSavedData.load(saved));
    }

    @Test
    void balancedReceiptLegTamperFailsFingerprintValidation() {
        CompoundTag saved = populatedLedger().save(new CompoundTag());
        CompoundTag receipt = saved.getList("receipts", Tag.TAG_COMPOUND).getCompound(0);
        ListTag legs = receipt.getList("legs", Tag.TAG_COMPOUND);
        legs.getCompound(0).putLong("delta", 99L);
        legs.getCompound(1).putLong("delta", -99L);

        assertThrows(IllegalStateException.class, () -> LedgerSavedData.load(saved));
    }

    @Test
    void missingTransactionReceiptFailsClosed() {
        CompoundTag saved = populatedLedger().save(new CompoundTag());
        saved.getList("receipts", Tag.TAG_COMPOUND).remove(0);

        assertThrows(IllegalArgumentException.class, () -> LedgerSavedData.load(saved));
    }

    @Test
    void extraTransactionReceiptFailsClosed() {
        CompoundTag saved = populatedLedger().save(new CompoundTag());
        LedgerTransaction extra = transfer(uuid(30L), "extra receipt", SOURCE, -1L,
                SECOND, 1L);
        ListTag receipts = saved.getList("receipts", Tag.TAG_COMPOUND);
        receipts.add(LedgerTransactionReceiptNbtCodec.write(
                LedgerTransactionReceipt.create(receipts.size(), extra)));

        assertThrows(IllegalArgumentException.class, () -> LedgerSavedData.load(saved));
    }

    @Test
    void idempotencyMappingMismatchFailsClosed() {
        CompoundTag saved = populatedLedger().save(new CompoundTag());
        ListTag keys = saved.getList("idempotency", Tag.TAG_COMPOUND);
        keys.getCompound(0).putString("key", "tampered idempotency");

        assertThrows(IllegalArgumentException.class, () -> LedgerSavedData.load(saved));
    }

    @Test
    void receiptReconstructionOverflowFailsClosed() {
        LedgerSavedData data = new LedgerSavedData();
        data.applyCommitted(transfer(uuid(40L), "maximum seed", SOURCE,
                -Long.MAX_VALUE, FIRST, Long.MAX_VALUE));
        data.applyCommitted(transfer(uuid(41L), "minimum seed", SOURCE,
                -1L, SECOND, 1L));
        CompoundTag saved = data.save(new CompoundTag());
        LedgerAccountId third = new LedgerAccountId(
                LedgerAccountType.PLAYER_WALLET, "player three");
        LedgerTransaction overflow = transfer(uuid(42L), "overflow receipt", SOURCE,
                -1L, third, 1L);
        addReceiptIndexes(saved, 2L, overflow);

        assertThrows(IllegalArgumentException.class, () -> LedgerSavedData.load(saved));
    }

    @Test
    void trulyEmptyLegacyStateMigratesToReceiptSchema() {
        CompoundTag legacy = new CompoundTag();
        legacy.putInt("schemaVersion", 1);
        legacy.put("balances", new ListTag());
        legacy.put("applied", new ListTag());
        legacy.put("idempotency", new ListTag());

        LedgerSavedData loaded = LedgerSavedData.load(legacy);
        CompoundTag migrated = loaded.save(new CompoundTag());

        assertEquals(2, migrated.getInt("schemaVersion"));
        assertTrue(migrated.getList("receipts", Tag.TAG_COMPOUND).isEmpty());
        assertTrue(loaded.isDirty());
    }

    @Test
    void nonemptyLegacyStateWithoutReceiptsFailsClosed() {
        CompoundTag legacy = populatedLedger().save(new CompoundTag());
        legacy.putInt("schemaVersion", 1);
        legacy.remove("receipts");

        assertThrows(IllegalStateException.class, () -> LedgerSavedData.load(legacy));
    }

    @Test
    void newerSchemaFailsClosed() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schemaVersion", Integer.MAX_VALUE);

        assertThrows(IllegalStateException.class, () -> LedgerSavedData.load(tag));
    }

    @Test
    void currentSchemaMissingStoresFailsClosed() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schemaVersion", 2);

        assertThrows(IllegalStateException.class, () -> LedgerSavedData.load(tag));
    }

    @Test
    void globallyNonzeroRestoredBalanceFailsClosed() {
        CompoundTag tag = emptyCurrentTag();
        CompoundTag balance = new CompoundTag();
        balance.putString("type", LedgerAccountType.PLAYER_RESERVED.name());
        balance.putString("owner", "player");
        balance.putLong("balance", 1L);
        tag.getList("balances", Tag.TAG_COMPOUND).add(balance);

        assertThrows(IllegalArgumentException.class, () -> LedgerSavedData.load(tag));
    }

    private static LedgerSavedData populatedLedger() {
        LedgerSavedData data = new LedgerSavedData();
        data.applyCommitted(seedTransaction());
        data.applyCommitted(transfer(uuid(2L), "player transfer", FIRST, -40L,
                SECOND, 40L));
        return data;
    }

    private static LedgerTransaction seedTransaction() {
        return transfer(uuid(1L), "seed players", SOURCE, -100L, FIRST, 100L);
    }

    private static LedgerTransaction transfer(UUID transactionId,
                                              String key,
                                              LedgerAccountId debitAccount,
                                              long debit,
                                              LedgerAccountId creditAccount,
                                              long credit) {
        return new LedgerTransaction(transactionId, key, key, List.of(
                new LedgerLeg(debitAccount, debit),
                new LedgerLeg(creditAccount, credit)));
    }

    private static CompoundTag balance(CompoundTag saved, LedgerAccountId account) {
        for (Tag raw : saved.getList("balances", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            if (entry.getString("type").equals(account.type().name())
                    && entry.getString("owner").equals(account.ownerKey())) {
                return entry;
            }
        }
        throw new AssertionError("Missing ledger balance");
    }

    private static void addReceiptIndexes(CompoundTag saved,
                                          long sequence,
                                          LedgerTransaction transaction) {
        LedgerTransactionReceipt receipt = LedgerTransactionReceipt.create(
                sequence, transaction);
        saved.getList("receipts", Tag.TAG_COMPOUND).add(
                LedgerTransactionReceiptNbtCodec.write(receipt));
        CompoundTag applied = new CompoundTag();
        applied.putUUID("transaction", transaction.transactionId());
        applied.putString("fingerprint", transaction.fingerprint());
        saved.getList("applied", Tag.TAG_COMPOUND).add(applied);
        CompoundTag key = new CompoundTag();
        key.putString("key", transaction.idempotencyKey());
        key.putUUID("transaction", transaction.transactionId());
        saved.getList("idempotency", Tag.TAG_COMPOUND).add(key);
    }

    private static CompoundTag emptyCurrentTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schemaVersion", 2);
        tag.put("balances", new ListTag());
        tag.put("applied", new ListTag());
        tag.put("idempotency", new ListTag());
        tag.put("receipts", new ListTag());
        return tag;
    }

    private static UUID uuid(long value) {
        return new UUID(0L, value);
    }
}
