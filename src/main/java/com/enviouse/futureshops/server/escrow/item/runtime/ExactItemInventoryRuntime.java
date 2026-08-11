package com.enviouse.futureshops.server.escrow.item.runtime;

import com.enviouse.futureshops.server.escrow.item.ItemInventoryBatchEntry;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryBatchPlanner;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationPlan;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationReceipt;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationToken;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryPlanStatus;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryState;
import net.minecraft.world.item.ItemStack;

import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ExactItemInventoryRuntime {
    public static final int MAX_RECOVERY_BATCH = 256;
    private static final org.slf4j.Logger LOGGER =
            com.mojang.logging.LogUtils.getLogger();

    private final DurableItemInventoryMutationGateway gateway;
    private final ItemInventoryStackPolicy stackPolicy;
    private final Clock clock;
    private final PlayerItemMutationLocks locks =
            new PlayerItemMutationLocks();

    public ExactItemInventoryRuntime(
            DurableItemInventoryMutationGateway gateway
    ) {
        this(gateway, ItemInventoryStackPolicy.strictDefault(),
                Clock.systemUTC());
    }

    public ExactItemInventoryRuntime(
            DurableItemInventoryMutationGateway gateway,
            ItemInventoryStackPolicy stackPolicy,
            Clock clock
    ) {
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.stackPolicy = Objects.requireNonNull(
                stackPolicy, "stackPolicy");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public ItemInventoryExecutionResult execute(
            ItemInventoryAccess access,
            UUID transactionId,
            UUID requestId,
            List<ItemInventoryBatchEntry> entries
    ) {
        Objects.requireNonNull(access, "access");
        UUID playerId = requireUuid(access.playerId(), "playerId");
        UUID transaction = requireUuid(transactionId, "transactionId");
        UUID request = requireUuid(requestId, "requestId");
        List<ItemInventoryBatchEntry> requested = List.copyOf(
                Objects.requireNonNull(entries, "entries"));
        byte[] requestedFingerprint = ItemInventoryBatchPlanner.fingerprint(
                requested);
        return locks.withLock(playerId, () -> executeLocked(access,
                transaction, request, requested, requestedFingerprint));
    }

    public ItemInventoryExecutionResult recover(
            ItemInventoryAccess access,
            UUID requestId
    ) {
        Objects.requireNonNull(access, "access");
        UUID playerId = requireUuid(access.playerId(), "playerId");
        UUID request = requireUuid(requestId, "requestId");
        return locks.withLock(playerId, () -> {
            Optional<ItemInventoryJournalEntry> found = gateway.find(
                    request);
            if (found.isEmpty()) {
                ItemInventoryTerminalTombstone tombstone = gateway
                        .findTerminal(request).orElseThrow(() ->
                        new IllegalArgumentException(
                                "Item inventory recovery request is unknown"));
                requirePlayer(tombstone, playerId);
                return compactedResult(tombstone);
            }
            ItemInventoryJournalEntry entry = found.orElseThrow();
            requirePlayer(entry, playerId);
            return recoverLocked(access, entry, true);
        });
    }

    public List<ItemInventoryExecutionResult> recoverPrepared(
            ItemInventoryAccess access,
            int limit
    ) {
        Objects.requireNonNull(access, "access");
        if (limit <= 0 || limit > MAX_RECOVERY_BATCH) {
            throw new IllegalArgumentException(
                    "Item inventory recovery limit is invalid");
        }
        UUID playerId = requireUuid(access.playerId(), "playerId");
        return locks.withLock(playerId, () -> {
            List<ItemInventoryJournalEntry> prepared = List.copyOf(
                    gateway.preparedForPlayer(playerId, limit));
            if (prepared.size() > limit) {
                throw new IllegalStateException(
                        "Item inventory gateway exceeded its recovery limit");
            }
            List<ItemInventoryExecutionResult> results = new ArrayList<>(
                    prepared.size());
            for (ItemInventoryJournalEntry entry : prepared) {
                requirePlayer(entry, playerId);
                if (entry.status()
                        != ItemInventoryJournalStatus.PREPARED
                        && entry.status()
                        != ItemInventoryJournalStatus.QUARANTINED) {
                    throw new IllegalStateException(
                            "Item inventory gateway returned terminal recovery work");
                }
                results.add(recoverLocked(access, entry, true));
            }
            return List.copyOf(results);
        });
    }

    int trackedPlayerLocks() {
        return locks.trackedPlayers();
    }

    private ItemInventoryExecutionResult executeLocked(
            ItemInventoryAccess access,
            UUID transactionId,
            UUID requestId,
            List<ItemInventoryBatchEntry> entries,
            byte[] requestedFingerprint
    ) {
        Optional<ItemInventoryJournalEntry> existing = gateway.find(
                requestId);
        if (existing.isPresent()) {
            ItemInventoryJournalEntry entry = existing.orElseThrow();
            requireReplayIdentity(entry, access.playerId(), transactionId,
                    requestedFingerprint);
            return recoverLocked(access, entry, true);
        }
        Optional<ItemInventoryTerminalTombstone> compacted =
                gateway.findTerminal(requestId);
        if (compacted.isPresent()) {
            ItemInventoryTerminalTombstone tombstone =
                    compacted.orElseThrow();
            requireReplayIdentity(tombstone, access.playerId(),
                    transactionId, requestedFingerprint);
            return compactedResult(tombstone);
        }
        List<ItemInventoryJournalEntry> unresolved = List.copyOf(
                gateway.preparedForPlayer(access.playerId(), 1));
        if (!unresolved.isEmpty()) {
            requirePlayer(unresolved.get(0), access.playerId());
            throw new IllegalStateException(
                    "Player has an unresolved item inventory mutation");
        }
        ItemInventoryState before = access.capture();
        ItemInventoryMutationPlan plan = ItemInventoryBatchPlanner.plan(
                before, entries);
        if (!plan.applicable()) {
            return ItemInventoryExecutionResult.rejected(
                    plan.status() == ItemInventoryPlanStatus
                            .INSUFFICIENT_CAPACITY
                            ? ItemInventoryExecutionStatus
                            .INSUFFICIENT_CAPACITY
                            : ItemInventoryExecutionStatus
                            .INSUFFICIENT_ITEMS);
        }
        ItemInventoryMutationToken token =
                ItemInventoryMutationToken.create(access.playerId(),
                        transactionId, requestId, plan);
        ItemInventoryMutationReceipt receipt =
                ItemInventoryMutationReceipt.create(token, plan, now());
        ItemInventoryMutationIntent intent =
                ItemInventoryMutationIntent.create(token, plan, receipt);
        if (!supports(intent)) {
            return ItemInventoryExecutionResult.rejected(
                    ItemInventoryExecutionStatus.UNSUPPORTED_STACK);
        }
        ItemInventoryGatewayResult prepared = gateway
                .appendPreparedDurably(intent);
        requireGatewayIdentity(prepared.entry(), intent.token());
        if (!prepared.replayed()
                && !prepared.entry().intent().equals(intent)) {
            throw new IllegalStateException(
                    "Item inventory gateway changed a prepared intent");
        }
        if (!prepared.replayed()
                && prepared.entry().status()
                != ItemInventoryJournalStatus.PREPARED) {
            throw new IllegalStateException(
                    "Item inventory gateway did not prepare its intent");
        }
        if (prepared.replayed()) {
            requireReplayIdentity(prepared.entry(), access.playerId(),
                    transactionId, requestedFingerprint);
            return recoverLocked(access, prepared.entry(), true);
        }
        ItemInventoryState live = access.capture();
        if (!live.matchesInventoryHash(token.beforeInventoryHash())) {
            return abortWithoutWrites(prepared.entry(),
                    ItemInventoryAbortReason.PREIMAGE_CHANGED, false);
        }
        return applyAndCommit(access, prepared.entry(), false,
                ItemInventoryAbortReason.APPLY_FAILED_ROLLED_BACK);
    }

    private ItemInventoryExecutionResult recoverLocked(
            ItemInventoryAccess access,
            ItemInventoryJournalEntry entry,
            boolean replayed
    ) {
        requireGatewayIdentity(entry, entry.intent().token());
        if (gateway.playerQuarantined(access.playerId())
                && entry.status()
                != ItemInventoryJournalStatus.QUARANTINED) {
            return ItemInventoryExecutionResult.state(
                    ItemInventoryExecutionStatus.MANUAL_REVIEW,
                    entry.intent(), true);
        }
        if (entry.status() == ItemInventoryJournalStatus.ABORTED) {
            return ItemInventoryExecutionResult.state(
                    ItemInventoryExecutionStatus.ABORTED,
                    entry.intent(), replayed);
        }
        if (entry.status() == ItemInventoryJournalStatus.QUARANTINED) {
            return ItemInventoryExecutionResult.state(
                    ItemInventoryExecutionStatus.MANUAL_REVIEW,
                    entry.intent(), true);
        }
        if (!supports(entry.intent())) {
            return quarantine(entry,
                    ItemInventoryQuarantineReason.UNSUPPORTED_STACK,
                    replayed);
        }
        if (entry.status() == ItemInventoryJournalStatus.COMMITTED) {
            return recoverCommitted(access, entry);
        }
        SlotImage image = classify(access.capture(), entry.intent());
        if (image == SlotImage.AFTER) {
            return persistAndCommit(access, entry, replayed);
        }
        if (image == SlotImage.UNKNOWN) {
            return quarantine(entry,
                    ItemInventoryQuarantineReason.UNKNOWN_SLOT_IMAGE,
                    replayed);
        }
        return applyAndCommit(access, entry, replayed,
                ItemInventoryAbortReason.RECOVERY_FAILED_ROLLED_BACK);
    }

    private ItemInventoryExecutionResult applyAndCommit(
            ItemInventoryAccess access,
            ItemInventoryJournalEntry entry,
            boolean replayed,
            ItemInventoryAbortReason rollbackReason
    ) {
        ItemInventoryMutationIntent intent = entry.intent();
        if (entry.status() != ItemInventoryJournalStatus.PREPARED) {
            throw new IllegalStateException(
                    "Only a prepared item mutation can be applied");
        }
        SlotImage initial = classify(access.capture(), intent);
        if (initial == SlotImage.AFTER) {
            return persistAndCommit(access, entry, replayed);
        }
        if (initial == SlotImage.UNKNOWN) {
            return quarantine(entry,
                    ItemInventoryQuarantineReason.UNKNOWN_SLOT_IMAGE,
                    replayed);
        }
        try {
            writeAfter(access, intent);
            if (classify(access.capture(), intent) != SlotImage.AFTER) {
                throw new IllegalStateException(
                        "Item inventory postimage verification failed");
            }
        } catch (RuntimeException applyFailure) {
            if (!rollback(access, intent)) {
                return quarantine(entry,
                        ItemInventoryQuarantineReason.ROLLBACK_FAILED,
                        replayed);
            }
            if (!persistAndForce(access, intent, SlotImage.BEFORE)) {
                return ItemInventoryExecutionResult.state(
                        ItemInventoryExecutionStatus.RECOVERY_REQUIRED,
                        intent, replayed);
            }
            try {
                return abortWithoutWrites(entry, rollbackReason, replayed);
            } catch (RuntimeException abortFailure) {
                abortFailure.addSuppressed(applyFailure);
                return ItemInventoryExecutionResult.state(
                        ItemInventoryExecutionStatus.RECOVERY_REQUIRED,
                        intent, replayed);
            }
        }
        return persistAndCommit(access, entry, replayed);
    }

    private ItemInventoryExecutionResult persistAndCommit(
            ItemInventoryAccess access,
            ItemInventoryJournalEntry entry,
            boolean replayed
    ) {
        if (!persistAndForce(access, entry.intent(), SlotImage.AFTER)) {
            return ItemInventoryExecutionResult.state(
                    ItemInventoryExecutionStatus.RECOVERY_REQUIRED,
                    entry.intent(), replayed);
        }
        try {
            return commitApplied(entry, replayed);
        } catch (RuntimeException commitFailure) {
            LOGGER.error(
                    "Exact item inventory commit failed for request {}",
                    entry.intent().token().requestId(), commitFailure);
            return ItemInventoryExecutionResult.state(
                    ItemInventoryExecutionStatus.RECOVERY_REQUIRED,
                    entry.intent(), replayed);
        }
    }

    private ItemInventoryExecutionResult recoverCommitted(
            ItemInventoryAccess access,
            ItemInventoryJournalEntry entry
    ) {
        ItemInventoryMutationIntent intent = entry.intent();
        if (gateway.hasLaterRequestForPlayer(
                access.playerId(), intent.token().requestId())) {
            return ItemInventoryExecutionResult.replayed(
                    entry.committedReceipt().orElseThrow());
        }
        SlotImage image;
        try {
            image = classify(access.capture(), intent);
        } catch (RuntimeException exception) {
            return quarantine(entry,
                    ItemInventoryQuarantineReason.COMMITTED_REPAIR_FAILED,
                    true);
        }
        if (image == SlotImage.UNKNOWN) {
            return quarantine(entry,
                    ItemInventoryQuarantineReason.UNKNOWN_SLOT_IMAGE,
                    true);
        }
        if (image == SlotImage.AFTER) {
            return ItemInventoryExecutionResult.replayed(
                    entry.committedReceipt().orElseThrow());
        }
        try {
            writeAfter(access, intent);
        } catch (RuntimeException exception) {
            SlotImage failed;
            try {
                failed = classify(access.capture(), intent);
            } catch (RuntimeException inspectionFailure) {
                return quarantine(entry,
                        ItemInventoryQuarantineReason
                                .COMMITTED_REPAIR_FAILED, true);
            }
            return failed == SlotImage.UNKNOWN
                    ? quarantine(entry,
                    ItemInventoryQuarantineReason
                            .COMMITTED_REPAIR_FAILED, true)
                    : ItemInventoryExecutionResult.state(
                    ItemInventoryExecutionStatus.RECOVERY_REQUIRED,
                    intent, true);
        }
        if (classify(access.capture(), intent) != SlotImage.AFTER) {
            return quarantine(entry,
                    ItemInventoryQuarantineReason
                            .COMMITTED_REPAIR_FAILED, true);
        }
        if (!persistAndForce(access, intent, SlotImage.AFTER)) {
            return quarantine(entry,
                    ItemInventoryQuarantineReason.COMMITTED_REPAIR_FAILED,
                    true);
        }
        return ItemInventoryExecutionResult.replayed(
                entry.committedReceipt().orElseThrow());
    }

    private ItemInventoryExecutionResult quarantine(
            ItemInventoryJournalEntry entry,
            ItemInventoryQuarantineReason reason,
            boolean replayed
    ) {
        if (entry.status() == ItemInventoryJournalStatus.QUARANTINED) {
            return ItemInventoryExecutionResult.state(
                    ItemInventoryExecutionStatus.MANUAL_REVIEW,
                    entry.intent(), true);
        }
        try {
            ItemInventoryMutationQuarantine quarantine =
                    new ItemInventoryMutationQuarantine(
                            entry.intent().token(), reason, now());
            ItemInventoryGatewayResult result = gateway
                    .appendQuarantinedDurably(quarantine);
            requireGatewayIdentity(result.entry(), entry.intent().token());
            if (result.entry().status()
                    != ItemInventoryJournalStatus.QUARANTINED) {
                throw new IllegalStateException(
                        "Item inventory gateway did not quarantine its intent");
            }
            return ItemInventoryExecutionResult.state(
                    ItemInventoryExecutionStatus.MANUAL_REVIEW,
                    result.entry().intent(), replayed || result.replayed());
        } catch (RuntimeException exception) {
            return ItemInventoryExecutionResult.state(
                    ItemInventoryExecutionStatus.RECOVERY_REQUIRED,
                    entry.intent(), replayed);
        }
    }

    private ItemInventoryExecutionResult commitApplied(
            ItemInventoryJournalEntry entry,
            boolean replayed
    ) {
        if (entry.status() != ItemInventoryJournalStatus.PREPARED) {
            throw new IllegalStateException(
                    "Only a prepared item mutation can be committed");
        }
        ItemInventoryGatewayResult committed = gateway
                .appendCommittedDurably(entry.intent().plannedReceipt());
        requireGatewayIdentity(committed.entry(), entry.intent().token());
        if (committed.entry().status()
                != ItemInventoryJournalStatus.COMMITTED) {
            throw new IllegalStateException(
                    "Item inventory gateway did not commit its receipt");
        }
        ItemInventoryMutationReceipt receipt = committed.entry()
                .committedReceipt().orElseThrow();
        return replayed || committed.replayed()
                ? ItemInventoryExecutionResult.replayed(receipt)
                : ItemInventoryExecutionResult.applied(receipt);
    }

    private ItemInventoryExecutionResult abortWithoutWrites(
            ItemInventoryJournalEntry entry,
            ItemInventoryAbortReason reason,
            boolean replayed
    ) {
        ItemInventoryMutationAbort abort = new ItemInventoryMutationAbort(
                entry.intent().token(), reason, now());
        ItemInventoryGatewayResult aborted = gateway
                .appendAbortedDurably(abort);
        requireGatewayIdentity(aborted.entry(), entry.intent().token());
        if (aborted.entry().status()
                != ItemInventoryJournalStatus.ABORTED) {
            throw new IllegalStateException(
                    "Item inventory gateway did not abort its intent");
        }
        return ItemInventoryExecutionResult.state(
                ItemInventoryExecutionStatus.ABORTED,
                aborted.entry().intent(), replayed || aborted.replayed());
    }

    private static void writeAfter(
            ItemInventoryAccess access,
            ItemInventoryMutationIntent intent
    ) {
        for (ItemInventorySlotMutationEvidence evidence
                : intent.slotEvidence()) {
            access.write(evidence.slot(), evidence.afterStack());
        }
        access.flush();
    }

    private static boolean rollback(
            ItemInventoryAccess access,
            ItemInventoryMutationIntent intent
    ) {
        ItemInventoryState current;
        try {
            current = access.capture();
        } catch (RuntimeException exception) {
            return false;
        }
        if (classify(current, intent) == SlotImage.UNKNOWN) {
            return false;
        }
        try {
            for (ItemInventorySlotMutationEvidence evidence
                    : intent.slotEvidence()) {
                access.write(evidence.slot(), evidence.beforeStack());
            }
            access.flush();
            return classify(access.capture(), intent) == SlotImage.BEFORE;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean persistAndForce(
            ItemInventoryAccess access,
            ItemInventoryMutationIntent intent,
            SlotImage expected
    ) {
        try {
            access.savePlayerData();
            access.forcePlayerData();
            boolean matches = classify(access.capture(), intent) == expected;
            if (!matches) {
                LOGGER.error(
                        "Player item inventory durability verification failed for player {} and request {}",
                        access.playerId(), intent.token().requestId());
            }
            return matches;
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Player item inventory durability barrier failed for player {} and request {}",
                    access.playerId(), intent.token().requestId(), exception);
            return false;
        }
    }

    private boolean supports(ItemInventoryMutationIntent intent) {
        for (ItemInventorySlotMutationEvidence evidence
                : intent.slotEvidence()) {
            ItemStack before = evidence.beforeStack();
            ItemStack after = evidence.afterStack();
            if (!stackPolicy.supports(before, intent.token().direction())
                    || !stackPolicy.supports(after,
                    intent.token().direction())) {
                return false;
            }
        }
        return true;
    }

    private static SlotImage classify(
            ItemInventoryState state,
            ItemInventoryMutationIntent intent
    ) {
        boolean before = false;
        boolean after = false;
        for (ItemInventorySlotMutationEvidence evidence
                : intent.slotEvidence()) {
            ItemStack current = state.stack(evidence.slot());
            if (evidence.matchesBefore(current)) {
                before = true;
            } else if (evidence.matchesAfter(current)) {
                after = true;
            } else {
                return SlotImage.UNKNOWN;
            }
        }
        if (before && after) {
            return SlotImage.MIXED;
        }
        return after ? SlotImage.AFTER : SlotImage.BEFORE;
    }

    private static void requireReplayIdentity(
            ItemInventoryJournalEntry entry,
            UUID playerId,
            UUID transactionId,
            byte[] requestedFingerprint
    ) {
        ItemInventoryMutationToken token = entry.intent().token();
        if (!token.playerId().equals(playerId)
                || !token.transactionId().equals(transactionId)
                || !MessageDigest.isEqual(token.batchFingerprint(),
                requestedFingerprint)) {
            throw new IllegalStateException(
                    "Item inventory request identity conflicts");
        }
    }

    private static void requirePlayer(
            ItemInventoryJournalEntry entry,
            UUID playerId
    ) {
        if (!entry.intent().token().playerId().equals(playerId)) {
            throw new IllegalStateException(
                    "Item inventory recovery player conflicts");
        }
    }

    private static void requirePlayer(
            ItemInventoryTerminalTombstone tombstone,
            UUID playerId
    ) {
        if (!tombstone.token().playerId().equals(playerId)) {
            throw new IllegalStateException(
                    "Item inventory recovery player conflicts");
        }
    }

    private static void requireReplayIdentity(
            ItemInventoryTerminalTombstone tombstone,
            UUID playerId,
            UUID transactionId,
            byte[] requestedFingerprint
    ) {
        var token = tombstone.token();
        if (!token.playerId().equals(playerId)
                || !token.transactionId().equals(transactionId)
                || !MessageDigest.isEqual(token.batchFingerprint(),
                requestedFingerprint)) {
            throw new IllegalStateException(
                    "Item inventory compacted request identity conflicts");
        }
    }

    private static ItemInventoryExecutionResult compactedResult(
            ItemInventoryTerminalTombstone tombstone
    ) {
        return tombstone.status() == ItemInventoryJournalStatus.COMMITTED
                ? ItemInventoryExecutionResult.compactedReplay(
                tombstone.token())
                : ItemInventoryExecutionResult.compactedAbort(
                tombstone.token());
    }

    private static void requireGatewayIdentity(
            ItemInventoryJournalEntry entry,
            ItemInventoryMutationToken token
    ) {
        if (!entry.intent().token().equals(token)) {
            throw new IllegalStateException(
                    "Item inventory gateway identity conflicts");
        }
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    private static UUID requireUuid(UUID value, String name) {
        UUID result = Objects.requireNonNull(value, name);
        if (result.getMostSignificantBits() == 0L
                && result.getLeastSignificantBits() == 0L) {
            throw new IllegalArgumentException(name + " cannot be zero");
        }
        return result;
    }

    private enum SlotImage {
        BEFORE,
        AFTER,
        MIXED,
        UNKNOWN
    }
}
