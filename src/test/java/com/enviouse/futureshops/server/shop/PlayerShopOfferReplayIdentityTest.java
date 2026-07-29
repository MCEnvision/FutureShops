package com.enviouse.futureshops.server.shop;

import com.enviouse.futureshops.catalog.offer.OfferAction;
import com.enviouse.futureshops.catalog.offer.OfferLimitPolicy;
import com.enviouse.futureshops.money.PaymentSource;
import com.enviouse.futureshops.network.packets.C2SPlayerShopOfferPacket;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopEscrowIntent;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopIdentity;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopItemMatchMode;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopListingSnapshot;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopOfferSelection;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopOperation;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopPaymentSource;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopTradeMethod;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerShopOfferReplayIdentityTest {
    private static final UUID REQUEST = UUID.fromString(
            "12345678-1234-5678-1234-567812345678");

    @Test
    void replayBindsQuantityPaymentOptionActionAndRevision() {
        PlayerShopEscrowIntent intent = intent(identity(
                UUID.fromString(
                        "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")));
        C2SPlayerShopOfferPacket exact = packet(
                "free", OfferAction.ACQUIRE_FROM_SHOP,
                2, 9L, Optional.empty());

        assertTrue(PlayerShopEscrowTransactionService
                .replayRequestMatches(exact, intent));
        assertFalse(PlayerShopEscrowTransactionService
                .replayRequestMatches(packet(
                        "free", OfferAction.ACQUIRE_FROM_SHOP,
                        3, 9L, Optional.empty()), intent));
        assertFalse(PlayerShopEscrowTransactionService
                .replayRequestMatches(packet(
                        "other", OfferAction.ACQUIRE_FROM_SHOP,
                        2, 9L, Optional.empty()), intent));
        assertFalse(PlayerShopEscrowTransactionService
                .replayRequestMatches(packet(
                        "free", OfferAction.ACQUIRE_FROM_SHOP,
                        2, 10L, Optional.empty()), intent));
        assertFalse(PlayerShopEscrowTransactionService
                .replayRequestMatches(packet(
                        "free", OfferAction.SELL_TO_SHOP,
                        2, 9L, Optional.empty()), intent));
        assertFalse(PlayerShopEscrowTransactionService
                .replayRequestMatches(packet(
                        "free", OfferAction.ACQUIRE_FROM_SHOP,
                        2, 9L, Optional.of(PaymentSource.WALLET)),
                        intent));
    }

    @Test
    void usageKeyUsesImmutableRegistryIdentity() {
        PlayerShopIdentity first = identity(UUID.fromString(
                "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        PlayerShopIdentity second = identity(UUID.fromString(
                "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));

        assertNotEquals(PlayerShopEscrowTransactionService
                        .usageShopKey(first),
                PlayerShopEscrowTransactionService
                        .usageShopKey(second));
    }

    @Test
    void replayRejectsServerOfferIntentOnPlayerShopRoute() {
        PlayerShopIdentity identity = identity(UUID.fromString(
                "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        PlayerShopEscrowIntent serverIntent = intent(
                identity,
                PlayerShopOperation.SERVER_SHOP_OFFER_ACQUIRE);

        assertFalse(PlayerShopEscrowTransactionService
                .replayRequestMatches(packet(
                        "free", OfferAction.ACQUIRE_FROM_SHOP,
                        2, 9L, Optional.empty()), serverIntent));
    }

    private static C2SPlayerShopOfferPacket packet(
            String optionId,
            OfferAction action,
            int quantity,
            long revision,
            Optional<PaymentSource> source
    ) {
        return new C2SPlayerShopOfferPacket(
                BlockPos.ZERO, 0, "listing", optionId,
                action, quantity, revision, source,
                REQUEST, 0);
    }

    private static PlayerShopEscrowIntent intent(
            PlayerShopIdentity identity
    ) {
        return intent(identity,
                PlayerShopOperation.PLAYER_SHOP_OFFER_ACQUIRE);
    }

    private static PlayerShopEscrowIntent intent(
            PlayerShopIdentity identity,
            PlayerShopOperation operation
    ) {
        PlayerShopListingSnapshot listing =
                PlayerShopListingSnapshot.capture(
                        "listing", 0,
                        PlayerShopListingSnapshot.Direction.SELL,
                        PlayerShopListingSnapshot
                                .ConfiguredTradeMode.MONEY,
                        1, 0L, null, 0,
                        0L, 0, 0,
                        List.of(new PlayerShopListingSnapshot.ItemTemplate(
                                "minecraft:stone", 1,
                                PlayerShopItemMatchMode.ITEM_ONLY,
                                new byte[]{1})),
                        new PlayerShopListingSnapshot.PromotionSnapshot(
                                "", 0.0D, 0, 0, 0L, 0L,
                                false, false),
                        false, false,
                        operation == PlayerShopOperation
                                .SERVER_SHOP_OFFER_ACQUIRE);
        return PlayerShopEscrowIntent.prepared(
                REQUEST, identity.ownerId(), identity.ownerId(),
                identity,
                operation,
                PlayerShopTradeMethod.FREE,
                PlayerShopPaymentSource.NONE, 2,
                Instant.parse("2026-07-24T12:00:00Z"),
                listing, List.of(), List.of(), List.of(),
                List.of(), Optional.of(
                        new PlayerShopOfferSelection(
                                "listing", 9L, "free",
                                OfferAction.ACQUIRE_FROM_SHOP,
                                OfferLimitPolicy.defaults(),
                                OfferLimitPolicy.defaults(), 0L,
                                listing.outputs(), List.of())));
    }

    private static PlayerShopIdentity identity(UUID registryId) {
        UUID owner = UUID.fromString(
                "cccccccc-cccc-cccc-cccc-cccccccccccc");
        return new PlayerShopIdentity(
                registryId, 1L, "default",
                "minecraft:overworld", 0, 64, 0, owner);
    }
}
