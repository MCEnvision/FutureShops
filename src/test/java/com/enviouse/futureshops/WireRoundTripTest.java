package com.enviouse.futureshops;

import com.enviouse.futureshops.data.CatalogItem;
import com.enviouse.futureshops.network.packets.C2SBuyRequestPacket;
import com.enviouse.futureshops.network.packets.C2SVerifyAdminCartPacket;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Wire round-trip guards for the per-listing-id protocol-24 change. These catch encode/decode field
 * order drift — the leading {@code listingId} must be written first and read first on every line that
 * carries it, or buy/sell/cart desync silently.
 */
public class WireRoundTripTest {

    private static FriendlyByteBuf buf() {
        return new FriendlyByteBuf(Unpooled.buffer());
    }

    @Test
    void catalogItemRoundTripsListingId() {
        CatalogItem in = new CatalogItem(
                "enchanted_book_2", "minecraft:enchanted_book", "Sharpness V Book",
                3000L, 0L, -1, true, false, "books", false, 0L, false,
                "{Enchantments:[{id:\"minecraft:sharpness\",lvl:5s}]}");
        FriendlyByteBuf b = buf();
        CatalogItem.encode(b, in);
        CatalogItem out = CatalogItem.decode(b);
        assertEquals(in, out, "CatalogItem must round-trip all 13 fields including listingId + nbtJson");
        assertEquals("enchanted_book_2", out.listingId());
        assertEquals("minecraft:enchanted_book", out.itemId());
    }

    @Test
    void catalogItemNullListingIdEncodesAsItemId() {
        // A server-built CatalogItem always carries a listingId, but guard the encode fallback so a
        // null never corrupts the stream — it must serialize as the registry itemId.
        CatalogItem in = new CatalogItem(
                null, "minecraft:diamond", "Diamond",
                500L, 250L, -1, true, false, "all", false, 0L, false, "");
        FriendlyByteBuf b = buf();
        CatalogItem.encode(b, in);
        CatalogItem out = CatalogItem.decode(b);
        assertEquals("minecraft:diamond", out.listingId(), "null listingId encodes as itemId");
        assertEquals("minecraft:diamond", out.itemId());
    }

    @Test
    void buyLineItemRoundTripsListingId() {
        C2SBuyRequestPacket.LineItem in = new C2SBuyRequestPacket.LineItem("enchanted_book_2", 7);
        FriendlyByteBuf b = buf();
        C2SBuyRequestPacket.LineItem.encode(b, in);
        C2SBuyRequestPacket.LineItem out = C2SBuyRequestPacket.LineItem.decode(b);
        assertEquals(in, out);
        assertEquals("enchanted_book_2", out.listingId());
        assertEquals(7, out.quantity());
    }

    @Test
    void adminCartLineRoundTripsListingId() {
        C2SVerifyAdminCartPacket.AdminCartLine in =
                new C2SVerifyAdminCartPacket.AdminCartLine("enchanted_book_2", 3, 3000L);
        FriendlyByteBuf b = buf();
        C2SVerifyAdminCartPacket.AdminCartLine.encode(b, in);
        C2SVerifyAdminCartPacket.AdminCartLine out = C2SVerifyAdminCartPacket.AdminCartLine.decode(b);
        assertEquals(in, out);
        assertEquals("enchanted_book_2", out.listingId());
    }
}
