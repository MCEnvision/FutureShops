package com.enviouse.futureshops.data;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client-side ingredient requirement for one barter recipe.
 *
 * <p>Wire order: {@code itemId} (writeUtf), {@code count} (writeVarInt), {@code nbtJson}
 * (writeUtf — appended last in protocol 25). A blank {@code nbtJson} means "no NBT
 * requirement" (lenient identity matching, the legacy behaviour); non-blank SNBT means the
 * server matches handed-in stacks NBT-strictly against this exact tag, and the client mirrors
 * that for icons, names, tooltips and owned-counts.
 */
public record CatalogBarterIngredient(String itemId, int count, String nbtJson) {

    public CatalogBarterIngredient {
        nbtJson = nbtJson == null ? "" : nbtJson;
    }

    public static void encode(FriendlyByteBuf buffer, CatalogBarterIngredient ingredient) {
        buffer.writeUtf(ingredient.itemId);
        buffer.writeVarInt(ingredient.count);
        buffer.writeUtf(ingredient.nbtJson == null ? "" : ingredient.nbtJson);
    }

    public static CatalogBarterIngredient decode(FriendlyByteBuf buffer) {
        return new CatalogBarterIngredient(buffer.readUtf(), buffer.readVarInt(), buffer.readUtf());
    }
}
