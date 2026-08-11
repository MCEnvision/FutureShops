package com.enviouse.futureshops.server.escrow.item.runtime;

import com.enviouse.futureshops.MinecraftTestBootstrap;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersistentItemInventoryJournalTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void strictLifecycleMaintainsRequestAndPlayerIndexes() {
        PersistentItemInventoryJournal journal =
                new PersistentItemInventoryJournal();
        UUID playerId = UUID.randomUUID();
        ItemInventoryMutationIntent first = intent(playerId,
                UUID.randomUUID());
        ItemInventoryJournalTransition prepared =
                ItemInventoryJournalTransition.prepare(first);

        assertFalse(journal.preflightCommitted(prepared).replayed());
        assertEquals(0L, journal.revision());
        assertFalse(journal.applyCommitted(prepared).replayed());
        assertTrue(journal.preflightCommitted(prepared).replayed());
        assertEquals(List.of(ItemInventoryJournalEntry.prepared(first)),
                journal.preparedForPlayer(playerId, 1));
        assertEquals(1, journal.entriesForPlayer(playerId, 1).size());

        assertFalse(journal.applyCommitted(
                ItemInventoryJournalTransition.commit(
                        first.plannedReceipt())).replayed());
        assertTrue(journal.preparedForPlayer(playerId, 1).isEmpty());
        assertTrue(journal.applyCommitted(prepared).replayed());
        assertTrue(journal.applyCommitted(
                ItemInventoryJournalTransition.commit(
                        first.plannedReceipt())).replayed());

        ItemInventoryMutationQuarantine quarantine =
                new ItemInventoryMutationQuarantine(first.token(),
                        ItemInventoryQuarantineReason
                                .COMMITTED_REPAIR_FAILED,
                        ItemInventoryJournalTestFixtures.NOW);
        assertFalse(journal.applyCommitted(
                ItemInventoryJournalTransition.quarantine(
                        quarantine)).replayed());
        assertTrue(journal.playerQuarantined(playerId));
        assertTrue(journal.preflightCommitted(
                ItemInventoryJournalTransition.commit(
                        first.plannedReceipt())).replayed());
        assertTrue(journal.find(first.token().requestId()).orElseThrow()
                .committedReceipt().isPresent());

        ItemInventoryMutationIntent blocked = intent(playerId,
                UUID.randomUUID());
        assertThrows(ItemInventoryJournalConflictException.class,
                () -> journal.preflightCommitted(
                        ItemInventoryJournalTransition.prepare(blocked)));
    }

    @Test
    void conflictsFailBeforeMutationAndOpposingTerminalsNeverReplaceState() {
        PersistentItemInventoryJournal journal =
                new PersistentItemInventoryJournal();
        UUID playerId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        ItemInventoryMutationIntent intent =
                ItemInventoryJournalTestFixtures.intent(playerId,
                        UUID.randomUUID(), requestId);
        journal.applyCommitted(ItemInventoryJournalTransition.prepare(
                intent));
        long revision = journal.revision();

        ItemInventoryMutationIntent conflicting =
                ItemInventoryJournalTestFixtures.intent(UUID.randomUUID(),
                        UUID.randomUUID(), requestId);
        assertThrows(ItemInventoryJournalConflictException.class,
                () -> journal.preflightCommitted(
                        ItemInventoryJournalTransition.prepare(conflicting)));
        assertEquals(revision, journal.revision());

        ItemInventoryMutationAbort abort = new ItemInventoryMutationAbort(
                intent.token(), ItemInventoryAbortReason.CALLER_CANCELLED,
                ItemInventoryJournalTestFixtures.NOW);
        journal.applyCommitted(ItemInventoryJournalTransition.abort(abort));
        assertThrows(ItemInventoryJournalConflictException.class,
                () -> journal.preflightCommitted(
                        ItemInventoryJournalTransition.commit(
                                intent.plannedReceipt())));
        assertThrows(ItemInventoryJournalConflictException.class,
                () -> journal.preflightCommitted(
                        ItemInventoryJournalTransition.quarantine(
                                new ItemInventoryMutationQuarantine(
                                        intent.token(),
                                        ItemInventoryQuarantineReason
                                                .UNKNOWN_SLOT_IMAGE,
                                        ItemInventoryJournalTestFixtures
                                                .NOW))));
        assertEquals(ItemInventoryJournalStatus.ABORTED,
                journal.find(requestId).orElseThrow().status());
    }

    @Test
    void unresolvedMutationExcludesAnotherRequestForTheSamePlayer() {
        PersistentItemInventoryJournal journal =
                new PersistentItemInventoryJournal();
        UUID playerId = UUID.randomUUID();
        journal.applyCommitted(ItemInventoryJournalTransition.prepare(
                intent(playerId, UUID.randomUUID())));

        assertThrows(ItemInventoryJournalConflictException.class,
                () -> journal.preflightCommitted(
                        ItemInventoryJournalTransition.prepare(
                                intent(playerId, UUID.randomUUID()))));
        assertThrows(IllegalArgumentException.class,
                () -> journal.preparedForPlayer(playerId, 0));
        assertThrows(IllegalArgumentException.class,
                () -> journal.preparedForPlayer(playerId,
                        PersistentItemInventoryJournal.MAX_QUERY_RESULTS
                                + 1));
    }

    @Test
    void quarantineHidesExistingPreparedWorkAndSurvivesIndexRebuild() {
        PersistentItemInventoryJournal journal =
                new PersistentItemInventoryJournal();
        UUID playerId = UUID.randomUUID();
        ItemInventoryMutationIntent older = intent(playerId,
                UUID.randomUUID());
        journal.applyCommitted(ItemInventoryJournalTransition.prepare(
                older));
        journal.applyCommitted(ItemInventoryJournalTransition.commit(
                older.plannedReceipt()));
        ItemInventoryMutationIntent pending = intent(playerId,
                UUID.randomUUID());
        journal.applyCommitted(ItemInventoryJournalTransition.prepare(
                pending));
        journal.applyCommitted(ItemInventoryJournalTransition.quarantine(
                new ItemInventoryMutationQuarantine(older.token(),
                        ItemInventoryQuarantineReason
                                .COMMITTED_REPAIR_FAILED,
                        ItemInventoryJournalTestFixtures.NOW)));

        assertTrue(journal.preparedForPlayer(playerId, 1).isEmpty());
        assertEquals(2, journal.entriesForPlayer(playerId, 2).size());
        assertTrue(journal.hasLaterRequestForPlayer(playerId,
                older.token().requestId()));
        assertFalse(journal.hasLaterRequestForPlayer(playerId,
                pending.token().requestId()));

        PersistentItemInventoryJournal rebuilt =
                new PersistentItemInventoryJournal();
        rebuilt.rebuild(ItemInventoryJournalSnapshotCodec.decode(
                ItemInventoryJournalSnapshotCodec.encode(
                        journal.snapshot())));
        assertTrue(rebuilt.playerQuarantined(playerId));
        assertTrue(rebuilt.preparedForPlayer(playerId, 1).isEmpty());
        assertEquals(ItemInventoryJournalStatus.PREPARED,
                rebuilt.find(pending.token().requestId()).orElseThrow()
                        .status());
    }

    @Test
    void snapshotAndSavedDataRoundTripRebuildEveryIndex() {
        ItemInventoryJournalSavedData saved =
                new ItemInventoryJournalSavedData();
        UUID committedPlayer = UUID.randomUUID();
        ItemInventoryMutationIntent committed = intent(committedPlayer,
                UUID.randomUUID());
        saved.applyCommitted(ItemInventoryJournalTransition.prepare(
                committed));
        saved.applyCommitted(ItemInventoryJournalTransition.commit(
                committed.plannedReceipt()));
        UUID preparedPlayer = UUID.randomUUID();
        ItemInventoryMutationIntent prepared = intent(preparedPlayer,
                UUID.randomUUID());
        saved.applyCommitted(ItemInventoryJournalTransition.prepare(
                prepared));

        byte[] encoded = ItemInventoryJournalSnapshotCodec.encode(
                saved.snapshot());
        ItemInventoryJournalSnapshot decoded =
                ItemInventoryJournalSnapshotCodec.decode(encoded);
        assertEquals(saved.snapshot(), decoded);

        ItemInventoryJournalSavedData loaded =
                ItemInventoryJournalSavedData.load(
                        saved.save(new CompoundTag()));
        assertEquals(saved.snapshot(), loaded.snapshot());
        assertEquals(1, loaded.preparedForPlayer(preparedPlayer, 1).size());
        assertEquals(1, loaded.entriesForPlayer(committedPlayer, 1).size());
        assertTrue(loaded.hasMaterializedState());
    }

    @Test
    void quarantineAdministrationRetainsEvidenceAndOnlyReleaseUnblocks() {
        PersistentItemInventoryJournal journal =
                new PersistentItemInventoryJournal();
        UUID playerId = UUID.randomUUID();
        ItemInventoryMutationIntent intent = intent(playerId,
                UUID.randomUUID());
        journal.applyCommitted(ItemInventoryJournalTransition.prepare(
                intent));
        journal.applyCommitted(ItemInventoryJournalTransition.commit(
                intent.plannedReceipt()));
        ItemInventoryMutationQuarantine quarantine =
                new ItemInventoryMutationQuarantine(intent.token(),
                        ItemInventoryQuarantineReason
                                .COMMITTED_REPAIR_FAILED,
                        ItemInventoryJournalTestFixtures.NOW);
        journal.applyCommitted(ItemInventoryJournalTransition.quarantine(
                quarantine));

        ItemInventoryQuarantineAdministration keep =
                new ItemInventoryQuarantineAdministration(
                        UUID.randomUUID(), intent.token().requestId(),
                        playerId, UUID.randomUUID(),
                        ItemInventoryQuarantineAdministrativeAction
                                .KEEP_QUARANTINED,
                        journal.revision(),
                        ItemInventoryQuarantineAdministration
                                .quarantineDigest(quarantine),
                        Optional.empty(), "Keep for investigation",
                        ItemInventoryJournalTestFixtures.NOW);
        assertFalse(journal.applyAdministration(keep).replayed());
        assertTrue(journal.playerQuarantined(playerId));
        assertFalse(journal.inspectQuarantine(intent.token().requestId())
                .orElseThrow().resolved());

        ItemInventoryQuarantineAdministration release =
                new ItemInventoryQuarantineAdministration(
                        UUID.randomUUID(), intent.token().requestId(),
                        playerId, UUID.randomUUID(),
                        ItemInventoryQuarantineAdministrativeAction.RELEASE,
                        journal.revision(),
                        ItemInventoryQuarantineAdministration
                                .quarantineDigest(quarantine),
                        Optional.empty(), "Evidence reviewed",
                        ItemInventoryJournalTestFixtures.NOW.plusSeconds(1));
        assertFalse(journal.applyAdministration(release).replayed());
        assertTrue(journal.applyAdministration(release).replayed());
        assertFalse(journal.playerQuarantined(playerId));
        ItemInventoryQuarantineInspection inspection =
                journal.inspectQuarantine(intent.token().requestId())
                        .orElseThrow();
        assertTrue(inspection.resolved());
        assertEquals(List.of(keep, release), inspection.reviews());
        assertEquals(release,
                ItemInventoryQuarantineAdministrationCodec.decode(
                        ItemInventoryQuarantineAdministrationCodec.encode(
                                release)));

        PersistentItemInventoryJournal rebuilt =
                new PersistentItemInventoryJournal();
        rebuilt.rebuild(ItemInventoryJournalSnapshotCodec.decode(
                ItemInventoryJournalSnapshotCodec.encode(
                        journal.snapshot())));
        assertFalse(rebuilt.playerQuarantined(playerId));
        assertTrue(rebuilt.inspectQuarantine(intent.token().requestId())
                .orElseThrow().resolved());
    }

    @Test
    void compactionOnlyReplacesExactTerminalEvidenceWithReplayTombstones() {
        PersistentItemInventoryJournal journal =
                new PersistentItemInventoryJournal();
        UUID playerId = UUID.randomUUID();
        ItemInventoryMutationIntent intent = intent(playerId,
                UUID.randomUUID());
        journal.applyCommitted(ItemInventoryJournalTransition.prepare(
                intent));
        journal.applyCommitted(ItemInventoryJournalTransition.commit(
                intent.plannedReceipt()));
        ItemInventoryJournalEntry terminal = journal.find(
                intent.token().requestId()).orElseThrow();
        UUID commandId = UUID.randomUUID();
        UUID checkpointId = UUID.randomUUID();
        ItemInventoryTerminalTombstone tombstone =
                ItemInventoryTerminalTombstone.fromEntry(terminal,
                        commandId, checkpointId);
        ItemInventoryJournalCompaction compaction =
                new ItemInventoryJournalCompaction(commandId, checkpointId,
                        UUID.randomUUID(), UUID.randomUUID(), 17L,
                        new byte[32], List.of(tombstone));

        assertFalse(journal.preflightCompaction(compaction).replayed());
        assertFalse(journal.applyCompaction(compaction).replayed());
        assertTrue(journal.applyCompaction(compaction).replayed());
        assertTrue(journal.find(intent.token().requestId()).isEmpty());
        assertEquals(tombstone, journal.findTombstone(
                intent.token().requestId()).orElseThrow());
        assertTrue(journal.entriesForPlayer(playerId, 1).isEmpty());
        assertThrows(ItemInventoryJournalConflictException.class,
                () -> journal.preflightCommitted(
                        ItemInventoryJournalTransition.prepare(intent)));
        assertEquals(compaction,
                ItemInventoryJournalCompactionCodec.decode(
                        ItemInventoryJournalCompactionCodec.encode(
                                compaction)));

        PersistentItemInventoryJournal rebuilt =
                new PersistentItemInventoryJournal();
        rebuilt.rebuild(ItemInventoryJournalSnapshotCodec.decode(
                ItemInventoryJournalSnapshotCodec.encode(
                        journal.snapshot())));
        assertEquals(tombstone, rebuilt.findTombstone(
                intent.token().requestId()).orElseThrow());
        assertTrue(rebuilt.entriesForPlayer(playerId, 1).isEmpty());
    }

    @Test
    void malformedAndOversizedSnapshotsFailClosed() {
        ItemInventoryMutationIntent intent = intent(UUID.randomUUID(),
                UUID.randomUUID());
        ItemInventoryJournalEntry entry =
                ItemInventoryJournalEntry.prepared(intent);

        assertThrows(IllegalArgumentException.class,
                () -> new ItemInventoryJournalSnapshot(0L,
                        List.of(entry)));
        assertThrows(IllegalArgumentException.class,
                () -> new ItemInventoryJournalSnapshot(
                        PersistentItemInventoryJournal.MAX_ENTRIES + 1L,
                        Collections.nCopies(
                                PersistentItemInventoryJournal.MAX_ENTRIES
                                        + 1, entry)));

        byte[] encoded = ItemInventoryJournalSnapshotCodec.encode(
                new ItemInventoryJournalSnapshot(1L, List.of(entry)));
        encoded[encoded.length / 2] ^= 1;
        assertThrows(IllegalArgumentException.class,
                () -> ItemInventoryJournalSnapshotCodec.decode(encoded));

        CompoundTag wrongSchema = new CompoundTag();
        wrongSchema.putString("schemaVersion", "bad");
        assertThrows(IllegalStateException.class,
                () -> ItemInventoryJournalSavedData.load(wrongSchema));
        CompoundTag missingSnapshot = new CompoundTag();
        missingSnapshot.putInt("schemaVersion",
                ItemInventoryJournalSavedData.CURRENT_VERSION);
        assertThrows(IllegalStateException.class,
                () -> ItemInventoryJournalSavedData.load(missingSnapshot));
    }

    private static ItemInventoryMutationIntent intent(
            UUID playerId,
            UUID requestId
    ) {
        return ItemInventoryJournalTestFixtures.intent(playerId,
                UUID.randomUUID(), requestId);
    }
}
