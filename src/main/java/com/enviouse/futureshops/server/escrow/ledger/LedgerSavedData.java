package com.enviouse.futureshops.server.escrow.ledger;

import com.enviouse.futureshops.server.SavedDataMigrations;
import com.enviouse.futureshops.server.escrow.runtime.EscrowManagedSavedData;
import com.enviouse.futureshops.server.escrow.runtime.EscrowRuntimeStoreBinding;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class LedgerSavedData extends EscrowManagedSavedData {
    public static final String DATA_NAME = "futureshops_escrow_ledger";
    private static final int CURRENT_VERSION = 2;
    private static final int MAX_ENTRIES = 1_000_000;

    private final InMemoryLedger ledger = new InMemoryLedger();

    public static LedgerSavedData load(CompoundTag tag) {
        if (tag.contains("schemaVersion")
                && !tag.contains("schemaVersion", Tag.TAG_INT)) {
            throw new IllegalStateException("Escrow ledger schema has the wrong type");
        }
        int version = SavedDataMigrations.readVersion(tag);
        if (version > CURRENT_VERSION) {
            throw new IllegalStateException("Escrow ledger schema is newer than this build");
        }
        if (version < 0) {
            throw new IllegalStateException("Escrow ledger schema is invalid");
        }
        SavedDataMigrations.needsMigration(DATA_NAME, version, CURRENT_VERSION);
        LedgerSavedData data = new LedgerSavedData();
        Map<LedgerAccountId, Long> balances = new HashMap<>();
        Map<UUID, String> fingerprints = new HashMap<>();
        Map<String, UUID> idempotencyKeys = new HashMap<>();
        Map<UUID, LedgerTransactionReceipt> receipts = new HashMap<>();

        ListTag balanceTags = requireList(tag, "balances", version);
        requireBound(balanceTags.size());
        for (Tag value : balanceTags) {
            CompoundTag entry = (CompoundTag) value;
            LedgerAccountType type;
            try {
                type = LedgerAccountType.valueOf(entry.getString("type"));
            } catch (IllegalArgumentException ex) {
                throw new IllegalStateException("Unknown escrow ledger account type", ex);
            }
            if (!entry.contains("type", Tag.TAG_STRING)
                    || !entry.contains("owner", Tag.TAG_STRING)
                    || !entry.contains("balance", Tag.TAG_LONG)) {
                throw new IllegalStateException("Escrow ledger balance is missing");
            }
            LedgerAccountId account = new LedgerAccountId(type, entry.getString("owner"));
            Long previous = balances.put(account, entry.getLong("balance"));
            if (previous != null) {
                throw new IllegalStateException("Duplicate escrow ledger account");
            }
        }

        ListTag appliedTags = requireList(tag, "applied", version);
        requireBound(appliedTags.size());
        for (Tag value : appliedTags) {
            CompoundTag entry = (CompoundTag) value;
            if (!entry.hasUUID("transaction")
                    || !entry.contains("fingerprint", Tag.TAG_STRING)) {
                throw new IllegalStateException("Escrow ledger entry is missing transaction ID");
            }
            UUID id = entry.getUUID("transaction");
            String fingerprint = entry.getString("fingerprint");
            if (fingerprint.isBlank() || fingerprints.put(id, fingerprint) != null) {
                throw new IllegalStateException("Invalid escrow ledger transaction fingerprint");
            }
        }

        ListTag keyTags = requireList(tag, "idempotency", version);
        requireBound(keyTags.size());
        for (Tag value : keyTags) {
            CompoundTag entry = (CompoundTag) value;
            if (!entry.hasUUID("transaction")
                    || !entry.contains("key", Tag.TAG_STRING)) {
                throw new IllegalStateException("Escrow ledger idempotency entry is invalid");
            }
            String key = entry.getString("key");
            UUID transactionId = entry.getUUID("transaction");
            if (key.isBlank() || idempotencyKeys.put(key, transactionId) != null) {
                throw new IllegalStateException("Duplicate escrow ledger idempotency key");
            }
            if (!fingerprints.containsKey(transactionId)) {
                throw new IllegalStateException("Escrow ledger idempotency entry references missing transaction");
            }
        }
        ListTag receiptTags = requireList(tag, "receipts", version);
        requireBound(receiptTags.size());
        if (version < CURRENT_VERSION) {
            if (!balances.isEmpty() || !fingerprints.isEmpty() || !idempotencyKeys.isEmpty()
                    || !receiptTags.isEmpty()) {
                throw new IllegalStateException(
                        "Legacy escrow ledger state has no verifiable transaction receipts");
            }
            data.ledger.restore(Map.of(), Map.of(), Map.of(), Map.of());
            data.setDirty();
            return data;
        }

        for (Tag value : receiptTags) {
            LedgerTransactionReceipt receipt = LedgerTransactionReceiptNbtCodec.read(
                    (CompoundTag) value);
            UUID transactionId = receipt.transaction().transactionId();
            if (receipts.put(transactionId, receipt) != null) {
                throw new IllegalStateException("Duplicate escrow ledger transaction receipt");
            }
        }
        data.ledger.restore(balances, fingerprints, idempotencyKeys, receipts);
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        SavedDataMigrations.writeVersion(tag, CURRENT_VERSION);
        Map<LedgerAccountId, Long> balanceSnapshot = ledger.snapshotBalances();
        Map<UUID, String> fingerprintSnapshot = ledger.snapshotAppliedFingerprints();
        Map<String, UUID> idempotencySnapshot = ledger.snapshotIdempotencyKeys();
        Map<UUID, LedgerTransactionReceipt> receiptSnapshot =
                ledger.snapshotTransactionReceipts();
        requireBound(balanceSnapshot.size());
        requireBound(fingerprintSnapshot.size());
        requireBound(idempotencySnapshot.size());
        requireBound(receiptSnapshot.size());
        ListTag balances = new ListTag();
        for (Map.Entry<LedgerAccountId, Long> entry : balanceSnapshot.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(accountComparator())).toList()) {
            CompoundTag value = new CompoundTag();
            value.putString("type", entry.getKey().type().name());
            value.putString("owner", entry.getKey().ownerKey());
            value.putLong("balance", entry.getValue());
            balances.add(value);
        }
        tag.put("balances", balances);

        ListTag applied = new ListTag();
        for (Map.Entry<UUID, String> entry : fingerprintSnapshot.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(UUID::toString)))
                .toList()) {
            CompoundTag value = new CompoundTag();
            value.putUUID("transaction", entry.getKey());
            value.putString("fingerprint", entry.getValue());
            applied.add(value);
        }
        tag.put("applied", applied);

        ListTag keys = new ListTag();
        for (Map.Entry<String, UUID> entry : idempotencySnapshot.entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).toList()) {
            CompoundTag value = new CompoundTag();
            value.putString("key", entry.getKey());
            value.putUUID("transaction", entry.getValue());
            keys.add(value);
        }
        tag.put("idempotency", keys);

        ListTag receipts = new ListTag();
        for (LedgerTransactionReceipt receipt : receiptSnapshot.values().stream()
                .sorted(Comparator.comparingLong(
                                LedgerTransactionReceipt::applicationSequence)
                        .thenComparing(value ->
                                value.transaction().transactionId().toString()))
                .toList()) {
            receipts.add(LedgerTransactionReceiptNbtCodec.write(receipt));
        }
        tag.put("receipts", receipts);
        return tag;
    }

    public static LedgerSavedData get(MinecraftServer server) {
        return EscrowRuntimeStoreBinding.bind(server,
                server.overworld().getDataStorage().computeIfAbsent(
                        LedgerSavedData::load, LedgerSavedData::new, DATA_NAME));
    }

    public synchronized void replaceFromValidated(LedgerSavedData source) {
        requireEscrowMutationPermit();
        Objects.requireNonNull(source, "source");
        if (source == this) {
            return;
        }
        LedgerStateSnapshot snapshot = source.snapshotForRestore();
        ledger.restore(snapshot.balances(), snapshot.fingerprints(),
                snapshot.idempotencyKeys(), snapshot.receipts());
        setDirty();
    }

    public synchronized LedgerApplyResult applyCommitted(LedgerTransaction transaction) {
        requireEscrowMutationPermit();
        LedgerApplyResult result = ledger.apply(transaction);
        if (result.applied()) {
            setDirty();
        }
        return result;
    }

    public synchronized LedgerApplyResult preflightCommitted(LedgerTransaction transaction) {
        return ledger.preflight(transaction);
    }

    public synchronized long balance(LedgerAccountId account) {
        return ledger.balance(account);
    }

    public synchronized boolean containsAccount(LedgerAccountId account) {
        return ledger.containsAccount(account);
    }

    public synchronized Map<LedgerAccountId, Long> snapshotBalances() {
        return ledger.snapshotBalances();
    }

    public synchronized boolean wasApplied(UUID transactionId) {
        return ledger.wasApplied(transactionId);
    }

    public synchronized Optional<LedgerTransactionReceipt> transactionReceipt(
            UUID transactionId
    ) {
        return ledger.transactionReceipt(transactionId);
    }

    public synchronized boolean hasMaterializedState() {
        return ledger.hasMaterializedState();
    }

    private static void requireBound(int size) {
        if (size < 0 || size > MAX_ENTRIES) {
            throw new IllegalStateException("Escrow ledger exceeds entry limit");
        }
    }

    private static ListTag requireList(CompoundTag tag, String key, int version) {
        if (!tag.contains(key)) {
            if (version == CURRENT_VERSION) {
                throw new IllegalStateException("Escrow ledger data is missing");
            }
            return new ListTag();
        }
        Tag raw = tag.get(key);
        if (!(raw instanceof ListTag list)
                || (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)) {
            throw new IllegalStateException("Escrow ledger data has the wrong type");
        }
        return list;
    }

    private synchronized LedgerStateSnapshot snapshotForRestore() {
        return new LedgerStateSnapshot(ledger.snapshotBalances(),
                ledger.snapshotAppliedFingerprints(), ledger.snapshotIdempotencyKeys(),
                ledger.snapshotTransactionReceipts());
    }

    private static Comparator<LedgerAccountId> accountComparator() {
        return Comparator.comparing((LedgerAccountId value) -> value.type().ordinal())
                .thenComparing(LedgerAccountId::ownerKey);
    }

    private record LedgerStateSnapshot(Map<LedgerAccountId, Long> balances,
                                       Map<UUID, String> fingerprints,
                                       Map<String, UUID> idempotencyKeys,
                                       Map<UUID, LedgerTransactionReceipt> receipts) {
    }
}
