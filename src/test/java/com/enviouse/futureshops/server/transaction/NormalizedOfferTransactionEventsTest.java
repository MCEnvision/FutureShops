package com.enviouse.futureshops.server.transaction;

import com.enviouse.futureshops.catalog.offer.OfferItemComponent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NormalizedOfferTransactionEventsTest {
    @Test
    void positiveMoneyCannotBecomeFree() {
        assertThrows(IllegalArgumentException.class, () ->
                NormalizedOfferTransactionEvents
                        .requireAuthorizedMoney(
                                false, true, 500L, 0L));
    }

    @Test
    void explicitFreeAcceptsOnlyZero() {
        assertEquals(0L, NormalizedOfferTransactionEvents
                .requireAuthorizedMoney(
                        true, false, 0L, 0L));
        assertThrows(IllegalArgumentException.class, () ->
                NormalizedOfferTransactionEvents
                        .requireAuthorizedMoney(
                                true, false, 0L, 1L));
    }

    @Test
    void barterOnlyKeepsItsZeroMoneyLeg() {
        assertEquals(0L, NormalizedOfferTransactionEvents
                .requireAuthorizedMoney(
                        false, false, 0L, 0L));
        assertThrows(IllegalArgumentException.class, () ->
                NormalizedOfferTransactionEvents
                        .requireAuthorizedMoney(
                                false, false, 0L, 1L));
    }

    @Test
    void paidEventMayChangePriceOnlyToAnotherPositiveValue() {
        assertEquals(375L, NormalizedOfferTransactionEvents
                .requireAuthorizedMoney(
                        false, true, 500L, 375L));
    }

    @Test
    void barterEvidenceContainsEveryScaledComponent() {
        var entries = NormalizedOfferTransactionEvents.barterEntries(
                List.of(
                        new OfferItemComponent(
                                "iron", "minecraft:iron_ingot",
                                4, ""),
                        new OfferItemComponent(
                                "stick", "minecraft:stick",
                                1, "")),
                3);

        assertEquals(2, entries.size());
        assertEquals("minecraft:iron_ingot",
                entries.get(0).itemId());
        assertEquals(12, entries.get(0).count());
        assertEquals("minecraft:stick",
                entries.get(1).itemId());
        assertEquals(3, entries.get(1).count());
    }

    @Test
    void barterEvidenceRejectsOverflow() {
        assertThrows(ArithmeticException.class, () ->
                NormalizedOfferTransactionEvents.barterEntries(
                        List.of(new OfferItemComponent(
                                "large", "minecraft:stone",
                                Integer.MAX_VALUE, "")),
                        2));
    }
}
