package com.enviouse.futureshops.server.escrow.stock;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StockStoreSnapshotCodecTest {
    private static final Instant NOW = Instant.parse("2026-07-17T13:00:00Z");

    @Test
    void everyRecordCodecRoundTrips() {
        StockKey key = new StockKey("server", "minecraft:emerald");
        StockDefinition definition = new StockDefinition(key, StockPolicy.limited(20L), fp('a'));
        CatalogStockState listing = CatalogStockState.seed(definition, NOW);
        UUID transactionId = UUID.randomUUID();
        StockReservation reservation = StockReservation.held(transactionId, key, 4L,
                true, NOW.plusSeconds(1));
        StockMutationReceipt receipt = new StockMutationReceipt(UUID.randomUUID(),
                StockMutationType.RESERVE, fp('b'), 1L,
                java.util.Optional.of(transactionId), java.util.Optional.of(key),
                java.util.Optional.of(reservation.reservationId()),
                java.util.List.of(),
                StockMutationOutcome.APPLIED, 1L, 0L, NOW.plusSeconds(1));

        assertEquals(definition, StockRecordCodec.decodeDefinition(
                StockRecordCodec.encodeDefinition(definition)));
        assertEquals(listing, StockRecordCodec.decodeListing(
                StockRecordCodec.encodeListing(listing)));
        assertEquals(reservation, StockRecordCodec.decodeReservation(
                StockRecordCodec.encodeReservation(reservation)));
        assertEquals(receipt, StockRecordCodec.decodeReceipt(
                StockRecordCodec.encodeReceipt(receipt)));
    }

    @Test
    void snapshotCodecIsDeterministicAndCrashSafe() {
        PersistentStockRepository repository = new PersistentStockRepository(fp('a'));
        StockKey first = new StockKey("a", "minecraft:apple");
        StockKey second = new StockKey("z", "minecraft:diamond");
        repository.seed(UUID.randomUUID(), new StockDefinition(second,
                StockPolicy.limited(9L), fp('b')), NOW);
        repository.seed(UUID.randomUUID(), new StockDefinition(first,
                StockPolicy.unlimitedStock(), fp('c')), NOW.plusSeconds(1));
        repository.reserve(UUID.randomUUID(), UUID.randomUUID(), second, 2L, 0L,
                NOW.plusSeconds(2));
        StockStoreSnapshot snapshot = repository.snapshot();

        byte[] encoded = StockStoreSnapshotCodec.encode(snapshot);
        StockStoreSnapshot decoded = StockStoreSnapshotCodec.decode(encoded);
        assertEquals(snapshot, decoded);

        Map<StockKey, CatalogStockState> reversedListings = new LinkedHashMap<>();
        reversedListings.put(second, snapshot.listings().get(second));
        reversedListings.put(first, snapshot.listings().get(first));
        Map<UUID, StockMutationReceipt> reversedReceipts = new LinkedHashMap<>();
        snapshot.receipts().entrySet().stream()
                .sorted(Map.Entry.<UUID, StockMutationReceipt>comparingByKey().reversed())
                .forEach(entry -> reversedReceipts.put(entry.getKey(), entry.getValue()));
        StockStoreSnapshot reordered = new StockStoreSnapshot(snapshot.storeRevision(),
                snapshot.catalogFingerprint(), reversedListings, snapshot.reservations(),
                reversedReceipts);
        assertArrayEquals(encoded, StockStoreSnapshotCodec.encode(reordered));

        PersistentStockRepository restored = new PersistentStockRepository();
        restored.rebuild(decoded);
        assertEquals(snapshot, restored.snapshot());
    }

    @Test
    void malformedRecordAndSnapshotPayloadsAreRejected() {
        StockDefinition definition = new StockDefinition(
                new StockKey("server", "minecraft:stone"), StockPolicy.limited(3L), fp('a'));
        byte[] encodedDefinition = StockRecordCodec.encodeDefinition(definition);
        byte[] wrongMagic = encodedDefinition.clone();
        wrongMagic[0] ^= 1;
        assertThrows(IllegalArgumentException.class,
                () -> StockRecordCodec.decodeDefinition(wrongMagic));
        byte[] trailing = Arrays.copyOf(encodedDefinition, encodedDefinition.length + 1);
        assertThrows(IllegalArgumentException.class,
                () -> StockRecordCodec.decodeDefinition(trailing));
        byte[] truncated = Arrays.copyOf(encodedDefinition, encodedDefinition.length - 1);
        assertThrows(IllegalArgumentException.class,
                () -> StockRecordCodec.decodeDefinition(truncated));

        int policyBooleanOffset = 6 + 4 + "server".length()
                + 4 + "minecraft:stone".length();
        byte[] malformedBoolean = encodedDefinition.clone();
        malformedBoolean[policyBooleanOffset] = 2;
        assertThrows(IllegalArgumentException.class,
                () -> StockRecordCodec.decodeDefinition(malformedBoolean));

        byte[] encodedSnapshot = StockStoreSnapshotCodec.encode(
                StockStoreSnapshot.empty(fp('0')));
        byte[] bitFlip = encodedSnapshot.clone();
        bitFlip[10] ^= 1;
        assertThrows(IllegalArgumentException.class,
                () -> StockStoreSnapshotCodec.decode(bitFlip));
        byte[] snapshotTrailing = Arrays.copyOf(encodedSnapshot, encodedSnapshot.length + 1);
        assertThrows(IllegalArgumentException.class,
                () -> StockStoreSnapshotCodec.decode(snapshotTrailing));
        byte[] snapshotTruncated = Arrays.copyOf(encodedSnapshot, encodedSnapshot.length - 1);
        assertThrows(IllegalArgumentException.class,
                () -> StockStoreSnapshotCodec.decode(snapshotTruncated));
        assertThrows(IllegalArgumentException.class,
                () -> StockStoreSnapshotCodec.decode(new byte[0]));
    }

    @Test
    void rebuildRejectsMismatchedMapIdentityAndMissingReservationListing() {
        StockKey key = new StockKey("server", "minecraft:stone");
        StockKey other = new StockKey("server", "minecraft:dirt");
        CatalogStockState listing = CatalogStockState.seed(new StockDefinition(key,
                StockPolicy.limited(4L), fp('a')), NOW);
        StockStoreSnapshot wrongKey = new StockStoreSnapshot(0L, fp('a'),
                Map.of(other, listing), Map.of(), Map.of());
        assertThrows(StockConflictException.class,
                () -> PersistentStockRepository.validateSnapshot(wrongKey));

        UUID transactionId = UUID.randomUUID();
        StockReservation reservation = StockReservation.held(transactionId, key, 1L,
                true, NOW);
        StockStoreSnapshot orphan = new StockStoreSnapshot(0L, fp('a'), Map.of(),
                Map.of(reservation.reservationId(), reservation), Map.of());
        assertThrows(StockConflictException.class,
                () -> new PersistentStockRepository().rebuild(orphan));
    }

    @Test
    void rebuildRejectsTerminalReceiptThatConflictsWithReservationState() {
        StockKey key = new StockKey("server", "minecraft:stone");
        PersistentStockRepository repository = new PersistentStockRepository(fp('a'));
        repository.seed(UUID.randomUUID(), new StockDefinition(key,
                StockPolicy.limited(4L), fp('a')), NOW);
        UUID transactionId = UUID.randomUUID();
        repository.reserve(UUID.randomUUID(), transactionId, key, 1L, 0L,
                NOW.plusSeconds(1));
        StockReservationId reservationId = StockReservationId.forTransaction(
                transactionId, key);
        repository.commit(UUID.randomUUID(), transactionId, reservationId, 0L,
                NOW.plusSeconds(2));
        StockStoreSnapshot valid = repository.snapshot();
        StockReservation committed = valid.reservations().get(reservationId);
        StockReservation conflicting = new StockReservation(reservationId, transactionId,
                key, committed.quantity(), committed.inventoryBacked(),
                StockReservationState.RELEASED, 1L, committed.createdAt(),
                committed.updatedAt());
        StockStoreSnapshot invalid = new StockStoreSnapshot(valid.storeRevision(),
                valid.catalogFingerprint(), valid.listings(), Map.of(reservationId, conflicting),
                valid.receipts());

        assertThrows(StockConflictException.class,
                () -> PersistentStockRepository.validateSnapshot(invalid));
    }

    private static String fp(char value) {
        return String.valueOf(value).repeat(64);
    }
}
