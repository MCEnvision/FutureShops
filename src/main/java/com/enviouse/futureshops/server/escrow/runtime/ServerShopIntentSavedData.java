package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.SavedDataMigrations;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ServerShopIntentSavedData
        extends EscrowManagedSavedData {
    public static final String DATA_NAME =
            "futureshops_escrow_server_shop_intents";
    public static final int CURRENT_VERSION = 1;
    public static final int MAXIMUM_ENTRIES = 100_000;
    public static final long MAXIMUM_PAYLOAD_BYTES = 50_331_648L;

    private static final String ENTRIES_KEY = "entries";
    private static final int SELL = 1;
    private static final int BARTER = 2;
    private static final Comparator<UUID> UUID_ORDER = Comparator
            .comparingLong(UUID::getMostSignificantBits)
            .thenComparingLong(UUID::getLeastSignificantBits);

    private final Map<UUID, ServerShopSellIntent> sellIntents =
            new HashMap<>();
    private final Map<UUID, ServerShopBarterIntent> barterIntents =
            new HashMap<>();
    private long payloadBytes;

    public static ServerShopIntentSavedData load(CompoundTag tag) {
        Objects.requireNonNull(tag, "tag");
        if (tag.contains("schemaVersion")
                && !tag.contains("schemaVersion", Tag.TAG_INT)) {
            throw new IllegalStateException(
                    "Server shop intent schema has the wrong type");
        }
        int version = SavedDataMigrations.readVersion(tag);
        if (version > CURRENT_VERSION) {
            throw new IllegalStateException(
                    "Server shop intent schema is newer than this build");
        }
        if (version < 0) {
            throw new IllegalStateException(
                    "Server shop intent schema is invalid");
        }
        SavedDataMigrations.needsMigration(DATA_NAME, version,
                CURRENT_VERSION);
        ServerShopIntentSavedData data =
                new ServerShopIntentSavedData();
        if (version == 0 && !tag.contains(ENTRIES_KEY)) {
            data.setDirty();
            return data;
        }
        if (!tag.contains(ENTRIES_KEY, Tag.TAG_LIST)) {
            throw new IllegalStateException(
                    "Server shop intent entries are missing");
        }
        ListTag entries = SavedDataMigrations.requireList(
                tag, ENTRIES_KEY, Tag.TAG_COMPOUND,
                MAXIMUM_ENTRIES, "Server shop intent entries");
        try {
            for (Tag raw : entries) {
                CompoundTag entry = (CompoundTag) raw;
                if (!entry.contains("kind", Tag.TAG_INT)
                        || !entry.contains("payload",
                        Tag.TAG_BYTE_ARRAY)) {
                    throw new IllegalArgumentException(
                            "Server shop intent entry is malformed");
                }
                byte[] payload = entry.getByteArray("payload");
                data.requirePayloadCapacity(payload.length);
                if (entry.getInt("kind") == SELL) {
                    ServerShopSellIntent value =
                            ServerShopSellIntentCodec.decode(payload);
                    data.putLoadedSell(value);
                } else if (entry.getInt("kind") == BARTER) {
                    ServerShopBarterIntent value =
                            ServerShopBarterIntentCodec.decode(payload);
                    data.putLoadedBarter(value);
                } else {
                    throw new IllegalArgumentException(
                            "Server shop intent entry kind is invalid");
                }
                data.payloadBytes = Math.addExact(data.payloadBytes,
                        payload.length);
            }
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "Server shop intent entries failed validation",
                    exception);
        }
        if (version < CURRENT_VERSION) {
            data.setDirty();
        }
        return data;
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag) {
        SavedDataMigrations.writeVersion(tag, CURRENT_VERSION);
        List<EncodedEntry> encoded = encodedEntries();
        ListTag entries = new ListTag();
        for (EncodedEntry value : encoded) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("kind", value.kind());
            entry.putByteArray("payload", value.payload());
            entries.add(entry);
        }
        tag.put(ENTRIES_KEY, entries);
        return tag;
    }

    public static ServerShopIntentSavedData get(MinecraftServer server) {
        return EscrowRuntimeStoreBinding.bind(server,
                server.overworld().getDataStorage().computeIfAbsent(
                        ServerShopIntentSavedData::load,
                        ServerShopIntentSavedData::new, DATA_NAME));
    }

    public synchronized Optional<ServerShopSellIntent> sellIntent(
            UUID requestId
    ) {
        return Optional.ofNullable(sellIntents.get(
                Objects.requireNonNull(requestId, "requestId")));
    }

    public synchronized Optional<ServerShopBarterIntent> barterIntent(
            UUID requestId
    ) {
        return Optional.ofNullable(barterIntents.get(
                Objects.requireNonNull(requestId, "requestId")));
    }

    public synchronized List<ServerShopSellIntent> preparedSellIntents(
            int limit
    ) {
        requireLimit(limit);
        return sellIntents.values().stream()
                .filter(value -> value.status()
                        == ServerShopSellIntent.Status.PREPARED)
                .sorted(Comparator.comparing(
                        ServerShopSellIntent::requestId, UUID_ORDER))
                .limit(limit).toList();
    }

    public synchronized List<ServerShopBarterIntent>
    preparedBarterIntents(int limit) {
        requireLimit(limit);
        return barterIntents.values().stream()
                .filter(value -> value.status()
                        == ServerShopBarterIntent.Status.PREPARED)
                .sorted(Comparator.comparing(
                        ServerShopBarterIntent::requestId, UUID_ORDER))
                .limit(limit).toList();
    }

    public synchronized MutationDisposition preflightPrepareSell(
            ServerShopSellIntent intent
    ) {
        return inspectPrepareSell(intent);
    }

    public synchronized MutationDisposition applyPrepareSell(
            ServerShopSellIntent intent
    ) {
        requireEscrowMutationPermit();
        MutationDisposition disposition = inspectPrepareSell(intent);
        if (disposition == MutationDisposition.APPLIED) {
            byte[] encoded = ServerShopSellIntentCodec.encode(intent);
            sellIntents.put(intent.requestId(), intent);
            payloadBytes = Math.addExact(payloadBytes, encoded.length);
            setDirty();
        }
        return disposition;
    }

    public synchronized MutationDisposition preflightSellAbort(
            ServerShopSellIntent expected,
            ServerShopSellIntent terminal
    ) {
        new ServerShopSellLifecycleEvent.Abort(expected, terminal);
        return inspectSellTransition(expected, terminal);
    }

    public synchronized MutationDisposition applySellAbort(
            ServerShopSellIntent expected,
            ServerShopSellIntent terminal
    ) {
        requireEscrowMutationPermit();
        new ServerShopSellLifecycleEvent.Abort(expected, terminal);
        MutationDisposition disposition = inspectSellTransition(
                expected, terminal);
        if (disposition == MutationDisposition.APPLIED) {
            replaceSell(expected, terminal);
        }
        return disposition;
    }

    public synchronized MutationDisposition preflightCompleteSell(
            ServerShopSellIntent completed
    ) {
        return inspectCompleteSell(completed);
    }

    public synchronized MutationDisposition applyCompleteSell(
            ServerShopSellIntent completed
    ) {
        requireEscrowMutationPermit();
        MutationDisposition disposition = inspectCompleteSell(completed);
        if (disposition == MutationDisposition.APPLIED) {
            ServerShopSellIntent current = sellIntents.get(
                    completed.requestId());
            replaceSell(current, completed);
        }
        return disposition;
    }

    public synchronized MutationDisposition preflightPrepareBarter(
            ServerShopBarterIntent intent
    ) {
        return inspectPrepareBarter(intent);
    }

    public synchronized MutationDisposition applyPrepareBarter(
            ServerShopBarterIntent intent
    ) {
        requireEscrowMutationPermit();
        MutationDisposition disposition = inspectPrepareBarter(intent);
        if (disposition == MutationDisposition.APPLIED) {
            byte[] encoded = ServerShopBarterIntentCodec.encode(intent);
            barterIntents.put(intent.requestId(), intent);
            payloadBytes = Math.addExact(payloadBytes, encoded.length);
            setDirty();
        }
        return disposition;
    }

    public synchronized MutationDisposition preflightCompleteBarter(
            ServerShopBarterIntent terminal
    ) {
        return inspectBarterTransition(terminal);
    }

    public synchronized MutationDisposition applyCompleteBarter(
            ServerShopBarterIntent terminal
    ) {
        requireEscrowMutationPermit();
        MutationDisposition disposition = inspectBarterTransition(terminal);
        if (disposition == MutationDisposition.APPLIED) {
            ServerShopBarterIntent current = barterIntents.get(
                    terminal.requestId());
            replaceBarter(current, terminal);
        }
        return disposition;
    }

    public synchronized void replaceFromValidated(
            ServerShopIntentSavedData source
    ) {
        requireEscrowMutationPermit();
        Objects.requireNonNull(source, "source");
        if (source == this) {
            return;
        }
        Snapshot snapshot = source.snapshot();
        sellIntents.clear();
        sellIntents.putAll(snapshot.sell());
        barterIntents.clear();
        barterIntents.putAll(snapshot.barter());
        payloadBytes = snapshot.payloadBytes();
        setDirty();
    }

    public synchronized boolean hasMaterializedState() {
        return !sellIntents.isEmpty() || !barterIntents.isEmpty();
    }

    public synchronized int size() {
        return Math.addExact(sellIntents.size(), barterIntents.size());
    }

    private MutationDisposition inspectPrepareSell(
            ServerShopSellIntent intent
    ) {
        Objects.requireNonNull(intent, "intent");
        if (intent.status() != ServerShopSellIntent.Status.PREPARED) {
            throw new IllegalArgumentException(
                    "Server shop sell intent is not prepared");
        }
        rejectCrossDomain(intent.requestId(), barterIntents);
        ServerShopSellIntent current = sellIntents.get(intent.requestId());
        if (current != null) {
            if (current.equals(intent)) {
                return MutationDisposition.REPLAYED;
            }
            throw conflict();
        }
        requireEntryCapacity();
        requirePayloadCapacity(
                ServerShopSellIntentCodec.encode(intent).length);
        return MutationDisposition.APPLIED;
    }

    private MutationDisposition inspectPrepareBarter(
            ServerShopBarterIntent intent
    ) {
        Objects.requireNonNull(intent, "intent");
        if (intent.status() != ServerShopBarterIntent.Status.PREPARED) {
            throw new IllegalArgumentException(
                    "Server shop barter intent is not prepared");
        }
        rejectCrossDomain(intent.requestId(), sellIntents);
        ServerShopBarterIntent current = barterIntents.get(
                intent.requestId());
        if (current != null) {
            if (current.equals(intent)) {
                return MutationDisposition.REPLAYED;
            }
            throw conflict();
        }
        requireEntryCapacity();
        requirePayloadCapacity(
                ServerShopBarterIntentCodec.encode(intent).length);
        return MutationDisposition.APPLIED;
    }

    private MutationDisposition inspectSellTransition(
            ServerShopSellIntent expected,
            ServerShopSellIntent terminal
    ) {
        ServerShopSellIntent current = sellIntents.get(
                terminal.requestId());
        if (terminal.equals(current)) {
            return MutationDisposition.REPLAYED;
        }
        if (!expected.equals(current)) {
            throw conflict();
        }
        requireReplacementCapacity(
                ServerShopSellIntentCodec.encode(current).length,
                ServerShopSellIntentCodec.encode(terminal).length);
        return MutationDisposition.APPLIED;
    }

    private MutationDisposition inspectCompleteSell(
            ServerShopSellIntent completed
    ) {
        Objects.requireNonNull(completed, "completed");
        if (completed.status()
                != ServerShopSellIntent.Status.COMMITTED
                || completed.revision() != 1L) {
            throw new IllegalArgumentException(
                    "Server shop sell completed intent is invalid");
        }
        ServerShopSellIntent current = sellIntents.get(
                completed.requestId());
        if (completed.equals(current)) {
            return MutationDisposition.REPLAYED;
        }
        if (current == null
                || current.status()
                != ServerShopSellIntent.Status.PREPARED
                || !current.intentFingerprint().equals(
                completed.intentFingerprint())) {
            throw conflict();
        }
        requireReplacementCapacity(
                ServerShopSellIntentCodec.encode(current).length,
                ServerShopSellIntentCodec.encode(completed).length);
        return MutationDisposition.APPLIED;
    }

    private MutationDisposition inspectBarterTransition(
            ServerShopBarterIntent terminal
    ) {
        Objects.requireNonNull(terminal, "terminal");
        if (terminal.status()
                == ServerShopBarterIntent.Status.PREPARED
                || terminal.revision() != 1L) {
            throw new IllegalArgumentException(
                    "Server shop barter terminal intent is invalid");
        }
        ServerShopBarterIntent current = barterIntents.get(
                terminal.requestId());
        if (terminal.equals(current)) {
            return MutationDisposition.REPLAYED;
        }
        if (current == null
                || current.status()
                != ServerShopBarterIntent.Status.PREPARED
                || !current.intentFingerprint().equals(
                terminal.intentFingerprint())) {
            throw conflict();
        }
        requireReplacementCapacity(
                ServerShopBarterIntentCodec.encode(current).length,
                ServerShopBarterIntentCodec.encode(terminal).length);
        return MutationDisposition.APPLIED;
    }

    private void replaceSell(
            ServerShopSellIntent current,
            ServerShopSellIntent replacement
    ) {
        int before = ServerShopSellIntentCodec.encode(current).length;
        int after = ServerShopSellIntentCodec.encode(replacement).length;
        sellIntents.put(replacement.requestId(), replacement);
        payloadBytes = Math.addExact(Math.subtractExact(payloadBytes,
                before), after);
        setDirty();
    }

    private void replaceBarter(
            ServerShopBarterIntent current,
            ServerShopBarterIntent replacement
    ) {
        int before = ServerShopBarterIntentCodec.encode(current).length;
        int after = ServerShopBarterIntentCodec.encode(replacement).length;
        barterIntents.put(replacement.requestId(), replacement);
        payloadBytes = Math.addExact(Math.subtractExact(payloadBytes,
                before), after);
        setDirty();
    }

    private void putLoadedSell(ServerShopSellIntent value) {
        if (barterIntents.containsKey(value.requestId())
                || sellIntents.putIfAbsent(value.requestId(), value)
                != null) {
            throw conflict();
        }
    }

    private void putLoadedBarter(ServerShopBarterIntent value) {
        if (sellIntents.containsKey(value.requestId())
                || barterIntents.putIfAbsent(value.requestId(), value)
                != null) {
            throw conflict();
        }
    }

    private List<EncodedEntry> encodedEntries() {
        List<EncodedEntry> values = new ArrayList<>(size());
        sellIntents.values().forEach(value -> values.add(
                new EncodedEntry(SELL, value.requestId(),
                        ServerShopSellIntentCodec.encode(value))));
        barterIntents.values().forEach(value -> values.add(
                new EncodedEntry(BARTER, value.requestId(),
                        ServerShopBarterIntentCodec.encode(value))));
        values.sort(Comparator.comparing(EncodedEntry::requestId,
                UUID_ORDER));
        return List.copyOf(values);
    }

    private synchronized Snapshot snapshot() {
        return new Snapshot(Map.copyOf(sellIntents),
                Map.copyOf(barterIntents), payloadBytes);
    }

    private void requireEntryCapacity() {
        if (size() >= MAXIMUM_ENTRIES) {
            throw new IllegalStateException(
                    "Server shop intent entry capacity is exhausted");
        }
    }

    private void requirePayloadCapacity(long additional) {
        if (additional <= 0L
                || payloadBytes > MAXIMUM_PAYLOAD_BYTES - additional) {
            throw new IllegalStateException(
                    "Server shop intent payload capacity is exhausted");
        }
    }

    private void requireReplacementCapacity(long before, long after) {
        long withoutCurrent = Math.subtractExact(payloadBytes, before);
        if (after <= 0L
                || withoutCurrent > MAXIMUM_PAYLOAD_BYTES - after) {
            throw new IllegalStateException(
                    "Server shop intent payload capacity is exhausted");
        }
    }

    private static void requireLimit(int limit) {
        if (limit <= 0 || limit > MAXIMUM_ENTRIES) {
            throw new IllegalArgumentException(
                    "Server shop intent recovery limit is invalid");
        }
    }

    private static void rejectCrossDomain(
            UUID requestId,
            Map<UUID, ?> otherDomain
    ) {
        if (otherDomain.containsKey(requestId)) {
            throw conflict();
        }
    }

    private static ServerShopIntentConflictException conflict() {
        return new ServerShopIntentConflictException(
                "Server shop intent request conflicts with stored state");
    }

    public enum MutationDisposition {
        APPLIED,
        REPLAYED
    }

    private record EncodedEntry(
            int kind,
            UUID requestId,
            byte[] payload
    ) {
        private EncodedEntry {
            payload = payload.clone();
        }

        @Override
        public byte[] payload() {
            return payload.clone();
        }
    }

    private record Snapshot(
            Map<UUID, ServerShopSellIntent> sell,
            Map<UUID, ServerShopBarterIntent> barter,
            long payloadBytes
    ) {
    }
}
