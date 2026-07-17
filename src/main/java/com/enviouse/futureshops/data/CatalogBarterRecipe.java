package com.enviouse.futureshops.data;

import net.minecraft.network.FriendlyByteBuf;

import java.util.List;

/**
 * Client-side barter recipe delivered in the shop data sync packet.
 *
 * <p>Wire order: {@code recipeId} (writeUtf), {@code targetItemId} (writeUtf),
 * {@code outputCount} (writeVarInt), {@code ingredients} (writeCollection of
 * {@link CatalogBarterIngredient}), {@code targetListingId} (writeUtf — appended last in
 * protocol 25).
 *
 * <p>{@code targetItemId} is always a REGISTRY id — kept as the icon/name fallback.
 * {@code targetListingId} is the resolved reward listing's resolution key, filled by the server
 * at catalog build (recipe targets resolve listingId-first, falling back to the first listing
 * whose registry itemId matches). Blank when the target could not be resolved to a listing —
 * clients must then fall back to registry-id lookups (the legacy behaviour).
 */
public record CatalogBarterRecipe(
        String recipeId,
        String targetItemId,
        int outputCount,
        List<CatalogBarterIngredient> ingredients,
        String targetListingId) {

    public CatalogBarterRecipe {
        targetListingId = targetListingId == null ? "" : targetListingId;
    }

    public static void encode(FriendlyByteBuf buffer, CatalogBarterRecipe recipe) {
        buffer.writeUtf(recipe.recipeId);
        buffer.writeUtf(recipe.targetItemId);
        buffer.writeVarInt(recipe.outputCount);
        buffer.writeCollection(recipe.ingredients, CatalogBarterIngredient::encode);
        buffer.writeUtf(recipe.targetListingId == null ? "" : recipe.targetListingId);
    }

    public static CatalogBarterRecipe decode(FriendlyByteBuf buffer) {
        return new CatalogBarterRecipe(
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readVarInt(),
                buffer.readList(CatalogBarterIngredient::decode),
                buffer.readUtf());
    }
}
