package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.client.market.MarketModule;
import com.enviouse.futureshops.server.market.claim.MarketClaimCollectionCode;
import com.enviouse.futureshops.server.market.claim.MarketClaimCollectionCommand;
import com.enviouse.futureshops.server.market.claim.MarketClaimCollectionResult;
import com.enviouse.futureshops.server.market.claim.MarketClaimPresentationKind;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.OptionalLong;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MarketClaimCollectionPacketTest {
    @Test
    void requestAndResponseRoundTripExactly() {
        MarketClaimCollectionCommand command = command(
                MarketModule.BAZAAR, UUID.randomUUID());
        C2SMarketClaimCollectionPacket request =
                new C2SMarketClaimCollectionPacket(command);
        FriendlyByteBuf requestBuffer = buffer();
        C2SMarketClaimCollectionPacket.encode(request, requestBuffer);
        C2SMarketClaimCollectionPacket restored =
                C2SMarketClaimCollectionPacket.decode(requestBuffer);
        assertEquals(request, restored);
        assertEquals(command.fingerprint(),
                restored.command().fingerprint());

        MarketClaimCollectionResult result =
                new MarketClaimCollectionResult(command.requestId(),
                        command.routeNonce(), command.module(),
                        command.view(), command.claimId(),
                        MarketClaimPresentationKind.MONEY,
                        MarketClaimCollectionCode.COLLECTED, 50L, 0L,
                        OptionalLong.of(900L), true, true);
        S2CMarketClaimCollectionPacket response =
                new S2CMarketClaimCollectionPacket(result);
        FriendlyByteBuf responseBuffer = buffer();
        S2CMarketClaimCollectionPacket.encode(response,
                responseBuffer);
        assertEquals(response,
                S2CMarketClaimCollectionPacket.decode(responseBuffer));
    }

    @Test
    void trailingDataIsRejectedInBothDirections() {
        C2SMarketClaimCollectionPacket request =
                new C2SMarketClaimCollectionPacket(command(
                        MarketModule.AUCTION_HOUSE,
                        UUID.randomUUID()));
        FriendlyByteBuf requestBuffer = buffer();
        C2SMarketClaimCollectionPacket.encode(request, requestBuffer);
        requestBuffer.writeByte(1);
        assertThrows(DecoderException.class, () ->
                C2SMarketClaimCollectionPacket.decode(requestBuffer));

        MarketClaimCollectionCommand command = request.command();
        S2CMarketClaimCollectionPacket response =
                new S2CMarketClaimCollectionPacket(
                        MarketClaimCollectionResult.failure(command,
                                MarketClaimCollectionCode.WALLET_FULL));
        FriendlyByteBuf responseBuffer = buffer();
        S2CMarketClaimCollectionPacket.encode(response,
                responseBuffer);
        responseBuffer.writeByte(1);
        assertThrows(DecoderException.class, () ->
                S2CMarketClaimCollectionPacket.decode(responseBuffer));
    }

    @Test
    void identitiesViewsAndResultBoundsAreStrict() {
        UUID zero = new UUID(0L, 0L);
        assertThrows(IllegalArgumentException.class, () ->
                new MarketClaimCollectionCommand(zero,
                        UUID.randomUUID(), MarketModule.BAZAAR,
                        "claims", UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () ->
                new MarketClaimCollectionCommand(UUID.randomUUID(),
                        UUID.randomUUID(), MarketModule.BAZAAR,
                        "products", UUID.randomUUID()));

        MarketClaimCollectionCommand command = command(
                MarketModule.BAZAAR, UUID.randomUUID());
        assertThrows(IllegalArgumentException.class, () ->
                new MarketClaimCollectionResult(command.requestId(),
                        command.routeNonce(), command.module(),
                        command.view(), command.claimId(),
                        MarketClaimPresentationKind.MONEY,
                        MarketClaimCollectionCode.COLLECTED, -1L, 0L,
                        OptionalLong.empty(), false, true));
        assertThrows(IllegalArgumentException.class, () ->
                new MarketClaimCollectionResult(command.requestId(),
                        command.routeNonce(), command.module(),
                        command.view(), command.claimId(),
                        MarketClaimPresentationKind.UNKNOWN,
                        MarketClaimCollectionCode.NOT_FOUND, 0L, 1L,
                        OptionalLong.empty(), false, false));
        assertThrows(IllegalArgumentException.class, () ->
                new MarketClaimCollectionResult(command.requestId(),
                        command.routeNonce(), command.module(),
                        command.view(), command.claimId(),
                        MarketClaimPresentationKind.ITEM,
                        MarketClaimCollectionCode.COLLECTED, 1L, 0L,
                        OptionalLong.empty(), false, false));
    }

    @Test
    void decoderRejectsNonClaimsViewAndNegativeVarLongs() {
        FriendlyByteBuf request = buffer();
        request.writeUUID(UUID.randomUUID());
        request.writeUUID(UUID.randomUUID());
        request.writeUtf(MarketModule.BAZAAR.id(), 32);
        request.writeUtf("products", 32);
        request.writeUUID(UUID.randomUUID());
        assertThrows(DecoderException.class, () ->
                C2SMarketClaimCollectionPacket.decode(request));

        MarketClaimCollectionCommand command = command(
                MarketModule.BAZAAR, UUID.randomUUID());
        FriendlyByteBuf response = buffer();
        response.writeUUID(command.requestId());
        response.writeUUID(command.routeNonce());
        response.writeUtf(command.module().id(), 32);
        response.writeUtf(command.view(), 32);
        response.writeUUID(command.claimId());
        response.writeEnum(MarketClaimPresentationKind.MONEY);
        response.writeEnum(MarketClaimCollectionCode.WALLET_FULL);
        response.writeVarLong(-1L);
        response.writeVarLong(1L);
        response.writeBoolean(false);
        response.writeBoolean(false);
        response.writeBoolean(false);
        assertThrows(DecoderException.class, () ->
                S2CMarketClaimCollectionPacket.decode(response));
    }

    private static MarketClaimCollectionCommand command(
            MarketModule module,
            UUID claimId
    ) {
        return new MarketClaimCollectionCommand(UUID.randomUUID(),
                UUID.randomUUID(), module, "claims", claimId);
    }

    private static FriendlyByteBuf buffer() {
        return new FriendlyByteBuf(Unpooled.buffer());
    }
}
