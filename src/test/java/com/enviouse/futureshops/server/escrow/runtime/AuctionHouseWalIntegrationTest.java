package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.admin.EscrowAdministrativeAuditSavedData;
import com.enviouse.futureshops.server.escrow.claim.ClaimSavedData;
import com.enviouse.futureshops.server.escrow.custody.CustodySavedData;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryJournalSavedData;
import com.enviouse.futureshops.server.escrow.ledger.LedgerSavedData;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintSavedData;
import com.enviouse.futureshops.server.escrow.stock.StockSavedData;
import com.enviouse.futureshops.server.escrow.store.EscrowTransactionSavedData;
import com.enviouse.futureshops.server.market.auction.AuctionHouseBook;
import com.enviouse.futureshops.server.market.auction.AuctionHouseMutation;
import com.enviouse.futureshops.server.market.auction.AuctionHouseMutationCodec;
import com.enviouse.futureshops.server.market.auction.AuctionHouseSavedData;
import com.enviouse.futureshops.server.market.auction.AuctionHouseSnapshot;
import com.enviouse.futureshops.server.market.auction.AuctionItemLot;
import com.enviouse.futureshops.server.market.auction.AuctionListingType;
import com.enviouse.futureshops.server.market.auction.AuctionRuleSnapshot;
import com.enviouse.futureshops.server.market.auction.AuctionTimeBasis;
import com.enviouse.futureshops.server.market.auction.CreateAuctionCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionHouseWalIntegrationTest {
    @Test
    void scopedLaneRejectsWrongIdentityConflictAndStaleAncestry(
            @TempDir Path directory
    ) throws Exception {
        Path journal = directory.resolve("auction.wal");
        AuctionHouseSavedData auctions = new AuctionHouseSavedData();
        Fixture fixture = fixture(new EscrowRuntimeSavedData(), auctions);
        EscrowRuntimeCoordinator coordinator = coordinator(journal, fixture);
        assertEquals(EscrowRuntimeState.READY, coordinator.start());

        AuctionHouseMutation mutation = createMutation(
                AuctionHouseSnapshot.empty(), id(1), id(2), id(3));
        EscrowJournalEvent event = event(mutation);
        assertThrows(IllegalArgumentException.class,
                () -> coordinator.commit(mutation.requestId(), event));

        EscrowCommitResult applied = coordinator
                .commitAuctionHouseMutation(mutation.requestId(), event);
        assertFalse(applied.replayed());
        long appliedBytes = Files.size(journal);
        assertEquals(mutation.apply(AuctionHouseSnapshot.empty()).snapshot(),
                auctions.snapshot());

        assertTrue(coordinator.commitAuctionHouseMutation(
                mutation.requestId(), event).replayed());
        assertEquals(appliedBytes, Files.size(journal));

        AuctionHouseMutation conflicting = createMutation(
                AuctionHouseSnapshot.empty(), mutation.requestId(),
                id(20), id(21));
        assertThrows(IllegalArgumentException.class,
                () -> coordinator.commitAuctionHouseMutation(
                        conflicting.requestId(), event(conflicting)));
        assertEquals(appliedBytes, Files.size(journal));

        AuctionHouseMutation stale = createMutation(
                AuctionHouseSnapshot.empty(), id(30), id(31), id(32));
        assertThrows(IllegalArgumentException.class,
                () -> coordinator.commitAuctionHouseMutation(
                        stale.requestId(), event(stale)));
        assertThrows(EscrowRuntimeException.class,
                () -> coordinator.commitAuctionHouseMutation(
                        id(40), event));
        assertEquals(appliedBytes, Files.size(journal));
        coordinator.stop();
    }

    @Test
    void restartRebuildsColdStateAndAcceptsMaterializedReplay(
            @TempDir Path directory
    ) {
        Path journal = directory.resolve("restart.wal");
        EscrowRuntimeSavedData firstCursor = new EscrowRuntimeSavedData();
        AuctionHouseSavedData materialized = new AuctionHouseSavedData();
        Fixture firstFixture = fixture(firstCursor, materialized);
        EscrowRuntimeCoordinator first = coordinator(journal, firstFixture);
        assertEquals(EscrowRuntimeState.READY, first.start());

        AuctionHouseMutation mutation = createMutation(
                materialized.snapshot(), id(50), id(51), id(52));
        first.commitAuctionHouseMutation(mutation.requestId(),
                event(mutation));
        UUID lineage = firstCursor.journalLineage().orElseThrow();
        AuctionHouseSnapshot expected = materialized.snapshot();
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
        AuctionHouseSavedData cold = new AuctionHouseSavedData();
        Fixture coldFixture = fixture(coldCursor, cold);
        EscrowRuntimeCoordinator restarted = coordinator(journal,
                coldFixture);
        assertEquals(EscrowRuntimeState.READY, restarted.start());
        assertEquals(expected, cold.snapshot());
        assertEquals(2L, coldCursor.lastAppliedSequence());
        restarted.stop();
    }

    @Test
    void materializedAuctionStateWithoutJournalFailsClosed(
            @TempDir Path directory
    ) {
        AuctionHouseSavedData auctions = new AuctionHouseSavedData();
        AuctionHouseMutation mutation = createMutation(
                auctions.snapshot(), id(70), id(71), id(72));
        auctions.applyCommitted(mutation);
        EscrowRuntimeCoordinator coordinator = coordinator(
                directory.resolve("missing.wal"),
                fixture(new EscrowRuntimeSavedData(), auctions));

        assertEquals(EscrowRuntimeState.MAINTENANCE,
                coordinator.start());
        assertTrue(coordinator.failure().isPresent());
        assertEquals(mutation.apply(AuctionHouseSnapshot.empty()).snapshot(),
                auctions.snapshot());
        coordinator.stop();
    }

    private static EscrowRuntimeCoordinator coordinator(
            Path journal,
            Fixture fixture
    ) {
        return new EscrowRuntimeCoordinator(journal, fixture.cursor(),
                fixture.applier(), fixture.auctions()::hasMaterializedState);
    }

    private static Fixture fixture(EscrowRuntimeSavedData cursor,
                                   AuctionHouseSavedData auctions) {
        EscrowSavedDataMutationApplier applier =
                new EscrowSavedDataMutationApplier(
                        new EscrowTransactionSavedData(),
                        new LedgerSavedData(), new ClaimSavedData(),
                        new EscrowAdministrativeAuditSavedData(),
                        new CustodySavedData(),
                        new ProtectedMintSavedData(),
                        new StockSavedData(),
                        new ItemInventoryJournalSavedData(), auctions,
                        MaintenanceRuntimeMutationHandler.unavailable(),
                        AtmWithdrawalApplyFaultInjector.NONE, null);
        return new Fixture(cursor, auctions, applier);
    }

    private static EscrowRuntimeSavedData cursorAtLineage(UUID lineage) {
        EscrowRuntimeSavedData cursor = new EscrowRuntimeSavedData();
        cursor.establishLineage(lineage, 1L);
        return cursor;
    }

    private static EscrowJournalEvent event(AuctionHouseMutation mutation) {
        return new EscrowJournalEvent(
                EscrowJournalEventType.AUCTION_HOUSE_MUTATION,
                AuctionHouseMutationCodec.encode(mutation));
    }

    private static AuctionHouseMutation createMutation(
            AuctionHouseSnapshot previous,
            UUID requestId,
            UUID listingId,
            UUID sellerId
    ) {
        AuctionHouseBook book = new AuctionHouseBook(previous);
        book.create(new CreateAuctionCommand(requestId, listingId, sellerId,
                id(listingId.getLeastSignificantBits() + 1000L),
                new AuctionItemLot(
                        id(listingId.getLeastSignificantBits() + 2000L),
                        "minecraft:diamond", "a".repeat(64), 1, 32,
                        "materials", "diamond minecraft"),
                AuctionListingType.AUCTION_WITH_BUYOUT,
                100L, 1000L,
                new AuctionRuleSnapshot(10L, 250, 10L, 0, true,
                        60L, 60L, 120L, 2, true,
                        AuctionTimeBasis.REAL_TIME, true, 7L),
                1000L, 2000L));
        return AuctionHouseMutation.between(previous, book.snapshot(),
                requestId);
    }

    private static UUID id(long value) {
        return new UUID(9L, value);
    }

    private record Fixture(
            EscrowRuntimeSavedData cursor,
            AuctionHouseSavedData auctions,
            EscrowSavedDataMutationApplier applier
    ) {
    }
}
