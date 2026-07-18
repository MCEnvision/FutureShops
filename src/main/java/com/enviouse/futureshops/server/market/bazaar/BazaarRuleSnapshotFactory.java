package com.enviouse.futureshops.server.market.bazaar;

import com.enviouse.futureshops.config.BazaarConfig;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Maps the live {@link BazaarConfig.Settings} onto the immutable per-order
 * {@link BazaarRuleSnapshot} contract (plan §1: active orders snapshot the applicable rules;
 * config reload cannot alter a live contract retroactively).
 *
 * <p>{@code configRevision} is not a config field — {@link BazaarOrderBook#setEffectiveRules}
 * requires it to be monotonic and forbids one revision from meaning two different rule sets.
 * {@link #nextEffective} therefore derives it from the book's current effective snapshot: same
 * content keeps the current revision (no push needed), changed content advances it by one.
 */
public final class BazaarRuleSnapshotFactory {
    /** Revision used the first time rules are ever pushed into an empty order book. */
    public static final long FIRST_CONFIG_REVISION = 0L;

    /**
     * {@link BazaarConfig} has no minimum-order-lifetime knob yet; live contracts snapshot zero
     * (no lifetime restriction) until one exists.
     */
    public static final long MINIMUM_LIFETIME_MILLIS = 0L;

    private BazaarRuleSnapshotFactory() {
    }

    public static BazaarRuleSnapshot from(
            BazaarConfig.Settings settings,
            long configRevision
    ) {
        Objects.requireNonNull(settings, "settings");
        BazaarConfig.FeeRules fees = settings.fees();
        BazaarConfig.OrderRules orders = settings.orders();
        BazaarConfig.MatchingRules matching = settings.matching();
        BazaarConfig.MarketSafetyRules safety = settings.marketSafety();
        return new BazaarRuleSnapshot(
                fees.makerBasisPoints(),
                fees.takerBasisPoints(),
                orders.maximumQuantity(),
                orders.maximumNotionalMinor(),
                orders.maximumOpenPerPlayer(),
                orders.maximumOpenPerProductPerPlayer(),
                orders.maximumEscrowedValuePerPlayerMinor(),
                selfTradePolicy(matching.selfTradePolicy()),
                executionPricePolicy(matching.executionPricePolicy()),
                safety.circuitBreaker(),
                safety.priceBandBasisPoints(),
                MINIMUM_LIFETIME_MILLIS,
                configRevision);
    }

    /**
     * The snapshot new orders must reference so {@link BazaarOrderBook#create} accepts them.
     * Returns the current effective snapshot unchanged when the configuration still means the
     * same thing; otherwise a content-equal-to-config snapshot at the next revision, which the
     * caller must push via a {@code SET_EFFECTIVE_RULES} lifecycle mutation before use.
     */
    public static BazaarRuleSnapshot nextEffective(
            BazaarConfig.Settings settings,
            Optional<BazaarRuleSnapshot> currentEffective
    ) {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(currentEffective, "currentEffective");
        if (currentEffective.isEmpty()) {
            return from(settings, FIRST_CONFIG_REVISION);
        }
        BazaarRuleSnapshot current = currentEffective.orElseThrow();
        BazaarRuleSnapshot candidate = from(settings, current.configRevision());
        if (candidate.equals(current)) {
            return current;
        }
        return from(settings, Math.addExact(current.configRevision(), 1L));
    }

    public static BazaarSelfTradePolicy selfTradePolicy(String configured) {
        return switch (normalized(configured)) {
            case "cancel_taker" -> BazaarSelfTradePolicy.CANCEL_TAKER;
            case "cancel_maker" -> BazaarSelfTradePolicy.CANCEL_MAKER;
            case "skip_self" -> BazaarSelfTradePolicy.SKIP_SELF;
            default -> throw new IllegalArgumentException(
                    "Bazaar self trade policy is unknown");
        };
    }

    public static BazaarExecutionPricePolicy executionPricePolicy(String configured) {
        return switch (normalized(configured)) {
            case "maker" -> BazaarExecutionPricePolicy.MAKER;
            case "taker" -> BazaarExecutionPricePolicy.TAKER;
            case "midpoint" -> BazaarExecutionPricePolicy.MIDPOINT;
            default -> throw new IllegalArgumentException(
                    "Bazaar execution price policy is unknown");
        };
    }

    private static String normalized(String value) {
        return Objects.requireNonNull(value, "value").strip()
                .toLowerCase(Locale.ROOT);
    }
}
