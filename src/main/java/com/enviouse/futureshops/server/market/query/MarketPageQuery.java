package com.enviouse.futureshops.server.market.query;

import com.enviouse.futureshops.client.market.MarketModule;

import java.util.Locale;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.UUID;

public record MarketPageQuery(
        UUID requestId,
        UUID routeNonce,
        MarketModule module,
        String view,
        String search,
        String category,
        String sort,
        int pageIndex,
        int pageSize,
        OptionalLong minimumPriceMinor,
        OptionalLong maximumPriceMinor,
        long serverTimeMillis
) {
    public static final int MAXIMUM_PAGE_SIZE = 100;
    public static final int MAXIMUM_PAGE_INDEX = 1_000_000;

    private static final UUID ZERO = new UUID(0L, 0L);

    public MarketPageQuery {
        requestId = requireId(requestId, "requestId");
        routeNonce = requireId(routeNonce, "routeNonce");
        module = Objects.requireNonNull(module, "module");
        view = text(view, 32, false, "view")
                .toLowerCase(Locale.ROOT);
        search = text(search, 128, true, "search");
        category = text(category, 128, true, "category")
                .toLowerCase(Locale.ROOT);
        sort = text(sort, 32, true, "sort")
                .toLowerCase(Locale.ROOT);
        minimumPriceMinor = Objects.requireNonNull(
                minimumPriceMinor, "minimumPriceMinor");
        maximumPriceMinor = Objects.requireNonNull(
                maximumPriceMinor, "maximumPriceMinor");
        if (module == MarketModule.SHOP
                || pageIndex < 0 || pageIndex > MAXIMUM_PAGE_INDEX
                || pageSize <= 0 || pageSize > MAXIMUM_PAGE_SIZE
                || serverTimeMillis < 0L
                || minimumPriceMinor.isPresent()
                && minimumPriceMinor.getAsLong() < 0L
                || maximumPriceMinor.isPresent()
                && maximumPriceMinor.getAsLong() < 0L
                || minimumPriceMinor.isPresent()
                && maximumPriceMinor.isPresent()
                && minimumPriceMinor.getAsLong()
                > maximumPriceMinor.getAsLong()) {
            throw new IllegalArgumentException(
                    "Market page query is invalid");
        }
    }

    public static MarketPageQuery root(
            UUID requestId,
            UUID routeNonce,
            MarketModule module,
            String view,
            int pageSize,
            long serverTimeMillis
    ) {
        return new MarketPageQuery(requestId, routeNonce, module,
                view, "", "", "", 0, pageSize,
                OptionalLong.empty(), OptionalLong.empty(),
                serverTimeMillis);
    }

    private static UUID requireId(UUID value, String label) {
        UUID result = Objects.requireNonNull(value, label);
        if (ZERO.equals(result)) {
            throw new IllegalArgumentException(
                    "Market page identity is invalid");
        }
        return result;
    }

    private static String text(
            String value,
            int maximum,
            boolean allowEmpty,
            String label
    ) {
        String normalized = Objects.requireNonNull(value, label).strip();
        if (!normalized.equals(value)
                || !allowEmpty && normalized.isEmpty()
                || normalized.length() > maximum
                || !wellFormed(normalized)) {
            throw new IllegalArgumentException(
                    "Market page text is invalid");
        }
        return normalized;
    }

    private static boolean wellFormed(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isHighSurrogate(character)) {
                if (index + 1 >= value.length()
                        || !Character.isLowSurrogate(
                        value.charAt(index + 1))) {
                    return false;
                }
                index++;
            } else if (Character.isLowSurrogate(character)
                    || Character.isISOControl(character)) {
                return false;
            }
        }
        return true;
    }
}
