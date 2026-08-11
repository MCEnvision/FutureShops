package com.enviouse.futureshops.server.market.control;

import java.util.Objects;
import java.util.OptionalLong;

public record MarketPauseTimingEvidence(
        long pausedAtMillis,
        OptionalLong resumedAtMillis,
        long accumulatedPausedMillisBefore,
        long accumulatedPausedMillisAfter
) {
    public MarketPauseTimingEvidence {
        resumedAtMillis = Objects.requireNonNull(
                resumedAtMillis, "resumedAtMillis");
        if (pausedAtMillis < 0L || accumulatedPausedMillisBefore < 0L
                || accumulatedPausedMillisAfter < 0L) {
            throw new IllegalArgumentException(
                    "Market pause timing evidence is invalid");
        }
        if (resumedAtMillis.isEmpty()) {
            if (accumulatedPausedMillisAfter
                    != accumulatedPausedMillisBefore) {
                throw new IllegalArgumentException(
                        "Open market pause changed accumulated time");
            }
        } else {
            long resumedAt = resumedAtMillis.getAsLong();
            if (resumedAt < pausedAtMillis) {
                throw new IllegalArgumentException(
                        "Market pause resume time is invalid");
            }
            long expected = Math.addExact(
                    accumulatedPausedMillisBefore,
                    Math.subtractExact(resumedAt, pausedAtMillis));
            if (accumulatedPausedMillisAfter != expected) {
                throw new IllegalArgumentException(
                        "Market pause duration evidence is invalid");
            }
        }
    }

    public static MarketPauseTimingEvidence opened(
            long pausedAtMillis,
            long accumulatedPausedMillis
    ) {
        return new MarketPauseTimingEvidence(pausedAtMillis,
                OptionalLong.empty(), accumulatedPausedMillis,
                accumulatedPausedMillis);
    }

    public static MarketPauseTimingEvidence closed(
            long pausedAtMillis,
            long resumedAtMillis,
            long accumulatedPausedMillisBefore
    ) {
        long after = Math.addExact(accumulatedPausedMillisBefore,
                Math.subtractExact(resumedAtMillis, pausedAtMillis));
        return new MarketPauseTimingEvidence(pausedAtMillis,
                OptionalLong.of(resumedAtMillis),
                accumulatedPausedMillisBefore, after);
    }

    public boolean open() {
        return resumedAtMillis.isEmpty();
    }
}
