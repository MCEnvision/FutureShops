package com.enviouse.futureshops.server.market.control;

import com.enviouse.futureshops.client.market.MarketModule;
import com.enviouse.futureshops.client.market.MarketModuleAvailability;
import com.enviouse.futureshops.server.market.MarketModuleAccessPolicy;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 6 disable / re-enable drills (plan §18) over the pure market control
 * transition logic (plan §11): a full freeze → re-enable round trip, the
 * draining "disable" mode, the exhaustive fail-closed transition matrix, the
 * claims-stay-available guarantee in every disable mode, and the accumulated
 * paused-time arithmetic across repeated cycles. No Forge bootstrap.
 */
class ModuleDisableDrillTest {
    private static final MarketControlActor ACTOR =
            new MarketControlActor(id(1), "Drill operator");

    @Test
    void freezeThenReEnableRoundTripRestoresNewValueOperations() {
        MarketControlState initial = MarketControlState.initial(100L);
        MarketControlTransitionCommand freeze = command(id(10),
                MarketControlModule.AUCTION_HOUSE, 0L,
                MarketModuleStatus.FROZEN, "Disable drill", 110L, 120L,
                Optional.empty(), Optional.empty());
        MarketControlApplyResult frozen =
                MarketControlRepository.transition(initial, freeze);
        MarketModuleControl frozenControl = frozen.state().module(
                MarketControlModule.AUCTION_HOUSE);

        assertTrue(frozenControl.status().timersPaused());
        assertFalse(frozenControl.acceptsNewValueOperations());
        assertEquals(1L, frozenControl.revision());

        // A retried disable command is a replay, never a second transition.
        MarketControlApplyResult replay =
                MarketControlRepository.transition(frozen.state(), freeze);
        assertTrue(replay.replayed());
        assertEquals(frozen.state(), replay.state());

        MarketControlApplyResult enabled = transition(frozen.state(),
                id(11), MarketControlModule.AUCTION_HOUSE, 1L,
                MarketModuleStatus.ENABLED, "Re-enable drill", 150L,
                160L, Optional.empty(), Optional.empty());
        MarketModuleControl control = enabled.state().module(
                MarketControlModule.AUCTION_HOUSE);

        assertEquals(MarketModuleStatus.ENABLED, control.status());
        assertTrue(control.acceptsNewValueOperations());
        assertTrue(control.allowsExistingValueOperations());
        assertFalse(control.status().timersPaused());
        assertEquals(2L, control.revision());
        assertEquals(2L, enabled.state().globalRevision());
        assertEquals(40L, control.accumulatedPausedMillis());
        assertEquals(2, enabled.state().auditEntries().size());

        // The WAL-shaped mutation replays the same re-enable exactly once.
        MarketControlMutation mutation = enabled.mutation().orElseThrow();
        MarketControlApplyResult applied =
                MarketControlRepository.applyMutation(frozen.state(),
                        mutation);
        assertEquals(enabled.state(), applied.state());
        assertTrue(MarketControlRepository.applyMutation(applied.state(),
                mutation).replayed());
    }

    @Test
    void drainingDisableKeepsExistingValueOperationsAvailable() {
        MarketControlApplyResult draining = transition(
                MarketControlState.initial(100L), id(20),
                MarketControlModule.BAZAAR, 0L,
                MarketModuleStatus.DRAINING, "Drain drill", 110L, 120L,
                Optional.empty(), Optional.empty());
        MarketModuleControl control = draining.state().module(
                MarketControlModule.BAZAAR);

        assertFalse(control.acceptsNewValueOperations());
        assertTrue(control.allowsExistingValueOperations());
        assertFalse(control.status().timersPaused());

        MarketModuleAvailability availability =
                MarketModuleAccessPolicy.capability(MarketModule.BAZAAR,
                        true, true, Optional.of(control), 0L);
        assertEquals(MarketModuleAvailability.DRAINING, availability);
        assertFalse(availability.allowsNewValueOperations());
        assertTrue(availability.allowsOwnershipCancellationRoutes());
        assertTrue(availability.canOpenView(MarketModule.BAZAAR,
                "orders"));
        assertTrue(availability.allowsClaims());
    }

    @Test
    void noDisableModeEverBlocksClaims() {
        // Plan §11: claims and cancellation remain available; no disable
        // mode may strand escrowed value behind a closed door.
        for (MarketModuleAvailability mode : Set.of(
                MarketModuleAvailability.FROZEN,
                MarketModuleAvailability.DRAINING,
                MarketModuleAvailability.CANCEL_AND_REFUND,
                MarketModuleAvailability.CLAIMS_ONLY)) {
            assertTrue(mode.allowsClaims(),
                    () -> mode + " must keep claims open");
            assertFalse(mode.allowsNewValueOperations(),
                    () -> mode + " must refuse new value operations");
        }
        for (MarketModule module : MarketModule.values()) {
            assertTrue(MarketModuleAvailability.FROZEN.canOpenView(
                    module, "claims"));
            assertTrue(MarketModuleAvailability.DRAINING.canOpenView(
                    module, "claims"));
            assertTrue(MarketModuleAvailability.CANCEL_AND_REFUND
                    .canOpenView(module, "claims"));
        }
        // Cancellation routes stay open while draining or cancelling.
        assertTrue(MarketModuleAvailability.DRAINING
                .allowsOwnershipCancellationRoutes());
        assertTrue(MarketModuleAvailability.CANCEL_AND_REFUND
                .allowsOwnershipCancellationRoutes());

        // A config-disabled module degrades to FROZEN, never to invisible.
        MarketControlState state = MarketControlState.initial(100L);
        assertEquals(MarketModuleAvailability.FROZEN,
                MarketModuleAccessPolicy.capability(MarketModule.BAZAAR,
                        false, true,
                        Optional.of(state.module(
                                MarketControlModule.BAZAAR)), 0L));

        // The claims page stays reachable under a frozen control.
        MarketControlApplyResult frozen = transition(state, id(30),
                MarketControlModule.BAZAAR, 0L,
                MarketModuleStatus.FROZEN, "Freeze drill", 110L, 120L,
                Optional.empty(), Optional.empty());
        MarketModuleAccessPolicy.PageAccess access =
                MarketModuleAccessPolicy.pageAccess(MarketModule.BAZAAR,
                        "claims", true, true, Optional.of(
                                frozen.state().module(
                                        MarketControlModule.BAZAAR)));
        assertTrue(access.allowed());
        assertEquals(MarketModuleAvailability.FROZEN,
                access.availability());
    }

    @Test
    void transitionMatrixFailsClosedOutsideTheAllowedEdges() {
        Map<MarketModuleStatus, Set<MarketModuleStatus>> allowed = Map.of(
                MarketModuleStatus.ENABLED, Set.of(
                        MarketModuleStatus.FROZEN,
                        MarketModuleStatus.DRAINING,
                        MarketModuleStatus.CANCEL_AND_REFUND),
                MarketModuleStatus.FROZEN, Set.of(
                        MarketModuleStatus.ENABLED,
                        MarketModuleStatus.DRAINING,
                        MarketModuleStatus.CANCEL_AND_REFUND),
                MarketModuleStatus.DRAINING, Set.of(
                        MarketModuleStatus.ENABLED,
                        MarketModuleStatus.FROZEN,
                        MarketModuleStatus.CANCEL_AND_REFUND),
                MarketModuleStatus.CANCEL_AND_REFUND, Set.of(
                        MarketModuleStatus.ENABLED));
        long seed = 1_000L;
        for (MarketModuleStatus source : MarketModuleStatus.values()) {
            for (MarketModuleStatus target
                    : MarketModuleStatus.values()) {
                seed += 10L;
                Prepared prepared = stateAt(source, seed);
                Optional<UUID> cancellation = target
                        == MarketModuleStatus.CANCEL_AND_REFUND
                        ? Optional.of(id(seed + 3L)) : Optional.empty();
                Optional<MarketControlSafetyEvidence> evidence =
                        source == MarketModuleStatus.CANCEL_AND_REFUND
                                && target == MarketModuleStatus.ENABLED
                                ? Optional.of(
                                new MarketControlSafetyEvidence(
                                        id(seed + 4L),
                                        prepared.batch().orElseThrow(),
                                        130L, 0L, 0L, true))
                                : Optional.empty();
                MarketControlTransitionCommand command = command(
                        id(seed + 5L), MarketControlModule.BAZAAR,
                        prepared.revision(), target,
                        "Matrix " + source + " to " + target, 130L,
                        140L, cancellation, evidence);
                if (target != source
                        && allowed.get(source).contains(target)) {
                    MarketControlApplyResult result =
                            MarketControlRepository.transition(
                                    prepared.state(), command);
                    assertEquals(target, result.state().module(
                                    MarketControlModule.BAZAAR).status(),
                            () -> source + " to " + target
                                    + " must be allowed");
                } else {
                    assertThrows(MarketControlConflictException.class,
                            () -> MarketControlRepository.transition(
                                    prepared.state(), command),
                            () -> source + " to " + target
                                    + " must fail closed");
                }
            }
        }
    }

    @Test
    void accumulatedPausedMillisSurvivesRepeatedDisableCycles() {
        MarketControlState state = MarketControlState.initial(100L);
        long revision = 0L;
        long expected = 0L;
        long[][] cycles = {{120L, 170L}, {200L, 210L}, {300L, 345L}};
        long seed = 40L;
        for (long[] cycle : cycles) {
            MarketControlApplyResult frozen = transition(state,
                    id(seed++), MarketControlModule.SHOP, revision++,
                    MarketModuleStatus.FROZEN, "Cycle freeze",
                    cycle[0] - 5L, cycle[0], Optional.empty(),
                    Optional.empty());
            MarketModuleControl paused = frozen.state().module(
                    MarketControlModule.SHOP);
            // An open pause accrues linearly from the freeze instant.
            assertEquals(expected + 7L,
                    paused.accumulatedPausedMillisThrough(
                            cycle[0] + 7L));
            MarketControlApplyResult enabled = transition(frozen.state(),
                    id(seed++), MarketControlModule.SHOP, revision++,
                    MarketModuleStatus.ENABLED, "Cycle resume",
                    cycle[1] - 5L, cycle[1], Optional.empty(),
                    Optional.empty());
            expected += cycle[1] - cycle[0];
            MarketModuleControl resumed = enabled.state().module(
                    MarketControlModule.SHOP);
            assertEquals(expected, resumed.accumulatedPausedMillis());
            assertEquals(expected, enabled.auditEntry()
                    .pauseTimingEvidence().orElseThrow()
                    .accumulatedPausedMillisAfter());
            // Enabled time never accrues pause.
            assertEquals(expected,
                    resumed.accumulatedPausedMillisThrough(
                            cycle[1] + 10_000L));
            state = enabled.state();
        }
        assertEquals(105L, state.module(MarketControlModule.SHOP)
                .accumulatedPausedMillis());

        // Draining does not accrue pause time either (timers keep running).
        MarketControlApplyResult draining = transition(state, id(seed),
                MarketControlModule.SHOP, revision,
                MarketModuleStatus.DRAINING, "Drain after cycles", 400L,
                410L, Optional.empty(), Optional.empty());
        assertEquals(105L, draining.state().module(
                        MarketControlModule.SHOP)
                .accumulatedPausedMillisThrough(9_999L));
    }

    private record Prepared(
            MarketControlState state,
            long revision,
            Optional<UUID> batch
    ) {
    }

    private static Prepared stateAt(
            MarketModuleStatus source,
            long seed
    ) {
        MarketControlState state = MarketControlState.initial(100L);
        if (source == MarketModuleStatus.ENABLED) {
            return new Prepared(state, 0L, Optional.empty());
        }
        Optional<UUID> cancellation =
                source == MarketModuleStatus.CANCEL_AND_REFUND
                        ? Optional.of(id(seed + 1L)) : Optional.empty();
        MarketControlApplyResult result =
                MarketControlRepository.transition(state, command(
                        id(seed + 2L), MarketControlModule.BAZAAR, 0L,
                        source, "Drill setup", 110L, 120L, cancellation,
                        Optional.empty()));
        return new Prepared(result.state(), 1L, cancellation);
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
        return new UUID(11L, value);
    }
}
