package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.admin.EscrowAdministrativeAuditSavedData;
import com.enviouse.futureshops.server.escrow.claim.ClaimSavedData;
import com.enviouse.futureshops.server.escrow.custody.CustodySavedData;
import com.enviouse.futureshops.server.escrow.ledger.LedgerSavedData;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintSavedData;
import com.enviouse.futureshops.server.escrow.stock.StockConflictException;
import com.enviouse.futureshops.server.escrow.stock.StockDefinition;
import com.enviouse.futureshops.server.escrow.stock.StockKey;
import com.enviouse.futureshops.server.escrow.stock.StockMutationCommand;
import com.enviouse.futureshops.server.escrow.stock.StockMutationCommandCodec;
import com.enviouse.futureshops.server.escrow.stock.StockPolicy;
import com.enviouse.futureshops.server.escrow.stock.StockReservationDirection;
import com.enviouse.futureshops.server.escrow.stock.StockReservationRequest;
import com.enviouse.futureshops.server.escrow.stock.StockSavedData;
import com.enviouse.futureshops.server.escrow.stock.StockStoreSnapshot;
import com.enviouse.futureshops.server.escrow.store.EscrowTransactionSavedData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StockWalIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-07-17T18:00:00Z");
    private static final StockKey KEY = new StockKey(
            "server", "minecraft:diamond");

    @Test
    void scopedLanePreflightsIdentityConflictAndExactReplayBeforeAppend(
            @TempDir Path directory
    ) throws Exception {
        Path journal = directory.resolve("stock.wal");
        Fixture fixture = fixture(new EscrowRuntimeSavedData(),
                new StockSavedData());
        EscrowRuntimeCoordinator coordinator = coordinator(journal, fixture);
        assertEquals(EscrowRuntimeState.READY, coordinator.start());
        UUID requestId = UUID.randomUUID();
        StockMutationCommand.Seed seed = new StockMutationCommand.Seed(
                requestId, definition(10L), NOW);
        EscrowJournalEvent event = event(seed);

        assertThrows(IllegalArgumentException.class,
                () -> coordinator.commit(requestId, event));
        EscrowCommitResult applied = coordinator.commitStockMutation(
                requestId, event);
        assertFalse(applied.replayed());
        long appliedBytes = Files.size(journal);

        StockMutationCommand.Seed sameSemanticRequest =
                new StockMutationCommand.Seed(requestId, definition(10L),
                        NOW.plusSeconds(50));
        assertTrue(coordinator.commitStockMutation(requestId,
                event(sameSemanticRequest)).replayed());
        assertEquals(appliedBytes, Files.size(journal));

        StockMutationCommand.Seed conflicting = new StockMutationCommand.Seed(
                requestId, definition(11L), NOW);
        assertThrows(StockConflictException.class,
                () -> coordinator.commitStockMutation(requestId,
                        event(conflicting)));
        assertEquals(appliedBytes, Files.size(journal));
        assertThrows(EscrowRuntimeException.class,
                () -> coordinator.commitStockMutation(UUID.randomUUID(), event));
        assertEquals(appliedBytes, Files.size(journal));
        coordinator.stop();
    }

    @Test
    void restartRebuildsColdStockAndAcceptsPartiallyMaterializedReplay(
            @TempDir Path directory
    ) {
        Path journal = directory.resolve("restart.wal");
        EscrowRuntimeSavedData firstCursor = new EscrowRuntimeSavedData();
        StockSavedData materializedStock = new StockSavedData();
        Fixture firstFixture = fixture(firstCursor, materializedStock);
        EscrowRuntimeCoordinator first = coordinator(journal, firstFixture);
        assertEquals(EscrowRuntimeState.READY, first.start());

        StockMutationCommand.Seed seed = new StockMutationCommand.Seed(
                UUID.randomUUID(), definition(10L), NOW);
        first.commitStockMutation(seed.requestId(), event(seed));
        UUID transactionId = UUID.randomUUID();
        StockMutationCommand.ReserveBatch reserve =
                new StockMutationCommand.ReserveBatch(UUID.randomUUID(),
                        transactionId, List.of(new StockReservationRequest(
                        KEY, StockReservationDirection.OUTBOUND, 3L, 0L)),
                        NOW.plusSeconds(1));
        first.commitStockMutation(reserve.requestId(), event(reserve));
        UUID lineage = firstCursor.journalLineage().orElseThrow();
        StockStoreSnapshot expected = materializedStock.snapshot();
        first.stop();

        EscrowRuntimeSavedData staleCursor = cursorAtLineage(lineage);
        Fixture partialFixture = fixture(staleCursor, materializedStock);
        EscrowRuntimeCoordinator partial = coordinator(journal,
                partialFixture);
        assertEquals(EscrowRuntimeState.READY, partial.start());
        assertEquals(expected, materializedStock.snapshot());
        assertEquals(3L, staleCursor.lastAppliedSequence());
        partial.stop();

        EscrowRuntimeSavedData coldCursor = cursorAtLineage(lineage);
        StockSavedData coldStock = new StockSavedData();
        Fixture coldFixture = fixture(coldCursor, coldStock);
        EscrowRuntimeCoordinator cold = coordinator(journal, coldFixture);
        assertEquals(EscrowRuntimeState.READY, cold.start());
        assertEquals(expected, coldStock.snapshot());
        assertEquals(7L, coldStock.listing(KEY).availableQuantity());
        assertTrue(coldStock.conservation().conserved());
        assertEquals(3L, coldCursor.lastAppliedSequence());
        cold.stop();
    }

    private static EscrowRuntimeCoordinator coordinator(Path journal,
                                                        Fixture fixture) {
        return new EscrowRuntimeCoordinator(journal, fixture.cursor(),
                fixture.applier(), fixture.stock()::hasMaterializedState);
    }

    private static Fixture fixture(EscrowRuntimeSavedData cursor,
                                   StockSavedData stock) {
        EscrowSavedDataMutationApplier applier =
                new EscrowSavedDataMutationApplier(
                        new EscrowTransactionSavedData(),
                        new LedgerSavedData(), new ClaimSavedData(),
                        new EscrowAdministrativeAuditSavedData(),
                        new CustodySavedData(), new ProtectedMintSavedData(),
                        stock, MaintenanceRuntimeMutationHandler.unavailable(),
                        AtmWithdrawalApplyFaultInjector.NONE, null);
        return new Fixture(cursor, stock, applier);
    }

    private static EscrowRuntimeSavedData cursorAtLineage(UUID lineage) {
        EscrowRuntimeSavedData cursor = new EscrowRuntimeSavedData();
        cursor.establishLineage(lineage, 1L);
        return cursor;
    }

    private static EscrowJournalEvent event(StockMutationCommand command) {
        return new EscrowJournalEvent(EscrowJournalEventType.STOCK_MUTATION,
                StockMutationCommandCodec.encode(command));
    }

    private static StockDefinition definition(long quantity) {
        return new StockDefinition(KEY, StockPolicy.limited(quantity),
                "a".repeat(64));
    }

    private record Fixture(
            EscrowRuntimeSavedData cursor,
            StockSavedData stock,
            EscrowSavedDataMutationApplier applier
    ) {
    }
}
