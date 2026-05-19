package com.enviouse.futureshops.client.shop;

import com.enviouse.futureshops.Futureshops;
import com.enviouse.futureshops.block.ShopBlockEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class ShopBlockGeoModel extends GeoModel<ShopBlockEntity> {
    private static final ResourceLocation MODEL =
            new ResourceLocation(Futureshops.MODID, "geo/shop_block.geo.json");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Futureshops.MODID, "textures/block/shop_block.png");
    private static final ResourceLocation ANIMATION =
            new ResourceLocation(Futureshops.MODID, "animations/shop_block.animation.json");

    @Override
    public ResourceLocation getModelResource(ShopBlockEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(ShopBlockEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(ShopBlockEntity animatable) {
        return ANIMATION;
    }
}
