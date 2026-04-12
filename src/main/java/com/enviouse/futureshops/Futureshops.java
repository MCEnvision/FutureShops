package com.enviouse.futureshops;

import com.enviouse.futureshops.catalog.ShopCatalog;
import com.enviouse.futureshops.init.ModBlockEntities;
import com.enviouse.futureshops.init.ModBlocks;
import com.enviouse.futureshops.init.ModCreativeTabs;
import com.enviouse.futureshops.init.ModItems;
import com.enviouse.futureshops.coin.SpentMintsSavedData;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.server.economy.BalanceManager;
import com.enviouse.futureshops.server.session.ShopSessionManager;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(Futureshops.MODID)
public class Futureshops {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "futureshops";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();

    public Futureshops() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITY_TYPES.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);

        // Register our mod's ForgeConfigSpec so that Forge can create and load the config file for us
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(ShopPackets::register);
        LOGGER.info("FutureShops common setup complete.");
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        BalanceManager.initialize(event.getServer());
        // Eagerly load (or create) the coin-mint registry from disk.
        SpentMintsSavedData.get(event.getServer());
        // Load shop catalog from config/futureshops/shops/*.json (spec §24).
        ShopCatalog.reload(event.getServer());
        LOGGER.info("FutureShops server starting.");
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        // Force-close every open shop session so clients can dismiss their GUIs.
        ShopSessionManager.closeAllAndForceClose(event.getServer(), "SERVER_STOPPING");
        BalanceManager.clear();
        LOGGER.info("FutureShops server stopping.");
    }

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ClientModEvents {

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            // Some client setup code
            LOGGER.info("FutureShops client setup complete.");
        }
    }
}
