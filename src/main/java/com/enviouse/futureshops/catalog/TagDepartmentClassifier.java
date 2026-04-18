package com.enviouse.futureshops.catalog;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * @deprecated Replaced by admin-driven categories via {@code AdminCategorySavedData}
 * and the {@code /shopadmin category add/remove/list} commands.
 * Player-shop departments use {@code DepartmentSavedData} and {@code DepartmentPickerScreen}.
 * This class is retained for backward compatibility but is no longer used for auto-classification
 * in the admin catalog. It can still be called for diagnostic/migration purposes.
 *
 * <p>Previously: Item 15: Automatic department classification using Forge item tags and mod namespaces.
 */
@Deprecated
public final class TagDepartmentClassifier {
    private TagDepartmentClassifier() {
    }

    /**
     * Well-known tag prefixes mapped to department display names.
     * Order matters — first match wins.
     */
    private static final Map<String, String> TAG_DEPARTMENTS = new LinkedHashMap<>();

    static {
        // Materials
        TAG_DEPARTMENTS.put("forge:ingots", "Ingots");
        TAG_DEPARTMENTS.put("forge:nuggets", "Nuggets");
        TAG_DEPARTMENTS.put("forge:gems", "Gems");
        TAG_DEPARTMENTS.put("forge:dusts", "Dusts");
        TAG_DEPARTMENTS.put("forge:raw_materials", "Raw Materials");
        TAG_DEPARTMENTS.put("forge:plates", "Plates");
        TAG_DEPARTMENTS.put("forge:rods", "Rods");
        TAG_DEPARTMENTS.put("forge:wires", "Wires");

        // Blocks/Ores
        TAG_DEPARTMENTS.put("forge:ores", "Ores");
        TAG_DEPARTMENTS.put("forge:storage_blocks", "Storage Blocks");
        TAG_DEPARTMENTS.put("minecraft:logs", "Logs");
        TAG_DEPARTMENTS.put("minecraft:planks", "Planks");
        TAG_DEPARTMENTS.put("minecraft:wool", "Wool");
        TAG_DEPARTMENTS.put("minecraft:sand", "Sand");
        TAG_DEPARTMENTS.put("minecraft:stone_crafting_materials", "Stone");

        // Tools & Equipment
        TAG_DEPARTMENTS.put("forge:tools", "Tools");
        TAG_DEPARTMENTS.put("forge:tools/swords", "Swords");
        TAG_DEPARTMENTS.put("forge:tools/axes", "Axes");
        TAG_DEPARTMENTS.put("forge:tools/pickaxes", "Pickaxes");
        TAG_DEPARTMENTS.put("forge:tools/shovels", "Shovels");
        TAG_DEPARTMENTS.put("forge:tools/hoes", "Hoes");
        TAG_DEPARTMENTS.put("forge:armors", "Armor");

        // Food
        TAG_DEPARTMENTS.put("forge:foods", "Food");
        TAG_DEPARTMENTS.put("forge:crops", "Crops");
        TAG_DEPARTMENTS.put("forge:seeds", "Seeds");

        // Dyes & Misc
        TAG_DEPARTMENTS.put("forge:dyes", "Dyes");
        TAG_DEPARTMENTS.put("forge:glass", "Glass");
        TAG_DEPARTMENTS.put("forge:leather", "Leather");
        TAG_DEPARTMENTS.put("forge:string", "String");
        TAG_DEPARTMENTS.put("forge:feathers", "Feathers");
        TAG_DEPARTMENTS.put("forge:slimeballs", "Slime");
    }

    /**
     * Well-known mod namespaces mapped to friendly display names.
     */
    private static final Map<String, String> MOD_DEPARTMENTS = new LinkedHashMap<>();

    static {
        MOD_DEPARTMENTS.put("create", "Create");
        MOD_DEPARTMENTS.put("refinedstorage2", "Refined Storage");
        MOD_DEPARTMENTS.put("refinedstorage", "Refined Storage");
        MOD_DEPARTMENTS.put("ae2", "Applied Energistics");
        MOD_DEPARTMENTS.put("appliedenergistics2", "Applied Energistics");
        MOD_DEPARTMENTS.put("mekanism", "Mekanism");
        MOD_DEPARTMENTS.put("mekanismgenerators", "Mekanism");
        MOD_DEPARTMENTS.put("mekanismtools", "Mekanism");
        MOD_DEPARTMENTS.put("thermal", "Thermal Series");
        MOD_DEPARTMENTS.put("thermalexpansion", "Thermal Series");
        MOD_DEPARTMENTS.put("thermalfoundation", "Thermal Series");
        MOD_DEPARTMENTS.put("immersiveengineering", "Immersive Engineering");
        MOD_DEPARTMENTS.put("botania", "Botania");
        MOD_DEPARTMENTS.put("twilightforest", "Twilight Forest");
        MOD_DEPARTMENTS.put("tconstruct", "Tinkers' Construct");
        MOD_DEPARTMENTS.put("enderio", "Ender IO");
        MOD_DEPARTMENTS.put("farmersdelight", "Farmer's Delight");
        MOD_DEPARTMENTS.put("supplementaries", "Supplementaries");
        MOD_DEPARTMENTS.put("quark", "Quark");
    }

    /**
     * Classifies an item by its ID string, returning a department display name.
     *
     * @param itemId the registry name (e.g., "minecraft:diamond_sword")
     * @return a department display name, never null
     */
    public static String classify(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return "Miscellaneous";
        }

        Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(itemId));
        if (item == null) {
            return "Miscellaneous";
        }

        // 1. Check Forge/Minecraft tags
        String tagDept = classifyByTags(item);
        if (tagDept != null) {
            return tagDept;
        }

        // 2. Check mod namespace
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
        if (key != null && !"minecraft".equals(key.getNamespace())) {
            String modDept = MOD_DEPARTMENTS.get(key.getNamespace().toLowerCase(Locale.ROOT));
            if (modDept != null) {
                return modDept;
            }
            // Generic mod grouping — capitalize the namespace
            return capitalizeNamespace(key.getNamespace());
        }

        return "Miscellaneous";
    }

    /**
     * Classifies by checking Forge item tags. Returns null if no tag matches.
     */
    @Nullable
    private static String classifyByTags(Item item) {
        ItemStack stack = new ItemStack(item);
        for (Map.Entry<String, String> entry : TAG_DEPARTMENTS.entrySet()) {
            ResourceLocation tagLoc = ResourceLocation.parse(entry.getKey());
            TagKey<Item> tagKey = TagKey.create(Registries.ITEM, tagLoc);
            if (stack.is(tagKey)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Capitalizes a mod namespace into a display name (e.g., "mymod" → "Mymod").
     */
    private static String capitalizeNamespace(String namespace) {
        if (namespace.isEmpty()) return "Miscellaneous";
        return namespace.substring(0, 1).toUpperCase(Locale.ROOT) + namespace.substring(1);
    }
}

