package com.enviouse.futureshopsp.server.pricing;

import com.enviouse.futureshopsp.server.SavedDataMigrations;
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
    private static final int MAX_STATES = 10_000;
    private static final int MAX_KEY_LENGTH = 512;
    private static final int MAX_ACTIVITY = 1_000_000;

    /** Composite key → pricing state */
    private final Map<String, ItemPricingState> states = new ConcurrentHashMap<>();
    private boolean integrityValid = true;

    public DynamicPricingSavedData() {
    }

    public static DynamicPricingSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(new SavedData.Factory<>(DynamicPricingSavedData::new, DynamicPricingSavedData::load, null), DATA_NAME);
    }

    // ---- Activity recording ----

    public void recordBuy(String shopId, String itemId, int quantity) {
        record(shopId, itemId, quantity, true);
    }

    public void recordSell(String shopId, String itemId, int quantity) {
        record(shopId, itemId, quantity, false);
    }

    // ---- Pricing queries ----

    public ItemPricingState getState(String shopId, String itemId) {
        String key = key(shopId, itemId);
        if (key == null) {
            return new ItemPricingState();
        }
        if (!states.containsKey(key) && states.size() >= MAX_STATES) {
            return new ItemPricingState();
        }
        return states.computeIfAbsent(key, k -> new ItemPricingState());
    }

    public Map<String, ItemPricingState> allStates() {
        return Map.copyOf(states);
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

    public static DynamicPricingSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        DynamicPricingSavedData data = new DynamicPricingSavedData();
        int version = SavedDataMigrations.readVersion(tag);
        if (version > SCHEMA_VERSION) {
            data.integrityValid = false;
            return data;
        }
        SavedDataMigrations.needsMigration(DATA_NAME, version, SCHEMA_VERSION);
        Tag rawStates = tag.get("States");
        if (rawStates != null && !(rawStates instanceof CompoundTag)) {
            data.integrityValid = false;
            return data;
        }
        if (!(rawStates instanceof CompoundTag statesTag)) {
            return data;
        }
        if (statesTag.size() > MAX_STATES) {
            data.integrityValid = false;
            return data;
        }
        Map<String, ItemPricingState> staged = new java.util.HashMap<>();
        for (String key : statesTag.getAllKeys()) {
            if (key.isBlank() || key.length() > MAX_KEY_LENGTH) {
                data.integrityValid = false;
                return data;
            }
            Tag rawState = statesTag.get(key);
            if (!(rawState instanceof CompoundTag stateTag)
                    || !stateTag.contains("buys", Tag.TAG_INT)
                    || !stateTag.contains("sells", Tag.TAG_INT)
                    || !stateTag.contains("currentPrice", Tag.TAG_LONG)) {
                data.integrityValid = false;
                return data;
            }
            ItemPricingState state = new ItemPricingState();
            state.buysSinceLastCalc = stateTag.getInt("buys");
            state.sellsSinceLastCalc = stateTag.getInt("sells");
            state.currentPriceMinor = stateTag.getLong("currentPrice");
            if (!validState(state) || staged.put(key, state) != null) {
                data.integrityValid = false;
                return data;
            }
        }
        data.states.putAll(staged);
        return data;
    }

    private void record(String shopId, String itemId, int quantity, boolean buy) {
        String compositeKey = key(shopId, itemId);
        if (compositeKey == null || quantity <= 0 || quantity > MAX_ACTIVITY) {
            return;
        }
        if (!states.containsKey(compositeKey) && states.size() >= MAX_STATES) {
            return;
        }
        ItemPricingState state = states.computeIfAbsent(compositeKey, ignored -> new ItemPricingState());
        int current = buy ? state.buysSinceLastCalc : state.sellsSinceLastCalc;
        if (current > MAX_ACTIVITY - quantity) {
            return;
        }
        if (buy) {
            state.buysSinceLastCalc = current + quantity;
        } else {
            state.sellsSinceLastCalc = current + quantity;
        }
        setDirty();
    }

    private static String key(String shopId, String itemId) {
        if (shopId == null || itemId == null || shopId.isBlank() || itemId.isBlank()) {
            return null;
        }
        String compositeKey = shopId.trim() + ":" + itemId.trim();
        return compositeKey.length() <= MAX_KEY_LENGTH ? compositeKey : null;
    }

    private static boolean validState(ItemPricingState state) {
        return state.buysSinceLastCalc >= 0 && state.buysSinceLastCalc <= MAX_ACTIVITY
                && state.sellsSinceLastCalc >= 0 && state.sellsSinceLastCalc <= MAX_ACTIVITY
                && state.currentPriceMinor >= 0L;
    }

    public boolean integrityValid() {
        return integrityValid;
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
