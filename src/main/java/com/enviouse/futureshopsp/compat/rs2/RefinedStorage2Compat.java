package com.enviouse.futureshopsp.compat.rs2;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

/**
 * STUB soft-dependency hook for Refined Storage. The RS 1.21.1 (RS2) API integration is a separate
 * task; for now we only detect presence and log. No adapter is registered (see
 * {@link RefinedStorage2StorageAdapter}), so the mod loads cleanly with or without RS installed.
 */
public final class RefinedStorage2Compat {
    private static final Logger LOGGER = LogUtils.getLogger();

    private RefinedStorage2Compat() {
    }

    public static void init() {
        if (ModList.get().isLoaded("refinedstorage")) {
            LOGGER.info("Refined Storage detected; RS2 1.21.1 integration is not yet ported (stubbed).");
        }
    }
}
