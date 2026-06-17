package com.enviouse.futureshopsp.catalog;

import com.enviouse.futureshopsp.data.CatalogBarterIngredient;
import com.enviouse.futureshopsp.data.CatalogBarterRecipe;

import java.util.List;

/** Server-side barter recipe definition loaded from config. */
public record BarterRecipeDef(
        String recipeId,
        String targetItemId,
        int outputCount,
        List<BarterIngredientDef> ingredients) {

    public CatalogBarterRecipe toCatalogRecipe() {
        return new CatalogBarterRecipe(
                recipeId,
                targetItemId,
                outputCount,
                ingredients.stream()
                        .map(ingredient -> new CatalogBarterIngredient(ingredient.itemId(), ingredient.count()))
                        .toList());
    }
}

