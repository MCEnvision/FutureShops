package com.enviouse.futureshops.server.market.control;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MarketControlCodecTest {
    @Test
    void stateCodecRoundTripsEveryStatusAndTimingEvidence() {
        MarketControlState state = MarketControlState.initial(100L);
        state = apply(state, id(10), MarketControlModule.SHOP, 0L,
                MarketModuleStatus.DRAINING, 110L, 120L,
                Optional.empty(), Optional.empty()).state();
        state = apply(state, id(11), MarketControlModule.BAZAAR, 0L,
                MarketModuleStatus.FROZEN, 121L, 130L,
                Optional.empty(), Optional.empty()).state();
        state = apply(state, id(12), MarketControlModule.BAZAAR, 1L,
                MarketModuleStatus.ENABLED, 140L, 150L,
                Optional.empty(), Optional.empty()).state();
        UUID batch = id(13);
        state = apply(state, id(14),
                MarketControlModule.AUCTION_HOUSE, 0L,
                MarketModuleStatus.CANCEL_AND_REFUND, 151L, 160L,
                Optional.of(batch), Optional.empty()).state();
        MarketControlSafetyEvidence evidence =
                new MarketControlSafetyEvidence(id(15), batch,
                        170L, 0L, 0L, true);
        state = apply(state, id(16),
                MarketControlModule.AUCTION_HOUSE, 1L,
                MarketModuleStatus.ENABLED, 171L, 180L,
                Optional.empty(), Optional.of(evidence)).state();

        byte[] encoded = MarketControlStateCodec.encode(state);
        MarketControlState decoded =
                MarketControlStateCodec.decode(encoded);
        assertEquals(state, decoded);
        assertArrayEquals(encoded,
                MarketControlStateCodec.encode(decoded));
        assertEquals(MarketControlStateCodec.fingerprint(state),
                MarketControlStateCodec.fingerprint(decoded));
    }

    @Test
    void mutationCodecRoundTripsAndReplaysDeterministically() {
        MarketControlState initial = MarketControlState.initial(100L);
        MarketControlApplyResult planned = apply(initial, id(20),
                MarketControlModule.BAZAAR, 0L,
                MarketModuleStatus.FROZEN, 110L, 120L,
                Optional.empty(), Optional.empty());
        MarketControlMutation mutation =
                planned.mutation().orElseThrow();
        byte[] encoded = MarketControlMutationCodec.encode(mutation);
        MarketControlMutation decoded =
                MarketControlMutationCodec.decode(encoded);

        assertEquals(mutation, decoded);
        assertArrayEquals(encoded,
                MarketControlMutationCodec.encode(decoded));
        assertEquals(planned.state(),
                MarketControlRepository.applyMutation(initial, decoded)
                        .state());
    }

    @Test
    void codecsRejectTamperingTruncationAndOversizedInput() {
        MarketControlState state = apply(
                MarketControlState.initial(100L), id(30),
                MarketControlModule.SHOP, 0L,
                MarketModuleStatus.DRAINING, 110L, 120L,
                Optional.empty(), Optional.empty()).state();
        byte[] stateBytes = MarketControlStateCodec.encode(state);
        byte[] tamperedState = stateBytes.clone();
        tamperedState[12] ^= 1;
        assertThrows(IllegalArgumentException.class,
                () -> MarketControlStateCodec.decode(tamperedState));
        assertThrows(IllegalArgumentException.class,
                () -> MarketControlStateCodec.decode(Arrays.copyOf(
                        stateBytes, stateBytes.length - 1)));
        assertThrows(IllegalArgumentException.class,
                () -> MarketControlStateCodec.decode(new byte[
                        MarketControlStateCodec.MAX_ENCODED_BYTES + 1]));

        MarketControlMutation mutation = apply(
                MarketControlState.initial(100L), id(31),
                MarketControlModule.BAZAAR, 0L,
                MarketModuleStatus.FROZEN, 110L, 120L,
                Optional.empty(), Optional.empty())
                .mutation().orElseThrow();
        byte[] mutationBytes =
                MarketControlMutationCodec.encode(mutation);
        byte[] tamperedMutation = mutationBytes.clone();
        tamperedMutation[20] ^= 1;
        assertThrows(IllegalArgumentException.class,
                () -> MarketControlMutationCodec.decode(
                        tamperedMutation));
        assertThrows(IllegalArgumentException.class,
                () -> MarketControlMutationCodec.decode(Arrays.copyOf(
                        mutationBytes, mutationBytes.length - 1)));
        assertThrows(IllegalArgumentException.class,
                () -> MarketControlMutationCodec.decode(new byte[
                        MarketControlMutationCodec.MAX_ENCODED_BYTES
                                + 1]));
    }

    @Test
    void fingerprintsCoverEveryCommandField() {
        MarketControlTransitionCommand first = command(id(40),
                MarketControlModule.SHOP, 0L,
                MarketModuleStatus.DRAINING, 110L, 120L,
                Optional.empty(), Optional.empty());
        MarketControlTransitionCommand changedTime = command(id(40),
                MarketControlModule.SHOP, 0L,
                MarketModuleStatus.DRAINING, 110L, 121L,
                Optional.empty(), Optional.empty());
        MarketControlTransitionCommand changedActor =
                new MarketControlTransitionCommand(id(40),
                        MarketControlModule.SHOP, 0L,
                        MarketModuleStatus.DRAINING,
                        new MarketControlActor(id(99), "Other"),
                        "State change", 110L, 120L,
                        Optional.empty(), Optional.empty());

        String fingerprint =
                MarketControlRequestFingerprints.fingerprint(first);
        assertEquals(64, fingerprint.length());
        org.junit.jupiter.api.Assertions.assertNotEquals(fingerprint,
                MarketControlRequestFingerprints.fingerprint(
                        changedTime));
        org.junit.jupiter.api.Assertions.assertNotEquals(fingerprint,
                MarketControlRequestFingerprints.fingerprint(
                        changedActor));
    }

    private static MarketControlApplyResult apply(
            MarketControlState state,
            UUID requestId,
            MarketControlModule module,
            long revision,
            MarketModuleStatus target,
            long requestedAt,
            long appliedAt,
            Optional<UUID> cancellationBatch,
            Optional<MarketControlSafetyEvidence> evidence
    ) {
        return MarketControlRepository.transition(state,
                command(requestId, module, revision, target,
                        requestedAt, appliedAt, cancellationBatch,
                        evidence));
    }

    private static MarketControlTransitionCommand command(
            UUID requestId,
            MarketControlModule module,
            long revision,
            MarketModuleStatus target,
            long requestedAt,
            long appliedAt,
            Optional<UUID> cancellationBatch,
            Optional<MarketControlSafetyEvidence> evidence
    ) {
        return new MarketControlTransitionCommand(requestId, module,
                revision, target,
                new MarketControlActor(id(1), "Operator"),
                "State change", requestedAt, appliedAt,
                cancellationBatch, evidence);
    }

    private static UUID id(long value) {
        return new UUID(0L, value);
    }
}
