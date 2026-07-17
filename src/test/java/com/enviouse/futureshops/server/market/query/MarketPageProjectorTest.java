package com.enviouse.futureshops.server.market.query;

import com.enviouse.futureshops.client.market.MarketModule;
import com.enviouse.futureshops.client.market.MarketModuleAvailability;
import com.enviouse.futureshops.server.market.MarketModuleAccessPolicy;
import com.enviouse.futureshops.server.market.bazaar.BazaarExecutionPricePolicy;
import com.enviouse.futureshops.server.market.bazaar.BazaarOrderBook;
import com.enviouse.futureshops.server.market.bazaar.BazaarProduct;
import com.enviouse.futureshops.server.market.bazaar.BazaarProductStatus;
import com.enviouse.futureshops.server.market.bazaar.BazaarRuleSnapshot;
import com.enviouse.futureshops.server.market.bazaar.BazaarSelfTradePolicy;
import com.enviouse.futureshops.server.market.profile.MarketProfileSavedData;
import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.claim.OpenClaimPage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketPageProjectorTest {
    @Test
    void bazaarProductsExposeAnalyticsAndFavoriteState() {
        BazaarOrderBook book = new BazaarOrderBook();
        book.registerProduct(product("iron",
                "minecraft:iron_ingot", "metals"));
        book.registerProduct(product("gold",
                "minecraft:gold_ingot", "metals"));
        book.setEffectiveRules(rules());
        UUID player = UUID.randomUUID();
        MarketProfileSavedData.Snapshot profile =
                new MarketProfileSavedData.Snapshot(List.of(),
                        List.of(new MarketProfileSavedData.ProductKey(
                                "iron", 1L)), List.of(), List.of(),
                        List.of());
        MarketPageQuery query = new MarketPageQuery(UUID.randomUUID(),
                UUID.randomUUID(), MarketModule.BAZAAR, "watched", "",
                "", "name", 0, 28, OptionalLong.empty(),
                OptionalLong.empty(), 1000L);

        MarketPageSnapshot page = MarketPageProjector.bazaar(query,
                player, book.snapshot(), profile, List.of());

        assertEquals(1, page.totalResults());
        assertEquals("iron@1", page.cards().get(0).identity());
        assertTrue(page.cards().get(0).watched());
        assertEquals(List.of("metals"), page.categories());
    }

    @Test
    void internalEscrowMoneyNeverAppearsOnMarketClaimPages() {
        UUID player = UUID.randomUUID();
        MarketPageQuery query = new MarketPageQuery(UUID.randomUUID(),
                UUID.randomUUID(), MarketModule.BAZAAR, "claims", "",
                "", "name", 0, 28, OptionalLong.empty(),
                OptionalLong.empty(), 1000L);
        MarketProfileSavedData.Snapshot profile =
                new MarketProfileSavedData.Snapshot(List.of(), List.of(),
                        List.of(), List.of(), List.of());
        EscrowClaim internal = new EscrowClaim(UUID.randomUUID(),
                UUID.randomUUID(), player, "bazaar.internal.cash",
                ClaimKind.INTERNAL_ESCROW_MONEY, 100L, 100L,
                new byte[0], ClaimStatus.PENDING, "Internal cash",
                Instant.EPOCH, Instant.EPOCH);
        EscrowClaim publicClaim = new EscrowClaim(UUID.randomUUID(),
                UUID.randomUUID(), player, "bazaar.public.cash",
                ClaimKind.MONEY, 100L, 100L, new byte[0],
                ClaimStatus.PENDING, "Public cash", Instant.EPOCH,
                Instant.EPOCH);

        MarketPageSnapshot page = MarketPageProjector.bazaar(query,
                player, new BazaarOrderBook().snapshot(), profile,
                List.of(internal, publicClaim));

        assertEquals(1, page.totalResults());
        assertEquals(publicClaim.claimId().toString(),
                page.cards().get(0).identity());
    }

    @Test
    void refundPayloadSelectsMoneyOrItemPresentation() {
        UUID player = UUID.randomUUID();
        MarketPageQuery query = new MarketPageQuery(UUID.randomUUID(),
                UUID.randomUUID(), MarketModule.BAZAAR, "claims", "",
                "", "name", 0, 28, OptionalLong.empty(),
                OptionalLong.empty(), 1000L);
        MarketProfileSavedData.Snapshot profile =
                new MarketProfileSavedData.Snapshot(List.of(), List.of(),
                        List.of(), List.of(), List.of());
        EscrowClaim moneyRefund = new EscrowClaim(UUID.randomUUID(),
                UUID.randomUUID(), player, "bazaar.money.refund",
                ClaimKind.REFUND, 75L, 75L, new byte[0],
                ClaimStatus.PENDING, "Money refund", Instant.EPOCH,
                Instant.EPOCH);
        EscrowClaim itemRefund = new EscrowClaim(UUID.randomUUID(),
                UUID.randomUUID(), player, "bazaar.item.refund",
                ClaimKind.REFUND, 3L, 3L, new byte[]{1},
                ClaimStatus.PENDING, "Item refund", Instant.EPOCH,
                Instant.EPOCH);

        MarketPageSnapshot page = MarketPageProjector.bazaar(query,
                player, new BazaarOrderBook().snapshot(), profile,
                List.of(moneyRefund, itemRefund));
        MarketPageCard moneyCard = page.cards().stream()
                .filter(value -> value.identity().equals(
                        moneyRefund.claimId().toString()))
                .findFirst().orElseThrow();
        MarketPageCard itemCard = page.cards().stream()
                .filter(value -> value.identity().equals(
                        itemRefund.claimId().toString()))
                .findFirst().orElseThrow();

        assertEquals(2, page.totalResults());
        assertEquals(75L, moneyCard.primaryMinor());
        assertEquals(0L, moneyCard.quantity());
        assertTrue(moneyCard.primaryAction());
        assertEquals(0L, itemCard.primaryMinor());
        assertEquals(3L, itemCard.quantity());
        assertTrue(itemCard.primaryAction());
        assertEquals(75L, page.aggregatePrimaryMinor());
        assertEquals(3L, page.aggregateQuantity());
    }

    @Test
    void alreadyPagedClaimsKeepPageTwoAndExactLargeTotals() {
        UUID player = UUID.randomUUID();
        MarketPageQuery query = new MarketPageQuery(UUID.randomUUID(),
                UUID.randomUUID(), MarketModule.BAZAAR, "claims", "",
                "", "name", 2, 100, OptionalLong.empty(),
                OptionalLong.empty(), 1000L);
        MarketProfileSavedData.Snapshot profile =
                new MarketProfileSavedData.Snapshot(List.of(), List.of(),
                        List.of(), List.of(), List.of());
        List<EscrowClaim> claims = new ArrayList<>();
        for (long index = 201L; index <= 271L; index++) {
            claims.add(new EscrowClaim(new UUID(0L, index),
                    UUID.randomUUID(), player,
                    "bazaar.claim." + index, ClaimKind.MONEY,
                    index, index, new byte[0], index == 271L
                    ? ClaimStatus.QUARANTINED : ClaimStatus.PENDING,
                    "Paged claim " + index, Instant.EPOCH,
                    Instant.EPOCH));
        }
        OpenClaimPage claimPage = new OpenClaimPage(player,
                "bazaar.", 2, 100, 271, 3, claims);

        MarketPageSnapshot page = MarketPageProjector.bazaar(query,
                player, new BazaarOrderBook().snapshot(), profile,
                claimPage);

        assertEquals(271, page.totalResults());
        assertEquals(3, page.pageCount());
        assertEquals(2, page.pageIndex());
        assertEquals(71, page.cards().size());
        assertEquals(new UUID(0L, 201L).toString(),
                page.cards().get(0).identity());
        assertEquals(new UUID(0L, 271L).toString(),
                page.cards().get(70).identity());
        assertTrue(page.cards().get(0).primaryAction());
        assertEquals(ClaimStatus.QUARANTINED.name(),
                page.cards().get(70).state());
        assertFalse(page.cards().get(70).primaryAction());

        MarketPageSnapshot claimsOnly = MarketModuleAccessPolicy
                .applyPageActions(page,
                        MarketModuleAvailability.CLAIMS_ONLY);
        assertEquals(271, claimsOnly.totalResults());
        assertEquals(71, claimsOnly.cards().size());
        assertTrue(claimsOnly.cards().get(0).primaryAction());
        assertFalse(claimsOnly.cards().get(70).primaryAction());
    }

    @Test
    void pagedClaimProjectionRejectsMismatchedContracts() {
        UUID player = UUID.randomUUID();
        MarketPageQuery query = new MarketPageQuery(UUID.randomUUID(),
                UUID.randomUUID(), MarketModule.BAZAAR, "claims", "",
                "", "name", 2, 100, OptionalLong.empty(),
                OptionalLong.empty(), 1000L);
        MarketProfileSavedData.Snapshot profile =
                new MarketProfileSavedData.Snapshot(List.of(), List.of(),
                        List.of(), List.of(), List.of());
        BazaarOrderBook book = new BazaarOrderBook();

        assertThrows(IllegalArgumentException.class,
                () -> MarketPageProjector.bazaar(query, player,
                        book.snapshot(), profile, new OpenClaimPage(
                                UUID.randomUUID(), "bazaar.", 2,
                                100, 0, 0, List.of())));
        assertThrows(IllegalArgumentException.class,
                () -> MarketPageProjector.bazaar(query, player,
                        book.snapshot(), profile, new OpenClaimPage(
                                player, "auction.", 2, 100,
                                0, 0, List.of())));
        assertThrows(IllegalArgumentException.class,
                () -> MarketPageProjector.bazaar(query, player,
                        book.snapshot(), profile, new OpenClaimPage(
                                player, "bazaar.", 1, 100,
                                0, 0, List.of())));
        assertThrows(IllegalArgumentException.class,
                () -> MarketPageProjector.bazaar(query, player,
                        book.snapshot(), profile, new OpenClaimPage(
                                player, "bazaar.", 2, 99,
                                0, 0, List.of())));
    }

    private static BazaarProduct product(
            String id,
            String registry,
            String category
    ) {
        return new BazaarProduct(id, 1L, registry, "", category,
                1, 1L, 1L, 1_000_000L, 1_000_000,
                BazaarProductStatus.ACTIVE);
    }

    private static BazaarRuleSnapshot rules() {
        return new BazaarRuleSnapshot(0, 0, 1_000_000,
                1_000_000_000L, 32, 8, 10_000_000_000L,
                BazaarSelfTradePolicy.CANCEL_TAKER,
                BazaarExecutionPricePolicy.MAKER, false, 5000,
                0L, 1L);
    }
}
