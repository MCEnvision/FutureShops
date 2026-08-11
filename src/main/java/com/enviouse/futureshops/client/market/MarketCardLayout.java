package com.enviouse.futureshops.client.market;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class MarketCardLayout {
    public static final int MAXIMUM_CARDS = 100;

    private MarketCardLayout() {
    }

    public static Placement place(
            MarketRectangle content,
            int desiredColumns,
            int desiredGap,
            int cardCount
    ) {
        MarketRectangle bounds = Objects.requireNonNull(
                content, "content");
        if (desiredColumns <= 0 || desiredColumns > MAXIMUM_CARDS
                || desiredGap < 0 || desiredGap > 64
                || cardCount < 0 || cardCount > MAXIMUM_CARDS) {
            throw new IllegalArgumentException(
                    "Market card layout input is invalid");
        }
        if (cardCount == 0 || bounds.width() == 0
                || bounds.height() == 0) {
            return new Placement(List.of(), 0);
        }
        int columns = Math.min(Math.min(desiredColumns, cardCount),
                bounds.width());
        int gap = columns == 1 ? 0 : Math.min(desiredGap,
                Math.max(0, (bounds.width() - columns)
                        / (columns - 1)));
        int cardWidth = Math.max(1,
                (bounds.width() - gap * (columns - 1)) / columns);
        int rows = Math.max(1, (cardCount + columns - 1) / columns);
        int cardHeight = Math.max(46, Math.min(72,
                (bounds.height() - desiredGap * Math.max(0, rows - 1))
                        / rows));
        List<MarketRectangle> cards = new ArrayList<>(cardCount);
        for (int index = 0; index < cardCount; index++) {
            int column = index % columns;
            int row = index / columns;
            int x = bounds.x() + column * (cardWidth + gap);
            int y = bounds.y() + row * (cardHeight + desiredGap);
            if (y >= bounds.bottom()) {
                break;
            }
            int width = Math.min(cardWidth, bounds.right() - x);
            int height = Math.min(cardHeight, bounds.bottom() - y);
            if (width <= 0 || height <= 0) {
                break;
            }
            cards.add(new MarketRectangle(x, y, width, height));
        }
        return new Placement(cards, columns);
    }

    public record Placement(
            List<MarketRectangle> cards,
            int columns
    ) {
        public Placement {
            cards = List.copyOf(Objects.requireNonNull(cards, "cards"));
            if (columns < 0 || columns > MAXIMUM_CARDS
                    || cards.size() > MAXIMUM_CARDS) {
                throw new IllegalArgumentException(
                        "Market card placement is invalid");
            }
        }
    }
}
