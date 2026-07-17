package com.enviouse.futureshops.server.security;

import java.time.Duration;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public record ServerRequestSecuritySettings(
        int trackedKeyCap,
        Duration idleRetention,
        ActionLimit atmData,
        ActionLimit atmWithdrawal,
        ActionLimit atmCashCollection,
        ActionLimit atmDeposit
) {
    public ServerRequestSecuritySettings {
        if (trackedKeyCap <= 0) {
            throw new IllegalArgumentException(
                    "Tracked key cap must be positive");
        }
        requirePositiveDuration(idleRetention, "Idle retention");
        Objects.requireNonNull(atmData, "atmData");
        Objects.requireNonNull(atmWithdrawal, "atmWithdrawal");
        Objects.requireNonNull(atmCashCollection, "atmCashCollection");
        Objects.requireNonNull(atmDeposit, "atmDeposit");
    }

    public Map<ServerRequestAction, ActionLimit> actionLimits() {
        EnumMap<ServerRequestAction, ActionLimit> limits =
                new EnumMap<>(ServerRequestAction.class);
        limits.put(ServerRequestAction.ATM_DATA, atmData);
        limits.put(ServerRequestAction.ATM_WITHDRAWAL, atmWithdrawal);
        limits.put(ServerRequestAction.ATM_CASH_COLLECTION,
                atmCashCollection);
        limits.put(ServerRequestAction.ATM_DEPOSIT, atmDeposit);
        return Collections.unmodifiableMap(limits);
    }

    public static ServerRequestSecuritySettings defaults() {
        return new ServerRequestSecuritySettings(
                8_192,
                Duration.ofMinutes(10L),
                new ActionLimit(4, 1, Duration.ofSeconds(1L)),
                new ActionLimit(2, 1, Duration.ofSeconds(2L)),
                new ActionLimit(2, 1, Duration.ofSeconds(2L)),
                new ActionLimit(2, 1, Duration.ofSeconds(2L))
        );
    }

    private static void requirePositiveDuration(
            Duration value,
            String name
    ) {
        Duration duration = Objects.requireNonNull(value, name);
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(
                    name + " must be positive");
        }
        try {
            duration.toNanos();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    name + " is too large", exception);
        }
    }

    public record ActionLimit(
            int capacity,
            int refillTokens,
            Duration refillPeriod
    ) {
        public ActionLimit {
            if (capacity <= 0) {
                throw new IllegalArgumentException(
                        "Action capacity must be positive");
            }
            if (refillTokens <= 0 || refillTokens > capacity) {
                throw new IllegalArgumentException(
                        "Refill tokens must be positive and not exceed capacity");
            }
            requirePositiveDuration(refillPeriod, "Refill period");
        }
    }
}
