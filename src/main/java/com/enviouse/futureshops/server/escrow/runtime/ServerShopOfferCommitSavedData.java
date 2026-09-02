package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.SavedDataMigrations;
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

public final class ServerShopOfferCommitSavedData extends SavedData {
    private static final String DATA_ID =
            "futureshops_server_shop_offer_commits";
    private static final int CURRENT_VERSION = 2;
    private static final int MAXIMUM_COMMITS = 10_000;
    private static final int MAXIMUM_ARCHIVES = 65_536;
    private static final int MAXIMUM_ARCHIVES_PER_PLAYER = 4_096;
    private static final org.slf4j.Logger LOGGER =
            com.mojang.logging.LogUtils.getLogger();
    private final LinkedHashMap<UUID, ServerShopOfferCommit> commits =
            new LinkedHashMap<>();
    private final LinkedHashMap<UUID, ServerShopOfferReplayReceipt> archives =
            new LinkedHashMap<>();
    private final java.util.Map<UUID, Integer> archivePlayerCounts =
            new java.util.HashMap<>();
    private final int maximumCommits;
    private final int maximumArchives;
    private long mutationRevision;

    public ServerShopOfferCommitSavedData() {
        this(MAXIMUM_COMMITS, MAXIMUM_ARCHIVES);
    }

    ServerShopOfferCommitSavedData(int maximumCommits) {
        this(maximumCommits, MAXIMUM_ARCHIVES);
    }

    ServerShopOfferCommitSavedData(
            int maximumCommits,
            int maximumArchives
    ) {
        if (maximumCommits <= 0
                || maximumCommits > MAXIMUM_COMMITS
                || maximumArchives <= 0
                || maximumArchives > MAXIMUM_ARCHIVES) {
            throw new IllegalArgumentException(
                    "Server shop offer commit limit is invalid");
        }
        this.maximumCommits = maximumCommits;
        this.maximumArchives = maximumArchives;
    }

    public static ServerShopOfferCommitSavedData get(
            MinecraftServer server
    ) {
        return server.overworld().getDataStorage().computeIfAbsent(
                ServerShopOfferCommitSavedData::load,
                ServerShopOfferCommitSavedData::new,
                DATA_ID);
    }

    public synchronized Optional<ServerShopOfferCommit> find(
            UUID requestId
    ) {
        return Optional.ofNullable(commits.get(requestId));
    }

    public synchronized List<ServerShopOfferCommit> entries() {
        return List.copyOf(commits.values());
    }

    public synchronized Optional<ServerShopOfferReplayReceipt> findArchived(
            UUID requestId
    ) {
        return Optional.ofNullable(archives.get(requestId));
    }

    public synchronized List<ServerShopOfferReplayReceipt> archivedEntries() {
        return List.copyOf(archives.values());
    }

    public synchronized long mutationRevision() {
        return mutationRevision;
    }

    public synchronized boolean canCommit(UUID requestId) {
        java.util.Objects.requireNonNull(requestId, "requestId");
        return commits.containsKey(requestId)
                || archives.containsKey(requestId)
                || commits.size() < maximumCommits;
    }

    public synchronized int size() {
        return commits.size();
    }

    public synchronized boolean canRecordReplayReceipt(UUID requestId) {
        return canRecordReplayReceipt(requestId, null);
    }

    public synchronized boolean canRecordReplayReceipt(
            UUID requestId,
            UUID playerId
    ) {
        java.util.Objects.requireNonNull(requestId, "requestId");
        return archives.containsKey(requestId)
                || archives.size() < maximumArchives
                && (playerId == null
                || archivePlayerCounts.getOrDefault(
                playerId, 0) < MAXIMUM_ARCHIVES_PER_PLAYER);
    }

    public synchronized boolean recordReplayReceipt(
            ServerShopOfferReplayReceipt receipt
    ) {
        java.util.Objects.requireNonNull(receipt, "receipt");
        ServerShopOfferCommit commit =
                commits.get(receipt.requestId());
        if (commit == null || !receipt.matches(commit)) {
            throw new IllegalStateException(
                    "Server shop offer replay receipt has no matching commit");
        }
        ServerShopOfferReplayReceipt existing =
                archives.get(receipt.requestId());
        if (existing != null) {
            if (!existing.equals(receipt)) {
                throw new IllegalStateException(
                        "Server shop offer replay receipt conflicts");
            }
            return false;
        }
        if (archives.size() >= maximumArchives) {
            throw new IllegalStateException(
                    "Server shop offer replay archive is full");
        }
        int playerCount = archivePlayerCounts.getOrDefault(
                receipt.playerId(), 0);
        if (playerCount >= MAXIMUM_ARCHIVES_PER_PLAYER) {
            throw new IllegalStateException(
                    "Server shop offer replay player archive is full");
        }
        archives.put(receipt.requestId(), receipt);
        archivePlayerCounts.put(
                receipt.playerId(), Math.addExact(playerCount, 1));
        mutationRevision = Math.addExact(mutationRevision, 1L);
        setDirty();
        return true;
    }

    public synchronized Optional<UUID> compactOldestReplay() {
        java.util.Iterator<Map.Entry<UUID, ServerShopOfferCommit>>
                iterator = commits.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ServerShopOfferCommit> entry =
                    iterator.next();
            ServerShopOfferReplayReceipt receipt =
                    archives.get(entry.getKey());
            if (receipt != null && receipt.matches(entry.getValue())) {
                UUID requestId = entry.getKey();
                iterator.remove();
                mutationRevision = Math.addExact(
                        mutationRevision, 1L);
                setDirty();
                return Optional.of(requestId);
            }
        }
        return Optional.empty();
    }

    public synchronized Optional<UUID> compactOldestReplay(
            ServerShopOfferReplayLedger ledger
    ) {
        java.util.Objects.requireNonNull(ledger, "ledger");
        java.util.Iterator<Map.Entry<UUID, ServerShopOfferCommit>>
                iterator = commits.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ServerShopOfferCommit> entry =
                    iterator.next();
            Optional<ServerShopOfferReplayReceipt> receipt =
                    ledger.find(entry.getKey());
            if (receipt.isPresent()
                    && receipt.orElseThrow().matches(
                    entry.getValue())) {
                UUID requestId = entry.getKey();
                iterator.remove();
                mutationRevision = Math.addExact(
                        mutationRevision, 1L);
                setDirty();
                return Optional.of(requestId);
            }
        }
        return Optional.empty();
    }

    public synchronized boolean commit(
            ServerShopOfferCommit commit
    ) {
        ServerShopOfferReplayReceipt archived =
                archives.get(commit.requestId());
        if (archived != null) {
            if (!archived.matches(commit)) {
                throw new IllegalStateException(
                        "Server shop offer archived commit conflicts");
            }
            return false;
        }
        ServerShopOfferCommit existing = commits.get(commit.requestId());
        if (existing != null) {
            if (!existing.equals(commit)) {
                throw new IllegalStateException(
                        "Server shop offer commit identity conflicts");
            }
            return false;
        }
        if (commits.size() >= maximumCommits) {
            throw new IllegalStateException(
                    "Server shop offer commit repository is full");
        }
        commits.put(commit.requestId(), commit);
        mutationRevision = Math.addExact(mutationRevision, 1L);
        setDirty();
        return true;
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag) {
        SavedDataMigrations.writeVersion(tag, CURRENT_VERSION);
        ListTag entries = new ListTag();
        for (Map.Entry<UUID, ServerShopOfferCommit> entry
                : commits.entrySet()) {
            CompoundTag value = new CompoundTag();
            value.putUUID("Request", entry.getKey());
            value.putByteArray("Commit",
                    ServerShopOfferCommitCodec.encode(entry.getValue()));
            entries.add(value);
        }
        tag.put("Commits", entries);
        ListTag archived = new ListTag();
        for (ServerShopOfferReplayReceipt receipt
                : archives.values()) {
            archived.add(receipt.save());
        }
        tag.put("Archives", archived);
        return tag;
    }

    public static ServerShopOfferCommitSavedData load(
            CompoundTag tag
    ) {
        int loadedVersion = SavedDataMigrations.readVersion(tag);
        if (loadedVersion > CURRENT_VERSION) {
            throw new IllegalArgumentException(
                    "Server shop offer commit version is unsupported");
        }
        SavedDataMigrations.needsMigration(
                DATA_ID, loadedVersion, CURRENT_VERSION);
        ServerShopOfferCommitSavedData data =
                new ServerShopOfferCommitSavedData();
        ListTag entries = SavedDataMigrations.requireList(
                tag, "Commits", Tag.TAG_COMPOUND,
                MAXIMUM_COMMITS, "Server shop offer commits");
        for (int index = 0;
             index < entries.size() && data.commits.size()
                     < MAXIMUM_COMMITS;
             index++) {
            try {
                CompoundTag value = entries.getCompound(index);
                UUID requestId = value.getUUID("Request");
                byte[] encoded = value.getByteArray("Commit");
                ServerShopOfferCommit commit =
                        ServerShopOfferCommitCodec.decode(encoded);
                if (!commit.requestId().equals(requestId)
                        || data.commits.putIfAbsent(
                        requestId, commit) != null) {
                    throw new IllegalArgumentException(
                            "Server shop offer saved commit conflicts");
                }
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException(
                        "Server shop offer commit row " + index
                                + " is invalid", exception);
            }
        }
        if (loadedVersion >= 2) {
            ListTag archived = SavedDataMigrations.requireList(
                    tag, "Archives", Tag.TAG_COMPOUND,
                    MAXIMUM_ARCHIVES, "Server shop offer replay archives");
            for (int index = 0;
                 index < archived.size(); index++) {
                try {
                    ServerShopOfferReplayReceipt receipt =
                            ServerShopOfferReplayReceipt.load(
                                    archived.getCompound(index));
                    if (receipt.kind()
                            != ServerShopOfferReplayReceipt.Kind.SINGLE
                            || data.commits.containsKey(receipt.requestId())
                            && !receipt.matches(data.commits.get(
                            receipt.requestId()))
                            || data.archives.putIfAbsent(
                            receipt.requestId(), receipt) != null) {
                        throw new IllegalArgumentException(
                                "Server shop offer replay archive conflicts");
                    }
                    int playerCount = data.archivePlayerCounts.merge(
                            receipt.playerId(), 1, Math::addExact);
                    if (playerCount
                            > MAXIMUM_ARCHIVES_PER_PLAYER) {
                        throw new IllegalArgumentException(
                                "Server shop offer replay player archive is full");
                    }
                } catch (RuntimeException exception) {
                    throw new IllegalArgumentException(
                            "Server shop offer replay archive row "
                                    + index + " is invalid", exception);
                }
            }
        }
        data.mutationRevision = Math.addExact(
                data.commits.size(), data.archives.size());
        return data;
    }
}
