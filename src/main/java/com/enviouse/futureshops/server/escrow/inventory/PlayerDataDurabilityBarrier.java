package com.enviouse.futureshops.server.escrow.inventory;

import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.FileSystemException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class PlayerDataDurabilityBarrier {
    private final FileForcer fileForcer;
    private final DirectoryForcer directoryForcer;
    private final Set<UUID> unconfirmedReceipts = new HashSet<>();

    public PlayerDataDurabilityBarrier() {
        this(PlayerDataDurabilityBarrier::forceFile,
                PlayerDataDurabilityBarrier::forceDirectory);
    }

    PlayerDataDurabilityBarrier(
            FileForcer fileForcer,
            DirectoryForcer directoryForcer
    ) {
        this.fileForcer = Objects.requireNonNull(
                fileForcer, "fileForcer");
        this.directoryForcer = Objects.requireNonNull(
                directoryForcer, "directoryForcer");
    }

    DirectoryForceResult force(Path playerFile) throws IOException {
        Path file = Objects.requireNonNull(
                playerFile, "playerFile").toAbsolutePath();
        Path directory = file.getParent();
        if (directory == null) {
            throw new IOException(
                    "Player data file has no parent directory");
        }
        fileForcer.force(file);
        return Objects.requireNonNull(
                directoryForcer.force(directory),
                "directoryForceResult");
    }

    public void forcePlayerData(
            MinecraftServer server,
            UUID playerId
    ) throws IOException {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(playerId, "playerId");
        force(PlayerInventoryReceiptStore.playerFile(server, playerId));
    }

    synchronized void markUnconfirmed(UUID receiptId) {
        unconfirmedReceipts.add(Objects.requireNonNull(
                receiptId, "receiptId"));
    }

    synchronized boolean isUnconfirmed(UUID receiptId) {
        return unconfirmedReceipts.contains(Objects.requireNonNull(
                receiptId, "receiptId"));
    }

    private static void forceFile(Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(
                file, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    private static DirectoryForceResult forceDirectory(
            Path directory
    ) throws IOException {
        if (!Files.isDirectory(directory)) {
            throw new NoSuchFileException(directory.toString());
        }
        FileChannel channel;
        try {
            channel = FileChannel.open(
                    directory, StandardOpenOption.READ);
        } catch (UnsupportedOperationException exception) {
            return DirectoryForceResult.UNSUPPORTED_PLATFORM_BEST_EFFORT;
        } catch (FileSystemException exception) {
            if (isWindowsPlatform()) {
                return DirectoryForceResult
                        .UNSUPPORTED_PLATFORM_BEST_EFFORT;
            }
            throw exception;
        }
        try (channel) {
            try {
                channel.force(true);
            } catch (UnsupportedOperationException exception) {
                return DirectoryForceResult
                        .UNSUPPORTED_PLATFORM_BEST_EFFORT;
            } catch (IOException exception) {
                if (isWindowsPlatform()) {
                    return DirectoryForceResult
                            .UNSUPPORTED_PLATFORM_BEST_EFFORT;
                }
                throw exception;
            }
        }
        return DirectoryForceResult.FORCED;
    }

    private static boolean isWindowsPlatform() {
        return "\\".equals(FileSystems.getDefault().getSeparator());
    }

    enum DirectoryForceResult {
        FORCED,

        /**
         * The file was forced, but directory channels are unsupported.
         */
        UNSUPPORTED_PLATFORM_BEST_EFFORT
    }

    @FunctionalInterface
    interface FileForcer {
        void force(Path file) throws IOException;
    }

    @FunctionalInterface
    interface DirectoryForcer {
        DirectoryForceResult force(Path directory) throws IOException;
    }
}
