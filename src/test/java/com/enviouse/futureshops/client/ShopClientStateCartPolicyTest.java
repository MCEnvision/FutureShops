package com.enviouse.futureshops.client;

import com.enviouse.futureshops.data.CatalogItem;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopClientStateCartPolicyTest {
    @BeforeEach
    void resetBeforeTest() {
        ShopClientState.reset();
    }

    @AfterEach
    void resetAfterTest() {
        ShopClientState.reset();
    }

    @Test
    void addToCartPublishesFeedbackForTheQuantityActuallyAdded() {
        apply(item("diamond", 500L, 5));

        ShopClientState.addToCart("diamond", 9);

        assertEquals(5, ShopClientState.getCartTotalQuantity());
        ShopClientState.ShopStatus status = ShopClientState.getStatus();
        assertTrue(status.success());
        TranslatableContents contents = assertInstanceOf(
                TranslatableContents.class, status.message().getContents());
        assertEquals("gui.futureshops.status.cart.added", contents.getKey());
        assertArrayEquals(new Object[]{5, "Diamond"}, contents.getArgs());

        ShopClientState.clearStatus();
        ShopClientState.addToCart("diamond", 1);
        assertNull(ShopClientState.getStatus());
    }

    @Test
    void catalogRefreshRetainsValidLinesAndExactQuantities() {
        apply(item("diamond", 500L, 10));
        ShopClientState.addToCart("diamond", 6);

        apply(item("diamond", 500L, 8));
        assertEquals(List.of(new ShopClientState.CartEntry("diamond", 6)),
                ShopClientState.getCartEntries());

        apply(item("diamond", 500L, 3));
        assertEquals(List.of(new ShopClientState.CartEntry("diamond", 6)),
                ShopClientState.getCartEntries());
    }

    @Test
    void catalogRefreshDropsRemovedOrNonPurchasableLines() {
        apply(item("diamond", 500L, 10));
        ShopClientState.addToCart("diamond", 2);

        apply(item("diamond", 0L, 10));
        assertEquals(0, ShopClientState.getCartLineCount());

        apply(item("diamond", 500L, 10));
        ShopClientState.addToCart("diamond", 2);
        apply();
        assertEquals(0, ShopClientState.getCartLineCount());
    }

    @Test
    void timedOutCheckoutAllowsEditsAndRetainsOriginalSubmission() {
        apply(item("diamond", 500L, 10));
        ShopClientState.addToCart("diamond", 2);
        List<ShopClientState.CartEntry> original = ShopClientState.getCartEntries();
        UUID requestId = UUID.randomUUID();

        assertEquals(CartResponsePolicy.BeginDecision.STARTED,
                ShopClientState.beginCartCheckout(
                        requestId, original, "INVENTORY", 1_000L));
        assertEquals(CartResponsePolicy.TimeoutDecision.TIMED_OUT,
                ShopClientState.expireCartCheckout(16_000L));

        ShopClientState.addToCart("diamond", 1);
        assertEquals(List.of(new ShopClientState.CartEntry("diamond", 3)),
                ShopClientState.getCartEntries());
        assertEquals(CartResponsePolicy.BeginDecision.ALREADY_PENDING,
                ShopClientState.beginCartCheckout(
                        UUID.randomUUID(),
                        ShopClientState.getCartEntries(),
                        "WALLET", 16_001L));
        ShopClientState.CartCheckoutSubmission retry =
                ShopClientState.retryCartCheckout(16_001L).orElseThrow();
        assertEquals(requestId, retry.requestId());
        assertEquals("default", retry.shopId());
        assertEquals(original, retry.entries());
        assertEquals("INVENTORY", retry.paymentSource());
        ShopClientState.addToCart("diamond", 1);
        assertEquals(3, ShopClientState.getCartTotalQuantity());
    }

    @Test
    void timedOutCheckoutCanClearVisibleCartWithoutLosingOriginalSubmission() {
        apply(item("diamond", 500L, 10));
        ShopClientState.addToCart("diamond", 2);
        List<ShopClientState.CartEntry> original =
                ShopClientState.getCartEntries();
        UUID requestId = UUID.randomUUID();

        ShopClientState.beginCartCheckout(
                requestId, original, "WALLET", 1_000L);
        ShopClientState.expireCartCheckout(16_000L);
        ShopClientState.clearCartContents();

        assertTrue(ShopClientState.getCartEntries().isEmpty());
        assertTrue(ShopClientState.hasTrackedCartCheckout());
        ShopClientState.CartCheckoutSubmission retry =
                ShopClientState.retryCartCheckout(16_001L).orElseThrow();
        assertEquals(original, retry.entries());
        ShopClientState.applyCartCheckoutResponse(requestId, true);
        assertTrue(ShopClientState.getCartEntries().isEmpty());
        assertFalse(ShopClientState.hasTrackedCartCheckout());
    }

    @Test
    void activeCheckoutStillBlocksVisibleCartClear() {
        apply(item("diamond", 500L, 10));
        ShopClientState.addToCart("diamond", 2);
        List<ShopClientState.CartEntry> original =
                ShopClientState.getCartEntries();

        ShopClientState.beginCartCheckout(
                UUID.randomUUID(), original, "WALLET", 1_000L);
        ShopClientState.clearCartContents();

        assertEquals(original, ShopClientState.getCartEntries());
    }

    @Test
    void terminalCheckoutFailureUnlocksCartWithoutRemovingItems() {
        apply(item("diamond", 500L, 10));
        ShopClientState.addToCart("diamond", 2);
        List<ShopClientState.CartEntry> original =
                ShopClientState.getCartEntries();
        UUID requestId = UUID.randomUUID();

        ShopClientState.beginCartCheckout(
                requestId, original, "WALLET",
                System.currentTimeMillis());
        ShopClientState.applyCartCheckoutResponse(
                requestId, false, true);

        assertFalse(ShopClientState.hasTrackedCartCheckout());
        assertEquals(original, ShopClientState.getCartEntries());
        ShopClientState.addToCart("diamond", 1);
        assertEquals(3, ShopClientState.getCartTotalQuantity());
    }

    @Test
    void successfulResponseSubtractsOnlyAcknowledgedSnapshotQuantity() {
        apply(item("diamond", 500L, 10));
        ShopClientState.addToCart("diamond", 5);
        UUID requestId = UUID.randomUUID();
        long nowMillis = System.currentTimeMillis();

        ShopClientState.beginCartCheckout(
                requestId,
                List.of(new ShopClientState.CartEntry("diamond", 2)),
                "WALLET",
                nowMillis);
        ShopClientState.applyCartCheckoutResponse(requestId, true);

        assertEquals(List.of(new ShopClientState.CartEntry("diamond", 3)),
                ShopClientState.getCartEntries());
    }

    private static void apply(CatalogItem... items) {
        ShopClientState.applyShopData(
                "default", 0L, "Coins", 2,
                List.of(), List.of(items), List.of(), List.of(),
                true, List.of(), false);
    }

    private static CatalogItem item(String listingId, long buyPrice,
                                    int stock) {
        return new CatalogItem(
                listingId,
                "minecraft:diamond",
                "Diamond",
                buyPrice,
                0L,
                stock,
                false,
                false,
                "materials",
                false,
                0L,
                false,
                "",
                stock);
    }
}
