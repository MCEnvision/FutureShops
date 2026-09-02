package com.enviouse.futureshops.server.shop;

import com.enviouse.futureshops.server.SavedDataMigrations;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
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
    private static final int MAXIMUM_REFRESH_KEYS = 100_000;
    private static final int MAXIMUM_KEY_LENGTH = 512;

    /** Composite key → last refresh epoch millis */
    private final Map<String, Long> lastRefreshMillis = new ConcurrentHashMap<>();

    public StockRefreshSavedData() {
    }

    public static StockRefreshSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(StockRefreshSavedData::load, StockRefreshSavedData::new, DATA_NAME);
    }

    public long getLastRefresh(String compositeKey) {
        return lastRefreshMillis.getOrDefault(compositeKey, 0L);
    }

    public void setLastRefresh(String compositeKey, long epochMillis) {
        if (compositeKey == null || compositeKey.isBlank()
                || compositeKey.length() > MAXIMUM_KEY_LENGTH || epochMillis < 0L) {
            throw new IllegalArgumentException("Stock refresh entry is invalid");
        }
        if (!lastRefreshMillis.containsKey(compositeKey)
                && lastRefreshMillis.size() >= MAXIMUM_REFRESH_KEYS) {
            throw new IllegalStateException("Stock refresh entry limit is exceeded");
        }
        lastRefreshMillis.put(compositeKey, epochMillis);
        setDirty();
    }

    // ---- Persistence ----

    @Override
    public CompoundTag save(CompoundTag tag) {
        if (lastRefreshMillis.size() > MAXIMUM_REFRESH_KEYS) {
            throw new IllegalStateException("Stock refresh entry limit is exceeded");
        }
        SavedDataMigrations.writeVersion(tag, CURRENT_VERSION);
        CompoundTag refreshes = new CompoundTag();
        for (Map.Entry<String, Long> entry : lastRefreshMillis.entrySet()) {
            refreshes.putLong(entry.getKey(), entry.getValue());
        }
        tag.put("Refreshes", refreshes);
        return tag;
    }

    static StockRefreshSavedData load(CompoundTag tag) {
        StockRefreshSavedData data = new StockRefreshSavedData();
        SavedDataMigrations.readVersion(tag); // for future migration
        if (tag.contains("Refreshes") && !tag.contains("Refreshes", Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Stock refresh entries must be a compound");
        }
        if (tag.contains("Refreshes", Tag.TAG_COMPOUND)) {
            CompoundTag refreshes = tag.getCompound("Refreshes");
            if (refreshes.getAllKeys().size() > MAXIMUM_REFRESH_KEYS) {
                throw new IllegalArgumentException("Stock refresh entry limit is exceeded");
            }
            for (String key : refreshes.getAllKeys()) {
                long value = refreshes.getLong(key);
                if (key.isBlank() || key.length() > MAXIMUM_KEY_LENGTH || value < 0L) {
                    throw new IllegalArgumentException("Stock refresh entry is invalid");
                }
                data.lastRefreshMillis.put(key, value);
            }
        }
        return data;
    }
}
