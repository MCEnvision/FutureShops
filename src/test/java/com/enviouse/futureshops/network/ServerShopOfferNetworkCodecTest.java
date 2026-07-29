package com.enviouse.futureshops.network;

import com.enviouse.futureshops.catalog.offer.AcquireOfferOption;
import com.enviouse.futureshops.catalog.offer.OfferItemComponent;
import com.enviouse.futureshops.catalog.offer.OfferLimitPolicy;
import com.enviouse.futureshops.catalog.offer.OfferSchedule;
import com.enviouse.futureshops.catalog.offer.OfferStockPolicy;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferListing;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServerShopOfferNetworkCodecTest {
    @Test
    void listingRoundTripsWithinAggregateLimit() {
        ServerShopOfferListing listing = listing(List.of(
                new OfferItemComponent(
                        "iron", "minecraft:iron_ingot", 1, "")));

        byte[] encoded =
                ServerShopOfferNetworkCodec.encodeListingBytes(listing);

        assertEquals(listing,
                ServerShopOfferNetworkCodec.decodeListingBytes(encoded));
    }

    @Test
    void encodeRejectsListingOverAggregateLimit() {
        List<OfferItemComponent> outputs = new ArrayList<>();
        for (int index = 0; index < 36; index++) {
            outputs.add(new OfferItemComponent(
                    "component_" + index,
                    "minecraft:stone",
                    1,
                    "{value:" + "a".repeat(39_980) + index + "}"));
        }

        assertThrows(IllegalArgumentException.class, () ->
                ServerShopOfferNetworkCodec.encodeListingBytes(
                        listing(outputs)));
    }

    @Test
    void decodeRejectsListingOverAggregateLimitBeforeParsing() {
        byte[] oversized = new byte[
                ServerShopOfferNetworkCodec.MAX_ENCODED_LISTING_BYTES + 1];

        assertThrows(DecoderException.class, () ->
                ServerShopOfferNetworkCodec.decodeListingBytes(
                        oversized));
    }

    @Test
    void catalogRoundTripsInsideOneBoundedPayload() {
        List<ServerShopOfferListing> listings = List.of(
                listing(List.of(new OfferItemComponent(
                        "iron", "minecraft:iron_ingot", 1, ""))),
                listing(List.of(new OfferItemComponent(
                        "gold", "minecraft:gold_ingot", 2, "")))
                        .withRevision(3L));
        FriendlyByteBuf buffer = new FriendlyByteBuf(
                Unpooled.buffer());
        try {
            ServerShopOfferNetworkCodec.encodeListings(
                    buffer, listings);
            assertEquals(listings,
                    ServerShopOfferNetworkCodec.decodeListings(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void encodeRejectsAggregateCatalogOverLimit() {
        List<ServerShopOfferListing> listings = new ArrayList<>();
        for (int index = 0; index < 40; index++) {
            listings.add(listing(List.of(new OfferItemComponent(
                    "component_" + index,
                    "minecraft:stone", 1,
                    "{value:" + "a".repeat(32_000) + index + "}")))
                    .withRevision(index));
        }
        FriendlyByteBuf buffer = new FriendlyByteBuf(
                Unpooled.buffer());
        try {
            assertThrows(IllegalArgumentException.class, () ->
                    ServerShopOfferNetworkCodec.encodeListings(
                            buffer, listings));
        } finally {
            buffer.release();
        }
    }

    @Test
    void decodeRejectsAggregateCatalogBeforeParsing() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(
                Unpooled.buffer());
        try {
            buffer.writeVarInt(
                    ServerShopOfferNetworkCodec
                            .MAX_ENCODED_CATALOG_BYTES + 1);
            buffer.writeZero(
                    ServerShopOfferNetworkCodec
                            .MAX_ENCODED_CATALOG_BYTES + 1);
            assertThrows(DecoderException.class, () ->
                    ServerShopOfferNetworkCodec.decodeListings(buffer));
        } finally {
            buffer.release();
        }
    }

    private static ServerShopOfferListing listing(
            List<OfferItemComponent> outputs
    ) {
        AcquireOfferOption free = new AcquireOfferOption(
                "free", "Free", true, false, 0L, List.of(),
                1, OfferLimitPolicy.defaults(),
                OfferSchedule.always(), "");
        return new ServerShopOfferListing(
                "iron_bundle", 2L, "Iron bundle", "", "all",
                "minecraft:iron_ingot", "", true, 0L, "",
                outputs, List.of(free), List.of(),
                OfferStockPolicy.limited(100L, 0L),
                OfferLimitPolicy.defaults(), OfferSchedule.always(),
                List.of());
    }
}
