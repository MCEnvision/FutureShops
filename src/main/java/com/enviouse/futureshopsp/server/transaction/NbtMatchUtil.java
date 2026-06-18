package com.enviouse.futureshopsp.server.transaction;

import com.mojang.serialization.Dynamic;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Component-aware item matching (1.21.1 port of the NBT-aware matcher).
 *
 * <p>1.20.1 matched a stack against a required full {@code tag} (exact {@code CompoundTag} equality).
 * In 1.21.1 a variant is identified by its {@link DataComponentPatch} (the diff from the item's
 * default components), so:
 * <ul>
 *   <li>{@code nbtAware == false} → match any variant of the item (item type only);</li>
 *   <li>{@code nbtAware == true} → the candidate's component patch must EQUAL the required patch.
 *       A "bare" listing (old {@code null} requiredTag, which matched only no-tag items) becomes
 *       {@link DataComponentPatch#EMPTY}, which matches ONLY items with no non-default components —
 *       not "everything". This is the behaviour-preserving edge.</li>
 * </ul>
 */
public final class NbtMatchUtil {
    private static final int LEGACY_DATA_VERSION = 3465; // MC 1.20.1

    private NbtMatchUtil() {
    }

    /** @param requiredPatch the variant criterion; {@code null} is treated as {@link DataComponentPatch#EMPTY} (bare). */
    public static boolean matches(ItemStack stack, Item item, boolean nbtAware, @Nullable DataComponentPatch requiredPatch) {
        if (stack.isEmpty() || stack.getItem() != item) {
            return false;
        }
        if (!nbtAware) {
            return true;
        }
        return patchesEqual(stack.getComponentsPatch(), requiredPatch);
    }

    /** Null-safe component-patch equality. Two empty/null patches are equal (both = "no variant data"). */
    public static boolean patchesEqual(@Nullable DataComponentPatch a, @Nullable DataComponentPatch b) {
        DataComponentPatch pa = a == null ? DataComponentPatch.EMPTY : a;
        DataComponentPatch pb = b == null ? DataComponentPatch.EMPTY : b;
        return pa.equals(pb);
    }

    /**
     * Lazily migrates a persisted legacy listing {@code nbtTag} (a 1.20.1 item {@code tag} compound)
     * into the equivalent 1.21.1 {@link DataComponentPatch}, by running it through the vanilla item
     * DataFixer exactly as a world upgrade would — so a migrated listing matches identically to a
     * listing freshly created from the same item variant in 1.21.1.
     *
     * @return the migrated patch, or {@link DataComponentPatch#EMPTY} for a null/empty legacy tag
     *         (a bare listing) or an unparseable result.
     */
    public static DataComponentPatch legacyTagToPatch(HolderLookup.Provider registries,
                                                      ResourceLocation itemId,
                                                      @Nullable CompoundTag legacyTag) {
        if (legacyTag == null || legacyTag.isEmpty()) {
            return DataComponentPatch.EMPTY;
        }
        CompoundTag itemNbt = new CompoundTag();
        itemNbt.putString("id", itemId.toString());
        itemNbt.putByte("Count", (byte) 1);
        itemNbt.put("tag", legacyTag);

        int current = SharedConstants.getCurrentVersion().getDataVersion().getVersion();
        Dynamic<Tag> fixed = DataFixers.getDataFixer().update(
                References.ITEM_STACK,
                new Dynamic<>(NbtOps.INSTANCE, itemNbt),
                LEGACY_DATA_VERSION, current);

        return ItemStack.parse(registries, fixed.getValue())
                .map(ItemStack::getComponentsPatch)
                .orElse(DataComponentPatch.EMPTY);
    }
}
