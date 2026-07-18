package com.enviouse.futureshops;

import com.enviouse.futureshops.catalog.ShopCatalog;
import com.enviouse.futureshops.compat.rs2.RefinedStorage2Compat;
import com.enviouse.futureshops.config.AuctionHouseConfig;
import com.enviouse.futureshops.config.BazaarConfig;
import com.enviouse.futureshops.config.EscrowConfig;
import com.enviouse.futureshops.init.ModBlockEntities;
import com.enviouse.futureshops.init.ModBlocks;
import com.enviouse.futureshops.init.ModCreativeTabs;
import com.enviouse.futureshops.init.ModItems;
import com.enviouse.futureshops.money.SpentMintsSavedData;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.server.economy.BalanceManager;
import com.enviouse.futureshops.server.escrow.runtime.EscrowRuntimeManager;
import com.enviouse.futureshops.server.escrow.runtime.EscrowRuntimeService;
import com.enviouse.futureshops.server.escrow.runtime.EscrowRuntimeState;
import com.enviouse.futureshops.server.escrow.stock.migration.CatalogStockRuntime;
import com.enviouse.futureshops.server.economy.migration.LegacyBalanceMigrationManager;
import com.enviouse.futureshops.server.pricing.DynamicPricingEngine;
import com.enviouse.futureshops.server.security.ServerRequestSecurityManager;
import com.enviouse.futureshops.server.session.ShopSessionManager;
import com.enviouse.futureshops.server.shop.ExternalStorageRegistry;
import com.enviouse.futureshops.server.shop.ForgeCapabilityStorageAdapter;
import com.enviouse.futureshops.server.shop.StockRefreshScheduler;
import com.enviouse.futureshops.server.market.MarketModuleService;
import com.enviouse.futureshops.server.market.MarketCapabilityProjectionService;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.InterModComms;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;
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

        modEventBus.addListener(this::enqueueIMC);
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITY_TYPES.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);

        // Legacy id remapping: `futureshops:coin` → `futureshops:money` (Change B rename).
        MinecraftForge.EVENT_BUS.addListener(this::onMissingItemMappings);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);

        // Register our mod's ForgeConfigSpec so that Forge can create and load the config file for us
        ModLoadingContext context = ModLoadingContext.get();
        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC, "futureshops-common.toml");
        context.registerConfig(ModConfig.Type.COMMON, EscrowConfig.SPEC, EscrowConfig.FILE_NAME);
        context.registerConfig(ModConfig.Type.COMMON, AuctionHouseConfig.SPEC, AuctionHouseConfig.FILE_NAME);
        context.registerConfig(ModConfig.Type.COMMON, BazaarConfig.SPEC, BazaarConfig.FILE_NAME);
        context.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC, "futureshops-client.toml");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(ShopPackets::register);
        // Register default Forge capability storage adapter
        ExternalStorageRegistry.register(ForgeCapabilityStorageAdapter.INSTANCE);
        // Attempt RS2 soft-dependency integration
        RefinedStorage2Compat.init();
        LOGGER.info("FutureShops common setup complete.");
    }

    /**
     * Send InterModComms messages during the enqueue phase.
     * Blacklists the shop block from CarryOn pickup if CarryOn is loaded.
     */
    private void enqueueIMC(final InterModEnqueueEvent event) {
        if (ModList.get().isLoaded("carryon")) {
            InterModComms.sendTo("carryon", "blacklistBlock", () -> MODID + ":shop_block");
            LOGGER.info("Sent CarryOn IMC blacklist for {}:shop_block", MODID);
        }
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        MarketModuleService.clearSessions();
        ServerRequestSecurityManager.initialize(event.getServer());
        EscrowRuntimeService escrow = EscrowRuntimeManager.initialize(event.getServer());
        if (escrow.state() == EscrowRuntimeState.MAINTENANCE) {
            LOGGER.error("FutureShops escrow entered maintenance during startup.",
                    escrow.failure().orElse(null));
        } else if (escrow.state() == EscrowRuntimeState.RECOVERING) {
            LOGGER.warn("FutureShops escrow is recovering before value mutations become available.");
        }
        LegacyBalanceMigrationManager.initialize(event.getServer());
        BalanceManager.initialize(event.getServer());
        // Resolve the configured physical-currency adapter (built-in money item
        // or a foreign mod's items, e.g. Apocalypse Now cash).
        com.enviouse.futureshops.money.CurrencyManager.initialize();
        // Eagerly load (or create) the coin-mint registry from disk.
        SpentMintsSavedData.get(event.getServer());
        // Load shop catalog from config/futureshops/shops/*.json (spec §24).
        ShopCatalog.reload(event.getServer());
        CatalogStockRuntime.initialize(event.getServer(), escrow);
        // Initialize dynamic pricing engine (spec §30).
        DynamicPricingEngine.reset();
        // Initialize stock refresh scheduler (spec §31).
        StockRefreshScheduler.reset();
        // Market schedulers (auction expiry/settlement; bazaar order expiry) + the bazaar
        // product catalog (config/futureshops/bazaar/products/*.json → order book lifecycle).
        com.enviouse.futureshops.server.escrow.runtime.AuctionExpirationScheduler.reset();
        com.enviouse.futureshops.server.escrow.runtime.BazaarExpirationScheduler.reset();
        if (escrow.state() == EscrowRuntimeState.READY) {
            try {
                // Push the config-derived effective rule snapshot BEFORE the catalog reload so
                // browse-time config revisions are correct from the first frame (orders also
                // synchronize lazily, so this is presentation-correctness, not safety).
                com.enviouse.futureshops.server.escrow.runtime.BazaarActionService
                        .synchronizeEffectiveRules(escrow);
                com.enviouse.futureshops.server.market.bazaar.BazaarProductCatalogRuntime
                        .reload(escrow);
            } catch (RuntimeException exception) {
                LOGGER.error("Bazaar product catalog failed to load; bazaar browse stays "
                        + "empty until reload.", exception);
            }
        }
        LOGGER.info("FutureShops server starting.");
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        MarketModuleService.clearSessions();
        MarketCapabilityProjectionService.clearRevisionState();
        ServerRequestSecurityManager.shutdown(event.getServer());
        // Force-close every open shop session so clients can dismiss their GUIs.
        ShopSessionManager.closeAllAndForceClose(event.getServer(), "SERVER_STOPPING");
        BalanceManager.clear();
        LegacyBalanceMigrationManager.shutdown(event.getServer());
        if (EscrowRuntimeManager.getOrNull() != null) {
            try {
                EscrowRuntimeManager.shutdown(event.getServer());
            } catch (RuntimeException exception) {
                LOGGER.error("FutureShops escrow failed to close cleanly.", exception);
            }
        }
        com.enviouse.futureshops.money.CurrencyManager.clear();
        DynamicPricingEngine.reset();
        StockRefreshScheduler.reset();
        com.enviouse.futureshops.server.escrow.runtime.AuctionExpirationScheduler.reset();
        com.enviouse.futureshops.server.escrow.runtime.BazaarExpirationScheduler.reset();
        com.enviouse.futureshops.server.market.bazaar.BazaarProductCatalogRuntime.clear();
        // Wipe in-memory catalog so live stock doesn't leak into the next world (singleplayer).
        ShopCatalog.clear();
        com.enviouse.futureshops.server.shop.ShopConfigClipboard.clearAll();
        LOGGER.info("FutureShops server stopping.");
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.getServer() != null) {
            if (EscrowRuntimeManager.getOrNull() != null) {
                EscrowRuntimeManager.tick(event.getServer());
                CatalogStockRuntime.tick(event.getServer());
            }
            LegacyBalanceMigrationManager.tick(event.getServer());
            DynamicPricingEngine.onServerTick(event.getServer());
            StockRefreshScheduler.onServerTick(event.getServer());
            com.enviouse.futureshops.server.escrow.runtime.AuctionExpirationScheduler
                    .onServerTick(event.getServer());
            com.enviouse.futureshops.server.escrow.runtime.BazaarExpirationScheduler
                    .onServerTick(event.getServer());
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity()
                instanceof net.minecraft.server.level.ServerPlayer player) {
            ServerRequestSecurityManager.removePlayer(player);
            MarketModuleService.close(player.getUUID());
        }
    }

    /**
     * Legacy save-compat: prior releases registered the currency item as
     * {@code futureshops:coin}. The Change B rename moved it to {@code futureshops:money};
     * without this handler every existing world's coin stacks (in chests, inventories,
     * dropped items) would be wiped on load. Forge fires {@code MissingMappingsEvent}
     * during registry load for any saved id that has no live registry entry — we remap
     * ours inline here.
     */
    public void onMissingItemMappings(net.minecraftforge.registries.MissingMappingsEvent event) {
        for (var mapping : event.getMappings(net.minecraftforge.registries.ForgeRegistries.Keys.ITEMS, MODID)) {
            if ("coin".equals(mapping.getKey().getPath())) {
                mapping.remap(ModItems.MONEY_ITEM.get());
                LOGGER.info("Remapped legacy item id futureshops:coin -> futureshops:money");
            }
        }
    }

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
    public static class ClientModEvents {

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            // Change C: the currency item uses a single unified texture (dollar_bill) regardless
            // of stack count, so no item-property override is registered here anymore.
            LOGGER.info("FutureShops client setup complete.");
        }

        @SubscribeEvent
        public static void onRegisterRenderers(net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
            event.registerBlockEntityRenderer(
                    ModBlockEntities.SHOP_BLOCK_ENTITY.get(),
                    com.enviouse.futureshops.client.shop.ShopBlockGeoRenderer::new);
        }

        @SubscribeEvent
        public static void onRegisterKeyMappings(net.minecraftforge.client.event.RegisterKeyMappingsEvent event) {
            event.register(com.enviouse.futureshops.client.ModKeyMappings.OPEN_SHOP);
        }
    }
}
