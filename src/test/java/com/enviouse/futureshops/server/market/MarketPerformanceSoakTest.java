package com.enviouse.futureshops.server.market;

import com.enviouse.futureshops.server.market.auction.AuctionHouseBook;
import com.enviouse.futureshops.server.market.auction.AuctionItemLot;
import com.enviouse.futureshops.server.market.auction.AuctionListingType;
import com.enviouse.futureshops.server.market.auction.AuctionRuleSnapshot;
import com.enviouse.futureshops.server.market.auction.AuctionTimeBasis;
import com.enviouse.futureshops.server.market.auction.CreateAuctionCommand;
import com.enviouse.futureshops.server.market.bazaar.BazaarExecutionPricePolicy;
import com.enviouse.futureshops.server.market.bazaar.BazaarOrderBook;
import com.enviouse.futureshops.server.market.bazaar.BazaarOrderSide;
import com.enviouse.futureshops.server.market.bazaar.BazaarOrderType;
import com.enviouse.futureshops.server.market.bazaar.BazaarProduct;
import com.enviouse.futureshops.server.market.bazaar.BazaarProductStatus;
import com.enviouse.futureshops.server.market.bazaar.BazaarRuleSnapshot;
import com.enviouse.futureshops.server.market.bazaar.BazaarSelfTradePolicy;
import com.enviouse.futureshops.server.market.bazaar.BazaarTimeInForce;
import com.enviouse.futureshops.server.market.bazaar.CreateBazaarOrderCommand;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketPerformanceSoakTest {
    @Test
    void twoThousandAuctionsRemainRestorable() {
        AuctionHouseBook book = new AuctionHouseBook();
        AuctionRuleSnapshot rules = new AuctionRuleSnapshot(
                0L, 0, 1L, 0, false, 60_000L, 60_000L,
                0L, 0, true, AuctionTimeBasis.REAL_TIME, true, 1L);
        for (int index = 1; index <= 2_000; index++) {
            long value = index;
            CreateAuctionCommand command = new CreateAuctionCommand(
                    id(1L, value), id(2L, value), id(3L, value),
                    id(4L, value), new AuctionItemLot(id(5L, value),
                    "minecraft:emerald", "01".repeat(32), 1, 1,
                    "materials", "emerald minecraft"),
                    AuctionListingType.BUY_NOW, 0L, 100L, rules,
                    value, 0L);
            assertTrue(book.create(command).newlyCommitted());
        }
        assertEquals(2_000, book.snapshot().listings().size());
        assertEquals(book.snapshot(),
                new AuctionHouseBook(book.snapshot()).snapshot());
    }

    @Test
    void tenThousandBazaarOrdersRemainIndexedAndRestorable() {
        BazaarRuleSnapshot rules = new BazaarRuleSnapshot(
                0, 0, 1, 1_000_000L, 1, 1,
                1_000_000L, BazaarSelfTradePolicy.CANCEL_TAKER,
                BazaarExecutionPricePolicy.MAKER, false, 10_000,
                0L, 1L);
        BazaarOrderBook book = new BazaarOrderBook();
        book.registerProduct(new BazaarProduct("emerald", 1L,
                "minecraft:emerald", "", "materials", 1, 1L,
                1L, 1_000_000L, 1, BazaarProductStatus.ACTIVE));
        book.setEffectiveRules(rules);
        for (int index = 1; index <= 10_000; index++) {
            long value = index;
            CreateBazaarOrderCommand command =
                    new CreateBazaarOrderCommand(id(10L, value),
                            id(11L, value), id(12L, value),
                            id(13L, value), Optional.of(id(14L, value)),
                            Optional.empty(), "emerald", 1L,
                            BazaarOrderSide.BUY, BazaarOrderType.LIMIT,
                            BazaarTimeInForce.GOOD_UNTIL_CANCELLED,
                            100L, 1, value, 0L, rules);
            assertTrue(book.create(command).newlyCommitted());
        }
        assertEquals(10_000, book.snapshot().orders().size());
        assertEquals(book.snapshot(),
                BazaarOrderBook.restore(book.snapshot()).snapshot());
    }

    private static UUID id(long domain, long value) {
        return new UUID(domain, value);
    }
}
