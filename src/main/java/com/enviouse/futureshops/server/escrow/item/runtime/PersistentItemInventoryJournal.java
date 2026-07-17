package com.enviouse.futureshops.server.escrow.item.runtime;

import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationReceipt;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationToken;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class PersistentItemInventoryJournal {
    public static final int MAX_ENTRIES = 16_384;
    public static final int MAX_PLAYER_REQUESTS = 4_096;
    public static final int MAX_PREPARED_PER_PLAYER = 256;
    public static final int MAX_QUERY_RESULTS = 256;
    public static final int MAX_TOMBSTONES = 65_536;

    private static final long MAX_ENTRY_BYTES =
            ItemInventoryJournalSnapshotCodec.MAX_ENCODED_BYTES - 128L;

    private final Map<UUID, ItemInventoryJournalEntry> byRequest =
            new LinkedHashMap<>();
    private final Map<UUID, ItemInventoryTerminalTombstone> tombstones =
            new LinkedHashMap<>();
    private final Map<UUID, LinkedHashSet<UUID>> requestsByPlayer =
            new LinkedHashMap<>();
    private final Map<UUID, LinkedHashSet<UUID>> preparedByPlayer =
            new LinkedHashMap<>();
    private final Set<UUID> quarantinedPlayers = new LinkedHashSet<>();
    private final Map<UUID, ItemInventoryQuarantineAdministration>
            administrationsByCommand = new LinkedHashMap<>();
    private final Map<UUID, List<ItemInventoryQuarantineAdministration>>
            administrationsByRequest = new LinkedHashMap<>();
    private final Set<UUID> resolvedQuarantines = new LinkedHashSet<>();
    private long revision;
    private long entryBytes;

    public synchronized ItemInventoryJournalApplyResult preflightCommitted(
            ItemInventoryJournalTransition transition
    ) {
        return decide(Objects.requireNonNull(transition, "transition"));
    }

    public synchronized ItemInventoryJournalApplyResult applyCommitted(
            ItemInventoryJournalTransition transition
    ) {
        ItemInventoryJournalTransition value = Objects.requireNonNull(
                transition, "transition");
        ItemInventoryJournalApplyResult decision = decide(value);
        if (decision.replayed()) {
            return decision;
        }
        UUID requestId = value.requestId();
        ItemInventoryJournalEntry prior = byRequest.put(requestId,
                decision.entry());
        UUID playerId = decision.entry().intent().token().playerId();
        if (prior == null) {
            requestsByPlayer.computeIfAbsent(playerId,
                    ignored -> new LinkedHashSet<>()).add(requestId);
        } else if (prior.status() == ItemInventoryJournalStatus.PREPARED) {
            removePrepared(playerId, requestId);
        }
        if (decision.entry().status()
                == ItemInventoryJournalStatus.PREPARED) {
            preparedByPlayer.computeIfAbsent(playerId,
                    ignored -> new LinkedHashSet<>()).add(requestId);
        }
        if (decision.entry().status()
                == ItemInventoryJournalStatus.QUARANTINED) {
            quarantinedPlayers.add(playerId);
        }
        entryBytes = resultingEntryBytes(prior, decision.entry());
        revision = Math.addExact(revision, 1L);
        return decision;
    }

    public synchronized Optional<ItemInventoryJournalEntry> find(
            UUID requestId
    ) {
        return Optional.ofNullable(byRequest.get(Objects.requireNonNull(
                requestId, "requestId")));
    }

    public synchronized Optional<ItemInventoryTerminalTombstone>
    findTombstone(UUID requestId) {
        return Optional.ofNullable(tombstones.get(Objects.requireNonNull(
                requestId, "requestId")));
    }

    public synchronized ItemInventoryJournalCompactionResult
    preflightCompaction(ItemInventoryJournalCompaction compaction) {
        ItemInventoryJournalCompaction value = Objects.requireNonNull(
                compaction, "compaction");
        int existingTombstones = 0;
        long projectedBytes = entryBytes;
        for (ItemInventoryTerminalTombstone tombstone
                : value.tombstones()) {
            ItemInventoryTerminalTombstone existing = tombstones.get(
                    tombstone.requestId());
            if (existing != null) {
                if (!existing.equals(tombstone)) {
                    throw conflict(
                            "Item inventory compaction tombstone conflicts");
                }
                existingTombstones++;
                continue;
            }
            ItemInventoryJournalEntry entry = byRequest.get(
                    tombstone.requestId());
            if (!tombstone.matchesEntry(entry)) {
                throw conflict(
                        "Item inventory compaction evidence conflicts");
            }
            projectedBytes = Math.subtractExact(projectedBytes,
                    ItemInventoryJournalSnapshotCodec.encodedEntryBytes(
                            entry));
            projectedBytes = Math.addExact(projectedBytes,
                    ItemInventoryJournalSnapshotCodec
                            .encodedTombstoneBytes(tombstone));
        }
        if (existingTombstones != 0
                && existingTombstones != value.tombstones().size()) {
            throw conflict(
                    "Item inventory compaction is partially materialized");
        }
        if (existingTombstones == value.tombstones().size()) {
            return new ItemInventoryJournalCompactionResult(0, true);
        }
        requireRevisionCapacity();
        if (Math.addExact(tombstones.size(), value.tombstones().size())
                > MAX_TOMBSTONES || projectedBytes > MAX_ENTRY_BYTES) {
            throw conflict(
                    "Item inventory compaction capacity is exhausted");
        }
        return new ItemInventoryJournalCompactionResult(
                value.tombstones().size(), false);
    }

    public synchronized ItemInventoryJournalCompactionResult
    applyCompaction(ItemInventoryJournalCompaction compaction) {
        ItemInventoryJournalCompactionResult decision =
                preflightCompaction(compaction);
        if (decision.replayed()) {
            return decision;
        }
        for (ItemInventoryTerminalTombstone tombstone
                : compaction.tombstones()) {
            ItemInventoryJournalEntry removed = byRequest.remove(
                    tombstone.requestId());
            if (removed == null
                    || tombstones.put(tombstone.requestId(), tombstone)
                    != null) {
                throw new IllegalStateException(
                        "Item inventory compaction state is corrupt");
            }
            entryBytes = Math.subtractExact(entryBytes,
                    ItemInventoryJournalSnapshotCodec.encodedEntryBytes(
                            removed));
            entryBytes = Math.addExact(entryBytes,
                    ItemInventoryJournalSnapshotCodec
                            .encodedTombstoneBytes(tombstone));
        }
        revision = Math.addExact(revision, 1L);
        return decision;
    }

    public synchronized ItemInventoryQuarantineAdministrationResult
    preflightAdministration(
            ItemInventoryQuarantineAdministration administration
    ) {
        ItemInventoryQuarantineAdministration value = Objects.requireNonNull(
                administration, "administration");
        ItemInventoryQuarantineAdministration existing =
                administrationsByCommand.get(value.commandId());
        if (existing != null) {
            if (!existing.equals(value)) {
                throw conflict(
                        "Item inventory administration command conflicts");
            }
            return new ItemInventoryQuarantineAdministrationResult(
                    existing, true);
        }
        if (value.expectedJournalRevision() != revision) {
            throw conflict(
                    "Item inventory quarantine evidence is stale");
        }
        ItemInventoryJournalEntry target = byRequest.get(value.requestId());
        if (!value.matches(target)) {
            throw conflict(
                    "Item inventory quarantine evidence conflicts");
        }
        if (resolvedQuarantines.contains(value.requestId())) {
            throw conflict(
                    "Item inventory quarantine is already resolved");
        }
        requireRevisionCapacity();
        if (administrationsByCommand.size() >= MAX_ENTRIES) {
            throw conflict(
                    "Item inventory administration capacity is exhausted");
        }
        long projected = Math.addExact(entryBytes,
                ItemInventoryJournalSnapshotCodec
                        .encodedAdministrationBytes(value));
        if (projected > MAX_ENTRY_BYTES) {
            throw conflict(
                    "Item inventory journal storage capacity is exhausted");
        }
        return new ItemInventoryQuarantineAdministrationResult(
                value, false);
    }

    public synchronized ItemInventoryQuarantineAdministrationResult
    applyAdministration(
            ItemInventoryQuarantineAdministration administration
    ) {
        ItemInventoryQuarantineAdministrationResult decision =
                preflightAdministration(administration);
        if (decision.replayed()) {
            return decision;
        }
        ItemInventoryQuarantineAdministration value =
                decision.administration();
        administrationsByCommand.put(value.commandId(), value);
        administrationsByRequest.computeIfAbsent(value.requestId(),
                ignored -> new ArrayList<>()).add(value);
        if (value.action()
                != ItemInventoryQuarantineAdministrativeAction
                .KEEP_QUARANTINED) {
            resolvedQuarantines.add(value.requestId());
            refreshQuarantineState(value.playerId());
        }
        entryBytes = Math.addExact(entryBytes,
                ItemInventoryJournalSnapshotCodec
                        .encodedAdministrationBytes(value));
        revision = Math.addExact(revision, 1L);
        return decision;
    }

    public synchronized Optional<ItemInventoryQuarantineInspection>
    inspectQuarantine(UUID requestId) {
        UUID request = Objects.requireNonNull(requestId, "requestId");
        ItemInventoryJournalEntry entry = byRequest.get(request);
        if (entry == null
                || entry.status() != ItemInventoryJournalStatus.QUARANTINED) {
            return Optional.empty();
        }
        return Optional.of(new ItemInventoryQuarantineInspection(entry,
                resolvedQuarantines.contains(request),
                administrationsByRequest.getOrDefault(request,
                        List.of())));
    }

    public synchronized List<ItemInventoryJournalEntry> preparedForPlayer(
            UUID playerId,
            int limit
    ) {
        requireQueryLimit(limit);
        LinkedHashSet<UUID> requests = preparedByPlayer.get(
                Objects.requireNonNull(playerId, "playerId"));
        if (quarantinedPlayers.contains(playerId)
                || requests == null || requests.isEmpty()) {
            return List.of();
        }
        List<ItemInventoryJournalEntry> entries = new ArrayList<>(
                Math.min(limit, requests.size()));
        for (UUID requestId : requests) {
            ItemInventoryJournalEntry entry = byRequest.get(requestId);
            if (entry == null) {
                if (!tombstones.containsKey(requestId)) {
                    throw new IllegalStateException(
                            "Item inventory player index is corrupt");
                }
                continue;
            }
            entries.add(entry);
            if (entries.size() == limit) {
                break;
            }
        }
        return List.copyOf(entries);
    }

    public synchronized List<ItemInventoryJournalEntry> entriesForPlayer(
            UUID playerId,
            int limit
    ) {
        requireQueryLimit(limit);
        LinkedHashSet<UUID> requests = requestsByPlayer.get(
                Objects.requireNonNull(playerId, "playerId"));
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        List<ItemInventoryJournalEntry> entries = new ArrayList<>(
                Math.min(limit, requests.size()));
        for (UUID requestId : requests) {
            ItemInventoryJournalEntry entry = byRequest.get(requestId);
            if (entry == null) {
                if (!tombstones.containsKey(requestId)) {
                    throw new IllegalStateException(
                            "Item inventory player index is corrupt");
                }
                continue;
            }
            entries.add(entry);
            if (entries.size() == limit) {
                break;
            }
        }
        return List.copyOf(entries);
    }

    public synchronized boolean playerQuarantined(UUID playerId) {
        return quarantinedPlayers.contains(Objects.requireNonNull(
                playerId, "playerId"));
    }

    public synchronized boolean hasLaterRequestForPlayer(
            UUID playerId,
            UUID requestId
    ) {
        UUID player = Objects.requireNonNull(playerId, "playerId");
        UUID request = Objects.requireNonNull(requestId, "requestId");
        LinkedHashSet<UUID> requests = requestsByPlayer.get(player);
        if (requests == null) {
            return false;
        }
        boolean found = false;
        for (UUID indexed : requests) {
            if (found) {
                return true;
            }
            found = indexed.equals(request);
        }
        if (!found) {
            throw new IllegalArgumentException(
                    "Item inventory request does not belong to player");
        }
        return false;
    }

    public synchronized long revision() {
        return revision;
    }

    public synchronized ItemInventoryJournalSnapshot snapshot() {
        return new ItemInventoryJournalSnapshot(revision,
                List.copyOf(byRequest.values()),
                List.copyOf(administrationsByCommand.values()),
                List.copyOf(tombstones.values()));
    }

    public synchronized void rebuild(ItemInventoryJournalSnapshot snapshot) {
        ItemInventoryJournalSnapshot value = Objects.requireNonNull(
                snapshot, "snapshot");
        PersistentItemInventoryJournal rebuilt =
                new PersistentItemInventoryJournal();
        rebuilt.revision = value.revision();
        for (ItemInventoryJournalEntry entry : value.entries()) {
            rebuilt.restoreEntry(entry);
        }
        for (ItemInventoryQuarantineAdministration administration
                : value.administrations()) {
            rebuilt.restoreAdministration(administration);
        }
        for (ItemInventoryTerminalTombstone tombstone
                : value.tombstones()) {
            rebuilt.restoreTombstone(tombstone);
        }
        byRequest.clear();
        byRequest.putAll(rebuilt.byRequest);
        tombstones.clear();
        tombstones.putAll(rebuilt.tombstones);
        requestsByPlayer.clear();
        rebuilt.requestsByPlayer.forEach((playerId, requests) ->
                requestsByPlayer.put(playerId,
                        new LinkedHashSet<>(requests)));
        preparedByPlayer.clear();
        rebuilt.preparedByPlayer.forEach((playerId, requests) ->
                preparedByPlayer.put(playerId,
                        new LinkedHashSet<>(requests)));
        quarantinedPlayers.clear();
        quarantinedPlayers.addAll(rebuilt.quarantinedPlayers);
        administrationsByCommand.clear();
        administrationsByCommand.putAll(
                rebuilt.administrationsByCommand);
        administrationsByRequest.clear();
        rebuilt.administrationsByRequest.forEach((requestId, values) ->
                administrationsByRequest.put(requestId,
                        new ArrayList<>(values)));
        resolvedQuarantines.clear();
        resolvedQuarantines.addAll(rebuilt.resolvedQuarantines);
        revision = rebuilt.revision;
        entryBytes = rebuilt.entryBytes;
    }

    public synchronized boolean hasMaterializedState() {
        return revision != 0L || !byRequest.isEmpty()
                || !administrationsByCommand.isEmpty()
                || !tombstones.isEmpty();
    }

    private ItemInventoryJournalApplyResult decide(
            ItemInventoryJournalTransition transition
    ) {
        ItemInventoryJournalEntry existing = byRequest.get(
                transition.requestId());
        if (existing == null && tombstones.containsKey(
                transition.requestId())) {
            throw conflict(
                    "Item inventory request was compacted");
        }
        return switch (transition.type()) {
            case PREPARE -> decidePrepare(transition, existing);
            case COMMIT -> decideCommit(transition, existing);
            case ABORT -> decideAbort(transition, existing);
            case QUARANTINE -> decideQuarantine(transition, existing);
        };
    }

    private ItemInventoryJournalApplyResult decidePrepare(
            ItemInventoryJournalTransition transition,
            ItemInventoryJournalEntry existing
    ) {
        ItemInventoryMutationIntent intent = transition.intent()
                .orElseThrow();
        if (existing != null) {
            requireSameIntent(existing, intent);
            return new ItemInventoryJournalApplyResult(existing, true);
        }
        requireRevisionCapacity();
        requireNewRequestCapacity(intent.token().playerId());
        if (quarantinedPlayers.contains(intent.token().playerId())) {
            throw conflict(
                    "Player has a quarantined item inventory mutation");
        }
        LinkedHashSet<UUID> prepared = preparedByPlayer.get(
                intent.token().playerId());
        if (prepared != null && !prepared.isEmpty()) {
            throw conflict(
                    "Player already has a prepared item inventory mutation");
        }
        ItemInventoryJournalEntry result =
                ItemInventoryJournalEntry.prepared(intent);
        requireStorageCapacity(null, result);
        return new ItemInventoryJournalApplyResult(result, false);
    }

    private ItemInventoryJournalApplyResult decideCommit(
            ItemInventoryJournalTransition transition,
            ItemInventoryJournalEntry existing
    ) {
        ItemInventoryJournalEntry current = requireExisting(existing);
        ItemInventoryMutationReceipt receipt = transition.receipt()
                .orElseThrow();
        requireSameToken(current, receipt.token());
        if (!current.intent().plannedReceipt().equals(receipt)) {
            throw conflict(
                    "Item inventory commit differs from its prepared receipt");
        }
        if (current.status() == ItemInventoryJournalStatus.COMMITTED
                && current.committedReceipt().orElseThrow()
                .equals(receipt)
                || current.status()
                == ItemInventoryJournalStatus.QUARANTINED
                && current.committedReceipt().filter(receipt::equals)
                .isPresent()) {
            return new ItemInventoryJournalApplyResult(current, true);
        }
        if (current.status() != ItemInventoryJournalStatus.PREPARED) {
            throw conflict(
                    "Item inventory request cannot enter committed state");
        }
        requireRevisionCapacity();
        ItemInventoryJournalEntry result =
                ItemInventoryJournalEntry.committed(
                        current.intent(), receipt);
        requireStorageCapacity(current, result);
        return new ItemInventoryJournalApplyResult(result, false);
    }

    private ItemInventoryJournalApplyResult decideAbort(
            ItemInventoryJournalTransition transition,
            ItemInventoryJournalEntry existing
    ) {
        ItemInventoryJournalEntry current = requireExisting(existing);
        ItemInventoryMutationAbort abort = transition.abort()
                .orElseThrow();
        requireSameToken(current, abort.token());
        if (current.status() == ItemInventoryJournalStatus.ABORTED
                && current.abort().orElseThrow().equals(abort)) {
            return new ItemInventoryJournalApplyResult(current, true);
        }
        if (current.status() != ItemInventoryJournalStatus.PREPARED) {
            throw conflict(
                    "Item inventory request cannot enter aborted state");
        }
        requireRevisionCapacity();
        ItemInventoryJournalEntry result =
                ItemInventoryJournalEntry.aborted(current.intent(), abort);
        requireStorageCapacity(current, result);
        return new ItemInventoryJournalApplyResult(result, false);
    }

    private ItemInventoryJournalApplyResult decideQuarantine(
            ItemInventoryJournalTransition transition,
            ItemInventoryJournalEntry existing
    ) {
        ItemInventoryJournalEntry current = requireExisting(existing);
        ItemInventoryMutationQuarantine quarantine = transition.quarantine()
                .orElseThrow();
        requireSameToken(current, quarantine.token());
        if (current.status() == ItemInventoryJournalStatus.QUARANTINED
                && current.quarantine().orElseThrow().equals(quarantine)) {
            return new ItemInventoryJournalApplyResult(current, true);
        }
        if (current.status() != ItemInventoryJournalStatus.PREPARED
                && current.status()
                != ItemInventoryJournalStatus.COMMITTED) {
            throw conflict(
                    "Item inventory request cannot enter quarantine");
        }
        requireRevisionCapacity();
        ItemInventoryJournalEntry result =
                ItemInventoryJournalEntry.quarantined(
                        current, quarantine);
        requireStorageCapacity(current, result);
        return new ItemInventoryJournalApplyResult(result, false);
    }

    private void restoreEntry(ItemInventoryJournalEntry entry) {
        Objects.requireNonNull(entry, "entry");
        UUID requestId = entry.intent().token().requestId();
        UUID playerId = entry.intent().token().playerId();
        if (byRequest.putIfAbsent(requestId, entry) != null) {
            throw new IllegalArgumentException(
                    "Item inventory journal repeats a request");
        }
        LinkedHashSet<UUID> playerRequests = requestsByPlayer
                .computeIfAbsent(playerId,
                        ignored -> new LinkedHashSet<>());
        if (playerRequests.size() >= MAX_PLAYER_REQUESTS) {
            throw new IllegalArgumentException(
                    "Item inventory journal player index exceeds its limit");
        }
        playerRequests.add(requestId);
        if (entry.status() == ItemInventoryJournalStatus.PREPARED) {
            LinkedHashSet<UUID> prepared = preparedByPlayer
                    .computeIfAbsent(playerId,
                            ignored -> new LinkedHashSet<>());
            if (!prepared.isEmpty()
                    || prepared.size() >= MAX_PREPARED_PER_PLAYER) {
                throw new IllegalArgumentException(
                        "Item inventory journal prepared index is invalid");
            }
            prepared.add(requestId);
        }
        if (entry.status() == ItemInventoryJournalStatus.QUARANTINED) {
            quarantinedPlayers.add(playerId);
        }
        entryBytes = Math.addExact(entryBytes,
                ItemInventoryJournalSnapshotCodec.encodedEntryBytes(entry));
        if (entryBytes > MAX_ENTRY_BYTES) {
            throw new IllegalArgumentException(
                    "Item inventory journal snapshot exceeds its limit");
        }
    }

    private void restoreAdministration(
            ItemInventoryQuarantineAdministration administration
    ) {
        ItemInventoryQuarantineAdministration value = Objects.requireNonNull(
                administration, "administration");
        ItemInventoryJournalEntry target = byRequest.get(value.requestId());
        if (!value.matches(target)
                || administrationsByCommand.putIfAbsent(
                value.commandId(), value) != null) {
            throw new IllegalArgumentException(
                    "Item inventory administration snapshot is invalid");
        }
        administrationsByRequest.computeIfAbsent(value.requestId(),
                ignored -> new ArrayList<>()).add(value);
        if (value.action()
                != ItemInventoryQuarantineAdministrativeAction
                .KEEP_QUARANTINED) {
            if (!resolvedQuarantines.add(value.requestId())) {
                throw new IllegalArgumentException(
                        "Item inventory quarantine was resolved twice");
            }
            refreshQuarantineState(value.playerId());
        }
        entryBytes = Math.addExact(entryBytes,
                ItemInventoryJournalSnapshotCodec
                        .encodedAdministrationBytes(value));
        if (entryBytes > MAX_ENTRY_BYTES) {
            throw new IllegalArgumentException(
                    "Item inventory journal snapshot exceeds its limit");
        }
    }

    private void restoreTombstone(
            ItemInventoryTerminalTombstone tombstone
    ) {
        ItemInventoryTerminalTombstone value = Objects.requireNonNull(
                tombstone, "tombstone");
        UUID requestId = value.requestId();
        UUID playerId = value.token().playerId();
        if (byRequest.containsKey(requestId)
                || tombstones.putIfAbsent(requestId, value) != null) {
            throw new IllegalArgumentException(
                    "Item inventory tombstone repeats a request");
        }
        LinkedHashSet<UUID> requests = requestsByPlayer.computeIfAbsent(
                playerId, ignored -> new LinkedHashSet<>());
        if (requests.size() >= MAX_PLAYER_REQUESTS
                || !requests.add(requestId)) {
            throw new IllegalArgumentException(
                    "Item inventory tombstone player index is invalid");
        }
        entryBytes = Math.addExact(entryBytes,
                ItemInventoryJournalSnapshotCodec
                        .encodedTombstoneBytes(value));
        if (entryBytes > MAX_ENTRY_BYTES) {
            throw new IllegalArgumentException(
                    "Item inventory journal snapshot exceeds its limit");
        }
    }

    private void refreshQuarantineState(UUID playerId) {
        LinkedHashSet<UUID> requests = requestsByPlayer.get(playerId);
        boolean unresolved = requests != null && requests.stream()
                .map(byRequest::get)
                .filter(Objects::nonNull)
                .filter(entry -> entry.status()
                        == ItemInventoryJournalStatus.QUARANTINED)
                .anyMatch(entry -> !resolvedQuarantines.contains(
                        entry.intent().token().requestId()));
        if (unresolved) {
            quarantinedPlayers.add(playerId);
        } else {
            quarantinedPlayers.remove(playerId);
        }
    }

    private void requireNewRequestCapacity(UUID playerId) {
        if (byRequest.size() >= MAX_ENTRIES) {
            throw conflict("Item inventory request capacity is exhausted");
        }
        Set<UUID> requests = requestsByPlayer.get(playerId);
        if (requests != null && requests.size() >= MAX_PLAYER_REQUESTS) {
            throw conflict(
                    "Item inventory player request capacity is exhausted");
        }
    }

    private void requireStorageCapacity(
            ItemInventoryJournalEntry prior,
            ItemInventoryJournalEntry result
    ) {
        long projected = resultingEntryBytes(prior, result);
        if (projected > MAX_ENTRY_BYTES) {
            throw conflict(
                    "Item inventory journal storage capacity is exhausted");
        }
    }

    private long resultingEntryBytes(
            ItemInventoryJournalEntry prior,
            ItemInventoryJournalEntry result
    ) {
        long removed = prior == null ? 0L
                : ItemInventoryJournalSnapshotCodec.encodedEntryBytes(prior);
        long added = ItemInventoryJournalSnapshotCodec
                .encodedEntryBytes(result);
        return Math.addExact(Math.subtractExact(entryBytes, removed), added);
    }

    private void requireRevisionCapacity() {
        if (revision == Long.MAX_VALUE) {
            throw conflict("Item inventory journal revision is exhausted");
        }
    }

    private ItemInventoryJournalEntry requireExisting(
            ItemInventoryJournalEntry existing
    ) {
        if (existing == null) {
            throw conflict("Item inventory request is not prepared");
        }
        return existing;
    }

    private static void requireSameIntent(
            ItemInventoryJournalEntry existing,
            ItemInventoryMutationIntent intent
    ) {
        if (!existing.intent().equals(intent)) {
            throw conflict("Item inventory request identity conflicts");
        }
    }

    private static void requireSameToken(
            ItemInventoryJournalEntry existing,
            ItemInventoryMutationToken token
    ) {
        if (!existing.intent().token().equals(token)) {
            throw conflict("Item inventory transition identity conflicts");
        }
    }

    private void removePrepared(UUID playerId, UUID requestId) {
        LinkedHashSet<UUID> prepared = preparedByPlayer.get(playerId);
        if (prepared == null || !prepared.remove(requestId)) {
            throw new IllegalStateException(
                    "Item inventory prepared index is corrupt");
        }
        if (prepared.isEmpty()) {
            preparedByPlayer.remove(playerId);
        }
    }

    private static void requireQueryLimit(int limit) {
        if (limit <= 0 || limit > MAX_QUERY_RESULTS) {
            throw new IllegalArgumentException(
                    "Item inventory journal query limit is invalid");
        }
    }

    private static ItemInventoryJournalConflictException conflict(
            String message
    ) {
        return new ItemInventoryJournalConflictException(message);
    }
}
