package com.enviouse.futureshops.server.market.bazaar;

import com.enviouse.futureshops.server.SavedDataMigrations;
import com.enviouse.futureshops.server.escrow.runtime.EscrowManagedSavedData;
import com.enviouse.futureshops.server.escrow.runtime.EscrowRuntimeStoreBinding;
import com.enviouse.futureshops.server.market.bazaar.escrow.BazaarCreateEscrowIntent;
import com.enviouse.futureshops.server.market.bazaar.escrow.BazaarEscrowLifecycleEvent;
import com.enviouse.futureshops.server.market.bazaar.escrow.BazaarEscrowLifecycleRepository;
import com.enviouse.futureshops.server.market.bazaar.escrow.BazaarEscrowLifecycleState;
import com.enviouse.futureshops.server.market.bazaar.escrow.BazaarEscrowLifecycleStateCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;

public final class BazaarSavedData extends EscrowManagedSavedData {
    public static final String DATA_NAME = "futureshops_bazaar";
    public static final int CURRENT_VERSION = 2;

    private static final String SNAPSHOT_KEY = "snapshot";
    private static final String ESCROW_LIFECYCLE_KEY = "escrowLifecycle";
    private static final String SNAPSHOT_DIGEST_KEY = "snapshotDigest";
    private static final String ESCROW_LIFECYCLE_DIGEST_KEY =
            "escrowLifecycleDigest";
    private static final BazaarOrderBookSnapshot EMPTY =
            new BazaarOrderBook().snapshot();

    private BazaarOrderBookSnapshot snapshot = EMPTY;
    private BazaarEscrowLifecycleState escrowLifecycle =
            BazaarEscrowLifecycleState.empty();

    public static BazaarSavedData load(CompoundTag tag) {
        Objects.requireNonNull(tag, "tag");
        if (tag.contains("schemaVersion")
                && !tag.contains("schemaVersion", Tag.TAG_INT)) {
            throw new IllegalStateException(
                    "Bazaar schema has the wrong type");
        }
        int version = SavedDataMigrations.readVersion(tag);
        if (version > CURRENT_VERSION) {
            throw new IllegalStateException(
                    "Bazaar schema is newer than this build");
        }
        if (version < 0) {
            throw new IllegalStateException(
                    "Bazaar schema is invalid");
        }
        SavedDataMigrations.needsMigration(DATA_NAME, version,
                CURRENT_VERSION);
        BazaarSavedData data = new BazaarSavedData();
        if (version == 0 && !tag.contains(SNAPSHOT_KEY)) {
            data.setDirty();
            return data;
        }
        if (!tag.contains(SNAPSHOT_KEY, Tag.TAG_BYTE_ARRAY)) {
            throw new IllegalStateException(
                    "Bazaar snapshot is missing");
        }
        try {
            byte[] encodedSnapshot = tag.getByteArray(SNAPSHOT_KEY);
            data.snapshot = BazaarOrderBookSnapshotCodec.decode(
                    encodedSnapshot);
            if (version >= CURRENT_VERSION) {
                if (!tag.contains(ESCROW_LIFECYCLE_KEY,
                        Tag.TAG_BYTE_ARRAY)) {
                    throw new IllegalStateException(
                            "Bazaar escrow lifecycle snapshot is missing");
                }
                byte[] encodedLifecycle = tag.getByteArray(
                        ESCROW_LIFECYCLE_KEY);
                requireDigest(tag, SNAPSHOT_DIGEST_KEY,
                        encodedSnapshot);
                requireDigest(tag, ESCROW_LIFECYCLE_DIGEST_KEY,
                        encodedLifecycle);
                data.escrowLifecycle =
                        BazaarEscrowLifecycleStateCodec.decode(
                                encodedLifecycle);
                BazaarEscrowLifecycleRepository.validateAgainst(
                        data.snapshot, data.escrowLifecycle);
            }
            data.requireCombinedSize(data.snapshot,
                    data.escrowLifecycle);
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "Bazaar snapshot failed validation", exception);
        }
        if (version < CURRENT_VERSION) {
            data.setDirty();
        }
        return data;
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag) {
        BazaarEscrowLifecycleRepository.validateAgainst(snapshot,
                escrowLifecycle);
        requireCombinedSize(snapshot, escrowLifecycle);
        SavedDataMigrations.writeVersion(tag, CURRENT_VERSION);
        byte[] encodedSnapshot = BazaarOrderBookSnapshotCodec.encode(
                snapshot);
        byte[] encodedLifecycle = BazaarEscrowLifecycleStateCodec.encode(
                escrowLifecycle);
        tag.putByteArray(SNAPSHOT_KEY, encodedSnapshot);
        tag.putByteArray(ESCROW_LIFECYCLE_KEY, encodedLifecycle);
        tag.putByteArray(SNAPSHOT_DIGEST_KEY, sha256(encodedSnapshot));
        tag.putByteArray(ESCROW_LIFECYCLE_DIGEST_KEY,
                sha256(encodedLifecycle));
        return tag;
    }

    public static BazaarSavedData get(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return EscrowRuntimeStoreBinding.bind(server,
                server.overworld().getDataStorage().computeIfAbsent(
                        BazaarSavedData::load,
                        BazaarSavedData::new,
                        DATA_NAME));
    }

    public synchronized BazaarMutation.ApplyResult preflightCommitted(
            BazaarMutation mutation
    ) {
        BazaarMutation.ApplyResult result = Objects.requireNonNull(
                mutation, "mutation").apply(snapshot);
        BazaarEscrowLifecycleRepository.validateAgainst(result.snapshot(),
                escrowLifecycle);
        requireCombinedSize(result.snapshot(), escrowLifecycle);
        return result;
    }

    public synchronized BazaarMutation.ApplyResult applyCommitted(
            BazaarMutation mutation
    ) {
        requireEscrowMutationPermit();
        BazaarMutation.ApplyResult result = preflightCommitted(mutation);
        if (!result.replayed()) {
            snapshot = result.snapshot();
            setDirty();
        }
        return result;
    }

    public synchronized BazaarEscrowLifecycleRepository.ApplyResult
    preflightEscrowLifecycleCommitted(
            BazaarEscrowLifecycleEvent event
    ) {
        BazaarEscrowLifecycleRepository.ApplyResult result =
                BazaarEscrowLifecycleRepository.apply(snapshot,
                        escrowLifecycle,
                        Objects.requireNonNull(event, "event"));
        BazaarEscrowLifecycleRepository.validateAgainst(
                result.orderBook(), result.lifecycle());
        requireCombinedSize(result.orderBook(), result.lifecycle());
        return result;
    }

    public synchronized BazaarEscrowLifecycleRepository.ApplyResult
    applyEscrowLifecycleCommitted(
            BazaarEscrowLifecycleEvent event
    ) {
        requireEscrowMutationPermit();
        BazaarEscrowLifecycleRepository.ApplyResult result =
                preflightEscrowLifecycleCommitted(event);
        if (!result.replayed()) {
            snapshot = result.orderBook();
            escrowLifecycle = result.lifecycle();
            setDirty();
        }
        return result;
    }

    public synchronized void replaceFromValidated(BazaarSavedData source) {
        requireEscrowMutationPermit();
        Objects.requireNonNull(source, "source");
        if (source == this) {
            return;
        }
        snapshot = BazaarOrderBookSnapshotCodec.decode(
                BazaarOrderBookSnapshotCodec.encode(source.snapshot()));
        escrowLifecycle = BazaarEscrowLifecycleStateCodec.decode(
                BazaarEscrowLifecycleStateCodec.encode(
                        source.escrowLifecycleSnapshot()));
        BazaarEscrowLifecycleRepository.validateAgainst(snapshot,
                escrowLifecycle);
        requireCombinedSize(snapshot, escrowLifecycle);
        setDirty();
    }

    public synchronized BazaarOrderBookSnapshot snapshot() {
        return snapshot;
    }

    public synchronized BazaarEscrowLifecycleState
    escrowLifecycleSnapshot() {
        return escrowLifecycle;
    }

    public synchronized BazaarCreateEscrowIntent createIntent(
            UUID requestId
    ) {
        return escrowLifecycle.createIntents().get(
                Objects.requireNonNull(requestId, "requestId"));
    }

    public synchronized BazaarProduct product(String productId) {
        Objects.requireNonNull(productId, "productId");
        return snapshot.products().stream()
                .filter(product -> product.productId().equals(productId))
                .max(Comparator.comparingLong(BazaarProduct::version))
                .orElse(null);
    }

    public synchronized BazaarProduct productVersion(String productId,
                                                      long version) {
        Objects.requireNonNull(productId, "productId");
        return snapshot.products().stream()
                .filter(product -> product.productId().equals(productId)
                        && product.version() == version)
                .findFirst().orElse(null);
    }

    public synchronized BazaarOrder order(UUID orderId) {
        Objects.requireNonNull(orderId, "orderId");
        return snapshot.orders().stream()
                .filter(order -> order.orderId().equals(orderId))
                .findFirst().orElse(null);
    }

    public synchronized BazaarFill fill(UUID fillId) {
        Objects.requireNonNull(fillId, "fillId");
        return snapshot.fills().stream()
                .filter(fill -> fill.fillId().equals(fillId))
                .findFirst().orElse(null);
    }

    public synchronized BazaarRequestReceipt receipt(UUID requestId) {
        return snapshot.receipts().get(
                Objects.requireNonNull(requestId, "requestId"));
    }

    public synchronized String lifecycleReceipt(UUID mutationId) {
        return snapshot.lifecycleReceipts().get(
                Objects.requireNonNull(mutationId, "mutationId"));
    }

    public synchronized boolean hasMaterializedState() {
        return !snapshot.equals(EMPTY)
                || escrowLifecycle.hasMaterializedState();
    }

    private void requireCombinedSize(
            BazaarOrderBookSnapshot orderBook,
            BazaarEscrowLifecycleState lifecycle
    ) {
        long size = Math.addExact(
                BazaarOrderBookSnapshotCodec.encode(orderBook).length,
                BazaarEscrowLifecycleStateCodec.encode(lifecycle).length);
        if (size > BazaarOrderBookSnapshotCodec.MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException(
                    "Bazaar order book and escrow lifecycle exceed their shared limit");
        }
    }

    private static void requireDigest(
            CompoundTag tag,
            String key,
            byte[] encoded
    ) {
        if (!tag.contains(key, Tag.TAG_BYTE_ARRAY)) {
            throw new IllegalStateException(
                    "Bazaar snapshot digest is missing");
        }
        byte[] digest = tag.getByteArray(key);
        if (digest.length != 32 || !MessageDigest.isEqual(
                digest, sha256(encoded))) {
            throw new IllegalStateException(
                    "Bazaar snapshot digest is invalid");
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
