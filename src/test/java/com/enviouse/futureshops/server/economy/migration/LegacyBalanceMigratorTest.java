package com.enviouse.futureshops.server.economy.migration;

import com.enviouse.futureshops.server.economy.InternalBalanceSavedData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyBalanceMigratorTest {
    private static final UUID FIRST = UUID.fromString(
            "00000000-0000-0000-0000-000000000001");
    private static final UUID SECOND = UUID.fromString(
            "10000000-0000-0000-0000-000000000000");
    private static final UUID THIRD = UUID.fromString(
            "ffffffff-ffff-ffff-ffff-ffffffffffff");

    @Test
    void importsInUuidOrderAndPreservesLegacyArchive() {
        InternalBalanceSavedData legacy = balances(Map.of(
                THIRD, 0L,
                FIRST, 75L,
                SECOND, 25L));
        Map<UUID, Long> original = legacy.snapshotBalances();
        RecordingGateway gateway = new RecordingGateway(false);
        LegacyBalanceMigrationSavedData migration =
                new LegacyBalanceMigrationSavedData();

        LegacyBalanceMigrationBatchResult result =
                new LegacyBalanceMigrator(gateway).runBatch(
                        legacy, migration, 10, () -> { });

        assertEquals(LegacyBalanceMigrationStage.COMPLETE, result.stage());
        assertEquals(List.of(FIRST, SECOND, THIRD), gateway.requests.stream()
                .map(WalletInitializationRequest::playerId).toList());
        assertEquals(0L, gateway.requests.get(2).balanceMinorUnits());
        assertEquals(original, legacy.snapshotBalances());
        assertTrue(legacy.isMigrationSourceSealed());
        assertTrue(legacy.isMigrationArchiveReadOnly());
    }

    @Test
    void boundedBatchResumesAndArchivesOnlyAfterEveryEntry() {
        InternalBalanceSavedData legacy = balances(Map.of(
                FIRST, 10L,
                SECOND, 20L,
                THIRD, 30L));
        RecordingGateway gateway = new RecordingGateway(false);
        LegacyBalanceMigrator migrator = new LegacyBalanceMigrator(gateway);
        LegacyBalanceMigrationSavedData migration =
                new LegacyBalanceMigrationSavedData();

        LegacyBalanceMigrationBatchResult first = migrator.runBatch(
                legacy, migration, 2, () -> { });

        assertEquals(LegacyBalanceMigrationStage.IMPORTING, first.stage());
        assertEquals(2, first.processedEntries());
        assertEquals(2, first.nextEntryIndex());
        assertTrue(legacy.isMigrationSourceSealed());
        assertFalse(legacy.isMigrationArchiveReadOnly());
        assertThrows(IllegalStateException.class,
                () -> legacy.setBalance(UUID.randomUUID(), 1L));

        LegacyBalanceMigrationSavedData resumed =
                LegacyBalanceMigrationSavedData.load(
                        migration.save(new CompoundTag()));
        InternalBalanceSavedData resumedLegacy =
                InternalBalanceSavedData.load(
                        legacy.save(new CompoundTag()));
        LegacyBalanceMigrationBatchResult second = migrator.runBatch(
                resumedLegacy, resumed, 2, () -> { });

        assertEquals(LegacyBalanceMigrationStage.COMPLETE, second.stage());
        assertEquals(1, second.processedEntries());
        assertTrue(resumedLegacy.isMigrationArchiveReadOnly());
    }

    @Test
    void walletCallOccursOnlyAfterSnapshotDurabilityBarriers() {
        InternalBalanceSavedData legacy = balances(Map.of(FIRST, 10L));
        AtomicInteger flushes = new AtomicInteger();
        WalletInitializationGateway gateway = request -> {
            assertTrue(flushes.get() >= 2);
            return WalletInitializationResult.applied();
        };

        new LegacyBalanceMigrator(gateway).runBatch(
                legacy,
                new LegacyBalanceMigrationSavedData(),
                1,
                flushes::incrementAndGet);

        assertTrue(flushes.get() >= 4);
    }

    @Test
    void replayAfterCrashDoesNotDuplicateInitialization() {
        InternalBalanceSavedData legacy = balances(Map.of(FIRST, 10L));
        LegacyBalanceMigrationSavedData migration =
                new LegacyBalanceMigrationSavedData();
        CrashAfterApplyGateway gateway = new CrashAfterApplyGateway();
        LegacyBalanceMigrator migrator = new LegacyBalanceMigrator(gateway);

        assertThrows(SimulatedCrash.class,
                () -> migrator.runBatch(
                        legacy, migration, 1, () -> { }));
        assertEquals(0, migration.nextEntryIndex());

        LegacyBalanceMigrationBatchResult resumed = migrator.runBatch(
                legacy, migration, 1, () -> { });

        assertEquals(LegacyBalanceMigrationStage.COMPLETE, resumed.stage());
        assertEquals(2, gateway.calls);
        assertEquals(1, gateway.durableRequests.size());
    }

    @Test
    void changedLegacySourceFailsBeforeAnotherWalletCall() {
        InternalBalanceSavedData legacy = balances(Map.of(
                FIRST, 10L,
                SECOND, 20L));
        RecordingGateway gateway = new RecordingGateway(false);
        LegacyBalanceMigrator migrator = new LegacyBalanceMigrator(gateway);
        LegacyBalanceMigrationSavedData migration =
                new LegacyBalanceMigrationSavedData();
        migrator.runBatch(legacy, migration, 1, () -> { });
        int callsBeforeChange = gateway.requests.size();

        CompoundTag alteredTag = legacy.save(new CompoundTag());
        ListTag entries = alteredTag.getList("balances", Tag.TAG_COMPOUND);
        ((CompoundTag) entries.get(0)).putLong("balance", 999L);
        InternalBalanceSavedData altered =
                InternalBalanceSavedData.load(alteredTag);

        LegacyBalanceMigrationBatchResult result = migrator.runBatch(
                altered, migration, 1, () -> { });

        assertEquals(LegacyBalanceMigrationStage.FAILED, result.stage());
        assertEquals(LegacyBalanceMigrationFailure.SNAPSHOT_CHANGED,
                result.failure());
        assertEquals(callsBeforeChange, gateway.requests.size());
    }

    @Test
    void negativeBalancesFailClosedAndReportEveryPlayer() {
        InternalBalanceSavedData legacy = balances(Map.of(
                FIRST, -10L,
                SECOND, 0L,
                THIRD, -30L));
        RecordingGateway gateway = new RecordingGateway(false);

        LegacyBalanceMigrationBatchResult result =
                new LegacyBalanceMigrator(gateway).runBatch(
                        legacy,
                        new LegacyBalanceMigrationSavedData(),
                        10,
                        () -> { });

        assertEquals(LegacyBalanceMigrationStage.FAILED, result.stage());
        assertEquals(LegacyBalanceMigrationFailure.NEGATIVE_LEGACY_BALANCE,
                result.failure());
        assertEquals(List.of(FIRST, THIRD), result.affectedPlayers());
        assertTrue(gateway.requests.isEmpty());
        assertFalse(legacy.isMigrationArchiveReadOnly());
    }

    @Test
    void negativeBalancesRequireBothPolicyAndGatewaySupport() {
        InternalBalanceSavedData legacy = balances(Map.of(FIRST, -10L));
        RecordingGateway unsupported = new RecordingGateway(false);
        LegacyBalanceMigrationBatchResult unsupportedResult =
                new LegacyBalanceMigrator(
                        unsupported,
                        new LegacyBalanceMigrationPolicy(true))
                        .runBatch(legacy,
                                new LegacyBalanceMigrationSavedData(),
                                1,
                                () -> { });
        assertEquals(LegacyBalanceMigrationStage.FAILED,
                unsupportedResult.stage());

        InternalBalanceSavedData supportedLegacy =
                balances(Map.of(FIRST, -10L));
        RecordingGateway supported = new RecordingGateway(true);
        LegacyBalanceMigrationBatchResult supportedResult =
                new LegacyBalanceMigrator(
                        supported,
                        new LegacyBalanceMigrationPolicy(true))
                        .runBatch(supportedLegacy,
                                new LegacyBalanceMigrationSavedData(),
                                1,
                                () -> { });

        assertEquals(LegacyBalanceMigrationStage.COMPLETE,
                supportedResult.stage());
        assertEquals(-10L,
                supported.requests.get(0).balanceMinorUnits());
    }

    @Test
    void emptySnapshotCompletesWithoutWalletCalls() {
        InternalBalanceSavedData legacy = new InternalBalanceSavedData();
        RecordingGateway gateway = new RecordingGateway(false);

        LegacyBalanceMigrationBatchResult result =
                new LegacyBalanceMigrator(gateway).runBatch(
                        legacy,
                        new LegacyBalanceMigrationSavedData(),
                        4,
                        () -> { });

        assertEquals(LegacyBalanceMigrationStage.COMPLETE, result.stage());
        assertEquals(0, result.totalEntries());
        assertTrue(gateway.requests.isEmpty());
        assertTrue(legacy.isMigrationArchiveReadOnly());
    }

    @Test
    void completedMigrationIsIdempotent() {
        InternalBalanceSavedData legacy = balances(Map.of(FIRST, 10L));
        RecordingGateway gateway = new RecordingGateway(false);
        LegacyBalanceMigrator migrator = new LegacyBalanceMigrator(gateway);
        LegacyBalanceMigrationSavedData migration =
                new LegacyBalanceMigrationSavedData();
        migrator.runBatch(legacy, migration, 1, () -> { });

        LegacyBalanceMigrationBatchResult replay = migrator.runBatch(
                legacy, migration, 1, () -> { });

        assertEquals(LegacyBalanceMigrationStage.COMPLETE, replay.stage());
        assertEquals(1, gateway.requests.size());
    }

    @Test
    void completedNegativeMigrationSurvivesLaterPolicyChange() {
        InternalBalanceSavedData legacy = balances(Map.of(FIRST, -10L));
        RecordingGateway gateway = new RecordingGateway(true);
        LegacyBalanceMigrationSavedData migration =
                new LegacyBalanceMigrationSavedData();
        new LegacyBalanceMigrator(
                gateway, new LegacyBalanceMigrationPolicy(true))
                .runBatch(legacy, migration, 1, () -> { });

        LegacyBalanceMigrationBatchResult replay =
                new LegacyBalanceMigrator(
                        gateway, new LegacyBalanceMigrationPolicy(false))
                        .runBatch(legacy, migration, 1, () -> { });

        assertEquals(LegacyBalanceMigrationStage.COMPLETE, replay.stage());
        assertEquals(1, gateway.requests.size());
    }

    private static InternalBalanceSavedData balances(Map<UUID, Long> values) {
        InternalBalanceSavedData data = new InternalBalanceSavedData();
        values.forEach(data::setBalance);
        return data;
    }

    private static final class RecordingGateway
            implements WalletInitializationGateway {
        private final boolean negativeSupport;
        private final List<WalletInitializationRequest> requests =
                new ArrayList<>();
        private final Map<UUID, WalletInitializationRequest> durable =
                new LinkedHashMap<>();

        private RecordingGateway(boolean negativeSupport) {
            this.negativeSupport = negativeSupport;
        }

        @Override
        public WalletInitializationResult initialize(
                WalletInitializationRequest request
        ) {
            requests.add(request);
            WalletInitializationRequest existing = durable.putIfAbsent(
                    request.requestId(), request);
            if (existing == null) {
                return WalletInitializationResult.applied();
            }
            if (existing.equals(request)) {
                return WalletInitializationResult.replayed();
            }
            return WalletInitializationResult.conflict(
                    "Request identity conflicts");
        }

        @Override
        public boolean supportsNegativeLegacyBalances() {
            return negativeSupport;
        }
    }

    private static final class CrashAfterApplyGateway
            implements WalletInitializationGateway {
        private final Map<UUID, WalletInitializationRequest> durableRequests =
                new HashMap<>();
        private int calls;

        @Override
        public WalletInitializationResult initialize(
                WalletInitializationRequest request
        ) {
            calls++;
            WalletInitializationRequest existing = durableRequests.putIfAbsent(
                    request.requestId(), request);
            if (existing == null) {
                throw new SimulatedCrash();
            }
            if (!existing.equals(request)) {
                return WalletInitializationResult.conflict(
                        "Request identity conflicts");
            }
            return WalletInitializationResult.replayed();
        }
    }

    private static final class SimulatedCrash extends RuntimeException {
    }
}
