package com.enviouse.futureshops.server.market.bazaar;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BazaarProductStackMatcherTest {
    @Test
    void ordinaryCommodityIsAccepted() {
        assertEquals(BazaarProductStackMatchResult.ACCEPTED,
                BazaarProductStackMatcher.match(commodity(
                        BazaarItemRestrictions.safeDefaults()),
                        new ItemStack(Items.IRON_INGOT),
                        "minecraft:overworld"));
    }

    @Test
    void commodityRejectsNamedAndUnknownTagVariants() {
        ItemStack named = new ItemStack(Items.IRON_INGOT);
        named.setHoverName(Component.literal("Special Iron"));
        assertEquals(BazaarProductStackMatchResult.NAMED_DENIED,
                BazaarProductStackMatcher.match(commodity(
                        BazaarItemRestrictions.safeDefaults()), named,
                        "minecraft:overworld"));

        ItemStack unknown = new ItemStack(Items.IRON_INGOT);
        unknown.getOrCreateTag().putString("CustomMarketData", "value");
        assertEquals(BazaarProductStackMatchResult.UNSUPPORTED_TAG_DATA,
                BazaarProductStackMatcher.match(commodity(
                        BazaarItemRestrictions.safeDefaults()), unknown,
                        "minecraft:overworld"));
    }

    @Test
    void explicitCommodityRestrictionAllowsOnlyKnownTagClass() {
        ItemStack named = new ItemStack(Items.IRON_INGOT);
        named.setHoverName(Component.literal("Any Named Iron"));
        BazaarItemRestrictions restrictions =
                new BazaarItemRestrictions(false, true, false,
                        false, false);

        assertEquals(BazaarProductStackMatchResult.ACCEPTED,
                BazaarProductStackMatcher.match(commodity(restrictions),
                        named, "minecraft:overworld"));
        named.getOrCreateTag().putInt("Unknown", 1);
        assertEquals(BazaarProductStackMatchResult.UNSUPPORTED_TAG_DATA,
                BazaarProductStackMatcher.match(commodity(restrictions),
                        named, "minecraft:overworld"));
    }

    @Test
    void exactIdentityAcceptsOnlyTheCanonicalTag() {
        ItemStack expected = new ItemStack(Items.DIAMOND);
        expected.setHoverName(Component.literal("Market Gem"));
        BazaarProduct product = new BazaarProduct("market_gem", 1L,
                "minecraft:diamond", expected.getTag().toString(), "gems",
                1, 1L, 1L, 1_000_000L, 64,
                BazaarProductStatus.ACTIVE);
        BazaarProductDefinition exact = new BazaarProductDefinition(product,
                "Market Gem", "minecraft:diamond",
                BazaarProductIdentityPolicy.EXACT, List.of(),
                BazaarItemRestrictions.safeDefaults(), "gem.json");

        assertEquals(BazaarProductStackMatchResult.ACCEPTED,
                BazaarProductStackMatcher.match(exact, expected,
                        "minecraft:overworld"));
        ItemStack other = new ItemStack(Items.DIAMOND);
        other.setHoverName(Component.literal("Other Gem"));
        assertEquals(BazaarProductStackMatchResult.EXACT_IDENTITY_MISMATCH,
                BazaarProductStackMatcher.match(exact, other,
                        "minecraft:overworld"));
    }

    @Test
    void dimensionAllowlistFailsClosed() {
        BazaarProductDefinition definition = new BazaarProductDefinition(
                commodity(BazaarItemRestrictions.safeDefaults()).product(),
                "", "minecraft:iron_ingot",
                BazaarProductIdentityPolicy.COMMODITY,
                List.of("minecraft:overworld"),
                BazaarItemRestrictions.safeDefaults(), "iron.json");

        assertEquals(BazaarProductStackMatchResult.WRONG_DIMENSION,
                BazaarProductStackMatcher.match(definition,
                        new ItemStack(Items.IRON_INGOT),
                        "minecraft:the_nether"));
    }

    private static BazaarProductDefinition commodity(
            BazaarItemRestrictions restrictions) {
        BazaarProduct product = new BazaarProduct("iron", 1L,
                "minecraft:iron_ingot", "", "metals", 1, 1L,
                1L, 1_000_000L, 1000, BazaarProductStatus.ACTIVE);
        return new BazaarProductDefinition(product, "Iron",
                "minecraft:iron_ingot",
                BazaarProductIdentityPolicy.COMMODITY, List.of(),
                restrictions, "iron.json");
    }
}
