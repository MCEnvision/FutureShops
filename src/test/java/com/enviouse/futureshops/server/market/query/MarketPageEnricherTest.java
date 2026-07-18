package com.enviouse.futureshops.server.market.query;

import com.enviouse.futureshops.client.market.MarketModule;
import com.enviouse.futureshops.server.market.auction.AuctionHouseBook;
import com.enviouse.futureshops.server.market.auction.AuctionItemLot;
import com.enviouse.futureshops.server.market.auction.AuctionListingType;
import com.enviouse.futureshops.server.market.auction.AuctionRuleSnapshot;
import com.enviouse.futureshops.server.market.auction.AuctionTimeBasis;
import com.enviouse.futureshops.server.market.auction.CreateAuctionCommand;
import com.enviouse.futureshops.server.market.profile.MarketProfileSavedData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketPageEnricherTest {
    @Test
    void auctionCardsExposeNamesExactItemsAndLiveActivity() {
        UUID listingId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        AuctionHouseBook book = new AuctionHouseBook();
        book.create(new CreateAuctionCommand(UUID.randomUUID(), listingId,
                sellerId, UUID.randomUUID(), new AuctionItemLot(
                UUID.randomUUID(), "minecraft:enchanted_book",
                "a".repeat(64), 1, 64, "books", "enchanted book"),
                AuctionListingType.BUY_NOW, 0L, 500L,
                new AuctionRuleSnapshot(0L, 0, 1L, 0, false,
                        60_000L, 60_000L, 0L, 0, true,
                        AuctionTimeBasis.REAL_TIME, true, 1L),
                900L, 0L));
        MarketPageQuery query = new MarketPageQuery(UUID.randomUUID(),
                UUID.randomUUID(), MarketModule.AUCTION_HOUSE, "browse",
                "", "", "ending_soon", 0, 28,
                OptionalLong.empty(), OptionalLong.empty(), 1_000L);
        MarketPageSnapshot page = MarketPageProjector.auction(query,
                UUID.randomUUID(), book.snapshot(),
                new MarketProfileSavedData.Snapshot(List.of(), List.of(),
                        List.of(), List.of(), List.of()), List.of());
        String snbt = "{id:\"minecraft:enchanted_book\",Count:1b,tag:{StoredEnchantments:[{id:\"minecraft:mending\",lvl:1s}]}}";

        MarketPageSnapshot enriched = MarketPageEnricher.auction(page,
                book.snapshot(), id -> id.equals(sellerId)
                        ? "SellerName" : "Other", Map.of(listingId, snbt));

        MarketCardInsights insights = enriched.cards().get(0).insights();
        assertEquals("SellerName", insights.ownerName());
        assertEquals(1L, insights.activeListings());
        assertEquals(snbt, insights.itemStackSnbt());
        assertTrue(insights.itemHasData());
    }
}
