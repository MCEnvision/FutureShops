package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.MinecraftTestBootstrap;
import com.enviouse.futureshops.server.escrow.admin.EscrowAdministrativeAuditSavedData;
import com.enviouse.futureshops.server.escrow.claim.ClaimSavedData;
import com.enviouse.futureshops.server.escrow.custody.CustodySavedData;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryAbortReason;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryJournalConflictException;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryJournalSavedData;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryJournalSnapshot;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryJournalStatus;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryJournalTestFixtures;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryJournalTransition;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryJournalTransitionCodec;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryMutationAbort;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryMutationIntent;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryMutationQuarantine;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryQuarantineReason;
import com.enviouse.futureshops.server.escrow.ledger.LedgerSavedData;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintSavedData;
import com.enviouse.futureshops.server.escrow.stock.StockSavedData;
import com.enviouse.futureshops.server.escrow.store.EscrowTransactionSavedData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemInventoryGatewayWalIntegrationTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void scopedGatewayAppendsEachTransitionOnceAndRejectsConflicts(
            @TempDir Path directory
    ) throws Exception {
        Path journalPath = directory.resolve("item.wal");
        Fixture fixture = fixture(new EscrowRuntimeSavedData(),
                new ItemInventoryJournalSavedData());
        EscrowRuntimeCoordinator coordinator = coordinator(journalPath,
                fixture);
        assertEquals(EscrowRuntimeState.READY, coordinator.start());
        EscrowItemInventoryMutationGateway gateway = gateway(coordinator,
                fixture.itemJournal());
        UUID playerId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        ItemInventoryMutationIntent intent =
                ItemInventoryJournalTestFixtures.intent(playerId,
                        UUID.randomUUID(), requestId);
        EscrowJournalEvent prepareEvent = event(
                ItemInventoryJournalTransition.prepare(intent));

        assertThrows(IllegalArgumentException.class,
                () -> coordinator.commit(requestId, prepareEvent));
        assertThrows(EscrowRuntimeException.class,
                () -> coordinator.commitItemInventoryMutation(
                        UUID.randomUUID(), prepareEvent));
        assertThrows(IllegalArgumentException.class,
                () -> coordinator.commitItemInventoryMutation(requestId,
                        new EscrowJournalEvent(
                                EscrowJournalEventType.TRANSACTION_UPSERT,
                                new byte[]{1})));
        assertFalse(gateway.appendPreparedDurably(intent).replayed());
        long preparedBytes = Files.size(journalPath);
        assertTrue(gateway.appendPreparedDurably(intent).replayed());
        assertEquals(preparedBytes, Files.size(journalPath));

        ItemInventoryMutationIntent conflicting =
                ItemInventoryJournalTestFixtures.intent(UUID.randomUUID(),
                        UUID.randomUUID(), requestId);
        assertThrows(ItemInventoryJournalConflictException.class,
                () -> gateway.appendPreparedDurably(conflicting));
        assertEquals(preparedBytes, Files.size(journalPath));

        assertFalse(gateway.appendCommittedDurably(
                intent.plannedReceipt()).replayed());
        long committedBytes = Files.size(journalPath);
        assertTrue(gateway.appendCommittedDurably(
                intent.plannedReceipt()).replayed());
        assertEquals(committedBytes, Files.size(journalPath));
        assertThrows(ItemInventoryJournalConflictException.class,
                () -> gateway.appendAbortedDurably(
                        new ItemInventoryMutationAbort(intent.token(),
                                ItemInventoryAbortReason.CALLER_CANCELLED,
                                ItemInventoryJournalTestFixtures.NOW)));

        ItemInventoryMutationQuarantine quarantine =
                new ItemInventoryMutationQuarantine(intent.token(),
                        ItemInventoryQuarantineReason
                                .COMMITTED_REPAIR_FAILED,
                        ItemInventoryJournalTestFixtures.NOW);
        assertFalse(gateway.appendQuarantinedDurably(
                quarantine).replayed());
        assertTrue(gateway.appendQuarantinedDurably(
                quarantine).replayed());
        assertEquals(ItemInventoryJournalStatus.QUARANTINED,
                gateway.find(requestId).orElseThrow().status());

        ItemInventoryMutationIntent blocked =
                ItemInventoryJournalTestFixtures.intent(playerId,
                        UUID.randomUUID(), UUID.randomUUID());
        assertThrows(ItemInventoryJournalConflictException.class,
                () -> gateway.appendPreparedDurably(blocked));
        coordinator.stop();
    }

    @Test
    void crashReplayAcceptsSavedStateAheadOfCursorAndRebuildsColdState(
            @TempDir Path directory
    ) {
        Path journalPath = directory.resolve("replay.wal");
        EscrowRuntimeSavedData firstCursor =
                new EscrowRuntimeSavedData();
        ItemInventoryJournalSavedData materialized =
                new ItemInventoryJournalSavedData();
        Fixture firstFixture = fixture(firstCursor, materialized);
        EscrowRuntimeCoordinator first = coordinator(journalPath,
                firstFixture);
        assertEquals(EscrowRuntimeState.READY, first.start());
        EscrowItemInventoryMutationGateway firstGateway = gateway(first,
                materialized);
        ItemInventoryMutationIntent intent =
                ItemInventoryJournalTestFixtures.intent(UUID.randomUUID(),
                        UUID.randomUUID(), UUID.randomUUID());
        firstGateway.appendPreparedDurably(intent);
        firstGateway.appendCommittedDurably(intent.plannedReceipt());
        firstGateway.appendQuarantinedDurably(
                new ItemInventoryMutationQuarantine(intent.token(),
                        ItemInventoryQuarantineReason
                                .COMMITTED_REPAIR_FAILED,
                        ItemInventoryJournalTestFixtures.NOW));
        UUID lineage = firstCursor.journalLineage().orElseThrow();
        ItemInventoryJournalSnapshot expected = materialized.snapshot();
        first.stop();

        EscrowRuntimeSavedData staleCursor = cursorAtLineage(lineage);
        Fixture partialFixture = fixture(staleCursor, materialized);
        EscrowRuntimeCoordinator partial = coordinator(journalPath,
                partialFixture);
        assertEquals(EscrowRuntimeState.READY, partial.start());
        assertEquals(expected, materialized.snapshot());
        assertEquals(4L, staleCursor.lastAppliedSequence());
        partial.stop();

        EscrowRuntimeSavedData coldCursor = cursorAtLineage(lineage);
        ItemInventoryJournalSavedData cold =
                new ItemInventoryJournalSavedData();
        Fixture coldFixture = fixture(coldCursor, cold);
        EscrowRuntimeCoordinator recovered = coordinator(journalPath,
                coldFixture);
        assertEquals(EscrowRuntimeState.READY, recovered.start());
        assertEquals(expected, cold.snapshot());
        assertEquals(ItemInventoryJournalStatus.QUARANTINED,
                cold.find(intent.token().requestId()).orElseThrow()
                        .status());
        assertTrue(cold.playerQuarantined(intent.token().playerId()));
        recovered.stop();
    }

    @Test
    void restartLeavesPreparedWorkDiscoverableForBoundedRecovery(
            @TempDir Path directory
    ) {
        Path journalPath = directory.resolve("prepared.wal");
        EscrowRuntimeSavedData firstCursor =
                new EscrowRuntimeSavedData();
        Fixture firstFixture = fixture(firstCursor,
                new ItemInventoryJournalSavedData());
        EscrowRuntimeCoordinator first = coordinator(journalPath,
                firstFixture);
        assertEquals(EscrowRuntimeState.READY, first.start());
        ItemInventoryMutationIntent intent =
                ItemInventoryJournalTestFixtures.intent(UUID.randomUUID(),
                        UUID.randomUUID(), UUID.randomUUID());
        gateway(first, firstFixture.itemJournal())
                .appendPreparedDurably(intent);
        UUID lineage = firstCursor.journalLineage().orElseThrow();
        first.stop();

        ItemInventoryJournalSavedData cold =
                new ItemInventoryJournalSavedData();
        Fixture recoveredFixture = fixture(cursorAtLineage(lineage), cold);
        EscrowRuntimeCoordinator recovered = coordinator(journalPath,
                recoveredFixture);
        assertEquals(EscrowRuntimeState.READY, recovered.start());
        assertEquals(1, cold.preparedForPlayer(
                intent.token().playerId(), 1).size());
        assertEquals(ItemInventoryJournalStatus.PREPARED,
                cold.find(intent.token().requestId()).orElseThrow().status());
        recovered.stop();
    }

    private static EscrowRuntimeCoordinator coordinator(
            Path path,
            Fixture fixture
    ) {
        return new EscrowRuntimeCoordinator(path, fixture.cursor(),
                fixture.applier(), fixture.itemJournal()::hasMaterializedState);
    }

    private static EscrowItemInventoryMutationGateway gateway(
            EscrowRuntimeCoordinator coordinator,
            ItemInventoryJournalSavedData journal
    ) {
        return new EscrowItemInventoryMutationGateway(coordinator,
                journal, () -> true);
    }

    private static Fixture fixture(
            EscrowRuntimeSavedData cursor,
            ItemInventoryJournalSavedData itemJournal
    ) {
        EscrowSavedDataMutationApplier applier =
                new EscrowSavedDataMutationApplier(
                        new EscrowTransactionSavedData(),
                        new LedgerSavedData(), new ClaimSavedData(),
                        new EscrowAdministrativeAuditSavedData(),
                        new CustodySavedData(),
                        new ProtectedMintSavedData(),
                        new StockSavedData(), itemJournal,
                        MaintenanceRuntimeMutationHandler.unavailable(),
                        AtmWithdrawalApplyFaultInjector.NONE, null);
        return new Fixture(cursor, itemJournal, applier);
    }

    private static EscrowRuntimeSavedData cursorAtLineage(UUID lineage) {
        EscrowRuntimeSavedData cursor = new EscrowRuntimeSavedData();
        cursor.establishLineage(lineage, 1L);
        return cursor;
    }

    private static EscrowJournalEvent event(
            ItemInventoryJournalTransition transition
    ) {
        return new EscrowJournalEvent(
                EscrowJournalEventType.ITEM_INVENTORY_MUTATION,
                ItemInventoryJournalTransitionCodec.encode(transition));
    }

    private record Fixture(
            EscrowRuntimeSavedData cursor,
            ItemInventoryJournalSavedData itemJournal,
            EscrowSavedDataMutationApplier applier
    ) {
    }
}
