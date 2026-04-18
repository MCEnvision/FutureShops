package com.enviouse.futureshops.server.shop;

import com.enviouse.futureshops.server.SavedDataMigrations;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Admin-managed catalog categories — persistent server-wide registry.
 * Admins create/remove categories via /shopadmin category commands.
 * These categories are used for the admin shop catalog sidebar and
 * can also serve as pre-seeded suggestions in the player-shop department picker.
 *
 * Also stores item→category assignments so admins can classify individual items
 * into categories (replacing the old TagDepartmentClassifier auto-classification).
 */
public class AdminCategorySavedData extends SavedData {
    private static final String DATA_NAME = "futureshops_admin_categories";
    private static final int CURRENT_VERSION = 1;
    private final Set<String> categories = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    /** Maps itemId → categoryName (e.g., "minecraft:diamond_sword" → "Weapons") */
    private final Map<String, String> itemAssignments = new HashMap<>();

    public AdminCategorySavedData() {
    }

    public static AdminCategorySavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(AdminCategorySavedData::load, AdminCategorySavedData::new, DATA_NAME);
    }

    /**
     * Adds an admin-defined category. Returns true if it was new.
     */
    public boolean addCategory(String name) {
        if (name == null || name.isBlank()) return false;
        String trimmed = name.trim();
        if (trimmed.length() > 48) trimmed = trimmed.substring(0, 48);
        boolean added = categories.add(trimmed);
        if (added) {
            setDirty();
        }
        return added;
    }

    /**
     * Removes an admin-defined category.
     */
    public boolean removeCategory(String name) {
        boolean removed = categories.remove(name);
        if (removed) {
            // Remove all item assignments for this category
            itemAssignments.entrySet().removeIf(e -> name.equalsIgnoreCase(e.getValue()));
            setDirty();
        }
        return removed;
    }

    /**
     * Returns all admin categories sorted alphabetically.
     */
    public List<String> getAllSorted() {
        return List.copyOf(categories);
    }

    /**
     * Returns true if any admin categories are defined.
     */
    public boolean hasCategories() {
        return !categories.isEmpty();
    }

    /**
     * Searches categories matching a prefix (case-insensitive).
     */
    public List<String> search(String prefix, int maxResults) {
        if (prefix == null || prefix.isBlank()) {
            return categories.stream().limit(maxResults).collect(Collectors.toList());
        }
        String lower = prefix.toLowerCase(Locale.ROOT);
        return categories.stream()
                .filter(c -> c.toLowerCase(Locale.ROOT).contains(lower))
                .limit(maxResults)
                .collect(Collectors.toList());
    }

    public int size() {
        return categories.size();
    }

    /**
     * Assigns an item to a category. The category must already exist.
     * @return true if the assignment was made or updated
     */
    public boolean assignItem(String itemId, String categoryName) {
        if (itemId == null || itemId.isBlank() || categoryName == null || categoryName.isBlank()) return false;
        String trimmedCat = categoryName.trim();
        // Category must exist
        if (!categories.contains(trimmedCat)) return false;
        String prev = itemAssignments.put(itemId.trim(), trimmedCat);
        if (!trimmedCat.equals(prev)) {
            setDirty();
            return true;
        }
        return prev == null; // was newly assigned
    }

    /**
     * Removes an item's category assignment.
     */
    public boolean unassignItem(String itemId) {
        if (itemId == null || itemId.isBlank()) return false;
        boolean removed = itemAssignments.remove(itemId.trim()) != null;
        if (removed) setDirty();
        return removed;
    }

    /**
     * Returns the admin-assigned category for an item, or null if not assigned.
     */
    @Nullable
    public String getItemCategory(String itemId) {
        if (itemId == null) return null;
        return itemAssignments.get(itemId.trim());
    }

    /**
     * Returns an unmodifiable view of all item→category assignments.
     */
    public Map<String, String> getAllAssignments() {
        return Collections.unmodifiableMap(itemAssignments);
    }

    /**
     * Returns all items assigned to a specific category.
     */
    public List<String> getItemsInCategory(String categoryName) {
        if (categoryName == null || categoryName.isBlank()) return List.of();
        return itemAssignments.entrySet().stream()
                .filter(e -> categoryName.equalsIgnoreCase(e.getValue()))
                .map(Map.Entry::getKey)
                .sorted()
                .collect(Collectors.toList());
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        SavedDataMigrations.writeVersion(tag, CURRENT_VERSION);
        ListTag list = new ListTag();
        for (String cat : categories) {
            list.add(StringTag.valueOf(cat));
        }
        tag.put("Categories", list);

        // Save item→category assignments
        CompoundTag assignTag = new CompoundTag();
        for (Map.Entry<String, String> entry : itemAssignments.entrySet()) {
            assignTag.putString(entry.getKey(), entry.getValue());
        }
        tag.put("ItemAssignments", assignTag);

        return tag;
    }

    private static AdminCategorySavedData load(CompoundTag tag) {
        AdminCategorySavedData data = new AdminCategorySavedData();
        int version = SavedDataMigrations.readVersion(tag);
        SavedDataMigrations.needsMigration(DATA_NAME, version, CURRENT_VERSION);
        // v0→v1: no structural changes, just adds the version tag on next save
        if (tag.contains("Categories", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Categories", Tag.TAG_STRING);
            for (Tag t : list) {
                data.categories.add(t.getAsString());
            }
        }
        if (tag.contains("ItemAssignments", Tag.TAG_COMPOUND)) {
            CompoundTag assignTag = tag.getCompound("ItemAssignments");
            for (String key : assignTag.getAllKeys()) {
                data.itemAssignments.put(key, assignTag.getString(key));
            }
        }
        return data;
    }
}
