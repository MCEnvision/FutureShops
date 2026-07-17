package com.enviouse.futureshops.server.escrow.checkpoint;

import com.enviouse.futureshops.server.escrow.admin.EscrowAdministrativeAuditSavedData;
import com.enviouse.futureshops.server.escrow.claim.ClaimSavedData;
import com.enviouse.futureshops.server.escrow.custody.CustodySavedData;
import com.enviouse.futureshops.server.escrow.ledger.LedgerSavedData;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintSavedData;
import com.enviouse.futureshops.server.escrow.runtime.EscrowCheckpointSnapshotBundle;
import com.enviouse.futureshops.server.escrow.runtime.EscrowPreparedCheckpointRestore;
import com.enviouse.futureshops.server.escrow.runtime.EscrowMutationPermit;
import com.enviouse.futureshops.server.escrow.runtime.EscrowRuntimeSavedData;
import com.enviouse.futureshops.server.escrow.store.EscrowTransactionSavedData;
import com.enviouse.futureshops.server.escrow.stock.StockSavedData;
import net.minecraft.nbt.CompoundTag;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

public final class EscrowSavedDataCheckpointBundle implements EscrowCheckpointSnapshotBundle {
    private final EscrowTransactionSavedData transactions;
    private final LedgerSavedData ledger;
    private final ClaimSavedData claims;
    private final EscrowAdministrativeAuditSavedData administrativeAudit;
    private final CustodySavedData custody;
    private final ProtectedMintSavedData protectedMints;
    private final StockSavedData stock;
    private final EscrowRuntimeSavedData runtimeMetadata;
    private final BooleanSupplier serverThreadCheck;
    private final int maximumStoreBytes;
    private final long maximumAggregateBytes;
    private final Object restoreLock = new Object();
    private final EscrowMutationPermit mutationPermit;

    public EscrowSavedDataCheckpointBundle(
            EscrowTransactionSavedData transactions,
            LedgerSavedData ledger,
            ClaimSavedData claims,
            EscrowAdministrativeAuditSavedData administrativeAudit,
            CustodySavedData custody,
            ProtectedMintSavedData protectedMints,
            EscrowRuntimeSavedData runtimeMetadata,
            BooleanSupplier serverThreadCheck) {
        this(transactions, ledger, claims, administrativeAudit, custody, protectedMints,
                new StockSavedData(), runtimeMetadata, serverThreadCheck,
                EscrowCheckpoint.MAX_STORE_BYTES,
                EscrowCheckpoint.MAX_AGGREGATE_STORE_BYTES, null);
    }

    public EscrowSavedDataCheckpointBundle(
            EscrowTransactionSavedData transactions,
            LedgerSavedData ledger,
            ClaimSavedData claims,
            EscrowAdministrativeAuditSavedData administrativeAudit,
            CustodySavedData custody,
            ProtectedMintSavedData protectedMints,
            EscrowRuntimeSavedData runtimeMetadata,
            BooleanSupplier serverThreadCheck,
            EscrowMutationPermit mutationPermit) {
        this(transactions, ledger, claims, administrativeAudit, custody, protectedMints,
                new StockSavedData(), runtimeMetadata, serverThreadCheck,
                EscrowCheckpoint.MAX_STORE_BYTES,
                EscrowCheckpoint.MAX_AGGREGATE_STORE_BYTES,
                Objects.requireNonNull(mutationPermit, "mutationPermit"));
    }

    public EscrowSavedDataCheckpointBundle(
            EscrowTransactionSavedData transactions,
            LedgerSavedData ledger,
            ClaimSavedData claims,
            EscrowAdministrativeAuditSavedData administrativeAudit,
            CustodySavedData custody,
            ProtectedMintSavedData protectedMints,
            StockSavedData stock,
            EscrowRuntimeSavedData runtimeMetadata,
            BooleanSupplier serverThreadCheck) {
        this(transactions, ledger, claims, administrativeAudit, custody,
                protectedMints, stock, runtimeMetadata, serverThreadCheck,
                EscrowCheckpoint.MAX_STORE_BYTES,
                EscrowCheckpoint.MAX_AGGREGATE_STORE_BYTES, null);
    }

    public EscrowSavedDataCheckpointBundle(
            EscrowTransactionSavedData transactions,
            LedgerSavedData ledger,
            ClaimSavedData claims,
            EscrowAdministrativeAuditSavedData administrativeAudit,
            CustodySavedData custody,
            ProtectedMintSavedData protectedMints,
            StockSavedData stock,
            EscrowRuntimeSavedData runtimeMetadata,
            BooleanSupplier serverThreadCheck,
            EscrowMutationPermit mutationPermit) {
        this(transactions, ledger, claims, administrativeAudit, custody,
                protectedMints, stock, runtimeMetadata, serverThreadCheck,
                EscrowCheckpoint.MAX_STORE_BYTES,
                EscrowCheckpoint.MAX_AGGREGATE_STORE_BYTES,
                Objects.requireNonNull(mutationPermit, "mutationPermit"));
    }

    EscrowSavedDataCheckpointBundle(
            EscrowTransactionSavedData transactions,
            LedgerSavedData ledger,
            ClaimSavedData claims,
            EscrowAdministrativeAuditSavedData administrativeAudit,
            CustodySavedData custody,
            ProtectedMintSavedData protectedMints,
            EscrowRuntimeSavedData runtimeMetadata,
            BooleanSupplier serverThreadCheck,
            int maximumStoreBytes,
            long maximumAggregateBytes) {
        this(transactions, ledger, claims, administrativeAudit, custody, protectedMints,
                new StockSavedData(), runtimeMetadata, serverThreadCheck,
                maximumStoreBytes,
                maximumAggregateBytes, null);
    }

    EscrowSavedDataCheckpointBundle(
            EscrowTransactionSavedData transactions,
            LedgerSavedData ledger,
            ClaimSavedData claims,
            EscrowAdministrativeAuditSavedData administrativeAudit,
            CustodySavedData custody,
            ProtectedMintSavedData protectedMints,
            EscrowRuntimeSavedData runtimeMetadata,
            BooleanSupplier serverThreadCheck,
            int maximumStoreBytes,
            long maximumAggregateBytes,
            EscrowMutationPermit mutationPermit) {
        this(transactions, ledger, claims, administrativeAudit, custody,
                protectedMints, new StockSavedData(), runtimeMetadata,
                serverThreadCheck, maximumStoreBytes, maximumAggregateBytes,
                mutationPermit);
    }

    EscrowSavedDataCheckpointBundle(
            EscrowTransactionSavedData transactions,
            LedgerSavedData ledger,
            ClaimSavedData claims,
            EscrowAdministrativeAuditSavedData administrativeAudit,
            CustodySavedData custody,
            ProtectedMintSavedData protectedMints,
            StockSavedData stock,
            EscrowRuntimeSavedData runtimeMetadata,
            BooleanSupplier serverThreadCheck,
            int maximumStoreBytes,
            long maximumAggregateBytes,
            EscrowMutationPermit mutationPermit) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.claims = Objects.requireNonNull(claims, "claims");
        this.administrativeAudit = Objects.requireNonNull(administrativeAudit,
                "administrativeAudit");
        this.custody = Objects.requireNonNull(custody, "custody");
        this.protectedMints = Objects.requireNonNull(protectedMints, "protectedMints");
        this.stock = Objects.requireNonNull(stock, "stock");
        this.runtimeMetadata = Objects.requireNonNull(runtimeMetadata, "runtimeMetadata");
        this.serverThreadCheck = Objects.requireNonNull(serverThreadCheck,
                "serverThreadCheck");
        this.mutationPermit = mutationPermit;
        if (maximumStoreBytes <= EscrowCheckpointComponentCodec.FIXED_BYTES
                || maximumStoreBytes > EscrowCheckpoint.MAX_STORE_BYTES
                || maximumAggregateBytes <= 0L
                || maximumAggregateBytes > EscrowCheckpoint.MAX_AGGREGATE_STORE_BYTES) {
            throw new IllegalArgumentException("Escrow checkpoint snapshot limits are invalid");
        }
        this.maximumStoreBytes = maximumStoreBytes;
        this.maximumAggregateBytes = maximumAggregateBytes;
    }

    @Override
    public Map<EscrowCheckpointStore, byte[]> captureSnapshots() {
        requireServerThread();
        synchronized (restoreLock) {
            EnumMap<EscrowCheckpointStore, byte[]> snapshots =
                    new EnumMap<>(EscrowCheckpointStore.class);
            snapshots.put(EscrowCheckpointStore.TRANSACTIONS,
                    encode(EscrowCheckpointStore.TRANSACTIONS,
                            transactions.save(new CompoundTag())));
            snapshots.put(EscrowCheckpointStore.LEDGER,
                    encode(EscrowCheckpointStore.LEDGER,
                            ledger.save(new CompoundTag())));
            snapshots.put(EscrowCheckpointStore.CLAIMS,
                    encode(EscrowCheckpointStore.CLAIMS,
                            claims.save(new CompoundTag())));
            snapshots.put(EscrowCheckpointStore.ADMINISTRATIVE_AUDIT,
                    encode(EscrowCheckpointStore.ADMINISTRATIVE_AUDIT,
                            administrativeAudit.save(new CompoundTag())));
            snapshots.put(EscrowCheckpointStore.CUSTODY,
                    encode(EscrowCheckpointStore.CUSTODY,
                            custody.save(new CompoundTag())));
            snapshots.put(EscrowCheckpointStore.PROTECTED_MINT,
                    encode(EscrowCheckpointStore.PROTECTED_MINT,
                            protectedMints.save(new CompoundTag())));
            snapshots.put(EscrowCheckpointStore.STOCK,
                    encode(EscrowCheckpointStore.STOCK,
                            stock.save(new CompoundTag())));
            snapshots.put(EscrowCheckpointStore.RUNTIME_METADATA,
                    encode(EscrowCheckpointStore.RUNTIME_METADATA,
                            runtimeMetadata.save(new CompoundTag())));
            validateAggregate(snapshots);
            return immutableCopy(snapshots);
        }
    }

    @Override
    public EscrowPreparedCheckpointRestore prepareTrustedRestore(
            TrustedEscrowCheckpoint trustedCheckpoint) {
        Objects.requireNonNull(trustedCheckpoint, "trustedCheckpoint");
        EscrowCheckpoint checkpoint = trustedCheckpoint.checkpoint();
        return prepareSnapshots(checkpoint.snapshots(),
                checkpoint.sourceJournalLineageId(), checkpoint.baseJournalSequence());
    }

    public EscrowPreparedCheckpointRestore prepareSnapshots(
            Map<EscrowCheckpointStore, byte[]> snapshots,
            UUID expectedSourceLineage,
            long expectedBaseSequence) {
        Objects.requireNonNull(expectedSourceLineage, "expectedSourceLineage");
        if (expectedBaseSequence < 1L) {
            throw new IllegalArgumentException(
                    "Escrow checkpoint expected base sequence is invalid");
        }
        Map<EscrowCheckpointStore, byte[]> copied = validateAndCopy(snapshots);
        PreparedStores prepared = decodeAndValidate(copied);
        if (!prepared.runtimeMetadata().journalLineage()
                .filter(expectedSourceLineage::equals).isPresent()
                || prepared.runtimeMetadata().lastAppliedSequence()
                != expectedBaseSequence) {
            throw new EscrowCheckpointSnapshotException(
                    "Escrow checkpoint runtime metadata does not match its journal base");
        }
        return new PreparedRestore(this, prepared);
    }

    private PreparedStores decodeAndValidate(Map<EscrowCheckpointStore, byte[]> snapshots) {
        EscrowTransactionSavedData decodedTransactions = load(
                EscrowCheckpointStore.TRANSACTIONS, snapshots,
                EscrowTransactionSavedData::load);
        LedgerSavedData decodedLedger = load(EscrowCheckpointStore.LEDGER, snapshots,
                LedgerSavedData::load);
        ClaimSavedData decodedClaims = load(EscrowCheckpointStore.CLAIMS, snapshots,
                ClaimSavedData::load);
        EscrowAdministrativeAuditSavedData decodedAudit = load(
                EscrowCheckpointStore.ADMINISTRATIVE_AUDIT, snapshots,
                EscrowAdministrativeAuditSavedData::load);
        CustodySavedData decodedCustody = load(EscrowCheckpointStore.CUSTODY, snapshots,
                CustodySavedData::load);
        ProtectedMintSavedData decodedMints = load(EscrowCheckpointStore.PROTECTED_MINT,
                snapshots, ProtectedMintSavedData::load);
        StockSavedData decodedStock = load(EscrowCheckpointStore.STOCK,
                snapshots, StockSavedData::load);
        EscrowRuntimeSavedData decodedRuntime = load(
                EscrowCheckpointStore.RUNTIME_METADATA, snapshots,
                EscrowRuntimeSavedData::load);
        return new PreparedStores(decodedTransactions, decodedLedger, decodedClaims,
                decodedAudit, decodedCustody, decodedMints, decodedStock,
                decodedRuntime);
    }

    private <T> T load(EscrowCheckpointStore store,
                       Map<EscrowCheckpointStore, byte[]> snapshots,
                       Function<CompoundTag, T> loader) {
        CompoundTag tag = EscrowCheckpointComponentCodec.decode(store,
                snapshots.get(store), maximumStoreBytes);
        try {
            return Objects.requireNonNull(loader.apply(tag), "decoded checkpoint store");
        } catch (RuntimeException exception) {
            throw new EscrowCheckpointSnapshotException(
                    "Escrow checkpoint store failed validation, " + store.name(), exception);
        }
    }

    private byte[] encode(EscrowCheckpointStore store, CompoundTag tag) {
        return EscrowCheckpointComponentCodec.encode(store, tag, maximumStoreBytes);
    }

    private Map<EscrowCheckpointStore, byte[]> validateAndCopy(
            Map<EscrowCheckpointStore, byte[]> snapshots) {
        Objects.requireNonNull(snapshots, "snapshots");
        if (snapshots.size() != EscrowCheckpointStore.values().length) {
            throw new EscrowCheckpointSnapshotException(
                    "Escrow checkpoint snapshot component count is invalid");
        }
        EnumMap<EscrowCheckpointStore, byte[]> copied =
                new EnumMap<>(EscrowCheckpointStore.class);
        for (EscrowCheckpointStore store : EscrowCheckpointStore.values()) {
            byte[] value = snapshots.get(store);
            if (value == null) {
                throw new EscrowCheckpointSnapshotException(
                        "Escrow checkpoint snapshot component is missing");
            }
            if (value.length > maximumStoreBytes) {
                throw new EscrowCheckpointSnapshotException(
                        "Escrow checkpoint snapshot component exceeds its size limit");
            }
            copied.put(store, value.clone());
        }
        validateAggregate(copied);
        return Collections.unmodifiableMap(copied);
    }

    private void validateAggregate(Map<EscrowCheckpointStore, byte[]> snapshots) {
        long aggregate = 0L;
        try {
            for (EscrowCheckpointStore store : EscrowCheckpointStore.values()) {
                byte[] value = Objects.requireNonNull(snapshots.get(store),
                        "snapshot component");
                if (value.length > maximumStoreBytes) {
                    throw new EscrowCheckpointSnapshotException(
                            "Escrow checkpoint snapshot component exceeds its size limit");
                }
                aggregate = Math.addExact(aggregate, value.length);
                if (aggregate > maximumAggregateBytes) {
                    throw new EscrowCheckpointSnapshotException(
                            "Escrow checkpoint snapshots exceed their aggregate size limit");
                }
            }
        } catch (ArithmeticException exception) {
            throw new EscrowCheckpointSnapshotException(
                    "Escrow checkpoint snapshot size overflowed", exception);
        }
    }

    private void applyPrepared(PreparedStores prepared) {
        requireServerThread();
        synchronized (restoreLock) {
            if (mutationPermit == null) {
                applyPreparedAuthorized(prepared);
                return;
            }
            try (EscrowMutationPermit.Scope ignored = mutationPermit.activate()) {
                applyPreparedAuthorized(prepared);
            }
        }
    }

    private void applyPreparedAuthorized(PreparedStores prepared) {
        transactions.replaceFromValidated(prepared.transactions());
        ledger.replaceFromValidated(prepared.ledger());
        claims.replaceFromValidated(prepared.claims());
        administrativeAudit.replaceFromValidated(prepared.administrativeAudit());
        custody.replaceFromValidated(prepared.custody());
        protectedMints.replaceFromValidated(prepared.protectedMints());
        stock.replaceFromValidated(prepared.stock());
        runtimeMetadata.replaceFromValidated(prepared.runtimeMetadata());
    }

    private void requireServerThread() {
        if (!serverThreadCheck.getAsBoolean()) {
            throw new IllegalStateException(
                    "Escrow checkpoint snapshot operation requires the server thread");
        }
    }

    private static Map<EscrowCheckpointStore, byte[]> immutableCopy(
            Map<EscrowCheckpointStore, byte[]> snapshots) {
        EnumMap<EscrowCheckpointStore, byte[]> copied =
                new EnumMap<>(EscrowCheckpointStore.class);
        snapshots.forEach((store, value) -> copied.put(store, value.clone()));
        return Collections.unmodifiableMap(copied);
    }

    private record PreparedStores(
            EscrowTransactionSavedData transactions,
            LedgerSavedData ledger,
            ClaimSavedData claims,
            EscrowAdministrativeAuditSavedData administrativeAudit,
            CustodySavedData custody,
            ProtectedMintSavedData protectedMints,
            StockSavedData stock,
            EscrowRuntimeSavedData runtimeMetadata) {
    }

    private static final class PreparedRestore implements EscrowPreparedCheckpointRestore {
        private final EscrowSavedDataCheckpointBundle owner;
        private final PreparedStores prepared;
        private boolean applied;

        private PreparedRestore(EscrowSavedDataCheckpointBundle owner,
                                PreparedStores prepared) {
            this.owner = owner;
            this.prepared = prepared;
        }

        @Override
        public synchronized void apply() {
            if (applied) {
                throw new IllegalStateException(
                        "Escrow checkpoint restore was already applied");
            }
            owner.applyPrepared(prepared);
            applied = true;
        }
    }
}
