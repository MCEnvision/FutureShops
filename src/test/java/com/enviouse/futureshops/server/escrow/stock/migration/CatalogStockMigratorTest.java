package com.enviouse.futureshops.server.escrow.stock.migration;

import com.enviouse.futureshops.server.escrow.stock.PersistentStockRepository;
import com.enviouse.futureshops.server.escrow.stock.StockCommandResult;
import com.enviouse.futureshops.server.escrow.stock.StockConservationReport;
import com.enviouse.futureshops.server.escrow.stock.StockDefinition;
import com.enviouse.futureshops.server.escrow.stock.StockKey;
import com.enviouse.futureshops.server.escrow.stock.StockMutationCommand;
import com.enviouse.futureshops.server.escrow.stock.StockPolicy;
import com.enviouse.futureshops.server.escrow.stock.StockStoreSnapshot;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogStockMigratorTest {
    private static final Instant NOW =
            Instant.parse("2026-07-17T12:00:00Z");

    @Test
    void partialReplayIsDeterministicAndCompletesOnlyAfterCheckpoint() {
        PersistentStockRepository repository =
                new PersistentStockRepository();
        RepositoryBackend backend = new RepositoryBackend(repository);
        CatalogStockSeedGateway gateway =
                new CatalogStockSeedGateway(backend);
        CatalogStockMigrator migrator =
                new CatalogStockMigrator(gateway);
        CatalogStockSeedSnapshot source = snapshot();
        CatalogStockMigrationSavedData migration =
                new CatalogStockMigrationSavedData();
        TestBarrier barrier = new TestBarrier(false);

        CatalogStockMigrationResult first = migrator.runBatch(
                source, migration, 1, NOW, barrier);
        assertEquals(CatalogStockMigrationStage.IMPORTING,
                first.stage());
        assertEquals(1, first.nextEntryIndex());

        CatalogStockMigrationSavedData reloaded =
                CatalogStockMigrationSavedData.load(
                        migration.save(new CompoundTag()));
        CatalogStockMigrationResult second = migrator.runBatch(
                source, reloaded, 10, NOW.plusSeconds(1), barrier);
        assertEquals(CatalogStockMigrationStage.VERIFIED,
                second.stage());
        assertEquals(source.fingerprint(),
                repository.snapshot().catalogFingerprint());
        assertEquals(13L,
                repository.conservation().finiteAvailableQuantity());
        assertTrue(repository.conservation().conserved());
        long beforeCheckpoint = repository.snapshot().storeRevision();

        barrier.checkpointVerified = true;
        CatalogStockMigrationResult complete = migrator.runBatch(
                source, reloaded, 10, NOW.plusSeconds(2), barrier);
        assertEquals(CatalogStockMigrationStage.COMPLETE,
                complete.stage());
        assertEquals(beforeCheckpoint, complete.completionSequence());

        CatalogStockMigrationResult replay = migrator.runBatch(
                source, reloaded, 10, NOW.plusSeconds(3), barrier);
        assertEquals(CatalogStockMigrationStage.COMPLETE,
                replay.stage());
        assertEquals(beforeCheckpoint,
                repository.snapshot().storeRevision());
    }

    @Test
    void nonemptyDestinationAndChangedSourceFailClosed() {
        PersistentStockRepository occupied =
                new PersistentStockRepository();
        CatalogStockSeedSnapshot source = snapshot();
        occupied.seed(java.util.UUID.randomUUID(),
                source.entries().get(0).definition(), NOW);
        CatalogStockMigrationSavedData rejected =
                new CatalogStockMigrationSavedData();
        CatalogStockMigrationResult nonempty = new CatalogStockMigrator(
                new CatalogStockSeedGateway(
                        new RepositoryBackend(occupied))).runBatch(
                source, rejected, 10, NOW,
                new TestBarrier(true));
        assertEquals(CatalogStockMigrationStage.FAILED,
                nonempty.stage());
        assertEquals(CatalogStockMigrationFailure.STOCK_STORE_NOT_EMPTY,
                nonempty.failure());

        PersistentStockRepository repository =
                new PersistentStockRepository();
        CatalogStockMigrationSavedData partial =
                new CatalogStockMigrationSavedData();
        TestBarrier barrier = new TestBarrier(false);
        CatalogStockMigrator migrator = new CatalogStockMigrator(
                new CatalogStockSeedGateway(
                        new RepositoryBackend(repository)));
        migrator.runBatch(source, partial, 1, NOW, barrier);
        CatalogStockSeedEntry changedEntry =
                source.entries().get(0);
        CatalogStockSeedSnapshot changed =
                CatalogStockSeedSnapshot.capture(List.of(
                        new CatalogStockSeedEntry(changedEntry.key(),
                                false, changedEntry.configuredQuantity(),
                                changedEntry.availableQuantity() + 1L,
                                changedEntry.configFingerprint()),
                        source.entries().get(1),
                        source.entries().get(2)));

        CatalogStockMigrationResult changedResult = migrator.runBatch(
                changed, partial, 10, NOW.plusSeconds(1), barrier);
        assertEquals(CatalogStockMigrationStage.FAILED,
                changedResult.stage());
        assertEquals(CatalogStockMigrationFailure.SOURCE_CHANGED,
                changedResult.failure());
    }

    @Test
    void verifiedMaterializedDestinationCanBeAdoptedAfterLegacyFailure() {
        PersistentStockRepository repository =
                new PersistentStockRepository();
        CatalogStockSeedSnapshot source = snapshot();
        CatalogStockSeedGateway gateway = new CatalogStockSeedGateway(
                new RepositoryBackend(repository));
        CatalogStockMigrator migrator = new CatalogStockMigrator(gateway);
        CatalogStockMigrationSavedData initial =
                new CatalogStockMigrationSavedData();
        TestBarrier barrier = new TestBarrier(true);

        assertEquals(CatalogStockMigrationStage.COMPLETE,
                migrator.runBatch(source, initial, 10, NOW, barrier)
                        .stage());
        long revision = repository.snapshot().storeRevision();

        CatalogStockMigrationSavedData failed =
                new CatalogStockMigrationSavedData();
        failed.fail(CatalogStockMigrationFailure.STOCK_STORE_NOT_EMPTY,
                "legacy migration interrupted after stock store creation");
        assertTrue(failed.canRetryMaterializedState());

        CatalogStockMigrationResult recovered = migrator.runBatch(
                source, failed, 10, NOW.plusSeconds(1), barrier);

        assertEquals(CatalogStockMigrationStage.COMPLETE,
                recovered.stage());
        assertEquals(CatalogStockMigrationFailure.NONE,
                recovered.failure());
        assertEquals(revision, repository.snapshot().storeRevision());
        assertTrue(repository.conservation().conserved());
    }

    @Test
    void completeMaterializedDestinationCanBeAdoptedWithMissingMetadata() {
        PersistentStockRepository repository =
                new PersistentStockRepository();
        CatalogStockSeedSnapshot source = snapshot();
        CatalogStockSeedGateway gateway = new CatalogStockSeedGateway(
                new RepositoryBackend(repository));
        CatalogStockMigrator migrator = new CatalogStockMigrator(gateway);
        TestBarrier barrier = new TestBarrier(true);

        assertEquals(CatalogStockMigrationStage.COMPLETE,
                migrator.runBatch(source,
                        new CatalogStockMigrationSavedData(), 10, NOW,
                        barrier).stage());
        long revision = repository.snapshot().storeRevision();

        CatalogStockMigrationResult adopted = migrator.runBatch(
                source, new CatalogStockMigrationSavedData(), 10,
                NOW.plusSeconds(1), barrier);

        assertEquals(CatalogStockMigrationStage.COMPLETE,
                adopted.stage());
        assertEquals(revision, repository.snapshot().storeRevision());
        assertTrue(gateway.verifyRestoredLineage(source).valid());
    }

    @Test
    void incompatibleMaterializedDestinationRemainsFailedWithEvidence() {
        PersistentStockRepository repository =
                new PersistentStockRepository();
        CatalogStockSeedSnapshot source = snapshot();
        CatalogStockSeedGateway gateway = new CatalogStockSeedGateway(
                new RepositoryBackend(repository));
        CatalogStockMigrator migrator = new CatalogStockMigrator(gateway);
        StockDefinition incompatible = new StockDefinition(
                new StockKey("default", "different"),
                StockPolicy.limited(2L), "d".repeat(64));
        repository.seed(UUID.randomUUID(), incompatible, NOW);

        CatalogStockMigrationSavedData failed =
                new CatalogStockMigrationSavedData();
        failed.fail(CatalogStockMigrationFailure.STOCK_STORE_NOT_EMPTY,
                "legacy migration interrupted");

        CatalogStockMigrationResult result = migrator.runBatch(
                source, failed, 10, NOW, new TestBarrier(true));

        assertEquals(CatalogStockMigrationStage.FAILED, result.stage());
        assertEquals(CatalogStockMigrationFailure.STOCK_STORE_NOT_EMPTY,
                result.failure());
        assertTrue(result.detail().contains("incompatible materialized state"));
        assertTrue(result.detail().contains("Catalog stock"));
        assertFalse(failed.canRetryMaterializedState());
    }

    @Test
    void restoredLineageAllowsLaterReloadsAndReservationsAfterRestart() {
        PersistentStockRepository repository =
                new PersistentStockRepository();
        CatalogStockSeedSnapshot source = snapshot();
        CatalogStockMigrationSavedData migration =
                new CatalogStockMigrationSavedData();
        CatalogStockSeedGateway gateway = new CatalogStockSeedGateway(
                new RepositoryBackend(repository));
        CatalogStockMigrationResult complete = new CatalogStockMigrator(
                gateway).runBatch(source, migration, 10, NOW,
                new TestBarrier(true));
        assertEquals(CatalogStockMigrationStage.COMPLETE,
                complete.stage());

        StockDefinition added = new StockDefinition(
                new StockKey("default", "added"),
                StockPolicy.limited(8L), "d".repeat(64));
        repository.reconcileReload(UUID.randomUUID(),
                java.util.stream.Stream.concat(
                        source.definitions().stream(),
                        java.util.stream.Stream.of(added)).toList(),
                "e".repeat(64), NOW.plusSeconds(1));
        StockKey heldKey = source.entries().get(0).key();
        repository.reserve(UUID.randomUUID(), UUID.randomUUID(), heldKey,
                1L, repository.listing(heldKey).revision(),
                NOW.plusSeconds(2));

        PersistentStockRepository restarted =
                new PersistentStockRepository();
        restarted.rebuild(repository.snapshot());
        CatalogStockSeedGateway restoredGateway =
                new CatalogStockSeedGateway(
                        new RepositoryBackend(restarted));

        assertFalse(restoredGateway.verify(source).valid());
        CatalogStockVerification restored =
                restoredGateway.verifyRestoredLineage(source);
        assertTrue(restored.valid());
        assertTrue(restored.completionSequence()
                >= migration.completionSequence());
    }

    private static CatalogStockSeedSnapshot snapshot() {
        return CatalogStockSeedSnapshot.capture(List.of(
                new CatalogStockSeedEntry(
                        new StockKey("default", "limited"),
                        false, 10L, 6L, "a".repeat(64)),
                new CatalogStockSeedEntry(
                        new StockKey("default", "overflow"),
                        false, 5L, 7L, "b".repeat(64)),
                new CatalogStockSeedEntry(
                        new StockKey("default", "unlimited"),
                        true, 0L, 0L, "c".repeat(64))));
    }

    private static final class RepositoryBackend
            implements CatalogStockSeedBackend {
        private final PersistentStockRepository repository;

        private RepositoryBackend(PersistentStockRepository repository) {
            this.repository = repository;
        }

        @Override
        public boolean ready() {
            return true;
        }

        @Override
        public StockCommandResult commit(
                StockMutationCommand command
        ) {
            return repository.applyCommitted(command);
        }

        @Override
        public StockStoreSnapshot snapshot() {
            return repository.snapshot();
        }

        @Override
        public StockConservationReport conservation() {
            return repository.conservation();
        }
    }

    private static final class TestBarrier
            implements CatalogStockMigrationDurabilityBarrier {
        private boolean checkpointVerified;
        private int flushes;

        private TestBarrier(boolean checkpointVerified) {
            this.checkpointVerified = checkpointVerified;
        }

        @Override
        public void flush() {
            flushes++;
        }

        @Override
        public boolean checkpointVerified(
                String checksum,
                long completionSequence
        ) {
            assertFalse(checksum.isBlank());
            assertTrue(completionSequence > 0L);
            return checkpointVerified;
        }
    }
}
