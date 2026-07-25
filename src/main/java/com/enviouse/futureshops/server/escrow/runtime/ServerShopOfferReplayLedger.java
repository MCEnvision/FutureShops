package com.enviouse.futureshops.server.escrow.runtime;

import net.minecraft.server.MinecraftServer;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.zip.CRC32C;

public final class ServerShopOfferReplayLedger
        implements AutoCloseable {
    private static final org.slf4j.Logger LOGGER =
            com.mojang.logging.LogUtils.getLogger();
    private static final java.util.concurrent.atomic.AtomicBoolean
            DIRECTORY_FORCE_WARNING =
            new java.util.concurrent.atomic.AtomicBoolean();
    public static final int MAXIMUM_DISCOVERY_BATCH = 1_024;
    private static final int INDEX_MAGIC = 0x46534F49;
    private static final int INDEX_VERSION = 1;
    private static final int DIGEST_BYTES = 32;
    private static final int INDEX_RECORD_BYTES =
            Integer.BYTES + Integer.BYTES
                    + Long.BYTES * 2 + DIGEST_BYTES
                    + Integer.BYTES;
    private static final Map<MinecraftServer, ServerShopOfferReplayLedger>
            SERVERS = new WeakHashMap<>();
    private static final LinkOption[] NO_FOLLOW =
            new LinkOption[]{LinkOption.NOFOLLOW_LINKS};

    private final Path root;
    private final Path indexPath;
    private final FileChannel lockChannel;
    private final FileLock lock;
    private final FileChannel index;
    private boolean closed;

    private ServerShopOfferReplayLedger(Path requestedRoot)
            throws IOException {
        root = requestedRoot.toAbsolutePath().normalize();
        requireSafeDirectory(root);
        Path lockPath = safeChild(root, "ledger.lock");
        requireSafeFileOrAbsent(lockPath);
        lockChannel = FileChannel.open(
                lockPath, StandardOpenOption.CREATE,
                StandardOpenOption.READ, StandardOpenOption.WRITE,
                LinkOption.NOFOLLOW_LINKS);
        FileLock acquired;
        try {
            acquired = lockChannel.tryLock();
        } catch (OverlappingFileLockException exception) {
            closeAfterFailure(lockChannel, null);
            throw new IOException(
                    "Server shop offer replay ledger is already open",
                    exception);
        }
        if (acquired == null) {
            closeAfterFailure(lockChannel, null);
            throw new IOException(
                    "Server shop offer replay ledger is already locked");
        }
        lock = acquired;
        indexPath = safeChild(root, "index.wal");
        requireSafeFileOrAbsent(indexPath);
        FileChannel openedIndex = null;
        try {
            openedIndex = FileChannel.open(
                    indexPath, StandardOpenOption.CREATE,
                    StandardOpenOption.READ, StandardOpenOption.WRITE,
                    LinkOption.NOFOLLOW_LINKS);
            long validBytes = openedIndex.size()
                    - openedIndex.size() % INDEX_RECORD_BYTES;
            if (validBytes != openedIndex.size()) {
                openedIndex.truncate(validBytes);
                openedIndex.force(true);
            }
            openedIndex.position(validBytes);
            index = openedIndex;
            forceDirectory(root);
        } catch (IOException | RuntimeException exception) {
            closeAfterFailure(lockChannel, acquired);
            if (openedIndex != null) {
                try {
                    openedIndex.close();
                } catch (IOException closeFailure) {
                    exception.addSuppressed(closeFailure);
                }
            }
            throw exception;
        }
    }

    public static synchronized ServerShopOfferReplayLedger get(
            MinecraftServer server
    ) {
        java.util.Objects.requireNonNull(server, "server");
        ServerShopOfferReplayLedger existing = SERVERS.get(server);
        if (existing != null && !existing.closed) {
            return existing;
        }
        try {
            ServerShopOfferReplayLedger opened = new
                    ServerShopOfferReplayLedger(
                    EscrowRuntimeService.journalPath(server)
                            .getParent().resolve("offer_replay"));
            SERVERS.put(server, opened);
            return opened;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to open server shop offer replay ledger",
                    exception);
        }
    }

    static synchronized void shutdown(MinecraftServer server) {
        ServerShopOfferReplayLedger ledger = SERVERS.remove(
                java.util.Objects.requireNonNull(server, "server"));
        if (ledger != null) {
            ledger.close();
        }
    }

    static ServerShopOfferReplayLedger open(Path root) {
        try {
            return new ServerShopOfferReplayLedger(
                    java.util.Objects.requireNonNull(root, "root"));
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to open server shop offer replay ledger",
                    exception);
        }
    }

    public synchronized Optional<ServerShopOfferReplayReceipt> find(
            UUID requestId
    ) {
        ensureOpen();
        Path target = receiptPath(requestId, false);
        if (!Files.exists(target, NO_FOLLOW)) {
            return Optional.empty();
        }
        return Optional.of(readReceipt(target, requestId));
    }

    public synchronized boolean record(
            ServerShopOfferReplayReceipt receipt
    ) {
        ensureOpen();
        java.util.Objects.requireNonNull(receipt, "receipt");
        Path target = receiptPath(receipt.requestId(), true);
        if (Files.exists(target, NO_FOLLOW)) {
            ServerShopOfferReplayReceipt existing =
                    readReceipt(target, receipt.requestId());
            if (!existing.equals(receipt)) {
                throw new IllegalStateException(
                        "Server shop offer replay identity conflicts");
            }
            return false;
        }
        byte[] encoded =
                ServerShopOfferReplayReceiptCodec.encode(receipt);
        byte[] digest = sha256(encoded);
        Path directory = target.getParent();
        Path temporary = safeChild(
                directory, "." + receipt.requestId()
                        + "." + UUID.randomUUID() + ".tmp");
        try {
            writeNewForced(temporary, encoded);
            appendIndex(receipt.requestId(), digest);
            try {
                Files.move(temporary, target,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException(
                        "Atomic replay receipt move is not supported",
                        exception);
            }
            forceDirectory(directory);
            return true;
        } catch (FileAlreadyExistsException exception) {
            deleteTemporary(temporary, exception);
            ServerShopOfferReplayReceipt existing =
                    readReceipt(target, receipt.requestId());
            if (!existing.equals(receipt)) {
                throw new IllegalStateException(
                        "Server shop offer replay identity conflicts",
                        exception);
            }
            return false;
        } catch (IOException exception) {
            deleteTemporary(temporary, exception);
            throw new IllegalStateException(
                    "Unable to persist server shop offer replay receipt",
                    exception);
        }
    }

    public synchronized DiscoveryBatch readDiscoveryBatch(
            long byteOffset,
            int maximumRecords
    ) {
        ensureOpen();
        if (byteOffset < 0L
                || byteOffset % INDEX_RECORD_BYTES != 0L
                || maximumRecords <= 0
                || maximumRecords > MAXIMUM_DISCOVERY_BATCH) {
            throw new IllegalArgumentException(
                    "Server shop offer replay discovery bounds are invalid");
        }
        try {
            long size = index.size();
            long completeSize = size - size % INDEX_RECORD_BYTES;
            if (byteOffset > completeSize) {
                throw new IllegalArgumentException(
                        "Server shop offer replay discovery offset is invalid");
            }
            long offset = byteOffset;
            List<ServerShopOfferReplayReceipt> receipts =
                    new ArrayList<>();
            int inspected = 0;
            while (offset < completeSize
                    && inspected < maximumRecords) {
                ByteBuffer encoded = ByteBuffer.allocate(
                        INDEX_RECORD_BYTES);
                readFully(index, encoded, offset);
                encoded.flip();
                IndexRecord record = decodeIndex(encoded);
                Path path = receiptPath(record.requestId(), false);
                if (Files.exists(path, NO_FOLLOW)) {
                    byte[] receiptBytes = readBounded(path);
                    if (!Arrays.equals(
                            record.digest(), sha256(receiptBytes))) {
                        throw new IllegalStateException(
                                "Server shop offer replay discovery conflicts");
                    }
                    ServerShopOfferReplayReceipt receipt =
                            ServerShopOfferReplayReceiptCodec.decode(
                                    receiptBytes);
                    if (!receipt.requestId().equals(
                            record.requestId())) {
                        throw new IllegalStateException(
                                "Server shop offer replay discovery identity conflicts");
                    }
                    receipts.add(receipt);
                }
                offset = Math.addExact(offset, INDEX_RECORD_BYTES);
                inspected++;
            }
            return new DiscoveryBatch(
                    receipts, offset, offset == completeSize, inspected);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to read server shop offer replay discovery",
                    exception);
        }
    }

    long indexSize() {
        ensureOpen();
        try {
            return index.size();
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to inspect server shop offer replay discovery",
                    exception);
        }
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        IOException failure = null;
        try {
            index.force(true);
        } catch (IOException exception) {
            failure = exception;
        }
        try {
            index.close();
        } catch (IOException exception) {
            failure = combine(failure, exception);
        }
        try {
            if (lock.isValid()) {
                lock.release();
            }
        } catch (IOException exception) {
            failure = combine(failure, exception);
        }
        try {
            lockChannel.close();
        } catch (IOException exception) {
            failure = combine(failure, exception);
        }
        if (failure != null) {
            throw new IllegalStateException(
                    "Unable to close server shop offer replay ledger",
                    failure);
        }
    }

    private void appendIndex(UUID requestId, byte[] digest)
            throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(
                INDEX_RECORD_BYTES);
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeInt(INDEX_MAGIC);
        output.writeInt(INDEX_VERSION);
        BinaryCodecSupport.writeUuid(output, requestId);
        output.write(digest);
        output.flush();
        byte[] body = bytes.toByteArray();
        CRC32C checksum = new CRC32C();
        checksum.update(body, 0, body.length);
        output.writeInt((int) checksum.getValue());
        output.flush();
        byte[] record = bytes.toByteArray();
        if (record.length != INDEX_RECORD_BYTES) {
            throw new IOException(
                    "Server shop offer replay discovery record is invalid");
        }
        index.position(index.size());
        writeFully(index, ByteBuffer.wrap(record));
        index.force(true);
    }

    private static IndexRecord decodeIndex(ByteBuffer encoded) {
        byte[] body = new byte[
                INDEX_RECORD_BYTES - Integer.BYTES];
        encoded.get(body);
        int storedChecksum = encoded.getInt();
        CRC32C checksum = new CRC32C();
        checksum.update(body, 0, body.length);
        if ((int) checksum.getValue() != storedChecksum) {
            throw new IllegalStateException(
                    "Server shop offer replay discovery checksum is invalid");
        }
        ByteBuffer input = ByteBuffer.wrap(body);
        if (input.getInt() != INDEX_MAGIC
                || input.getInt() != INDEX_VERSION) {
            throw new IllegalStateException(
                    "Server shop offer replay discovery header is invalid");
        }
        UUID requestId = new UUID(input.getLong(), input.getLong());
        byte[] digest = new byte[DIGEST_BYTES];
        input.get(digest);
        if (requestId.equals(new UUID(0L, 0L))
                || input.hasRemaining()) {
            throw new IllegalStateException(
                    "Server shop offer replay discovery record is invalid");
        }
        return new IndexRecord(requestId, digest);
    }

    private ServerShopOfferReplayReceipt readReceipt(
            Path target,
            UUID expectedRequestId
    ) {
        byte[] encoded = readBounded(target);
        ServerShopOfferReplayReceipt receipt =
                ServerShopOfferReplayReceiptCodec.decode(encoded);
        if (!receipt.requestId().equals(expectedRequestId)) {
            throw new IllegalStateException(
                    "Server shop offer replay file identity conflicts");
        }
        return receipt;
    }

    private byte[] readBounded(Path target) {
        try {
            BasicFileAttributes attributes = Files.readAttributes(
                    target, BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isRegularFile()
                    || attributes.size() <= 0L
                    || attributes.size()
                    > ServerShopOfferReplayReceiptCodec
                    .MAXIMUM_BYTES) {
                throw new IllegalStateException(
                        "Server shop offer replay file is invalid");
            }
            byte[] encoded = Files.readAllBytes(target);
            if (encoded.length != attributes.size()) {
                throw new IllegalStateException(
                        "Server shop offer replay file changed while reading");
            }
            return encoded;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to read server shop offer replay file",
                    exception);
        }
    }

    private Path receiptPath(UUID requestId, boolean createDirectories) {
        java.util.Objects.requireNonNull(requestId, "requestId");
        if (requestId.equals(new UUID(0L, 0L))) {
            throw new IllegalArgumentException(
                    "Server shop offer replay request is invalid");
        }
        String compact = requestId.toString().replace("-", "");
        Path first = safeChild(root, compact.substring(0, 2));
        Path second = safeChild(first, compact.substring(2, 4));
        if (createDirectories) {
            requireSafeDirectory(first);
            requireSafeDirectory(second);
        } else {
            requireSafeExistingDirectoryOrAbsent(first);
            requireSafeExistingDirectoryOrAbsent(second);
        }
        Path target = safeChild(
                second, requestId + ".receipt");
        requireSafeFileOrAbsent(target);
        return target;
    }

    private static void requireSafeDirectory(Path directory) {
        try {
            if (Files.exists(directory, NO_FOLLOW)) {
                if (Files.isSymbolicLink(directory)
                        || !Files.isDirectory(
                        directory, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IllegalStateException(
                            "Server shop offer replay path is unsafe");
                }
                return;
            }
            Path parent = directory.getParent();
            if (parent != null && !Files.exists(parent, NO_FOLLOW)) {
                requireSafeDirectory(parent);
            }
            Files.createDirectory(directory);
            forceDirectory(directory);
            if (parent != null) {
                forceDirectory(parent);
            }
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to create server shop offer replay directory",
                    exception);
        }
    }

    private static void requireSafeExistingDirectoryOrAbsent(
            Path directory
    ) {
        if (!Files.exists(directory, NO_FOLLOW)) {
            return;
        }
        if (Files.isSymbolicLink(directory)
                || !Files.isDirectory(
                directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException(
                    "Server shop offer replay path is unsafe");
        }
    }

    private static void requireSafeFileOrAbsent(Path path) {
        if (!Files.exists(path, NO_FOLLOW)) {
            return;
        }
        try {
            BasicFileAttributes attributes = Files.readAttributes(
                    path, BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isRegularFile()
                    || Files.isSymbolicLink(path)) {
                throw new IllegalStateException(
                        "Server shop offer replay path is unsafe");
            }
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to inspect server shop offer replay path",
                    exception);
        }
    }

    private static Path safeChild(Path parent, String child) {
        Path resolved = parent.resolve(child).normalize();
        if (!resolved.getParent().equals(parent)) {
            throw new IllegalArgumentException(
                    "Server shop offer replay path escapes its root");
        }
        return resolved;
    }

    private static void writeNewForced(Path path, byte[] value)
            throws IOException {
        try (FileChannel channel = FileChannel.open(
                path, StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            writeFully(channel, ByteBuffer.wrap(value));
            channel.force(true);
        }
    }

    private static void writeFully(
            FileChannel channel,
            ByteBuffer buffer
    ) throws IOException {
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
    }

    private static void readFully(
            FileChannel channel,
            ByteBuffer buffer,
            long position
    ) throws IOException {
        while (buffer.hasRemaining()) {
            int read = channel.read(buffer, position);
            if (read < 0) {
                throw new IOException(
                        "Server shop offer replay discovery is truncated");
            }
            position = Math.addExact(position, read);
        }
    }

    private static void forceDirectory(Path directory)
            throws IOException {
        try (FileChannel channel = FileChannel.open(
                directory, StandardOpenOption.READ,
                LinkOption.NOFOLLOW_LINKS)) {
            channel.force(true);
        } catch (UnsupportedOperationException exception) {
            if (DIRECTORY_FORCE_WARNING.compareAndSet(false, true)) {
                LOGGER.warn(
                        "Server shop replay directory force is unavailable on this platform");
            }
        }
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA256 is unavailable", exception);
        }
    }

    private static void deleteTemporary(
            Path temporary,
            Exception failure
    ) {
        try {
            Files.deleteIfExists(temporary);
        } catch (IOException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    private static void closeAfterFailure(
            FileChannel channel,
            FileLock lock
    ) throws IOException {
        IOException failure = null;
        if (lock != null) {
            try {
                lock.release();
            } catch (IOException exception) {
                failure = exception;
            }
        }
        try {
            channel.close();
        } catch (IOException exception) {
            failure = combine(failure, exception);
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static IOException combine(
            IOException first,
            IOException second
    ) {
        if (first == null) {
            return second;
        }
        first.addSuppressed(second);
        return first;
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException(
                    "Server shop offer replay ledger is closed");
        }
    }

    public record DiscoveryBatch(
            List<ServerShopOfferReplayReceipt> receipts,
            long nextByteOffset,
            boolean endOfIndex,
            int inspectedRecords
    ) {
        public DiscoveryBatch {
            receipts = List.copyOf(receipts);
            if (nextByteOffset < 0L || inspectedRecords < 0) {
                throw new IllegalArgumentException(
                        "Server shop offer replay discovery batch is invalid");
            }
        }
    }

    private record IndexRecord(UUID requestId, byte[] digest) {
        private IndexRecord {
            digest = digest.clone();
        }

        @Override
        public byte[] digest() {
            return digest.clone();
        }
    }
}
