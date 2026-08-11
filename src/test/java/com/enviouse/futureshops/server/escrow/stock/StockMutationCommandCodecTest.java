package com.enviouse.futureshops.server.escrow.stock;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StockMutationCommandCodecTest {
    private static final Instant NOW = Instant.parse("2026-07-17T16:00:00Z");
    private static final StockKey FIRST = new StockKey(
            "server", "minecraft:apple");
    private static final StockKey SECOND = new StockKey(
            "server", "minecraft:diamond");

    @Test
    void everyTypedCommandRoundTrips() {
        UUID transactionId = UUID.randomUUID();
        StockReservationId firstReservation =
                StockReservationId.forTransaction(transactionId, FIRST,
                        StockReservationDirection.OUTBOUND);
        StockReservationId secondReservation =
                StockReservationId.forTransaction(transactionId, SECOND,
                        StockReservationDirection.INBOUND);
        List<StockMutationCommand> commands = List.of(
                new StockMutationCommand.Seed(UUID.randomUUID(),
                        definition(FIRST, 10L, 'a'), NOW),
                new StockMutationCommand.Reserve(UUID.randomUUID(),
                        transactionId, FIRST, 2L, 4L, NOW.plusSeconds(1)),
                new StockMutationCommand.Resolve(UUID.randomUUID(),
                        StockMutationType.COMMIT, transactionId,
                        firstReservation, 0L, NOW.plusSeconds(2)),
                new StockMutationCommand.Resolve(UUID.randomUUID(),
                        StockMutationType.RELEASE, transactionId,
                        firstReservation, 0L, NOW.plusSeconds(3)),
                new StockMutationCommand.DefinitionChange(UUID.randomUUID(),
                        StockMutationType.REFRESH,
                        definition(FIRST, 12L, 'b'), 5L,
                        NOW.plusSeconds(4)),
                new StockMutationCommand.DefinitionChange(UUID.randomUUID(),
                        StockMutationType.ADMIN_RESET,
                        definition(FIRST, 8L, 'c'), 6L,
                        NOW.plusSeconds(5)),
                new StockMutationCommand.Reconcile(UUID.randomUUID(),
                        List.of(definition(SECOND, 3L, 'd'),
                                definition(FIRST, 7L, 'e')), fp('f'),
                        NOW.plusSeconds(6)),
                new StockMutationCommand.ReserveBatch(UUID.randomUUID(),
                        transactionId, List.of(
                        new StockReservationRequest(SECOND,
                                StockReservationDirection.INBOUND, 1L, 2L),
                        new StockReservationRequest(FIRST,
                                StockReservationDirection.OUTBOUND, 2L, 3L)),
                        NOW.plusSeconds(7)),
                new StockMutationCommand.ResolveBatch(UUID.randomUUID(),
                        StockMutationType.COMMIT_BATCH, transactionId,
                        List.of(new StockReservationResolution(
                                        secondReservation, 0L),
                                new StockReservationResolution(
                                        firstReservation, 0L)),
                        NOW.plusSeconds(8)),
                new StockMutationCommand.ResolveBatch(UUID.randomUUID(),
                        StockMutationType.RELEASE_BATCH, transactionId,
                        List.of(new StockReservationResolution(
                                        secondReservation, 0L),
                                new StockReservationResolution(
                                        firstReservation, 0L)),
                        NOW.plusSeconds(9)));

        for (StockMutationCommand command : commands) {
            assertEquals(command, StockMutationCommandCodec.decode(
                    StockMutationCommandCodec.encode(command)));
        }
    }

    @Test
    void unorderedBatchInputHasOneCanonicalEncoding() {
        UUID requestId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        StockReservationRequest first = new StockReservationRequest(FIRST,
                StockReservationDirection.OUTBOUND, 2L, 3L);
        StockReservationRequest second = new StockReservationRequest(SECOND,
                StockReservationDirection.INBOUND, 1L, 2L);

        StockMutationCommand forward = new StockMutationCommand.ReserveBatch(
                requestId, transactionId, List.of(first, second), NOW);
        StockMutationCommand reverse = new StockMutationCommand.ReserveBatch(
                requestId, transactionId, List.of(second, first), NOW);

        assertEquals(forward, reverse);
        assertArrayEquals(StockMutationCommandCodec.encode(forward),
                StockMutationCommandCodec.encode(reverse));
    }

    @Test
    void malformedTruncatedTrailingAndNewerCommandsFailClosed() {
        byte[] encoded = StockMutationCommandCodec.encode(
                new StockMutationCommand.Seed(UUID.randomUUID(),
                        definition(FIRST, 10L, 'a'), NOW));
        byte[] wrongMagic = encoded.clone();
        wrongMagic[0] ^= 1;
        assertThrows(IllegalArgumentException.class,
                () -> StockMutationCommandCodec.decode(wrongMagic));
        assertThrows(IllegalArgumentException.class,
                () -> StockMutationCommandCodec.decode(
                        Arrays.copyOf(encoded, encoded.length - 1)));
        assertThrows(IllegalArgumentException.class,
                () -> StockMutationCommandCodec.decode(
                        Arrays.copyOf(encoded, encoded.length + 1)));

        byte[] newer = encoded.clone();
        newer[4] = 0;
        newer[5] = 2;
        assertThrows(IllegalStateException.class,
                () -> StockMutationCommandCodec.decode(newer));
        assertThrows(IllegalArgumentException.class,
                () -> StockMutationCommandCodec.decode(new byte[
                        StockMutationCommandCodec.MAX_ENCODED_BYTES + 1]));
    }

    private static StockDefinition definition(StockKey key, long quantity,
                                              char fingerprint) {
        return new StockDefinition(key, StockPolicy.limited(quantity),
                fp(fingerprint));
    }

    private static String fp(char value) {
        return String.valueOf(value).repeat(64);
    }
}
