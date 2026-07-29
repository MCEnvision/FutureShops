package com.enviouse.futureshops.client.editor;

import com.enviouse.futureshops.catalog.offer.AcquireOfferOption;
import com.enviouse.futureshops.catalog.offer.OfferItemComponent;
import com.enviouse.futureshops.catalog.offer.OfferLimitPolicy;
import com.enviouse.futureshops.catalog.offer.OfferSchedule;
import com.enviouse.futureshops.catalog.offer.OfferStockPolicy;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferListing;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfferEditorSimpleModeTest {
    private static final OfferItemComponent OUTPUT =
            new OfferItemComponent(
                    "output", "minecraft:iron_ingot", 1, "");

    @Test
    void appliesAndDetectsEverySimpleTradeShape() {
        for (OfferEditorSimpleMode.Mode mode :
                OfferEditorSimpleMode.Mode.values()) {
            if (mode == OfferEditorSimpleMode.Mode.ADVANCED) {
                continue;
            }
            ServerShopOfferListing result =
                    OfferEditorSimpleMode.apply(base(), mode);
            assertEquals(mode, OfferEditorSimpleMode.detect(result));
        }
    }

    @Test
    void sellOnlyMovesTheOutputIntoTheShopInput() {
        ServerShopOfferListing result = OfferEditorSimpleMode.apply(
                base(), OfferEditorSimpleMode.Mode.SELL_ONLY);

        assertTrue(result.outputs().isEmpty());
        assertEquals(List.of(OUTPUT.itemId()),
                result.sellOptions().get(0).itemInputs().stream()
                        .map(OfferItemComponent::itemId).toList());
        assertEquals(OUTPUT.itemId(), result.iconItemId());
    }

    @Test
    void switchingBackFromSellOnlyRestoresAnOutput() {
        OfferItemComponent second = new OfferItemComponent(
                "second", "minecraft:iron_sword", 1, "");
        ServerShopOfferListing bundle = copyWithOutputs(
                base(), List.of(OUTPUT, second));
        ServerShopOfferListing sellOnly = OfferEditorSimpleMode.apply(
                bundle, OfferEditorSimpleMode.Mode.SELL_ONLY);
        ServerShopOfferListing money = OfferEditorSimpleMode.apply(
                sellOnly, OfferEditorSimpleMode.Mode.MONEY);

        assertEquals(2, sellOnly.sellOptions().get(0)
                .itemInputs().size());
        assertEquals(2, money.outputs().size());
        assertEquals(OUTPUT.itemId(), money.outputs().get(0).itemId());
        assertEquals(second.itemId(), money.outputs().get(1).itemId());
        assertTrue(money.acquireOptions().get(0).moneyCostPresent());
    }

    @Test
    void preservesExistingMoneyAndBarterValuesAcrossModeChanges() {
        AcquireOfferOption compound = new AcquireOfferOption(
                "compound", "Money and barter", false, true,
                250L, List.of(new OfferItemComponent(
                "cost", "minecraft:emerald", 3, "")), 1,
                OfferLimitPolicy.defaults(), OfferSchedule.always(), "");
        ServerShopOfferListing source = copyWithAcquire(
                base(), List.of(compound));

        ServerShopOfferListing choice = OfferEditorSimpleMode.apply(
                source, OfferEditorSimpleMode.Mode.MONEY_OR_BARTER);

        assertEquals(250L, choice.acquireOptions().get(0)
                .moneyCostMinorUnits());
        assertEquals(3, choice.acquireOptions().get(1)
                .itemCosts().get(0).count());
    }

    private static ServerShopOfferListing base() {
        return new ServerShopOfferListing(
                "simple", 0L, "Simple", "", "all",
                OUTPUT.itemId(), "", true, 0L, "",
                List.of(OUTPUT),
                List.of(AcquireOfferOption.money("money", 100L)),
                List.of(), OfferStockPolicy.unlimited(),
                OfferLimitPolicy.defaults(), OfferSchedule.always(),
                List.of());
    }

    private static ServerShopOfferListing copyWithAcquire(
            ServerShopOfferListing listing,
            List<AcquireOfferOption> acquire
    ) {
        return new ServerShopOfferListing(
                listing.listingId(), listing.revision(),
                listing.displayName(), listing.description(),
                listing.categoryId(), listing.iconItemId(),
                listing.iconNbt(), listing.active(),
                listing.expiresAtEpoch(), listing.permissionNode(),
                listing.outputs(), acquire, listing.sellOptions(),
                listing.stockPolicy(), listing.limits(),
                listing.schedule(), listing.bundleComparisons());
    }

    private static ServerShopOfferListing copyWithOutputs(
            ServerShopOfferListing listing,
            List<OfferItemComponent> outputs
    ) {
        return new ServerShopOfferListing(
                listing.listingId(), listing.revision(),
                listing.displayName(), listing.description(),
                listing.categoryId(), listing.iconItemId(),
                listing.iconNbt(), listing.active(),
                listing.expiresAtEpoch(), listing.permissionNode(),
                outputs, listing.acquireOptions(),
                listing.sellOptions(), listing.stockPolicy(),
                listing.limits(), listing.schedule(),
                listing.bundleComparisons());
    }
}
