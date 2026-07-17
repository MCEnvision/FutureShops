package com.enviouse.futureshops.client.market;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketLayoutEngineTest {
    @Test
    void fullHdGuiScalesChooseStableResponsiveModes() {
        MarketLayout scaleTwo = MarketLayoutEngine.compute(
            MarketViewport.scaled(1920, 1080, 2), "normal", "medium");
        MarketLayout scaleThree = MarketLayoutEngine.compute(
            MarketViewport.scaled(1920, 1080, 3), "normal", "medium");
        MarketLayout scaleFour = MarketLayoutEngine.compute(
            MarketViewport.scaled(1920, 1080, 4), "normal", "medium");

        assertEquals(MarketLayoutMode.WIDE, scaleTwo.mode());
        assertEquals(MarketLayoutMode.MEDIUM, scaleThree.mode());
        assertEquals(MarketLayoutMode.NARROW, scaleFour.mode());
        assertTrue(scaleTwo.fullBrand());
        assertFalse(scaleThree.fullBrand());
        assertTrue(scaleFour.categoryDrawer());
        assertTrue(scaleFour.secondaryTabRow());
        assertTrue(scaleFour.secondaryTabs().height() > 0);
        assertEquals(0, scaleTwo.secondaryTabs().height());
        assertEquals(0, scaleThree.secondaryTabs().height());
        assertFalse(scaleFour.secondaryTabs().overlaps(
                scaleFour.toolbar()));
        assertEquals(1, scaleFour.cardColumns());
    }

    @Test
    void standardResolutionRemainsUsableAtScalesTwoThreeAndFour() {
        for (int scale : new int[]{2, 3, 4}) {
            MarketLayout layout = MarketLayoutEngine.compute(
                MarketViewport.scaled(1280, 720, scale), "compact", "small");
            assertTrue(layout.window().width() >= 300);
            assertTrue(layout.window().height() >= 176);
            assertTrue(layout.content().width() > 0);
            assertTrue(layout.cardColumns() > 0);
            assertFalse(layout.header().overlaps(layout.footer()));
            assertFalse(layout.secondaryTabs().overlaps(
                    layout.toolbar()));
        }
    }

    @Test
    void densityAndCardSizeChangeCapacityWithoutBreakingRegions() {
        MarketViewport viewport = MarketViewport.scaled(1920, 1080, 2);
        MarketLayout compact = MarketLayoutEngine.compute(viewport, "compact", "small");
        MarketLayout comfortable = MarketLayoutEngine.compute(viewport, "comfortable", "large");

        assertTrue(compact.cardColumns() > comfortable.cardColumns());
        assertTrue(compact.padding() < comfortable.padding());
        assertFalse(compact.header().overlaps(compact.footer()));
        assertFalse(comfortable.header().overlaps(comfortable.footer()));
    }

    @Test
    void extremeScaleNeverPlacesTheWindowOutsideTheViewport() {
        MarketViewport viewport = MarketViewport.scaled(160, 90, 10);
        MarketLayout layout = MarketLayoutEngine.compute(
            viewport, "comfortable", "large");

        assertTrue(layout.window().right() <= viewport.guiWidth());
        assertTrue(layout.window().bottom() <= viewport.guiHeight());
        assertFalse(layout.header().overlaps(layout.footer()));
        assertTrue(layout.content().right() <= layout.window().right());
        assertTrue(layout.content().bottom() <= layout.window().bottom());
    }
}
