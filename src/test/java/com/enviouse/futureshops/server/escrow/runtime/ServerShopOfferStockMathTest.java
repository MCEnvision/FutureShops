package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.catalog.offer.AcquireOfferOption;
import com.enviouse.futureshops.catalog.offer.OfferAction;
import com.enviouse.futureshops.catalog.offer.OfferItemComponent;
import com.enviouse.futureshops.catalog.offer.OfferLimitPolicy;
import com.enviouse.futureshops.catalog.offer.OfferSchedule;
import com.enviouse.futureshops.catalog.offer.OfferStockPolicy;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferListing;
import com.enviouse.futureshops.money.PaymentSource;
import com.enviouse.futureshops.server.escrow.stock.CatalogStockState;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServerShopOfferStockMathTest {
    @Test
    void singleAcquireReservationUsesOutputMultiplier() throws Exception {
        AcquireOfferOption option = option(3);
        ServerShopOfferService.Request request =
                new ServerShopOfferService.Request(
                        UUID.randomUUID(), UUID.randomUUID(),
                        "default", "iron_pack", "money",
                        OfferAction.ACQUIRE_FROM_SHOP, 4, 2L,
                        Optional.of(PaymentSource.WALLET), 0);
        Class<?> factsType = Class.forName(
                ServerShopOfferService.class.getName() + "$OptionFacts");
        Constructor<?> constructor = factsType.getDeclaredConstructor(
                AcquireOfferOption.class,
                com.enviouse.futureshops.catalog.offer
                        .SellOfferOption.class,
                boolean.class, long.class, OfferLimitPolicy.class,
                OfferSchedule.class, String.class);
        constructor.setAccessible(true);
        Object facts = constructor.newInstance(
                option, null, true, 100L, option.limits(),
                option.schedule(), "");
        Method stockQuantity = ServerShopOfferService.class
                .getDeclaredMethod(
                        "stockQuantity",
                        ServerShopOfferService.Request.class,
                        factsType);
        stockQuantity.setAccessible(true);

        assertEquals(12, stockQuantity.invoke(
                null, request, facts));
    }

    @Test
    void cartReservationAddsMultipliedOutputQuantities()
            throws Exception {
        AcquireOfferOption option = option(3);
        ServerShopOfferListing listing = listing(option);
        Class<?> lineType = Class.forName(
                ServerShopOfferCartService.class.getName()
                        + "$QuotedLine");
        Constructor<?> constructor = lineType.getDeclaredConstructor(
                ServerShopOfferListing.class, AcquireOfferOption.class,
                int.class, CatalogStockState.class, long.class,
                Optional.class);
        constructor.setAccessible(true);
        Object first = constructor.newInstance(
                listing, option, 2, null, 0L, Optional.empty());
        Object second = constructor.newInstance(
                listing, option, 4, null, 0L, Optional.empty());
        Method stockTotals = ServerShopOfferCartService.class
                .getDeclaredMethod("stockTotals", List.class);
        stockTotals.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, Integer> totals =
                (Map<String, Integer>) stockTotals.invoke(
                        null, List.of(first, second));

        assertEquals(18, totals.get("iron_pack"));
    }

    @Test
    void cartUsageTotalsRemainInRequestedBundleUnits() {
        AcquireOfferOption option = option(3);
        ServerShopOfferListing listing = listing(option);
        List<ServerShopOfferCartPreparedSavedData.QuotedLine> lines =
                List.of(
                        new ServerShopOfferCartPreparedSavedData.QuotedLine(
                                listing, option.optionId(), 2, 1L,
                                0L, Optional.empty()),
                        new ServerShopOfferCartPreparedSavedData.QuotedLine(
                                listing, option.optionId(), 4, 1L,
                                0L, Optional.empty()));

        Map<String, Integer> totals =
                ServerShopOfferCartService.requestedListingTotals(lines);

        assertEquals(6, totals.get("iron_pack"));
    }

    private static AcquireOfferOption option(int outputMultiplier) {
        return new AcquireOfferOption(
                "money", "Money", false, true, 5L, List.of(),
                outputMultiplier, OfferLimitPolicy.defaults(),
                OfferSchedule.always(), "");
    }

    private static ServerShopOfferListing listing(
            AcquireOfferOption option
    ) {
        OfferItemComponent output = new OfferItemComponent(
                "iron", "minecraft:iron_ingot", 1, "");
        return new ServerShopOfferListing(
                "iron_pack", 2L, "Iron pack", "", "all",
                "minecraft:iron_ingot", "", true, 0L, "",
                List.of(output), List.of(option), List.of(),
                OfferStockPolicy.limited(100L, 0L),
                OfferLimitPolicy.defaults(), OfferSchedule.always(),
                List.of());
    }
}
