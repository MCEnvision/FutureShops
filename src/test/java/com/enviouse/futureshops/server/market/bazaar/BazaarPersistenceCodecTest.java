package com.enviouse.futureshops.server.market.bazaar;

import org.junit.jupiter.api.Test;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BazaarPersistenceCodecTest {
    @Test
    void snapshotEncodingIsCanonicalAndRoundTripsCompleteState() {
        BazaarOrderBookSnapshot snapshot = richSnapshot();

        byte[] encoded = BazaarOrderBookSnapshotCodec.encode(snapshot);
        BazaarOrderBookSnapshot decoded = BazaarOrderBookSnapshotCodec.decode(
                encoded);

        assertEquals(snapshot, decoded);
        assertArrayEquals(encoded, BazaarOrderBookSnapshotCodec.encode(decoded));
        assertEquals(BazaarOrderBookSnapshotCodec.fingerprint(snapshot),
                BazaarOrderBookSnapshotCodec.fingerprint(decoded));
        assertFalse(decoded.fills().isEmpty());
        assertFalse(decoded.terminalTransactions().isEmpty());
        assertFalse(decoded.lifecycleReceipts().isEmpty());
    }

    @Test
    void requestMutationCodecRoundTripsAppliesAndReplays() {
        RequestFixture fixture = requestFixture(id(200), id(201), id(202),
                id(203), id(204));

        byte[] encoded = BazaarMutationCodec.encode(fixture.mutation());
        BazaarMutation decoded = BazaarMutationCodec.decode(encoded);
        BazaarMutation.ApplyResult applied = decoded.apply(fixture.previous());
        BazaarMutation.ApplyResult replayed = decoded.apply(fixture.next());

        assertEquals(fixture.mutation(), decoded);
        assertArrayEquals(encoded, BazaarMutationCodec.encode(decoded));
        assertFalse(applied.replayed());
        assertEquals(fixture.next(), applied.snapshot());
        assertTrue(replayed.replayed());
        assertEquals(fixture.next(), replayed.snapshot());
    }

    @Test
    void lifecycleMutationCodecCoversEveryCommandShapeAndReplay() {
        BazaarOrderBookSnapshot snapshot = new BazaarOrderBook().snapshot();
        List<BazaarLifecycleCommand> commands = List.of(
                BazaarLifecycleCommand.setEffectiveRules(id(300), rules()),
                BazaarLifecycleCommand.registerProduct(id(301), product()),
                BazaarLifecycleCommand.setProductStatus(id(302), "iron",
                        BazaarProductStatus.HALTED),
                BazaarLifecycleCommand.setReferencePrice(id(303), "iron",
                        100L));

        for (BazaarLifecycleCommand command : commands) {
            BazaarMutation mutation = BazaarMutation.lifecycle(snapshot,
                    command);
            byte[] encoded = BazaarMutationCodec.encode(mutation);
            BazaarMutation decoded = BazaarMutationCodec.decode(encoded);
            BazaarMutation.ApplyResult applied = decoded.apply(snapshot);
            BazaarMutation.ApplyResult replayed = decoded.apply(
                    applied.snapshot());

            assertEquals(mutation, decoded);
            assertArrayEquals(encoded, BazaarMutationCodec.encode(decoded));
            assertFalse(applied.replayed());
            assertTrue(replayed.replayed());
            assertEquals(applied.snapshot(), replayed.snapshot());
            snapshot = applied.snapshot();
        }

        assertEquals(4, snapshot.lifecycleReceipts().size());
        assertEquals(BazaarProductStatus.HALTED,
                BazaarOrderBook.restore(snapshot).product("iron")
                        .orElseThrow().status());
        assertEquals(100L, snapshot.referencePrices().get("iron"));
    }

    @Test
    void mutationRejectsStaleAncestry() {
        RequestFixture fixture = requestFixture(id(400), id(401), id(402),
                id(403), id(404));
        BazaarOrderBook fork = BazaarOrderBook.restore(fixture.previous());
        fork.create(sellCommand(id(410), id(411), id(412), id(413),
                id(414), 100L, 2));

        assertThrows(IllegalArgumentException.class,
                () -> fixture.mutation().apply(fork.snapshot()));
    }

    @Test
    void mutationRejectsConflictingReplay() {
        UUID requestId = id(500);
        RequestFixture fixture = requestFixture(requestId, id(501), id(502),
                id(503), id(504));
        BazaarOrderBook fork = BazaarOrderBook.restore(fixture.previous());
        fork.create(sellCommand(requestId, id(511), id(512), id(513),
                id(514), 110L, 3));

        assertThrows(IllegalArgumentException.class,
                () -> fixture.mutation().apply(fork.snapshot()));
    }

    @Test
    void mutationRejectsWrongNextFingerprint() {
        RequestFixture fixture = requestFixture(id(600), id(601), id(602),
                id(603), id(604));
        BazaarMutation wrong = new BazaarMutation(
                fixture.mutation().mutationId(),
                fixture.mutation().previousSnapshotFingerprint(),
                "0".repeat(64), fixture.mutation().requestReceipt(),
                fixture.mutation().lifecycleCommand());

        assertThrows(IllegalArgumentException.class,
                () -> wrong.apply(fixture.previous()));
    }

    @Test
    void codecsRejectTruncationAndTampering() {
        byte[] snapshot = BazaarOrderBookSnapshotCodec.encode(richSnapshot());
        RequestFixture fixture = requestFixture(id(700), id(701), id(702),
                id(703), id(704));
        byte[] mutation = BazaarMutationCodec.encode(fixture.mutation());

        assertThrows(IllegalArgumentException.class, () ->
                BazaarOrderBookSnapshotCodec.decode(
                        truncatePayloadAndRestoreDigest(snapshot)));
        assertThrows(IllegalArgumentException.class, () ->
                BazaarMutationCodec.decode(
                        truncatePayloadAndRestoreDigest(mutation)));

        byte[] tamperedSnapshot = snapshot.clone();
        tamperedSnapshot[8] ^= 1;
        byte[] tamperedMutation = mutation.clone();
        tamperedMutation[8] ^= 1;
        assertThrows(IllegalArgumentException.class, () ->
                BazaarOrderBookSnapshotCodec.decode(tamperedSnapshot));
        assertThrows(IllegalArgumentException.class, () ->
                BazaarMutationCodec.decode(tamperedMutation));
    }

    @Test
    void codecsRejectOversizedTopLevelInput() {
        assertThrows(IllegalArgumentException.class, () ->
                BazaarOrderBookSnapshotCodec.decode(new byte[
                        BazaarOrderBookSnapshotCodec.MAX_ENCODED_BYTES + 1]));
        assertThrows(IllegalArgumentException.class, () ->
                BazaarMutationCodec.decode(new byte[
                        BazaarMutationCodec.MAX_ENCODED_BYTES + 1]));
    }

    private static RequestFixture requestFixture(UUID requestId, UUID orderId,
                                                 UUID ownerId,
                                                 UUID activationId,
                                                 UUID custodyId) {
        BazaarOrderBook book = configuredBook();
        BazaarOrderBookSnapshot previous = book.snapshot();
        BazaarOperationResult result = book.create(sellCommand(requestId,
                orderId, ownerId, activationId, custodyId, 100L, 2));
        assertTrue(result.newlyCommitted());
        BazaarOrderBookSnapshot next = book.snapshot();
        return new RequestFixture(previous, next,
                BazaarMutation.between(previous, next, requestId));
    }

    private static BazaarOrderBookSnapshot richSnapshot() {
        BazaarOrderBook book = configuredBook();
        BazaarOrderBookSnapshot initial = book.snapshot();
        BazaarMutation lifecycle = BazaarMutation.lifecycle(initial,
                BazaarLifecycleCommand.setReferencePrice(id(10), "iron",
                        100L));
        book = BazaarOrderBook.restore(lifecycle.apply(initial).snapshot());

        BazaarOperationResult sell = book.create(sellCommand(id(20), id(21),
                id(22), id(23), id(24), 100L, 3));
        BazaarOperationResult buy = book.create(buyCommand(id(30), id(31),
                id(32), id(33), id(34), 100L, 2));
        assertTrue(sell.newlyCommitted());
        assertTrue(buy.newlyCommitted());

        BazaarOrder remaining = book.order(sell.orderId()).orElseThrow();
        UUID cancelRequest = id(40);
        BazaarOperationResult cancelled = book.cancel(
                new CancelBazaarOrderCommand(cancelRequest, sell.orderId(),
                        id(22), BazaarIds.terminal(cancelRequest,
                        sell.orderId(), BazaarOperationType.CANCEL),
                        remaining.revision(), 1_000L));
        assertTrue(cancelled.newlyCommitted());
        return book.snapshot();
    }

    private static BazaarOrderBook configuredBook() {
        BazaarOrderBook book = new BazaarOrderBook();
        book.registerProduct(product());
        book.setEffectiveRules(rules());
        return book;
    }

    private static CreateBazaarOrderCommand sellCommand(
            UUID requestId, UUID orderId, UUID ownerId, UUID activationId,
            UUID custodyId, long price, int quantity) {
        return new CreateBazaarOrderCommand(requestId, orderId, ownerId,
                activationId, Optional.empty(), Optional.of(custodyId),
                "iron", 1L, BazaarOrderSide.SELL, BazaarOrderType.LIMIT,
                BazaarTimeInForce.GOOD_UNTIL_CANCELLED, price, quantity,
                orderId.getLeastSignificantBits(), 0L, rules());
    }

    private static CreateBazaarOrderCommand buyCommand(
            UUID requestId, UUID orderId, UUID ownerId, UUID activationId,
            UUID holdId, long price, int quantity) {
        return new CreateBazaarOrderCommand(requestId, orderId, ownerId,
                activationId, Optional.of(holdId), Optional.empty(),
                "iron", 1L, BazaarOrderSide.BUY, BazaarOrderType.LIMIT,
                BazaarTimeInForce.GOOD_UNTIL_CANCELLED, price, quantity,
                orderId.getLeastSignificantBits(), 0L, rules());
    }

    private static BazaarProduct product() {
        return new BazaarProduct("iron", 1L, "minecraft:iron_ingot", "",
                "ores", 1, 1L, 1L, 1_000_000L, 10_000,
                BazaarProductStatus.ACTIVE);
    }

    private static BazaarRuleSnapshot rules() {
        return new BazaarRuleSnapshot(10, 25, 10_000,
                10_000_000_000L, 100, 50, 100_000_000_000L,
                BazaarSelfTradePolicy.CANCEL_TAKER,
                BazaarExecutionPricePolicy.MAKER, false, 5_000, 0L, 1L);
    }

    private static byte[] truncatePayloadAndRestoreDigest(byte[] encoded) {
        int digestLength = 32;
        byte[] payload = Arrays.copyOf(encoded,
                encoded.length - digestLength - 1);
        byte[] truncated = Arrays.copyOf(payload,
                payload.length + digestLength);
        byte[] digest = sha256(payload);
        System.arraycopy(digest, 0, truncated, payload.length, digestLength);
        return truncated;
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }

    private static UUID id(long value) {
        return new UUID(7L, value);
    }

    private record RequestFixture(BazaarOrderBookSnapshot previous,
                                  BazaarOrderBookSnapshot next,
                                  BazaarMutation mutation) {
    }
}
