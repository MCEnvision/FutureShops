package com.enviouse.futureshopsp.client.shop;

import com.enviouse.futureshopsp.block.ShopBlockEntity;
import com.enviouse.futureshopsp.init.ModBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * BEWLR that draws the shop block's GeckoLib model for the item form of the
 * block (inventory slot, held hand, dropped entity). Delegates to the global
 * {@link BlockEntityRenderDispatcher#renderItem} path using a dummy
 * {@link ShopBlockEntity}, so the BER registered for {@code SHOP_BLOCK_ENTITY}
 * (i.e. {@link ShopBlockGeoRenderer}) does the actual drawing and we get the
 * same GeckoLib model we see in the world.
 */
public class ShopBlockItemRenderer extends BlockEntityWithoutLevelRenderer {
    public static ShopBlockItemRenderer create() {
        Minecraft mc = Minecraft.getInstance();
        return new ShopBlockItemRenderer(mc.getBlockEntityRenderDispatcher(), mc.getEntityModels());
    }

    private ShopBlockEntity dummy;

    public ShopBlockItemRenderer(BlockEntityRenderDispatcher dispatcher, net.minecraft.client.model.geom.EntityModelSet modelSet) {
        super(dispatcher, modelSet);
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        // No custom texture baking — the BER handles all rendering.
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack,
                             MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (dummy == null) {
            dummy = new ShopBlockEntity(BlockPos.ZERO, ModBlocks.SHOP_BLOCK.get().defaultBlockState());
        }
        Minecraft.getInstance()
                .getBlockEntityRenderDispatcher()
                .renderItem(dummy, poseStack, buffer, packedLight, packedOverlay);
    }

    // Unused — BlockEntityWithoutLevelRenderer requires a ModelManager in its
    // constructor on some Forge revisions; vanilla's constructor already
    // accepts the dispatcher directly so we don't need to reference this.
    @SuppressWarnings("unused")
    private static ModelManager unusedRef() { return null; }
}
