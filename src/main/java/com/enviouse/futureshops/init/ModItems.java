package com.enviouse.futureshops.init;

import com.enviouse.futureshops.Futureshops;
import com.enviouse.futureshops.item.ShopBlockItem;
import com.enviouse.futureshops.money.MoneyItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Futureshops.MODID);

    public static final long MONEY_DENOMINATION_MINOR_UNITS = 100L;

    // Registry id is intentionally `money` — legacy `futureshops:coin` items are remapped
    // at load time via LegacyIdMigrator so existing saves keep working.
    public static final RegistryObject<Item> MONEY_ITEM = ITEMS.register("money",
            () -> new MoneyItem(new Item.Properties().stacksTo(64), MONEY_DENOMINATION_MINOR_UNITS));

    public static final RegistryObject<Item> SHOP_BLOCK_ITEM = ITEMS.register("shop_block",
            () -> new ShopBlockItem(ModBlocks.SHOP_BLOCK.get(), new Item.Properties()));

    private ModItems() {
    }
}
