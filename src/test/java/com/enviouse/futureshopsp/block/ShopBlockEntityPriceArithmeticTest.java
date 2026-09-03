package com.enviouse.futureshopsp.block;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShopBlockEntityPriceArithmeticTest {
    @Test
    void listingPriceOverflowIsRejected() {
        ShopBlockEntity.Listing listing = new ShopBlockEntity.Listing("minecraft:stone");
        listing.setMoneyPriceMinor(Long.MAX_VALUE);

        assertEquals(-1L, listing.calculatePrice(2));
    }

    @Test
    void buybackAndBarterOverflowAreRejected() {
        ShopBlockEntity.Listing listing = new ShopBlockEntity.Listing("minecraft:stone");
        listing.setBuybackPriceMinor(Long.MAX_VALUE);
        listing.setBarterItemCount(Integer.MAX_VALUE);

        assertEquals(-1L, listing.calculateBuybackTotal(2));
        assertEquals(-1, listing.effectiveBarterTotal(2));
    }
}
