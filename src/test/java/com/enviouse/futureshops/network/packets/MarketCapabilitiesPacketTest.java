package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.client.market.MarketCapabilitiesSnapshot;
import com.enviouse.futureshops.client.market.MarketModule;
import com.enviouse.futureshops.client.market.MarketModuleAvailability;
import com.enviouse.futureshops.client.market.MarketModuleCapability;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MarketCapabilitiesPacketTest {
    @Test
    void requestAndResponseRoundTripExactly() {
        C2SMarketCapabilitiesPacket request =
                new C2SMarketCapabilitiesPacket(UUID.randomUUID());
        FriendlyByteBuf requestBuffer = buffer();
        C2SMarketCapabilitiesPacket.encode(request, requestBuffer);
        assertEquals(request,
                C2SMarketCapabilitiesPacket.decode(requestBuffer));

        S2CMarketCapabilitiesPacket response =
                new S2CMarketCapabilitiesPacket(snapshot(
                        request.requestId()));
        FriendlyByteBuf responseBuffer = buffer();
        S2CMarketCapabilitiesPacket.encode(response, responseBuffer);
        assertEquals(response,
                S2CMarketCapabilitiesPacket.decode(responseBuffer));
    }

    @Test
    void feeAndPlayerCatalogCapabilitiesRoundTripExactly() {
        MarketCapabilitiesSnapshot base = snapshot(UUID.randomUUID());
        MarketCapabilitiesSnapshot configured =
                new MarketCapabilitiesSnapshot(base.requestId(),
                        base.revision(), base.showNavigation(),
                        base.defaultModule(),
                        base.walletBalanceMinorUnits(),
                        base.walletBalanceKnown(), base.currencyName(),
                        base.currencyDecimals(), 175L, true,
                        base.auctionDurationPresetSeconds(),
                        base.modules());
        S2CMarketCapabilitiesPacket packet =
                new S2CMarketCapabilitiesPacket(configured);
        FriendlyByteBuf buffer = buffer();

        S2CMarketCapabilitiesPacket.encode(packet, buffer);
        S2CMarketCapabilitiesPacket decoded =
                S2CMarketCapabilitiesPacket.decode(buffer);

        assertEquals(packet, decoded);
        assertEquals(175L,
                decoded.snapshot().auctionListingFeeMinor());
        assertEquals(true,
                decoded.snapshot().bazaarPlayerCatalog());
    }

    @Test
    void lifecycleAvailabilityValuesAreAppendOnlyAndRoundTrip() {
        assertEquals(0, MarketModuleAvailability.ENABLED.ordinal());
        assertEquals(1,
                MarketModuleAvailability.CLAIMS_ONLY.ordinal());
        assertEquals(2, MarketModuleAvailability.DISABLED.ordinal());
        assertEquals(3, MarketModuleAvailability.HIDDEN.ordinal());
        assertEquals(4, MarketModuleAvailability.FROZEN.ordinal());
        assertEquals(5, MarketModuleAvailability.DRAINING.ordinal());
        assertEquals(6, MarketModuleAvailability
                .CANCEL_AND_REFUND.ordinal());
        MarketCapabilitiesSnapshot lifecycle =
                new MarketCapabilitiesSnapshot(UUID.randomUUID(), 8L,
                        true, MarketModule.BAZAAR, List.of(
                        capability(MarketModule.SHOP,
                                MarketModuleAvailability.FROZEN, 0L),
                        capability(MarketModule.BAZAAR,
                                MarketModuleAvailability.DRAINING, 0L),
                        capability(MarketModule.AUCTION_HOUSE,
                                MarketModuleAvailability
                                        .CANCEL_AND_REFUND, 0L)));
        S2CMarketCapabilitiesPacket packet =
                new S2CMarketCapabilitiesPacket(lifecycle);
        FriendlyByteBuf buffer = buffer();

        S2CMarketCapabilitiesPacket.encode(packet, buffer);

        assertEquals(packet,
                S2CMarketCapabilitiesPacket.decode(buffer));
    }

    @Test
    void requestIdentityAndTrailingDataAreRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                new C2SMarketCapabilitiesPacket(new UUID(0L, 0L)));
        FriendlyByteBuf buffer = buffer();
        buffer.writeUUID(UUID.randomUUID());
        buffer.writeByte(1);

        assertThrows(DecoderException.class,
                () -> C2SMarketCapabilitiesPacket.decode(buffer));
    }

    @Test
    void responseRequiresTheCompleteBoundedModuleSet() {
        MarketModuleCapability shop = capability(MarketModule.SHOP,
                MarketModuleAvailability.ENABLED, 0L);
        MarketCapabilitiesSnapshot incomplete =
                new MarketCapabilitiesSnapshot(UUID.randomUUID(), 1L,
                        true, MarketModule.SHOP, List.of(shop));

        assertThrows(IllegalArgumentException.class, () ->
                new S2CMarketCapabilitiesPacket(incomplete));

        FriendlyByteBuf oversized = responseHeader();
        oversized.writeVarInt(
                S2CMarketCapabilitiesPacket.MODULE_COUNT + 1);
        assertThrows(DecoderException.class, () ->
                S2CMarketCapabilitiesPacket.decode(oversized));
    }

    @Test
    void oversizedTextAndNegativeCountersAreRejectedDuringDecode() {
        FriendlyByteBuf oversizedName = responseHeader();
        oversizedName.writeVarInt(3);
        oversizedName.writeEnum(MarketModule.SHOP);
        oversizedName.writeEnum(MarketModuleAvailability.ENABLED);
        oversizedName.writeUtf("x".repeat(65), 65);
        assertThrows(DecoderException.class, () ->
                S2CMarketCapabilitiesPacket.decode(oversizedName));

        FriendlyByteBuf negativeClaims = responseHeader();
        negativeClaims.writeVarInt(3);
        writeCapability(negativeClaims, MarketModule.SHOP,
                MarketModuleAvailability.ENABLED, -1L);
        writeCapability(negativeClaims, MarketModule.BAZAAR,
                MarketModuleAvailability.ENABLED, 0L);
        writeCapability(negativeClaims, MarketModule.AUCTION_HOUSE,
                MarketModuleAvailability.ENABLED, 0L);
        assertThrows(DecoderException.class, () ->
                S2CMarketCapabilitiesPacket.decode(negativeClaims));
    }

    @Test
    void responseTrailingDataIsRejected() {
        S2CMarketCapabilitiesPacket response =
                new S2CMarketCapabilitiesPacket(
                        snapshot(UUID.randomUUID()));
        FriendlyByteBuf buffer = buffer();
        S2CMarketCapabilitiesPacket.encode(response, buffer);
        buffer.writeByte(1);

        assertThrows(DecoderException.class,
                () -> S2CMarketCapabilitiesPacket.decode(buffer));
    }

    @Test
    void appendedWalletPresentationIsStrictAndRequired() {
        MarketCapabilitiesSnapshot snapshot = snapshot(
                UUID.randomUUID());
        assertEquals(-1250L, snapshot.walletBalanceMinorUnits());
        assertEquals("Credits", snapshot.currencyName());
        assertEquals(3, snapshot.currencyDecimals());
        assertEquals(false, snapshot.escrowReady());

        assertThrows(IllegalArgumentException.class, () ->
                new MarketCapabilitiesSnapshot(UUID.randomUUID(), 1L,
                        true, MarketModule.SHOP, 1L, false,
                        "Credits", 2, snapshot.modules()));
        assertThrows(IllegalArgumentException.class, () ->
                new MarketCapabilitiesSnapshot(UUID.randomUUID(), 1L,
                        true, MarketModule.SHOP, 0L, true,
                        "x".repeat(65), 2, snapshot.modules()));
        assertThrows(IllegalArgumentException.class, () ->
                new MarketCapabilitiesSnapshot(UUID.randomUUID(), 1L,
                        true, MarketModule.SHOP, 0L, true,
                        "Credits", 7, snapshot.modules()));

        FriendlyByteBuf truncated = responseHeader();
        truncated.writeVarInt(3);
        writeCapability(truncated, MarketModule.SHOP,
                MarketModuleAvailability.ENABLED, 0L);
        writeCapability(truncated, MarketModule.BAZAAR,
                MarketModuleAvailability.ENABLED, 0L);
        writeCapability(truncated, MarketModule.AUCTION_HOUSE,
                MarketModuleAvailability.ENABLED, 0L);
        assertThrows(DecoderException.class, () ->
                S2CMarketCapabilitiesPacket.decode(truncated));
    }

    private static MarketCapabilitiesSnapshot snapshot(UUID requestId) {
        return new MarketCapabilitiesSnapshot(requestId, 4L, true,
                false, MarketModule.SHOP, -1250L, true, "Credits", 3,
                0L, false,
                List.of(3_600L, 21_600L, 86_400L, 259_200L,
                        604_800L),
                List.of(
                capability(MarketModule.SHOP,
                        MarketModuleAvailability.ENABLED, 0L),
                capability(MarketModule.BAZAAR,
                        MarketModuleAvailability.CLAIMS_ONLY, 2L),
                capability(MarketModule.AUCTION_HOUSE,
                        MarketModuleAvailability.DISABLED, 0L)));
    }

    private static MarketModuleCapability capability(
            MarketModule module,
            MarketModuleAvailability availability,
            long claims
    ) {
        return new MarketModuleCapability(module, availability,
                module.defaultDisplayName(), module.defaultAccent(),
                claims, 4L);
    }

    private static FriendlyByteBuf responseHeader() {
        FriendlyByteBuf buffer = buffer();
        buffer.writeUUID(UUID.randomUUID());
        buffer.writeLong(4L);
        buffer.writeBoolean(true);
        buffer.writeEnum(MarketModule.SHOP);
        return buffer;
    }

    private static void writeCapability(
            FriendlyByteBuf buffer,
            MarketModule module,
            MarketModuleAvailability availability,
            long claims
    ) {
        buffer.writeEnum(module);
        buffer.writeEnum(availability);
        buffer.writeUtf(module.defaultDisplayName(), 64);
        buffer.writeUtf(module.defaultAccent(), 9);
        buffer.writeVarLong(claims);
        buffer.writeLong(4L);
    }

    private static FriendlyByteBuf buffer() {
        return new FriendlyByteBuf(Unpooled.buffer());
    }
}
