package com.enviouse.futureshops.server.escrow.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerShopOfferReplayLedgerDurabilityTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void windowsDirectoryChannelDenialUsesBestEffortFallback()
            throws Exception {
        ServerShopOfferReplayLedger.DirectoryForceResult result =
                ServerShopOfferReplayLedger.forceDirectory(
                        temporaryDirectory, true, ignored -> {
                            throw new AccessDeniedException(
                                    temporaryDirectory.toString());
                        });

        assertEquals(ServerShopOfferReplayLedger.DirectoryForceResult
                .UNSUPPORTED_PLATFORM_BEST_EFFORT, result);
    }

    @Test
    void supportedPlatformDirectoryFailureStillFailsClosed() {
        assertThrows(AccessDeniedException.class,
                () -> ServerShopOfferReplayLedger.forceDirectory(
                        temporaryDirectory, false, ignored -> {
                            throw new AccessDeniedException(
                                    temporaryDirectory.toString());
                        }));
    }

    @Test
    void missingDirectoryFailsBeforeForceAttempt() {
        AtomicBoolean called = new AtomicBoolean();

        assertThrows(NoSuchFileException.class,
                () -> ServerShopOfferReplayLedger.forceDirectory(
                        temporaryDirectory.resolve("missing"),
                        true, ignored -> called.set(true)));
        assertFalse(called.get());
    }

    @Test
    void ordinaryIoFailureIsNotDowngraded() {
        IOException failure = assertThrows(IOException.class,
                () -> ServerShopOfferReplayLedger.forceDirectory(
                        temporaryDirectory, true, ignored -> {
                            throw new IOException("force failed");
                        }));

        assertEquals("force failed", failure.getMessage());
    }

    @Test
    void successfulDirectoryForceIsReported() throws Exception {
        AtomicBoolean called = new AtomicBoolean();

        ServerShopOfferReplayLedger.DirectoryForceResult result =
                ServerShopOfferReplayLedger.forceDirectory(
                        temporaryDirectory, false,
                        ignored -> called.set(true));

        assertTrue(called.get());
        assertEquals(ServerShopOfferReplayLedger.DirectoryForceResult
                .FORCED, result);
    }
}
