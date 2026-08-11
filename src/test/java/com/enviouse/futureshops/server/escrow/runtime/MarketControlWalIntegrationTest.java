package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.admin.EscrowAdministrativeAuditSavedData;
import com.enviouse.futureshops.server.escrow.claim.ClaimSavedData;
import com.enviouse.futureshops.server.escrow.custody.CustodySavedData;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryJournalSavedData;
import com.enviouse.futureshops.server.escrow.ledger.LedgerSavedData;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintSavedData;
import com.enviouse.futureshops.server.escrow.stock.StockSavedData;
import com.enviouse.futureshops.server.escrow.store.EscrowTransactionSavedData;
import com.enviouse.futureshops.server.market.auction.AuctionHouseSavedData;
import com.enviouse.futureshops.server.market.bazaar.BazaarSavedData;
import com.enviouse.futureshops.server.market.control.MarketControlActor;
import com.enviouse.futureshops.server.market.control.MarketControlApplyResult;
import com.enviouse.futureshops.server.market.control.MarketControlModule;
import com.enviouse.futureshops.server.market.control.MarketControlMutation;
import com.enviouse.futureshops.server.market.control.MarketControlMutationCodec;
import com.enviouse.futureshops.server.market.control.MarketControlRepository;
import com.enviouse.futureshops.server.market.control.MarketControlSavedData;
import com.enviouse.futureshops.server.market.control.MarketControlTransitionCommand;
import com.enviouse.futureshops.server.market.control.MarketModuleStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketControlWalIntegrationTest {
    @Test
    void scopedLaneCommitsOrdinaryControlsAndRejectsUnsafePaths(
            @TempDir Path directory
    ) throws Exception {
        Path journal = directory.resolve("market.control.wal");
        MarketControlSavedData control = new MarketControlSavedData();
        Fixture fixture = fixture(new EscrowRuntimeSavedData(), control);
        EscrowRuntimeCoordinator coordinator = coordinator(journal,
                fixture);
        assertEquals(EscrowRuntimeState.READY, coordinator.start());

        MarketControlTransitionCommand freeze = command(id(1), 0L,
                MarketModuleStatus.FROZEN, Optional.empty(), 100L,
                110L);
        MarketControlMutation freezeMutation = mutation(control, freeze);
        EscrowJournalEvent freezeEvent = event(freezeMutation);
        assertThrows(IllegalArgumentException.class,
                () -> coordinator.commit(freeze.requestId(),
                        freezeEvent));

        MarketControlTransitionCommand cancel = command(id(2), 0L,
                MarketModuleStatus.CANCEL_AND_REFUND,
                Optional.of(id(3)), 100L, 110L);
        MarketControlMutation cancelMutation =
                MarketControlRepository.transition(control.snapshot(),
                        cancel).mutation().orElseThrow();
        EscrowJournalEvent cancelEvent = event(cancelMutation);
        long beforeCancel = Files.size(journal);
        assertThrows(IllegalArgumentException.class,
                () -> coordinator.commitMarketControlMutation(
                        cancel.requestId(), cancelEvent));
        assertThrows(IllegalArgumentException.class,
                () -> fixture.applier().preflight(
                        cancel.requestId(), cancelEvent));
        assertEquals(beforeCancel, Files.size(journal));
        assertFalse(control.hasMaterializedState());

        EscrowCommitResult applied = coordinator
                .commitMarketControlMutation(freeze.requestId(),
                        freezeEvent);
        assertFalse(applied.replayed());
        assertEquals(MarketModuleStatus.FROZEN,
                control.snapshot().module(
                        MarketControlModule.AUCTION_HOUSE).status());
        long afterApply = Files.size(journal);
        assertTrue(coordinator.commitMarketControlMutation(
                freeze.requestId(), freezeEvent).replayed());
        assertEquals(afterApply, Files.size(journal));

        assertThrows(IllegalArgumentException.class,
                () -> coordinator.commitMarketControlMutation(
                        id(99), freezeEvent));
        coordinator.stop();
    }

    @Test
    void restartRebuildsColdControlStateAndReplaysMaterializedState(
            @TempDir Path directory
    ) {
        Path journal = directory.resolve("restart.wal");
        EscrowRuntimeSavedData firstCursor = new EscrowRuntimeSavedData();
        MarketControlSavedData materialized = new MarketControlSavedData();
        Fixture firstFixture = fixture(firstCursor, materialized);
        EscrowRuntimeCoordinator first = coordinator(journal,
                firstFixture);
        assertEquals(EscrowRuntimeState.READY, first.start());

        MarketControlTransitionCommand drain = command(id(10), 0L,
                MarketModuleStatus.DRAINING, Optional.empty(), 100L,
                110L);
        MarketControlMutation mutation = mutation(materialized, drain);
        first.commitMarketControlMutation(drain.requestId(),
                event(mutation));
        UUID lineage = firstCursor.journalLineage().orElseThrow();
        var expected = materialized.snapshot();
        first.stop();

        EscrowRuntimeSavedData partialCursor = cursorAtLineage(lineage);
        Fixture partialFixture = fixture(partialCursor, materialized);
        EscrowRuntimeCoordinator partial = coordinator(journal,
                partialFixture);
        assertEquals(EscrowRuntimeState.READY, partial.start());
        assertEquals(expected, materialized.snapshot());
        assertEquals(2L, partialCursor.lastAppliedSequence());
        partial.stop();

        EscrowRuntimeSavedData coldCursor = cursorAtLineage(lineage);
        MarketControlSavedData cold = new MarketControlSavedData();
        Fixture coldFixture = fixture(coldCursor, cold);
        EscrowRuntimeCoordinator restarted = coordinator(journal,
                coldFixture);
        assertEquals(EscrowRuntimeState.READY, restarted.start());
        assertEquals(expected, cold.snapshot());
        assertEquals(2L, coldCursor.lastAppliedSequence());
        restarted.stop();
    }

    @Test
    void materializedControlWithoutJournalFailsClosed(
            @TempDir Path directory
    ) {
        MarketControlSavedData control = new MarketControlSavedData();
        MarketControlTransitionCommand command = command(id(20), 0L,
                MarketModuleStatus.FROZEN, Optional.empty(), 100L,
                110L);
        control.applyCommitted(mutation(control, command));
        EscrowRuntimeCoordinator coordinator = coordinator(
                directory.resolve("missing.wal"),
                fixture(new EscrowRuntimeSavedData(), control));

        assertEquals(EscrowRuntimeState.MAINTENANCE,
                coordinator.start());
        assertTrue(coordinator.failure().isPresent());
        assertEquals(MarketModuleStatus.FROZEN,
                control.snapshot().module(
                        MarketControlModule.AUCTION_HOUSE).status());
        coordinator.stop();
    }

    private static EscrowRuntimeCoordinator coordinator(
            Path journal,
            Fixture fixture
    ) {
        return new EscrowRuntimeCoordinator(journal, fixture.cursor(),
                fixture.applier(),
                fixture.control()::hasMaterializedState);
    }

    private static Fixture fixture(
            EscrowRuntimeSavedData cursor,
            MarketControlSavedData control
    ) {
        EscrowSavedDataMutationApplier applier =
                new EscrowSavedDataMutationApplier(
                        new EscrowTransactionSavedData(),
                        new LedgerSavedData(), new ClaimSavedData(),
                        new EscrowAdministrativeAuditSavedData(),
                        new CustodySavedData(),
                        new ProtectedMintSavedData(),
                        new StockSavedData(),
                        new ItemInventoryJournalSavedData(),
                        new AuctionHouseSavedData(),
                        new BazaarSavedData(),
                        new ServerShopIntentSavedData(),
                        new PlayerShopEscrowSavedData(), control,
                        MaintenanceRuntimeMutationHandler.unavailable(),
                        AtmWithdrawalApplyFaultInjector.NONE, null);
        return new Fixture(cursor, control, applier);
    }

    private static MarketControlMutation mutation(
            MarketControlSavedData control,
            MarketControlTransitionCommand command
    ) {
        MarketControlApplyResult planned =
                control.planStandalone(command);
        return planned.mutation().orElseThrow();
    }

    private static EscrowJournalEvent event(
            MarketControlMutation mutation
    ) {
        return new EscrowJournalEvent(
                EscrowJournalEventType.MARKET_CONTROL_MUTATION,
                MarketControlMutationCodec.encode(mutation));
    }

    private static MarketControlTransitionCommand command(
            UUID requestId,
            long revision,
            MarketModuleStatus status,
            Optional<UUID> cancellationBatch,
            long requestedAt,
            long appliedAt
    ) {
        return new MarketControlTransitionCommand(requestId,
                MarketControlModule.AUCTION_HOUSE, revision, status,
                new MarketControlActor(id(100), "Operator"),
                "Runtime test", requestedAt, appliedAt,
                cancellationBatch, Optional.empty());
    }

    private static EscrowRuntimeSavedData cursorAtLineage(UUID lineage) {
        EscrowRuntimeSavedData cursor = new EscrowRuntimeSavedData();
        cursor.establishLineage(lineage, 1L);
        return cursor;
    }

    private static UUID id(long value) {
        return new UUID(13L, value);
    }

    private record Fixture(
            EscrowRuntimeSavedData cursor,
            MarketControlSavedData control,
            EscrowSavedDataMutationApplier applier
    ) {
    }
}
