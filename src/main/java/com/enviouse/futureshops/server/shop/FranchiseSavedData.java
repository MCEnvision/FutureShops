package com.enviouse.futureshops.server.shop;

import com.enviouse.futureshops.server.SavedDataMigrations;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Persistent franchise grouping system.
 * A franchise links multiple players together so they can manage each other's shops
 * and share revenue/stock visibility.
 */
public class FranchiseSavedData extends SavedData {
    private static final String DATA_ID = "futureshops_franchises";
    private static final int CURRENT_VERSION = 1;

    /** Maps franchise UUID → franchise data. */
    private final Map<UUID, Franchise> franchises = new HashMap<>();
    /** Maps player UUID → franchise UUID for fast lookup. */
    private final Map<UUID, UUID> playerToFranchise = new HashMap<>();
    /** Pending invites: invited player UUID → franchise UUID. */
    private final Map<UUID, UUID> pendingInvites = new HashMap<>();

    public FranchiseSavedData() {
    }

    // ═══ Franchise management ═══

    public CreateResult createFranchise(UUID leader, String name) {
        if (playerToFranchise.containsKey(leader)) {
            return new CreateResult(false, "ALREADY_IN_FRANCHISE");
        }
        UUID franchiseId = UUID.randomUUID();
        Franchise franchise = new Franchise(franchiseId, name.trim(), leader);
        franchise.members.add(leader);
        franchises.put(franchiseId, franchise);
        playerToFranchise.put(leader, franchiseId);
        setDirty();
        return new CreateResult(true, "CREATED");
    }

    public boolean invite(UUID inviter, UUID target) {
        UUID franchiseId = playerToFranchise.get(inviter);
        if (franchiseId == null) return false;
        Franchise franchise = franchises.get(franchiseId);
        if (franchise == null || !franchise.leader.equals(inviter)) return false;
        if (playerToFranchise.containsKey(target)) return false;
        if (franchise.members.size() >= 20) return false; // Max members
        pendingInvites.put(target, franchiseId);
        return true;
    }

    public boolean acceptInvite(UUID player) {
        UUID franchiseId = pendingInvites.remove(player);
        if (franchiseId == null) return false;
        Franchise franchise = franchises.get(franchiseId);
        if (franchise == null) return false;
        if (playerToFranchise.containsKey(player)) return false;
        franchise.members.add(player);
        playerToFranchise.put(player, franchiseId);
        setDirty();
        return true;
    }

    public boolean declineInvite(UUID player) {
        return pendingInvites.remove(player) != null;
    }

    public boolean kick(UUID leader, UUID target) {
        UUID franchiseId = playerToFranchise.get(leader);
        if (franchiseId == null) return false;
        Franchise franchise = franchises.get(franchiseId);
        if (franchise == null || !franchise.leader.equals(leader)) return false;
        if (leader.equals(target)) return false; // Can't kick yourself
        if (!franchise.members.remove(target)) return false;
        playerToFranchise.remove(target);
        setDirty();
        return true;
    }

    public boolean promote(UUID leader, UUID newLeader) {
        UUID franchiseId = playerToFranchise.get(leader);
        if (franchiseId == null) return false;
        Franchise franchise = franchises.get(franchiseId);
        if (franchise == null || !franchise.leader.equals(leader)) return false;
        if (!franchise.members.contains(newLeader)) return false;
        franchise.leader = newLeader;
        setDirty();
        return true;
    }

    public boolean leave(UUID player) {
        UUID franchiseId = playerToFranchise.get(player);
        if (franchiseId == null) return false;
        Franchise franchise = franchises.get(franchiseId);
        if (franchise == null) return false;
        franchise.members.remove(player);
        playerToFranchise.remove(player);
        if (franchise.members.isEmpty()) {
            franchises.remove(franchiseId);
        } else if (franchise.leader.equals(player)) {
            // Auto-promote the first remaining member
            franchise.leader = franchise.members.iterator().next();
        }
        setDirty();
        return true;
    }

    public boolean disband(UUID leader) {
        UUID franchiseId = playerToFranchise.get(leader);
        if (franchiseId == null) return false;
        Franchise franchise = franchises.get(franchiseId);
        if (franchise == null || !franchise.leader.equals(leader)) return false;
        for (UUID member : franchise.members) {
            playerToFranchise.remove(member);
        }
        franchises.remove(franchiseId);
        setDirty();
        return true;
    }

    // ═══ Queries ═══

    /**
     * Checks if two players are in the same franchise — used for ownership checks.
     * Returns true if the shop owner and the acting player are franchise members.
     */
    public boolean isFranchiseMember(UUID shopOwner, UUID actingPlayer) {
        UUID ownerFranchise = playerToFranchise.get(shopOwner);
        if (ownerFranchise == null) return false;
        UUID actorFranchise = playerToFranchise.get(actingPlayer);
        return ownerFranchise.equals(actorFranchise);
    }

    public Franchise getFranchise(UUID player) {
        UUID franchiseId = playerToFranchise.get(player);
        return franchiseId == null ? null : franchises.get(franchiseId);
    }

    public boolean hasPendingInvite(UUID player) {
        return pendingInvites.containsKey(player);
    }

    public UUID getPendingInviteFranchise(UUID player) {
        return pendingInvites.get(player);
    }

    /**
     * Returns the top N franchises sorted by member count (descending).
     * Used for leaderboard display.
     */
    public List<FranchiseLeaderEntry> getTopFranchises(int count) {
        return franchises.values().stream()
                .sorted(Comparator.comparingInt((Franchise f) -> f.members.size()).reversed())
                .limit(count)
                .map(f -> new FranchiseLeaderEntry(f.id, f.name, f.leader, f.members.size()))
                .toList();
    }

    // ═══ Persistence ═══

    @Override
    public CompoundTag save(CompoundTag tag) {
        SavedDataMigrations.writeVersion(tag, CURRENT_VERSION);
        ListTag franchiseList = new ListTag();
        for (Franchise franchise : franchises.values()) {
            CompoundTag fTag = new CompoundTag();
            fTag.putUUID("Id", franchise.id);
            fTag.putString("Name", franchise.name);
            fTag.putUUID("Leader", franchise.leader);
            fTag.putLong("CreatedAt", franchise.createdAt);
            ListTag memberList = new ListTag();
            for (UUID member : franchise.members) {
                CompoundTag mTag = new CompoundTag();
                mTag.putUUID("UUID", member);
                memberList.add(mTag);
            }
            fTag.put("Members", memberList);
            franchiseList.add(fTag);
        }
        tag.put("Franchises", franchiseList);
        return tag;
    }

    public static FranchiseSavedData load(CompoundTag tag) {
        FranchiseSavedData data = new FranchiseSavedData();
        int version = SavedDataMigrations.readVersion(tag);
        SavedDataMigrations.needsMigration(DATA_ID, version, CURRENT_VERSION);
        ListTag franchiseList = tag.getList("Franchises", Tag.TAG_COMPOUND);
        for (Tag fTag : franchiseList) {
            CompoundTag fCompound = (CompoundTag) fTag;
            UUID id = fCompound.getUUID("Id");
            String name = fCompound.getString("Name");
            UUID leader = fCompound.getUUID("Leader");
            long createdAt = fCompound.getLong("CreatedAt");
            Franchise franchise = new Franchise(id, name, leader);
            franchise.createdAt = createdAt;
            ListTag memberList = fCompound.getList("Members", Tag.TAG_COMPOUND);
            for (Tag mTag : memberList) {
                UUID member = ((CompoundTag) mTag).getUUID("UUID");
                franchise.members.add(member);
                data.playerToFranchise.put(member, id);
            }
            data.franchises.put(id, franchise);
        }
        return data;
    }

    public static FranchiseSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                FranchiseSavedData::load, FranchiseSavedData::new, DATA_ID);
    }

    // ═══ Data types ═══

    public static class Franchise {
        public final UUID id;
        public String name;
        public UUID leader;
        public final Set<UUID> members = new HashSet<>();
        public long createdAt;

        public Franchise(UUID id, String name, UUID leader) {
            this.id = id;
            this.name = name;
            this.leader = leader;
            this.createdAt = System.currentTimeMillis() / 1000L;
        }

        public Set<UUID> getMembers() {
            return Collections.unmodifiableSet(members);
        }
    }

    public record CreateResult(boolean success, String code) {
    }

    public record FranchiseLeaderEntry(UUID franchiseId, String name, UUID leader, int memberCount) {
    }
}

