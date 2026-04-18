package com.enviouse.futureshops.server.shop;

import com.enviouse.futureshops.server.SavedDataMigrations;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Server-global persistent registry of custom department names.
 * Shop owners create departments when classifying their listings.
 * Departments are searchable to avoid duplicates.
 */
public class DepartmentSavedData extends SavedData {
    private static final String DATA_NAME = "futureshops_departments";
    private static final int CURRENT_VERSION = 1;
    private final Set<String> departments = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

    public DepartmentSavedData() {
    }

    public static DepartmentSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(DepartmentSavedData::load, DepartmentSavedData::new, DATA_NAME);
    }

    /**
     * Adds a department name. Returns true if it was new.
     */
    public boolean addDepartment(String name) {
        if (name == null || name.isBlank()) return false;
        String trimmed = name.trim();
        if (trimmed.length() > 48) trimmed = trimmed.substring(0, 48);
        boolean added = departments.add(trimmed);
        if (added) setDirty();
        return added;
    }

    /**
     * Removes a department name.
     */
    public boolean removeDepartment(String name) {
        boolean removed = departments.remove(name);
        if (removed) setDirty();
        return removed;
    }

    /**
     * Returns all departments sorted alphabetically.
     */
    public List<String> getAllSorted() {
        return List.copyOf(departments);
    }

    /**
     * Searches departments matching a prefix (case-insensitive).
     * Returns up to maxResults matches sorted alphabetically.
     */
    public List<String> search(String prefix, int maxResults) {
        if (prefix == null || prefix.isBlank()) {
            return departments.stream().limit(maxResults).collect(Collectors.toList());
        }
        String lower = prefix.toLowerCase(Locale.ROOT);
        return departments.stream()
                .filter(d -> d.toLowerCase(Locale.ROOT).contains(lower))
                .limit(maxResults)
                .collect(Collectors.toList());
    }

    public int size() {
        return departments.size();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        SavedDataMigrations.writeVersion(tag, CURRENT_VERSION);
        ListTag list = new ListTag();
        for (String dept : departments) {
            list.add(StringTag.valueOf(dept));
        }
        tag.put("Departments", list);
        return tag;
    }

    private static DepartmentSavedData load(CompoundTag tag) {
        DepartmentSavedData data = new DepartmentSavedData();
        int version = SavedDataMigrations.readVersion(tag);
        SavedDataMigrations.needsMigration(DATA_NAME, version, CURRENT_VERSION);
        if (tag.contains("Departments", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Departments", Tag.TAG_STRING);
            for (Tag t : list) {
                data.departments.add(t.getAsString());
            }
        }
        return data;
    }
}

