package com.enviouse.futureshops.client;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    void timedOutCheckoutRetainsOriginalLinesTokensAndPaymentSource() {
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
        assertEquals(original, PlayerShopCartState.getEntries());
        PlayerShopCartState.CheckoutSubmission retry =
                PlayerShopCartState.retryCheckout(16_001L).orElseThrow();
        assertEquals(requestId, retry.requestId());
        assertEquals(original, retry.entries());
        assertEquals("WALLET", retry.paymentSource());
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
