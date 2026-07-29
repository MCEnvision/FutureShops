package com.enviouse.futureshops.server.escrow.checkpoint;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystemException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class EscrowDurableFiles {
    private static final Logger LOGGER = LoggerFactory.getLogger(
            EscrowDurableFiles.class);
    private static final AtomicBoolean DIRECTORY_FORCE_WARNING =
            new AtomicBoolean();

    private EscrowDurableFiles() {
    }

    static void moveNewAtomically(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            throw new IOException("Atomic escrow file move is not supported", exception);
        }
    }

    static void replaceAtomically(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            throw new IOException("Atomic escrow file replacement is not supported", exception);
        }
    }

    static void forceDirectory(Path directory) throws IOException {
        DirectoryForceResult result = forceDirectory(directory,
                isWindowsPlatform(), EscrowDurableFiles::nativeForce);
        if (result == DirectoryForceResult
                .UNSUPPORTED_PLATFORM_BEST_EFFORT
                && DIRECTORY_FORCE_WARNING.compareAndSet(false, true)) {
            LOGGER.warn("Escrow directory force is unavailable on this platform. FutureShops will continue after forcing each file and completing its atomic move.");
        }
    }

    static DirectoryForceResult forceDirectory(
            Path directory,
            boolean windowsPlatform,
            DirectoryForceAction action
    ) throws IOException {
        if (!Files.isDirectory(directory)) {
            throw new NoSuchFileException(directory.toString());
        }
        try {
            action.force(directory);
            return DirectoryForceResult.FORCED;
        } catch (UnsupportedOperationException exception) {
            return DirectoryForceResult
                    .UNSUPPORTED_PLATFORM_BEST_EFFORT;
        } catch (FileSystemException exception) {
            if (windowsPlatform) {
                return DirectoryForceResult
                        .UNSUPPORTED_PLATFORM_BEST_EFFORT;
            }
            throw exception;
        }
    }

    private static void nativeForce(Path directory) throws IOException {
        try (FileChannel channel = FileChannel.open(directory,
                StandardOpenOption.READ)) {
            channel.force(true);
        }
    }

    private static boolean isWindowsPlatform() {
        return "\\".equals(FileSystems.getDefault().getSeparator());
    }

    enum DirectoryForceResult {
        FORCED,
        UNSUPPORTED_PLATFORM_BEST_EFFORT
    }

    @FunctionalInterface
    interface DirectoryForceAction {
        void force(Path directory) throws IOException;
    }
}
