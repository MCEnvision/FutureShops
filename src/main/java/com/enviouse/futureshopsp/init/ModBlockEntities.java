package com.enviouse.futureshopsp.init;

import com.enviouse.futureshopsp.Futureshops;
import com.enviouse.futureshopsp.block.ShopBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Futureshops.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ShopBlockEntity>> SHOP_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("shop_block_entity", () ->
                    BlockEntityType.Builder.of(ShopBlockEntity::new, ModBlocks.SHOP_BLOCK.get()).build(null));

    private ModBlockEntities() {
    }
}
