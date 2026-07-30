package com.enviouse.futureshops.data;

import com.enviouse.futureshops.catalog.offer.ServerShopOfferListing;
import com.enviouse.futureshops.network.ServerShopOfferNetworkCodec;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Objects;
import java.util.Optional;

public record PlayerShopNormalizedOfferData(
        int clientListingIndex,
        int sourceListingIndex,
        boolean unavailable,
        Optional<ServerShopOfferListing> offer
) {
    private static final int MAXIMUM_OFFER_BYTES = 1_048_576;

    public PlayerShopNormalizedOfferData {
        offer = Objects.requireNonNull(offer, "offer");
        if (clientListingIndex < 0 || sourceListingIndex < 0
                || unavailable && offer.isPresent()) {
            throw new IllegalArgumentException(
                    "Player shop offer snapshot is invalid");
        }
    }

    public static void encode(
            FriendlyByteBuf buffer,
            PlayerShopNormalizedOfferData data
    ) {
        buffer.writeVarInt(data.clientListingIndex());
        buffer.writeVarInt(data.sourceListingIndex());
        buffer.writeBoolean(data.unavailable());
        buffer.writeBoolean(data.offer().isPresent());
        data.offer().ifPresent(offer -> buffer.writeByteArray(
                ServerShopOfferNetworkCodec.encodeListingBytes(offer)));
    }

    public static PlayerShopNormalizedOfferData decode(
            FriendlyByteBuf buffer
    ) {
        try {
            int clientIndex = buffer.readVarInt();
            int sourceIndex = buffer.readVarInt();
            boolean unavailable = buffer.readBoolean();
            Optional<ServerShopOfferListing> offer =
                    buffer.readBoolean()
                            ? Optional.of(ServerShopOfferNetworkCodec
                            .decodeListingBytes(buffer.readByteArray(
                                    MAXIMUM_OFFER_BYTES)))
                            : Optional.empty();
            return new PlayerShopNormalizedOfferData(
                    clientIndex, sourceIndex, unavailable, offer);
        } catch (RuntimeException exception) {
            throw new DecoderException(
                    "Player shop offer snapshot is malformed",
                    exception);
        }
    }
}
