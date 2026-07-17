package com.enviouse.futureshops.server.economy.migration;

import com.enviouse.futureshops.server.economy.InternalBalanceSavedData;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class LegacyBalanceMigrator {
    public static final int MAXIMUM_BATCH_SIZE = 1024;

    private final WalletInitializationGateway walletGateway;
    private final LegacyBalanceMigrationPolicy policy;

    public LegacyBalanceMigrator(WalletInitializationGateway walletGateway) {
        this(walletGateway,
                LegacyBalanceMigrationPolicy.rejectNegativeBalances());
    }

    public LegacyBalanceMigrator(
            WalletInitializationGateway walletGateway,
            LegacyBalanceMigrationPolicy policy
    ) {
        this.walletGateway = Objects.requireNonNull(
                walletGateway, "walletGateway");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public LegacyBalanceMigrationBatchResult runBatch(
            InternalBalanceSavedData legacyBalances,
            LegacyBalanceMigrationSavedData migration,
            int requestedBatchSize,
            MigrationDurabilityBarrier durabilityBarrier
    ) {
        Objects.requireNonNull(legacyBalances, "legacyBalances");
        Objects.requireNonNull(migration, "migration");
        Objects.requireNonNull(durabilityBarrier, "durabilityBarrier");
        if (requestedBatchSize <= 0) {
            throw new IllegalArgumentException(
                    "Legacy migration batch size must be positive");
        }
        int batchSize = Math.min(requestedBatchSize, MAXIMUM_BATCH_SIZE);
        if (migration.stage() == LegacyBalanceMigrationStage.FAILED) {
            return migration.result(0, migration.failureDetail());
        }

        LegacyBalanceSnapshot snapshot;
        if (migration.stage() == LegacyBalanceMigrationStage.UNINITIALIZED) {
            snapshot = captureOrFail(legacyBalances, migration,
                    durabilityBarrier);
            if (snapshot == null) {
                return migration.result(0, migration.failureDetail());
            }
            migration.initializeSnapshot(snapshot);
        } else {
            snapshot = migration.snapshot();
        }

        if (!sealAndVerifySource(legacyBalances, migration, snapshot,
                durabilityBarrier)) {
            return migration.result(0, migration.failureDetail());
        }

        if (migration.stage() == LegacyBalanceMigrationStage.SNAPSHOT_PENDING) {
            durabilityBarrier.flush();
            migration.markSnapshotDurable();
            durabilityBarrier.flush();
        }

        if (migration.stage() == LegacyBalanceMigrationStage.COMPLETE
                || migration.stage()
                == LegacyBalanceMigrationStage.IMPORTS_COMPLETE) {
            return ensureCompleteArchive(legacyBalances, migration,
                    snapshot, durabilityBarrier, 0);
        }

        List<UUID> negativePlayers = migration.negativeBalancePlayers();
        if (!negativePlayers.isEmpty()
                && (!policy.allowNegativeLegacyBalances()
                || !walletGateway.supportsNegativeLegacyBalances())) {
            migration.fail(
                    LegacyBalanceMigrationFailure.NEGATIVE_LEGACY_BALANCE,
                    "Negative legacy balances are not supported");
            durabilityBarrier.flush();
            return migration.result(0, migration.failureDetail());
        }

        int processed = 0;
        String detail = "";
        while (processed < batchSize) {
            Optional<LegacyBalanceEntry> next = migration.nextEntry();
            if (next.isEmpty()) {
                break;
            }
            LegacyBalanceEntry entry = next.orElseThrow();
            WalletInitializationRequest request =
                    WalletInitializationIds.legacyRequest(entry);
            WalletInitializationResult result = Objects.requireNonNull(
                    walletGateway.initialize(request),
                    "wallet initialization result");
            switch (result.disposition()) {
                case APPLIED, REPLAYED -> {
                }
                case RETRY_LATER -> {
                    detail = result.detail();
                    if (processed > 0) {
                        durabilityBarrier.flush();
                    }
                    return migration.result(processed, detail);
                }
                case ALREADY_INITIALIZED, CONFLICT -> {
                    migration.fail(
                            LegacyBalanceMigrationFailure.WALLET_CONFLICT,
                            result.detail().isEmpty()
                                    ? "Wallet initialization conflicts"
                                    : result.detail());
                    durabilityBarrier.flush();
                    return migration.result(processed,
                            migration.failureDetail());
                }
            }
            migration.advance(entry, request.requestId());
            processed++;
        }

        if (processed > 0) {
            durabilityBarrier.flush();
        }
        if (migration.nextEntry().isPresent()) {
            return migration.result(processed, detail);
        }

        migration.markImportsComplete();
        durabilityBarrier.flush();
        return ensureCompleteArchive(legacyBalances, migration,
                snapshot, durabilityBarrier, processed);
    }

    private static LegacyBalanceSnapshot captureOrFail(
            InternalBalanceSavedData legacyBalances,
            LegacyBalanceMigrationSavedData migration,
            MigrationDurabilityBarrier durabilityBarrier
    ) {
        try {
            return LegacyBalanceSnapshot.capture(
                    legacyBalances.snapshotBalances());
        } catch (IllegalArgumentException | NullPointerException exception) {
            migration.fail(
                    LegacyBalanceMigrationFailure.SNAPSHOT_CORRUPT,
                    "Legacy balance snapshot is invalid");
            durabilityBarrier.flush();
            return null;
        }
    }

    private static boolean sealAndVerifySource(
            InternalBalanceSavedData legacyBalances,
            LegacyBalanceMigrationSavedData migration,
            LegacyBalanceSnapshot expected,
            MigrationDurabilityBarrier durabilityBarrier
    ) {
        Map<UUID, Long> currentBalances = legacyBalances.snapshotBalances();
        LegacyBalanceSnapshot current;
        try {
            current = LegacyBalanceSnapshot.capture(currentBalances);
        } catch (IllegalArgumentException | NullPointerException exception) {
            migration.fail(
                    LegacyBalanceMigrationFailure.SNAPSHOT_CHANGED,
                    "Legacy balance source is invalid");
            durabilityBarrier.flush();
            return false;
        }
        if (!expected.equals(current)) {
            migration.fail(
                    LegacyBalanceMigrationFailure.SNAPSHOT_CHANGED,
                    "Legacy balance source changed after snapshot");
            durabilityBarrier.flush();
            return false;
        }
        Optional<String> sealedFingerprint =
                legacyBalances.migrationSnapshotFingerprint();
        if (sealedFingerprint.isPresent()
                && !sealedFingerprint.orElseThrow()
                .equals(expected.fingerprint())) {
            migration.fail(
                    LegacyBalanceMigrationFailure.SOURCE_SEAL_CONFLICT,
                    "Legacy balance source seal conflicts");
            durabilityBarrier.flush();
            return false;
        }
        if (sealedFingerprint.isEmpty()) {
            try {
                legacyBalances.sealMigrationSource(expected.fingerprint());
            } catch (IllegalArgumentException | IllegalStateException exception) {
                migration.fail(
                        LegacyBalanceMigrationFailure.SOURCE_SEAL_CONFLICT,
                        "Legacy balance source could not be sealed");
                durabilityBarrier.flush();
                return false;
            }
            durabilityBarrier.flush();
        }
        return true;
    }

    private static LegacyBalanceMigrationBatchResult ensureCompleteArchive(
            InternalBalanceSavedData legacyBalances,
            LegacyBalanceMigrationSavedData migration,
            LegacyBalanceSnapshot snapshot,
            MigrationDurabilityBarrier durabilityBarrier,
            int processed
    ) {
        try {
            legacyBalances.markMigrationArchiveReadOnly(
                    snapshot.fingerprint());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            migration.fail(
                    LegacyBalanceMigrationFailure.ARCHIVE_CONFLICT,
                    "Legacy balance archive could not be finalized");
            durabilityBarrier.flush();
            return migration.result(processed, migration.failureDetail());
        }
        if (migration.stage()
                == LegacyBalanceMigrationStage.IMPORTS_COMPLETE) {
            migration.markComplete();
        }
        durabilityBarrier.flush();
        return migration.result(processed, "");
    }
}
