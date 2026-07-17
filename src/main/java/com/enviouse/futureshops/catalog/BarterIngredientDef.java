package com.enviouse.futureshops.catalog;

/**
 * One ingredient required by a barter recipe.
 *
 * <p>{@code nbtJson} is optional SNBT loaded from the {@code "nbt"} field on ingredient objects
 * in the shop JSON (blank when absent). Non-blank means the handed-in stacks must carry this
 * exact tag to count (NBT-STRICT matching, mirroring the sell path); blank keeps the legacy
 * lenient identity match where any variant of the item qualifies.
 */
public record BarterIngredientDef(String itemId, int count, String nbtJson) {

    public BarterIngredientDef {
        nbtJson = nbtJson == null ? "" : nbtJson;
    }

    /** Legacy convenience: no NBT requirement (lenient matching). */
    public BarterIngredientDef(String itemId, int count) {
        this(itemId, count, "");
    }
}
