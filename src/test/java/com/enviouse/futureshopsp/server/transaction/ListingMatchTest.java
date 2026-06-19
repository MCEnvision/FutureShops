package com.enviouse.futureshopsp.server.transaction;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
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

    /**
     * PERSISTENCE GATE: the listing variant must survive the BE save→load round-trip (patchToTag→tagToPatch)
     * with identical match behaviour, and a legacy 1.20.1 nbtTag must migrate, persist as NbtPatch, and reload
     * stably. A silent serialization bug here corrupts persisted player listings on world reload.
     */
    @Test
    void persistedVariantRoundTripsAndMatchesIdentically(MinecraftServer server) {
        var registries = server.registryAccess();

        ItemStack damaged = new ItemStack(Items.DIAMOND_PICKAXE);
        damaged.setDamageValue(5);
        DataComponentPatch patch = damaged.getComponentsPatch();

        // BE save (patchToTag) -> reload (tagToPatch) must be identity.
        Tag saved = NbtMatchUtil.patchToTag(registries, patch);
        DataComponentPatch reloaded = NbtMatchUtil.tagToPatch(registries, saved);
        assertEquals(patch, reloaded, "persisted variant patch must round-trip save->load");

        ItemStack bare = new ItemStack(Items.DIAMOND_PICKAXE);
        assertTrue(NbtMatchUtil.matches(damaged, Items.DIAMOND_PICKAXE, true, reloaded));
        assertFalse(NbtMatchUtil.matches(bare, Items.DIAMOND_PICKAXE, true, reloaded));

        // Legacy listing: stored 1.20.1 NbtTag -> migrate on load -> re-saved as NbtPatch -> reloads stably,
        // and equals the fresh-variant patch (listing-side rescued==fresh).
        CompoundTag legacyTag = new CompoundTag();
        legacyTag.putInt("Damage", 5);
        DataComponentPatch migrated = NbtMatchUtil.legacyTagToPatch(registries, key(), legacyTag);
        assertEquals(patch, migrated, "migrated legacy listing patch must equal a fresh variant patch");
        DataComponentPatch afterReload = NbtMatchUtil.tagToPatch(registries, NbtMatchUtil.patchToTag(registries, migrated));
        assertEquals(migrated, afterReload, "migrated legacy listing must persist as NbtPatch and reload stably");

        // Empty (bare listing) persists and reloads as empty — not "matches everything".
        assertEquals(DataComponentPatch.EMPTY,
                NbtMatchUtil.tagToPatch(registries, NbtMatchUtil.patchToTag(registries, DataComponentPatch.EMPTY)));
    }

    /**
     * ADMIN-CONFIG MIGRATION GATE (persisted server config; same blast radius as the player-listing
     * migration): admin shop {@code nbtJson} is an SNBT string on disk. An existing 1.20.1 config holds a
     * raw item tag ({@code {Damage:5}}); a config (re)written under 1.21 holds component-patch SNBT. Both
     * must read to the same variant patch and match the same items, and a bare/blank listing must match
     * ONLY bare items (not everything). A silent regression here corrupts which items admin shops accept.
     */
    @Test
    void adminConfigVariantMigratesAndMatchesIdentically(MinecraftServer server) {
        var registries = server.registryAccess();
        ResourceLocation id = key();

        ItemStack damaged = new ItemStack(Items.DIAMOND_PICKAXE);
        damaged.setDamageValue(5);
        DataComponentPatch freshPatch = damaged.getComponentsPatch();

        // Legacy 1.20.1 admin config nbtJson (raw item-tag SNBT) and new 1.21 nbtJson (component-patch SNBT).
        DataComponentPatch fromLegacy = NbtMatchUtil.snbtToPatchMigrating(registries, id, "{Damage:5}");
        String newSnbt = NbtMatchUtil.patchToSnbt(registries, freshPatch);
        DataComponentPatch fromNew = NbtMatchUtil.snbtToPatchMigrating(registries, id, newSnbt);

        assertEquals(freshPatch, fromLegacy, "legacy admin SNBT must migrate to the fresh-variant patch");
        assertEquals(freshPatch, fromNew, "new-format admin SNBT must round-trip (not double-migrate as legacy)");

        ItemStack bare = new ItemStack(Items.DIAMOND_PICKAXE);
        for (DataComponentPatch p : new DataComponentPatch[]{fromLegacy, fromNew}) {
            assertTrue(NbtMatchUtil.matches(damaged, Items.DIAMOND_PICKAXE, true, p), "must match the damaged variant");
            assertFalse(NbtMatchUtil.matches(bare, Items.DIAMOND_PICKAXE, true, p), "must NOT match the bare item");
        }

        // Blank admin nbtJson = bare listing: matches ONLY bare, NOT a variant, NOT everything.
        DataComponentPatch empty = NbtMatchUtil.snbtToPatchMigrating(registries, id, "");
        assertEquals(DataComponentPatch.EMPTY, empty);
        assertTrue(NbtMatchUtil.matches(bare, Items.DIAMOND_PICKAXE, true, empty));
        assertFalse(NbtMatchUtil.matches(damaged, Items.DIAMOND_PICKAXE, true, empty),
                "a bare admin listing must not accept a tagged variant");
    }
}
