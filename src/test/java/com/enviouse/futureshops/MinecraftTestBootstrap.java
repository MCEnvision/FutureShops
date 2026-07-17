package com.enviouse.futureshops;

import com.enviouse.futureshops.init.ModItems;
import com.enviouse.futureshops.money.MoneyItem;
import net.minecraft.SharedConstants;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.RegistryObject;

import java.lang.reflect.Field;

public final class MinecraftTestBootstrap {
    private static boolean initialized;

    private MinecraftTestBootstrap() {
    }

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        SharedConstants.tryDetectVersion();
        try {
            Field bootstrapped = Bootstrap.class.getDeclaredField(
                    "isBootstrapped");
            bootstrapped.setAccessible(true);
            if (!bootstrapped.getBoolean(null)) {
                bootstrapped.setBoolean(null, true);
                registerMoneyItem();
                BuiltInRegistries.bootStrap();
            } else {
                bindRegisteredMoneyItem();
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                    "Unable to initialize Minecraft test registries",
                    exception);
        }
        initialized = true;
    }

    private static void registerMoneyItem() throws ReflectiveOperationException {
        ResourceLocation id = new ResourceLocation(Futureshops.MODID, "money");
        Item money = Registry.register(BuiltInRegistries.ITEM, id,
                new MoneyItem(new Item.Properties().stacksTo(64),
                        ModItems.MONEY_DENOMINATION_MINOR_UNITS));
        bindMoneyItem(money);
    }

    private static void bindRegisteredMoneyItem()
            throws ReflectiveOperationException {
        ResourceLocation id = new ResourceLocation(Futureshops.MODID, "money");
        if (!BuiltInRegistries.ITEM.containsKey(id)) {
            throw new IllegalStateException(
                    "Minecraft registries were initialized without the test money item");
        }
        bindMoneyItem(BuiltInRegistries.ITEM.get(id));
    }

    private static void bindMoneyItem(Item money)
            throws ReflectiveOperationException {
        Field value = RegistryObject.class.getDeclaredField("value");
        value.setAccessible(true);
        value.set(ModItems.MONEY_ITEM, money);
    }
}
