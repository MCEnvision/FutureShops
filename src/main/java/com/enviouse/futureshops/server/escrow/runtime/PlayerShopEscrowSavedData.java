package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.SavedDataMigrations;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopEscrowLifecycleEvent;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopExecutionSnapshot;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopExecutionSnapshotCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class PlayerShopEscrowSavedData
        extends EscrowManagedSavedData {
    public static final String DATA_NAME =
            "futureshops_escrow_player_shop";
    public static final int CURRENT_VERSION = 1;
    public static final int MAXIMUM_ENTRIES = 100_000;
    public static final long MAXIMUM_PAYLOAD_BYTES = 67_000_000L;

    private static final String ENTRIES_KEY = "entries";
    private static final Comparator<UUID> UUID_ORDER = Comparator
            .comparingLong(UUID::getMostSignificantBits)
            .thenComparingLong(UUID::getLeastSignificantBits);

    private final Map<UUID, Entry> entries = new HashMap<>();
    private long payloadBytes;

    public static PlayerShopEscrowSavedData load(CompoundTag tag) {
        Objects.requireNonNull(tag, "tag");
        if (tag.contains("schemaVersion")
                && !tag.contains("schemaVersion", Tag.TAG_INT)) {
            throw new IllegalStateException(
                    "Player shop escrow schema has the wrong type");
        }
        int version = SavedDataMigrations.readVersion(tag);
        if (version > CURRENT_VERSION || version < 0) {
            throw new IllegalStateException(
                    version > CURRENT_VERSION
                            ? "Player shop escrow schema is newer than this build"
                            : "Player shop escrow schema is invalid");
        }
        SavedDataMigrations.needsMigration(DATA_NAME, version,
                CURRENT_VERSION);
        PlayerShopEscrowSavedData data = new PlayerShopEscrowSavedData();
        if (version == 0 && !tag.contains(ENTRIES_KEY)) {
            data.setDirty();
            return data;
        }
        if (!tag.contains(ENTRIES_KEY, Tag.TAG_LIST)) {
            throw new IllegalStateException(
                    "Player shop escrow entries are missing");
        }
        ListTag values = tag.getList(ENTRIES_KEY, Tag.TAG_COMPOUND);
        if (values.size() > MAXIMUM_ENTRIES) {
            throw new IllegalStateException(
                    "Player shop escrow entry capacity is exceeded");
        }
        try {
            for (Tag raw : values) {
                CompoundTag value = (CompoundTag) raw;
                if (!value.hasUUID("requestId")
                        || !value.contains("revision", Tag.TAG_LONG)
                        || !value.contains("settlementImported",
                        Tag.TAG_BYTE)
                        || !value.contains("payload", Tag.TAG_BYTE_ARRAY)) {
                    throw new IllegalArgumentException(
                            "Player shop escrow entry is malformed");
                }
                UUID requestId = value.getUUID("requestId");
                long revision = value.getLong("revision");
                byte[] payload = value.getByteArray("payload");
                PlayerShopExecutionSnapshot snapshot =
                        PlayerShopExecutionSnapshotCodec.decode(payload);
                Entry entry = new Entry(revision, snapshot,
                        value.getBoolean("settlementImported"));
                if (!requestId.equals(snapshot.intent().requestId())
                        || data.entries.put(requestId, entry) != null) {
                    throw new IllegalArgumentException(
                            "Player shop escrow entry identity is invalid");
                }
                data.payloadBytes = Math.addExact(data.payloadBytes,
                        payload.length);
                if (data.payloadBytes > MAXIMUM_PAYLOAD_BYTES) {
                    throw new IllegalArgumentException(
                            "Player shop escrow payload capacity is exceeded");
                }
            }
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "Player shop escrow entries failed validation", exception);
        }
        if (version < CURRENT_VERSION) {
            data.setDirty();
        }
        return data;
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag) {
        SavedDataMigrations.writeVersion(tag, CURRENT_VERSION);
        ListTag values = new ListTag();
        entries.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(UUID_ORDER))
                .forEach(entry -> {
                    CompoundTag value = new CompoundTag();
                    value.putUUID("requestId", entry.getKey());
                    value.putLong("revision", entry.getValue().revision());
                    value.putBoolean("settlementImported",
                            entry.getValue().settlementImported());
                    value.putByteArray("payload",
                            PlayerShopExecutionSnapshotCodec.encode(
                                    entry.getValue().snapshot()));
                    values.add(value);
                });
        tag.put(ENTRIES_KEY, values);
        return tag;
    }

    public static PlayerShopEscrowSavedData get(MinecraftServer server) {
        return EscrowRuntimeStoreBinding.bind(server,
                server.overworld().getDataStorage().computeIfAbsent(
                        PlayerShopEscrowSavedData::load,
                        PlayerShopEscrowSavedData::new, DATA_NAME));
    }

    public synchronized Optional<Entry> entry(UUID requestId) {
        return Optional.ofNullable(entries.get(
                Objects.requireNonNull(requestId, "requestId")));
    }

    public synchronized List<Entry> pendingRecovery(int limit) {
        if (limit <= 0 || limit > MAXIMUM_ENTRIES) {
            throw new IllegalArgumentException(
                    "Player shop recovery limit is invalid");
        }
        return entries.entrySet().stream()
                .filter(value -> value.getValue().snapshot().commit() == null)
                .sorted(Map.Entry.comparingByKey(UUID_ORDER))
                .limit(limit).map(Map.Entry::getValue).toList();
    }

    public synchronized MutationDisposition preflight(
            PlayerShopEscrowLifecycleEvent event
    ) {
        return inspect(event);
    }

    public synchronized MutationDisposition apply(
            PlayerShopEscrowLifecycleEvent event
    ) {
        requireEscrowMutationPermit();
        MutationDisposition disposition = inspect(event);
        if (disposition == MutationDisposition.APPLIED) {
            Entry previous = entries.put(event.requestId(), new Entry(
                    event.nextRevision(), event.snapshot(),
                    event.settlementImported()));
            int previousBytes = previous == null ? 0
                    : PlayerShopExecutionSnapshotCodec.encode(
                    previous.snapshot()).length;
            int nextBytes = PlayerShopExecutionSnapshotCodec.encode(
                    event.snapshot()).length;
            payloadBytes = Math.addExact(
                    Math.subtractExact(payloadBytes, previousBytes), nextBytes);
            setDirty();
        }
        return disposition;
    }

    public synchronized void replaceFromValidated(
            PlayerShopEscrowSavedData source
    ) {
        requireEscrowMutationPermit();
        Objects.requireNonNull(source, "source");
        if (source == this) return;
        entries.clear();
        entries.putAll(source.snapshotEntries());
        payloadBytes = source.payloadBytes;
        setDirty();
    }

    public synchronized boolean hasMaterializedState() {
        return !entries.isEmpty();
    }

    public synchronized int size() {
        return entries.size();
    }

    private MutationDisposition inspect(PlayerShopEscrowLifecycleEvent event) {
        Objects.requireNonNull(event, "event");
        byte[] encoded = PlayerShopExecutionSnapshotCodec.encode(
                event.snapshot());
        Entry current = entries.get(event.requestId());
        if (current != null && current.revision() == event.nextRevision()
                && current.snapshot().equals(event.snapshot())
                && current.settlementImported()
                == event.settlementImported()) {
            return MutationDisposition.REPLAYED;
        }
        if (current == null) {
            if (event.expectedRevision() != -1L
                    || event.nextRevision() != 0L
                    || phase(event.snapshot()) != 0
                    || event.settlementImported()) {
                throw new PlayerShopEscrowConflictException();
            }
            requireEntryCapacity();
            requirePayloadCapacity(encoded.length, 0);
            return MutationDisposition.APPLIED;
        }
        if (current.revision() != event.expectedRevision()
                || !sameIdentity(current.snapshot(), event.snapshot())
                || phase(event.snapshot()) < phase(current.snapshot())
                || current.settlementImported()
                && !event.settlementImported()) {
            throw new PlayerShopEscrowConflictException();
        }
        requirePayloadCapacity(encoded.length,
                PlayerShopExecutionSnapshotCodec.encode(
                        current.snapshot()).length);
        return MutationDisposition.APPLIED;
    }

    private static int phase(PlayerShopExecutionSnapshot snapshot) {
        if (snapshot.commit() != null) return 4;
        if (snapshot.claimCreation() != null) return 3;
        if (snapshot.funding() != null) return 2;
        if (snapshot.preparation() != null) return 1;
        return 0;
    }

    private static boolean sameIdentity(
            PlayerShopExecutionSnapshot current,
            PlayerShopExecutionSnapshot next
    ) {
        return current.requestIdentity().equals(next.requestIdentity())
                && current.intent().intentFingerprint().equals(
                next.intent().intentFingerprint())
                && Objects.equals(current.settlementImport(),
                next.settlementImport());
    }

    private void requireEntryCapacity() {
        if (entries.size() >= MAXIMUM_ENTRIES) {
            throw new IllegalStateException(
                    "Player shop escrow entry capacity is exceeded");
        }
    }

    private void requirePayloadCapacity(int nextBytes, int previousBytes) {
        long next = Math.addExact(Math.subtractExact(payloadBytes,
                previousBytes), nextBytes);
        if (next > MAXIMUM_PAYLOAD_BYTES) {
            throw new IllegalStateException(
                    "Player shop escrow payload capacity is exceeded");
        }
    }

    private synchronized Map<UUID, Entry> snapshotEntries() {
        return Map.copyOf(entries);
    }

    public record Entry(
            long revision,
            PlayerShopExecutionSnapshot snapshot,
            boolean settlementImported
    ) {
        public Entry {
            if (revision < 0L) {
                throw new IllegalArgumentException(
                        "Player shop escrow revision is invalid");
            }
            snapshot = Objects.requireNonNull(snapshot, "snapshot");
            if (settlementImported && snapshot.settlementImport() == null) {
                throw new IllegalArgumentException(
                        "Player shop settlement marker is invalid");
            }
        }
    }

    public enum MutationDisposition {
        APPLIED,
        REPLAYED
    }
}
