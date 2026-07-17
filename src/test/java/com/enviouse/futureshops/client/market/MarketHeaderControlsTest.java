package com.enviouse.futureshops.client.market;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketHeaderControlsTest {
    @Test
    void accountControlsStayBoundedAtFullHdGuiScales() {
        for (int scale : new int[]{2, 3, 4}) {
            MarketLayout layout = MarketLayoutEngine.compute(
                    MarketViewport.scaled(1920, 1080, scale),
                    "normal", "medium");
            MarketHeaderControls controls = MarketHeaderControls.compute(
                    layout.header(), layout.mode(), true);
            List<MarketRectangle> rectangles = new ArrayList<>(
                    controls.accountPills());
            rectangles.add(controls.search());
            rectangles.add(controls.back());
            rectangles.add(controls.close());
            for (MarketRectangle rectangle : rectangles) {
                assertTrue(rectangle.x() >= layout.header().x());
                assertTrue(rectangle.y() >= layout.header().y());
                assertTrue(rectangle.right()
                        <= layout.header().right());
                assertTrue(rectangle.bottom()
                        <= layout.header().bottom());
            }
            for (int left = 0; left < rectangles.size(); left++) {
                if (rectangles.get(left).width() == 0
                        || rectangles.get(left).height() == 0) {
                    continue;
                }
                for (int right = left + 1;
                     right < rectangles.size(); right++) {
                    if (rectangles.get(right).width() == 0
                            || rectangles.get(right).height() == 0) {
                        continue;
                    }
                    assertFalse(rectangles.get(left).overlaps(
                            rectangles.get(right)),
                            "scale " + scale);
                }
            }
        }
    }

    @Test
    void fixedGeometryCannotBeExpandedByLongPresentationText() {
        MarketLayout layout = MarketLayoutEngine.compute(
                MarketViewport.scaled(1280, 720, 4),
                "comfortable", "large");
        MarketHeaderControls controls = MarketHeaderControls.compute(
                layout.header(), layout.mode(), true);
        String longCurrency = "Long translated currency name ".repeat(8);

        assertTrue(longCurrency.length()
                > controls.balance().width());
        assertTrue(controls.balance().right()
                <= layout.header().right());
        assertFalse(controls.balance().overlaps(controls.profile()));
    }
}
