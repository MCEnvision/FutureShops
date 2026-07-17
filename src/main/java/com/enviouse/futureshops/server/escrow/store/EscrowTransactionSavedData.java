package com.enviouse.futureshops.server.escrow.store;

import com.enviouse.futureshops.server.SavedDataMigrations;
import com.enviouse.futureshops.server.escrow.model.EscrowRequestKey;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import com.enviouse.futureshops.server.escrow.model.EscrowTransactionId;
import com.enviouse.futureshops.server.escrow.runtime.EscrowManagedSavedData;
import com.enviouse.futureshops.server.escrow.runtime.EscrowRuntimeStoreBinding;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class EscrowTransactionSavedData extends EscrowManagedSavedData {
    public static final String DATA_NAME = "futureshops_escrow_transactions";
    public static final int MAX_RECORDS = 1_000_000;
    private static final int CURRENT_VERSION = 1;

    private final EscrowTransactionRepository repository;

    public EscrowTransactionSavedData() {
        this(MAX_RECORDS);
    }

    EscrowTransactionSavedData(int maximumRecords) {
        repository = new EscrowTransactionRepository(maximumRecords);
    }

    public static EscrowTransactionSavedData load(CompoundTag tag) {
        return load(tag, MAX_RECORDS);
    }

    static EscrowTransactionSavedData load(CompoundTag tag, int maximumRecords) {
        if (tag.contains("schemaVersion")
                && !tag.contains("schemaVersion", Tag.TAG_INT)) {
            throw new IllegalStateException("Escrow transaction schema has the wrong type");
        }
        int version = SavedDataMigrations.readVersion(tag);
        if (version > CURRENT_VERSION) {
            throw new IllegalStateException("Escrow transaction schema is newer than this build");
        }
        if (version < 0) {
            throw new IllegalStateException("Escrow transaction schema is invalid");
        }
        SavedDataMigrations.needsMigration(DATA_NAME, version, CURRENT_VERSION);
        EscrowTransactionSavedData data = new EscrowTransactionSavedData(maximumRecords);
        List<EscrowTransaction> transactions = new ArrayList<>();
        if (tag.contains("transactions")) {
            if (!tag.contains("transactions", Tag.TAG_LIST)) {
                throw new IllegalStateException("Escrow transaction records have the wrong type");
            }
            ListTag records = (ListTag) tag.get("transactions");
            if (!records.isEmpty() && records.getElementType() != Tag.TAG_COMPOUND) {
                throw new IllegalStateException("Escrow transaction record type is invalid");
            }
            requireRecordCount(records.size(), maximumRecords);
            for (Tag raw : records) {
                if (!(raw instanceof CompoundTag record)) {
                    throw new IllegalStateException("Escrow transaction record is invalid");
                }
                transactions.add(EscrowTransactionNbtCodec.decode(record));
            }
        } else if (version == CURRENT_VERSION) {
            throw new IllegalStateException("Escrow transaction records are missing");
        }
        data.repository.restore(transactions);
        if (version < CURRENT_VERSION) {
            data.setDirty();
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        SavedDataMigrations.writeVersion(tag, CURRENT_VERSION);
        List<EscrowTransaction> transactions = new ArrayList<>(repository.snapshot().values());
        requireRecordCount(transactions.size(), MAX_RECORDS);
        transactions.sort(Comparator.comparing(value -> value.transactionId().toString()));
        ListTag records = new ListTag();
        for (EscrowTransaction transaction : transactions) {
            records.add(EscrowTransactionNbtCodec.encode(transaction));
        }
        tag.put("transactions", records);
        return tag;
    }

    public static EscrowTransactionSavedData get(MinecraftServer server) {
        return EscrowRuntimeStoreBinding.bind(server,
                server.overworld().getDataStorage().computeIfAbsent(
                EscrowTransactionSavedData::load,
                EscrowTransactionSavedData::new,
                DATA_NAME));
    }

    public synchronized void replaceFromValidated(EscrowTransactionSavedData source) {
        requireEscrowMutationPermit();
        Objects.requireNonNull(source, "source");
        if (source == this) {
            return;
        }
        repository.restore(source.snapshotTransactions().values());
        setDirty();
    }

    public synchronized EscrowStoreApplyResult applyCommitted(EscrowTransaction transaction) {
        requireEscrowMutationPermit();
        EscrowStoreApplyResult result = repository.apply(transaction);
        if (result.applied()) {
            setDirty();
        }
        return result;
    }

    public synchronized EscrowStoreApplyResult preflightCommitted(EscrowTransaction transaction) {
        return repository.preflight(transaction);
    }

    public synchronized EscrowTransaction getTransaction(EscrowTransactionId transactionId) {
        return repository.get(transactionId);
    }

    public synchronized EscrowTransaction getByRequestKey(EscrowRequestKey requestKey) {
        return repository.getByRequestKey(requestKey);
    }

    public synchronized Map<EscrowTransactionId, EscrowTransaction> snapshotTransactions() {
        return repository.snapshot();
    }

    public synchronized List<EscrowTransaction> recoveryCandidatesAfter(
            Optional<EscrowTransactionId> after,
            int limit
    ) {
        return repository.recoveryCandidatesAfter(after, limit);
    }

    public synchronized boolean hasMaterializedState() {
        return repository.size() > 0;
    }

    private static void requireRecordCount(int count, int maximumRecords) {
        if (count < 0 || count > maximumRecords) {
            throw new IllegalStateException("Escrow transaction store exceeds its record limit");
        }
    }
}
