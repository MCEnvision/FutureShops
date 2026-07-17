package com.enviouse.futureshops.client.market;

import java.util.Objects;

public record MarketLayout(
    MarketLayoutMode mode,
    MarketRectangle window,
    MarketRectangle header,
    MarketRectangle breadcrumb,
    MarketRectangle categoryRail,
    MarketRectangle toolbar,
    MarketRectangle content,
    MarketRectangle footer,
    int cardColumns,
    int padding,
    boolean fullBrand,
    boolean categoryDrawer,
    boolean secondaryTabRow
) {
    public MarketLayout {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(header, "header");
        Objects.requireNonNull(breadcrumb, "breadcrumb");
        Objects.requireNonNull(categoryRail, "categoryRail");
        Objects.requireNonNull(toolbar, "toolbar");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(footer, "footer");
        if (cardColumns <= 0 || padding < 0) {
            throw new IllegalArgumentException("Market layout columns and padding must be positive.");
        }
        for (MarketRectangle rectangle : new MarketRectangle[]{
            header, breadcrumb, categoryRail, toolbar, content, footer
        }) {
            if (rectangle.right() > window.right() || rectangle.bottom() > window.bottom()) {
                throw new IllegalArgumentException("Market layout region is outside the window.");
            }
        }
        if (mode == MarketLayoutMode.NARROW && (!categoryDrawer || cardColumns != 1)) {
            throw new IllegalArgumentException("Narrow market layout requires a drawer and one content column.");
        }
        if (mode != MarketLayoutMode.NARROW && categoryDrawer) {
            throw new IllegalArgumentException("Only narrow market layout uses a category drawer.");
        }
    }
}
