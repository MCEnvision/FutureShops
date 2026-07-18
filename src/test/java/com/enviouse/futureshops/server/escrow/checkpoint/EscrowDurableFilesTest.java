package com.enviouse.futureshops.server.escrow.checkpoint;

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

class EscrowDurableFilesTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void windowsDirectoryChannelDenialUsesBestEffortFallback()
            throws Exception {
        EscrowDurableFiles.DirectoryForceResult result =
                EscrowDurableFiles.forceDirectory(temporaryDirectory,
                        true, ignored -> {
                            throw new AccessDeniedException(
                                    temporaryDirectory.toString());
                        });

        assertEquals(EscrowDurableFiles.DirectoryForceResult
                .UNSUPPORTED_PLATFORM_BEST_EFFORT, result);
    }

    @Test
    void supportedPlatformDirectoryFailureStillFailsClosed() {
        assertThrows(AccessDeniedException.class,
                () -> EscrowDurableFiles.forceDirectory(
                        temporaryDirectory, false, ignored -> {
                            throw new AccessDeniedException(
                                    temporaryDirectory.toString());
                        }));
    }

    @Test
    void missingDirectoryFailsBeforeTheForceAttempt() {
        AtomicBoolean called = new AtomicBoolean();

        assertThrows(NoSuchFileException.class,
                () -> EscrowDurableFiles.forceDirectory(
                        temporaryDirectory.resolve("missing"), true,
                        ignored -> called.set(true)));
        assertFalse(called.get());
    }

    @Test
    void ordinaryIoFailureIsNeverDowngraded() {
        IOException failure = assertThrows(IOException.class,
                () -> EscrowDurableFiles.forceDirectory(
                        temporaryDirectory, true, ignored -> {
                            throw new IOException("force failed");
                        }));

        assertEquals("force failed", failure.getMessage());
    }

    @Test
    void successfulDirectoryForceIsReported() throws Exception {
        AtomicBoolean called = new AtomicBoolean();

        EscrowDurableFiles.DirectoryForceResult result =
                EscrowDurableFiles.forceDirectory(temporaryDirectory,
                        false, ignored -> called.set(true));

        assertTrue(called.get());
        assertEquals(EscrowDurableFiles.DirectoryForceResult.FORCED,
                result);
    }
}
