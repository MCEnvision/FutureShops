package com.enviouse.futureshops.init;

import com.enviouse.futureshops.Futureshops;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Futureshops.MODID);

    public static final RegistryObject<CreativeModeTab> FUTURESHOPS_TAB = CREATIVE_MODE_TABS.register("futureshops_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.futureshops.futureshops_tab"))
                    .icon(() -> new ItemStack(ModItems.SHOP_BLOCK_ITEM.get()))
                    .displayItems((params, output) -> {
                        output.accept(ModItems.SHOP_BLOCK_ITEM.get());
                        output.accept(ModItems.MONEY_ITEM.get());
                    })
                    .build()
    );

    private ModCreativeTabs() {
    }
}

