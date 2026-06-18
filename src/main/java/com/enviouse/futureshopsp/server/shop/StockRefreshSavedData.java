package com.enviouse.futureshopsp.server.shop;

import com.enviouse.futureshopsp.server.SavedDataMigrations;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persists last-refresh timestamps for the stock refresh scheduler (spec §31).
 * Each key is "{shopId}:{itemId}", value is epoch millis of last stock reset.
 */
public class StockRefreshSavedData extends SavedData {
    public static final String DATA_NAME = "futureshops_stock_refresh";
    private static final int CURRENT_VERSION = 1;

    /** Composite key → last refresh epoch millis */
    private final Map<String, Long> lastRefreshMillis = new ConcurrentHashMap<>();

    public StockRefreshSavedData() {
    }

    public static StockRefreshSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(new SavedData.Factory<>(StockRefreshSavedData::new, StockRefreshSavedData::load, null), DATA_NAME);
    }

    public long getLastRefresh(String compositeKey) {
        return lastRefreshMillis.getOrDefault(compositeKey, 0L);
    }

    public void setLastRefresh(String compositeKey, long epochMillis) {
        lastRefreshMillis.put(compositeKey, epochMillis);
        setDirty();
    }

    // ---- Persistence ----

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        SavedDataMigrations.writeVersion(tag, CURRENT_VERSION);
        CompoundTag refreshes = new CompoundTag();
        for (Map.Entry<String, Long> entry : lastRefreshMillis.entrySet()) {
            refreshes.putLong(entry.getKey(), entry.getValue());
        }
        tag.put("Refreshes", refreshes);
        return tag;
    }

    private static StockRefreshSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        StockRefreshSavedData data = new StockRefreshSavedData();
        SavedDataMigrations.readVersion(tag); // for future migration
        if (tag.contains("Refreshes", Tag.TAG_COMPOUND)) {
            CompoundTag refreshes = tag.getCompound("Refreshes");
            for (String key : refreshes.getAllKeys()) {
                data.lastRefreshMillis.put(key, refreshes.getLong(key));
            }
        }
        return data;
    }
}

