package com.enviouse.futureshopsp.server.transaction;

import com.enviouse.futureshopsp.server.util.PageBounds;

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
    private static final int MAX_PLAYERS = 10_000;
    private static final int MAX_ENTRIES_PER_PLAYER = 200;
    private static final int MAX_TYPE_LENGTH = 64;
    private static final int MAX_ITEM_ID_LENGTH = 256;
    private static final int MAX_NOTE_LENGTH = 512;
    private static final int MAX_SEARCH_LENGTH = 256;
    private static final int MAX_QUANTITY = 1_000_000;

    private final Map<UUID, List<TransactionHistoryEntry>> entriesByPlayer = new HashMap<>();
    private boolean integrityValid = true;

    public static TransactionHistorySavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        TransactionHistorySavedData data = new TransactionHistorySavedData();
        int version = SavedDataMigrations.readVersion(tag);
        if (version > CURRENT_VERSION) {
            data.integrityValid = false;
            return data;
        }
        SavedDataMigrations.needsMigration(DATA_NAME, version, CURRENT_VERSION);
        Tag playersTag = tag.get("players");
        if (playersTag != null && !(playersTag instanceof ListTag)) {
            data.integrityValid = false;
            return data;
        }
        ListTag players = playersTag instanceof ListTag list ? list : new ListTag();
        if (players.size() > MAX_PLAYERS) {
            data.integrityValid = false;
            return data;
        }
        Map<UUID, List<TransactionHistoryEntry>> staged = new HashMap<>();
        for (Tag playerTag : players) {
            if (!(playerTag instanceof CompoundTag playerCompound)
                    || !playerCompound.hasUUID("uuid")) {
                data.integrityValid = false;
                return data;
            }
            UUID uuid = playerCompound.getUUID("uuid");
            Tag entriesValue = playerCompound.get("entries");
            if (!(entriesValue instanceof ListTag entriesTag)
                    || entriesTag.size() > MAX_ENTRIES_PER_PLAYER) {
                data.integrityValid = false;
                return data;
            }
            List<TransactionHistoryEntry> entries = new ArrayList<>();
            for (Tag entryTag : entriesTag) {
                if (!(entryTag instanceof CompoundTag tx)
                        || !tx.contains("ts", Tag.TAG_LONG)
                        || !tx.contains("type", Tag.TAG_STRING)
                        || !tx.contains("item", Tag.TAG_STRING)
                        || !tx.contains("qty", Tag.TAG_INT)
                        || !tx.contains("total", Tag.TAG_LONG)
                        || !tx.contains("note", Tag.TAG_STRING)) {
                    data.integrityValid = false;
                    return data;
                }
                TransactionHistoryEntry entry = new TransactionHistoryEntry(
                        tx.getLong("ts"), tx.getString("type"), tx.getString("item"),
                        tx.getInt("qty"), tx.getLong("total"), tx.getString("note"));
                if (!isValidEntry(entry)) {
                    data.integrityValid = false;
                    return data;
                }
                entries.add(entry);
            }
            if (staged.put(uuid, entries) != null) {
                data.integrityValid = false;
                return data;
            }
        }
        data.entriesByPlayer.putAll(staged);
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

    public synchronized void append(UUID playerUUID, TransactionHistoryEntry entry) {
        if (playerUUID == null || !isValidEntry(entry)
                || (!entriesByPlayer.containsKey(playerUUID) && entriesByPlayer.size() >= MAX_PLAYERS)) {
            return;
        }
        List<TransactionHistoryEntry> entries = entriesByPlayer.computeIfAbsent(playerUUID, ignored -> new ArrayList<>());
        entries.add(0, entry);
        if (entries.size() > MAX_ENTRIES_PER_PLAYER) {
            entries.subList(MAX_ENTRIES_PER_PLAYER, entries.size()).clear();
        }
        setDirty();
    }

    public synchronized List<TransactionHistoryEntry> getPage(UUID playerUUID, int page, int pageSize) {
        return getPage(playerUUID, page, pageSize, TransactionHistoryEntry.HistoryFilter.ALL);
    }

    public synchronized List<TransactionHistoryEntry> getPage(UUID playerUUID, int page, int pageSize, TransactionHistoryEntry.HistoryFilter filter) {
        return getPage(playerUUID, page, pageSize, filter, "", TransactionHistoryEntry.SortOrder.NEWEST, TransactionHistoryEntry.TimeWindow.ALL);
    }

    public synchronized List<TransactionHistoryEntry> getPage(UUID playerUUID, int page, int pageSize,
                                                                 TransactionHistoryEntry.HistoryFilter filter,
                                                                 String searchText,
                                                                 TransactionHistoryEntry.SortOrder sortOrder,
                                                                 TransactionHistoryEntry.TimeWindow timeWindow) {
        List<TransactionHistoryEntry> entries = entriesByPlayer.getOrDefault(playerUUID, List.of());
        String query = searchText == null ? "" : searchText.trim().toLowerCase(java.util.Locale.ROOT);
        if (query.length() > MAX_SEARCH_LENGTH) {
            return List.of();
        }
        TransactionHistoryEntry.HistoryFilter safeFilter = filter == null
                ? TransactionHistoryEntry.HistoryFilter.ALL : filter;
        TransactionHistoryEntry.SortOrder safeSort = sortOrder == null ? TransactionHistoryEntry.SortOrder.NEWEST : sortOrder;
        TransactionHistoryEntry.TimeWindow safeWindow = timeWindow == null ? TransactionHistoryEntry.TimeWindow.ALL : timeWindow;

        java.util.stream.Stream<TransactionHistoryEntry> stream = entries.stream()
                .filter(entry -> safeFilter.matches(entry))
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
        int safePage = PageBounds.normalizePage(page);
        int safePageSize = PageBounds.normalizePageSize(pageSize);
        long fromLong = PageBounds.offset(safePage, safePageSize);
        if (fromLong >= filteredEntries.size()) {
            return List.of();
        }
        int from = (int) fromLong;
        int to = (int) Math.min((long) filteredEntries.size(), fromLong + safePageSize);
        return List.copyOf(filteredEntries.subList(from, to));
    }

    public synchronized int getTotalPages(UUID playerUUID, int pageSize) {
        return getTotalPages(playerUUID, pageSize, TransactionHistoryEntry.HistoryFilter.ALL);
    }

    public synchronized int getTotalPages(UUID playerUUID, int pageSize, TransactionHistoryEntry.HistoryFilter filter) {
        return getTotalPages(playerUUID, pageSize, filter, "", TransactionHistoryEntry.SortOrder.NEWEST, TransactionHistoryEntry.TimeWindow.ALL);
    }

    public synchronized int getTotalPages(UUID playerUUID, int pageSize,
                                             TransactionHistoryEntry.HistoryFilter filter,
                                             String searchText,
                                             TransactionHistoryEntry.SortOrder sortOrder,
                                             TransactionHistoryEntry.TimeWindow timeWindow) {
        int safePageSize = Math.max(1, pageSize);
        String query = searchText == null ? "" : searchText.trim().toLowerCase(java.util.Locale.ROOT);
        if (query.length() > MAX_SEARCH_LENGTH) {
            return 1;
        }
        TransactionHistoryEntry.HistoryFilter safeFilter = filter == null
                ? TransactionHistoryEntry.HistoryFilter.ALL : filter;
        TransactionHistoryEntry.TimeWindow safeWindow = timeWindow == null ? TransactionHistoryEntry.TimeWindow.ALL : timeWindow;
        int count = (int) entriesByPlayer.getOrDefault(playerUUID, List.of()).stream()
                .filter(entry -> safeFilter.matches(entry))
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

    public synchronized Map<UUID, List<TransactionHistoryEntry>> snapshotEntriesByPlayer() {
        Map<UUID, List<TransactionHistoryEntry>> snapshot = new HashMap<>();
        entriesByPlayer.forEach((uuid, entries) -> snapshot.put(uuid, List.copyOf(entries)));
        return snapshot;
    }

    public synchronized boolean integrityValid() {
        return integrityValid;
    }

    private static boolean isValidEntry(TransactionHistoryEntry entry) {
        return entry != null && entry.type() != null && entry.itemId() != null && entry.note() != null
                && isBoundedText(entry.type(), MAX_TYPE_LENGTH)
                && isBoundedText(entry.itemId(), MAX_ITEM_ID_LENGTH)
                && isBoundedText(entry.note(), MAX_NOTE_LENGTH)
                && entry.quantity() >= 0 && entry.quantity() <= MAX_QUANTITY;
    }

    private static boolean isBoundedText(String value, int maxLength) {
        return value.length() <= maxLength && value.indexOf('\n') < 0 && value.indexOf('\r') < 0;
    }
}
