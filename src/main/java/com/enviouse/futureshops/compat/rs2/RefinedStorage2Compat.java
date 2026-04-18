package com.enviouse.futureshops.compat.rs2;

import com.enviouse.futureshops.server.shop.ExternalStorageAdapter;
import com.enviouse.futureshops.server.shop.ExternalStorageRegistry;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

/**
 * Soft-dependency integration with Refined Storage 2.
 * If RS2 is loaded, registers an {@link ExternalStorageAdapter} that queries RS2 networks
 * for item storage.
 *
 * <p>Since RS2 is a compile-only dependency, all RS2-specific classes are isolated in
 * this package to avoid ClassNotFoundError when RS2 is not present.
 */
public final class RefinedStorage2Compat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String RS2_MOD_ID = "refinedstorage2";
    private static final String RS1_MOD_ID = "refinedstorage";

    private RefinedStorage2Compat() {
    }

    /**
     * Call during common setup. If RS is loaded (either mod ID), registers the storage adapter.
     * Safe to call even if RS is not present.
     */
    public static void init() {
        if (ModList.get().isLoaded(RS1_MOD_ID) || ModList.get().isLoaded(RS2_MOD_ID)) {
            try {
                // Isolate RS2-dependent code in a separate class to prevent class loading errors
                RefinedStorage2AdapterBootstrap.register();
                LOGGER.info("FutureShops: Refined Storage 2 integration enabled.");
            } catch (Throwable t) {
                LOGGER.warn("FutureShops: Refined Storage 2 detected but integration failed: {}", t.getMessage());
            }
        } else {
            LOGGER.info("FutureShops: Refined Storage 2 not detected — RS2 integration disabled.");
        }
    }

    /**
     * Isolated bootstrap class. Only loaded when RS2 is confirmed present.
     * This prevents NoClassDefFoundError from propagating to the main compat class.
     */
    static final class RefinedStorage2AdapterBootstrap {
        static void register() {
            // For now, register a placeholder adapter that uses Forge capabilities.
            // When the RS2 API stabilizes, this will use the RS2 network query API.
            // RS2 blocks expose IItemHandler via Forge capabilities anyway,
            // so the ForgeCapabilityStorageAdapter already handles them.
            // This class exists as the integration hook point for future RS2-native queries.

            // Register the RS2-specific adapter that queries the RS2 network API directly
            ExternalStorageRegistry.register(new RefinedStorage2StorageAdapter());
        }
    }
}

