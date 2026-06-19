package com.enviouse.futureshopsp.item;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;

/**
 * BlockItem form of the shop block. The custom GeckoLib inventory renderer (formerly installed via
 * Forge's {@code initializeClient}) is registered in 1.21.1 through {@code RegisterClientExtensionsEvent}
 * (client tranche), so this is now a plain marker subclass.
 */
public class ShopBlockItem extends BlockItem {
    public ShopBlockItem(Block block, Properties properties) {
        super(block, properties);
    }
}
