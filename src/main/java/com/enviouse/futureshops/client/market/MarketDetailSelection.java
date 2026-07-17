package com.enviouse.futureshops.client.market;

import com.enviouse.futureshops.server.market.query.MarketPageCard;

import java.util.Objects;

public record MarketDetailSelection(
        MarketModule module,
        String sourceView,
        MarketPageCard card
) {
    public MarketDetailSelection {
        module = Objects.requireNonNull(module, "module");
        sourceView = Objects.requireNonNull(sourceView, "sourceView");
        card = Objects.requireNonNull(card, "card");
        if (sourceView.isEmpty() || sourceView.length() > 128
                || !sourceView.equals(sourceView.strip())) {
            throw new IllegalArgumentException(
                    "Market detail source view is invalid");
        }
    }

    public String identity() {
        return card.identity();
    }
}
