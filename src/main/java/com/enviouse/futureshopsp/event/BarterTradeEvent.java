package com.enviouse.futureshopsp.event;

import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.Event;

import java.util.List;
import java.util.UUID;

/**
 * Fired around barter trades (spec §33).
 * <p>
 * {@code Pre} is cancellable — cancelling blocks the trade.
 * {@code Post} fires after a successful barter for logging/webhooks.
 */
public abstract class BarterTradeEvent extends Event {
    private final UUID playerUUID;
    private final String shopId;
    private final String recipeId;
    private final String targetItemId;
    private final int outputQuantity;
    private final List<IngredientEntry> ingredients;

    protected BarterTradeEvent(UUID playerUUID, String shopId, String recipeId,
                               String targetItemId, int outputQuantity,
                               List<IngredientEntry> ingredients) {
        this.playerUUID = playerUUID;
        this.shopId = shopId;
        this.recipeId = recipeId;
        this.targetItemId = targetItemId;
        this.outputQuantity = outputQuantity;
        this.ingredients = List.copyOf(ingredients);
    }

    public UUID getPlayerUUID() { return playerUUID; }
    public String getShopId() { return shopId; }
    public String getRecipeId() { return recipeId; }
    public String getTargetItemId() { return targetItemId; }
    public int getOutputQuantity() { return outputQuantity; }
    public List<IngredientEntry> getIngredients() { return ingredients; }

    /**
     * Cancellable event fired before ingredients are consumed.
     * Cancel to block the trade entirely.
     */
        public static class Pre extends BarterTradeEvent implements ICancellableEvent {
        public Pre(UUID playerUUID, String shopId, String recipeId,
                   String targetItemId, int outputQuantity,
                   List<IngredientEntry> ingredients) {
            super(playerUUID, shopId, recipeId, targetItemId, outputQuantity, ingredients);
        }
    }

    /**
     * Non-cancellable event fired after a successful barter trade.
     */
    public static class Post extends BarterTradeEvent {
        public Post(UUID playerUUID, String shopId, String recipeId,
                    String targetItemId, int outputQuantity,
                    List<IngredientEntry> ingredients) {
            super(playerUUID, shopId, recipeId, targetItemId, outputQuantity, ingredients);
        }
    }

    /**
     * Immutable record describing one ingredient consumed in the trade.
     */
    public record IngredientEntry(String itemId, int count) {
    }
}

