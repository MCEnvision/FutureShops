package com.enviouse.futureshops.init;

import com.enviouse.futureshops.Futureshops;
import com.enviouse.futureshops.block.ShopBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Futureshops.MODID);

    public static final RegistryObject<BlockEntityType<ShopBlockEntity>> SHOP_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register("shop_block_entity", () ->
        BlockEntityType.Builder.of(ShopBlockEntity::new, ModBlocks.SHOP_BLOCK.get()).build(null)
    );

    private ModBlockEntities() {
    }
}

