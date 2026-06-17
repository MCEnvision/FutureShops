package com.enviouse.futureshopsp.server;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import org.slf4j.Logger;

/**
 * Schema versioning and migration utility for all FutureShops SavedData classes.
 * <p>
 * Each SavedData class should:
 * <ol>
 *   <li>Define a {@code CURRENT_VERSION} constant.</li>
 *   <li>Call {@link #writeVersion(CompoundTag, int)} in {@code save()}.</li>
 *   <li>Call {@link #readVersion(CompoundTag)} in {@code load()} and dispatch through
 *       their migration chain if the loaded version is older.</li>
 * </ol>
 * <p>
 * Migration functions are per-class. This utility provides the read/write hooks
 * and a logging framework for tracking migrations.
 */
public final class SavedDataMigrations {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String VERSION_KEY = "schemaVersion";

    private SavedDataMigrations() {
    }

    /**
     * Reads the schema version from a tag. Returns 0 if not present (legacy data).
     */
    public static int readVersion(CompoundTag tag) {
        return tag.contains(VERSION_KEY) ? tag.getInt(VERSION_KEY) : 0;
    }

    /**
     * Writes the schema version into a tag.
     */
    public static void writeVersion(CompoundTag tag, int version) {
        tag.putInt(VERSION_KEY, version);
    }

    /**
     * Logs a migration event.
     */
    public static void logMigration(String dataName, int fromVersion, int toVersion) {
        LOGGER.info("[FutureShops] Migrating {} from schema v{} to v{}", dataName, fromVersion, toVersion);
    }

    /**
     * Checks if migration is needed and logs appropriately.
     * Returns true if the loaded version is older than current.
     */
    public static boolean needsMigration(String dataName, int loadedVersion, int currentVersion) {
        if (loadedVersion < currentVersion) {
            logMigration(dataName, loadedVersion, currentVersion);
            return true;
        }
        if (loadedVersion > currentVersion) {
            LOGGER.warn("[FutureShops] {} has schema v{} which is NEWER than current v{}. " +
                    "Data may not load correctly if downgrading.", dataName, loadedVersion, currentVersion);
        }
        return false;
    }
}

