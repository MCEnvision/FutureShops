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

    private final Map<UUID, Long> balances = new HashMap<>();

    public static InternalBalanceSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        InternalBalanceSavedData data = new InternalBalanceSavedData();
        int version = SavedDataMigrations.readVersion(tag);
        SavedDataMigrations.needsMigration(DATA_NAME, version, CURRENT_VERSION);
        ListTag entries = tag.getList("balances", Tag.TAG_COMPOUND);
        for (Tag entryTag : entries) {
            CompoundTag entry = (CompoundTag) entryTag;
            if (entry.hasUUID("player") && entry.contains("balance", Tag.TAG_LONG)) {
                data.balances.put(entry.getUUID("player"), entry.getLong("balance"));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
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

    public long getBalanceOrDefault(UUID playerUUID, long defaultBalance) {
        Long stored = balances.get(playerUUID);
        if (stored != null) {
            return stored;
        }

        balances.put(playerUUID, defaultBalance);
        setDirty();
        return defaultBalance;
    }

    public void setBalance(UUID playerUUID, long amountMinorUnits) {
        balances.put(playerUUID, amountMinorUnits);
        setDirty();
    }

    public Map<UUID, Long> snapshotBalances() {
        return Collections.unmodifiableMap(new HashMap<>(balances));
    }
}
