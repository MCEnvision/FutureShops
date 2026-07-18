package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.config.BazaarConfig;
import com.enviouse.futureshops.server.market.bazaar.BazaarExecutionPricePolicy;
import com.enviouse.futureshops.server.market.bazaar.BazaarOrderBook;
import com.enviouse.futureshops.server.market.bazaar.BazaarOrderSide;
import com.enviouse.futureshops.server.market.bazaar.BazaarOrderType;
import com.enviouse.futureshops.server.market.bazaar.BazaarRuleSnapshot;
import com.enviouse.futureshops.server.market.bazaar.BazaarRuleSnapshotFactory;
import com.enviouse.futureshops.server.market.bazaar.BazaarSelfTradePolicy;
import com.enviouse.futureshops.server.market.bazaar.BazaarTimeInForce;
import com.enviouse.futureshops.server.market.bazaar.escrow.BazaarEscrowPaymentSource;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure decision logic of {@link BazaarActionService} and
 * {@link BazaarRuleSnapshotFactory} — wire parsing, order-shape gating, expiration math, and the
 * config → rule-snapshot contract. No Forge bootstrap: nothing here touches item stacks,
 * registries, or capabilities.
 */
class BazaarActionServiceDecisionTest {

    private static BazaarConfig.Settings defaults() {
        return BazaarConfig.Settings.defaults();
    }

    private static BazaarConfig.Settings withFees(BazaarConfig.FeeRules fees) {
        BazaarConfig.Settings base = defaults();
        return new BazaarConfig.Settings(base.branding(), base.lifecycle(), base.orders(),
                base.productDefaults(), base.matching(), fees, base.marketSafety(),
                base.history(), base.payment(), base.notifications(), base.browse());
    }

    @Test
    void catalogControlAndPlayerProductIdentityAreStable() {
        assertEquals(BazaarConfig.CatalogControl.ADMIN,
                BazaarConfig.CatalogControl.fromWire("admin"));
        assertEquals(BazaarConfig.CatalogControl.PLAYERS,
                BazaarConfig.CatalogControl.fromWire("PLAYERS"));
        String first = BazaarActionService.playerProductId(
                "minecraft:spruce_door");
        assertEquals(first, BazaarActionService.playerProductId(
                "minecraft:spruce_door"));
        assertNotEquals(first, BazaarActionService.playerProductId(
                "minecraft:oak_door"));
        assertTrue(first.matches("player\\.[0-9a-f]{40}"));
    }

    @Test
    void factoryMapsEveryConfiguredRuleExactly() {
        BazaarConfig.Settings settings = defaults();
        BazaarRuleSnapshot snapshot = BazaarRuleSnapshotFactory.from(settings, 9L);

        assertEquals(settings.fees().makerBasisPoints(), snapshot.makerFeeBasisPoints());
        assertEquals(settings.fees().takerBasisPoints(), snapshot.takerFeeBasisPoints());
        assertEquals(settings.orders().maximumQuantity(), snapshot.maximumOrderQuantity());
        assertEquals(settings.orders().maximumNotionalMinor(),
                snapshot.maximumNotionalMinor());
        assertEquals(settings.orders().maximumOpenPerPlayer(),
                snapshot.maximumOpenOrdersPerPlayer());
        assertEquals(settings.orders().maximumOpenPerProductPerPlayer(),
                snapshot.maximumOpenOrdersPerProductPerPlayer());
        assertEquals(settings.orders().maximumEscrowedValuePerPlayerMinor(),
                snapshot.maximumEscrowedValuePerPlayerMinor());
        assertEquals(BazaarSelfTradePolicy.CANCEL_TAKER, snapshot.selfTradePolicy());
        assertEquals(BazaarExecutionPricePolicy.MAKER, snapshot.executionPricePolicy());
        assertEquals(settings.marketSafety().circuitBreaker(),
                snapshot.circuitBreakerEnabled());
        assertEquals(settings.marketSafety().priceBandBasisPoints(),
                snapshot.priceBandBasisPoints());
        assertEquals(BazaarRuleSnapshotFactory.MINIMUM_LIFETIME_MILLIS,
                snapshot.minimumLifetimeMillis());
        assertEquals(9L, snapshot.configRevision());
    }

    @Test
    void nextEffectiveKeepsRevisionUntilTheConfigurationChangesMeaning() {
        BazaarConfig.Settings settings = defaults();
        BazaarRuleSnapshot first = BazaarRuleSnapshotFactory.nextEffective(settings,
                Optional.empty());
        assertEquals(BazaarRuleSnapshotFactory.FIRST_CONFIG_REVISION, first.configRevision());

        BazaarRuleSnapshot unchanged = BazaarRuleSnapshotFactory.nextEffective(settings,
                Optional.of(first));
        assertEquals(first, unchanged);

        BazaarConfig.Settings changed = withFees(new BazaarConfig.FeeRules(11, 25, "void"));
        BazaarRuleSnapshot advanced = BazaarRuleSnapshotFactory.nextEffective(changed,
                Optional.of(first));
        assertEquals(Math.addExact(first.configRevision(), 1L), advanced.configRevision());
        assertEquals(11, advanced.makerFeeBasisPoints());

        // The advanced snapshot is exactly what the book's setEffectiveRules accepts: the same
        // revision never means two different rule sets and revisions never move backward.
        BazaarOrderBook book = new BazaarOrderBook();
        book.setEffectiveRules(first);
        book.setEffectiveRules(advanced);
        assertEquals(Optional.of(advanced), book.effectiveRules());
    }

    @Test
    void policyParsingIsStrictAndCaseInsensitive() {
        assertEquals(BazaarSelfTradePolicy.CANCEL_MAKER,
                BazaarRuleSnapshotFactory.selfTradePolicy(" Cancel_Maker "));
        assertEquals(BazaarSelfTradePolicy.SKIP_SELF,
                BazaarRuleSnapshotFactory.selfTradePolicy("skip_self"));
        assertEquals(BazaarExecutionPricePolicy.MIDPOINT,
                BazaarRuleSnapshotFactory.executionPricePolicy("MIDPOINT"));
        assertThrows(IllegalArgumentException.class,
                () -> BazaarRuleSnapshotFactory.selfTradePolicy("cancel_everyone"));
        assertThrows(IllegalArgumentException.class,
                () -> BazaarRuleSnapshotFactory.executionPricePolicy(""));
    }

    @Test
    void wireEnumParsingFailsClosed() {
        assertEquals(BazaarOrderSide.BUY, BazaarActionService.parseSide(" BUY "));
        assertEquals(BazaarOrderSide.SELL, BazaarActionService.parseSide("sell"));
        assertNull(BazaarActionService.parseSide("short"));
        assertNull(BazaarActionService.parseSide(null));

        assertEquals(BazaarOrderType.LIMIT, BazaarActionService.parseType("limit"));
        assertEquals(BazaarOrderType.INSTANT, BazaarActionService.parseType("Instant"));
        assertNull(BazaarActionService.parseType("market"));

        assertEquals(BazaarTimeInForce.GOOD_UNTIL_CANCELLED,
                BazaarActionService.parseTimeInForce("good_until_cancelled"));
        assertEquals(BazaarTimeInForce.FILL_OR_KILL,
                BazaarActionService.parseTimeInForce("FILL_OR_KILL"));
        assertNull(BazaarActionService.parseTimeInForce("forever"));

        assertEquals(BazaarEscrowPaymentSource.WALLET,
                BazaarActionService.parsePaymentSource("wallet"));
        assertEquals(BazaarEscrowPaymentSource.INVENTORY_CASH,
                BazaarActionService.parsePaymentSource("physical"));
        assertEquals(BazaarEscrowPaymentSource.INVENTORY_CASH,
                BazaarActionService.parsePaymentSource("inventory_cash"));
        assertNull(BazaarActionService.parsePaymentSource("credit"));
    }

    @Test
    void shapeDenialFollowsTheConfiguredOrderRules() {
        BazaarConfig.OrderRules orders = defaults().orders();
        assertNull(BazaarActionService.shapeDenial(orders, BazaarOrderSide.BUY,
                BazaarOrderType.LIMIT, BazaarTimeInForce.GOOD_UNTIL_CANCELLED));
        assertNull(BazaarActionService.shapeDenial(orders, BazaarOrderSide.SELL,
                BazaarOrderType.LIMIT, BazaarTimeInForce.GOOD_UNTIL_TIME));

        // An instant order must not rest (plan §9 order types).
        assertEquals("time_in_force", BazaarActionService.shapeDenial(orders,
                BazaarOrderSide.BUY, BazaarOrderType.INSTANT,
                BazaarTimeInForce.GOOD_UNTIL_CANCELLED));
        // An INSTANT order's non-resting TIF is mechanical — allowed regardless of the
        // allow_immediate_or_cancel / allow_fill_or_kill flags (plan §9 ships instant buy/sell
        // in the initial release; those flags govern LIMIT orders only).
        assertNull(BazaarActionService.shapeDenial(orders,
                BazaarOrderSide.BUY, BazaarOrderType.INSTANT,
                BazaarTimeInForce.IMMEDIATE_OR_CANCEL));
        assertNull(BazaarActionService.shapeDenial(orders,
                BazaarOrderSide.SELL, BazaarOrderType.INSTANT,
                BazaarTimeInForce.FILL_OR_KILL));
        // The IOC/FOK config flags still gate LIMIT orders (default off until proven).
        assertEquals("time_in_force", BazaarActionService.shapeDenial(orders,
                BazaarOrderSide.BUY, BazaarOrderType.LIMIT,
                BazaarTimeInForce.IMMEDIATE_OR_CANCEL));
        assertEquals("time_in_force", BazaarActionService.shapeDenial(orders,
                BazaarOrderSide.SELL, BazaarOrderType.LIMIT,
                BazaarTimeInForce.FILL_OR_KILL));

        BazaarConfig.OrderRules sellOnly = new BazaarConfig.OrderRules(false, true, true,
                true, false, false, 32, 8, 1_000_000, 100_000_000_000L, 500_000_000_000L,
                168, 720);
        assertEquals("side", BazaarActionService.shapeDenial(sellOnly, BazaarOrderSide.BUY,
                BazaarOrderType.LIMIT, BazaarTimeInForce.GOOD_UNTIL_CANCELLED));

        BazaarConfig.OrderRules instantOnly = new BazaarConfig.OrderRules(true, true, false,
                true, true, false, 32, 8, 1_000_000, 100_000_000_000L, 500_000_000_000L,
                168, 720);
        assertEquals("type", BazaarActionService.shapeDenial(instantOnly, BazaarOrderSide.BUY,
                BazaarOrderType.LIMIT, BazaarTimeInForce.GOOD_UNTIL_CANCELLED));
        assertNull(BazaarActionService.shapeDenial(instantOnly, BazaarOrderSide.BUY,
                BazaarOrderType.INSTANT, BazaarTimeInForce.IMMEDIATE_OR_CANCEL));
    }

    @Test
    void expirationMathIsExactAndFailsClosed() {
        BazaarConfig.OrderRules orders = defaults().orders();
        long now = 1_000_000L;

        // Non-persistent time in force never carries a deadline and rejects one being smuggled.
        assertEquals(0L, BazaarActionService.expiresAtMillis(orders,
                BazaarTimeInForce.GOOD_UNTIL_CANCELLED, 0L, now));
        assertThrows(IllegalArgumentException.class,
                () -> BazaarActionService.expiresAtMillis(orders,
                        BazaarTimeInForce.GOOD_UNTIL_CANCELLED, 4L, now));
        assertThrows(IllegalArgumentException.class,
                () -> BazaarActionService.expiresAtMillis(orders,
                        BazaarTimeInForce.IMMEDIATE_OR_CANCEL, 1L, now));

        // Zero hours means the configured default; explicit hours are bounded by the maximum.
        assertEquals(now + orders.defaultExpirationHours() * 3_600_000L,
                BazaarActionService.expiresAtMillis(orders,
                        BazaarTimeInForce.GOOD_UNTIL_TIME, 0L, now));
        assertEquals(now + 5L * 3_600_000L, BazaarActionService.expiresAtMillis(orders,
                BazaarTimeInForce.GOOD_UNTIL_TIME, 5L, now));
        assertThrows(IllegalArgumentException.class,
                () -> BazaarActionService.expiresAtMillis(orders,
                        BazaarTimeInForce.GOOD_UNTIL_TIME,
                        orders.maximumExpirationHours() + 1L, now));
        assertThrows(ArithmeticException.class,
                () -> BazaarActionService.expiresAtMillis(orders,
                        BazaarTimeInForce.GOOD_UNTIL_TIME, 5L, Long.MAX_VALUE));
    }

    @Test
    void derivedIdentitiesAreDeterministicAndDistinctPerPurpose() {
        UUID requestId = new UUID(7L, 9L);
        assertEquals(BazaarActionService.derived("bazaar.order.", requestId),
                BazaarActionService.derived("bazaar.order.", requestId));
        assertNotEquals(BazaarActionService.derived("bazaar.order.", requestId),
                BazaarActionService.derived("bazaar.hold.", requestId));
        assertNotEquals(BazaarActionService.derived("bazaar.order.", requestId),
                BazaarActionService.derived("bazaar.order.", new UUID(7L, 10L)));
    }

    @Test
    void effectiveRulesComeFromTheBooksEffectiveRevisionOnly() {
        BazaarOrderBook book = new BazaarOrderBook();
        assertTrue(BazaarActionService.effectiveRules(book.snapshot()).isEmpty());
        assertEquals(0L, BazaarActionService.configRevision(book.snapshot()));

        BazaarRuleSnapshot rules = BazaarRuleSnapshotFactory.from(defaults(), 3L);
        book.setEffectiveRules(rules);
        assertEquals(Optional.of(rules),
                BazaarActionService.effectiveRules(book.snapshot()));
        assertEquals(3L, BazaarActionService.configRevision(book.snapshot()));
    }
}
