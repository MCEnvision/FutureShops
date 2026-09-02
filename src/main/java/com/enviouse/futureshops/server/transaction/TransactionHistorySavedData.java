package com.enviouse.futureshops.server.transaction;

import com.enviouse.futureshops.server.util.PageBounds;
import com.enviouse.futureshops.data.TransactionHistoryEntry;
import com.enviouse.futureshops.server.SavedDataMigrations;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Persistent per-player transaction history scaffold. */
public final class TransactionHistorySavedData extends SavedData {
    public static final String DATA_NAME = "futureshops_tx_history";
    private static final int CURRENT_VERSION = 3;
    private static final int MAX_ENTRIES_PER_PLAYER = 200;

    private final Map<UUID, List<TransactionHistoryEntry>> entriesByPlayer = new HashMap<>();
    private final Map<UUID, Set<String>> idempotencyMarkersByPlayer =
            new HashMap<>();

    public static TransactionHistorySavedData load(CompoundTag tag) {
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
                        tx.getString("note"),
                        tx.getString("nbt"))); // optional; "" when absent (pre-nbt entries)
            }
            data.entriesByPlayer.put(uuid, entries);
        }
        ListTag markerOwners = tag.getList(
                "idempotency_markers", Tag.TAG_COMPOUND);
        for (Tag ownerTag : markerOwners) {
            CompoundTag owner = (CompoundTag) ownerTag;
            if (!owner.hasUUID("uuid")) {
                continue;
            }
            Set<String> markers = new HashSet<>();
            for (Tag markerTag : owner.getList(
                    "markers", Tag.TAG_STRING)) {
                String marker = markerTag.getAsString();
                if (!marker.isBlank()) {
                    markers.add(marker);
                }
            }
            if (!markers.isEmpty()) {
                data.idempotencyMarkersByPlayer.put(
                        owner.getUUID("uuid"), markers);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
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
                txTag.putString("nbt", tx.nbtJson() == null ? "" : tx.nbtJson());
                entries.add(txTag);
            }
            playerTag.put("entries", entries);
            players.add(playerTag);
        }
        tag.put("players", players);
        ListTag markerOwners = new ListTag();
        for (Map.Entry<UUID, Set<String>> entry
                : idempotencyMarkersByPlayer.entrySet()) {
            CompoundTag owner = new CompoundTag();
            owner.putUUID("uuid", entry.getKey());
            ListTag markers = new ListTag();
            entry.getValue().stream().sorted()
                    .map(StringTag::valueOf)
                    .forEach(markers::add);
            owner.put("markers", markers);
            markerOwners.add(owner);
        }
        tag.put("idempotency_markers", markerOwners);
        return tag;
    }

    public static TransactionHistorySavedData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
                TransactionHistorySavedData::load,
                TransactionHistorySavedData::new,
                DATA_NAME);
    }

    public void append(UUID playerUUID, TransactionHistoryEntry entry) {
        List<TransactionHistoryEntry> entries = entriesByPlayer.computeIfAbsent(playerUUID, ignored -> new ArrayList<>());
        entries.add(0, entry);
        if (entries.size() > MAX_ENTRIES_PER_PLAYER) {
            entries.subList(MAX_ENTRIES_PER_PLAYER, entries.size()).clear();
        }
        setDirty();
    }

    public synchronized boolean appendIfAbsent(
            UUID playerUUID,
            String idempotencyMarker,
            TransactionHistoryEntry entry
    ) {
        String marker = java.util.Objects.requireNonNull(
                idempotencyMarker, "idempotencyMarker");
        if (marker.isBlank()) {
            throw new IllegalArgumentException(
                    "Transaction history marker cannot be blank");
        }
        UUID owner = java.util.Objects.requireNonNull(
                playerUUID, "playerUUID");
        TransactionHistoryEntry historyEntry =
                java.util.Objects.requireNonNull(entry, "entry");
        Set<String> markers = idempotencyMarkersByPlayer
                .computeIfAbsent(owner, ignored -> new HashSet<>());
        if (!markers.add(marker)) {
            return false;
        }
        List<TransactionHistoryEntry> entries = entriesByPlayer
                .computeIfAbsent(owner,
                        ignored -> new ArrayList<>());
        entries.add(0, historyEntry);
        if (entries.size() > MAX_ENTRIES_PER_PLAYER) {
            entries.subList(MAX_ENTRIES_PER_PLAYER,
                    entries.size()).clear();
        }
        setDirty();
        return true;
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
