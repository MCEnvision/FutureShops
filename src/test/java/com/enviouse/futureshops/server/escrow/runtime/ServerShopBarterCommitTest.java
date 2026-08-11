package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.MinecraftTestBootstrap;
import com.enviouse.futureshops.money.ItemStackSnapshotCodec;
import com.enviouse.futureshops.server.escrow.item.ExactItemClaimPayload;
import com.enviouse.futureshops.server.escrow.model.EscrowOperation;
import com.enviouse.futureshops.server.escrow.model.EscrowState;
import com.enviouse.futureshops.server.escrow.stock.StockMutationType;
import com.enviouse.futureshops.server.escrow.stock.StockReservationDirection;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerShopBarterCommitTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void exactIngredientsBundlesClaimsAndOutboundStockAreConserved() {
        ServerShopBarterCommit commit =
                ServerShopBarterTestFixtures.commit();

        assertEquals(6, commit.totalIngredientQuantity());
        assertEquals(8, commit.totalOutputQuantity());
        assertEquals(2, commit.ingredients().size());
        assertEquals(2, commit.outputs().size());
        assertEquals(3, commit.outputClaims().size());
        assertEquals(EscrowOperation.SERVER_SHOP_BARTER,
                commit.completedTransaction().operation());
        assertEquals(EscrowState.COMPLETED,
                commit.completedTransaction().state());
        assertEquals(2,
                commit.stockReservation().reservations().size());
        assertEquals(2, commit.stockCommit().reservations().size());
        assertEquals(StockMutationType.COMMIT_BATCH,
                commit.stockCommit().operation());
        assertTrue(commit.stockReservation().reservations().stream()
                .allMatch(value -> value.direction()
                        == StockReservationDirection.OUTBOUND));
        assertEquals(ServerShopBarterTestFixtures.QUOTED_AT,
                commit.stockReservation().appliedAt());
        assertEquals(ServerShopBarterTestFixtures.APPLIED_AT,
                commit.stockCommit().appliedAt());
        ExactItemClaimPayload first = commit.outputs().get(0)
                .portions().get(0);
        ItemStack reward = first.resolve().resolvedStack().orElseThrow();
        assertEquals("bundle",
                reward.getTag().getString("reward"));
        ServerShopBarterConservationValidator.validate(commit);
    }

    @Test
    void deterministicIdentifiersAndCanonicalEvidenceRepeatExactly() {
        ServerShopBarterCommit first =
                ServerShopBarterTestFixtures.commit();
        ServerShopBarterCommit second =
                ServerShopBarterTestFixtures.commit();

        assertEquals(first, second);
        assertEquals(first.wireFingerprint(),
                second.wireFingerprint());
        assertEquals(first.quoteFingerprint(),
                second.quoteFingerprint());
        assertEquals(first.outputClaims().stream()
                        .map(value -> value.claimId()).toList(),
                second.outputClaims().stream()
                        .map(value -> value.claimId()).toList());
        assertNotEquals(ServerShopBarterCommit.stockReserveRequestId(
                        first.requestId()),
                ServerShopBarterCommit.stockCommitRequestId(
                        first.requestId()));
    }

    @Test
    void duplicateIngredientIdentifiersAndExactIdentitiesFailClosed() {
        List<ServerShopBarterCommit.Ingredient> duplicateIdentity =
                List.of(new ServerShopBarterCommit.Ingredient(0,
                                "first", "minecraft:emerald", 1,
                                ServerShopBarterTestFixtures
                                        .emeraldTemplate()),
                        new ServerShopBarterCommit.Ingredient(1,
                                "second", "minecraft:emerald", 2,
                                ServerShopBarterTestFixtures
                                        .emeraldTemplate()));
        List<ServerShopBarterCommit.Ingredient> duplicateId =
                List.of(new ServerShopBarterCommit.Ingredient(0,
                                "same", "minecraft:emerald", 1,
                                ServerShopBarterTestFixtures
                                        .emeraldTemplate()),
                        new ServerShopBarterCommit.Ingredient(1,
                                "same", "minecraft:diamond", 1,
                                ServerShopBarterTestFixtures
                                        .diamondTemplate()));

        assertThrows(IllegalArgumentException.class, () ->
                requestWithIngredients(duplicateIdentity, 1));
        assertThrows(IllegalArgumentException.class, () ->
                requestWithIngredients(duplicateId, 1));
    }

    @Test
    void duplicateOutputListingsAndMissingBundleUnitsFailClosed() {
        ServerShopBarterService.PreparedRequest valid =
                ServerShopBarterTestFixtures.request();
        ServerShopBarterCommit.OutputLine first = valid.outputs().get(0);
        ServerShopBarterCommit.OutputLine second = valid.outputs().get(1);
        ServerShopBarterCommit.OutputLine duplicate =
                new ServerShopBarterCommit.OutputLine(1,
                        first.listingId(), second.itemId(),
                        second.quantityPerTrade(),
                        second.expectedStockRevision(),
                        second.portions());
        List<ServerShopBarterCommit.OutputLine> partial =
                new ArrayList<>(valid.outputs());
        ExactItemClaimPayload only = ExactItemClaimPayload.capture(
                valid.identity().requestId(),
                ServerShopBarterCommit.outputSourceKey(
                        valid.identity().requestId(), 0),
                0, 1, ServerShopBarterTestFixtures.rewardApple(5));
        partial.set(0, new ServerShopBarterCommit.OutputLine(0,
                first.listingId(), first.itemId(),
                first.quantityPerTrade(),
                first.expectedStockRevision(), List.of(only)));

        assertThrows(IllegalArgumentException.class, () ->
                new ServerShopBarterService.PreparedRequest(
                        valid.identity(), valid.quoteRevision(),
                        valid.recipeRevision(), valid.quoteCreatedAt(),
                        valid.ingredients(), List.of(first, duplicate),
                        valid.shopReference()));
        assertThrows(IllegalArgumentException.class, () ->
                new ServerShopBarterService.PreparedRequest(
                        valid.identity(), valid.quoteRevision(),
                        valid.recipeRevision(), valid.quoteCreatedAt(),
                        valid.ingredients(), partial,
                        valid.shopReference()));
    }

    @Test
    void multipliedIngredientOverflowFailsBeforeCustody() {
        List<ServerShopBarterCommit.Ingredient> ingredients = List.of(
                new ServerShopBarterCommit.Ingredient(0, "overflow",
                        "minecraft:emerald", Integer.MAX_VALUE,
                        ServerShopBarterTestFixtures.emeraldTemplate()));

        assertThrows(ArithmeticException.class, () ->
                requestWithIngredients(ingredients, 2));
    }

    @Test
    void changedIngredientNbtCannotReuseExactCustodyEvidence() {
        ServerShopBarterCommit valid =
                ServerShopBarterTestFixtures.commit();
        ItemStack changed = ServerShopBarterTestFixtures
                .taggedEmerald(1);
        changed.getOrCreateTag().putInt("quality", 99);
        List<ServerShopBarterCommit.Ingredient> changedIngredients =
                new ArrayList<>(valid.ingredients());
        changedIngredients.set(0,
                new ServerShopBarterCommit.Ingredient(0,
                        "emerald.exact", "minecraft:emerald", 2,
                        ItemStackSnapshotCodec.encode(changed)));

        assertThrows(IllegalArgumentException.class, () ->
                new ServerShopBarterCommit(valid.requestId(),
                        valid.playerId(), valid.shopId(), valid.recipeId(),
                        valid.multiplier(), valid.quoteRevision(),
                        valid.recipeRevision(), valid.quoteCreatedAt(),
                        changedIngredients, valid.outputs(),
                        valid.ingredientCustodyReceipt(),
                        valid.completedTransaction(),
                        valid.stockReservation(), valid.stockCommit(),
                        valid.outputClaims()));
        assertArrayEquals(
                ServerShopBarterTestFixtures.emeraldTemplate(),
                valid.ingredients().get(0).exactItemTemplate());
    }

    private static ServerShopBarterService.PreparedRequest
    requestWithIngredients(
            List<ServerShopBarterCommit.Ingredient> ingredients,
            int multiplier
    ) {
        UUID requestId = UUID.fromString(
                "33000000-0000-0000-0000-000000000003");
        return new ServerShopBarterService.PreparedRequest(
                new ServerShopBarterService.Identity(requestId,
                        ServerShopBarterTestFixtures.PLAYER_ID,
                        "default", "test.recipe", multiplier),
                1L, 1L, ServerShopBarterTestFixtures.QUOTED_AT,
                ingredients,
                ServerShopBarterTestFixtures.outputs(
                        requestId, multiplier),
                ServerShopBarterTestFixtures.shopReference());
    }
}
