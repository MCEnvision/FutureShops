package com.enviouse.futureshopsp.server.shop;

import com.enviouse.futureshopsp.server.SavedDataMigrations;
import com.enviouse.futureshopsp.server.economy.EconomyRecordChecksum;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Durable exact item records for player shop barter payments. */
public final class PlayerShopBarterEscrowSavedData extends SavedData {
    public static final String DATA_NAME = "futureshops_player_shop_barter_escrow";
    private static final int CURRENT_VERSION = 1;
    private static final int MAX_RECORDS = 10_000;
    private static final int MAX_STACKS_PER_RECORD = 128;

    public enum State {
        PREPARED,
        REMOVED,
        STORED,
        COMPLETE,
        REFUNDED,
        RECOVERY_REQUIRED
    }

    private final Map<UUID, EscrowRecord> records = new LinkedHashMap<>();
    private boolean integrityValid = true;
    private boolean cleanMarkerValid = true;

    public PlayerShopBarterEscrowSavedData() {
    }

    public static PlayerShopBarterEscrowSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PlayerShopBarterEscrowSavedData::new,
                        PlayerShopBarterEscrowSavedData::load, null), DATA_NAME);
    }

    public static PlayerShopBarterEscrowSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        PlayerShopBarterEscrowSavedData data = new PlayerShopBarterEscrowSavedData();
        int version = SavedDataMigrations.readVersion(tag);
        if (version > CURRENT_VERSION) {
            data.integrityValid = false;
            return data;
        }
        SavedDataMigrations.needsMigration(DATA_NAME, version, CURRENT_VERSION);
        if (tag.contains("cleanMarker", Tag.TAG_BYTE)) {
            data.cleanMarkerValid = tag.getBoolean("cleanMarker");
        }
        ListTag entries = tag.getList("records", Tag.TAG_COMPOUND);
        if (entries.size() > MAX_RECORDS) {
            data.integrityValid = false;
            return data;
        }
        for (Tag raw : entries) {
            if (!(raw instanceof CompoundTag entry)) {
                data.integrityValid = false;
                continue;
            }
            try {
                EscrowRecord record = readEntry(entry);
                if (!entry.getString("checksum").equals(checksum(record))) {
                    data.integrityValid = false;
                    continue;
                }
                if (data.records.put(record.requestId(), record) != null) {
                    data.integrityValid = false;
                }
            } catch (RuntimeException exception) {
                data.integrityValid = false;
            }
        }
        return data;
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        SavedDataMigrations.writeVersion(tag, CURRENT_VERSION);
        ListTag entries = new ListTag();
        for (EscrowRecord record : records.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("request", record.requestId());
            entry.putUUID("buyer", record.buyer());
            entry.putLong("shopPos", record.shopPos());
            entry.putString("dimension", record.dimension());
            entry.putString("itemId", record.itemId());
            entry.putInt("quantity", record.quantity());
            entry.putString("state", record.state().name());
            ListTag stacks = new ListTag();
            for (CompoundTag stack : record.stacks()) {
                stacks.add(stack.copy());
            }
            entry.put("stacks", stacks);
            entry.putString("checksum", checksum(record));
            entries.add(entry);
        }
        tag.put("records", entries);
        tag.putBoolean("cleanMarker", cleanMarkerValid);
        return tag;
    }

    public synchronized boolean prepare(UUID requestId, UUID buyer, long shopPos, String dimension,
                                         String itemId, int quantity, List<ItemStack> stacks,
                                         HolderLookup.Provider provider) {
        if (requestId == null || buyer == null || quantity <= 0 || stacks == null
                || stacks.isEmpty() || stacks.size() > MAX_STACKS_PER_RECORD
                || itemId == null || itemId.isBlank() || records.containsKey(requestId)
                || records.size() >= MAX_RECORDS) {
            return false;
        }
        List<CompoundTag> encoded = new ArrayList<>();
        int encodedQuantity = 0;
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()) {
                return false;
            }
            encodedQuantity = Math.addExact(encodedQuantity, stack.getCount());
            encoded.add((CompoundTag) stack.save(provider));
        }
        if (encodedQuantity != quantity) {
            return false;
        }
        EscrowRecord record = new EscrowRecord(requestId, buyer, shopPos,
                dimension == null ? "" : dimension, itemId, quantity, encoded, State.PREPARED);
        records.put(requestId, record);
        setDirty();
        return true;
    }

    public synchronized boolean markRemoved(UUID requestId, List<ItemStack> stacks, HolderLookup.Provider provider) {
        return transitionWithContent(requestId, State.PREPARED, State.REMOVED, stacks, provider);
    }

    public synchronized boolean markStored(UUID requestId) {
        return transition(requestId, State.REMOVED, State.STORED);
    }

    public synchronized boolean markComplete(UUID requestId) {
        EscrowRecord current = records.get(requestId);
        if (current == null || (current.state() != State.STORED && current.state() != State.REMOVED)) {
            return false;
        }
        records.put(requestId, current.withState(State.COMPLETE));
        setDirty();
        return true;
    }

    public synchronized boolean markRefunded(UUID requestId) {
        EscrowRecord current = records.get(requestId);
        if (current == null || current.state() == State.COMPLETE || current.state() == State.REFUNDED) {
            return current != null && current.state() == State.REFUNDED;
        }
        records.put(requestId, current.withState(State.REFUNDED));
        setDirty();
        return true;
    }

    public synchronized boolean markRecoveryRequired(UUID requestId) {
        EscrowRecord current = records.get(requestId);
        if (current == null || current.state() == State.COMPLETE || current.state() == State.REFUNDED) {
            return false;
        }
        records.put(requestId, current.withState(State.RECOVERY_REQUIRED));
        setDirty();
        return true;
    }

    public synchronized EscrowRecord find(UUID requestId) {
        return records.get(requestId);
    }

    public synchronized List<EscrowRecord> snapshot() {
        return List.copyOf(records.values());
    }

    public synchronized boolean hasIncompleteRecords() {
        return records.values().stream().anyMatch(record -> record.state() != State.COMPLETE
                && record.state() != State.REFUNDED);
    }

    public synchronized boolean integrityValid() {
        return integrityValid;
    }

    public synchronized boolean cleanMarkerValid() {
        return cleanMarkerValid;
    }

    public synchronized boolean flush() {
        return true;
    }

    public synchronized void markUnclean() {
        cleanMarkerValid = false;
        setDirty();
    }

    public synchronized void markCleanMarker() {
        cleanMarkerValid = true;
        setDirty();
    }

    private boolean transitionWithContent(UUID requestId, State expected, State next,
                                           List<ItemStack> stacks, HolderLookup.Provider provider) {
        EscrowRecord current = records.get(requestId);
        if (current == null || current.state() != expected || stacks == null || stacks.isEmpty()) {
            return false;
        }
        List<CompoundTag> encoded = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()) {
                return false;
            }
            encoded.add((CompoundTag) stack.save(provider));
        }
        if (!encoded.equals(current.stacks())) {
            return false;
        }
        records.put(requestId, current.withState(next));
        setDirty();
        return true;
    }

    private boolean transition(UUID requestId, State expected, State next) {
        EscrowRecord current = records.get(requestId);
        if (current == null || current.state() != expected) {
            return false;
        }
        records.put(requestId, current.withState(next));
        setDirty();
        return true;
    }

    private static EscrowRecord readEntry(CompoundTag entry) {
        if (!entry.hasUUID("request") || !entry.hasUUID("buyer")
                || !entry.contains("shopPos", Tag.TAG_LONG)
                || !entry.contains("dimension", Tag.TAG_STRING)
                || !entry.contains("itemId", Tag.TAG_STRING)
                || !entry.contains("quantity", Tag.TAG_INT)
                || !entry.contains("state", Tag.TAG_STRING)
                || !entry.contains("stacks", Tag.TAG_LIST)
                || !entry.contains("checksum", Tag.TAG_STRING)) {
            throw new IllegalArgumentException("barter escrow record is incomplete");
        }
        ListTag stackList = entry.getList("stacks", Tag.TAG_COMPOUND);
        if (stackList.isEmpty() || stackList.size() > MAX_STACKS_PER_RECORD) {
            throw new IllegalArgumentException("barter escrow stack list is invalid");
        }
        List<CompoundTag> stacks = new ArrayList<>();
        for (Tag raw : stackList) {
            if (!(raw instanceof CompoundTag stack)) {
                throw new IllegalArgumentException("barter escrow stack is invalid");
            }
            stacks.add(stack.copy());
        }
        return new EscrowRecord(entry.getUUID("request"), entry.getUUID("buyer"), entry.getLong("shopPos"),
                entry.getString("dimension"), entry.getString("itemId"), entry.getInt("quantity"),
                stacks, State.valueOf(entry.getString("state")));
    }

    private static String checksum(EscrowRecord record) {
        return EconomyRecordChecksum.sha256(record.requestId() + "|" + record.buyer() + "|"
                + record.shopPos() + "|" + record.dimension() + "|" + record.itemId() + "|"
                + record.quantity() + "|" + record.state() + "|" + record.stacks());
    }

    public record EscrowRecord(UUID requestId, UUID buyer, long shopPos, String dimension, String itemId,
                               int quantity, List<CompoundTag> stacks, State state) {
        public EscrowRecord {
            stacks = List.copyOf(stacks);
        }

        private EscrowRecord withState(State next) {
            return new EscrowRecord(requestId, buyer, shopPos, dimension, itemId, quantity, stacks, next);
        }
    }
}
