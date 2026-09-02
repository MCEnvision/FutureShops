package com.enviouse.futureshops.catalog;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Durable replay outcomes for committed administrator bulk catalog requests. */
public final class AdminBulkReplaySavedData extends SavedData {
    private static final String DATA_NAME = "futureshops_admin_bulk_replay";
    private static final int CURRENT_VERSION = 1;
    private static final int MAX_ENTRIES = AdminBulkListingService.MAX_REPLAY_ENTRIES;

    private final Map<UUID, AdminBulkListingPlanner.Preview> outcomes = new LinkedHashMap<>();

    public static AdminBulkReplaySavedData load(CompoundTag tag) {
        AdminBulkReplaySavedData data = new AdminBulkReplaySavedData();
        ListTag entries = tag.getList("entries", Tag.TAG_COMPOUND);
        for (int index = 0; index < entries.size() && data.outcomes.size() < MAX_ENTRIES; index++) {
            CompoundTag entry = entries.getCompound(index);
            try {
                UUID requestId = UUID.fromString(entry.getString("requestId"));
                ListTag rowTags = entry.getList("rows", Tag.TAG_COMPOUND);
                java.util.ArrayList<AdminBulkListingPlanner.Row> rows = new java.util.ArrayList<>();
                for (int rowIndex = 0; rowIndex < rowTags.size()
                        && rows.size() < AdminBulkListingPlanner.MAX_SELECTIONS; rowIndex++) {
                    CompoundTag row = rowTags.getCompound(rowIndex);
                    rows.add(new AdminBulkListingPlanner.Row(
                            row.getInt("ordinal"), row.getString("itemId"), row.getString("nbt"),
                            row.getString("canonicalNbt"), row.getString("identityDigest"),
                            row.getString("listingId"), row.getString("displayName"),
                            AdminBulkListingPlanner.Action.valueOf(row.getString("action")),
                            row.getString("reason"), row.getBoolean("replaceEligible")));
                }
                AdminBulkListingPlanner.Preview preview = new AdminBulkListingPlanner.Preview(
                        requestId, entry.getString("registryFingerprint"),
                        entry.getString("catalogFingerprint"), entry.getString("categoryId"),
                        entry.getString("priceText"), entry.getString("stockText"),
                        entry.getLong("priceMinor"), entry.getInt("stock"), rows,
                        entry.getString("fingerprint"), new com.google.gson.JsonObject());
                if (!preview.fingerprint().isBlank()) {
                    data.outcomes.put(requestId, preview);
                }
            } catch (RuntimeException ignored) {
                // Ignore one malformed historical outcome and retain all valid outcomes.
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("version", CURRENT_VERSION);
        ListTag entries = new ListTag();
        outcomes.forEach((requestId, preview) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("requestId", requestId.toString());
            entry.putString("registryFingerprint", preview.registryFingerprint());
            entry.putString("catalogFingerprint", preview.catalogFingerprint());
            entry.putString("categoryId", preview.categoryId());
            entry.putString("priceText", preview.priceText());
            entry.putString("stockText", preview.stockText());
            entry.putLong("priceMinor", preview.priceMinor());
            entry.putInt("stock", preview.stock());
            entry.putString("fingerprint", preview.fingerprint());
            ListTag rows = new ListTag();
            preview.rows().forEach(value -> {
                CompoundTag row = new CompoundTag();
                row.putInt("ordinal", value.ordinal());
                row.putString("itemId", value.itemId());
                row.putString("nbt", value.nbt());
                row.putString("canonicalNbt", value.canonicalNbt());
                row.putString("identityDigest", value.identityDigest());
                row.putString("listingId", value.listingId());
                row.putString("displayName", value.displayName());
                row.putString("action", value.action().name());
                row.putString("reason", value.reason());
                row.putBoolean("replaceEligible", value.replaceEligible());
                rows.add(row);
            });
            entry.put("rows", rows);
            entries.add(entry);
        });
        tag.put("entries", entries);
        return tag;
    }

    public static AdminBulkReplaySavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                AdminBulkReplaySavedData::load, AdminBulkReplaySavedData::new, DATA_NAME);
    }

    public Optional<AdminBulkListingPlanner.Preview> find(UUID requestId) {
        return Optional.ofNullable(outcomes.get(requestId));
    }

    public void record(UUID requestId, AdminBulkListingPlanner.Preview preview) {
        if (requestId == null || preview == null) {
            return;
        }
        outcomes.remove(requestId);
        outcomes.put(requestId, preview);
        while (outcomes.size() > MAX_ENTRIES) {
            outcomes.remove(outcomes.keySet().iterator().next());
        }
        setDirty();
    }
}
