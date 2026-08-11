package com.enviouse.futureshops.client.editor;

import com.enviouse.futureshops.catalog.offer.OfferItemComponent;
import com.enviouse.futureshops.catalog.offer.OfferLimitPolicy;
import com.enviouse.futureshops.catalog.offer.OfferSchedule;
import com.enviouse.futureshops.catalog.offer.OfferStockPolicy;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferListing;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferValidator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfferEditorTemplatesTest {
    private static final OfferItemComponent HELD =
            new OfferItemComponent(
                    "output", "minecraft:iron_ingot", 4, "");

    @Test
    void templatesCreateNormalizedDirectionAndPaymentShapes() {
        ServerShopOfferListing money = apply(
                OfferEditorTemplates.Template.MONEY);
        ServerShopOfferListing free = apply(
                OfferEditorTemplates.Template.FREE);
        ServerShopOfferListing choice = apply(
                OfferEditorTemplates.Template.MONEY_OR_BARTER);
        ServerShopOfferListing compound = apply(
                OfferEditorTemplates.Template.MONEY_AND_BARTER);
        ServerShopOfferListing sell = apply(
                OfferEditorTemplates.Template.SELL);
        ServerShopOfferListing both = apply(
                OfferEditorTemplates.Template.BUY_AND_SELL);

        assertTrue(money.acquireOptions().get(0).moneyCostPresent());
        assertTrue(free.acquireOptions().get(0).free());
        assertEquals(2, choice.acquireOptions().size());
        assertTrue(compound.acquireOptions().get(0)
                .moneyCostPresent());
        assertTrue(compound.acquireOptions().get(0)
                .itemCosts().isEmpty());
        assertTrue(sell.acquireOptions().isEmpty());
        assertEquals(List.of(HELD),
                sell.sellOptions().get(0).itemInputs());
        assertFalse(both.acquireOptions().isEmpty());
        assertFalse(both.sellOptions().isEmpty());
    }

    @Test
    void blankAdvancedTemplateDoesNotInventComponents() {
        ServerShopOfferListing result = OfferEditorTemplates.apply(
                base(), OfferEditorTemplates.Template.ADVANCED,
                Optional.empty());

        assertTrue(result.outputs().isEmpty());
        assertTrue(result.acquireOptions().isEmpty());
        assertTrue(result.sellOptions().isEmpty());
    }

    @Test
    void quickAddPreservesDirectionPriceStockAndBundleOutputs() {
        ServerShopOfferListing buy = OfferEditorTemplates.quickAdd(
                base(), OfferEditorTemplates.Template.MONEY,
                List.of(HELD), "Iron", 100L, 12L);
        ServerShopOfferListing sell = OfferEditorTemplates.quickAdd(
                base(), OfferEditorTemplates.Template.SELL,
                List.of(HELD), "Iron", 75L, 20L);
        OfferItemComponent second = new OfferItemComponent(
                "output_2", "minecraft:stick", 2, "");
        ServerShopOfferListing bundle = OfferEditorTemplates.quickAdd(
                base(), OfferEditorTemplates.Template.BUNDLE,
                List.of(HELD, second), "Tools", 250L, -1L);

        assertEquals(100L,
                buy.acquireOptions().get(0).moneyCostMinorUnits());
        assertEquals(OfferStockPolicy.Type.LIMITED_INDEPENDENT,
                buy.stockPolicy().type());
        assertEquals(12L, buy.stockPolicy().quantity());
        assertTrue(sell.outputs().isEmpty());
        assertEquals(List.of(HELD),
                sell.sellOptions().get(0).itemInputs());
        assertEquals(75L,
                sell.sellOptions().get(0).moneyPayoutMinorUnits());
        assertEquals(20L, sell.sellOptions().get(0).capacity());
        assertEquals(List.of(HELD, second), bundle.outputs());
        assertEquals(250L,
                bundle.acquireOptions().get(0).moneyCostMinorUnits());
        assertTrue(ServerShopOfferValidator.validate(buy).valid());
        assertTrue(ServerShopOfferValidator.validate(sell).valid());
        assertTrue(ServerShopOfferValidator.validate(bundle).valid());
    }

    @Test
    void quickAddRejectsFreeMoneyAndKeepsBarterIncompleteForEditor() {
        assertThrows(IllegalArgumentException.class, () ->
                OfferEditorTemplates.quickAdd(
                        base(), OfferEditorTemplates.Template.MONEY,
                        List.of(HELD), "Iron", 0L, -1L));
        ServerShopOfferListing barter = OfferEditorTemplates.quickAdd(
                base(), OfferEditorTemplates.Template.BARTER,
                List.of(HELD), "Iron", 0L, -1L);
        assertEquals(List.of(HELD), barter.outputs());
        assertTrue(barter.acquireOptions().get(0).itemCosts().isEmpty());
    }

    private static ServerShopOfferListing apply(
            OfferEditorTemplates.Template template
    ) {
        return OfferEditorTemplates.apply(
                base(), template, Optional.of(HELD));
    }

    private static ServerShopOfferListing base() {
        return new ServerShopOfferListing(
                "new_offer", 0L, "", "", "all", "", "",
                true, 0L, "", List.of(), List.of(), List.of(),
                OfferStockPolicy.unlimited(),
                OfferLimitPolicy.defaults(), OfferSchedule.always(),
                List.of());
    }
}
