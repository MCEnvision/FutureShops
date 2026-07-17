package com.enviouse.futureshops.client.market;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketCardLayoutTest {
    @Test
    void visibleCardTargetsStayInsideTheirContentBounds() {
        MarketRectangle content = new MarketRectangle(211, 83,
                701, 431);

        MarketCardLayout.Placement placement = MarketCardLayout.place(
                content, 4, 9, 28);

        assertEquals(4, placement.columns());
        assertEquals(28, placement.cards().size());
        for (MarketRectangle card : placement.cards()) {
            assertTrue(content.contains(card.x(), card.y()));
            assertTrue(content.contains(card.right() - 1,
                    card.bottom() - 1));
        }
        for (int left = 0; left < placement.cards().size(); left++) {
            for (int right = left + 1;
                 right < placement.cards().size(); right++) {
                assertFalse(placement.cards().get(left).overlaps(
                        placement.cards().get(right)));
            }
        }
    }

    @Test
    void narrowAndTinyLayoutsRemainBounded() {
        MarketRectangle narrow = new MarketRectangle(0, 0, 47, 61);

        MarketCardLayout.Placement placement = MarketCardLayout.place(
                narrow, 8, 12, 100);

        assertTrue(placement.cards().size()
                <= MarketCardLayout.MAXIMUM_CARDS);
        assertTrue(placement.columns() <= narrow.width());
        for (MarketRectangle card : placement.cards()) {
            assertTrue(card.right() <= narrow.right());
            assertTrue(card.bottom() <= narrow.bottom());
        }
        assertTrue(MarketCardLayout.place(
                new MarketRectangle(0, 0, 10, 0), 1, 0, 1)
                .cards().isEmpty());
    }

    @Test
    void cardAndGeometryBoundsFailClosed() {
        MarketRectangle content = new MarketRectangle(0, 0, 100, 100);

        assertThrows(IllegalArgumentException.class,
                () -> MarketCardLayout.place(content, 0, 4, 1));
        assertThrows(IllegalArgumentException.class,
                () -> MarketCardLayout.place(content, 1, 65, 1));
        assertThrows(IllegalArgumentException.class,
                () -> MarketCardLayout.place(content, 1, 4, 101));
    }
}
