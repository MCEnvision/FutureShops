package com.enviouse.futureshops.data;

import net.minecraft.network.FriendlyByteBuf;

/** Client-side ingredient requirement for one barter recipe. */
public record CatalogBarterIngredient(String itemId, int count) {
    public static void encode(FriendlyByteBuf buffer, CatalogBarterIngredient ingredient) {
        buffer.writeUtf(ingredient.itemId);
        buffer.writeVarInt(ingredient.count);
    }

    public static CatalogBarterIngredient decode(FriendlyByteBuf buffer) {
        return new CatalogBarterIngredient(buffer.readUtf(), buffer.readVarInt());
    }
}

