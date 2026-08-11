package com.enviouse.futureshops.server.escrow.stock;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StockModelInvariantTest {
    private static final Instant NOW = Instant.parse("2026-07-17T14:00:00Z");

    @Test
    void stockKeyIsStableAndStrict() {
        StockKey first = new StockKey("default", "minecraft:diamond");
        StockKey same = new StockKey("default", "minecraft:diamond");
        StockKey otherListing = new StockKey("default", "minecraft:emerald");
        StockKey otherShop = new StockKey("vip", "minecraft:diamond");

        assertEquals(first, same);
        assertNotEquals(first, otherListing);
        assertNotEquals(first, otherShop);
        assertThrows(IllegalArgumentException.class, () -> new StockKey("", "stone"));
        assertThrows(IllegalArgumentException.class,
                () -> new StockKey("bad shop", "stone"));
        assertThrows(IllegalArgumentException.class,
                () -> new StockKey("default", "x".repeat(
                        StockLimits.MAX_IDENTIFIER_LENGTH + 1)));
    }

    @Test
    void reservationIdentityIsBoundToTransactionAndListing() {
        UUID transactionId = UUID.randomUUID();
        StockKey key = new StockKey("default", "minecraft:diamond");
        StockReservationId expected = StockReservationId.forTransaction(transactionId, key);

        assertEquals(expected, StockReservationId.forTransaction(transactionId, key));
        assertNotEquals(expected, StockReservationId.forTransaction(UUID.randomUUID(), key));
        assertNotEquals(expected, StockReservationId.forTransaction(transactionId,
                new StockKey("default", "minecraft:emerald")));
        assertThrows(IllegalArgumentException.class, () -> new StockReservation(
                new StockReservationId(UUID.randomUUID()), transactionId, key, 1L, true,
                StockReservationState.HELD, 0L, NOW, NOW));
    }

    @Test
    void reservationLifecycleCannotSkipOrReverse() {
        UUID transactionId = UUID.randomUUID();
        StockKey key = new StockKey("default", "minecraft:diamond");
        StockReservation held = StockReservation.held(transactionId, key, 2L, true, NOW);
        StockReservation committed = held.resolve(StockReservationState.COMMITTED,
                NOW.plusSeconds(1));

        assertEquals(1L, committed.revision());
        assertThrows(StockConflictException.class, () -> committed.resolve(
                StockReservationState.RELEASED, NOW.plusSeconds(2)));
        assertThrows(StockConflictException.class, () -> held.resolve(
                StockReservationState.COMMITTED, NOW.minusSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> new StockReservation(
                held.reservationId(), transactionId, key, 2L, true,
                StockReservationState.HELD, 1L, NOW, NOW));
    }

    @Test
    void unlimitedAndFiniteRepresentationsCannotBeMixed() {
        StockKey key = new StockKey("default", "minecraft:diamond");
        assertThrows(IllegalArgumentException.class, () -> new StockPolicy(true, 1L));
        assertThrows(IllegalArgumentException.class, () -> new CatalogStockState(key,
                StockPolicy.unlimitedStock(), CatalogStockStatus.ACTIVE, 1L, fp('a'),
                0L, NOW));
        CatalogStockState unlimited = CatalogStockState.seed(new StockDefinition(key,
                StockPolicy.unlimitedStock(), fp('a')), NOW);
        assertEquals(-1L, unlimited.displayQuantity());
        assertEquals(0L, unlimited.availableQuantity());
    }

    private static String fp(char value) {
        return String.valueOf(value).repeat(64);
    }
}
