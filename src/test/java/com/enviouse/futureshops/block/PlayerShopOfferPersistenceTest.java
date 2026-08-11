package com.enviouse.futureshops.block;

import com.enviouse.futureshops.catalog.offer.AcquireOfferOption;
import com.enviouse.futureshops.catalog.offer.OfferLimitPolicy;
import com.enviouse.futureshops.catalog.offer.OfferSchedule;
import com.enviouse.futureshops.catalog.offer.SellOfferOption;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferListing;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerShopOfferPersistenceTest {
    private static final String SHOP_IDENTITY =
            "00000000-0000-0001-0000-000000000002";

    @Test
    void legacyMigrationPreservesModesBundlesAndBuybackShape() {
        CompoundTag legacy = legacyListing();

        ShopBlockEntity.Listing listing =
                ShopBlockEntity.Listing.load(
                        legacy, SHOP_IDENTITY, 3);
        ServerShopOfferListing offer =
                listing.normalizedOffer().orElseThrow();

        assertEquals(2, listing.offerSchemaVersion());
        assertFalse(listing.offerUnavailable());
        assertEquals("minecraft:diamond", listing.itemId());
        assertEquals(2, listing.baseQuantity());
        assertEquals(250L, listing.moneyPriceMinor());
        assertEquals(3, listing.barterItemCount());
        assertEquals(4, listing.buybackBought());
        assertEquals(4L,
                listing.legacyBuybackConsumedBaseline());
        assertEquals(2, offer.outputs().size());
        assertEquals(2, offer.acquireOptions().size());
        assertFalse(offer.acquireOptions().stream()
                .anyMatch(AcquireOfferOption::free));
        AcquireOfferOption money = offer.acquireOptions().stream()
                .filter(AcquireOfferOption::moneyCostPresent)
                .findFirst().orElseThrow();
        AcquireOfferOption barter = offer.acquireOptions().stream()
                .filter(AcquireOfferOption::hasItemCosts)
                .findFirst().orElseThrow();
        assertEquals(250L, money.moneyCostMinorUnits());
        assertEquals(3, barter.itemCosts().get(0).count());
        SellOfferOption sell = offer.sellOptions().get(0);
        assertEquals(125L, sell.moneyPayoutMinorUnits());
        assertEquals(10L, sell.capacity());
        assertEquals(1, sell.itemInputs().size());
        assertEquals("minecraft:diamond",
                sell.itemInputs().get(0).itemId());
        assertEquals(2, sell.itemInputs().get(0).count());

        CompoundTag saved = listing.save();
        assertEquals(2, saved.getInt("OfferSchemaVersion"));
        assertEquals("BOTH", saved.getString("TradeMode"));
        assertEquals(250L, saved.getLong("MoneyPriceMinor"));
        assertEquals(4, saved.getInt("BuybackBought"));
        ShopBlockEntity.Listing restored =
                ShopBlockEntity.Listing.load(
                        saved, SHOP_IDENTITY, 3);
        assertEquals(offer,
                restored.normalizedOffer().orElseThrow());
    }

    @Test
    void migrationKeepsPromoAsRuntimeOverlayInsteadOfFreezingIt() {
        CompoundTag legacy = legacyListing();
        CompoundTag promo = new CompoundTag();
        promo.putString("Type", "PERCENTAGE");
        promo.putDouble("Value", 100.0D);
        promo.putLong("Start", 0L);
        promo.putLong("End", 0L);
        legacy.put("Promo", promo);

        ShopBlockEntity.Listing listing =
                ShopBlockEntity.Listing.load(
                        legacy, SHOP_IDENTITY, 0);
        ServerShopOfferListing offer =
                listing.normalizedOffer().orElseThrow();

        assertEquals(0L, listing.effectiveUnitPriceMinor());
        assertEquals(250L, offer.acquireOptions().stream()
                .filter(AcquireOfferOption::moneyCostPresent)
                .findFirst().orElseThrow().moneyCostMinorUnits());
        assertEquals(3, offer.acquireOptions().stream()
                .filter(AcquireOfferOption::hasItemCosts)
                .findFirst().orElseThrow()
                .itemCosts().get(0).count());
        assertFalse(offer.acquireOptions().stream()
                .anyMatch(AcquireOfferOption::free));
    }

    @Test
    void migrationPreservesLegacyNbtAwarenessFlags() {
        CompoundTag legacy = legacyListing();
        legacy.remove("BundleOutputs");
        CompoundTag itemNbt = new CompoundTag();
        itemNbt.putString("variant", "listed");
        CompoundTag barterNbt = new CompoundTag();
        barterNbt.putString("variant", "payment");
        legacy.put("NbtTag", itemNbt);
        legacy.put("BarterNbtTag", barterNbt);

        ServerShopOfferListing generic =
                ShopBlockEntity.Listing.load(
                        legacy, SHOP_IDENTITY, 0)
                        .normalizedOffer().orElseThrow();
        assertEquals("", generic.outputs().get(0).exactNbt());
        assertEquals("", generic.sellOptions().get(0)
                .itemInputs().get(0).exactNbt());
        assertEquals("", generic.acquireOptions().stream()
                .filter(AcquireOfferOption::hasItemCosts)
                .findFirst().orElseThrow()
                .itemCosts().get(0).exactNbt());

        legacy.putBoolean("NbtAware", true);
        legacy.putBoolean("BarterNbtAware", true);
        ServerShopOfferListing strict =
                ShopBlockEntity.Listing.load(
                        legacy, SHOP_IDENTITY, 0)
                        .normalizedOffer().orElseThrow();
        assertEquals(itemNbt.toString(),
                strict.outputs().get(0).exactNbt());
        assertEquals(itemNbt.toString(),
                strict.sellOptions().get(0)
                        .itemInputs().get(0).exactNbt());
        assertEquals(barterNbt.toString(),
                strict.acquireOptions().stream()
                        .filter(AcquireOfferOption::hasItemCosts)
                        .findFirst().orElseThrow()
                        .itemCosts().get(0).exactNbt());
    }

    @Test
    void migratedIdsAreStableAndUniquePerListingOrdinal() {
        CompoundTag legacy = legacyListing();
        legacy.putInt("OfferSchemaVersion", 1);
        legacy.putString("ListingId", "untrusted_legacy_id");

        ShopBlockEntity.Listing first =
                ShopBlockEntity.Listing.load(
                        legacy, SHOP_IDENTITY, 0);
        ShopBlockEntity.Listing replay =
                ShopBlockEntity.Listing.load(
                        legacy, SHOP_IDENTITY, 0);
        ShopBlockEntity.Listing second =
                ShopBlockEntity.Listing.load(
                        legacy, SHOP_IDENTITY, 1);

        assertEquals(first.listingId(), replay.listingId());
        assertNotEquals(first.listingId(), second.listingId());
        assertNotEquals("untrusted_legacy_id", first.listingId());
    }

    @Test
    void legacyEditsRefreshOnlyLegacyOwnedProjection() {
        ShopBlockEntity.Listing migrated =
                ShopBlockEntity.Listing.load(
                        legacyListing(), SHOP_IDENTITY, 0);
        migrated.setMoneyPriceMinor(475L);

        assertEquals(475L,
                migrated.normalizedOffer().orElseThrow()
                        .acquireOptions().stream()
                        .filter(AcquireOfferOption::moneyCostPresent)
                        .findFirst().orElseThrow()
                        .moneyCostMinorUnits());

        ServerShopOfferListing current =
                migrated.normalizedOffer().orElseThrow();
        assertEquals(4L,
                migrated.legacyBuybackConsumedBaseline());
        AcquireOfferOption free = AcquireOfferOption.free("free");
        ServerShopOfferListing advanced =
                new ServerShopOfferListing(
                        current.listingId(), current.revision(),
                        current.displayName(), current.description(),
                        current.categoryId(), current.iconItemId(),
                        current.iconNbt(), current.active(),
                        current.expiresAtEpoch(),
                        current.permissionNode(), current.outputs(),
                        java.util.List.of(free),
                        current.sellOptions(), current.stockPolicy(),
                        OfferLimitPolicy.defaults(),
                        OfferSchedule.always(),
                        current.bundleComparisons());
        advanced = advanced.withRevision(
                com.enviouse.futureshops.catalog.offer
                        .ServerShopOfferRevision.compute(advanced));
        migrated.setNormalizedOffer(advanced);
        migrated.setMoneyPriceMinor(999L);
        migrated.promo();

        assertEquals(4, migrated.buybackBought());
        assertEquals(0L,
                migrated.legacyBuybackConsumedBaseline());
        assertEquals(advanced,
                migrated.normalizedOffer().orElseThrow());
    }

    @Test
    void unsupportedFuturePayloadIsPreservedAndUnavailable() {
        CompoundTag future = legacyListing();
        byte[] payload = {9, 8, 7, 6};
        future.putInt("OfferSchemaVersion", 9);
        future.putByteArray("NormalizedOffer", payload);

        ShopBlockEntity.Listing listing = assertDoesNotThrow(
                () -> ShopBlockEntity.Listing.load(
                        future, SHOP_IDENTITY, 0));

        assertTrue(listing.offerUnavailable());
        assertTrue(listing.normalizedOffer().isEmpty());
        assertFalse(listing.allowsSell());
        assertFalse(listing.allowsBuy());
        CompoundTag saved = listing.save();
        assertEquals(9, saved.getInt("OfferSchemaVersion"));
        assertArrayEquals(payload,
                saved.getByteArray("NormalizedOffer"));
    }

    @Test
    void malformedCurrentPayloadIsPreservedAndFailsClosed() {
        CompoundTag malformed = legacyListing();
        byte[] payload = {1, 2, 3};
        malformed.putInt("OfferSchemaVersion", 2);
        malformed.putBoolean("LegacyOfferProjection", false);
        malformed.putByteArray("NormalizedOffer", payload);

        ShopBlockEntity.Listing listing = assertDoesNotThrow(
                () -> ShopBlockEntity.Listing.load(
                        malformed, SHOP_IDENTITY, 0));

        assertTrue(listing.offerUnavailable());
        assertTrue(listing.normalizedOffer().isEmpty());
        assertFalse(listing.allowsSell());
        CompoundTag saved = listing.save();
        assertEquals(2, saved.getInt("OfferSchemaVersion"));
        assertArrayEquals(payload,
                saved.getByteArray("NormalizedOffer"));
    }

    @Test
    void malformedSchemaAndMissingPayloadFailClosed() {
        byte[] payload = {4, 3, 2, 1};
        CompoundTag malformedSchema = legacyListing();
        malformedSchema.putString("OfferSchemaVersion", "two");
        malformedSchema.putByteArray("NormalizedOffer", payload);

        ShopBlockEntity.Listing malformed = assertDoesNotThrow(
                () -> ShopBlockEntity.Listing.load(
                        malformedSchema, SHOP_IDENTITY, 0));

        assertTrue(malformed.offerUnavailable());
        assertFalse(malformed.allowsSell());
        CompoundTag preserved = malformed.save();
        assertEquals(2, preserved.getInt("OfferSchemaVersion"));
        assertArrayEquals(payload,
                preserved.getByteArray("NormalizedOffer"));

        CompoundTag missingSchema = legacyListing();
        missingSchema.putByteArray("NormalizedOffer", payload);
        ShopBlockEntity.Listing schemaLess = assertDoesNotThrow(
                () -> ShopBlockEntity.Listing.load(
                        missingSchema, SHOP_IDENTITY, 0));
        assertTrue(schemaLess.offerUnavailable());
        assertArrayEquals(payload,
                schemaLess.save().getByteArray("NormalizedOffer"));

        CompoundTag missingPayload = legacyListing();
        missingPayload.putInt("OfferSchemaVersion", 2);
        missingPayload.putBoolean("LegacyOfferProjection", false);
        missingPayload.putInt("BaseQuantity", 0);

        ShopBlockEntity.Listing missing = assertDoesNotThrow(
                () -> ShopBlockEntity.Listing.load(
                        missingPayload, SHOP_IDENTITY, 0));

        assertTrue(missing.offerUnavailable());
        assertFalse(missing.allowsSell());
    }

    @Test
    void missingLegacyProjectionPayloadIsRebuiltAndMarkedDirty() {
        CompoundTag repairable = legacyListing();
        repairable.putInt("OfferSchemaVersion", 2);
        repairable.putString("ListingId",
                "player_listing_repairable");
        repairable.putBoolean("LegacyOfferProjection", true);

        ShopBlockEntity.Listing repaired =
                ShopBlockEntity.Listing.load(
                        repairable, SHOP_IDENTITY, 0);

        assertFalse(repaired.offerUnavailable());
        assertTrue(repaired.normalizedOffer().isPresent());
        assertTrue(repaired.migratedOfferPersistence());
        assertTrue(repaired.save().contains(
                "NormalizedOffer", net.minecraft.nbt.Tag.TAG_BYTE_ARRAY));
    }

    @Test
    void persistenceCodecRejectsOversizedAndNoncanonicalData() {
        byte[] oversized = new byte[
                PlayerShopOfferPersistenceCodec.MAX_ENCODED_BYTES + 1];
        Arrays.fill(oversized, (byte) 1);

        assertThrows(IllegalArgumentException.class,
                () -> PlayerShopOfferPersistenceCodec.decode(
                        2, oversized));
        assertThrows(IllegalArgumentException.class,
                () -> PlayerShopOfferPersistenceCodec.decode(
                        2, new byte[]{1, 2, 3, 4}));
    }

    private static CompoundTag legacyListing() {
        CompoundTag tag = new CompoundTag();
        tag.putString("ItemId", "minecraft:diamond");
        tag.putString("TradeMode", "BOTH");
        tag.putLong("MoneyPriceMinor", 250L);
        tag.putString("BarterItemId", "minecraft:emerald");
        tag.putInt("BarterItemCount", 3);
        tag.putString("Department", "ores");
        tag.putString("ListingDescription", "legacy listing");
        tag.putInt("BaseQuantity", 2);
        tag.putString("Direction", "BOTH");
        tag.putLong("BuybackPriceMinor", 125L);
        tag.putInt("BuybackCap", 10);
        tag.putInt("BuybackBought", 4);
        ListTag outputs = new ListTag();
        outputs.add(new ShopBlockEntity.BundleEntry(
                "minecraft:iron_pickaxe", 1, null).save());
        outputs.add(new ShopBlockEntity.BundleEntry(
                "minecraft:iron_sword", 1, null).save());
        tag.put("BundleOutputs", outputs);
        return tag;
    }
}
