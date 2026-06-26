package com.enviouse.futureshops.server.transaction;

import com.enviouse.futureshops.network.packets.C2SBuyRequestPacket.LineItem;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The load-bearing guarantee of the feature: {@link ShopBuyService#mergeLines} merges buy lines by
 * <em>listingId</em>, so two NBT variants that share a registry itemId stay distinct cart lines and
 * each resolves to its own listing — they must NOT collapse the way registry-itemId keying did.
 */
public class MergeLinesMultiVariantTest {

    @Test
    void variantsWithDistinctListingIdsDoNotCollapse() {
        Map<String, Integer> merged = ShopBuyService.mergeLines(List.of(
                new LineItem("enchanted_book_1", 2),
                new LineItem("enchanted_book_2", 3),
                new LineItem("enchanted_book_1", 1)));

        assertEquals(2, merged.size(), "two distinct listingIds remain two lines");
        assertEquals(3, merged.get("enchanted_book_1"), "same listingId accumulates");
        assertEquals(3, merged.get("enchanted_book_2"));
        assertFalse(merged.containsKey("minecraft:enchanted_book"),
                "merge keys on listingId, not the shared registry itemId");
    }

    @Test
    void blankAndNullListingIdsAreDropped() {
        Map<String, Integer> merged = ShopBuyService.mergeLines(List.of(
                new LineItem("ok_1", 5),
                new LineItem("", 9),
                new LineItem(null, 9)));
        assertEquals(1, merged.size());
        assertEquals(5, merged.get("ok_1"));
    }
}
