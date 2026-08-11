package com.enviouse.futureshops.server.market.query;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record MarketCardInsights(
        String ownerName,
        Optional<UUID> participantId,
        String participantName,
        String participantRole,
        String itemStackSnbt,
        boolean itemHasData,
        long activeListings,
        long activeBuyOrders,
        long tradesLastHour,
        long tradesLastDay,
        long unitsLastDay,
        long averagePriceMinor,
        List<Long> priceHistoryMinor
) {
    public static final int MAXIMUM_PRICE_POINTS = 24;
    public static final int MAXIMUM_STACK_SNBT = 32_767;

    public MarketCardInsights {
        ownerName = text(ownerName, 64);
        participantId = Objects.requireNonNull(participantId,
                "participantId");
        participantName = text(participantName, 64);
        participantRole = text(participantRole, 32);
        itemStackSnbt = text(itemStackSnbt, MAXIMUM_STACK_SNBT);
        priceHistoryMinor = List.copyOf(Objects.requireNonNull(
                priceHistoryMinor, "priceHistoryMinor"));
        if (participantId.filter(value -> value.equals(
                new UUID(0L, 0L))).isPresent()
                || activeListings < 0L || activeBuyOrders < 0L
                || tradesLastHour < 0L || tradesLastDay < 0L
                || unitsLastDay < 0L || averagePriceMinor < 0L
                || priceHistoryMinor.size() > MAXIMUM_PRICE_POINTS
                || priceHistoryMinor.stream().anyMatch(value ->
                value == null || value < 0L)) {
            throw new IllegalArgumentException(
                    "Market card insights are invalid");
        }
    }

    public static MarketCardInsights empty() {
        return new MarketCardInsights("", Optional.empty(), "", "",
                "", false, 0L, 0L, 0L, 0L, 0L, 0L, List.of());
    }

    private static String text(String value, int maximum) {
        String result = Objects.requireNonNull(value, "value");
        if (result.length() > maximum) {
            throw new IllegalArgumentException(
                    "Market card insight text is invalid");
        }
        return result;
    }
}
