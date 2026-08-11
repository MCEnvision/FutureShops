package com.enviouse.futureshops.client.market;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record MarketPriceChartModel(
        Kind kind,
        List<Long> values,
        List<Tone> tones
) {
    public MarketPriceChartModel {
        kind = Objects.requireNonNull(kind, "kind");
        values = List.copyOf(Objects.requireNonNull(values, "values"));
        tones = List.copyOf(Objects.requireNonNull(tones, "tones"));
        if (values.size() != tones.size()
                || values.stream().anyMatch(value -> value == null
                || value <= 0L)
                || kind == Kind.EMPTY && !values.isEmpty()
                || kind != Kind.EMPTY && values.isEmpty()) {
            throw new IllegalArgumentException(
                    "Market price chart model is invalid");
        }
    }

    public static MarketPriceChartModel create(
            List<Long> history,
            long currentMinor,
            long nextMinor,
            long buyoutMinor
    ) {
        List<Long> prices = Objects.requireNonNull(history, "history")
                .stream()
                .filter(value -> value != null && value > 0L)
                .toList();
        if (prices.size() >= 2) {
            List<Tone> tones = new ArrayList<>(prices.size());
            tones.add(Tone.GOLD);
            for (int index = 1; index < prices.size(); index++) {
                tones.add(prices.get(index) >= prices.get(index - 1)
                        ? Tone.POSITIVE : Tone.NEGATIVE);
            }
            return new MarketPriceChartModel(
                    Kind.PRICE_HISTORY, prices, tones);
        }
        List<Long> ladder = new ArrayList<>(3);
        List<Tone> tones = new ArrayList<>(3);
        addDistinct(ladder, tones, currentMinor, Tone.GOLD);
        addDistinct(ladder, tones, nextMinor, Tone.POSITIVE);
        addDistinct(ladder, tones, buyoutMinor, Tone.NEGATIVE);
        if (ladder.isEmpty()) {
            return new MarketPriceChartModel(
                    Kind.EMPTY, List.of(), List.of());
        }
        return new MarketPriceChartModel(Kind.BID_LADDER,
                ladder, tones);
    }

    public long minimum() {
        return values.stream().mapToLong(Long::longValue)
                .min().orElse(0L);
    }

    public long maximum() {
        return values.stream().mapToLong(Long::longValue)
                .max().orElse(0L);
    }

    public long latest() {
        return values.isEmpty() ? 0L : values.get(values.size() - 1);
    }

    private static void addDistinct(
            List<Long> values,
            List<Tone> tones,
            long value,
            Tone tone
    ) {
        if (value <= 0L || values.contains(value)) {
            return;
        }
        values.add(value);
        tones.add(tone);
    }

    public enum Kind {
        PRICE_HISTORY,
        BID_LADDER,
        EMPTY
    }

    public enum Tone {
        GOLD,
        POSITIVE,
        NEGATIVE
    }
}
