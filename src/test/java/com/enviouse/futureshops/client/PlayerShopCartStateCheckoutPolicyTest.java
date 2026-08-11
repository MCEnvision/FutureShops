package com.enviouse.futureshops.client;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerShopCartStateCheckoutPolicyTest {
    @BeforeEach
    void resetBeforeTest() {
        PlayerShopCartState.clear();
    }

    @AfterEach
    void resetAfterTest() {
        PlayerShopCartState.clear();
    }

    @Test
    void timedOutCheckoutAllowsEditsAndRetainsOriginalSubmission() {
        PlayerShopCartState.addToCart(
                new BlockPos(4, 70, 8), 3, 2,
                "minecraft:diamond", "Diamonds", 500L, 1,
                "MONEY", "", 0, "", "", false);
        List<PlayerShopCartState.CartEntry> original = PlayerShopCartState.getEntries();
        UUID requestId = UUID.randomUUID();

        assertEquals(CartResponsePolicy.BeginDecision.STARTED,
                PlayerShopCartState.beginCheckout(
                        requestId, original, "WALLET", 1_000L));
        assertEquals(CartResponsePolicy.TimeoutDecision.TIMED_OUT,
                PlayerShopCartState.expireCheckout(16_000L));

        PlayerShopCartState.setQuantity(0, 9);
        assertEquals(9, PlayerShopCartState.getEntries().get(0).quantity());
        assertEquals(CartResponsePolicy.BeginDecision.ALREADY_PENDING,
                PlayerShopCartState.beginCheckout(
                        UUID.randomUUID(),
                        PlayerShopCartState.getEntries(),
                        "WALLET", 16_001L));
        PlayerShopCartState.CheckoutSubmission retry =
                PlayerShopCartState.retryCheckout(16_001L).orElseThrow();
        assertEquals(requestId, retry.requestId());
        assertEquals(original, retry.entries());
        assertEquals("WALLET", retry.paymentSource());
        PlayerShopCartState.setQuantity(0, 10);
        assertEquals(9, PlayerShopCartState.getEntries().get(0).quantity());
    }

    @Test
    void timedOutCheckoutCanClearVisibleCartWithoutLosingOriginalSubmission() {
        PlayerShopCartState.addToCart(
                new BlockPos(4, 70, 8), 3, 2,
                "minecraft:diamond", "Diamonds", 500L, 1,
                "MONEY", "", 0, "", "", false);
        List<PlayerShopCartState.CartEntry> original =
                PlayerShopCartState.getEntries();
        UUID requestId = UUID.randomUUID();

        PlayerShopCartState.beginCheckout(
                requestId, original, "WALLET", 1_000L);
        PlayerShopCartState.expireCheckout(16_000L);
        PlayerShopCartState.clearEntries();

        assertTrue(PlayerShopCartState.getEntries().isEmpty());
        assertTrue(PlayerShopCartState.hasTrackedCheckout());
        PlayerShopCartState.CheckoutSubmission retry =
                PlayerShopCartState.retryCheckout(16_001L).orElseThrow();
        assertEquals(original, retry.entries());
        PlayerShopCartState.applyCheckoutResponse(
                requestId, 0, true);
        assertTrue(PlayerShopCartState.getEntries().isEmpty());
        assertFalse(PlayerShopCartState.hasTrackedCheckout());
    }

    @Test
    void activeCheckoutStillBlocksVisibleCartClear() {
        PlayerShopCartState.addToCart(
                new BlockPos(4, 70, 8), 3, 2,
                "minecraft:diamond", "Diamonds", 500L, 1,
                "MONEY", "", 0, "", "", false);
        List<PlayerShopCartState.CartEntry> original =
                PlayerShopCartState.getEntries();

        PlayerShopCartState.beginCheckout(
                UUID.randomUUID(), original, "WALLET", 1_000L);
        PlayerShopCartState.clearEntries();

        assertEquals(original, PlayerShopCartState.getEntries());
    }

    @Test
    void terminalCheckoutFailureUnlocksCartWithoutRemovingItems() {
        PlayerShopCartState.addToCart(
                new BlockPos(4, 70, 8), 3, 2,
                "minecraft:diamond", "Diamonds", 500L, 1,
                "MONEY", "", 0, "", "", false);
        List<PlayerShopCartState.CartEntry> original =
                PlayerShopCartState.getEntries();
        UUID requestId = UUID.randomUUID();

        PlayerShopCartState.beginCheckout(
                requestId, original, "WALLET",
                System.currentTimeMillis());
        PlayerShopCartState.applyCheckoutResponse(
                requestId, 0, false);

        assertFalse(PlayerShopCartState.hasTrackedCheckout());
        assertEquals(original, PlayerShopCartState.getEntries());
        PlayerShopCartState.setQuantity(0, 3);
        assertEquals(3, PlayerShopCartState.getEntries().get(0).quantity());
    }

    @Test
    void successfulLineResponseSubtractsOnlyAcknowledgedSnapshotQuantity() {
        BlockPos shopPos = new BlockPos(4, 70, 8);
        PlayerShopCartState.addToCart(
                shopPos, 3, 5,
                "minecraft:diamond", "Diamonds", 500L, 1,
                "MONEY", "", 0, "", "", false);
        PlayerShopCartState.CartEntry current =
                PlayerShopCartState.getEntries().get(0);
        PlayerShopCartState.CartEntry submitted = new PlayerShopCartState.CartEntry(
                current.shopPos(), current.listingIndex(), 2,
                current.itemId(), current.shopName(), current.unitPriceMinor(),
                current.baseQuantity(), current.tradeMode(), current.barterItemId(),
                current.barterItemCount(), current.barterNbtJson(), current.nbtJson(),
                current.chosenPayment(), current.nbtAware());
        UUID requestId = UUID.randomUUID();
        long nowMillis = System.currentTimeMillis();

        PlayerShopCartState.beginCheckout(
                requestId, List.of(submitted), "WALLET", nowMillis);
        PlayerShopCartState.applyCheckoutResponse(requestId, 0, true);

        assertEquals(3, PlayerShopCartState.getEntries().get(0).quantity());
    }
}
