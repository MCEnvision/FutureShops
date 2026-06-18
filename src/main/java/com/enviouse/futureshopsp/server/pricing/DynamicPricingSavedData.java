package com.enviouse.futureshopsp.server.pricing;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistent storage for dynamic pricing activity counters and current price offsets.
 * Each key is "{shopId}:{itemId}".
 */
public class DynamicPricingSavedData extends SavedData {
    private static final String DATA_NAME = "futureshops_dynamic_pricing";
    private static final int SCHEMA_VERSION = 1;

    /** Composite key → pricing state */
    private final Map<String, ItemPricingState> states = new ConcurrentHashMap<>();

    public DynamicPricingSavedData() {
    }

    public static DynamicPricingSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(new SavedData.Factory<>(DynamicPricingSavedData::new, DynamicPricingSavedData::load, null), DATA_NAME);
    }

    // ---- Activity recording ----

    public void recordBuy(String shopId, String itemId, int quantity) {
        String key = key(shopId, itemId);
        states.computeIfAbsent(key, k -> new ItemPricingState()).addBuys(quantity);
        setDirty();
    }

    public void recordSell(String shopId, String itemId, int quantity) {
        String key = key(shopId, itemId);
        states.computeIfAbsent(key, k -> new ItemPricingState()).addSells(quantity);
        setDirty();
    }

    // ---- Pricing queries ----

    public ItemPricingState getState(String shopId, String itemId) {
        return states.computeIfAbsent(key(shopId, itemId), k -> new ItemPricingState());
    }

    public Map<String, ItemPricingState> allStates() {
        return states;
    }

    public void markDirtyExplicit() {
        setDirty();
    }

    // ---- Persistence ----

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt("schemaVersion", SCHEMA_VERSION);
        CompoundTag statesTag = new CompoundTag();
        for (Map.Entry<String, ItemPricingState> entry : states.entrySet()) {
            CompoundTag stateTag = new CompoundTag();
            ItemPricingState s = entry.getValue();
            stateTag.putInt("buys", s.buysSinceLastCalc);
            stateTag.putInt("sells", s.sellsSinceLastCalc);
            stateTag.putLong("currentPrice", s.currentPriceMinor);
            statesTag.put(entry.getKey(), stateTag);
        }
        tag.put("States", statesTag);
        return tag;
    }

    private static DynamicPricingSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        DynamicPricingSavedData data = new DynamicPricingSavedData();
        // Schema version is read for future migration; currently v1 only
        if (tag.contains("States", Tag.TAG_COMPOUND)) {
            CompoundTag statesTag = tag.getCompound("States");
            for (String key : statesTag.getAllKeys()) {
                CompoundTag stateTag = statesTag.getCompound(key);
                ItemPricingState state = new ItemPricingState();
                state.buysSinceLastCalc = stateTag.getInt("buys");
                state.sellsSinceLastCalc = stateTag.getInt("sells");
                state.currentPriceMinor = stateTag.getLong("currentPrice");
                data.states.put(key, state);
            }
        }
        return data;
    }

    private static String key(String shopId, String itemId) {
        return shopId + ":" + itemId;
    }

    /**
     * Mutable pricing state per item per shop.
     */
    public static class ItemPricingState {
        public int buysSinceLastCalc;
        public int sellsSinceLastCalc;
        /** 0 means "use base price" (no dynamic adjustment yet). */
        public long currentPriceMinor;

        public void addBuys(int count) {
            buysSinceLastCalc += count;
        }

        public void addSells(int count) {
            sellsSinceLastCalc += count;
        }

        public void resetCounters() {
            buysSinceLastCalc = 0;
            sellsSinceLastCalc = 0;
        }
    }
}
