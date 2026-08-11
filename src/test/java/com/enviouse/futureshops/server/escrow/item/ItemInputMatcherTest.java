package com.enviouse.futureshops.server.escrow.item;

import com.enviouse.futureshops.MinecraftTestBootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemInputMatcherTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void exactAndItemOnlyModesRemainDistinct() {
        ItemStack red = taggedEmerald("red");
        ItemStack blue = taggedEmerald("blue");

        ItemInputMatcher exact = ItemInputMatcher.exact(red);
        ItemInputMatcher itemOnly = ItemInputMatcher.itemOnly(red);

        assertTrue(exact.matches(red));
        assertFalse(exact.matches(blue));
        assertTrue(itemOnly.matches(red));
        assertTrue(itemOnly.matches(blue));
        assertFalse(itemOnly.matches(new ItemStack(Items.DIAMOND)));
    }

    @Test
    void invalidOrIgnoredSnbtFailsClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> ItemInputMatcher.parse("minecraft:emerald",
                        ItemMatchMode.EXACT, "{broken:"));
        assertThrows(IllegalArgumentException.class,
                () -> ItemInputMatcher.parse("minecraft:emerald",
                        ItemMatchMode.ITEM_ONLY, "{quality:\"red\"}"));
        assertThrows(IllegalArgumentException.class,
                () -> ItemInputMatcher.parse("missingmod:not_present",
                        ItemMatchMode.ITEM_ONLY, ""));

        ItemInputMatcher parsed = ItemInputMatcher.parse(
                "minecraft:emerald", ItemMatchMode.EXACT,
                "{quality:\"red\"}");
        assertTrue(parsed.matches(taggedEmerald("red")));
        assertFalse(parsed.matches(taggedEmerald("blue")));
    }

    @Test
    void oversizedAndDeepSnbtIsRejectedBeforeParsing() {
        assertThrows(IllegalArgumentException.class,
                () -> ItemInputMatcher.parse("minecraft:emerald",
                        ItemMatchMode.EXACT,
                        "x".repeat(ItemInputMatcher.MAX_SNBT_CHARACTERS + 1)));
        String deep = "{".repeat(ItemInputMatcher.MAX_SNBT_DEPTH + 1)
                + "}".repeat(ItemInputMatcher.MAX_SNBT_DEPTH + 1);
        assertThrows(IllegalArgumentException.class,
                () -> ItemInputMatcher.parse("minecraft:emerald",
                        ItemMatchMode.EXACT, deep));
    }

    private static ItemStack taggedEmerald(String quality) {
        ItemStack stack = new ItemStack(Items.EMERALD, 4);
        stack.getOrCreateTag().putString("quality", quality);
        return stack;
    }
}
