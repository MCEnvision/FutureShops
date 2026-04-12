package com.enviouse.futureshops.server.shop;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PlayerShopRegistrySavedData extends SavedData {
    private static final String DATA_NAME = "futureshops_player_shop_registry";

    private final Map<UUID, List<ShopRef>> shopsByOwner = new HashMap<>();

    public static PlayerShopRegistrySavedData load(CompoundTag tag) {
        PlayerShopRegistrySavedData data = new PlayerShopRegistrySavedData();
        ListTag owners = tag.getList("owners", Tag.TAG_COMPOUND);
        for (Tag ownerTag : owners) {
            CompoundTag ownerCompound = (CompoundTag) ownerTag;
            if (!ownerCompound.hasUUID("owner")) {
                continue;
            }
            UUID owner = ownerCompound.getUUID("owner");
            List<ShopRef> refs = new ArrayList<>();
            ListTag shops = ownerCompound.getList("shops", Tag.TAG_COMPOUND);
            for (Tag shopTag : shops) {
                CompoundTag shopCompound = (CompoundTag) shopTag;
                refs.add(new ShopRef(
                        ResourceLocation.parse(shopCompound.getString("dimension")),
                        shopCompound.getLong("pos")));
            }
            data.shopsByOwner.put(owner, refs);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
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
        return overworld.getDataStorage().computeIfAbsent(
                PlayerShopRegistrySavedData::load,
                PlayerShopRegistrySavedData::new,
                DATA_NAME);
    }

    public synchronized void register(UUID owner, ResourceLocation dimension, long posLong) {
        List<ShopRef> refs = shopsByOwner.computeIfAbsent(owner, ignored -> new ArrayList<>());
        ShopRef ref = new ShopRef(dimension, posLong);
        if (!refs.contains(ref)) {
            refs.add(ref);
            setDirty();
        }
    }

    public synchronized void remove(ResourceLocation dimension, long posLong) {
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

    public record ShopRef(ResourceLocation dimension, long posLong) {
    }
}

