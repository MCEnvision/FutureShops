package com.enviouse.futureshops.catalog.offer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerShopOfferValidatorTest {
    @Test
    void requiredTradeMatrixValidates() {
        assertValid(listing(List.of(AcquireOfferOption.free("free")),
                List.of(), oneOutput()));
        assertValid(listing(List.of(AcquireOfferOption.money("money", 10L)),
                List.of(), oneOutput()));
        assertValid(listing(List.of(items("barter",
                        component("iron", "minecraft:iron_ingot", 4))),
                List.of(), oneOutput()));
        assertValid(listing(List.of(
                        AcquireOfferOption.money("money", 10L),
                        items("barter",
                                component("iron",
                                        "minecraft:iron_ingot", 4))),
                List.of(), oneOutput()));
        assertValid(listing(List.of(compound("compound", 5L,
                        component("emerald", "minecraft:emerald", 2))),
                List.of(), oneOutput()));
        assertValid(listing(List.of(
                        items("iron", component("iron",
                                "minecraft:iron_ingot", 4)),
                        items("gold", component("gold",
                                "minecraft:gold_ingot", 2))),
                List.of(), oneOutput()));
        assertValid(listing(List.of(items("multi",
                        component("iron", "minecraft:iron_ingot", 4),
                        component("stick", "minecraft:stick", 1))),
                List.of(), oneOutput()));
        assertValid(listing(List.of(AcquireOfferOption.money("money", 10L)),
                List.of(), oneOutput()));
        assertValid(listing(List.of(), List.of(sell("sell",
                component("input", "minecraft:iron_ingot", 1))),
                oneOutput()));
        assertValid(new ServerShopOfferListing(
                "sell_only", 0L, "Sell Only", "", "all",
                "minecraft:iron_ingot", "", true, 0L, "",
                List.of(), List.of(), List.of(sell("sell",
                component("input", "minecraft:iron_ingot", 1))),
                OfferStockPolicy.unlimited(),
                OfferLimitPolicy.defaults(), OfferSchedule.always(),
                List.of()));
        assertValid(listing(List.of(AcquireOfferOption.money("money", 10L)),
                List.of(sell("sell",
                        component("input", "minecraft:iron_ingot", 1))),
                oneOutput()));
        assertValid(listing(List.of(), List.of(
                        sell("iron", component("iron",
                                "minecraft:iron_ingot", 2)),
                        sell("gold", component("gold",
                                "minecraft:gold_ingot", 1))),
                oneOutput()));
        assertValid(listing(List.of(AcquireOfferOption.money("money", 10L)),
                List.of(), List.of(
                        component("pickaxe",
                                "minecraft:iron_pickaxe", 1),
                        component("sword", "minecraft:iron_sword", 1),
                        component("shovel",
                                "minecraft:iron_shovel", 1))));
        assertValid(listing(List.of(), List.of(sell("bundle",
                        component("pickaxe",
                                "minecraft:iron_pickaxe", 1),
                        component("sword", "minecraft:iron_sword", 1))),
                oneOutput()));
    }

    @Test
    void freeCannotContainCosts() {
        AcquireOfferOption invalid = new AcquireOfferOption("free",
                "Free", true, true, 1L,
                List.of(component("iron", "minecraft:iron_ingot", 1)),
                1, OfferLimitPolicy.defaults(), OfferSchedule.always(), "");
        assertFalse(ServerShopOfferValidator.validate(
                listing(List.of(invalid), List.of(), oneOutput())).valid());
    }

    @Test
    void legacyZeroIsNotCompiledAsFree() {
        com.enviouse.futureshops.catalog.ItemDef item =
                new com.enviouse.futureshops.catalog.ItemDef(
                        "minecraft:diamond", "Diamond",
                        0L, 0L, -1, false, "all");
        ServerShopOfferListing compiled =
                LegacyServerShopOfferCompiler.compile(item, List.of());
        assertTrue(compiled.acquireOptions().isEmpty());
        assertTrue(compiled.sellOptions().isEmpty());
    }

    @Test
    void duplicateIdsAndUnnormalizedComponentsFail() {
        AcquireOfferOption duplicateOne = items("same",
                component("one", "minecraft:iron_ingot", 1),
                component("two", "minecraft:iron_ingot", 2));
        AcquireOfferOption duplicateTwo =
                AcquireOfferOption.money("same", 5L);
        OfferValidationResult result = ServerShopOfferValidator.validate(
                listing(List.of(duplicateOne, duplicateTwo), List.of(),
                        oneOutput()));
        assertFalse(result.valid());
        assertTrue(result.issues().stream().anyMatch(issue ->
                issue.code().equals("offer.option.duplicate_id")));
        assertTrue(result.issues().stream().anyMatch(issue ->
                issue.code().equals("offer.component.not_normalized")));
    }

    @Test
    void revisionIsStableAndChangesWithContent() {
        ServerShopOfferListing original = listing(
                List.of(AcquireOfferOption.money("money", 10L)),
                List.of(), oneOutput());
        long first = ServerShopOfferRevision.compute(original);
        long second = ServerShopOfferRevision.compute(original);
        ServerShopOfferListing changed = listing(
                List.of(AcquireOfferOption.money("money", 11L)),
                List.of(), oneOutput());
        assertTrue(first >= 0L);
        assertTrue(first <= ServerShopOfferRevision.MAXIMUM_REVISION);
        assertTrue(first == second);
        assertNotEquals(first, ServerShopOfferRevision.compute(changed));
    }

    @Test
    void bundleComparisonRequiresExactActiveStandaloneMoneyListing() {
        OfferItemComponent bundleOutput =
                component("tool", "minecraft:iron_pickaxe", 1);
        ServerShopOfferListing bundle = new ServerShopOfferListing(
                "bundle", 0L, "Bundle", "", "all",
                bundleOutput.itemId(), "", true, 0L, "",
                List.of(bundleOutput),
                List.of(AcquireOfferOption.money("bundle_money", 8L)),
                List.of(), OfferStockPolicy.unlimited(),
                OfferLimitPolicy.defaults(), OfferSchedule.always(),
                List.of(new OfferBundleComparison(
                        "tool", "standalone", "money")));
        ServerShopOfferListing mismatched = new ServerShopOfferListing(
                "standalone", 0L, "Standalone", "", "all",
                "minecraft:iron_sword", "", false, 0L, "",
                List.of(component(
                        "output", "minecraft:iron_sword", 1)),
                List.of(compound("money", 4L,
                        component("stick", "minecraft:stick", 1))),
                List.of(), OfferStockPolicy.unlimited(),
                OfferLimitPolicy.defaults(), OfferSchedule.always(),
                List.of());

        OfferValidationResult invalid =
                ServerShopOfferCatalogValidator.validate(
                        List.of(bundle, mismatched),
                        ignored -> true, ignored -> true);

        assertFalse(invalid.valid());
        assertTrue(invalid.issues().stream().anyMatch(issue ->
                issue.code().equals(
                        "offer.comparison.listing_inactive")));
        assertTrue(invalid.issues().stream().anyMatch(issue ->
                issue.code().equals(
                        "offer.comparison.output_mismatch")));
        assertTrue(invalid.issues().stream().anyMatch(issue ->
                issue.code().equals(
                        "offer.comparison.money_option_missing")));
    }

    @Test
    void escrowFanoutAcceptsBoundaryAndRejectsUnexecutableListing() {
        OfferLimitPolicy maximum = new OfferLimitPolicy(
                2_304, 0L, 0L, 0L, 0L);
        List<OfferItemComponent> boundary = List.of(
                component("one", "minecraft:iron_sword", 1),
                component("two", "minecraft:iron_pickaxe", 1),
                component("three", "minecraft:iron_shovel", 1));
        ServerShopOfferListing accepted = listing(
                List.of(new AcquireOfferOption(
                        "money", "Money", false, true, 1L,
                        List.of(), 1, maximum,
                        OfferSchedule.always(), "")),
                List.of(), boundary);
        assertTrue(ServerShopOfferValidator.validate(
                accepted, ignored -> true, ignored -> true,
                ignored -> 1).valid());

        List<OfferItemComponent> oversized = List.of(
                component("one", "minecraft:iron_sword", 1),
                component("two", "minecraft:iron_pickaxe", 1),
                component("three", "minecraft:iron_shovel", 1),
                component("four", "minecraft:iron_hoe", 1));
        OfferValidationResult rejected =
                ServerShopOfferValidator.validate(
                        listing(accepted.acquireOptions(),
                                List.of(), oversized),
                        ignored -> true, ignored -> true,
                        ignored -> 1);
        assertFalse(rejected.valid());
        assertTrue(rejected.issues().stream().anyMatch(issue ->
                issue.code().equals(
                        "offer.escrow.fanout_out_of_bounds")));
    }

    @Test
    void escrowFanoutCountsInputsAndOutputsTogether() {
        OfferLimitPolicy maximum = new OfferLimitPolicy(
                2_304, 0L, 0L, 0L, 0L);
        AcquireOfferOption option = new AcquireOfferOption(
                "compound", "Money and Items", false, true, 1L,
                List.of(
                        component("first", "minecraft:iron_sword", 1),
                        component("second", "minecraft:iron_pickaxe", 1)),
                1, maximum, OfferSchedule.always(), "");
        OfferValidationResult rejected =
                ServerShopOfferValidator.validate(
                        listing(List.of(option), List.of(),
                                List.of(
                                        component("third",
                                                "minecraft:iron_shovel", 1),
                                        component("fourth",
                                                "minecraft:iron_hoe", 1))),
                        ignored -> true, ignored -> true,
                        ignored -> 1);
        assertFalse(rejected.valid());
        assertTrue(rejected.issues().stream().anyMatch(issue ->
                issue.code().equals(
                        "offer.escrow.fanout_out_of_bounds")));
    }

    private static void assertValid(ServerShopOfferListing listing) {
        OfferValidationResult result =
                ServerShopOfferValidator.validate(listing);
        assertTrue(result.valid(), () -> result.issues().toString());
    }

    private static ServerShopOfferListing listing(
            List<AcquireOfferOption> acquire,
            List<SellOfferOption> sell,
            List<OfferItemComponent> outputs
    ) {
        return new ServerShopOfferListing("listing", 0L, "Listing", "",
                "all", outputs.get(0).itemId(), outputs.get(0).exactNbt(),
                true, 0L, "", outputs, acquire, sell,
                OfferStockPolicy.unlimited(), OfferLimitPolicy.defaults(),
                OfferSchedule.always(), List.of());
    }

    private static List<OfferItemComponent> oneOutput() {
        return List.of(component("output", "minecraft:diamond", 1));
    }

    private static OfferItemComponent component(
            String id,
            String item,
            int count
    ) {
        return new OfferItemComponent(id, item, count, "");
    }

    private static AcquireOfferOption items(
            String id,
            OfferItemComponent... components
    ) {
        return new AcquireOfferOption(id, "Items", false, false, 0L,
                List.of(components), 1, OfferLimitPolicy.defaults(),
                OfferSchedule.always(), "");
    }

    private static AcquireOfferOption compound(
            String id,
            long money,
            OfferItemComponent... components
    ) {
        return new AcquireOfferOption(id, "Money and Items", false, true,
                money, List.of(components), 1,
                OfferLimitPolicy.defaults(), OfferSchedule.always(), "");
    }

    private static SellOfferOption sell(
            String id,
            OfferItemComponent... components
    ) {
        return new SellOfferOption(id, "Sell to Shop",
                List.of(components), 10L, 0L,
                OfferLimitPolicy.defaults(), OfferSchedule.always(), "");
    }
}
