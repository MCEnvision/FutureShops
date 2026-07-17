package com.enviouse.futureshops.catalog;

import com.enviouse.futureshops.data.CatalogBarterIngredient;
import com.enviouse.futureshops.data.CatalogBarterRecipe;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Server-side barter recipe definition loaded from config.
 *
 * <p>{@code targetItemId} holds the RAW {@code "targetItemId"} string from the shop JSON. It is
 * resolved listingId-FIRST (a listing's {@link ItemDef#resolutionKey()}), falling back to the
 * first listing whose registry {@code itemId} matches — see
 * {@link ShopCatalog#resolveBarterTarget(String, String)}. Legacy configs that target a plain
 * registry id keep working unchanged because legacy listings have {@code resolutionKey == itemId}.
 */
public record BarterRecipeDef(
        String recipeId,
        String targetItemId,
        int outputCount,
        List<BarterIngredientDef> ingredients) {

    /**
     * Converts to the wire DTO. When {@code resolvedTarget} is the listing this recipe's target
     * resolved to at catalog build, the DTO carries the listing's registry {@code itemId} (kept
     * as the icon/name fallback) and its resolution key as {@code targetListingId}; when
     * {@code null} (unresolvable target) the raw target string is passed through with a blank
     * {@code targetListingId}.
     */
    public CatalogBarterRecipe toCatalogRecipe(@Nullable ItemDef resolvedTarget) {
        return new CatalogBarterRecipe(
                recipeId,
                resolvedTarget != null ? resolvedTarget.itemId() : targetItemId,
                outputCount,
                ingredients.stream()
                        .map(ingredient -> new CatalogBarterIngredient(
                                ingredient.itemId(), ingredient.count(), ingredient.nbtJson()))
                        .toList(),
                resolvedTarget != null ? resolvedTarget.resolutionKey() : "");
    }
}
