package com.enviouse.futureshopsp.server.shop;

import com.enviouse.futureshopsp.server.SavedDataMigrations;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Server-wide toggle for whether the admin shop (/shop catalog) is visible to players.
 * When disabled, /shop only shows nearby player shops.
 */
public class AdminShopToggleSavedData extends SavedData {
    private static final String DATA_ID = "futureshops_admin_shop_toggle";
    private static final int CURRENT_VERSION = 1;

    private boolean adminShopEnabled = true;

    public AdminShopToggleSavedData() {
    }

    public boolean isAdminShopEnabled() {
        return adminShopEnabled;
    }

    public void setAdminShopEnabled(boolean enabled) {
        this.adminShopEnabled = enabled;
        setDirty();
    }

    public boolean toggle() {
        adminShopEnabled = !adminShopEnabled;
        setDirty();
        return adminShopEnabled;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        SavedDataMigrations.writeVersion(tag, CURRENT_VERSION);
        tag.putBoolean("AdminShopEnabled", adminShopEnabled);
        return tag;
    }

    public static AdminShopToggleSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        AdminShopToggleSavedData data = new AdminShopToggleSavedData();
        SavedDataMigrations.needsMigration(DATA_ID, SavedDataMigrations.readVersion(tag), CURRENT_VERSION);
        data.adminShopEnabled = !tag.contains("AdminShopEnabled") || tag.getBoolean("AdminShopEnabled");
        return data;
    }

    public static AdminShopToggleSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(AdminShopToggleSavedData::new, AdminShopToggleSavedData::load, null), DATA_ID);
    }
}

