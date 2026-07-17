package com.enviouse.futureshops.server.market.bazaar;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class BazaarProductStackMatcher {
    private BazaarProductStackMatcher() {
    }

    public static BazaarProductStackMatchResult match(
            BazaarProductDefinition definition,
            ItemStack stack,
            String dimensionId
    ) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(stack, "stack");
        String dimension = Objects.requireNonNull(dimensionId,
                "dimensionId").strip().toLowerCase(java.util.Locale.ROOT);
        if (stack.isEmpty()) {
            return BazaarProductStackMatchResult.EMPTY;
        }
        ResourceLocation registryKey = BuiltInRegistries.ITEM.getKey(
                stack.getItem());
        if (registryKey == null || !definition.product().registryId()
                .equals(registryKey.toString())) {
            return BazaarProductStackMatchResult.WRONG_ITEM;
        }
        if (!BazaarProductDefinitionLoader.validResourceId(dimension)
                || !definition.allowedDimensions().isEmpty()
                && !definition.allowedDimensions().contains(dimension)) {
            return BazaarProductStackMatchResult.WRONG_DIMENSION;
        }
        CompoundTag tag = stack.getTag();
        if (definition.identityPolicy()
                == BazaarProductIdentityPolicy.EXACT
                && (tag == null || !tag.toString().equals(
                definition.product().exactIdentity()))) {
            return BazaarProductStackMatchResult.EXACT_IDENTITY_MISMATCH;
        }
        BazaarItemRestrictions restrictions = definition.restrictions();
        if (stack.isDamaged() && !restrictions.allowDamaged()
                && definition.identityPolicy()
                != BazaarProductIdentityPolicy.EXACT) {
            return BazaarProductStackMatchResult.DAMAGED_DENIED;
        }
        if (stack.hasCustomHoverName() && !restrictions.allowNamed()
                && definition.identityPolicy()
                != BazaarProductIdentityPolicy.EXACT) {
            return BazaarProductStackMatchResult.NAMED_DENIED;
        }
        if (isEnchanted(stack) && !restrictions.allowEnchanted()
                && definition.identityPolicy()
                != BazaarProductIdentityPolicy.EXACT) {
            return BazaarProductStackMatchResult.ENCHANTED_DENIED;
        }
        if (isContainer(stack) && !restrictions.allowContainers()) {
            return BazaarProductStackMatchResult.CONTAINER_DENIED;
        }
        if (hasCapabilities(stack) && !restrictions.allowCapabilities()) {
            return BazaarProductStackMatchResult.CAPABILITY_DENIED;
        }
        if (definition.identityPolicy()
                == BazaarProductIdentityPolicy.COMMODITY
                && unsupportedCommodityTag(tag, restrictions)) {
            return BazaarProductStackMatchResult.UNSUPPORTED_TAG_DATA;
        }
        return BazaarProductStackMatchResult.ACCEPTED;
    }

    private static boolean isEnchanted(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return stack.isEnchanted() || tag != null
                && (tag.contains("Enchantments")
                || tag.contains("StoredEnchantments"));
    }

    private static boolean isContainer(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && (tag.contains("BlockEntityTag")
                || tag.contains("Items"));
    }

    private static boolean hasCapabilities(ItemStack stack) {
        CompoundTag serialized = stack.serializeNBT();
        if (serialized.contains("ForgeCaps")) {
            return true;
        }
        try {
            return LiveCapabilityProbe.exposesCapabilities(stack);
        } catch (LinkageError error) {
            return false;
        }
    }

    /**
     * Isolated so plain JUnit (no Forge runtime transformer) never has to
     * initialize {@link ForgeCapabilities}; in the live game this class
     * always loads and the capability checks run unchanged.
     */
    private static final class LiveCapabilityProbe {
        private LiveCapabilityProbe() {
        }

        static boolean exposesCapabilities(ItemStack stack) {
            return stack.getCapability(ForgeCapabilities.ITEM_HANDLER)
                    .isPresent()
                    || stack.getCapability(
                    ForgeCapabilities.FLUID_HANDLER_ITEM).isPresent()
                    || stack.getCapability(ForgeCapabilities.ENERGY)
                    .isPresent();
        }
    }

    private static boolean unsupportedCommodityTag(
            CompoundTag tag,
            BazaarItemRestrictions restrictions) {
        if (tag == null || tag.isEmpty()) {
            return false;
        }
        Set<String> allowed = new HashSet<>();
        if (restrictions.allowDamaged()) {
            allowed.add("Damage");
        }
        if (restrictions.allowNamed()) {
            allowed.add("display");
        }
        if (restrictions.allowEnchanted()) {
            allowed.add("Enchantments");
            allowed.add("StoredEnchantments");
        }
        if (restrictions.allowContainers()) {
            allowed.add("BlockEntityTag");
            allowed.add("Items");
        }
        return !allowed.containsAll(tag.getAllKeys());
    }
}
