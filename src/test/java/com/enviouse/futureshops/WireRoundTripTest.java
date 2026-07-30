package com.enviouse.futureshops;

import com.enviouse.futureshops.catalog.AdminShopOfferConfigWriter;
import com.enviouse.futureshops.catalog.offer.AcquireOfferOption;
import com.enviouse.futureshops.catalog.offer.OfferItemComponent;
import com.enviouse.futureshops.catalog.offer.OfferLimitPolicy;
import com.enviouse.futureshops.catalog.offer.OfferSchedule;
import com.enviouse.futureshops.catalog.offer.OfferStockPolicy;
import com.enviouse.futureshops.catalog.offer.OfferValidationIssue;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferListing;
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
import com.enviouse.futureshops.data.PlayerShopNormalizedOfferData;
import com.enviouse.futureshops.data.SettlementHistoryRow;
import com.enviouse.futureshops.data.TransactionHistoryEntry;
import com.enviouse.futureshops.network.packets.C2SAdminShopAddItemsPacket;
import com.enviouse.futureshops.network.packets.C2SAdminShopEditPacket;
import com.enviouse.futureshops.network.packets.C2SAtmWithdrawPacket;
import com.enviouse.futureshops.network.packets.C2SAtmCollectCashPacket;
import com.enviouse.futureshops.network.packets.C2SAtmDepositPacket;
import com.enviouse.futureshops.network.packets.C2SAtmDepositRecoveryPacket;
import com.enviouse.futureshops.network.packets.C2SBuyRequestPacket;
import com.enviouse.futureshops.network.packets.C2SPlayerShopBuyPacket;
import com.enviouse.futureshops.network.packets.C2SPlayerShopOfferSavePacket;
import com.enviouse.futureshops.network.packets.C2SVerifyAdminCartPacket;
import com.enviouse.futureshops.network.packets.C2SVerifyCartPacket;
import com.enviouse.futureshops.network.packets.S2CAdminEditAckPacket;
import com.enviouse.futureshops.network.packets.S2CAtmDataPacket;
import com.enviouse.futureshops.network.packets.S2CAtmResultPacket;
import com.enviouse.futureshops.network.packets.S2CAtmCollectCashResultPacket;
import com.enviouse.futureshops.network.packets.S2CAtmDepositResultPacket;
import com.enviouse.futureshops.network.packets.S2CBalTopUiPacket;
import com.enviouse.futureshops.network.packets.S2CBuyResponsePacket;
import com.enviouse.futureshops.network.packets.S2CPlayerShopResultPacket;
import com.enviouse.futureshops.network.packets.S2CPlayerShopOfferSaveResultPacket;
import com.enviouse.futureshops.network.packets.S2CShopDataPacket;
import com.enviouse.futureshops.server.shop.ShopResultCode;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
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
    void adminCartCheckoutRoundTripsRequestId() {
        UUID requestId = UUID.randomUUID();
        C2SBuyRequestPacket in = new C2SBuyRequestPacket(
                "server", true,
                List.of(new C2SBuyRequestPacket.LineItem("diamond", 4)),
                "WALLET", requestId);
        FriendlyByteBuf b = buf();

        C2SBuyRequestPacket.encode(in, b);
        C2SBuyRequestPacket out = C2SBuyRequestPacket.decode(b);

        assertEquals(in, out);
        assertEquals(requestId, out.requestId());
        assertEquals(0, b.readableBytes());
    }

    @Test
    void adminCartResponseRoundTripsRequestId() {
        UUID requestId = UUID.randomUUID();
        S2CBuyResponsePacket in = new S2CBuyResponsePacket(
                true, true, "server", ShopResultCode.OK,
                9_000L, 4, 1_000L, requestId);
        FriendlyByteBuf b = buf();

        S2CBuyResponsePacket.encode(in, b);
        S2CBuyResponsePacket out = S2CBuyResponsePacket.decode(b);

        assertEquals(in, out);
        assertEquals(requestId, out.requestId());
        assertEquals(0, b.readableBytes());
    }

    @Test
    void playerShopBuyRoundTripsCheckoutCorrelation() {
        UUID requestId = UUID.randomUUID();
        C2SPlayerShopBuyPacket in = new C2SPlayerShopBuyPacket(
                new BlockPos(3, 70, 9), 5, 2,
                "MONEY", "INVENTORY", requestId, 7);
        FriendlyByteBuf b = buf();

        C2SPlayerShopBuyPacket.encode(in, b);
        C2SPlayerShopBuyPacket out = C2SPlayerShopBuyPacket.decode(b);

        assertEquals(in, out);
        assertEquals(requestId, out.requestId());
        assertEquals(7, out.responseToken());
        assertEquals(0, b.readableBytes());
    }

    @Test
    void playerShopResultRoundTripsCheckoutCorrelation() {
        UUID requestId = UUID.randomUUID();
        S2CPlayerShopResultPacket in = new S2CPlayerShopResultPacket(
                false, "SERVER_ERROR", "", requestId, 7);
        FriendlyByteBuf b = buf();

        S2CPlayerShopResultPacket.encode(in, b);
        S2CPlayerShopResultPacket out = S2CPlayerShopResultPacket.decode(b);

        assertEquals(in, out);
        assertEquals(requestId, out.requestId());
        assertEquals(7, out.responseToken());
        assertEquals(0, b.readableBytes());
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
                        "CUSTOM_ITEM", "minecraft:diamond", List.of(entry),
                        List.of("cfg-a", "cfg-b"), List.of());
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
        assertTrue(out.normalizedOffers().isEmpty());
    }

    @Test
    void playerShopNormalizedOfferSnapshotRoundTrips() {
        ServerShopOfferListing offer = normalizedPlayerShopOffer();
        PlayerShopNormalizedOfferData normalized =
                new PlayerShopNormalizedOfferData(
                        1, 3, false, Optional.of(offer));
        FriendlyByteBuf buffer = buf();

        PlayerShopNormalizedOfferData.encode(buffer, normalized);
        PlayerShopNormalizedOfferData out =
                PlayerShopNormalizedOfferData.decode(buffer);

        assertEquals(normalized, out);
        assertEquals(offer, out.offer().orElseThrow());
        assertEquals(0, buffer.readableBytes());
    }

    @Test
    void playerShopOfferSaveRequestRoundTrips() {
        UUID requestId = UUID.fromString(
                "35000000-0000-0000-0000-000000000001");
        C2SPlayerShopOfferSavePacket in =
                new C2SPlayerShopOfferSavePacket(
                        requestId, new BlockPos(4, 70, -9), 2,
                        "player_offer", 14L,
                        normalizedPlayerShopOffer());
        FriendlyByteBuf buffer = buf();

        C2SPlayerShopOfferSavePacket.encode(in, buffer);
        C2SPlayerShopOfferSavePacket out =
                C2SPlayerShopOfferSavePacket.decode(buffer);

        assertEquals(in, out);
        assertEquals(requestId, out.requestId());
        assertEquals("player_offer", out.listingId());
        assertEquals(0, buffer.readableBytes());
    }

    @Test
    void playerShopOfferSaveRequestRejectsUnboundedFields() {
        UUID requestId = UUID.fromString(
                "35000000-0000-0000-0000-000000000003");
        ServerShopOfferListing offer = normalizedPlayerShopOffer();
        assertThrows(IllegalArgumentException.class,
                () -> new C2SPlayerShopOfferSavePacket(
                        requestId, BlockPos.ZERO,
                        com.enviouse.futureshops.server.transaction
                                .ShopTransactionUtil
                                .MAX_PLAYER_SHOP_LISTING_INDEX + 1,
                        offer.listingId(), offer.revision(), offer));
        assertThrows(IllegalArgumentException.class,
                () -> new C2SPlayerShopOfferSavePacket(
                        requestId, BlockPos.ZERO, 0,
                        offer.listingId(),
                        com.enviouse.futureshops.server.escrow.runtime
                                .ServerShopOfferCommit.MAX_REVISION + 1L,
                        offer));
    }

    @Test
    void playerShopOfferSaveResultRoundTripsSnapshotAndIssues() {
        UUID requestId = UUID.fromString(
                "35000000-0000-0000-0000-000000000002");
        OfferValidationIssue issue = new OfferValidationIssue(
                OfferValidationIssue.Severity.ERROR,
                "revision", "offer.player_shop.stale");
        S2CPlayerShopOfferSaveResultPacket in =
                new S2CPlayerShopOfferSaveResultPacket(
                        requestId,
                        AdminShopOfferConfigWriter.Status.STALE,
                        false, 14L,
                        Optional.of(normalizedPlayerShopOffer()),
                        List.of(issue));
        FriendlyByteBuf buffer = buf();

        S2CPlayerShopOfferSaveResultPacket.encode(in, buffer);
        S2CPlayerShopOfferSaveResultPacket out =
                S2CPlayerShopOfferSaveResultPacket.decode(buffer);

        assertEquals(in, out);
        assertEquals(requestId, out.requestId());
        assertEquals(List.of(issue), out.issues());
        assertEquals(0, buffer.readableBytes());
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

    // Protocol 34 ATM cash claim packets.

    @Test
    void atmDataPacketRoundTripsSecurityAndDenominations() {
        S2CAtmDataPacket.DepositRecoverySummary recovery =
                new S2CAtmDataPacket.DepositRecoverySummary(
                        UUID.fromString(
                                "30000000-0000-0000-0000-000000000010"),
                        UUID.fromString(
                                "30000000-0000-0000-0000-000000000011"),
                        "RECOVERY_PENDING", 3_007L);
        S2CAtmDataPacket in = new S2CAtmDataPacket(
                123_456L, true, "Credits", 2, "custom",
                S2CAtmDataPacket.ROUTE_FOREIGN, false, "a".repeat(64),
                List.of(
                        new AtmDenominationData("othermod:gold_bill", 10_000L, 64),
                        new AtmDenominationData("othermod:silver_coin", 25L, 16)),
                true, S2CAtmDataPacket.AVAILABLE, true, 6,
                List.of(
                        new S2CAtmDataPacket.CashClaimSummary(
                                UUID.fromString(
                                        "30000000-0000-0000-0000-000000000001"),
                                "PROTECTED_CASH", 12),
                        new S2CAtmDataPacket.CashClaimSummary(
                                UUID.fromString(
                                        "30000000-0000-0000-0000-000000000002"),
                                "FOREIGN_CASH", 3)),
                Optional.of(recovery));
        FriendlyByteBuf b = buf();
        S2CAtmDataPacket.encode(in, b);
        S2CAtmDataPacket out = S2CAtmDataPacket.decode(b);
        assertEquals(in, out);
        assertFalse(out.protectedMinting(), "foreign currency must advertise the unprotected mode");
        assertEquals(S2CAtmDataPacket.ROUTE_FOREIGN, out.route());
        assertTrue(out.balanceKnown());
        assertTrue(out.serviceAvailable());
        assertTrue(out.openScreen(), "command ATM data must preserve its open intent");
        assertEquals(2, out.denominations().size());
        assertEquals(6, out.pendingCashClaimCount());
        assertEquals(2, out.collectibleCashClaims().size());
        assertEquals(Optional.of(recovery), out.depositRecovery());
        assertEquals(0, b.readableBytes());
    }

    @Test
    void atmDepositRecoveryCheckRoundTripsExactIdentity() {
        C2SAtmDepositRecoveryPacket in =
                new C2SAtmDepositRecoveryPacket(
                        UUID.fromString(
                                "30000000-0000-0000-0000-000000000020"),
                        UUID.fromString(
                                "30000000-0000-0000-0000-000000000021"));
        FriendlyByteBuf buffer = buf();

        C2SAtmDepositRecoveryPacket.encode(in, buffer);
        C2SAtmDepositRecoveryPacket out =
                C2SAtmDepositRecoveryPacket.decode(buffer);

        assertEquals(in, out);
        assertEquals(0, buffer.readableBytes());
    }

    @Test
    void atmRefreshDataPacketKeepsOpenIntentFalse() {
        S2CAtmDataPacket in = new S2CAtmDataPacket(
                500L, true, "Credits", 2, "futureshops",
                S2CAtmDataPacket.ROUTE_PROTECTED, true, "b".repeat(64),
                List.of(new AtmDenominationData(
                        "futureshops:money", 100L, 64)),
                true, S2CAtmDataPacket.AVAILABLE, false);
        FriendlyByteBuf b = buf();
        S2CAtmDataPacket.encode(in, b);
        S2CAtmDataPacket out = S2CAtmDataPacket.decode(b);
        assertEquals(in, out);
        assertFalse(out.openScreen());
        assertEquals(0, b.readableBytes());
    }

    @Test
    void atmDataPacketPreservesMalformedCashRecoverySelection() {
        UUID claimId = UUID.fromString(
                "30000000-0000-0000-0000-000000000003");
        S2CAtmDataPacket in = new S2CAtmDataPacket(
                500L, true, "Credits", 2, "futureshops",
                S2CAtmDataPacket.ROUTE_PROTECTED, true,
                "9".repeat(64),
                List.of(new AtmDenominationData(
                        "futureshops:money", 100L, 64)),
                true, S2CAtmDataPacket.AVAILABLE, false, 1,
                List.of(new S2CAtmDataPacket.CashClaimSummary(
                        claimId, "PROTECTED_CASH", 0)));
        FriendlyByteBuf buffer = buf();

        S2CAtmDataPacket.encode(in, buffer);

        S2CAtmDataPacket out = S2CAtmDataPacket.decode(buffer);
        assertEquals(in, out);
        assertEquals(0, out.collectibleCashClaims().get(0).billCount());
        assertEquals(0, buffer.readableBytes());
    }

    @Test
    void unavailableAtmDataRoundTripsWithoutInventingABalance() {
        S2CAtmDataPacket in = new S2CAtmDataPacket(
                0L, false, "Credits", 2, "futureshops",
                S2CAtmDataPacket.ROUTE_PROTECTED, true, "c".repeat(64),
                List.of(new AtmDenominationData(
                        "futureshops:money", 100L, 64)),
                false, "MIGRATION_PENDING", true);
        FriendlyByteBuf b = buf();
        S2CAtmDataPacket.encode(in, b);

        S2CAtmDataPacket out = S2CAtmDataPacket.decode(b);

        assertEquals(in, out);
        assertFalse(out.balanceKnown());
        assertFalse(out.serviceAvailable());
        assertEquals("MIGRATION_PENDING", out.availabilityCode());
        assertEquals(0, b.readableBytes());
    }

    @Test
    void atmDataPacketBoundsNestedDenominationText() {
        FriendlyByteBuf b = buf();
        b.writeLong(0L);
        b.writeBoolean(false);
        b.writeUtf("Credits", 256);
        b.writeVarInt(2);
        b.writeUtf("custom", 128);
        b.writeUtf(S2CAtmDataPacket.ROUTE_FOREIGN, 32);
        b.writeBoolean(false);
        b.writeUtf("4".repeat(64), 64);
        b.writeVarInt(1);
        b.writeUtf("x".repeat(257));

        assertThrows(io.netty.handler.codec.DecoderException.class,
                () -> S2CAtmDataPacket.decode(b));
    }

    @Test
    void atmWithdrawPacketRoundTripsExactCounts() {
        C2SAtmWithdrawPacket in = new C2SAtmWithdrawPacket(
                UUID.fromString("31000000-0000-0000-0000-000000000001"),
                "d".repeat(64), List.of(1, 0, 3, 12));
        FriendlyByteBuf b = buf();
        C2SAtmWithdrawPacket.encode(in, b);
        C2SAtmWithdrawPacket out = C2SAtmWithdrawPacket.decode(b);
        assertEquals(in, out);
        assertEquals(0, b.readableBytes());
    }

    @Test
    void atmWithdrawPacketRejectsOversizedDenominationList() {
        FriendlyByteBuf b = buf();
        b.writeUUID(UUID.fromString(
                "31000000-0000-0000-0000-000000000002"));
        b.writeUtf("e".repeat(64), 64);
        b.writeVarInt(33);
        assertThrows(io.netty.handler.codec.DecoderException.class,
                () -> C2SAtmWithdrawPacket.decode(b));
    }

    @Test
    void atmWithdrawPacketRejectsNegativeAndAggregateCounts() {
        FriendlyByteBuf negative = buf();
        negative.writeUUID(UUID.fromString(
                "31000000-0000-0000-0000-000000000003"));
        negative.writeUtf("f".repeat(64), 64);
        negative.writeVarInt(1);
        negative.writeVarInt(-1);
        assertThrows(io.netty.handler.codec.DecoderException.class,
                () -> C2SAtmWithdrawPacket.decode(negative));

        FriendlyByteBuf aggregate = buf();
        aggregate.writeUUID(UUID.fromString(
                "31000000-0000-0000-0000-000000000004"));
        aggregate.writeUtf("1".repeat(64), 64);
        aggregate.writeVarInt(2);
        aggregate.writeVarInt(4096);
        aggregate.writeVarInt(1);
        assertThrows(io.netty.handler.codec.DecoderException.class,
                () -> C2SAtmWithdrawPacket.decode(aggregate));
    }

    @Test
    void atmWithdrawPacketRejectsInvalidIdentity() {
        FriendlyByteBuf zeroId = buf();
        zeroId.writeUUID(new UUID(0L, 0L));
        zeroId.writeUtf("5".repeat(64), 64);
        zeroId.writeVarInt(1);
        zeroId.writeVarInt(1);
        assertThrows(io.netty.handler.codec.DecoderException.class,
                () -> C2SAtmWithdrawPacket.decode(zeroId));

        FriendlyByteBuf badSignature = buf();
        badSignature.writeUUID(UUID.fromString(
                "31000000-0000-0000-0000-000000000005"));
        badSignature.writeUtf("not-a-digest", 64);
        badSignature.writeVarInt(1);
        badSignature.writeVarInt(1);
        assertThrows(io.netty.handler.codec.DecoderException.class,
                () -> C2SAtmWithdrawPacket.decode(badSignature));
    }

    @Test
    void atmResultPacketRoundTrips() {
        UUID requestId = UUID.fromString(
                "32000000-0000-0000-0000-000000000001");
        S2CAtmResultPacket in = new S2CAtmResultPacket(
                requestId, "CLAIMED", false, true, true,
                88_800L, 11_200L, 0, 12, "2".repeat(64));
        FriendlyByteBuf b = buf();
        S2CAtmResultPacket.encode(in, b);
        S2CAtmResultPacket out = S2CAtmResultPacket.decode(b);
        assertEquals(in, out);
        assertEquals(requestId, out.requestId());
        assertTrue(out.success());
        assertTrue(out.replayed());
        assertEquals("CLAIMED", out.code());
        assertEquals(0, b.readableBytes());
    }

    @Test
    void atmRateLimitedResultRoundTripsItsExactRetryableShape() {
        UUID requestId = UUID.fromString(
                "32000000-0000-0000-0000-000000000006");
        S2CAtmResultPacket in = new S2CAtmResultPacket(
                requestId, "RATE_LIMITED", true, false, false,
                0L, 0L, 0, 0, "6".repeat(64), 1_501L);
        FriendlyByteBuf buffer = buf();

        S2CAtmResultPacket.encode(in, buffer);

        assertEquals(in, S2CAtmResultPacket.decode(buffer));
        assertFalse(in.success());
        assertTrue(in.retryable());
        assertEquals(1_501L, in.retryAfterMillis());
        assertEquals(0, buffer.readableBytes());
    }

    @Test
    void atmRateLimitedResultRejectsAnyNonemptyOrTerminalShape() {
        UUID requestId = UUID.fromString(
                "32000000-0000-0000-0000-000000000007");
        String signature = "7".repeat(64);

        assertThrows(IllegalArgumentException.class,
                () -> new S2CAtmResultPacket(
                        requestId, "RATE_LIMITED", false, false, false,
                        0L, 0L, 0, 0, signature, 1_000L));
        assertThrows(IllegalArgumentException.class,
                () -> new S2CAtmResultPacket(
                        requestId, "RATE_LIMITED", true, true, false,
                        0L, 0L, 0, 0, signature, 1_000L));
        assertThrows(IllegalArgumentException.class,
                () -> new S2CAtmResultPacket(
                        requestId, "RATE_LIMITED", true, false, true,
                        0L, 0L, 0, 0, signature, 1_000L));
        assertThrows(IllegalArgumentException.class,
                () -> new S2CAtmResultPacket(
                        requestId, "RATE_LIMITED", true, false, false,
                        0L, 1L, 0, 0, signature, 1_000L));
        assertThrows(IllegalArgumentException.class,
                () -> new S2CAtmResultPacket(
                        requestId, "RATE_LIMITED", true, false, false,
                        0L, 0L, 1, 0, signature, 1_000L));
        assertThrows(IllegalArgumentException.class,
                () -> new S2CAtmResultPacket(
                        requestId, "RATE_LIMITED", true, false, false,
                        0L, 0L, 0, 0, signature, 0L));
        assertThrows(IllegalArgumentException.class,
                () -> new S2CAtmResultPacket(
                        requestId, "RATE_LIMITED", true, false, false,
                        0L, 0L, 0, 0, signature,
                        S2CAtmResultPacket.MAX_RETRY_AFTER_MILLIS + 1L));
    }

    @Test
    void atmResultPacketRejectsInvalidClaimCounts() {
        FriendlyByteBuf b = buf();
        b.writeUUID(UUID.fromString(
                "32000000-0000-0000-0000-000000000002"));
        b.writeUtf("CLAIMED", 64);
        b.writeBoolean(false);
        b.writeBoolean(false);
        b.writeBoolean(true);
        b.writeLong(10_000L);
        b.writeLong(100L);
        b.writeVarInt(0);
        b.writeVarInt(4097);
        b.writeUtf("3".repeat(64), 64);

        assertThrows(io.netty.handler.codec.DecoderException.class,
                () -> S2CAtmResultPacket.decode(b));
    }

    @Test
    void atmCashCollectionRequestRoundTripsExactClaimIds() {
        UUID playerId = UUID.fromString(
                "33000000-0000-0000-0000-000000000002");
        List<UUID> claimIds = List.of(
                UUID.fromString(
                        "33000000-0000-0000-0000-000000000011"),
                UUID.fromString(
                        "33000000-0000-0000-0000-000000000012"));
        C2SAtmCollectCashPacket in = new C2SAtmCollectCashPacket(
                C2SAtmCollectCashPacket.deriveRequestId(
                        playerId, claimIds), claimIds);
        FriendlyByteBuf b = buf();
        C2SAtmCollectCashPacket.encode(in, b);

        assertEquals(in, C2SAtmCollectCashPacket.decode(b));
        assertEquals(0, b.readableBytes());
    }

    @Test
    void atmCashCollectionRequestRejectsDuplicatesAndOversizedLists() {
        UUID claim = UUID.fromString(
                "33000000-0000-0000-0000-000000000021");
        assertThrows(IllegalArgumentException.class, () ->
                new C2SAtmCollectCashPacket(UUID.randomUUID(),
                        List.of(claim, claim)));
        assertThrows(IllegalArgumentException.class, () ->
                new C2SAtmCollectCashPacket(UUID.randomUUID(),
                        List.of(UUID.randomUUID(), UUID.randomUUID(),
                                UUID.randomUUID(), UUID.randomUUID(),
                                UUID.randomUUID())));
    }

    @Test
    void atmCashCollectionResultRoundTripsCorrelationAndCounts() {
        UUID quarantined = UUID.fromString(
                "33000000-0000-0000-0000-000000000032");
        S2CAtmCollectCashResultPacket in =
                new S2CAtmCollectCashResultPacket(
                        UUID.fromString(
                                "33000000-0000-0000-0000-000000000031"),
                        "PARTIALLY_DELIVERED", false, true,
                        14, 5, List.of(quarantined));
        FriendlyByteBuf b = buf();
        S2CAtmCollectCashResultPacket.encode(in, b);

        assertEquals(in, S2CAtmCollectCashResultPacket.decode(b));
        assertEquals(List.of(quarantined), in.quarantinedClaimIds());
        assertEquals(1, in.quarantinedClaimCount());
        assertEquals(0, b.readableBytes());
    }

    @Test
    void atmPartialCashCollectionWithoutQuarantineRemainsRetryable() {
        S2CAtmCollectCashResultPacket in =
                new S2CAtmCollectCashResultPacket(
                        UUID.fromString(
                                "33000000-0000-0000-0000-000000000035"),
                        "PARTIALLY_DELIVERED", true, true,
                        8, 2, List.of());
        FriendlyByteBuf b = buf();
        S2CAtmCollectCashResultPacket.encode(in, b);

        assertEquals(in, S2CAtmCollectCashResultPacket.decode(b));
        assertTrue(in.retryable());
        assertEquals(0, b.readableBytes());
    }

    @Test
    void atmCashCollectionRateLimitRoundTripsBoundedRetryDelay() {
        S2CAtmCollectCashResultPacket in =
                new S2CAtmCollectCashResultPacket(
                        UUID.fromString(
                                "33000000-0000-0000-0000-000000000036"),
                        "RATE_LIMITED", true, false,
                        0, 7, List.of(), 1_501L);
        FriendlyByteBuf buffer = buf();

        S2CAtmCollectCashResultPacket.encode(in, buffer);

        assertEquals(in,
                S2CAtmCollectCashResultPacket.decode(buffer));
        assertEquals(1_501L, in.retryAfterMillis());
        assertEquals(0, buffer.readableBytes());
    }

    @Test
    void atmCashCollectionRateLimitRejectsInexactRetryShapes() {
        UUID request = UUID.fromString(
                "33000000-0000-0000-0000-000000000037");

        assertThrows(IllegalArgumentException.class, () ->
                new S2CAtmCollectCashResultPacket(
                        request, "RATE_LIMITED", true, false,
                        0, 1, List.of(), 0L));
        assertThrows(IllegalArgumentException.class, () ->
                new S2CAtmCollectCashResultPacket(
                        request, "RATE_LIMITED", true, false,
                        0, 1, List.of(),
                        S2CAtmCollectCashResultPacket
                                .MAX_RETRY_AFTER_MILLIS + 1L));
        assertThrows(IllegalArgumentException.class, () ->
                new S2CAtmCollectCashResultPacket(
                        request, "RETRYABLE", true, false,
                        0, 1, List.of(), 1L));
    }

    @Test
    void atmCashCollectionDecoderRejectsUnboundedRetryDelay() {
        FriendlyByteBuf buffer = buf();
        buffer.writeUUID(UUID.fromString(
                "33000000-0000-0000-0000-000000000038"));
        buffer.writeUtf("RATE_LIMITED", 32);
        buffer.writeBoolean(true);
        buffer.writeBoolean(false);
        buffer.writeVarInt(0);
        buffer.writeVarInt(1);
        buffer.writeVarInt(0);
        buffer.writeVarLong(S2CAtmCollectCashResultPacket
                .MAX_RETRY_AFTER_MILLIS + 1L);

        assertThrows(io.netty.handler.codec.DecoderException.class,
                () -> S2CAtmCollectCashResultPacket.decode(buffer));
    }

    @Test
    void atmCashCollectionResultRejectsUnboundedOrDuplicateRecoveryHandles() {
        UUID request = UUID.fromString(
                "33000000-0000-0000-0000-000000000041");
        UUID claim = UUID.fromString(
                "33000000-0000-0000-0000-000000000042");
        assertThrows(IllegalArgumentException.class, () ->
                new S2CAtmCollectCashResultPacket(request,
                        "MANUAL_REVIEW", false, false, 0, 0,
                        List.of(claim, claim)));
        assertThrows(IllegalArgumentException.class, () ->
                new S2CAtmCollectCashResultPacket(request,
                        "MANUAL_REVIEW", false, false, 0, 0,
                        List.of(UUID.randomUUID(), UUID.randomUUID(),
                                UUID.randomUUID(), UUID.randomUUID(),
                                UUID.randomUUID())));
    }

    @Test
    void atmDepositRequestRoundTripsExactSourceAndOptionalAmount() {
        UUID requestId = UUID.fromString(
                "34000000-0000-0000-0000-000000000001");
        C2SAtmDepositPacket exact = new C2SAtmDepositPacket(
                requestId, "a".repeat(64),
                C2SAtmDepositPacket.Source.MAIN_HAND,
                OptionalLong.of(12_500L));
        FriendlyByteBuf exactBuffer = buf();
        C2SAtmDepositPacket.encode(exact, exactBuffer);
        assertEquals(exact, C2SAtmDepositPacket.decode(exactBuffer));
        assertEquals(0, exactBuffer.readableBytes());

        C2SAtmDepositPacket all = new C2SAtmDepositPacket(
                UUID.fromString(
                        "34000000-0000-0000-0000-000000000002"),
                "b".repeat(64),
                C2SAtmDepositPacket.Source.OFF_HAND,
                OptionalLong.empty());
        FriendlyByteBuf allBuffer = buf();
        C2SAtmDepositPacket.encode(all, allBuffer);
        assertEquals(all, C2SAtmDepositPacket.decode(allBuffer));
        assertEquals(0, allBuffer.readableBytes());
    }

    @Test
    void atmDepositRequestRejectsZeroAmountAndUnknownSource() {
        assertThrows(IllegalArgumentException.class, () ->
                new C2SAtmDepositPacket(UUID.randomUUID(),
                        "a".repeat(64),
                        C2SAtmDepositPacket.Source.INVENTORY,
                        OptionalLong.of(0L)));
        FriendlyByteBuf buffer = buf();
        buffer.writeUUID(UUID.randomUUID());
        buffer.writeUtf("a".repeat(64), 64);
        buffer.writeUtf("HOTBAR", 16);
        buffer.writeBoolean(false);
        assertThrows(io.netty.handler.codec.DecoderException.class,
                () -> C2SAtmDepositPacket.decode(buffer));

        FriendlyByteBuf badSignature = buf();
        badSignature.writeUUID(UUID.randomUUID());
        badSignature.writeUtf("not-a-signature", 64);
        badSignature.writeUtf("INVENTORY", 16);
        badSignature.writeBoolean(false);
        assertThrows(io.netty.handler.codec.DecoderException.class,
                () -> C2SAtmDepositPacket.decode(badSignature));

        FriendlyByteBuf oldWireShape = buf();
        oldWireShape.writeUUID(UUID.randomUUID());
        oldWireShape.writeUtf("INVENTORY", 16);
        oldWireShape.writeBoolean(false);
        assertThrows(io.netty.handler.codec.DecoderException.class,
                () -> C2SAtmDepositPacket.decode(oldWireShape));
    }

    @Test
    void atmDepositSuccessRoundTripsSafeSettlementFacts() {
        UUID requestId = UUID.fromString(
                "34000000-0000-0000-0000-000000000011");
        UUID transactionId = UUID.fromString(
                "34000000-0000-0000-0000-000000000012");
        S2CAtmDepositResultPacket in =
                new S2CAtmDepositResultPacket(
                        requestId, "SUCCESS", false, false,
                        Optional.of(transactionId), 1_000L, 4,
                        750L, 250L, true, 9_750L, true,
                        Optional.empty(), 0L);
        FriendlyByteBuf buffer = buf();

        S2CAtmDepositResultPacket.encode(in, buffer);

        assertEquals(in, S2CAtmDepositResultPacket.decode(buffer));
        assertTrue(in.success());
        assertEquals(0, buffer.readableBytes());
    }

    @Test
    void atmDepositConfigurationChangeAllowsPreflightOrDurableIdentity() {
        UUID requestId = UUID.fromString(
                "34000000-0000-0000-0000-000000000009");
        UUID transactionId = UUID.fromString(
                "34000000-0000-0000-0000-000000000010");
        for (Optional<UUID> transaction : List.of(
                Optional.<UUID>empty(), Optional.of(transactionId))) {
            S2CAtmDepositResultPacket input =
                    new S2CAtmDepositResultPacket(
                            requestId, "CONFIG_CHANGED", false, false,
                            transaction, 0L, 0, 0L, 0L,
                            false, 0L, false, Optional.empty(), 0L);
            FriendlyByteBuf buffer = buf();

            S2CAtmDepositResultPacket.encode(input, buffer);

            assertEquals(input,
                    S2CAtmDepositResultPacket.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        }
    }

    @Test
    void atmDepositRefundReportsExactReturnedInventoryValue() {
        UUID requestId = UUID.fromString(
                "34000000-0000-0000-0000-000000000013");
        UUID transactionId = UUID.fromString(
                "34000000-0000-0000-0000-000000000014");
        S2CAtmDepositResultPacket input =
                new S2CAtmDepositResultPacket(
                        requestId, "REFUNDED", false, false,
                        Optional.of(transactionId),
                        0L, 0, 0L, 0L,
                        1_250L, "ORIGINAL_INVENTORY",
                        false, 0L, false, Optional.empty(), 0L);
        FriendlyByteBuf buffer = buf();

        S2CAtmDepositResultPacket.encode(input, buffer);

        assertEquals(input,
                S2CAtmDepositResultPacket.decode(buffer));
        assertEquals(1_250L, input.returnedMinorUnits());
        assertEquals("ORIGINAL_INVENTORY", input.refundDestination());
        assertEquals(0, buffer.readableBytes());
    }

    @Test
    void atmDepositLegacySummaryOmitsUnsafeBillPayload() {
        S2CAtmDepositResultPacket in =
                new S2CAtmDepositResultPacket(
                        UUID.fromString(
                                "34000000-0000-0000-0000-000000000021"),
                        "LEGACY_MIGRATION_REQUIRED", false, false,
                        Optional.empty(), 0L, 0, 0L, 0L,
                        false, 0L, false,
                        Optional.of(new S2CAtmDepositResultPacket
                                .LegacyMigrationSummary(5_000L, 5, 2)),
                        0L);
        FriendlyByteBuf buffer = buf();

        S2CAtmDepositResultPacket.encode(in, buffer);

        assertEquals(in, S2CAtmDepositResultPacket.decode(buffer));
        assertEquals(2, in.legacyMigration().orElseThrow().entryCount());
        assertEquals(0, buffer.readableBytes());

    }

    @Test
    void atmDepositRateLimitRequiresExactBoundedRetryShape() {
        UUID requestId = UUID.fromString(
                "34000000-0000-0000-0000-000000000031");
        S2CAtmDepositResultPacket rateLimited =
                new S2CAtmDepositResultPacket(
                        requestId, "RATE_LIMITED", true, false,
                        Optional.empty(), 0L, 0, 0L, 0L,
                        false, 0L, false, Optional.empty(), 1_500L);
        FriendlyByteBuf buffer = buf();
        S2CAtmDepositResultPacket.encode(rateLimited, buffer);
        assertEquals(rateLimited,
                S2CAtmDepositResultPacket.decode(buffer));
        assertEquals(0, buffer.readableBytes());

        assertThrows(IllegalArgumentException.class, () ->
                new S2CAtmDepositResultPacket(
                        requestId, "RATE_LIMITED", true, false,
                        Optional.empty(), 0L, 0, 0L, 0L,
                        false, 0L, false, Optional.empty(), 0L));
    }

    @Test
    void atmDepositReplayIsOnlyValidForTerminalSuccess() {
        UUID requestId = UUID.fromString(
                "34000000-0000-0000-0000-000000000041");
        UUID transactionId = UUID.fromString(
                "34000000-0000-0000-0000-000000000042");
        S2CAtmDepositResultPacket replayed =
                new S2CAtmDepositResultPacket(
                        requestId, "SUCCESS", false, true,
                        Optional.of(transactionId), 500L, 1,
                        500L, 0L, true, 2_500L, false,
                        Optional.empty(), 0L);
        FriendlyByteBuf buffer = buf();
        S2CAtmDepositResultPacket.encode(replayed, buffer);
        assertEquals(replayed,
                S2CAtmDepositResultPacket.decode(buffer));
        assertEquals(0, buffer.readableBytes());

        assertThrows(IllegalArgumentException.class, () ->
                new S2CAtmDepositResultPacket(
                        requestId, "REQUEST_CONFLICT", false, true,
                        Optional.of(transactionId), 0L, 0, 0L, 0L,
                        false, 0L, false, Optional.empty(), 0L));

        S2CAtmDepositResultPacket conflict =
                new S2CAtmDepositResultPacket(
                        requestId, "REQUEST_CONFLICT", false, false,
                        Optional.of(transactionId), 0L, 0, 0L, 0L,
                        false, 0L, false, Optional.empty(), 0L);
        FriendlyByteBuf conflictBuffer = buf();
        S2CAtmDepositResultPacket.encode(conflict, conflictBuffer);
        assertEquals(conflict,
                S2CAtmDepositResultPacket.decode(conflictBuffer));
        assertEquals(0, conflictBuffer.readableBytes());

        S2CAtmDepositResultPacket cancelled =
                new S2CAtmDepositResultPacket(
                        requestId, "CANCELLED", false, false,
                        Optional.of(transactionId), 0L, 0, 0L, 0L,
                        false, 0L, false, Optional.empty(), 0L);
        FriendlyByteBuf cancelledBuffer = buf();
        S2CAtmDepositResultPacket.encode(cancelled, cancelledBuffer);
        assertEquals(cancelled,
                S2CAtmDepositResultPacket.decode(cancelledBuffer));
        assertEquals(0, cancelledBuffer.readableBytes());

        assertThrows(IllegalArgumentException.class, () ->
                new S2CAtmDepositResultPacket(
                        requestId, "CANCELLED", false, false,
                        Optional.of(transactionId), 0L, 0, 0L, 0L,
                        true, 500L, false, Optional.empty(), 0L));
    }

    private static ServerShopOfferListing normalizedPlayerShopOffer() {
        OfferItemComponent output = new OfferItemComponent(
                "output", "minecraft:diamond", 2, "");
        AcquireOfferOption free =
                AcquireOfferOption.free("free");
        return new ServerShopOfferListing(
                "player_offer", 14L, "Player Offer", "",
                "all", "minecraft:diamond", "", true,
                0L, "", List.of(output), List.of(free),
                List.of(), OfferStockPolicy.unlimited(),
                OfferLimitPolicy.defaults(), OfferSchedule.always(),
                List.of());
    }
}
