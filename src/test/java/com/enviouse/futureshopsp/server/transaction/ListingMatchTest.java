package com.enviouse.futureshopsp.server.transaction;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HIGH-BLAST-RADIUS GUARD (player-data tier): the component-aware listing matcher. Two edges where
 * a silent regression would change which items match a player-created listing:
 *  1. the bare-listing / empty-patch edge (must match ONLY bare items, not everything — both directions);
 *  2. a listing migrated from a persisted 1.20.1 nbtTag must match IDENTICALLY to a fresh 1.21.1
 *     listing of the same intent (the listing-side analogue of rescued-coin ≡ fresh-coin).
 * Runs on a booted server (needs the item registry + DataFixer + registry-aware ItemStack parsing).
 */
@ExtendWith(EphemeralTestServerProvider.class)
class ListingMatchTest {

    private static ResourceLocation key() {
        return BuiltInRegistries.ITEM.getKey(Items.DIAMOND_PICKAXE);
    }

    @Test
    void emptyPatchMatchesOnlyBareItems_bothDirections(MinecraftServer server) {
        ItemStack bare = new ItemStack(Items.DIAMOND_PICKAXE);
        ItemStack damaged = new ItemStack(Items.DIAMOND_PICKAXE);
        damaged.setDamageValue(5);
        DataComponentPatch variantPatch = damaged.getComponentsPatch();

        // nbtAware bare listing (empty patch): matches ONLY the bare item, NOT the damaged one.
        assertTrue(NbtMatchUtil.matches(bare, Items.DIAMOND_PICKAXE, true, DataComponentPatch.EMPTY),
                "bare nbtAware listing must match a bare item");
        assertFalse(NbtMatchUtil.matches(damaged, Items.DIAMOND_PICKAXE, true, DataComponentPatch.EMPTY),
                "bare nbtAware listing must NOT match a component-carrying item");

        // nbtAware variant listing: matches the right variant, not the wrong one.
        assertTrue(NbtMatchUtil.matches(damaged, Items.DIAMOND_PICKAXE, true, variantPatch),
                "variant listing must match the matching variant");
        assertFalse(NbtMatchUtil.matches(bare, Items.DIAMOND_PICKAXE, true, variantPatch),
                "variant listing must NOT match the wrong variant");

        // nbtAware == false: matches ANY variant (bare or component-carrying).
        assertTrue(NbtMatchUtil.matches(damaged, Items.DIAMOND_PICKAXE, false, DataComponentPatch.EMPTY));
        assertTrue(NbtMatchUtil.matches(bare, Items.DIAMOND_PICKAXE, false, variantPatch));

        // Wrong item type never matches.
        assertFalse(NbtMatchUtil.matches(new ItemStack(Items.STICK), Items.DIAMOND_PICKAXE, false, DataComponentPatch.EMPTY));
    }

    @Test
    void migratedLegacyListingMatchesIdenticallyToFresh(MinecraftServer server) {
        var registries = server.registryAccess();

        // Old 1.20.1 nbtTag for a damaged pickaxe variant.
        CompoundTag legacyTag = new CompoundTag();
        legacyTag.putInt("Damage", 5);
        DataComponentPatch migrated = NbtMatchUtil.legacyTagToPatch(registries, key(), legacyTag);

        // Fresh 1.21.1 listing of the same intent.
        ItemStack freshVariant = new ItemStack(Items.DIAMOND_PICKAXE);
        freshVariant.setDamageValue(5);
        DataComponentPatch fresh = freshVariant.getComponentsPatch();

        // Same patch -> identical match set.
        assertEquals(fresh, migrated, "migrated legacy listing patch must equal a fresh listing patch");

        ItemStack bare = new ItemStack(Items.DIAMOND_PICKAXE);
        ItemStack damaged = new ItemStack(Items.DIAMOND_PICKAXE);
        damaged.setDamageValue(5);
        assertEquals(
                NbtMatchUtil.matches(damaged, Items.DIAMOND_PICKAXE, true, fresh),
                NbtMatchUtil.matches(damaged, Items.DIAMOND_PICKAXE, true, migrated),
                "migrated and fresh listings must agree on the matching variant");
        assertEquals(
                NbtMatchUtil.matches(bare, Items.DIAMOND_PICKAXE, true, fresh),
                NbtMatchUtil.matches(bare, Items.DIAMOND_PICKAXE, true, migrated),
                "migrated and fresh listings must agree on the non-matching item");
        assertTrue(NbtMatchUtil.matches(damaged, Items.DIAMOND_PICKAXE, true, migrated));
        assertFalse(NbtMatchUtil.matches(bare, Items.DIAMOND_PICKAXE, true, migrated));

        // A bare legacy listing (empty/absent tag) migrates to the empty patch — identical to a fresh bare listing.
        assertEquals(DataComponentPatch.EMPTY, NbtMatchUtil.legacyTagToPatch(registries, key(), new CompoundTag()));
    }
}
