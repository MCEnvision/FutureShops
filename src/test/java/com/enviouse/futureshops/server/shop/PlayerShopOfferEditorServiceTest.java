package com.enviouse.futureshops.server.shop;

import com.enviouse.futureshops.MinecraftTestBootstrap;
import com.enviouse.futureshops.catalog.AdminShopOfferConfigWriter;
import com.enviouse.futureshops.catalog.offer.AcquireOfferOption;
import com.enviouse.futureshops.catalog.offer.OfferItemComponent;
import com.enviouse.futureshops.catalog.offer.OfferLimitPolicy;
import com.enviouse.futureshops.catalog.offer.OfferSchedule;
import com.enviouse.futureshops.catalog.offer.OfferStockPolicy;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferListing;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferRevision;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerShopOfferEditorServiceTest {
    @BeforeAll
    static void initializeMinecraftRegistries() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void validUpdateRecomputesRevision() {
        ServerShopOfferListing current = canonicalOffer(
                "player_offer", 100L);
        ServerShopOfferListing candidate = offer(
                "player_offer", 0L, 250L);

        PlayerShopOfferEditorService.MutationValidation result =
                PlayerShopOfferEditorService.validateMutation(
                        current, current.revision(),
                        current.listingId(), candidate);

        assertTrue(result.success());
        ServerShopOfferListing saved =
                result.snapshot().orElseThrow();
        assertEquals(250L,
                saved.acquireOptions().get(0)
                        .moneyCostMinorUnits());
        assertEquals(ServerShopOfferRevision.compute(candidate),
                saved.revision());
    }

    @Test
    void staleUpdateReturnsAuthoritativeSnapshot() {
        ServerShopOfferListing current = canonicalOffer(
                "player_offer", 100L);
        ServerShopOfferListing candidate = offer(
                "player_offer", 0L, 250L);

        PlayerShopOfferEditorService.MutationValidation result =
                PlayerShopOfferEditorService.validateMutation(
                        current, current.revision() + 1L,
                        current.listingId(), candidate);

        assertFalse(result.success());
        assertEquals(AdminShopOfferConfigWriter.Status.STALE,
                result.status());
        assertEquals(current, result.snapshot().orElseThrow());
        assertTrue(result.issues().stream().anyMatch(issue ->
                issue.code().equals("offer.player_shop.stale")));
    }

    @Test
    void stableListingIdentityCannotChange() {
        ServerShopOfferListing current = canonicalOffer(
                "player_offer", 100L);

        PlayerShopOfferEditorService.MutationValidation result =
                PlayerShopOfferEditorService.validateMutation(
                        current, current.revision(),
                        current.listingId(),
                        offer("different_offer", 0L, 100L));

        assertFalse(result.success());
        assertEquals(AdminShopOfferConfigWriter.Status.CONFLICT,
                result.status());
        assertEquals(current, result.snapshot().orElseThrow());
    }

    @Test
    void invalidPartialOfferIsRejectedWithoutReplacingSnapshot() {
        ServerShopOfferListing current = canonicalOffer(
                "player_offer", 100L);
        AcquireOfferOption invalid =
                new AcquireOfferOption(
                        "free", "Free", true, true, 1L,
                        List.of(), 1, OfferLimitPolicy.defaults(),
                        OfferSchedule.always(), "");
        ServerShopOfferListing candidate =
                new ServerShopOfferListing(
                        "player_offer", 0L, "Player Offer", "",
                        "all", "minecraft:diamond", "", true,
                        0L, "", List.of(new OfferItemComponent(
                        "output", "minecraft:diamond", 1, "")),
                        List.of(invalid), List.of(),
                        OfferStockPolicy.unlimited(),
                        OfferLimitPolicy.defaults(),
                        OfferSchedule.always(), List.of());

        PlayerShopOfferEditorService.MutationValidation result =
                PlayerShopOfferEditorService.validateMutation(
                        current, current.revision(),
                        current.listingId(), candidate);

        assertFalse(result.success());
        assertEquals(AdminShopOfferConfigWriter.Status.INVALID,
                result.status());
        assertEquals(current, result.snapshot().orElseThrow());
    }

    @Test
    void idempotentStaleRetrySucceeds() {
        ServerShopOfferListing current = canonicalOffer(
                "player_offer", 100L);

        PlayerShopOfferEditorService.MutationValidation result =
                PlayerShopOfferEditorService.validateMutation(
                        current, current.revision() + 1L,
                        current.listingId(), current);

        assertTrue(result.success());
        assertEquals(current, result.snapshot().orElseThrow());
    }

    private static ServerShopOfferListing canonicalOffer(
            String listingId,
            long money
    ) {
        ServerShopOfferListing offer =
                offer(listingId, 0L, money);
        return offer.withRevision(
                ServerShopOfferRevision.compute(offer));
    }

    private static ServerShopOfferListing offer(
            String listingId,
            long revision,
            long money
    ) {
        return new ServerShopOfferListing(
                listingId, revision, "Player Offer", "",
                "all", "minecraft:diamond", "", true,
                0L, "", List.of(new OfferItemComponent(
                "output", "minecraft:diamond", 1, "")),
                List.of(AcquireOfferOption.money("money", money)),
                List.of(), OfferStockPolicy.unlimited(),
                OfferLimitPolicy.defaults(),
                OfferSchedule.always(), List.of());
    }
}
