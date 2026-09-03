package com.enviouse.futureshopsp;

import com.enviouse.futureshopsp.catalog.ShopCatalog;
import com.enviouse.futureshopsp.client.shop.ShopBlockGeoRenderer;
import com.enviouse.futureshopsp.client.shop.ShopBlockItemRenderer;
import com.enviouse.futureshopsp.compat.rs2.RefinedStorage2Compat;
import com.enviouse.futureshopsp.init.ModBlockEntities;
import com.enviouse.futureshopsp.init.ModBlocks;
import com.enviouse.futureshopsp.init.ModCreativeTabs;
import com.enviouse.futureshopsp.init.ModItems;
import com.enviouse.futureshopsp.money.ModDataComponents;
import com.enviouse.futureshopsp.server.economy.BalanceManager;
import com.enviouse.futureshopsp.server.pricing.DynamicPricingEngine;
import com.enviouse.futureshopsp.server.session.ShopSessionManager;
import com.enviouse.futureshopsp.server.shop.ExternalStorageRegistry;
import com.enviouse.futureshopsp.server.shop.ForgeCapabilityStorageAdapter;
import com.enviouse.futureshopsp.server.shop.ShopConfigClipboard;
import com.enviouse.futureshopsp.server.shop.StockRefreshScheduler;
import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.InterModComms;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.event.lifecycle.InterModEnqueueEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * FutureShops mod entrypoint.
 *
 * <p>Identity (Decision A): runtime modid + all resource/data namespaces stay "futureshops"
 * (save-compat). The Java package is com.enviouse.futureshopsp ("p") — that never touches runtime IDs.
 */
@Mod(Futureshops.MODID)
public class Futureshops {
    public static final String MODID = "futureshops";
    private static final Logger LOGGER = LogUtils.getLogger();

    public Futureshops(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::enqueueIMC);

        // Legacy save-compat: futureshops:coin -> futureshops:money. NeoForge replaces Forge's
        // MissingMappingsEvent with a registry alias, set before RegisterEvent fires.
        ModItems.ITEMS.addAlias(
                ResourceLocation.fromNamespaceAndPath(MODID, "coin"),
                ResourceLocation.fromNamespaceAndPath(MODID, "money"));

        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITY_TYPES.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);
        ModDataComponents.COMPONENTS.register(modEventBus);

        // Game-bus server lifecycle/tick handlers below.
        NeoForge.EVENT_BUS.register(this);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ExternalStorageRegistry.register(ForgeCapabilityStorageAdapter.INSTANCE);
            RefinedStorage2Compat.init();
        });
        LOGGER.info("FutureShops common setup complete.");
    }

    private void enqueueIMC(final InterModEnqueueEvent event) {
        if (ModList.get().isLoaded("carryon")) {
            InterModComms.sendTo("carryon", "blacklistBlock", () -> MODID + ":shop_block");
            LOGGER.info("Sent CarryOn IMC blacklist for {}:shop_block", MODID);
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        MinecraftServer server = event.getServer();
        if (server.overworld() == null) {
            // Minimal/test server (e.g. MDG's EphemeralTestServer): the worlds aren't loaded at this
            // phase, so overworld-backed SavedData (catalog admin categories, etc.) isn't reachable.
            // A real dedicated/integrated server always has the overworld ready at ServerStartingEvent
            // (which is why the 1.20.1 Forge build did its warm-up here). Skip the data-dependent init.
            LOGGER.info("FutureShops server starting (overworld not yet available — skipping data warm-up).");
            return;
        }
        BalanceManager.initialize(server);
        // SpentMintsSavedData (the anti-dupe ledger) lazy-loads from the overworld on first
        // /withdraw or /deposit — no eager warm-up needed here.
        ShopCatalog.reload(server);
        DynamicPricingEngine.reset();
        StockRefreshScheduler.reset();
        LOGGER.info("FutureShops server starting.");
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        BalanceManager.beginDraining();
        ShopSessionManager.closeAllAndForceClose(event.getServer(), "SERVER_STOPPING");
        BalanceManager.clear();
        DynamicPricingEngine.reset();
        StockRefreshScheduler.reset();
        ShopConfigClipboard.clearAll();
        LOGGER.info("FutureShops server stopping.");
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        // Both schedulers reach overworld-backed SavedData (stock/pricing). A real server always has
        // the overworld loaded while ticking; the minimal EphemeralTestServer ticks without it, so
        // skip rather than NPE (which would otherwise crash the server thread).
        if (server.overworld() == null) {
            return;
        }
        DynamicPricingEngine.onServerTick(server);
        StockRefreshScheduler.onServerTick(server);
    }

    @EventBusSubscriber(modid = MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerBlockEntityRenderer(ModBlockEntities.SHOP_BLOCK_ENTITY.get(), ShopBlockGeoRenderer::new);
        }

        @SubscribeEvent
        public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
            // BlockItem custom inventory renderer (GeckoLib 3D model), formerly Forge initializeClient.
            event.registerItem(new IClientItemExtensions() {
                private ShopBlockItemRenderer renderer;

                @Override
                public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                    if (renderer == null) {
                        renderer = ShopBlockItemRenderer.create();
                    }
                    return renderer;
                }
            }, ModItems.SHOP_BLOCK_ITEM.get());
        }
    }
}
