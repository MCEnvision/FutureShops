package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.catalog.offer.AcquireOfferOption;
import com.enviouse.futureshops.catalog.offer.OfferItemComponent;
import com.enviouse.futureshops.catalog.offer.OfferLimitPolicy;
import com.enviouse.futureshops.catalog.offer.OfferSchedule;
import com.enviouse.futureshops.catalog.offer.OfferStockPolicy;
import com.enviouse.futureshops.catalog.offer.SellOfferOption;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferListing;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerShopOfferVisitorActionPolicyTest {
    @Test
    void freeOnlyListingShowsAcquireCart() {
        ServerShopOfferListing listing = listing(
                List.of(AcquireOfferOption.free("free")), List.of());

        assertTrue(ServerShopOfferVisitorActionPolicy
                .showsAcquireCart(listing));
    }

    @Test
    void freeAndPaidAlternativesShowAcquireCart() {
        ServerShopOfferListing listing = listing(List.of(
                AcquireOfferOption.free("free"),
                AcquireOfferOption.money("money", 100L)), List.of());

        assertTrue(ServerShopOfferVisitorActionPolicy
                .showsAcquireCart(listing));
    }

    @Test
    void sellOnlyListingHidesAcquireCart() {
        SellOfferOption sell = new SellOfferOption(
                "sell", "Sell", List.of(new OfferItemComponent(
                "input", "minecraft:stone", 1, "")),
                100L, -1L, OfferLimitPolicy.defaults(),
                OfferSchedule.always(), "");
        ServerShopOfferListing listing = listing(
                List.of(), List.of(sell));

        assertFalse(ServerShopOfferVisitorActionPolicy
                .showsAcquireCart(listing));
    }

    @Test
    void itemDetailUsesVisitorActionPolicy() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/enviouse/futureshops"
                        + "/client/screen/ItemDetailScreen.java"));

        assertTrue(source.contains(
                "ServerShopOfferVisitorActionPolicy"));
        assertFalse(source.contains(
                "noneMatch(AcquireOfferOption::free)"));
    }

    private static ServerShopOfferListing listing(
            List<AcquireOfferOption> acquire,
            List<SellOfferOption> sell
    ) {
        return new ServerShopOfferListing(
                "test", 1L, "Test", "", "all",
                "minecraft:stone", "", true, 0L, "",
                List.of(new OfferItemComponent(
                        "output", "minecraft:stone", 1, "")),
                acquire, sell, OfferStockPolicy.unlimited(),
                OfferLimitPolicy.defaults(), OfferSchedule.always(),
                List.of());
    }
}
