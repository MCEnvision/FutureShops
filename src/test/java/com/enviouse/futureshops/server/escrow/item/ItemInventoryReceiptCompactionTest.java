package com.enviouse.futureshops.server.escrow.item;

import com.enviouse.futureshops.MinecraftTestBootstrap;
import com.enviouse.futureshops.server.escrow.custody.CustodyAdapterInspectionStatus;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemInventoryReceiptCompactionTest {
    private static final int SNAPSHOT_MAGIC = 0x49525350;

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void checkpointProvenCompactionPreservesDurableEvidence() {
        ReceiptFixture first = fixture(Items.EMERALD, 8, 2,
                Instant.parse("2026-07-20T10:00:00Z"));
        ReceiptFixture second = fixture(Items.DIAMOND, 7, 3,
                Instant.parse("2026-07-20T10:00:01Z"));
        ItemInventoryReceiptRepository repository =
                new ItemInventoryReceiptRepository();
        repository.append(first.receipt());
        repository.append(second.receipt());

        assertEquals(1, repository.compactFullReceiptsWithVerifiedCheckpoint(
                proof(1L, first)));
        ItemInventoryReceiptSnapshot compacted = repository.snapshot();
        assertEquals(3L, compacted.revision());
        assertFalse(compacted.receipts().containsKey(
                first.token().requestId()));
        assertTrue(compacted.receipts().containsKey(
                second.token().requestId()));
        ItemInventoryReceiptTombstone tombstone = compacted.tombstones()
                .get(first.token().requestId());
        assertEquals(first.token().receiptId(), tombstone.receiptId());
        assertEquals(first.token().mutationId(), tombstone.mutationId());
        assertArrayEquals(first.token().digest(), tombstone.tokenDigest());
        assertArrayEquals(first.receipt().digest(),
                tombstone.receiptDigest());

        ItemInventoryReceiptInspection applied = repository.inspect(
                first.token(), first.plan().after());
        assertEquals(CustodyAdapterInspectionStatus.APPLIED,
                applied.status());
        assertInstanceOf(ItemInventoryReceiptTombstone.class,
                applied.evidence().orElseThrow());
        ItemInventoryReceiptInspection uncertain = repository.inspect(
                first.token(), first.before());
        assertEquals(CustodyAdapterInspectionStatus.UNKNOWN,
                uncertain.status());
        assertInstanceOf(ItemInventoryReceiptTombstone.class,
                uncertain.evidence().orElseThrow());
        assertTrue(repository.findFullReceipt(
                first.token().requestId()).isEmpty());
        assertInstanceOf(ItemInventoryReceiptTombstone.class,
                repository.findEvidence(first.token().requestId())
                        .orElseThrow());

        assertEquals(0, repository.compactFullReceiptsWithVerifiedCheckpoint(
                proof(1L, first)));
        assertEquals(compacted, repository.snapshot());

        ItemInventoryReceiptSnapshot restored =
                ItemInventoryReceiptSnapshotCodec.decode(
                        ItemInventoryReceiptSnapshotCodec.encode(compacted));
        assertEquals(compacted, restored);
        ItemInventoryReceiptRepository restoredRepository =
                new ItemInventoryReceiptRepository(restored);
        assertEquals(CustodyAdapterInspectionStatus.APPLIED,
                restoredRepository.inspect(first.token(),
                        first.plan().after()).status());
        assertInstanceOf(ItemInventoryReceiptTombstone.class,
                restoredRepository.findEvidence(first.token().requestId())
                        .orElseThrow());
    }

    @Test
    void exactTokenReplayIgnoresRegeneratedReceiptTimestamp() {
        ReceiptFixture fixture = fixture(Items.EMERALD, 9, 4,
                Instant.parse("2026-07-20T11:00:00Z"));
        ItemInventoryMutationReceipt regenerated =
                ItemInventoryMutationReceipt.create(fixture.token(),
                        fixture.plan(),
                        Instant.parse("2026-07-20T11:05:00Z"));
        ItemInventoryReceiptRepository repository =
                new ItemInventoryReceiptRepository();
        assertEquals(ItemInventoryReceiptAppendResult.APPLIED,
                repository.append(fixture.receipt()));
        assertEquals(ItemInventoryReceiptAppendResult.REPLAYED,
                repository.append(regenerated));
        assertEquals(fixture.receipt(), repository.findFullReceipt(
                fixture.token().requestId()).orElseThrow());

        repository.compactFullReceiptsWithVerifiedCheckpoint(
                proof(1L, fixture));
        assertEquals(ItemInventoryReceiptAppendResult.REPLAYED,
                repository.append(regenerated));
        assertInstanceOf(ItemInventoryReceiptTombstone.class,
                repository.findEvidence(fixture.token().requestId())
                        .orElseThrow());
    }

    @Test
    void onlyCallerProvenRequestIdsAreCompacted() {
        ReceiptFixture first = fixture(Items.EMERALD, 6, 1,
                Instant.parse("2026-07-20T12:00:00Z"));
        ReceiptFixture second = fixture(Items.DIAMOND, 6, 1,
                Instant.parse("2026-07-20T12:00:01Z"));
        ItemInventoryReceiptRepository repository =
                new ItemInventoryReceiptRepository();
        repository.append(first.receipt());
        repository.append(second.receipt());

        assertEquals(1, repository.compactFullReceiptsWithVerifiedCheckpoint(
                proof(1L, second)));
        assertTrue(repository.findFullReceipt(
                first.token().requestId()).isPresent());
        assertTrue(repository.findFullReceipt(
                second.token().requestId()).isEmpty());
    }

    @Test
    void versionOneDecodeMigratesToFullEvidenceWithoutCompaction() {
        ReceiptFixture fixture = fixture(Items.EMERALD, 5, 2,
                Instant.parse("2026-07-20T13:00:00Z"));
        byte[] versionOne = encodeVersionOne(1L,
                Map.of(fixture.token().requestId(), fixture.receipt()));
        ItemInventoryReceiptSnapshot decoded =
                ItemInventoryReceiptSnapshotCodec.decode(versionOne);

        assertEquals(Map.of(fixture.token().requestId(), fixture.receipt()),
                decoded.receipts());
        assertTrue(decoded.tombstones().isEmpty());
        ItemInventoryReceiptRepository repository =
                new ItemInventoryReceiptRepository(decoded);
        assertInstanceOf(ItemInventoryFullReceiptEvidence.class,
                repository.findEvidence(fixture.token().requestId())
                        .orElseThrow());
        assertTrue(repository.snapshot().tombstones().isEmpty());

        byte[] versionTwo = ItemInventoryReceiptSnapshotCodec.encode(
                repository.snapshot());
        assertEquals(2, ((versionTwo[4] & 0xff) << 8)
                | versionTwo[5] & 0xff);
        ItemInventoryReceiptSnapshot roundTrip =
                ItemInventoryReceiptSnapshotCodec.decode(versionTwo);
        assertTrue(roundTrip.tombstones().isEmpty());
        assertEquals(decoded.receipts(), roundTrip.receipts());
    }

    @Test
    void compactionRevisionOverflowLeavesRepositoryUnchanged() {
        ReceiptFixture fixture = fixture(Items.EMERALD, 5, 1,
                Instant.parse("2026-07-20T14:00:00Z"));
        ItemInventoryReceiptSnapshot initial =
                new ItemInventoryReceiptSnapshot(Long.MAX_VALUE,
                        Map.of(fixture.token().requestId(),
                                fixture.receipt()), Map.of());
        ItemInventoryReceiptRepository repository =
                new ItemInventoryReceiptRepository(initial);

        assertThrows(ArithmeticException.class,
                () -> repository.compactFullReceiptsWithVerifiedCheckpoint(
                        proof(1L, fixture)));
        assertEquals(initial, repository.snapshot());
    }

    @Test
    void compactionCapsFailWithoutPartialMutation() {
        ReceiptFixture first = fixture(Items.EMERALD, 5, 1,
                Instant.parse("2026-07-20T14:10:00Z"));
        ReceiptFixture second = fixture(Items.DIAMOND, 5, 1,
                Instant.parse("2026-07-20T14:10:01Z"));
        ItemInventoryReceiptTombstone firstTombstone =
                ItemInventoryReceiptTombstone.fromReceipt(first.receipt());
        ItemInventoryReceiptSnapshot initial =
                new ItemInventoryReceiptSnapshot(2L,
                        Map.of(second.token().requestId(), second.receipt()),
                        Map.of(first.token().requestId(), firstTombstone));
        ItemInventoryReceiptRepository tombstoneLimited =
                new ItemInventoryReceiptRepository(initial, 1,
                        ItemInventoryReceiptSnapshotCodec.MAX_ENCODED_BYTES);

        assertThrows(IllegalStateException.class,
                () -> tombstoneLimited
                        .compactFullReceiptsWithVerifiedCheckpoint(
                                proof(2L, second)));
        assertEquals(initial, tombstoneLimited.snapshot());

        ItemInventoryReceiptSnapshot empty =
                new ItemInventoryReceiptSnapshot(0L, Map.of(), Map.of());
        ItemInventoryReceiptRepository snapshotLimited =
                new ItemInventoryReceiptRepository(empty,
                        ItemInventoryReceiptRepository.MAX_TOMBSTONES, 100);
        assertThrows(IllegalArgumentException.class,
                () -> snapshotLimited.append(second.receipt()));
        assertEquals(empty, snapshotLimited.snapshot());
    }

    @Test
    void checkpointProofRejectsNonterminalIneligibleAndMismatchedEvidence() {
        ReceiptFixture fixture = fixture(Items.EMERALD, 5, 1,
                Instant.parse("2026-07-20T14:20:00Z"));
        ItemInventoryCheckpointRequestEvidence prepared = checkpointEvidence(
                fixture.token().requestId(), fixture.token().transactionId(),
                ItemInventoryCheckpointTerminalState.PREPARED, true);
        ItemInventoryCheckpointRequestEvidence ineligible =
                checkpointEvidence(fixture.token().requestId(),
                        fixture.token().transactionId(),
                        ItemInventoryCheckpointTerminalState.COMMITTED,
                        false);
        assertThrows(IllegalArgumentException.class,
                () -> ItemInventoryCheckpointCompactionProof.verified(0L,
                        ItemInventoryHashes.hashText("checkpoint"),
                        Map.of(fixture.token().requestId(), prepared)));
        assertThrows(IllegalArgumentException.class,
                () -> ItemInventoryCheckpointCompactionProof.verified(1L,
                        ItemInventoryHashes.hashText("checkpoint"),
                        Map.of(fixture.token().requestId(), prepared)));
        assertThrows(IllegalArgumentException.class,
                () -> ItemInventoryCheckpointCompactionProof.verified(1L,
                        ItemInventoryHashes.hashText("checkpoint"),
                        Map.of(fixture.token().requestId(), ineligible)));

        ItemInventoryReceiptRepository repository =
                new ItemInventoryReceiptRepository();
        repository.append(fixture.receipt());
        ItemInventoryReceiptSnapshot before = repository.snapshot();
        ItemInventoryCheckpointRequestEvidence mismatched =
                checkpointEvidence(fixture.token().requestId(),
                        UUID.randomUUID(),
                        ItemInventoryCheckpointTerminalState.COMMITTED,
                        true);
        ItemInventoryCheckpointCompactionProof wrongTransaction =
                ItemInventoryCheckpointCompactionProof.verified(1L,
                        ItemInventoryHashes.hashText("checkpoint"),
                        Map.of(fixture.token().requestId(), mismatched));
        assertThrows(IllegalStateException.class,
                () -> repository
                        .compactFullReceiptsWithVerifiedCheckpoint(
                                wrongTransaction));
        assertEquals(before, repository.snapshot());
    }

    @Test
    void architectureKeepsCompactionInternalAndAppendProjectionConstantTime()
            throws IOException {
        assertTrue(Arrays.stream(
                        ItemInventoryReceiptRepository.class
                                .getDeclaredMethods())
                .filter(method -> method.getName().contains("compact"))
                .noneMatch(method -> Modifier.isPublic(
                        method.getModifiers())));
        assertTrue(Arrays.stream(
                        ItemInventoryReceiptRepository.class
                                .getDeclaredMethods())
                .filter(method -> method.getName().contains("compact"))
                .noneMatch(method -> Arrays.asList(
                        method.getParameterTypes()).contains(Set.class)));

        String source = Files.readString(Path.of(
                "src/main/java/com/enviouse/futureshops/server/escrow/item/ItemInventoryReceiptRepository.java"));
        int appendStart = source.indexOf(
                "public synchronized ItemInventoryReceiptAppendResult append");
        int compactStart = source.indexOf(
                "synchronized int compactFullReceiptsWithVerifiedCheckpoint");
        String append = source.substring(appendStart, compactStart);
        assertFalse(append.contains("new HashMap<>(receipts)"));
        assertFalse(append.contains("receipts.values()"));
        assertTrue(append.contains("receipts.size(), 1"));
        assertFalse(source.substring(source.indexOf(
                        "public ItemInventoryReceiptRepository("), appendStart)
                .contains("compactFullReceipts"));
    }

    @Test
    void snapshotRejectsCrossKindIdentityConflicts() {
        ReceiptFixture first = fixture(Items.EMERALD, 5, 1,
                Instant.parse("2026-07-20T15:00:00Z"));
        ReceiptFixture second = fixture(Items.DIAMOND, 5, 1,
                Instant.parse("2026-07-20T15:00:01Z"));
        ItemInventoryReceiptTombstone conflicting =
                new ItemInventoryReceiptTombstone(
                        second.token().requestId(),
                        first.token().receiptId(),
                        first.token().mutationId(),
                        second.token().playerId(),
                        second.token().transactionId(),
                        second.token().digest(),
                        second.receipt().digest(),
                        second.receipt().appliedAt());

        assertThrows(IllegalArgumentException.class,
                () -> new ItemInventoryReceiptSnapshot(2L,
                        Map.of(first.token().requestId(), first.receipt()),
                        Map.of(second.token().requestId(), conflicting)));
    }

    @Test
    void versionTwoRejectsCorruptKindsIdsAndEntryCaps() {
        ReceiptFixture fixture = fixture(Items.EMERALD, 5, 1,
                Instant.parse("2026-07-20T16:00:00Z"));
        ItemInventoryReceiptRepository repository =
                new ItemInventoryReceiptRepository();
        repository.append(fixture.receipt());
        repository.compactFullReceiptsWithVerifiedCheckpoint(
                proof(1L, fixture));
        byte[] encoded = ItemInventoryReceiptSnapshotCodec.encode(
                repository.snapshot());

        byte[] invalidKind = resign(encoded, payload -> payload[18] = 99);
        assertThrows(IllegalArgumentException.class,
                () -> ItemInventoryReceiptSnapshotCodec.decode(invalidKind));
        byte[] zeroRequestId = resign(encoded, payload ->
                Arrays.fill(payload, 19, 35, (byte) 0));
        assertThrows(IllegalArgumentException.class,
                () -> ItemInventoryReceiptSnapshotCodec.decode(
                        zeroRequestId));

        int count = Math.addExact(ItemInventoryReceiptRepository.MAX_RECEIPTS,
                ItemInventoryReceiptRepository.MAX_TOMBSTONES) + 1;
        assertThrows(IllegalArgumentException.class,
                () -> ItemInventoryReceiptSnapshotCodec.decode(
                        snapshotHeaderOnly(count)));
    }

    private static ReceiptFixture fixture(
            net.minecraft.world.item.Item item,
            int initialCount,
            int extractedCount,
            Instant appliedAt
    ) {
        ItemInventoryState before = stateWith(
                new ItemStack(item, initialCount));
        ItemInventoryMutationPlan plan = ItemInventoryBatchPlanner.plan(
                before, List.of(ItemInventoryBatchEntry.extract(
                        UUID.randomUUID(), ItemInputMatcher.itemOnly(
                                net.minecraft.core.registries.BuiltInRegistries
                                        .ITEM.getKey(item).toString()),
                        extractedCount)));
        ItemInventoryMutationToken token =
                ItemInventoryMutationToken.create(UUID.randomUUID(),
                        UUID.randomUUID(), UUID.randomUUID(), plan);
        ItemInventoryMutationReceipt receipt =
                ItemInventoryMutationReceipt.create(token, plan, appliedAt);
        return new ReceiptFixture(before, plan, token, receipt);
    }

    private static ItemInventoryCheckpointCompactionProof proof(
            long generation,
            ReceiptFixture... fixtures
    ) {
        Map<UUID, ItemInventoryCheckpointRequestEvidence> evidence =
                new LinkedHashMap<>();
        for (ReceiptFixture fixture : fixtures) {
            evidence.put(fixture.token().requestId(),
                    checkpointEvidence(fixture.token().requestId(),
                            fixture.token().transactionId(),
                            ItemInventoryCheckpointTerminalState.COMMITTED,
                            true));
        }
        return ItemInventoryCheckpointCompactionProof.verified(generation,
                ItemInventoryHashes.hashText("checkpoint " + generation),
                evidence);
    }

    private static ItemInventoryCheckpointRequestEvidence checkpointEvidence(
            UUID requestId,
            UUID transactionId,
            ItemInventoryCheckpointTerminalState state,
            boolean eligible
    ) {
        return new ItemInventoryCheckpointRequestEvidence(requestId,
                transactionId, state, eligible,
                ItemInventoryHashes.hashText("terminal " + requestId));
    }

    private static ItemInventoryState stateWith(ItemStack first) {
        List<ItemStack> main = new ArrayList<>();
        main.add(first);
        while (main.size() < ItemInventorySlot.MAIN_SLOT_COUNT) {
            main.add(ItemStack.EMPTY);
        }
        return ItemInventoryState.of(main, ItemStack.EMPTY);
    }

    private static byte[] encodeVersionOne(
            long revision,
            Map<UUID, ItemInventoryMutationReceipt> receipts
    ) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(SNAPSHOT_MAGIC);
            output.writeShort(1);
            output.writeLong(revision);
            List<Map.Entry<UUID, ItemInventoryMutationReceipt>> ordered =
                    new ArrayList<>(new LinkedHashMap<>(receipts).entrySet());
            ordered.sort(Map.Entry.comparingByKey(
                    Comparator.comparing(UUID::toString)));
            output.writeInt(ordered.size());
            for (Map.Entry<UUID, ItemInventoryMutationReceipt> entry
                    : ordered) {
                ItemEscrowBinaryIo.writeUuid(output, entry.getKey());
                ItemEscrowBinaryIo.writeBytes(output,
                        ItemInventoryMutationReceiptCodec.encode(
                                entry.getValue()));
            }
            output.flush();
            return withDigest(bytes.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static byte[] snapshotHeaderOnly(int count) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(SNAPSHOT_MAGIC);
            output.writeShort(2);
            output.writeLong(count);
            output.writeInt(count);
            output.flush();
            return withDigest(bytes.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static byte[] resign(
            byte[] encoded,
            java.util.function.Consumer<byte[]> mutation
    ) {
        byte[] payload = Arrays.copyOf(encoded,
                encoded.length - ItemInventoryHashes.HASH_BYTES);
        mutation.accept(payload);
        return withDigest(payload);
    }

    private static byte[] withDigest(byte[] payload) {
        byte[] digest = ItemInventoryHashes.sha256(payload);
        byte[] encoded = Arrays.copyOf(payload,
                payload.length + digest.length);
        System.arraycopy(digest, 0, encoded, payload.length,
                digest.length);
        return encoded;
    }

    private record ReceiptFixture(
            ItemInventoryState before,
            ItemInventoryMutationPlan plan,
            ItemInventoryMutationToken token,
            ItemInventoryMutationReceipt receipt
    ) {
    }
}
