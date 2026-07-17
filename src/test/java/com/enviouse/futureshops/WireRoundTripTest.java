package com.enviouse.futureshops;

import com.enviouse.futureshops.data.BalanceTopEntry;
import com.enviouse.futureshops.data.AtmDenominationData;
import com.enviouse.futureshops.data.CatalogBarterIngredient;
import com.enviouse.futureshops.data.CatalogBarterRecipe;
import com.enviouse.futureshops.data.CatalogCategory;
import com.enviouse.futureshops.data.CatalogItem;
import com.enviouse.futureshops.data.CatalogPromo;
import com.enviouse.futureshops.data.FranchiseLeaderboardEntry;
import com.enviouse.futureshops.data.NearbyShopEntry;
import com.enviouse.futureshops.data.OwnedShopSummary;
import com.enviouse.futureshops.data.SettlementHistoryRow;
import com.enviouse.futureshops.data.TransactionHistoryEntry;
import com.enviouse.futureshops.network.packets.C2SAdminShopAddItemsPacket;
import com.enviouse.futureshops.network.packets.C2SAdminShopEditPacket;
import com.enviouse.futureshops.network.packets.C2SAtmWithdrawPacket;
import com.enviouse.futureshops.network.packets.C2SBuyRequestPacket;
import com.enviouse.futureshops.network.packets.C2SVerifyAdminCartPacket;
import com.enviouse.futureshops.network.packets.C2SVerifyCartPacket;
import com.enviouse.futureshops.network.packets.S2CAdminEditAckPacket;
import com.enviouse.futureshops.network.packets.S2CAtmDataPacket;
import com.enviouse.futureshops.network.packets.S2CAtmResultPacket;
import com.enviouse.futureshops.network.packets.S2CBalTopUiPacket;
import com.enviouse.futureshops.network.packets.S2CShopDataPacket;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wire round-trip guards for the per-listing-id protocol-24 change, the trailing-nbtJson
 * protocol-25 change and the protocol-26 admin-editor packets. These catch encode/decode field
 * order drift — the leading {@code listingId} must be written first and read first on every line
 * that carries it, every protocol-25 {@code nbtJson}/{@code targetListingId} must stay the LAST
 * field of its DTO (null encoding as blank, blank meaning "no NBT" / legacy behaviour), and the
 * protocol-26 {@code canEdit} must stay the LAST field of S2CShopDataPacket — or
 * buy/sell/cart/history desync silently.
 */
public class WireRoundTripTest {

    private static FriendlyByteBuf buf() {
        return new FriendlyByteBuf(Unpooled.buffer());
    }

    @Test
    void catalogItemRoundTripsListingId() {
        CatalogItem in = new CatalogItem(
                "enchanted_book_2", "minecraft:enchanted_book", "Sharpness V Book",
                3000L, 0L, 44, false, false, "books", false, 0L, false,
                "{Enchantments:[{id:\"minecraft:sharpness\",lvl:5s}]}", 64);
        FriendlyByteBuf b = buf();
        CatalogItem.encode(b, in);
        CatalogItem out = CatalogItem.decode(b);
        assertEquals(in, out, "CatalogItem must round-trip all 14 fields incl. nbtJson + configuredStock");
        assertEquals("enchanted_book_2", out.listingId());
        assertEquals("minecraft:enchanted_book", out.itemId());
        // Live remaining (44) and configured max (64) are distinct fields on the wire.
        assertEquals(44, out.stock());
        assertEquals(64, out.configuredStock());
    }

    @Test
    void catalogItemNullListingIdEncodesAsItemId() {
        // A server-built CatalogItem always carries a listingId, but guard the encode fallback so a
        // null never corrupts the stream — it must serialize as the registry itemId.
        CatalogItem in = new CatalogItem(
                null, "minecraft:diamond", "Diamond",
                500L, 250L, -1, true, false, "all", false, 0L, false, "", -1);
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
        // v25: trailing expectedNbtJson (variant-swap detection, mirrors the player cart line)
        C2SVerifyAdminCartPacket.AdminCartLine in = new C2SVerifyAdminCartPacket.AdminCartLine(
                "enchanted_book_2", 3, 3000L, "{StoredEnchantments:[{id:\"minecraft:mending\",lvl:1s}]}");
        FriendlyByteBuf b = buf();
        C2SVerifyAdminCartPacket.AdminCartLine.encode(b, in);
        C2SVerifyAdminCartPacket.AdminCartLine out = C2SVerifyAdminCartPacket.AdminCartLine.decode(b);
        assertEquals(in, out);
        assertEquals("enchanted_book_2", out.listingId());
        assertEquals("{StoredEnchantments:[{id:\"minecraft:mending\",lvl:1s}]}", out.expectedNbtJson());
    }

    @Test
    void adminCartLineNullNbtEncodesAsBlank() {
        C2SVerifyAdminCartPacket.AdminCartLine in =
                new C2SVerifyAdminCartPacket.AdminCartLine("legacy_id", 1, 100L, null);
        FriendlyByteBuf b = buf();
        C2SVerifyAdminCartPacket.AdminCartLine.encode(b, in);
        assertEquals("", C2SVerifyAdminCartPacket.AdminCartLine.decode(b).expectedNbtJson());
    }

    // ---- protocol 25: trailing nbtJson / targetListingId fields ----

    @Test
    void barterIngredientRoundTripsTrailingNbtJson() {
        CatalogBarterIngredient in = new CatalogBarterIngredient(
                "minecraft:potion", 2, "{Potion:\"minecraft:strong_healing\"}");
        FriendlyByteBuf b = buf();
        CatalogBarterIngredient.encode(b, in);
        CatalogBarterIngredient out = CatalogBarterIngredient.decode(b);
        assertEquals(in, out, "CatalogBarterIngredient must round-trip itemId + count + trailing nbtJson");
        assertEquals("{Potion:\"minecraft:strong_healing\"}", out.nbtJson());
        assertEquals(0, b.readableBytes(), "nbtJson must be the LAST field on the wire");
    }

    @Test
    void barterIngredientNullNbtJsonRoundTripsAsBlank() {
        // Legacy shape: blank nbtJson means "no NBT requirement" (lenient identity matching).
        // A null must normalize to blank on the wire, never corrupt the stream.
        CatalogBarterIngredient in = new CatalogBarterIngredient("minecraft:emerald", 4, null);
        FriendlyByteBuf b = buf();
        CatalogBarterIngredient.encode(b, in);
        CatalogBarterIngredient out = CatalogBarterIngredient.decode(b);
        assertEquals("", out.nbtJson(), "null nbtJson encodes as blank (legacy no-NBT shape)");
        assertEquals(new CatalogBarterIngredient("minecraft:emerald", 4, ""), out);
    }

    @Test
    void barterRecipeRoundTripsTrailingTargetListingId() {
        CatalogBarterRecipe in = new CatalogBarterRecipe(
                "recipe_1", "minecraft:enchanted_book", 1,
                List.of(
                        new CatalogBarterIngredient("minecraft:diamond", 3, ""),
                        new CatalogBarterIngredient("minecraft:potion", 1, "{Potion:\"minecraft:swiftness\"}")),
                "enchanted_book_2");
        FriendlyByteBuf b = buf();
        CatalogBarterRecipe.encode(b, in);
        CatalogBarterRecipe out = CatalogBarterRecipe.decode(b);
        assertEquals(in, out,
                "CatalogBarterRecipe must round-trip all 5 fields incl. nested ingredient nbtJson + trailing targetListingId");
        assertEquals("enchanted_book_2", out.targetListingId());
        assertEquals("{Potion:\"minecraft:swiftness\"}", out.ingredients().get(1).nbtJson());
        assertEquals(0, b.readableBytes(), "targetListingId must be the LAST field on the wire");
    }

    @Test
    void barterRecipeNullTargetListingIdRoundTripsAsBlank() {
        // Legacy shape: blank targetListingId means "unresolved" — clients fall back to
        // registry-id lookups exactly as before protocol 25.
        CatalogBarterRecipe in = new CatalogBarterRecipe(
                "recipe_2", "minecraft:diamond", 2,
                List.of(new CatalogBarterIngredient("minecraft:iron_ingot", 8, null)),
                null);
        FriendlyByteBuf b = buf();
        CatalogBarterRecipe.encode(b, in);
        CatalogBarterRecipe out = CatalogBarterRecipe.decode(b);
        assertEquals("", out.targetListingId(), "null targetListingId encodes as blank (unresolved legacy shape)");
        assertEquals("", out.ingredients().get(0).nbtJson());
    }

    @Test
    void verifyCartLineRoundTripsTrailingExpectedNbtJson() {
        C2SVerifyCartPacket.CartLine in = new C2SVerifyCartPacket.CartLine(
                new BlockPos(10, 64, -30), 2, 5,
                "minecraft:enchanted_book", 3000L, true, "BUY",
                "{StoredEnchantments:[{id:\"minecraft:sharpness\",lvl:5s}]}");
        FriendlyByteBuf b = buf();
        C2SVerifyCartPacket.CartLine.encode(b, in);
        C2SVerifyCartPacket.CartLine out = C2SVerifyCartPacket.CartLine.decode(b);
        assertEquals(in, out, "CartLine must round-trip all 8 fields incl. trailing expectedNbtJson");
        assertEquals("{StoredEnchantments:[{id:\"minecraft:sharpness\",lvl:5s}]}", out.expectedNbtJson());
        assertEquals(0, b.readableBytes(), "expectedNbtJson must be the LAST field on the wire");
    }

    @Test
    void verifyCartLineNullExpectedNbtJsonEncodesAsBlank() {
        // Legacy shape: a cart line for a plain listing carries no NBT snapshot; null must
        // serialize as blank so the server sees "no NBT expected".
        C2SVerifyCartPacket.CartLine in = new C2SVerifyCartPacket.CartLine(
                new BlockPos(0, 70, 0), 0, 1,
                "minecraft:diamond", 500L, false, "SELL", null);
        FriendlyByteBuf b = buf();
        C2SVerifyCartPacket.CartLine.encode(b, in);
        C2SVerifyCartPacket.CartLine out = C2SVerifyCartPacket.CartLine.decode(b);
        assertEquals("", out.expectedNbtJson(), "null expectedNbtJson encodes as blank (legacy no-NBT line)");
        assertEquals("minecraft:diamond", out.expectedItemId());
        assertEquals(500L, out.expectedPriceMinor());
    }

    @Test
    void verifyCartPacketRoundTripsLineList() {
        C2SVerifyCartPacket in = new C2SVerifyCartPacket(List.of(
                new C2SVerifyCartPacket.CartLine(
                        new BlockPos(1, 65, 2), 0, 3,
                        "minecraft:emerald", 900L, false, "BUY", ""),
                new C2SVerifyCartPacket.CartLine(
                        new BlockPos(-4, 80, 12), 1, 1,
                        "minecraft:potion", 1500L, true, "BUY", "{Potion:\"minecraft:night_vision\"}")));
        FriendlyByteBuf b = buf();
        C2SVerifyCartPacket.encode(in, b);
        C2SVerifyCartPacket out = C2SVerifyCartPacket.decode(b);
        assertEquals(in, out, "C2SVerifyCartPacket must round-trip its bounded line list");
        assertEquals(0, b.readableBytes());
    }

    @Test
    void settlementHistoryRowRoundTripsTrailingNbtJson() {
        SettlementHistoryRow in = new SettlementHistoryRow(
                1_752_300_000L, 12_500L, "SALE", "minecraft:netherite_sword", 1,
                "{Damage:0,Enchantments:[{id:\"minecraft:unbreaking\",lvl:3s}]}");
        FriendlyByteBuf b = buf();
        SettlementHistoryRow.encode(b, in);
        SettlementHistoryRow out = SettlementHistoryRow.decode(b);
        assertEquals(in, out, "SettlementHistoryRow must round-trip all 6 fields incl. trailing nbtJson");
        assertEquals(0, b.readableBytes(), "nbtJson must be the LAST field on the wire");
    }

    @Test
    void settlementHistoryRowNullNbtJsonEncodesAsBlank() {
        SettlementHistoryRow in = new SettlementHistoryRow(
                1_752_300_000L, 200L, "CLAIM", "minecraft:stone", 64, null);
        FriendlyByteBuf b = buf();
        SettlementHistoryRow.encode(b, in);
        SettlementHistoryRow out = SettlementHistoryRow.decode(b);
        assertEquals("", out.nbtJson(), "null nbtJson encodes as blank (legacy plain-item row)");
        assertEquals("minecraft:stone", out.itemId());
        assertEquals(64, out.quantity());
    }

    @Test
    void transactionHistoryEntryRoundTripsTrailingNbtJson() {
        TransactionHistoryEntry in = new TransactionHistoryEntry(
                1_752_300_000L, "BUY", "minecraft:enchanted_book", 2, 6_000L, "cart",
                "{StoredEnchantments:[{id:\"minecraft:mending\",lvl:1s}]}");
        FriendlyByteBuf b = buf();
        TransactionHistoryEntry.encode(b, in);
        TransactionHistoryEntry out = TransactionHistoryEntry.decode(b);
        assertEquals(in, out, "TransactionHistoryEntry must round-trip all 7 fields incl. trailing nbtJson");
        assertEquals(0, b.readableBytes(), "nbtJson must be the LAST field on the wire");
    }

    @Test
    void transactionHistoryEntryNullNbtJsonEncodesAsBlank() {
        TransactionHistoryEntry in = new TransactionHistoryEntry(
                1_752_300_000L, "SELL", "minecraft:wheat", 32, 480L, "", null);
        FriendlyByteBuf b = buf();
        TransactionHistoryEntry.encode(b, in);
        TransactionHistoryEntry out = TransactionHistoryEntry.decode(b);
        assertEquals("", out.nbtJson(), "null nbtJson encodes as blank (legacy plain-item entry)");
        assertEquals("minecraft:wheat", out.itemId());
        assertEquals(480L, out.totalMinorUnits());
    }

    @Test
    void ownedShopSummaryRoundTripsTrailingFeaturedNbtJson() {
        OwnedShopSummary in = new OwnedShopSummary(
                "minecraft:overworld", new BlockPos(12, 70, -8).asLong(), "minecraft:potion",
                5, 320, 1, true, 1_250L, 98_000L, "{Potion:\"minecraft:long_night_vision\"}");
        FriendlyByteBuf b = buf();
        OwnedShopSummary.encode(b, in);
        OwnedShopSummary out = OwnedShopSummary.decode(b);
        assertEquals(in, out, "OwnedShopSummary must round-trip all 10 fields incl. trailing featuredNbtJson");
        assertEquals(0, b.readableBytes(), "featuredNbtJson must be the LAST field on the wire");
    }

    @Test
    void ownedShopSummaryNullFeaturedNbtJsonEncodesAsBlank() {
        OwnedShopSummary in = new OwnedShopSummary(
                "minecraft:the_nether", new BlockPos(0, 40, 0).asLong(), "minecraft:gold_ingot",
                2, 12, 0, false, 0L, 5_000L, null);
        FriendlyByteBuf b = buf();
        OwnedShopSummary.encode(b, in);
        OwnedShopSummary out = OwnedShopSummary.decode(b);
        assertEquals("", out.featuredNbtJson(), "null featuredNbtJson encodes as blank (legacy plain featured item)");
        assertEquals("minecraft:gold_ingot", out.featuredItemId());
    }

    @Test
    void balTopUiPacketRoundTripsTrailingPopularItemNbtJson() {
        S2CBalTopUiPacket in = new S2CBalTopUiPacket(
                1, 3,
                List.of(new BalanceTopEntry(new UUID(1L, 2L), "EnVy", 1_000_000L)),
                "Coins", 2,
                new UUID(3L, 4L), "Alice", 42,
                new UUID(5L, 6L), "Bob", 17,
                "minecraft:enchanted_book", 12, 34L,
                List.of(new FranchiseLeaderboardEntry(new UUID(7L, 8L), "MegaMart", "Alice", 6)),
                "{StoredEnchantments:[{id:\"minecraft:fortune\",lvl:3s}]}");
        FriendlyByteBuf b = buf();
        S2CBalTopUiPacket.encode(in, b);
        S2CBalTopUiPacket out = S2CBalTopUiPacket.decode(b);
        assertEquals(in, out, "S2CBalTopUiPacket must round-trip all 16 fields incl. trailing popularItemNbtJson");
        assertEquals("{StoredEnchantments:[{id:\"minecraft:fortune\",lvl:3s}]}", out.popularItemNbtJson());
        assertEquals(0, b.readableBytes(), "popularItemNbtJson must be the LAST field on the wire");
    }

    // ---- protocol 26: admin-editor packets + trailing canEdit ----

    @Test
    void adminShopEditPacketRoundTrips() {
        C2SAdminShopEditPacket in = new C2SAdminShopEditPacket(
                "SAVE_LISTING", "enchanted_book_2", "Sharpness V Book", "books", 3000L, 1500L, -1L);
        FriendlyByteBuf b = buf();
        C2SAdminShopEditPacket.encode(in, b);
        C2SAdminShopEditPacket out = C2SAdminShopEditPacket.decode(b);
        assertEquals(in, out, "C2SAdminShopEditPacket must round-trip all 7 fields");
        assertEquals("SAVE_LISTING", out.action());
        assertEquals(-1L, out.longC());
        assertEquals(0, b.readableBytes(), "longC must be the LAST field on the wire");
    }

    @Test
    void adminShopAddItemsPacketRoundTrips() {
        C2SAdminShopAddItemsPacket in = new C2SAdminShopAddItemsPacket(
                List.of("minecraft:diamond", "minecraft:emerald", "tacz:modern_kinetic_gun"),
                "materials", 500L, 250L, -1L);
        FriendlyByteBuf b = buf();
        C2SAdminShopAddItemsPacket.encode(in, b);
        C2SAdminShopAddItemsPacket out = C2SAdminShopAddItemsPacket.decode(b);
        assertEquals(in, out, "C2SAdminShopAddItemsPacket must round-trip its id list + shared fields");
        assertEquals(3, out.itemIds().size());
        assertEquals(0, b.readableBytes(), "stock must be the LAST field on the wire");
    }

    @Test
    void adminShopAddItemsPacketRejectsOversizedList() {
        // Decode must refuse a hostile length prefix before allocating, mirroring the
        // C2SVerifyAdminCartPacket MAX_LINES guard (cap: 256 ids).
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 257; i++) {
            ids.add("minecraft:item_" + i);
        }
        C2SAdminShopAddItemsPacket in = new C2SAdminShopAddItemsPacket(ids, "all", 0L, 0L, -1L);
        FriendlyByteBuf b = buf();
        C2SAdminShopAddItemsPacket.encode(in, b);
        assertThrows(io.netty.handler.codec.DecoderException.class,
                () -> C2SAdminShopAddItemsPacket.decode(b));
    }

    @Test
    void adminEditAckPacketRoundTrips() {
        S2CAdminEditAckPacket in = new S2CAdminEditAckPacket(true, "ADDED", "12");
        FriendlyByteBuf b = buf();
        S2CAdminEditAckPacket.encode(in, b);
        S2CAdminEditAckPacket out = S2CAdminEditAckPacket.decode(b);
        assertEquals(in, out, "S2CAdminEditAckPacket must round-trip all 3 fields");
        assertEquals("ADDED", out.code());
        assertEquals(0, b.readableBytes(), "arg must be the LAST field on the wire");
    }

    @Test
    void adminEditAckPacketNullArgEncodesAsBlank() {
        S2CAdminEditAckPacket in = new S2CAdminEditAckPacket(false, "IO_ERROR", null);
        FriendlyByteBuf b = buf();
        S2CAdminEditAckPacket.encode(in, b);
        S2CAdminEditAckPacket out = S2CAdminEditAckPacket.decode(b);
        assertEquals("", out.arg(), "null arg encodes as blank (codes without an argument)");
        assertFalse(out.success());
    }

    @Test
    void shopDataPacketRoundTripsTrailingCanEdit() {
        S2CShopDataPacket in = new S2CShopDataPacket(
                "default", 123_456L, "Money", 2,
                List.of(new CatalogCategory("tools", "Tools", 1)),
                List.of(new CatalogItem(
                        "enchanted_book_2", "minecraft:enchanted_book", "Sharpness V Book",
                        3000L, 0L, -1, true, false, "books", false, 0L, false,
                        "{Enchantments:[{id:\"minecraft:sharpness\",lvl:5s}]}", -1)),
                List.of(new CatalogPromo("promo_1", "PERCENTAGE", "minecraft:diamond", 10.0D, 0L)),
                List.of(new CatalogBarterRecipe("recipe_1", "minecraft:enchanted_book", 1,
                        List.of(new CatalogBarterIngredient("minecraft:diamond", 3, "")),
                        "enchanted_book_2")),
                true,
                List.of(new NearbyShopEntry(new BlockPos(4, 70, -9), new UUID(1L, 2L),
                        "EnVy", "EnVy's Shop", 3, 42, 12.5D)),
                false,
                true);
        FriendlyByteBuf b = buf();
        S2CShopDataPacket.encode(in, b);
        S2CShopDataPacket out = S2CShopDataPacket.decode(b);
        assertEquals(in, out, "S2CShopDataPacket must round-trip all 12 fields incl. trailing canEdit");
        assertTrue(out.canEdit());
        assertFalse(out.forceOpen());
        assertEquals(0, b.readableBytes(), "canEdit must be the LAST field on the wire");
    }

    @Test
    void shopDataPacketBackwardCompatCtorsDefaultCanEditFalse() {
        // Pre-26 call sites must stay viewer-only — a defaulted true would show the edit toggle
        // to every player until the server's next authoritative send.
        S2CShopDataPacket legacy = new S2CShopDataPacket(
                "default", 0L, "Money", 2, List.of(), List.of(), List.of(), List.of());
        assertFalse(legacy.canEdit(), "8-arg backward-compat ctor must default canEdit=false");

        S2CShopDataPacket withForceOpen = new S2CShopDataPacket(
                "default", 0L, "Money", 2, List.of(), List.of(), List.of(), List.of(),
                true, List.of(), false);
        assertFalse(withForceOpen.canEdit(), "11-arg backward-compat ctor must default canEdit=false");
    }

    @Test
    void balTopUiPacketNullPopularItemNbtJsonEncodesAsBlank() {
        S2CBalTopUiPacket in = new S2CBalTopUiPacket(
                0, 1,
                List.of(),
                "Coins", 2,
                new UUID(0L, 0L), "", 0,
                new UUID(0L, 0L), "", 0,
                "minecraft:dirt", 1, 1L,
                List.of(),
                null);
        FriendlyByteBuf b = buf();
        S2CBalTopUiPacket.encode(in, b);
        S2CBalTopUiPacket out = S2CBalTopUiPacket.decode(b);
        assertEquals("", out.popularItemNbtJson(), "null popularItemNbtJson encodes as blank (legacy plain popular item)");
        assertEquals("minecraft:dirt", out.popularItemId());
    }

    @Test
    void playerShopListingDataRoundTripsHiddenShowcase() {
        // Protocol 27: hidden/showcase are the LAST two fields of PlayerShopListingData.
        com.enviouse.futureshops.data.PlayerShopListingData in =
                new com.enviouse.futureshops.data.PlayerShopListingData(
                        "minecraft:diamond", "MONEY", 500L, 500L, "", 1, 12,
                        com.enviouse.futureshops.data.PlayerShopPromoData.NONE,
                        false, "", true, List.of(), "gems", 1, 1, "",
                        false, "", "SELL", 0L, 0, 0,
                        /*hidden*/ true, /*showcase*/ false);
        FriendlyByteBuf b = buf();
        com.enviouse.futureshops.data.PlayerShopListingData.encode(b, in);
        com.enviouse.futureshops.data.PlayerShopListingData out =
                com.enviouse.futureshops.data.PlayerShopListingData.decode(b);
        assertTrue(out.hidden(), "hidden must round-trip as the second-to-last field");
        assertFalse(out.showcase(), "showcase must round-trip as the last field");
        assertEquals("minecraft:diamond", out.itemId());
        assertEquals(12, out.stock());
    }

    @Test
    void playerShopDataPacketRoundTripsIconAndStorages() {
        // Protocol 27: floatingIconMode/Item + the linkedStorages list are the LAST fields of
        // S2CPlayerShopDataPacket (appended after adminShopMode).
        com.enviouse.futureshops.data.PlayerShopStorageEntry entry =
                new com.enviouse.futureshops.data.PlayerShopStorageEntry(new BlockPos(1, 2, 3), "minecraft:chest", 42);
        com.enviouse.futureshops.network.packets.S2CPlayerShopDataPacket in =
                new com.enviouse.futureshops.network.packets.S2CPlayerShopDataPacket(
                        new BlockPos(0, 64, 0), true, new UUID(1L, 2L), "Owner",
                        List.of(), true, 100L, 500L, List.of("row"),
                        "Shop", false, true, "desc", "franchise", false, false,
                        "CUSTOM_ITEM", "minecraft:diamond", List.of(entry), List.of("cfg-a", "cfg-b"));
        FriendlyByteBuf b = buf();
        com.enviouse.futureshops.network.packets.S2CPlayerShopDataPacket.encode(in, b);
        com.enviouse.futureshops.network.packets.S2CPlayerShopDataPacket out =
                com.enviouse.futureshops.network.packets.S2CPlayerShopDataPacket.decode(b);
        assertEquals("CUSTOM_ITEM", out.floatingIconMode());
        assertEquals("minecraft:diamond", out.floatingIconItem());
        assertEquals(1, out.linkedStorages().size());
        assertEquals(new BlockPos(1, 2, 3), out.linkedStorages().get(0).pos());
        assertEquals("minecraft:chest", out.linkedStorages().get(0).blockId());
        assertEquals(42, out.linkedStorages().get(0).itemCount());
        assertEquals(List.of("cfg-a", "cfg-b"), out.savedConfigNames());
    }

    @Test
    void playerShopUnlinkStoragePacketRoundTrips() {
        // Protocol 27: identity-based per-storage unlink (shopPos + storagePos).
        com.enviouse.futureshops.network.packets.C2SPlayerShopUnlinkStoragePacket in =
                new com.enviouse.futureshops.network.packets.C2SPlayerShopUnlinkStoragePacket(
                        new BlockPos(5, 70, -8), new BlockPos(5, 70, -10));
        FriendlyByteBuf b = buf();
        com.enviouse.futureshops.network.packets.C2SPlayerShopUnlinkStoragePacket.encode(in, b);
        com.enviouse.futureshops.network.packets.C2SPlayerShopUnlinkStoragePacket out =
                com.enviouse.futureshops.network.packets.C2SPlayerShopUnlinkStoragePacket.decode(b);
        assertEquals(new BlockPos(5, 70, -8), out.shopPos());
        assertEquals(new BlockPos(5, 70, -10), out.storagePos());
    }

    @Test
    void playerShopSavedConfigPacketRoundTrips() {
        // Protocol 27: named saved-config op (shopPos + op + name).
        com.enviouse.futureshops.network.packets.C2SPlayerShopSavedConfigPacket in =
                new com.enviouse.futureshops.network.packets.C2SPlayerShopSavedConfigPacket(
                        new BlockPos(3, 64, 9), "SAVE", "my-layout");
        FriendlyByteBuf b = buf();
        com.enviouse.futureshops.network.packets.C2SPlayerShopSavedConfigPacket.encode(in, b);
        com.enviouse.futureshops.network.packets.C2SPlayerShopSavedConfigPacket out =
                com.enviouse.futureshops.network.packets.C2SPlayerShopSavedConfigPacket.decode(b);
        assertEquals(new BlockPos(3, 64, 9), out.shopPos());
        assertEquals("SAVE", out.op());
        assertEquals("my-layout", out.name());
    }

    @Test
    void playerShopIconPacketRoundTrips() {
        // Protocol 27: owner sets the block-top floating-icon mode (+ custom item).
        com.enviouse.futureshops.network.packets.C2SPlayerShopIconPacket in =
                new com.enviouse.futureshops.network.packets.C2SPlayerShopIconPacket(
                        new BlockPos(12, 64, -30), "CUSTOM_ITEM", "minecraft:diamond");
        FriendlyByteBuf b = buf();
        com.enviouse.futureshops.network.packets.C2SPlayerShopIconPacket.encode(in, b);
        com.enviouse.futureshops.network.packets.C2SPlayerShopIconPacket out =
                com.enviouse.futureshops.network.packets.C2SPlayerShopIconPacket.decode(b);
        assertEquals(new BlockPos(12, 64, -30), out.shopPos());
        assertEquals("CUSTOM_ITEM", out.iconMode());
        assertEquals("minecraft:diamond", out.iconItem());
    }

    // ---- protocol 30: physical-currency ATM ----

    @Test
    void atmDataPacketRoundTripsSecurityAndDenominations() {
        S2CAtmDataPacket in = new S2CAtmDataPacket(
                123_456L, "Credits", 2, "custom", false, "abc123",
                List.of(
                        new AtmDenominationData("othermod:gold_bill", 10_000L, 64),
                        new AtmDenominationData("othermod:silver_coin", 25L, 16)));
        FriendlyByteBuf b = buf();
        S2CAtmDataPacket.encode(in, b);
        S2CAtmDataPacket out = S2CAtmDataPacket.decode(b);
        assertEquals(in, out);
        assertFalse(out.protectedMinting(), "foreign currency must advertise the unprotected mode");
        assertEquals(2, out.denominations().size());
        assertEquals(0, b.readableBytes());
    }

    @Test
    void atmWithdrawPacketRoundTripsExactCounts() {
        C2SAtmWithdrawPacket in = new C2SAtmWithdrawPacket(
                "currency-signature", List.of(1, 0, 3, 12));
        FriendlyByteBuf b = buf();
        C2SAtmWithdrawPacket.encode(in, b);
        C2SAtmWithdrawPacket out = C2SAtmWithdrawPacket.decode(b);
        assertEquals(in, out);
        assertEquals(0, b.readableBytes());
    }

    @Test
    void atmWithdrawPacketRejectsOversizedDenominationList() {
        FriendlyByteBuf b = buf();
        b.writeUtf("signature", 128);
        b.writeVarInt(33);
        assertThrows(io.netty.handler.codec.DecoderException.class,
                () -> C2SAtmWithdrawPacket.decode(b));
    }

    @Test
    void atmResultPacketRoundTrips() {
        S2CAtmResultPacket in = new S2CAtmResultPacket(true, "SUCCESS", 88_800L, 11_200L);
        FriendlyByteBuf b = buf();
        S2CAtmResultPacket.encode(in, b);
        S2CAtmResultPacket out = S2CAtmResultPacket.decode(b);
        assertEquals(in, out);
        assertEquals(0, b.readableBytes());
    }
}
