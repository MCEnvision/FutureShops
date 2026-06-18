package com.enviouse.futureshopsp;

import com.enviouse.futureshopsp.init.ModItems;
import com.enviouse.futureshopsp.money.ModDataComponents;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

/**
 * FutureShops mod entrypoint. Built up incrementally as tranches land — currently wires the
 * money item, the coin data component, the legacy coin→money alias, and config. Blocks/BEs/
 * creative tabs/packets/event handlers are registered as their tranches are ported.
 */
@Mod(Futureshops.MODID)
public class Futureshops {
    // Runtime modid + all resource/data namespaces stay "futureshops" (save-compat, Decision A).
    // The Java package is com.enviouse.futureshopsp ("p") — that never touches runtime IDs.
    // Never let the bare string "futureshops" drift to "futureshopsp".
    public static final String MODID = "futureshops";
    private static final Logger LOGGER = LogUtils.getLogger();

    public Futureshops(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);

        // Legacy save-compat: futureshops:coin -> futureshops:money (Change B rename).
        // NeoForge has no MissingMappingsEvent; the replacement is a registry alias, which must be
        // registered before RegisterEvent fires. Lookups of the old id resolve to the new one.
        ModItems.ITEMS.addAlias(
                ResourceLocation.fromNamespaceAndPath(MODID, "coin"),
                ResourceLocation.fromNamespaceAndPath(MODID, "money"));

        // Mod content registration (incremental).
        ModItems.ITEMS.register(modEventBus);
        ModDataComponents.COMPONENTS.register(modEventBus);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // Real common-setup wiring (packets / storage registry / compat) is added in later tranches.
        LOGGER.info("FutureShops common setup");
    }
}
