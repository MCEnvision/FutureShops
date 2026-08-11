package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.catalog.offer.OfferAction;
import com.enviouse.futureshops.catalog.offer.OfferLimitPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerShopOfferReplayLedgerTest {
    private static final UUID PLAYER = UUID.fromString(
            "81000000-0000-0000-0000-000000000001");

    @TempDir
    Path temporaryDirectory;

    @Test
    void canonicalCodecRoundTripsAndRejectsCorruption() {
        ServerShopOfferReplayReceipt receipt = receipt(
                UUID.fromString(
                        "11000000-0000-0000-0000-000000000001"),
                "a".repeat(64));
        byte[] encoded =
                ServerShopOfferReplayReceiptCodec.encode(receipt);

        assertEquals(receipt,
                ServerShopOfferReplayReceiptCodec.decode(encoded));
        assertArrayEquals(encoded,
                ServerShopOfferReplayReceiptCodec.encode(
                        ServerShopOfferReplayReceiptCodec.decode(
                                encoded)));

        byte[] corrupt = encoded.clone();
        corrupt[12] ^= 0x40;
        assertThrows(IllegalArgumentException.class,
                () -> ServerShopOfferReplayReceiptCodec.decode(
                        corrupt));
        assertThrows(IllegalArgumentException.class,
                () -> ServerShopOfferReplayReceiptCodec.decode(
                        Arrays.copyOf(encoded, encoded.length - 1)));
        byte[] trailing = Arrays.copyOf(encoded, encoded.length + 1);
        assertThrows(IllegalArgumentException.class,
                () -> ServerShopOfferReplayReceiptCodec.decode(
                        trailing));
    }

    @Test
    void terminalFailureReceiptRoundTripsWithoutUsage() {
        UUID request = UUID.fromString(
                "12000000-0000-0000-0000-000000000001");
        ServerShopOfferReplayReceipt receipt =
                new ServerShopOfferReplayReceipt(
                        request,
                        ServerShopOfferReplayReceipt.Kind.CART,
                        "9".repeat(64),
                        ServerShopOfferService.Status.OUT_OF_STOCK,
                        List.of());

        byte[] encoded =
                ServerShopOfferReplayReceiptCodec.encode(receipt);
        ServerShopOfferReplayReceipt decoded =
                ServerShopOfferReplayReceiptCodec.decode(encoded);

        assertEquals(receipt, decoded);
        assertFalse(decoded.successful());
        assertTrue(decoded.usageEvidence().isEmpty());
    }

    @Test
    void everyDurableTerminalStatusHasStableCodec() {
        int ordinal = 1;
        for (ServerShopOfferService.Status status
                : ServerShopOfferService.Status.values()) {
            if (!ServerShopOfferReplayReceipt
                    .isDurableTerminalFailure(status)) {
                continue;
            }
            ServerShopOfferService.Request request = singleRequest(
                    new UUID(70L, ordinal++), 0);
            ServerShopOfferReplayReceipt receipt =
                    ServerShopOfferReplayReceipt.terminal(
                            request, status);
            byte[] encoded =
                    ServerShopOfferReplayReceiptCodec.encode(receipt);

            assertEquals(receipt,
                    ServerShopOfferReplayReceiptCodec.decode(encoded));
            assertTrue(receipt.usageEvidence().isEmpty());
        }
    }

    @Test
    void deterministicSingleFailuresReplayAfterStateChanges()
            throws Exception {
        Path root = temporaryDirectory.resolve("single_terminal");
        List<ServerShopOfferService.Status> statuses = List.of(
                ServerShopOfferService.Status.OUT_OF_STOCK,
                ServerShopOfferService.Status.STALE_REVISION,
                ServerShopOfferService.Status.NOT_AVAILABLE,
                ServerShopOfferService.Status.CANCELLED_BY_EVENT,
                ServerShopOfferService.Status.INVALID_REQUEST);
        try (ServerShopOfferReplayLedger ledger =
                     ServerShopOfferReplayLedger.open(root)) {
            for (int index = 0; index < statuses.size(); index++) {
                ServerShopOfferService.Request request =
                        singleRequest(new UUID(71L, index + 1L), 0);
                ServerShopOfferReplayReceipt terminal =
                        ServerShopOfferReplayReceipt.terminal(
                                request, statuses.get(index));
                assertTrue(terminal.usageEvidence().isEmpty());
                assertTrue(ledger.record(terminal));
            }
        }
        try (ServerShopOfferReplayLedger reopened =
                     ServerShopOfferReplayLedger.open(root)) {
            for (int index = 0; index < statuses.size(); index++) {
                ServerShopOfferService.Request changedStateReplay =
                        singleRequest(new UUID(71L, index + 1L), 91);
                ServerShopOfferReplayReceipt replayed = reopened.find(
                        changedStateReplay.requestId()).orElseThrow();
                assertTrue(replayed.matches(changedStateReplay));
                assertEquals(statuses.get(index), replayed.status());
                assertTrue(replayed.usageEvidence().isEmpty());
            }
        }
    }

    @Test
    void deterministicCartFailuresReplayAfterStateChanges()
            throws Exception {
        Path root = temporaryDirectory.resolve("cart_terminal");
        List<ServerShopOfferService.Status> statuses = List.of(
                ServerShopOfferService.Status.OUT_OF_STOCK,
                ServerShopOfferService.Status.STALE_REVISION,
                ServerShopOfferService.Status.NOT_AVAILABLE,
                ServerShopOfferService.Status.CANCELLED_BY_EVENT,
                ServerShopOfferService.Status.INVALID_REQUEST);
        try (ServerShopOfferReplayLedger ledger =
                     ServerShopOfferReplayLedger.open(root)) {
            for (int index = 0; index < statuses.size(); index++) {
                ServerShopOfferCartService.Request request =
                        cartRequest(new UUID(72L, index + 1L), 0);
                assertTrue(ledger.record(
                        ServerShopOfferReplayReceipt.terminal(
                                request, statuses.get(index))));
            }
        }
        try (ServerShopOfferReplayLedger reopened =
                     ServerShopOfferReplayLedger.open(root)) {
            for (int index = 0; index < statuses.size(); index++) {
                ServerShopOfferCartService.Request changedStateReplay =
                        cartRequest(new UUID(72L, index + 1L), 92);
                ServerShopOfferReplayReceipt replayed = reopened.find(
                        changedStateReplay.requestId()).orElseThrow();
                assertTrue(replayed.matches(changedStateReplay));
                assertEquals(statuses.get(index), replayed.status());
                assertTrue(replayed.usageEvidence().isEmpty());
            }
        }
    }

    @Test
    void terminalReplayRejectsConflictingUuidReuse() {
        Path root = temporaryDirectory.resolve("terminal_conflict");
        UUID requestId = new UUID(73L, 1L);
        ServerShopOfferService.Request original =
                singleRequest(requestId, 0);
        ServerShopOfferService.Request conflicting =
                new ServerShopOfferService.Request(
                        requestId, PLAYER, "default", "diamond",
                        "money", OfferAction.ACQUIRE_FROM_SHOP,
                        2, 100L, Optional.empty(), 0);
        try (ServerShopOfferReplayLedger ledger =
                     ServerShopOfferReplayLedger.open(root)) {
            ServerShopOfferReplayReceipt receipt =
                    ServerShopOfferReplayReceipt.terminal(
                            original,
                            ServerShopOfferService.Status.OUT_OF_STOCK);
            ledger.record(receipt);

            assertFalse(receipt.matches(conflicting));
            assertThrows(IllegalStateException.class, () ->
                    ledger.record(
                            ServerShopOfferReplayReceipt.terminal(
                                    conflicting,
                                    ServerShopOfferService.Status
                                            .STALE_REVISION)));
            assertEquals(receipt,
                    ledger.find(requestId).orElseThrow());
        }
    }

    @Test
    void transientFailuresCannotBecomeTerminalReceipts() {
        ServerShopOfferService.Request request =
                singleRequest(new UUID(74L, 1L), 0);

        for (ServerShopOfferService.Status status : List.of(
                ServerShopOfferService.Status.UNAVAILABLE,
                ServerShopOfferService.Status.RECOVERY_REQUIRED,
                ServerShopOfferService.Status.QUARANTINED,
                ServerShopOfferService.Status.CONFLICT)) {
            assertFalse(ServerShopOfferReplayReceipt
                    .isDurableTerminalFailure(status));
            assertThrows(IllegalArgumentException.class, () ->
                    ServerShopOfferReplayReceipt.terminal(
                            request, status));
        }
    }

    @Test
    void receiptPersistsAcrossRestartAndDiscoveryIsBounded()
            throws Exception {
        Path root = temporaryDirectory.resolve("ledger");
        ServerShopOfferReplayReceipt first = receipt(
                UUID.fromString(
                        "21000000-0000-0000-0000-000000000001"),
                "b".repeat(64));
        ServerShopOfferReplayReceipt second = receipt(
                UUID.fromString(
                        "22000000-0000-0000-0000-000000000002"),
                "c".repeat(64));
        try (ServerShopOfferReplayLedger ledger =
                     ServerShopOfferReplayLedger.open(root)) {
            assertTrue(ledger.record(first));
            assertTrue(ledger.record(second));
            assertFalse(ledger.record(first));
            ServerShopOfferReplayLedger.DiscoveryBatch batch =
                    ledger.readDiscoveryBatch(0L, 1);
            assertEquals(List.of(first), batch.receipts());
            assertFalse(batch.endOfIndex());
            assertEquals(1, batch.inspectedRecords());
            ServerShopOfferReplayLedger.DiscoveryBatch tail =
                    ledger.readDiscoveryBatch(
                            batch.nextByteOffset(), 1);
            assertEquals(List.of(second), tail.receipts());
            assertTrue(tail.endOfIndex());
        }
        try (ServerShopOfferReplayLedger reopened =
                     ServerShopOfferReplayLedger.open(root)) {
            assertEquals(first,
                    reopened.find(first.requestId()).orElseThrow());
            assertEquals(second,
                    reopened.find(second.requestId()).orElseThrow());
        }
    }

    @Test
    void conflictingIdentityFailsWithoutReplacingReceipt() {
        Path root = temporaryDirectory.resolve("conflict");
        UUID request = UUID.fromString(
                "31000000-0000-0000-0000-000000000001");
        ServerShopOfferReplayReceipt first =
                receipt(request, "d".repeat(64));
        ServerShopOfferReplayReceipt conflict =
                receipt(request, "e".repeat(64));
        try (ServerShopOfferReplayLedger ledger =
                     ServerShopOfferReplayLedger.open(root)) {
            ledger.record(first);
            assertThrows(IllegalStateException.class,
                    () -> ledger.record(conflict));
            assertEquals(first,
                    ledger.find(request).orElseThrow());
        }
    }

    @Test
    void corruptAndOversizedReceiptsFailClosed() throws Exception {
        Path root = temporaryDirectory.resolve("corrupt");
        UUID request = UUID.fromString(
                "41000000-0000-0000-0000-000000000001");
        try (ServerShopOfferReplayLedger ledger =
                     ServerShopOfferReplayLedger.open(root)) {
            ledger.record(receipt(request, "f".repeat(64)));
        }
        Path target = receiptPath(root, request);
        byte[] corrupt = Files.readAllBytes(target);
        corrupt[8] ^= 0x20;
        Files.write(target, corrupt,
                StandardOpenOption.TRUNCATE_EXISTING);
        try (ServerShopOfferReplayLedger ledger =
                     ServerShopOfferReplayLedger.open(root)) {
            assertThrows(IllegalArgumentException.class,
                    () -> ledger.find(request));
        }

        Files.write(target,
                new byte[ServerShopOfferReplayReceiptCodec
                        .MAXIMUM_BYTES + 1],
                StandardOpenOption.TRUNCATE_EXISTING);
        try (ServerShopOfferReplayLedger ledger =
                     ServerShopOfferReplayLedger.open(root)) {
            assertThrows(IllegalStateException.class,
                    () -> ledger.find(request));
        }
    }

    @Test
    void symlinkShardIsRejected() throws Exception {
        Path root = temporaryDirectory.resolve("symlink");
        try (ServerShopOfferReplayLedger ignored =
                     ServerShopOfferReplayLedger.open(root)) {
        }
        Path outside = temporaryDirectory.resolve("outside");
        Files.createDirectory(outside);
        Files.createSymbolicLink(root.resolve("aa"), outside);
        UUID request = UUID.fromString(
                "aa000000-0000-0000-0000-000000000001");

        try (ServerShopOfferReplayLedger ledger =
                     ServerShopOfferReplayLedger.open(root)) {
            assertThrows(IllegalStateException.class,
                    () -> ledger.find(request));
        }
    }

    @Test
    void truncatedIndexTailIsRemovedOnRestart() throws Exception {
        Path root = temporaryDirectory.resolve("tail");
        ServerShopOfferReplayReceipt receipt = receipt(
                UUID.fromString(
                        "51000000-0000-0000-0000-000000000001"),
                "1".repeat(64));
        long validSize;
        try (ServerShopOfferReplayLedger ledger =
                     ServerShopOfferReplayLedger.open(root)) {
            ledger.record(receipt);
            validSize = ledger.indexSize();
        }
        Files.write(root.resolve("index.wal"),
                new byte[]{1, 2, 3},
                StandardOpenOption.APPEND);

        try (ServerShopOfferReplayLedger ledger =
                     ServerShopOfferReplayLedger.open(root)) {
            assertEquals(validSize, ledger.indexSize());
            ServerShopOfferReplayLedger.DiscoveryBatch batch =
                    ledger.readDiscoveryBatch(0L, 10);
            assertEquals(List.of(receipt), batch.receipts());
            assertTrue(batch.endOfIndex());
        }
    }

    @Test
    void corruptDiscoveryRecordFailsClosed() throws Exception {
        Path root = temporaryDirectory.resolve("index_corrupt");
        ServerShopOfferReplayReceipt receipt = receipt(
                UUID.fromString(
                        "61000000-0000-0000-0000-000000000001"),
                "2".repeat(64));
        try (ServerShopOfferReplayLedger ledger =
                     ServerShopOfferReplayLedger.open(root)) {
            ledger.record(receipt);
        }
        Path index = root.resolve("index.wal");
        byte[] corrupt = Files.readAllBytes(index);
        corrupt[8] ^= 0x10;
        Files.write(index, corrupt,
                StandardOpenOption.TRUNCATE_EXISTING);

        try (ServerShopOfferReplayLedger ledger =
                     ServerShopOfferReplayLedger.open(root)) {
            assertThrows(IllegalStateException.class,
                    () -> ledger.readDiscoveryBatch(0L, 10));
        }
    }

    @Test
    void ledgerLockPreventsConcurrentWriters() {
        Path root = temporaryDirectory.resolve("locked");
        try (ServerShopOfferReplayLedger ledger =
                     ServerShopOfferReplayLedger.open(root)) {
            assertThrows(IllegalStateException.class,
                    () -> ServerShopOfferReplayLedger.open(root));
        }
    }

    private static ServerShopOfferReplayReceipt receipt(
            UUID requestId,
            String fingerprint
    ) {
        OfferLimitPolicy limits =
                new OfferLimitPolicy(64, 1_000L, 100L, 60L, 5L);
        return new ServerShopOfferReplayReceipt(
                requestId,
                ServerShopOfferReplayReceipt.Kind.SINGLE,
                fingerprint,
                ServerShopOfferService.Status.SUCCESS,
                List.of(new ServerShopOfferReplayReceipt.UsageEvidence(
                        requestId, PLAYER, "default", "iron",
                        "money", OfferAction.ACQUIRE_FROM_SHOP,
                        2, limits, limits, 0L, 100L)));
    }

    private static ServerShopOfferService.Request singleRequest(
            UUID requestId,
            int responseToken
    ) {
        return new ServerShopOfferService.Request(
                requestId, PLAYER, "default", "iron", "money",
                OfferAction.ACQUIRE_FROM_SHOP, 2, 100L,
                Optional.empty(), responseToken);
    }

    private static ServerShopOfferCartService.Request cartRequest(
            UUID requestId,
            int responseToken
    ) {
        return new ServerShopOfferCartService.Request(
                requestId, PLAYER, "default",
                List.of(new ServerShopOfferCartService.LineRequest(
                        "iron", "money", 2, 100L)),
                Optional.empty(), responseToken);
    }

    private static Path receiptPath(Path root, UUID requestId) {
        String compact = requestId.toString().replace("-", "");
        return root.resolve(compact.substring(0, 2))
                .resolve(compact.substring(2, 4))
                .resolve(requestId + ".receipt");
    }
}
