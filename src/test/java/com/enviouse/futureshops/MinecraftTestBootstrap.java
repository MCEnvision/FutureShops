package com.enviouse.futureshops;

import com.enviouse.futureshops.init.ModItems;
import com.enviouse.futureshops.money.MoneyItem;
import net.minecraft.SharedConstants;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Constructor;

public final class MinecraftTestBootstrap {
    private static boolean initialized;
    private static Item capabilityItem;
    private static final Capability<IItemHandler> TEST_ITEM_HANDLER_CAPABILITY =
            createTestCapability();

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
                registerCapabilityItem();
                BuiltInRegistries.bootStrap();
            } else {
                bindRegisteredMoneyItem();
                bindRegisteredCapabilityItem();
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                    "Unable to initialize Minecraft test registries",
                    exception);
        }
        initialized = true;
    }

    public static synchronized Item capabilityItem() {
        initialize();
        return capabilityItem;
    }

    public static Capability<IItemHandler> capabilityItemHandler() {
        return TEST_ITEM_HANDLER_CAPABILITY;
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

    private static void registerCapabilityItem() {
        ResourceLocation id = new ResourceLocation(
                Futureshops.MODID, "test_capability_item");
        capabilityItem = Registry.register(BuiltInRegistries.ITEM, id,
                new CapabilityItem(new Item.Properties().stacksTo(64)));
    }

    private static void bindRegisteredCapabilityItem() {
        ResourceLocation id = new ResourceLocation(
                Futureshops.MODID, "test_capability_item");
        if (!BuiltInRegistries.ITEM.containsKey(id)) {
            throw new IllegalStateException(
                    "Minecraft registries were initialized without the test capability item");
        }
        capabilityItem = BuiltInRegistries.ITEM.get(id);
    }

    private static void bindMoneyItem(Item money)
            throws ReflectiveOperationException {
        Field value = RegistryObject.class.getDeclaredField("value");
        value.setAccessible(true);
        value.set(ModItems.MONEY_ITEM, money);
    }

    @SuppressWarnings("unchecked")
    private static Capability<IItemHandler> createTestCapability() {
        try {
            Constructor<?> constructor = Capability.class
                    .getDeclaredConstructor(String.class);
            constructor.setAccessible(true);
            return (Capability<IItemHandler>) constructor.newInstance(
                    "com.enviouse.futureshops.test.item_handler");
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                    "Unable to create the test item capability", exception);
        }
    }

    private static final class CapabilityItem extends Item {
        private CapabilityItem(Properties properties) {
            super(properties);
        }

        @Override
        public ICapabilityProvider initCapabilities(
                ItemStack stack,
                @Nullable CompoundTag nbt
        ) {
            return new CapabilityInventoryProvider();
        }
    }

    private static final class CapabilityInventoryProvider
            implements ICapabilitySerializable<CompoundTag> {
        private final ItemStackHandler inventory = new ItemStackHandler(1);
        private final LazyOptional<IItemHandler> optional =
                LazyOptional.of(() -> inventory);

        @Override
        public @NotNull <T> LazyOptional<T> getCapability(
                @NotNull Capability<T> capability,
                @Nullable Direction side
        ) {
            return capability == TEST_ITEM_HANDLER_CAPABILITY
                    ? optional.cast()
                    : LazyOptional.empty();
        }

        @Override
        public CompoundTag serializeNBT() {
            return inventory.serializeNBT();
        }

        @Override
        public void deserializeNBT(CompoundTag nbt) {
            inventory.deserializeNBT(nbt);
        }
    }
}
