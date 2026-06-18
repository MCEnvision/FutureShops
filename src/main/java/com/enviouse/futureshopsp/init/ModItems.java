package com.enviouse.futureshopsp.init;

import com.enviouse.futureshopsp.Futureshops;
import com.enviouse.futureshopsp.money.MoneyItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Futureshops.MODID);

    public static final long MONEY_DENOMINATION_MINOR_UNITS = 100L;

    // Registry id is intentionally `money`. Legacy `futureshops:coin` saves are remapped at load
    // time via ModItems.ITEMS.addAlias(...) (wired in the entrypoint), so existing worlds keep working.
    // registerItem(name, factory, props) sets the registry id onto the Properties (1.21.1 requirement).
    public static final DeferredItem<Item> MONEY_ITEM = ITEMS.registerItem("money",
            props -> new MoneyItem(props, MONEY_DENOMINATION_MINOR_UNITS),
            new Item.Properties().stacksTo(64));

    // NOTE: SHOP_BLOCK_ITEM is added with the block cluster (it needs ModBlocks.SHOP_BLOCK).

    private ModItems() {
    }
}
