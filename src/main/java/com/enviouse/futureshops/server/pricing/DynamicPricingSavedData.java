package com.enviouse.futureshops.server.pricing;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Map;
import java.util.LinkedHashSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistent storage for dynamic pricing activity counters and current price offsets.
 * Each key is "{shopId}:{itemId}".
 */
public class DynamicPricingSavedData extends SavedData {
    private static final String DATA_NAME = "futureshops_dynamic_pricing";
    private static final int SCHEMA_VERSION = 2;
    private static final int MAXIMUM_STATES = 100_000;
    private static final int MAXIMUM_KEY_LENGTH = 512;
    private static final int MAXIMUM_ACTIVITY_RECEIPTS = 10_000;

    /** Composite key → pricing state */
    private final Map<String, ItemPricingState> states = new ConcurrentHashMap<>();
    private final LinkedHashSet<String> activityReceipts =
            new LinkedHashSet<>();

    public DynamicPricingSavedData() {
    }

    public static DynamicPricingSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(DynamicPricingSavedData::load, DynamicPricingSavedData::new, DATA_NAME);
    }

    // ---- Activity recording ----

    public void recordBuy(String shopId, String itemId, int quantity) {
        requireActivity(shopId, itemId, quantity);
        String key = key(shopId, itemId);
        ensureStateCapacity(key);
        states.computeIfAbsent(key, k -> new ItemPricingState()).addBuys(quantity);
        setDirty();
    }

    public void recordSell(String shopId, String itemId, int quantity) {
        requireActivity(shopId, itemId, quantity);
        String key = key(shopId, itemId);
        ensureStateCapacity(key);
        states.computeIfAbsent(key, k -> new ItemPricingState()).addSells(quantity);
        setDirty();
    }

    public synchronized boolean recordBuyOnce(
            String receipt,
            String shopId,
            String itemId,
            int quantity
    ) {
        return recordOnce(receipt, shopId, itemId, quantity, true);
    }

    public synchronized boolean recordSellOnce(
            String receipt,
            String shopId,
            String itemId,
            int quantity
    ) {
        return recordOnce(receipt, shopId, itemId, quantity, false);
    }

    private boolean recordOnce(
            String receipt,
            String shopId,
            String itemId,
            int quantity,
            boolean buy
    ) {
        if (receipt == null || receipt.isBlank()
                || receipt.length() > 512 || quantity <= 0) {
            throw new IllegalArgumentException(
                    "Dynamic pricing activity receipt is invalid");
        }
        requireActivity(shopId, itemId, quantity);
        if (!activityReceipts.add(receipt)) {
            return false;
        }
        while (activityReceipts.size() > MAXIMUM_ACTIVITY_RECEIPTS) {
            activityReceipts.remove(activityReceipts.iterator().next());
        }
        if (buy) {
            ensureStateCapacity(key(shopId, itemId));
            states.computeIfAbsent(key(shopId, itemId),
                    ignored -> new ItemPricingState()).addBuys(quantity);
        } else {
            states.computeIfAbsent(key(shopId, itemId),
                    ignored -> new ItemPricingState()).addSells(quantity);
        }
        setDirty();
        return true;
    }

    // ---- Pricing queries ----

    public ItemPricingState getState(String shopId, String itemId) {
        String stateKey = key(shopId, itemId);
        ensureStateCapacity(stateKey);
        return states.computeIfAbsent(stateKey, k -> new ItemPricingState());
    }

    public Map<String, ItemPricingState> allStates() {
        return states;
    }

    public void markDirtyExplicit() {
        setDirty();
    }

    // ---- Persistence ----

    @Override
    public CompoundTag save(CompoundTag tag) {
        if (states.size() > MAXIMUM_STATES) {
            throw new IllegalStateException("Dynamic pricing state limit is exceeded");
        }
        tag.putInt("schemaVersion", SCHEMA_VERSION);
        CompoundTag statesTag = new CompoundTag();
        for (Map.Entry<String, ItemPricingState> entry : states.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()
                    || entry.getKey().length() > MAXIMUM_KEY_LENGTH
                    || entry.getValue() == null
                    || entry.getValue().buysSinceLastCalc < 0
                    || entry.getValue().sellsSinceLastCalc < 0
                    || entry.getValue().currentPriceMinor < 0L) {
                throw new IllegalStateException("Dynamic pricing state is invalid");
            }
            CompoundTag stateTag = new CompoundTag();
            ItemPricingState s = entry.getValue();
            stateTag.putInt("buys", s.buysSinceLastCalc);
            stateTag.putInt("sells", s.sellsSinceLastCalc);
            stateTag.putLong("currentPrice", s.currentPriceMinor);
            statesTag.put(entry.getKey(), stateTag);
        }
        tag.put("States", statesTag);
        net.minecraft.nbt.ListTag receipts =
                new net.minecraft.nbt.ListTag();
        for (String receipt : activityReceipts) {
            if (receipt == null || receipt.isBlank() || receipt.length() > 512) {
                throw new IllegalStateException("Dynamic pricing receipt is invalid");
            }
            receipts.add(net.minecraft.nbt.StringTag.valueOf(receipt));
        }
        tag.put("ActivityReceipts", receipts);
        return tag;
    }

    public static DynamicPricingSavedData load(CompoundTag tag) {
        DynamicPricingSavedData data = new DynamicPricingSavedData();
        // Schema version is read for future migration; currently v1 only
        if (tag.contains("States") && !tag.contains("States", Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Dynamic pricing states must be a compound");
        }
        if (tag.contains("States", Tag.TAG_COMPOUND)) {
            CompoundTag statesTag = tag.getCompound("States");
            if (statesTag.getAllKeys().size() > MAXIMUM_STATES) {
                throw new IllegalArgumentException("Dynamic pricing state limit is exceeded");
            }
            for (String key : statesTag.getAllKeys()) {
                if (key.isBlank() || key.length() > MAXIMUM_KEY_LENGTH
                        || !statesTag.contains(key, Tag.TAG_COMPOUND)) {
                    throw new IllegalArgumentException("Dynamic pricing state key is invalid");
                }
                CompoundTag stateTag = statesTag.getCompound(key);
                ItemPricingState state = new ItemPricingState();
                state.buysSinceLastCalc = stateTag.getInt("buys");
                state.sellsSinceLastCalc = stateTag.getInt("sells");
                state.currentPriceMinor = stateTag.getLong("currentPrice");
                if (state.buysSinceLastCalc < 0 || state.sellsSinceLastCalc < 0
                        || state.currentPriceMinor < 0L) {
                    throw new IllegalArgumentException("Dynamic pricing state is invalid");
                }
                data.states.put(key, state);
            }
        }
        net.minecraft.nbt.ListTag receipts =
                com.enviouse.futureshops.server.SavedDataMigrations.requireList(
                        tag, "ActivityReceipts", Tag.TAG_STRING,
                        MAXIMUM_ACTIVITY_RECEIPTS, "Dynamic pricing receipts");
        int start = Math.max(0,
                receipts.size() - MAXIMUM_ACTIVITY_RECEIPTS);
        for (int index = start; index < receipts.size(); index++) {
            String receipt = receipts.getString(index);
            if (!receipt.isBlank() && receipt.length() <= 512) {
                data.activityReceipts.add(receipt);
            }
        }
        return data;
    }

    private static String key(String shopId, String itemId) {
        if (shopId == null || itemId == null) {
            throw new IllegalArgumentException("Dynamic pricing key is invalid");
        }
        String value = shopId + ":" + itemId;
        if (value.isBlank() || value.length() > MAXIMUM_KEY_LENGTH) {
            throw new IllegalArgumentException("Dynamic pricing key is invalid");
        }
        return value;
    }

    private void ensureStateCapacity(String stateKey) {
        if (!states.containsKey(stateKey) && states.size() >= MAXIMUM_STATES) {
            throw new IllegalStateException("Dynamic pricing state limit is exceeded");
        }
    }

    private static void requireActivity(String shopId, String itemId, int quantity) {
        key(shopId, itemId);
        if (quantity <= 0) {
            throw new IllegalArgumentException("Dynamic pricing quantity is invalid");
        }
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
