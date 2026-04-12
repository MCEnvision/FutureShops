package com.enviouse.futureshops.data;

import net.minecraft.network.FriendlyByteBuf;

import java.util.List;

/** Client-side barter recipe delivered in the shop data sync packet. */
public record CatalogBarterRecipe(
        String recipeId,
        String targetItemId,
        int outputCount,
        List<CatalogBarterIngredient> ingredients) {

    public static void encode(FriendlyByteBuf buffer, CatalogBarterRecipe recipe) {
        buffer.writeUtf(recipe.recipeId);
        buffer.writeUtf(recipe.targetItemId);
        buffer.writeVarInt(recipe.outputCount);
        buffer.writeCollection(recipe.ingredients, CatalogBarterIngredient::encode);
    }

    public static CatalogBarterRecipe decode(FriendlyByteBuf buffer) {
        return new CatalogBarterRecipe(
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readVarInt(),
                buffer.readList(CatalogBarterIngredient::decode));
    }
}

