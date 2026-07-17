package com.enviouse.futureshops.client.market;

import java.util.Objects;
import java.util.UUID;

public record MarketRoute(
    MarketModule module,
    String viewId,
    String categoryId,
    String query,
    String filterId,
    String sortId,
    int page,
    int scrollOffset,
    String selectionId,
    UUID routeNonce
) {
    private static final int MAX_IDENTIFIER_LENGTH = 128;
    private static final int MAX_QUERY_LENGTH = 128;

    public MarketRoute {
        Objects.requireNonNull(module, "module");
        viewId = bounded(viewId, MAX_IDENTIFIER_LENGTH, false, "Market view identifier");
        categoryId = bounded(categoryId, MAX_IDENTIFIER_LENGTH, true, "Market category identifier");
        query = bounded(query, MAX_QUERY_LENGTH, true, "Market query");
        filterId = bounded(filterId, MAX_IDENTIFIER_LENGTH, true, "Market filter identifier");
        sortId = bounded(sortId, MAX_IDENTIFIER_LENGTH, true, "Market sort identifier");
        selectionId = bounded(selectionId, MAX_IDENTIFIER_LENGTH, true, "Market selection identifier");
        if (page < 0 || scrollOffset < 0) {
            throw new IllegalArgumentException("Market page and scroll position must not be negative.");
        }
        if (routeNonce == null || routeNonce.equals(new UUID(0L, 0L))) {
            throw new IllegalArgumentException("Market route nonce is required.");
        }
    }

    public static MarketRoute root(MarketModule module, UUID nonce) {
        return new MarketRoute(module, module.rootView(), "", "", "", "", 0, 0, "", nonce);
    }

    public MarketRoute withSelection(String selection, UUID nonce) {
        return new MarketRoute(
            module, viewId, categoryId, query, filterId, sortId, page, scrollOffset, selection, nonce);
    }

    public MarketRoute withView(String nextView, UUID nonce) {
        return new MarketRoute(module, nextView, "", "", "", "", 0, 0, "", nonce);
    }

    public MarketRoute withNonce(UUID nonce) {
        return new MarketRoute(
            module, viewId, categoryId, query, filterId, sortId, page, scrollOffset, selectionId, nonce);
    }

    public boolean isRoot() {
        return viewId.equals(module.rootView())
            && categoryId.isEmpty()
            && query.isEmpty()
            && filterId.isEmpty()
            && sortId.isEmpty()
            && page == 0
            && scrollOffset == 0
            && selectionId.isEmpty();
    }

    private static String bounded(String value, int maximum, boolean allowEmpty, String label) {
        if (value == null) {
            throw new IllegalArgumentException(label + " is required.");
        }
        String normalized = value.strip();
        if ((!allowEmpty && normalized.isEmpty()) || normalized.length() > maximum) {
            throw new IllegalArgumentException(label + " has an invalid length.");
        }
        return normalized;
    }
}
