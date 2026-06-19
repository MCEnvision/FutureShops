package com.enviouse.futureshopsp.init;

import com.enviouse.futureshopsp.Futureshops;
import com.enviouse.futureshopsp.block.ShopBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Futureshops.MODID);

    public static final DeferredBlock<Block> SHOP_BLOCK = BLOCKS.registerBlock("shop_block",
            ShopBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 1200.0F)
                    .requiresCorrectToolForDrops().noOcclusion());

    private ModBlocks() {
    }
}
