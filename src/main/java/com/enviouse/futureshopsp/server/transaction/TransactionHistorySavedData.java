package com.enviouse.futureshopsp.server.transaction;

import com.enviouse.futureshopsp.data.TransactionHistoryEntry;
import com.enviouse.futureshopsp.server.SavedDataMigrations;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Persistent per-player transaction history scaffold. */
public final class TransactionHistorySavedData extends SavedData {
    public static final String DATA_NAME = "futureshops_tx_history";
    private static final int CURRENT_VERSION = 1;
    private static final int MAX_ENTRIES_PER_PLAYER = 200;

    private final Map<UUID, List<TransactionHistoryEntry>> entriesByPlayer = new HashMap<>();

    public static TransactionHistorySavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        TransactionHistorySavedData data = new TransactionHistorySavedData();
        int version = SavedDataMigrations.readVersion(tag);
        SavedDataMigrations.needsMigration(DATA_NAME, version, CURRENT_VERSION);
        ListTag players = tag.getList("players", Tag.TAG_COMPOUND);
        for (Tag playerTag : players) {
            CompoundTag playerCompound = (CompoundTag) playerTag;
            if (!playerCompound.hasUUID("uuid")) {
                continue;
            }
            UUID uuid = playerCompound.getUUID("uuid");
            ListTag entriesTag = playerCompound.getList("entries", Tag.TAG_COMPOUND);
            List<TransactionHistoryEntry> entries = new ArrayList<>();
            for (Tag entryTag : entriesTag) {
                CompoundTag tx = (CompoundTag) entryTag;
                entries.add(new TransactionHistoryEntry(
                        tx.getLong("ts"),
                        tx.getString("type"),
                        tx.getString("item"),
                        tx.getInt("qty"),
                        tx.getLong("total"),
                        tx.getString("note")));
            }
            data.entriesByPlayer.put(uuid, entries);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        SavedDataMigrations.writeVersion(tag, CURRENT_VERSION);
        ListTag players = new ListTag();
        for (Map.Entry<UUID, List<TransactionHistoryEntry>> entry : entriesByPlayer.entrySet()) {
            CompoundTag playerTag = new CompoundTag();
            playerTag.putUUID("uuid", entry.getKey());
            ListTag entries = new ListTag();
            for (TransactionHistoryEntry tx : entry.getValue()) {
                CompoundTag txTag = new CompoundTag();
                txTag.putLong("ts", tx.timestampEpochSeconds());
                txTag.putString("type", tx.type());
                txTag.putString("item", tx.itemId());
                txTag.putInt("qty", tx.quantity());
                txTag.putLong("total", tx.totalMinorUnits());
                txTag.putString("note", tx.note());
                entries.add(txTag);
            }
            playerTag.put("entries", entries);
            players.add(playerTag);
        }
        tag.put("players", players);
        return tag;
    }

    public static TransactionHistorySavedData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(new SavedData.Factory<>(TransactionHistorySavedData::new, TransactionHistorySavedData::load, null), DATA_NAME);
    }

    public void append(UUID playerUUID, TransactionHistoryEntry entry) {
        List<TransactionHistoryEntry> entries = entriesByPlayer.computeIfAbsent(playerUUID, ignored -> new ArrayList<>());
        entries.add(0, entry);
        if (entries.size() > MAX_ENTRIES_PER_PLAYER) {
            entries.subList(MAX_ENTRIES_PER_PLAYER, entries.size()).clear();
        }
        setDirty();
    }

    public List<TransactionHistoryEntry> getPage(UUID playerUUID, int page, int pageSize) {
        return getPage(playerUUID, page, pageSize, TransactionHistoryEntry.HistoryFilter.ALL);
    }

    public List<TransactionHistoryEntry> getPage(UUID playerUUID, int page, int pageSize, TransactionHistoryEntry.HistoryFilter filter) {
        return getPage(playerUUID, page, pageSize, filter, "", TransactionHistoryEntry.SortOrder.NEWEST, TransactionHistoryEntry.TimeWindow.ALL);
    }

    public List<TransactionHistoryEntry> getPage(UUID playerUUID, int page, int pageSize,
                                                 TransactionHistoryEntry.HistoryFilter filter,
                                                 String searchText,
                                                 TransactionHistoryEntry.SortOrder sortOrder,
                                                 TransactionHistoryEntry.TimeWindow timeWindow) {
        List<TransactionHistoryEntry> entries = entriesByPlayer.getOrDefault(playerUUID, List.of());
        String query = searchText == null ? "" : searchText.trim().toLowerCase(java.util.Locale.ROOT);
        TransactionHistoryEntry.SortOrder safeSort = sortOrder == null ? TransactionHistoryEntry.SortOrder.NEWEST : sortOrder;
        TransactionHistoryEntry.TimeWindow safeWindow = timeWindow == null ? TransactionHistoryEntry.TimeWindow.ALL : timeWindow;

        java.util.stream.Stream<TransactionHistoryEntry> stream = entries.stream()
                .filter(entry -> filter.matches(entry))
                .filter(entry -> safeWindow.matches(entry.timestampEpochSeconds()));
        if (!query.isBlank()) {
            stream = stream.filter(entry -> {
                String type = entry.type() == null ? "" : entry.type().toLowerCase(java.util.Locale.ROOT);
                String item = entry.itemId() == null ? "" : entry.itemId().toLowerCase(java.util.Locale.ROOT);
                String note = entry.note() == null ? "" : entry.note().toLowerCase(java.util.Locale.ROOT);
                return type.contains(query) || item.contains(query) || note.contains(query);
            });
        }

        List<TransactionHistoryEntry> filteredEntries = stream
                .sorted(safeSort == TransactionHistoryEntry.SortOrder.NEWEST
                        ? java.util.Comparator.comparingLong(TransactionHistoryEntry::timestampEpochSeconds).reversed()
                        : java.util.Comparator.comparingLong(TransactionHistoryEntry::timestampEpochSeconds))
                .toList();
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, pageSize);
        int from = (safePage - 1) * safePageSize;
        if (from >= filteredEntries.size()) {
            return List.of();
        }
        int to = Math.min(filteredEntries.size(), from + safePageSize);
        return List.copyOf(filteredEntries.subList(from, to));
    }

    public int getTotalPages(UUID playerUUID, int pageSize) {
        return getTotalPages(playerUUID, pageSize, TransactionHistoryEntry.HistoryFilter.ALL);
    }

    public int getTotalPages(UUID playerUUID, int pageSize, TransactionHistoryEntry.HistoryFilter filter) {
        return getTotalPages(playerUUID, pageSize, filter, "", TransactionHistoryEntry.SortOrder.NEWEST, TransactionHistoryEntry.TimeWindow.ALL);
    }

    public int getTotalPages(UUID playerUUID, int pageSize,
                             TransactionHistoryEntry.HistoryFilter filter,
                             String searchText,
                             TransactionHistoryEntry.SortOrder sortOrder,
                             TransactionHistoryEntry.TimeWindow timeWindow) {
        int safePageSize = Math.max(1, pageSize);
        String query = searchText == null ? "" : searchText.trim().toLowerCase(java.util.Locale.ROOT);
        TransactionHistoryEntry.TimeWindow safeWindow = timeWindow == null ? TransactionHistoryEntry.TimeWindow.ALL : timeWindow;
        int count = (int) entriesByPlayer.getOrDefault(playerUUID, List.of()).stream()
                .filter(entry -> filter.matches(entry))
                .filter(entry -> safeWindow.matches(entry.timestampEpochSeconds()))
                .filter(entry -> {
                    if (query.isBlank()) {
                        return true;
                    }
                    String type = entry.type() == null ? "" : entry.type().toLowerCase(java.util.Locale.ROOT);
                    String item = entry.itemId() == null ? "" : entry.itemId().toLowerCase(java.util.Locale.ROOT);
                    String note = entry.note() == null ? "" : entry.note().toLowerCase(java.util.Locale.ROOT);
                    return type.contains(query) || item.contains(query) || note.contains(query);
                })
                .count();
        return Math.max(1, (int) Math.ceil((double) count / safePageSize));
    }

    public Map<UUID, List<TransactionHistoryEntry>> snapshotEntriesByPlayer() {
        Map<UUID, List<TransactionHistoryEntry>> snapshot = new HashMap<>();
        entriesByPlayer.forEach((uuid, entries) -> snapshot.put(uuid, List.copyOf(entries)));
        return snapshot;
    }
}

