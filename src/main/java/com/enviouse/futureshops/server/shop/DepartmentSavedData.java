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
    private static final int MAXIMUM_DEPARTMENTS = 512;
    private static final int MAXIMUM_NAME_LENGTH = 48;
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
        if (trimmed.length() > MAXIMUM_NAME_LENGTH
                || (departments.size() >= MAXIMUM_DEPARTMENTS
                && !departments.contains(trimmed))) return false;
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
        if (departments.size() > MAXIMUM_DEPARTMENTS) {
            throw new IllegalStateException("Department list limit is exceeded");
        }
        SavedDataMigrations.writeVersion(tag, CURRENT_VERSION);
        ListTag list = new ListTag();
        for (String dept : departments) {
            if (dept == null || dept.isBlank() || dept.length() > MAXIMUM_NAME_LENGTH) {
                throw new IllegalStateException("Department name is invalid");
            }
            list.add(StringTag.valueOf(dept));
        }
        tag.put("Departments", list);
        return tag;
    }

    static DepartmentSavedData load(CompoundTag tag) {
        DepartmentSavedData data = new DepartmentSavedData();
        int version = SavedDataMigrations.readVersion(tag);
        SavedDataMigrations.needsMigration(DATA_NAME, version, CURRENT_VERSION);
        if (tag.contains("Departments")) {
            ListTag list = SavedDataMigrations.requireList(
                    tag, "Departments", Tag.TAG_STRING,
                    MAXIMUM_DEPARTMENTS, "Department");
            for (Tag t : list) {
                String name = t.getAsString().trim();
                if (name.isEmpty() || name.length() > MAXIMUM_NAME_LENGTH) {
                    throw new IllegalArgumentException("Department name is invalid");
                }
                data.departments.add(name);
            }
        }
        return data;
    }
}
