package com.enviouse.futureshopsp.server.economy;

import com.enviouse.futureshopsp.server.SavedDataMigrations;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class InternalBalanceSavedData extends SavedData {
    public static final String DATA_NAME = "futureshops_balances";
    private static final int CURRENT_VERSION = 1;
    private static final int MAX_RECORDS = 10_000;

    private final Map<UUID, Long> balances = new HashMap<>();
    private boolean integrityValid = true;

    public static InternalBalanceSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        InternalBalanceSavedData data = new InternalBalanceSavedData();
        int version = SavedDataMigrations.readVersion(tag);
        if (version > CURRENT_VERSION) {
            data.integrityValid = false;
            return data;
        }
        SavedDataMigrations.needsMigration(DATA_NAME, version, CURRENT_VERSION);
        Tag balancesTag = tag.get("balances");
        if (balancesTag != null && !(balancesTag instanceof ListTag)) {
            data.integrityValid = false;
            return data;
        }
        ListTag entries = balancesTag instanceof ListTag list ? list : new ListTag();
        if (entries.size() > MAX_RECORDS) {
            data.integrityValid = false;
            return data;
        }
        Map<UUID, Long> loaded = new HashMap<>();
        for (Tag entryTag : entries) {
            if (!(entryTag instanceof CompoundTag entry)
                    || !entry.hasUUID("player")
                    || !entry.contains("balance", Tag.TAG_LONG)) {
                data.integrityValid = false;
                return data;
            }
            if (loaded.put(entry.getUUID("player"), entry.getLong("balance")) != null) {
                data.integrityValid = false;
                return data;
            }
        }
        data.balances.putAll(loaded);
        return data;
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        SavedDataMigrations.writeVersion(tag, CURRENT_VERSION);
        ListTag entries = new ListTag();
        for (Map.Entry<UUID, Long> entry : balances.entrySet()) {
            CompoundTag balanceTag = new CompoundTag();
            balanceTag.putUUID("player", entry.getKey());
            balanceTag.putLong("balance", entry.getValue());
            entries.add(balanceTag);
        }
        tag.put("balances", entries);
        return tag;
    }

    public synchronized long getBalanceOrDefault(UUID playerUUID, long defaultBalance) {
        Long stored = balances.get(playerUUID);
        if (stored != null) {
            return stored;
        }

        balances.put(playerUUID, defaultBalance);
        setDirty();
        return defaultBalance;
    }

    public synchronized void setBalance(UUID playerUUID, long amountMinorUnits) {
        balances.put(playerUUID, amountMinorUnits);
        setDirty();
    }

    public synchronized Map<UUID, Long> snapshotBalances() {
        return Collections.unmodifiableMap(new HashMap<>(balances));
    }

    public synchronized boolean integrityValid() {
        return integrityValid;
    }
}
