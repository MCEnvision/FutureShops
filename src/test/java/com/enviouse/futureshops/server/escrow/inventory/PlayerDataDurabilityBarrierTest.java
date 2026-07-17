package com.enviouse.futureshops.server.escrow.inventory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerDataDurabilityBarrierTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void nativeBarrierForcesTheFinalFileWithoutChangingIt()
            throws Exception {
        Path playerFile = temporaryDirectory.resolve("player.dat");
        byte[] contents = new byte[]{1, 3, 5, 7, 9};
        Files.write(playerFile, contents);

        PlayerDataDurabilityBarrier.DirectoryForceResult result =
                new PlayerDataDurabilityBarrier().force(playerFile);

        assertArrayEquals(contents, Files.readAllBytes(playerFile));
        assertTrue(result
                == PlayerDataDurabilityBarrier.DirectoryForceResult.FORCED
                || result == PlayerDataDurabilityBarrier.DirectoryForceResult
                .UNSUPPORTED_PLATFORM_BEST_EFFORT);
    }

    @Test
    void fileForceFailureStopsBeforeDirectoryForce() {
        AtomicBoolean directoryCalled = new AtomicBoolean();
        PlayerDataDurabilityBarrier barrier =
                new PlayerDataDurabilityBarrier(
                        ignored -> {
                            throw new IOException("file force failed");
                        },
                        ignored -> {
                            directoryCalled.set(true);
                            return PlayerDataDurabilityBarrier
                                    .DirectoryForceResult.FORCED;
                        });

        IOException failure = assertThrows(IOException.class,
                () -> barrier.force(
                        temporaryDirectory.resolve("player.dat")));

        assertEquals("file force failed", failure.getMessage());
        assertFalse(directoryCalled.get());
    }

    @Test
    void supportedDirectoryForceFailurePropagates() {
        AtomicBoolean fileCalled = new AtomicBoolean();
        PlayerDataDurabilityBarrier barrier =
                new PlayerDataDurabilityBarrier(
                        ignored -> fileCalled.set(true),
                        ignored -> {
                            throw new IOException(
                                    "directory force failed");
                        });

        IOException failure = assertThrows(IOException.class,
                () -> barrier.force(
                        temporaryDirectory.resolve("player.dat")));

        assertTrue(fileCalled.get());
        assertEquals("directory force failed", failure.getMessage());
    }

    @Test
    void unsupportedDirectoryResultIsAnExplicitBestEffortFallback()
            throws Exception {
        AtomicBoolean fileCalled = new AtomicBoolean();
        PlayerDataDurabilityBarrier barrier =
                new PlayerDataDurabilityBarrier(
                        ignored -> fileCalled.set(true),
                        ignored -> PlayerDataDurabilityBarrier
                                .DirectoryForceResult
                                .UNSUPPORTED_PLATFORM_BEST_EFFORT);

        PlayerDataDurabilityBarrier.DirectoryForceResult result =
                barrier.force(temporaryDirectory.resolve("player.dat"));

        assertTrue(fileCalled.get());
        assertEquals(PlayerDataDurabilityBarrier.DirectoryForceResult
                .UNSUPPORTED_PLATFORM_BEST_EFFORT, result);
    }

    @Test
    void unconfirmedReceiptRemainsBlockedForTheBarrierLifetime()
            throws Exception {
        UUID receiptId = UUID.randomUUID();
        PlayerDataDurabilityBarrier barrier =
                new PlayerDataDurabilityBarrier(
                        ignored -> {
                        },
                        ignored -> PlayerDataDurabilityBarrier
                                .DirectoryForceResult.FORCED);

        assertFalse(barrier.isUnconfirmed(receiptId));
        barrier.markUnconfirmed(receiptId);
        barrier.force(temporaryDirectory.resolve("player.dat"));

        assertTrue(barrier.isUnconfirmed(receiptId));
    }
}
