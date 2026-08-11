package com.enviouse.futureshops.server.market.auction;

import com.enviouse.futureshops.server.SavedDataMigrations;
import com.enviouse.futureshops.server.escrow.runtime.EscrowManagedSavedData;
import com.enviouse.futureshops.server.escrow.runtime.EscrowRuntimeStoreBinding;
import com.enviouse.futureshops.server.market.auction.escrow.AuctionCreateEscrowIntent;
import com.enviouse.futureshops.server.market.auction.escrow.AuctionEscrowCommit;
import com.enviouse.futureshops.server.market.auction.escrow.AuctionEscrowLifecycleEvent;
import com.enviouse.futureshops.server.market.auction.escrow.AuctionEscrowLifecycleRepository;
import com.enviouse.futureshops.server.market.auction.escrow.AuctionEscrowLifecycleState;
import com.enviouse.futureshops.server.market.auction.escrow.AuctionEscrowLifecycleStateCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.UUID;

public final class AuctionHouseSavedData extends EscrowManagedSavedData {
    public static final String DATA_NAME = "futureshops_auction_house";
    public static final int CURRENT_VERSION = 2;

    private static final String SNAPSHOT_KEY = "snapshot";
    private static final String ESCROW_LIFECYCLE_KEY = "escrowLifecycle";
    private static final String SNAPSHOT_DIGEST_KEY = "snapshotDigest";
    private static final String ESCROW_LIFECYCLE_DIGEST_KEY =
            "escrowLifecycleDigest";

    private AuctionHouseSnapshot snapshot = AuctionHouseSnapshot.empty();
    private AuctionEscrowLifecycleState escrowLifecycle =
            AuctionEscrowLifecycleState.empty();

    public static AuctionHouseSavedData load(CompoundTag tag) {
        Objects.requireNonNull(tag, "tag");
        if (tag.contains("schemaVersion")
                && !tag.contains("schemaVersion", Tag.TAG_INT)) {
            throw new IllegalStateException(
                    "Auction house schema has the wrong type");
        }
        int version = SavedDataMigrations.readVersion(tag);
        if (version > CURRENT_VERSION) {
            throw new IllegalStateException(
                    "Auction house schema is newer than this build");
        }
        if (version < 0) {
            throw new IllegalStateException(
                    "Auction house schema is invalid");
        }
        SavedDataMigrations.needsMigration(DATA_NAME, version,
                CURRENT_VERSION);
        AuctionHouseSavedData data = new AuctionHouseSavedData();
        if (version == 0 && !tag.contains(SNAPSHOT_KEY)) {
            data.setDirty();
            return data;
        }
        if (!tag.contains(SNAPSHOT_KEY, Tag.TAG_BYTE_ARRAY)) {
            throw new IllegalStateException(
                    "Auction house snapshot is missing");
        }
        try {
            byte[] encodedSnapshot = tag.getByteArray(SNAPSHOT_KEY);
            data.snapshot = AuctionHouseSnapshotCodec.decode(
                    encodedSnapshot);
            if (version >= CURRENT_VERSION) {
                if (!tag.contains(ESCROW_LIFECYCLE_KEY,
                        Tag.TAG_BYTE_ARRAY)) {
                    throw new IllegalStateException(
                            "Auction escrow lifecycle snapshot is missing");
                }
                byte[] encodedLifecycle = tag.getByteArray(
                        ESCROW_LIFECYCLE_KEY);
                requireDigest(tag, SNAPSHOT_DIGEST_KEY,
                        encodedSnapshot);
                requireDigest(tag, ESCROW_LIFECYCLE_DIGEST_KEY,
                        encodedLifecycle);
                data.escrowLifecycle =
                        AuctionEscrowLifecycleStateCodec.decode(
                                encodedLifecycle);
            }
            data.requireCombinedSize(data.snapshot,
                    data.escrowLifecycle);
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "Auction house snapshot failed validation", exception);
        }
        if (version < CURRENT_VERSION) {
            data.setDirty();
        }
        return data;
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag) {
        requireCombinedSize(snapshot, escrowLifecycle);
        SavedDataMigrations.writeVersion(tag, CURRENT_VERSION);
        byte[] encodedSnapshot = AuctionHouseSnapshotCodec.encode(
                snapshot);
        byte[] encodedLifecycle = AuctionEscrowLifecycleStateCodec.encode(
                escrowLifecycle);
        tag.putByteArray(SNAPSHOT_KEY, encodedSnapshot);
        tag.putByteArray(ESCROW_LIFECYCLE_KEY, encodedLifecycle);
        tag.putByteArray(SNAPSHOT_DIGEST_KEY, sha256(encodedSnapshot));
        tag.putByteArray(ESCROW_LIFECYCLE_DIGEST_KEY,
                sha256(encodedLifecycle));
        return tag;
    }

    public static AuctionHouseSavedData get(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return EscrowRuntimeStoreBinding.bind(server,
                server.overworld().getDataStorage().computeIfAbsent(
                        AuctionHouseSavedData::load,
                        AuctionHouseSavedData::new,
                        DATA_NAME));
    }

    public synchronized AuctionHouseMutation.ApplyResult preflightCommitted(
            AuctionHouseMutation mutation
    ) {
        AuctionHouseMutation.ApplyResult result = Objects.requireNonNull(
                mutation, "mutation").apply(snapshot);
        requireCombinedSize(result.snapshot(), escrowLifecycle);
        return result;
    }

    public synchronized AuctionHouseMutation.ApplyResult applyCommitted(
            AuctionHouseMutation mutation
    ) {
        requireEscrowMutationPermit();
        AuctionHouseMutation.ApplyResult result = preflightCommitted(
                mutation);
        if (!result.replayed()) {
            snapshot = result.snapshot();
            setDirty();
        }
        return result;
    }

    public synchronized AuctionEscrowLifecycleRepository.ApplyResult
    preflightEscrowLifecycleCommitted(
            AuctionEscrowLifecycleEvent event
    ) {
        AuctionEscrowLifecycleRepository.ApplyResult result =
                AuctionEscrowLifecycleRepository.apply(snapshot,
                        escrowLifecycle,
                        Objects.requireNonNull(event, "event"));
        requireCombinedSize(result.auctionHouse(), result.lifecycle());
        return result;
    }

    public synchronized AuctionEscrowLifecycleRepository.ApplyResult
    applyEscrowLifecycleCommitted(
            AuctionEscrowLifecycleEvent event
    ) {
        requireEscrowMutationPermit();
        AuctionEscrowLifecycleRepository.ApplyResult result =
                preflightEscrowLifecycleCommitted(event);
        if (!result.replayed()) {
            snapshot = result.auctionHouse();
            escrowLifecycle = result.lifecycle();
            setDirty();
        }
        return result;
    }

    public synchronized void replaceFromValidated(
            AuctionHouseSavedData source
    ) {
        requireEscrowMutationPermit();
        Objects.requireNonNull(source, "source");
        if (source == this) {
            return;
        }
        snapshot = AuctionHouseSnapshotCodec.decode(
                AuctionHouseSnapshotCodec.encode(source.snapshot()));
        escrowLifecycle = AuctionEscrowLifecycleStateCodec.decode(
                AuctionEscrowLifecycleStateCodec.encode(
                        source.escrowLifecycleSnapshot()));
        requireCombinedSize(snapshot, escrowLifecycle);
        setDirty();
    }

    public synchronized AuctionHouseSnapshot snapshot() {
        return snapshot;
    }

    public synchronized AuctionListing listing(UUID listingId) {
        return snapshot.listings().get(
                Objects.requireNonNull(listingId, "listingId"));
    }

    public synchronized AuctionRequestReceipt receipt(UUID requestId) {
        return snapshot.requestReceipts().get(
                Objects.requireNonNull(requestId, "requestId"));
    }

    public synchronized AuctionEscrowLifecycleState
    escrowLifecycleSnapshot() {
        return escrowLifecycle;
    }

    public synchronized AuctionCreateEscrowIntent createIntent(
            UUID requestId
    ) {
        return escrowLifecycle.createIntents().get(
                Objects.requireNonNull(requestId, "requestId"));
    }

    public synchronized AuctionEscrowCommit escrowCommit(UUID requestId) {
        return escrowLifecycle.commits().get(
                Objects.requireNonNull(requestId, "requestId"));
    }

    public synchronized boolean hasMaterializedState() {
        AuctionHouseSnapshot current = snapshot;
        if (current.nextAcceptedSequence() != 0L
                || !current.listings().isEmpty()
                || !current.requestReceipts().isEmpty()
                || escrowLifecycle.hasMaterializedState()) {
            return true;
        }
        for (AuctionTimeBasis basis : AuctionTimeBasis.values()) {
            if (current.lastObservedTimeMillis(basis) != 0L) {
                return true;
            }
        }
        return false;
    }

    private void requireCombinedSize(
            AuctionHouseSnapshot auctionSnapshot,
            AuctionEscrowLifecycleState lifecycleSnapshot
    ) {
        long size = Math.addExact(
                AuctionHouseSnapshotCodec.encode(auctionSnapshot).length,
                AuctionEscrowLifecycleStateCodec.encode(
                        lifecycleSnapshot).length);
        if (size > AuctionHouseSnapshotCodec.MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException(
                    "Auction house and escrow lifecycle exceed their shared limit");
        }
    }

    private static void requireDigest(
            CompoundTag tag,
            String key,
            byte[] encoded
    ) {
        if (!tag.contains(key, Tag.TAG_BYTE_ARRAY)) {
            throw new IllegalStateException(
                    "Auction house snapshot digest is missing");
        }
        byte[] digest = tag.getByteArray(key);
        if (digest.length != 32 || !MessageDigest.isEqual(
                digest, sha256(encoded))) {
            throw new IllegalStateException(
                    "Auction house snapshot digest is invalid");
        }
    }

    private static byte[] sha256(byte[] encoded) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(encoded);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable",
                    exception);
        }
    }
}
