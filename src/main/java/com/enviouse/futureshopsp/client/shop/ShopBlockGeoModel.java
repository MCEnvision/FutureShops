package com.enviouse.futureshopsp.client.shop;

import com.enviouse.futureshopsp.Futureshops;
import com.enviouse.futureshopsp.block.ShopBlockEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class ShopBlockGeoModel extends GeoModel<ShopBlockEntity> {
    private static final ResourceLocation MODEL =
            ResourceLocation.fromNamespaceAndPath(Futureshops.MODID, "geo/shop_block.geo.json");
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Futureshops.MODID, "textures/block/shop_block.png");
    private static final ResourceLocation ANIMATION =
            ResourceLocation.fromNamespaceAndPath(Futureshops.MODID, "animations/shop_block.animation.json");

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
