package com.enviouse.futureshopsp.server.shop;

import com.enviouse.futureshopsp.server.SavedDataMigrations;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PlayerShopRegistrySavedData extends SavedData {
    private static final String DATA_NAME = "futureshops_player_shop_registry";
    private static final int CURRENT_VERSION = 1;
    private static final int MAX_OWNERS = 10_000;
    private static final int MAX_SHOPS_PER_OWNER = 1_000;

    private final Map<UUID, List<ShopRef>> shopsByOwner = new HashMap<>();
    private boolean integrityValid = true;

    public static PlayerShopRegistrySavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        PlayerShopRegistrySavedData data = new PlayerShopRegistrySavedData();
        int version = SavedDataMigrations.readVersion(tag);
        if (version > CURRENT_VERSION) {
            data.integrityValid = false;
            return data;
        }
        SavedDataMigrations.needsMigration(DATA_NAME, version, CURRENT_VERSION);
        Tag rawOwners = tag.get("owners");
        if (rawOwners != null && !(rawOwners instanceof ListTag)) {
            data.integrityValid = false;
            return data;
        }
        ListTag owners = rawOwners instanceof ListTag list ? list : new ListTag();
        if (owners.size() > MAX_OWNERS) {
            data.integrityValid = false;
            return data;
        }
        for (Tag ownerTag : owners) {
            if (!(ownerTag instanceof CompoundTag ownerCompound) || !ownerCompound.hasUUID("owner")) {
                data.integrityValid = false;
                continue;
            }
            UUID owner = ownerCompound.getUUID("owner");
            Tag rawShops = ownerCompound.get("shops");
            if (data.shopsByOwner.containsKey(owner) || (rawShops != null && !(rawShops instanceof ListTag))) {
                data.integrityValid = false;
                continue;
            }
            List<ShopRef> refs = new ArrayList<>();
            ListTag shops = rawShops instanceof ListTag list ? list : new ListTag();
            if (shops.size() > MAX_SHOPS_PER_OWNER) {
                data.integrityValid = false;
                continue;
            }
            for (Tag shopTag : shops) {
                if (!(shopTag instanceof CompoundTag shopCompound)
                        || !shopCompound.contains("dimension", Tag.TAG_STRING)
                        || !shopCompound.contains("pos", Tag.TAG_LONG)) {
                    data.integrityValid = false;
                    continue;
                }
                try {
                    refs.add(new ShopRef(ResourceLocation.parse(shopCompound.getString("dimension")),
                            shopCompound.getLong("pos")));
                } catch (RuntimeException exception) {
                    data.integrityValid = false;
                }
            }
            data.shopsByOwner.put(owner, refs);
        }
        if (!data.integrityValid) {
            data.shopsByOwner.clear();
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        SavedDataMigrations.writeVersion(tag, CURRENT_VERSION);
        ListTag owners = new ListTag();
        for (Map.Entry<UUID, List<ShopRef>> entry : shopsByOwner.entrySet()) {
            CompoundTag ownerTag = new CompoundTag();
            ownerTag.putUUID("owner", entry.getKey());
            ListTag shops = new ListTag();
            for (ShopRef ref : entry.getValue()) {
                CompoundTag shopTag = new CompoundTag();
                shopTag.putString("dimension", ref.dimension().toString());
                shopTag.putLong("pos", ref.posLong());
                shops.add(shopTag);
            }
            ownerTag.put("shops", shops);
            owners.add(ownerTag);
        }
        tag.put("owners", owners);
        return tag;
    }

    public static PlayerShopRegistrySavedData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(new SavedData.Factory<>(PlayerShopRegistrySavedData::new, PlayerShopRegistrySavedData::load, null), DATA_NAME);
    }

    public synchronized void register(UUID owner, ResourceLocation dimension, long posLong) {
        if (owner == null || dimension == null) {
            return;
        }
        List<ShopRef> refs = shopsByOwner.computeIfAbsent(owner, ignored -> new ArrayList<>());
        if (refs.size() >= MAX_SHOPS_PER_OWNER && !refs.contains(new ShopRef(dimension, posLong))) {
            return;
        }
        ShopRef ref = new ShopRef(dimension, posLong);
        if (!refs.contains(ref)) {
            refs.add(ref);
            setDirty();
        }
    }

    public synchronized void remove(ResourceLocation dimension, long posLong) {
        if (dimension == null) {
            return;
        }
        boolean changed = false;
        for (List<ShopRef> refs : shopsByOwner.values()) {
            changed |= refs.removeIf(ref -> ref.posLong() == posLong && ref.dimension().equals(dimension));
        }
        if (changed) {
            shopsByOwner.values().removeIf(List::isEmpty);
            setDirty();
        }
    }

    public synchronized List<ShopRef> getOwnedShops(UUID owner) {
        return List.copyOf(shopsByOwner.getOrDefault(owner, List.of()));
    }

    public synchronized Map<UUID, List<ShopRef>> snapshot() {
        Map<UUID, List<ShopRef>> snapshot = new HashMap<>();
        shopsByOwner.forEach((owner, refs) -> snapshot.put(owner, List.copyOf(refs)));
        return snapshot;
    }

    /**
     * Returns a flat map of all registered shops: posLong → ShopRecord (with owner + dimension).
     * Used by NearbyShopScanner for fast spatial lookup.
     */
    public synchronized Map<Long, ShopRecord> getAllShops() {
        Map<Long, ShopRecord> result = new HashMap<>();
        for (Map.Entry<UUID, List<ShopRef>> entry : shopsByOwner.entrySet()) {
            UUID owner = entry.getKey();
            for (ShopRef ref : entry.getValue()) {
                result.put(ref.posLong(), new ShopRecord(owner, ref.dimension().toString()));
            }
        }
        return result;
    }

    public synchronized boolean integrityValid() {
        return integrityValid;
    }

    public record ShopRef(ResourceLocation dimension, long posLong) {
    }

    public record ShopRecord(UUID owner, String dimension) {
    }
}
