package com.enviouse.futureshops.catalog.offer;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerShopBundleSavingsTest {
    private static final Instant NOW =
            Instant.parse("2026-07-24T20:00:00Z");

    @Test
    void calculationRequiresEveryStandalonePermission() {
        Fixture fixture = fixture();

        assertTrue(ServerShopBundleSavings.calculate(
                fixture.bundle(), fixture.bundleOption(), 2,
                fixture.listings(), NOW,
                permission -> !permission.equals("shop.diamond"),
                ServerShopBundleSavingsTest::moneyTotal).isEmpty());
        assertTrue(ServerShopBundleSavings.calculate(
                fixture.bundle(), fixture.bundleOption(), 2,
                fixture.listings(), NOW,
                permission -> !permission.equals("option.apple"),
                ServerShopBundleSavingsTest::moneyTotal).isEmpty());
    }

    @Test
    void calculationCapturesExactComparisonRevisions() {
        Fixture fixture = fixture();
        Set<String> permissions = Set.of(
                "shop.diamond", "option.diamond",
                "shop.apple", "option.apple");

        ServerShopBundleSavings.Snapshot snapshot =
                ServerShopBundleSavings.calculate(
                        fixture.bundle(), fixture.bundleOption(), 2,
                        fixture.listings(), NOW,
                        permissions::contains,
                        ServerShopBundleSavingsTest::moneyTotal)
                        .orElseThrow();

        assertEquals(24L, snapshot.individualTotalMinorUnits());
        assertEquals(18L, snapshot.bundleTotalMinorUnits());
        assertEquals(6L, snapshot.savingsMinorUnits());
        assertEquals(2_500L, snapshot.savingsBasisPoints());
        assertEquals(List.of(
                        new ServerShopBundleSavings.ComparisonRevision(
                                "diamond", "diamond_single",
                                "money", 11L),
                        new ServerShopBundleSavings.ComparisonRevision(
                                "apple", "apple_single",
                                "money", 12L)),
                snapshot.comparisonRevisions());
    }

    @Test
    void snapshotRejectsUnsafeComparisonRevisionEvidence() {
        ServerShopBundleSavings.ComparisonRevision valid =
                new ServerShopBundleSavings.ComparisonRevision(
                        "diamond", "diamond_single", "money", 11L);

        assertThrows(IllegalArgumentException.class,
                () -> new ServerShopBundleSavings.Snapshot(
                        12L, 9L, 3L, 2_500L,
                        List.of(
                                new ServerShopBundleSavings
                                        .ComparisonRevision(
                                        "diamond", "diamond_single",
                                        "money", -1L))));
        assertThrows(IllegalArgumentException.class,
                () -> new ServerShopBundleSavings.Snapshot(
                        12L, 9L, 3L, 2_500L,
                        List.of(valid,
                                new ServerShopBundleSavings
                                        .ComparisonRevision(
                                        "diamond", "other_listing",
                                        "money", 2L))));
    }

    private static long moneyTotal(
            ServerShopOfferListing listing,
            AcquireOfferOption option,
            int quantity
    ) {
        return Math.multiplyExact(
                option.moneyCostMinorUnits(), quantity);
    }

    private static Fixture fixture() {
        OfferItemComponent diamond = new OfferItemComponent(
                "diamond", "minecraft:diamond", 2, "");
        OfferItemComponent apple = new OfferItemComponent(
                "apple", "minecraft:apple", 1, "");
        AcquireOfferOption bundleOption =
                AcquireOfferOption.money("bundle_money", 9L);
        ServerShopOfferListing bundle = listing(
                "tool_bundle", 20L, "", List.of(diamond, apple),
                List.of(bundleOption), List.of(
                        new OfferBundleComparison(
                                "diamond", "diamond_single", "money"),
                        new OfferBundleComparison(
                                "apple", "apple_single", "money")));
        ServerShopOfferListing diamondSingle = listing(
                "diamond_single", 11L, "shop.diamond",
                List.of(new OfferItemComponent(
                        "diamond", "minecraft:diamond", 1, "")),
                List.of(moneyOption(
                        4L, "option.diamond")), List.of());
        ServerShopOfferListing appleSingle = listing(
                "apple_single", 12L, "shop.apple",
                List.of(new OfferItemComponent(
                        "apple", "minecraft:apple", 1, "")),
                List.of(moneyOption(
                        4L, "option.apple")), List.of());
        return new Fixture(bundle, bundleOption, Map.of(
                bundle.listingId(), bundle,
                diamondSingle.listingId(), diamondSingle,
                appleSingle.listingId(), appleSingle));
    }

    private static AcquireOfferOption moneyOption(
            long price,
            String permission
    ) {
        return new AcquireOfferOption(
                "money", "Money", false, true, price, List.of(), 1,
                OfferLimitPolicy.defaults(), OfferSchedule.always(),
                permission);
    }

    private static ServerShopOfferListing listing(
            String id,
            long revision,
            String permission,
            List<OfferItemComponent> outputs,
            List<AcquireOfferOption> options,
            List<OfferBundleComparison> comparisons
    ) {
        return new ServerShopOfferListing(
                id, revision, id, "", "all",
                outputs.get(0).itemId(), "", true, 0L, permission,
                outputs, options, List.of(),
                OfferStockPolicy.unlimited(),
                OfferLimitPolicy.defaults(), OfferSchedule.always(),
                comparisons);
    }

    private record Fixture(
            ServerShopOfferListing bundle,
            AcquireOfferOption bundleOption,
            Map<String, ServerShopOfferListing> listings
    ) {
    }
}
