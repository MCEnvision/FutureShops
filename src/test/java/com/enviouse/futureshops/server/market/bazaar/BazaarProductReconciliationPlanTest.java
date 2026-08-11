package com.enviouse.futureshops.server.market.bazaar;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BazaarProductReconciliationPlanTest {
    @Test
    void registersNewVersionsAndRetiresRemovedProducts() {
        BazaarOrderBook book = new BazaarOrderBook();
        book.registerProduct(product("iron", 1L, 1L,
                BazaarProductStatus.ACTIVE));
        book.registerProduct(product("gold", 1L, 1L,
                BazaarProductStatus.ACTIVE));
        BazaarProductDefinition ironVersionTwo = definition(
                product("iron", 2L, 5L, BazaarProductStatus.ACTIVE));
        BazaarProductCatalogSnapshot catalog =
                BazaarProductCatalogSnapshot.of(List.of(ironVersionTwo));

        BazaarProductReconciliationPlan plan =
                BazaarProductReconciliationPlan.create(book.snapshot(),
                        catalog);

        assertEquals(2, plan.actions().size());
        assertEquals(BazaarProductReconciliationAction.Type.REGISTER,
                plan.actions().get(0).type());
        assertEquals(BazaarProductReconciliationAction.Type.SET_STATUS,
                plan.actions().get(1).type());
        assertEquals(BazaarProductStatus.RETIRED,
                plan.actions().get(1).status().orElseThrow());
        assertEquals(plan.commands(), plan.commands());
        assertNotEquals(plan.commands().get(0).mutationId(),
                plan.commands().get(1).mutationId());
    }

    @Test
    void statusOnlyChangeDoesNotRewriteProductIdentity() {
        BazaarOrderBook book = new BazaarOrderBook();
        book.registerProduct(product("iron", 1L, 1L,
                BazaarProductStatus.ACTIVE));
        BazaarProductCatalogSnapshot catalog =
                BazaarProductCatalogSnapshot.of(List.of(definition(
                        product("iron", 1L, 1L,
                                BazaarProductStatus.HALTED))));

        BazaarProductReconciliationPlan plan =
                BazaarProductReconciliationPlan.create(book.snapshot(),
                        catalog);

        assertEquals(1, plan.actions().size());
        assertEquals(BazaarProductReconciliationAction.Type.SET_STATUS,
                plan.actions().get(0).type());
    }

    @Test
    void sameVersionRuleChangeRequiresVersionBump() {
        BazaarOrderBook book = new BazaarOrderBook();
        book.registerProduct(product("iron", 1L, 1L,
                BazaarProductStatus.ACTIVE));
        BazaarProductCatalogSnapshot changed =
                BazaarProductCatalogSnapshot.of(List.of(definition(
                        product("iron", 1L, 10L,
                                BazaarProductStatus.ACTIVE))));

        assertThrows(IllegalArgumentException.class, () ->
                BazaarProductReconciliationPlan.create(book.snapshot(),
                        changed));
    }

    @Test
    void persistedVersionCannotMoveBackward() {
        BazaarOrderBook book = new BazaarOrderBook();
        book.registerProduct(product("iron", 3L, 1L,
                BazaarProductStatus.ACTIVE));
        BazaarProductCatalogSnapshot stale =
                BazaarProductCatalogSnapshot.of(List.of(definition(
                        product("iron", 2L, 1L,
                                BazaarProductStatus.ACTIVE))));

        assertThrows(IllegalArgumentException.class, () ->
                BazaarProductReconciliationPlan.create(book.snapshot(),
                        stale));
    }

    private static BazaarProduct product(String id, long version,
                                          long tick,
                                          BazaarProductStatus status) {
        return new BazaarProduct(id, version, "minecraft:" + id
                + "_ingot", "", "ores", 1, tick, tick,
                1_000_000L, 1000, status);
    }

    private static BazaarProductDefinition definition(
            BazaarProduct product) {
        return new BazaarProductDefinition(product, "",
                product.registryId(), BazaarProductIdentityPolicy.COMMODITY,
                List.of(), BazaarItemRestrictions.safeDefaults(),
                product.productId() + ".json");
    }
}
