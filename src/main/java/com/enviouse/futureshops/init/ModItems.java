package com.enviouse.futureshops.init;

import com.enviouse.futureshops.Futureshops;
import com.enviouse.futureshops.coin.CoinItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Futureshops.MODID);

    public static final RegistryObject<Item> COIN_ITEM = ITEMS.register("coin", () -> new CoinItem(new Item.Properties().stacksTo(64), 100L));

    public static final RegistryObject<Item> SHOP_BLOCK_ITEM = ITEMS.register("shop_block", () -> new BlockItem(ModBlocks.SHOP_BLOCK.get(), new Item.Properties()));

    private ModItems() {
    }
}

