package com.enviouse.futureshops.item;

import com.enviouse.futureshops.client.shop.ShopBlockItemRenderer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Consumer;

/**
 * BlockItem form of the shop block. The only reason this subclass exists is to
 * override {@link #initializeClient} and install the {@link ShopBlockItemRenderer}
 * BEWLR so the item renders as the GeckoLib 3D model in inventory / first-person /
 * on the ground, rather than as a flat atlas sprite.
 */
public class ShopBlockItem extends BlockItem {
    public ShopBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private ShopBlockItemRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    renderer = ShopBlockItemRenderer.create();
                }
                return renderer;
            }
        });
    }
}
