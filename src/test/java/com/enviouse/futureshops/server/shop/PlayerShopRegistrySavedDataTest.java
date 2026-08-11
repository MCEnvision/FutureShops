package com.enviouse.futureshops.server.shop;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerShopRegistrySavedDataTest {
    private static final ResourceLocation OVERWORLD =
            ResourceLocation.parse("minecraft:overworld");
    private static final ResourceLocation NETHER =
            ResourceLocation.parse("minecraft:the_nether");

    @Test
    void equalCoordinatesInDifferentDimensionsRemainDistinct() {
        PlayerShopRegistrySavedData data = new PlayerShopRegistrySavedData();
        UUID owner = id(1);
        long position = new BlockPos(10, 64, 20).asLong();
        PlayerShopRegistrySavedData.ShopRef overworld =
                data.register(owner, OVERWORLD, position);
        PlayerShopRegistrySavedData.ShopRef nether =
                data.register(owner, NETHER, position);

        assertNotEquals(overworld.shopId(), nether.shopId());
        assertEquals(2, data.getAllShops().size());
        assertEquals(2, data.getOwnedShops(owner).size());
    }

    @Test
    void normalRegistrationRejectsOwnerMismatchAndTransferUsesCompareAndSet() {
        PlayerShopRegistrySavedData data = new PlayerShopRegistrySavedData();
        long position = new BlockPos(1, 2, 3).asLong();
        PlayerShopRegistrySavedData.ShopRef original =
                data.register(id(1), OVERWORLD, position);

        assertThrows(IllegalStateException.class,
                () -> data.register(id(2), OVERWORLD, position));
        assertThrows(IllegalStateException.class,
                () -> data.transferOwnership(
                        original.shopId(), id(9), 0L, id(2)));
        assertThrows(IllegalStateException.class,
                () -> data.transferOwnership(
                        original.shopId(), id(1), 1L, id(2)));

        PlayerShopRegistrySavedData.ShopRef transferred =
                data.transferOwnership(original.shopId(), id(1), 0L, id(2));

        assertEquals(original.shopId(), transferred.shopId());
        assertEquals(1L, transferred.revision());
        assertTrue(data.getOwnedShops(id(1)).isEmpty());
        assertEquals(transferred, data.getOwnedShops(id(2)).get(0));
        assertEquals(id(2), data.get(transferred.shopId()).orElseThrow().owner());
        assertThrows(IllegalStateException.class,
                () -> data.transferOwnership(
                        original.shopId(), id(1), 0L, id(3)));
    }

    @Test
    void tombstoneReactivationRetainsIdentityAcrossRelocation() {
        PlayerShopRegistrySavedData data = new PlayerShopRegistrySavedData();
        UUID owner = id(1);
        long oldPosition = new BlockPos(4, 70, 9).asLong();
        long newPosition = new BlockPos(40, 71, 90).asLong();
        PlayerShopRegistrySavedData.ShopRef original =
                data.register(owner, OVERWORLD, oldPosition);

        PlayerShopRegistrySavedData.ShopRecord tombstone = data.tombstone(
                original.shopId(), owner, original.revision(),
                OVERWORLD, oldPosition);

        assertFalse(tombstone.active());
        assertTrue(data.get(OVERWORLD, oldPosition).isEmpty());
        assertEquals(1, data.getAllRecords().size());

        PlayerShopRegistrySavedData.ShopRef relocated = data.reconcile(
                owner, Optional.of(original.shopId()), original.revision(),
                NETHER, newPosition);

        assertEquals(original.shopId(), relocated.shopId());
        assertEquals(1L, relocated.revision());
        assertTrue(data.get(NETHER, newPosition).orElseThrow().active());
        assertTrue(data.get(OVERWORLD, oldPosition).isEmpty());
    }

    @Test
    void blockEvidenceReconstructsARegistryRollback() {
        PlayerShopRegistrySavedData data = new PlayerShopRegistrySavedData();
        UUID stableId = id(40);
        long position = new BlockPos(5, 80, 6).asLong();

        PlayerShopRegistrySavedData.ShopRef reconstructed = data.reconcile(
                id(1), Optional.of(stableId), 7L, OVERWORLD, position);

        assertEquals(stableId, reconstructed.shopId());
        assertEquals(7L, reconstructed.revision());
        assertEquals(stableId,
                data.get(OVERWORLD, position).orElseThrow().shopId());

        CompoundTag saved = data.save(new CompoundTag());
        PlayerShopRegistrySavedData restored =
                PlayerShopRegistrySavedData.load(saved);
        assertEquals(reconstructed,
                restored.get(OVERWORLD, position).orElseThrow().asRef());
    }

    @Test
    void inactiveTombstoneSurvivesRoundTripAndDoesNotOccupyLocation() {
        PlayerShopRegistrySavedData data = new PlayerShopRegistrySavedData();
        long position = new BlockPos(7, 64, 8).asLong();
        PlayerShopRegistrySavedData.ShopRef created =
                data.register(id(1), OVERWORLD, position);
        data.tombstone(created.shopId(), id(1), created.revision(),
                OVERWORLD, position);

        PlayerShopRegistrySavedData restored = PlayerShopRegistrySavedData.load(
                data.save(new CompoundTag()));

        assertFalse(restored.get(created.shopId()).orElseThrow().active());
        assertTrue(restored.get(OVERWORLD, position).isEmpty());
        PlayerShopRegistrySavedData.ShopRef replacement =
                restored.register(id(2), OVERWORLD, position);
        assertNotEquals(created.shopId(), replacement.shopId());
        assertEquals(2, restored.getAllRecords().size());
    }

    @Test
    void duplicateLiveIdentitiesAndLocationsFailClosed() {
        long firstPosition = new BlockPos(1, 64, 1).asLong();
        long secondPosition = new BlockPos(2, 64, 2).asLong();
        CompoundTag duplicateId = schemaTwo(id(1),
                shop(id(10), "minecraft:overworld", firstPosition, 0L, true),
                shop(id(10), "minecraft:overworld", secondPosition, 0L, true));
        CompoundTag duplicateLocation = schemaTwo(id(1),
                shop(id(10), "minecraft:overworld", firstPosition, 0L, true),
                shop(id(11), "minecraft:overworld", firstPosition, 0L, true));

        assertThrows(IllegalArgumentException.class,
                () -> PlayerShopRegistrySavedData.load(duplicateId));
        assertThrows(IllegalArgumentException.class,
                () -> PlayerShopRegistrySavedData.load(duplicateLocation));
    }

    @Test
    void revisionOverflowLeavesOwnershipAndTombstoneStateUnchanged() {
        PlayerShopRegistrySavedData data = new PlayerShopRegistrySavedData();
        UUID stableId = id(50);
        long position = new BlockPos(3, 70, 4).asLong();
        data.reconcile(id(1), Optional.of(stableId), Long.MAX_VALUE,
                OVERWORLD, position);

        assertThrows(IllegalStateException.class,
                () -> data.transferOwnership(
                        stableId, id(1), Long.MAX_VALUE, id(2)));
        assertEquals(id(1), data.get(stableId).orElseThrow().owner());
        data.tombstone(stableId, id(1), Long.MAX_VALUE,
                OVERWORLD, position);

        assertThrows(IllegalStateException.class,
                () -> data.reconcile(id(1), Optional.of(stableId),
                        Long.MAX_VALUE, NETHER, position));
        assertFalse(data.get(stableId).orElseThrow().active());
        assertTrue(data.get(NETHER, position).isEmpty());
    }

    @Test
    void schemaZeroAndOneMigrationAreDeterministicAndDirty() {
        long position = new BlockPos(4, 70, 9).asLong();
        CompoundTag schemaZero = legacy(null, id(1),
                "minecraft:overworld", position);
        CompoundTag schemaOne = legacy(1, id(1),
                "minecraft:overworld", position);

        PlayerShopRegistrySavedData zero =
                PlayerShopRegistrySavedData.load(schemaZero);
        PlayerShopRegistrySavedData one =
                PlayerShopRegistrySavedData.load(schemaOne);

        assertTrue(zero.isDirty());
        assertTrue(one.isDirty());
        assertEquals(zero.getOwnedShops(id(1)).get(0).shopId(),
                one.getOwnedShops(id(1)).get(0).shopId());
        CompoundTag saved = zero.save(new CompoundTag());
        assertEquals(2, saved.getInt("schemaVersion"));
        assertTrue(saved.getList("owners", 10)
                .getCompound(0).getList("shops", 10)
                .getCompound(0).getBoolean("active"));
    }

    @Test
    void malformedListsDimensionsAndBoundsFailClosed() {
        CompoundTag wrongOwnerElements = new CompoundTag();
        wrongOwnerElements.putInt("schemaVersion", 2);
        ListTag owners = new ListTag();
        owners.add(StringTag.valueOf("bad"));
        wrongOwnerElements.put("owners", owners);

        CompoundTag wrongShopElements = new CompoundTag();
        wrongShopElements.putInt("schemaVersion", 2);
        ListTag validOwners = new ListTag();
        CompoundTag owner = new CompoundTag();
        owner.putUUID("owner", id(1));
        ListTag shops = new ListTag();
        shops.add(StringTag.valueOf("bad"));
        owner.put("shops", shops);
        validOwners.add(owner);
        wrongShopElements.put("owners", validOwners);

        String oversizedDimension = "a:" + "x".repeat(
                PlayerShopRegistrySavedData.MAXIMUM_DIMENSION_ID_LENGTH);
        CompoundTag oversized = schemaTwo(id(1),
                shop(id(10), oversizedDimension, 1L, 0L, true));

        CompoundTag tooManyForOwner = new CompoundTag();
        tooManyForOwner.putInt("schemaVersion", 2);
        ListTag boundedOwners = new ListTag();
        CompoundTag boundedOwner = new CompoundTag();
        boundedOwner.putUUID("owner", id(1));
        ListTag boundedShops = new ListTag();
        for (int index = 0;
             index <= PlayerShopRegistrySavedData.MAXIMUM_SHOPS_PER_OWNER;
             index++) {
            boundedShops.add(shop(id(100L + index),
                    "minecraft:overworld", index, 0L, false));
        }
        boundedOwner.put("shops", boundedShops);
        boundedOwners.add(boundedOwner);
        tooManyForOwner.put("owners", boundedOwners);

        assertThrows(IllegalArgumentException.class,
                () -> PlayerShopRegistrySavedData.load(wrongOwnerElements));
        assertThrows(IllegalArgumentException.class,
                () -> PlayerShopRegistrySavedData.load(wrongShopElements));
        assertThrows(IllegalArgumentException.class,
                () -> PlayerShopRegistrySavedData.load(oversized));
        assertThrows(IllegalArgumentException.class,
                () -> PlayerShopRegistrySavedData.load(tooManyForOwner));
    }

    @Test
    void malformedAndFutureSchemasFailClosed() {
        CompoundTag future = new CompoundTag();
        future.putInt("schemaVersion", 3);
        future.put("owners", new ListTag());
        CompoundTag wrongType = new CompoundTag();
        wrongType.putString("schemaVersion", "two");
        wrongType.put("owners", new ListTag());
        CompoundTag missingOwners = new CompoundTag();
        missingOwners.putInt("schemaVersion", 2);
        CompoundTag wrongActiveType = schemaTwo(id(1),
                shop(id(10), "minecraft:overworld", 1L, 0L, true));
        wrongActiveType.getList("owners", 10).getCompound(0)
                .getList("shops", 10).getCompound(0)
                .putString("active", "yes");

        assertThrows(IllegalArgumentException.class,
                () -> PlayerShopRegistrySavedData.load(future));
        assertThrows(IllegalArgumentException.class,
                () -> PlayerShopRegistrySavedData.load(wrongType));
        assertThrows(IllegalArgumentException.class,
                () -> PlayerShopRegistrySavedData.load(missingOwners));
        assertThrows(IllegalArgumentException.class,
                () -> PlayerShopRegistrySavedData.load(wrongActiveType));
    }

    private static CompoundTag legacy(Integer version, UUID ownerId,
                                      String dimension, long position) {
        CompoundTag root = new CompoundTag();
        if (version != null) {
            root.putInt("schemaVersion", version);
        }
        ListTag owners = new ListTag();
        CompoundTag owner = new CompoundTag();
        owner.putUUID("owner", ownerId);
        ListTag shops = new ListTag();
        CompoundTag shop = new CompoundTag();
        shop.putString("dimension", dimension);
        shop.putLong("pos", position);
        shops.add(shop);
        owner.put("shops", shops);
        owners.add(owner);
        root.put("owners", owners);
        return root;
    }

    private static CompoundTag schemaTwo(UUID ownerId, CompoundTag... entries) {
        CompoundTag root = new CompoundTag();
        root.putInt("schemaVersion", 2);
        ListTag owners = new ListTag();
        CompoundTag owner = new CompoundTag();
        owner.putUUID("owner", ownerId);
        ListTag shops = new ListTag();
        for (CompoundTag entry : entries) {
            shops.add(entry);
        }
        owner.put("shops", shops);
        owners.add(owner);
        root.put("owners", owners);
        return root;
    }

    private static CompoundTag shop(UUID shopId, String dimension, long position,
                                    long revision, boolean active) {
        CompoundTag shop = new CompoundTag();
        shop.putUUID("shop_id", shopId);
        shop.putString("dimension", dimension);
        shop.putLong("pos", position);
        shop.putLong("revision", revision);
        shop.putBoolean("active", active);
        return shop;
    }

    private static UUID id(long value) {
        return new UUID(0L, value);
    }
}
