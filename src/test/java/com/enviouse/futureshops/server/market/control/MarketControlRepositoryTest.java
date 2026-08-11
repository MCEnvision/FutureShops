package com.enviouse.futureshops.server.market.control;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketControlRepositoryTest {
    private static final MarketControlActor ACTOR =
            new MarketControlActor(id(1), "Operator");

    @Test
    void controlsAllModulesIndependentlyAndProjectsImmutableAudit() {
        MarketControlState initial = MarketControlState.initial(100L);
        MarketControlApplyResult shop = transition(initial, id(10),
                MarketControlModule.SHOP, 0L,
                MarketModuleStatus.DRAINING, "Drain shop", 110L,
                120L, Optional.empty(), Optional.empty());
        MarketControlApplyResult bazaar = transition(shop.state(), id(11),
                MarketControlModule.BAZAAR, 0L,
                MarketModuleStatus.FROZEN, "Freeze bazaar", 121L,
                125L, Optional.empty(), Optional.empty());
        MarketControlApplyResult auction = transition(bazaar.state(),
                id(12), MarketControlModule.AUCTION_HOUSE, 0L,
                MarketModuleStatus.DRAINING, "Drain auctions", 126L,
                130L, Optional.empty(), Optional.empty());

        assertEquals(3L, auction.state().globalRevision());
        assertEquals(MarketModuleStatus.DRAINING,
                auction.state().module(MarketControlModule.SHOP)
                        .status());
        assertEquals(MarketModuleStatus.FROZEN,
                auction.state().module(MarketControlModule.BAZAAR)
                        .status());
        assertEquals(MarketModuleStatus.DRAINING,
                auction.state().module(
                        MarketControlModule.AUCTION_HOUSE).status());
        assertFalse(auction.state().module(MarketControlModule.SHOP)
                .acceptsNewValueOperations());
        assertTrue(auction.state().module(MarketControlModule.SHOP)
                .allowsExistingValueOperations());

        MarketControlAuditProjection projection =
                MarketControlAuditProjection.from(auction.state());
        assertEquals(3, projection.entries().size());
        assertEquals(List.of(bazaar.auditEntry()),
                projection.entriesFor(MarketControlModule.BAZAAR));
        assertEquals(auction.auditEntry(),
                projection.entryFor(id(12)).orElseThrow());
        assertThrows(UnsupportedOperationException.class,
                () -> projection.entries().add(shop.auditEntry()));
        assertThrows(UnsupportedOperationException.class,
                () -> projection.modules().clear());
    }

    @Test
    void recordsExactPauseAndResumeTimingAcrossCycles() {
        MarketControlState initial = MarketControlState.initial(100L);
        MarketControlApplyResult frozen = transition(initial, id(20),
                MarketControlModule.AUCTION_HOUSE, 0L,
                MarketModuleStatus.FROZEN, "Emergency pause", 110L,
                120L, Optional.empty(), Optional.empty());
        MarketModuleControl frozenControl = frozen.state().module(
                MarketControlModule.AUCTION_HOUSE);

        assertTrue(frozenControl.status().timersPaused());
        assertEquals(30L,
                frozenControl.accumulatedPausedMillisThrough(150L));
        MarketPauseTimingEvidence opened = frozen.auditEntry()
                .pauseTimingEvidence().orElseThrow();
        assertTrue(opened.open());
        assertEquals(120L, opened.pausedAtMillis());

        MarketControlApplyResult resumed = transition(frozen.state(),
                id(21), MarketControlModule.AUCTION_HOUSE, 1L,
                MarketModuleStatus.ENABLED, "Resume auctions", 160L,
                170L, Optional.empty(), Optional.empty());
        MarketPauseTimingEvidence closed = resumed.auditEntry()
                .pauseTimingEvidence().orElseThrow();
        assertFalse(closed.open());
        assertEquals(50L, closed.accumulatedPausedMillisAfter());
        assertEquals(50L, resumed.state().module(
                MarketControlModule.AUCTION_HOUSE)
                .accumulatedPausedMillisThrough(500L));

        MarketControlApplyResult frozenAgain = transition(
                resumed.state(), id(22),
                MarketControlModule.AUCTION_HOUSE, 2L,
                MarketModuleStatus.FROZEN, "Second pause", 190L,
                200L, Optional.empty(), Optional.empty());
        MarketControlApplyResult drained = transition(
                frozenAgain.state(), id(23),
                MarketControlModule.AUCTION_HOUSE, 3L,
                MarketModuleStatus.DRAINING, "Resume draining", 220L,
                225L, Optional.empty(), Optional.empty());
        assertEquals(75L, drained.state().module(
                MarketControlModule.AUCTION_HOUSE)
                .accumulatedPausedMillis());
        assertEquals(75L, drained.auditEntry().pauseTimingEvidence()
                .orElseThrow().accumulatedPausedMillisAfter());
    }

    @Test
    void requestIdsAreIdempotentAndConflictingReuseIsRejected() {
        MarketControlState initial = MarketControlState.initial(100L);
        MarketControlTransitionCommand command = command(id(30),
                MarketControlModule.SHOP, 0L,
                MarketModuleStatus.FROZEN, "Freeze shop", 110L, 120L,
                Optional.empty(), Optional.empty());
        MarketControlApplyResult first =
                MarketControlRepository.transition(initial, command);
        MarketControlApplyResult replay =
                MarketControlRepository.transition(first.state(), command);

        assertTrue(replay.replayed());
        assertTrue(replay.mutation().isEmpty());
        assertEquals(first.state(), replay.state());
        assertEquals(first.auditEntry(), replay.auditEntry());

        MarketControlTransitionCommand conflict = command(id(30),
                MarketControlModule.SHOP, 0L,
                MarketModuleStatus.FROZEN, "Different reason", 110L,
                120L, Optional.empty(), Optional.empty());
        assertThrows(MarketControlConflictException.class,
                () -> MarketControlRepository.transition(
                        first.state(), conflict));
        assertThrows(MarketControlConflictException.class,
                () -> transition(first.state(), id(31),
                        MarketControlModule.SHOP, 0L,
                        MarketModuleStatus.DRAINING, "Stale change",
                        130L, 140L, Optional.empty(),
                        Optional.empty()));
    }

    @Test
    void cancelAndRefundIsStickyUntilCorrelatedSafetyEvidence() {
        UUID batchId = id(40);
        MarketControlApplyResult cancelled = transition(
                MarketControlState.initial(100L), id(41),
                MarketControlModule.BAZAAR, 0L,
                MarketModuleStatus.CANCEL_AND_REFUND,
                "Cancel all orders", 110L, 120L,
                Optional.of(batchId), Optional.empty());
        MarketModuleControl control = cancelled.state().module(
                MarketControlModule.BAZAAR);
        assertTrue(control.status().requiresCancellationAndRefund());
        assertFalse(control.allowsExistingValueOperations());
        assertEquals(batchId,
                control.cancellationBatchId().orElseThrow());

        assertThrows(MarketControlConflictException.class,
                () -> transition(cancelled.state(), id(42),
                        MarketControlModule.BAZAAR, 1L,
                        MarketModuleStatus.ENABLED, "Unsafe resume",
                        130L, 140L, Optional.empty(),
                        Optional.empty()));
        MarketControlSafetyEvidence incomplete =
                new MarketControlSafetyEvidence(id(43), batchId,
                        135L, 0L, 1L, true);
        assertThrows(MarketControlConflictException.class,
                () -> transition(cancelled.state(), id(44),
                        MarketControlModule.BAZAAR, 1L,
                        MarketModuleStatus.ENABLED, "Unsafe resume",
                        136L, 140L, Optional.empty(),
                        Optional.of(incomplete)));
        MarketControlSafetyEvidence wrongBatch =
                new MarketControlSafetyEvidence(id(45), id(46),
                        135L, 0L, 0L, true);
        assertThrows(MarketControlConflictException.class,
                () -> transition(cancelled.state(), id(47),
                        MarketControlModule.BAZAAR, 1L,
                        MarketModuleStatus.ENABLED, "Wrong batch",
                        136L, 140L, Optional.empty(),
                        Optional.of(wrongBatch)));

        MarketControlSafetyEvidence safe =
                new MarketControlSafetyEvidence(id(48), batchId,
                        135L, 0L, 0L, true);
        MarketControlApplyResult resumed = transition(
                cancelled.state(), id(49),
                MarketControlModule.BAZAAR, 1L,
                MarketModuleStatus.ENABLED, "Reopen bazaar", 136L,
                140L, Optional.empty(), Optional.of(safe));
        assertEquals(MarketModuleStatus.ENABLED,
                resumed.state().module(MarketControlModule.BAZAAR)
                        .status());
        assertTrue(resumed.state().module(MarketControlModule.BAZAAR)
                .cancellationBatchId().isEmpty());
        assertEquals(safe,
                resumed.auditEntry().safetyEvidence().orElseThrow());
        assertThrows(MarketControlConflictException.class,
                () -> transition(resumed.state(), id(51),
                        MarketControlModule.BAZAAR, 2L,
                        MarketModuleStatus.CANCEL_AND_REFUND,
                        "Reused cancellation batch", 150L, 160L,
                        Optional.of(batchId), Optional.empty()));
    }

    @Test
    void mutationsApplyOnceAndRejectAnUnrelatedBaseState() {
        MarketControlState initial = MarketControlState.initial(100L);
        MarketControlApplyResult planned = transition(initial, id(50),
                MarketControlModule.SHOP, 0L,
                MarketModuleStatus.DRAINING, "Drain shop", 110L,
                120L, Optional.empty(), Optional.empty());
        MarketControlMutation mutation =
                planned.mutation().orElseThrow();

        MarketControlApplyResult applied =
                MarketControlRepository.applyMutation(initial, mutation);
        assertEquals(planned.state(), applied.state());
        assertFalse(applied.replayed());
        assertTrue(MarketControlRepository.applyMutation(
                applied.state(), mutation).replayed());

        MarketControlState differentInitialization =
                MarketControlState.initial(101L);
        assertThrows(MarketControlConflictException.class,
                () -> MarketControlRepository.applyMutation(
                        differentInitialization, mutation));
    }

    @Test
    void rejectsInvalidTextTimingAndNoOpTransitions() {
        assertThrows(IllegalArgumentException.class,
                () -> new MarketControlActor(id(60), "bad\nactor"));
        assertThrows(IllegalArgumentException.class,
                () -> command(id(61), MarketControlModule.SHOP, 0L,
                        MarketModuleStatus.DRAINING, "bad\nreason", 1L,
                        2L, Optional.empty(), Optional.empty()));
        assertThrows(IllegalArgumentException.class,
                () -> command(id(62), MarketControlModule.SHOP, 0L,
                        MarketModuleStatus.DRAINING, "Bad time", 2L,
                        1L, Optional.empty(), Optional.empty()));
        assertThrows(MarketControlConflictException.class,
                () -> transition(MarketControlState.initial(100L),
                        id(63), MarketControlModule.SHOP, 0L,
                        MarketModuleStatus.ENABLED, "No change", 110L,
                        120L, Optional.empty(), Optional.empty()));
    }

    private static MarketControlApplyResult transition(
            MarketControlState state,
            UUID requestId,
            MarketControlModule module,
            long revision,
            MarketModuleStatus target,
            String reason,
            long requestedAt,
            long appliedAt,
            Optional<UUID> cancellationBatch,
            Optional<MarketControlSafetyEvidence> evidence
    ) {
        return MarketControlRepository.transition(state,
                command(requestId, module, revision, target, reason,
                        requestedAt, appliedAt, cancellationBatch,
                        evidence));
    }

    private static MarketControlTransitionCommand command(
            UUID requestId,
            MarketControlModule module,
            long revision,
            MarketModuleStatus target,
            String reason,
            long requestedAt,
            long appliedAt,
            Optional<UUID> cancellationBatch,
            Optional<MarketControlSafetyEvidence> evidence
    ) {
        return new MarketControlTransitionCommand(requestId, module,
                revision, target, ACTOR, reason, requestedAt, appliedAt,
                cancellationBatch, evidence);
    }

    private static UUID id(long value) {
        return new UUID(0L, value);
    }
}
