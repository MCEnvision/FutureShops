package com.enviouse.futureshops.server.market.query;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record MarketPageCard(
        MarketPageCardKind kind,
        String identity,
        Optional<UUID> ownerId,
        String registryId,
        int itemCount,
        String title,
        String category,
        String state,
        long revision,
        long primaryMinor,
        long secondaryMinor,
        long quantity,
        long remainingMillis,
        boolean watched,
        boolean primaryAction,
        boolean secondaryAction,
        long tertiaryMinor
) {
    public MarketPageCard {
        kind = Objects.requireNonNull(kind, "kind");
        identity = text(identity, 192, false);
        ownerId = Objects.requireNonNull(ownerId, "ownerId");
        registryId = text(registryId, 256, true);
        title = text(title, 256, false);
        category = text(category, 128, true);
        state = text(state, 64, false);
        if (ownerId.filter(value -> value.equals(
                new UUID(0L, 0L))).isPresent()
                || itemCount < 0 || revision < 0L || primaryMinor < 0L
                || secondaryMinor < 0L || quantity < 0L
                || remainingMillis < 0L || tertiaryMinor < 0L) {
            throw new IllegalArgumentException(
                    "Market page card values are invalid");
        }
    }

    /**
     * Legacy-shape constructor ({@code tertiaryMinor = 0}). The trailing tertiary value carries
     * an AUCTION card's buyout price when a bidding listing also allows buy-now — without it the
     * client cannot offer [Buy Now] on AUCTION_WITH_BUYOUT listings (the only other type signal
     * on the wire is the {@code Long.MAX_VALUE} remaining-time sentinel of pure BUY_NOW lots).
     */
    public MarketPageCard(MarketPageCardKind kind, String identity, Optional<UUID> ownerId,
                          String registryId, int itemCount, String title, String category,
                          String state, long revision, long primaryMinor, long secondaryMinor,
                          long quantity, long remainingMillis, boolean watched,
                          boolean primaryAction, boolean secondaryAction) {
        this(kind, identity, ownerId, registryId, itemCount, title, category, state, revision,
                primaryMinor, secondaryMinor, quantity, remainingMillis, watched, primaryAction,
                secondaryAction, 0L);
    }

    private static String text(
            String value,
            int maximum,
            boolean allowEmpty
    ) {
        String normalized = Objects.requireNonNull(value, "value");
        if (!allowEmpty && normalized.isEmpty()
                || normalized.length() > maximum
                || !normalized.equals(normalized.strip())) {
            throw new IllegalArgumentException(
                    "Market page card text is invalid");
        }
        for (int index = 0; index < normalized.length(); index++) {
            char character = normalized.charAt(index);
            if (Character.isHighSurrogate(character)) {
                if (index + 1 >= normalized.length()
                        || !Character.isLowSurrogate(
                        normalized.charAt(index + 1))) {
                    throw new IllegalArgumentException(
                            "Market page card text is invalid");
                }
                index++;
            } else if (Character.isLowSurrogate(character)
                    || Character.isISOControl(character)) {
                throw new IllegalArgumentException(
                        "Market page card text is invalid");
            }
        }
        return normalized;
    }
}
