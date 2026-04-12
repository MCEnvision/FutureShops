package com.enviouse.futureshops.init;

import com.enviouse.futureshops.Futureshops;
import com.enviouse.futureshops.block.ShopBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Futureshops.MODID);

    public static final RegistryObject<Block> SHOP_BLOCK = BLOCKS.register("shop_block", () -> new ShopBlock(
        BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 1200.0F).requiresCorrectToolForDrops().noOcclusion()
    ));

    private ModBlocks() {
    }
}

