package com.enviouse.futureshops.server.escrow.journal;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.zip.CRC32C;

public final class WriteAheadJournal implements AutoCloseable {
    public static final int FORMAT_VERSION = 1;
    public static final int MAX_PAYLOAD_BYTES = 16_777_216;
    public static final int MAX_REPLAY_BATCH_RECORDS = 10_000;
    public static final long MAX_REPLAY_BATCH_BYTES = 67_108_864L;

    private static final int MAGIC = 0x46534A31;
    private static final int PREFIX_WITHOUT_CHECKSUM_BYTES = Integer.BYTES + Short.BYTES + Short.BYTES + Integer.BYTES;
    private static final int PREFIX_BYTES = PREFIX_WITHOUT_CHECKSUM_BYTES + Integer.BYTES;
    private static final int FRAME_METADATA_BYTES = Long.BYTES
            + Long.BYTES * 4
            + Integer.BYTES;
    private static final int FIXED_FRAME_BYTES = FRAME_METADATA_BYTES + Integer.BYTES;
    private static final int CHECKSUM_BUFFER_BYTES = 65_536;
    public static final int MAX_RECORD_BYTES = PREFIX_BYTES + FIXED_FRAME_BYTES + MAX_PAYLOAD_BYTES;

    private final Path path;
    private final FileChannel channel;
    private final FileLock lock;
    private final JournalScanResult recovery;
    private long nextSequence;
    private boolean closed;
    private boolean failed;

    private WriteAheadJournal(
            Path path,
            FileChannel channel,
            FileLock lock,
            JournalScanResult recovery,
            long nextSequence
    ) {
        this.path = path;
        this.channel = channel;
        this.lock = lock;
        this.recovery = recovery;
        this.nextSequence = nextSequence;
    }

    public static WriteAheadJournal open(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        Path absolutePath = path.toAbsolutePath().normalize();
        Path parent = absolutePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        FileChannel channel = FileChannel.open(
                absolutePath,
                StandardOpenOption.CREATE,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE
        );
        FileLock lock = null;
        try {
            try {
                lock = channel.tryLock();
            } catch (OverlappingFileLockException exception) {
                throw new IOException("Journal is already open", exception);
            }
            if (lock == null) {
                throw new IOException("Journal is already locked");
            }

            JournalScanResult recovery = scanMetadata(channel);
            if (recovery.truncatedTail()) {
                channel.truncate(recovery.validBytes());
                channel.force(true);
            }
            channel.position(recovery.validBytes());
            long nextSequence = nextAfter(recovery.lastSequence());
            return new WriteAheadJournal(absolutePath, channel, lock, recovery, nextSequence);
        } catch (IOException | RuntimeException exception) {
            closeAfterOpenFailure(channel, lock, exception);
            throw exception;
        }
    }

    public synchronized JournalRecord append(UUID transactionId, UUID stepId, byte[] payload) throws IOException {
        return append(nextSequence, transactionId, stepId, payload);
    }

    public synchronized JournalRecord append(
            long sequence,
            UUID transactionId,
            UUID stepId,
            byte[] payload
    ) throws IOException {
        ensureWritable();
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(stepId, "stepId");
        Objects.requireNonNull(payload, "payload");
        if (payload.length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Payload exceeds maximum journal record size");
        }
        if (sequence != nextSequence) {
            throw new IllegalArgumentException("Expected journal sequence " + nextSequence + " but received " + sequence);
        }
        long followingSequence = nextAfter(sequence);

        JournalRecord record = new JournalRecord(sequence, transactionId, stepId, payload);
        ByteBuffer encoded = encode(record);
        try {
            channel.position(channel.size());
            writeFully(channel, encoded);
            channel.force(true);
        } catch (IOException exception) {
            failed = true;
            throw exception;
        }
        nextSequence = followingSequence;
        return record;
    }

    public JournalScanResult recovery() {
        return recovery;
    }

    public synchronized JournalReplayBatch replayBatch(long byteOffset,
                                                       long expectedSequence,
                                                       int maximumRecords,
                                                       long maximumRecordBytes) throws IOException {
        ensureOpen();
        requireReplayBounds(byteOffset, expectedSequence, maximumRecords, maximumRecordBytes);
        long fileBytes = channel.size();
        if (byteOffset > fileBytes) {
            throw new IllegalArgumentException("Journal replay offset exceeds file size");
        }
        if (byteOffset == fileBytes) {
            return new JournalReplayBatch(List.of(), byteOffset, byteOffset,
                    expectedSequence, expectedSequence, 0L, true);
        }

        List<JournalRecord> records = new ArrayList<>();
        ValidationBuffers buffers = new ValidationBuffers();
        long offset = byteOffset;
        long nextExpected = expectedSequence;
        long totalRecordBytes = 0L;
        while (offset < fileBytes && records.size() < maximumRecords) {
            FrameMetadata metadata = readFrameMetadata(channel, offset, fileBytes, false,
                    buffers);
            if (metadata.sequence() != nextExpected) {
                throw corrupt(offset, "Journal replay sequence does not match expected sequence");
            }
            if (!records.isEmpty()
                    && metadata.recordBytes() > maximumRecordBytes - totalRecordBytes) {
                break;
            }
            byte[] payload = readPayload(channel, offset, metadata.payloadBytes());
            records.add(new JournalRecord(metadata.sequence(), metadata.transactionId(),
                    metadata.stepId(), payload));
            totalRecordBytes = Math.addExact(totalRecordBytes, metadata.recordBytes());
            offset = Math.addExact(offset, metadata.recordBytes());
            nextExpected = nextAfter(metadata.sequence());
        }
        return new JournalReplayBatch(records, byteOffset, offset, expectedSequence,
                nextExpected, totalRecordBytes, offset == fileBytes);
    }

    public synchronized long nextSequence() throws IOException {
        ensureOpen();
        return nextSequence;
    }

    public synchronized long sizeBytes() throws IOException {
        ensureOpen();
        return channel.size();
    }

    public synchronized long recordCount() throws IOException {
        ensureOpen();
        return Math.subtractExact(nextSequence, 1L);
    }

    public Path path() {
        return path;
    }

    public synchronized boolean failed() {
        return failed;
    }

    @Override
    public synchronized void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        IOException failure = null;
        try {
            if (channel.isOpen()) {
                channel.force(true);
            }
        } catch (IOException exception) {
            failure = exception;
        }
        try {
            if (lock.isValid()) {
                lock.release();
            }
        } catch (IOException exception) {
            failure = combine(failure, exception);
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

    private static JournalScanResult scanMetadata(FileChannel channel) throws IOException {
        long originalBytes = channel.size();
        long offset = 0L;
        long firstSequence = 0L;
        long previousSequence = 0L;
        long recordCount = 0L;
        ValidationBuffers buffers = new ValidationBuffers();

        while (offset < originalBytes) {
            FrameMetadata metadata = readFrameMetadata(channel, offset, originalBytes, true,
                    buffers);
            if (metadata == null) {
                return new JournalScanResult(recordCount, firstSequence, previousSequence,
                        true, offset, originalBytes);
            }
            if (previousSequence != 0L && metadata.sequence() != previousSequence + 1L) {
                throw corrupt(offset, "Journal sequence is not contiguous");
            }
            if (recordCount == 0L) {
                firstSequence = metadata.sequence();
            }
            recordCount = Math.addExact(recordCount, 1L);
            previousSequence = metadata.sequence();
            offset = Math.addExact(offset, metadata.recordBytes());
        }
        return new JournalScanResult(recordCount, firstSequence, previousSequence,
                false, offset, originalBytes);
    }

    private static ByteBuffer encode(JournalRecord record) {
        byte[] payload = record.payload();
        int frameBytes = FIXED_FRAME_BYTES + payload.length;
        int recordBytes = PREFIX_BYTES + frameBytes;
        ByteBuffer encoded = ByteBuffer.allocate(recordBytes);
        encoded.putInt(MAGIC);
        encoded.putShort((short) FORMAT_VERSION);
        encoded.putShort((short) 0);
        encoded.putInt(frameBytes);
        CRC32C headerChecksum = new CRC32C();
        headerChecksum.update(encoded.array(), 0, PREFIX_WITHOUT_CHECKSUM_BYTES);
        encoded.putInt((int) headerChecksum.getValue());
        encoded.putLong(record.sequence());
        writeUuid(encoded, record.transactionId());
        writeUuid(encoded, record.stepId());
        encoded.putInt(payload.length);
        encoded.put(payload);

        CRC32C checksum = new CRC32C();
        checksum.update(encoded.array(), 0, recordBytes - Integer.BYTES);
        encoded.putInt((int) checksum.getValue());
        encoded.flip();
        return encoded;
    }

    private static FrameMetadata readFrameMetadata(FileChannel channel, long offset,
                                                   long fileBytes,
                                                   boolean incompleteTailAllowed,
                                                   ValidationBuffers buffers)
            throws IOException {
        long remaining = fileBytes - offset;
        if (remaining < PREFIX_BYTES) {
            if (incompleteTailAllowed) {
                return null;
            }
            throw new EOFException("Journal replay reached an incomplete prefix");
        }

        ByteBuffer prefix = buffers.prefix;
        prefix.clear();
        readFully(channel, prefix, offset);
        prefix.flip();
        int magic = prefix.getInt();
        int version = Short.toUnsignedInt(prefix.getShort());
        int flags = Short.toUnsignedInt(prefix.getShort());
        int frameBytes = prefix.getInt();
        int storedHeaderChecksum = prefix.getInt();
        if (magic != MAGIC) {
            throw corrupt(offset, "Journal magic does not match");
        }
        if (version != FORMAT_VERSION) {
            throw corrupt(offset, "Unsupported journal format version " + version);
        }
        if (flags != 0) {
            throw corrupt(offset, "Unsupported journal flags " + flags);
        }
        verifyHeaderChecksum(prefix.array(), storedHeaderChecksum, offset);
        if (frameBytes < FIXED_FRAME_BYTES
                || frameBytes > FIXED_FRAME_BYTES + MAX_PAYLOAD_BYTES) {
            throw corrupt(offset, "Journal frame length is invalid");
        }
        long recordBytes = PREFIX_BYTES + (long) frameBytes;
        if (remaining < recordBytes) {
            if (incompleteTailAllowed) {
                return null;
            }
            throw new EOFException("Journal replay reached an incomplete record");
        }

        ByteBuffer metadata = buffers.metadata;
        metadata.clear();
        readFully(channel, metadata, offset + PREFIX_BYTES);
        metadata.flip();
        long sequence = metadata.getLong();
        UUID transactionId = readUuid(metadata);
        UUID stepId = readUuid(metadata);
        int payloadBytes = metadata.getInt();
        int expectedPayloadBytes = frameBytes - FIXED_FRAME_BYTES;
        if (payloadBytes < 0 || payloadBytes > MAX_PAYLOAD_BYTES
                || payloadBytes != expectedPayloadBytes) {
            throw corrupt(offset, "Journal payload length is invalid");
        }
        if (sequence <= 0L) {
            throw corrupt(offset, "Journal sequence must be positive");
        }
        verifyChecksum(channel, offset, recordBytes, buffers);
        return new FrameMetadata(sequence, transactionId, stepId, payloadBytes, recordBytes);
    }

    private static byte[] readPayload(FileChannel channel, long recordOffset, int payloadBytes)
            throws IOException {
        byte[] payload = new byte[payloadBytes];
        if (payloadBytes == 0) {
            return payload;
        }
        readFully(channel, ByteBuffer.wrap(payload),
                recordOffset + PREFIX_BYTES + FRAME_METADATA_BYTES);
        return payload;
    }

    private static void verifyChecksum(FileChannel channel, long offset, long recordBytes,
                                       ValidationBuffers buffers)
            throws IOException {
        long checksumBytes = recordBytes - Integer.BYTES;
        CRC32C checksum = new CRC32C();
        ByteBuffer buffer = buffers.checksum;
        long position = offset;
        long remaining = checksumBytes;
        while (remaining > 0L) {
            int chunkBytes = (int) Math.min(buffer.capacity(), remaining);
            buffer.clear();
            buffer.limit(chunkBytes);
            readFully(channel, buffer, position);
            buffer.flip();
            checksum.update(buffer);
            position += chunkBytes;
            remaining -= chunkBytes;
        }
        ByteBuffer stored = buffers.storedChecksum;
        stored.clear();
        readFully(channel, stored, offset + checksumBytes);
        stored.flip();
        int storedChecksum = stored.getInt();
        if ((int) checksum.getValue() != storedChecksum) {
            throw corrupt(offset, "Journal record checksum does not match");
        }
    }

    private static void verifyHeaderChecksum(byte[] bytes, int storedChecksum, long offset)
            throws JournalCorruptionException {
        CRC32C checksum = new CRC32C();
        checksum.update(bytes, 0, PREFIX_WITHOUT_CHECKSUM_BYTES);
        if ((int) checksum.getValue() != storedChecksum) {
            throw corrupt(offset, "Journal header checksum does not match");
        }
    }

    private static UUID readUuid(ByteBuffer buffer) {
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    private static void writeUuid(ByteBuffer buffer, UUID value) {
        buffer.putLong(value.getMostSignificantBits());
        buffer.putLong(value.getLeastSignificantBits());
    }

    private static void readFully(FileChannel channel, ByteBuffer target, long offset) throws IOException {
        long position = offset;
        while (target.hasRemaining()) {
            int read = channel.read(target, position);
            if (read < 0) {
                throw new EOFException("Journal ended while reading a complete record");
            }
            if (read == 0) {
                Thread.yield();
                continue;
            }
            position += read;
        }
    }

    private static void writeFully(FileChannel channel, ByteBuffer source) throws IOException {
        while (source.hasRemaining()) {
            channel.write(source);
        }
    }

    private static long nextAfter(long sequence) throws IOException {
        if (sequence == Long.MAX_VALUE) {
            throw new IOException("Journal sequence space is exhausted");
        }
        return sequence + 1L;
    }

    private static void requireReplayBounds(long byteOffset, long expectedSequence,
                                            int maximumRecords, long maximumRecordBytes) {
        if (byteOffset < 0L || expectedSequence <= 0L) {
            throw new IllegalArgumentException("Journal replay position is invalid");
        }
        if (maximumRecords <= 0 || maximumRecords > MAX_REPLAY_BATCH_RECORDS) {
            throw new IllegalArgumentException("Journal replay record limit is invalid");
        }
        if (maximumRecordBytes <= 0L || maximumRecordBytes > MAX_REPLAY_BATCH_BYTES) {
            throw new IllegalArgumentException("Journal replay byte limit is invalid");
        }
    }

    private void ensureOpen() throws IOException {
        if (closed || !channel.isOpen()) {
            throw new IOException("Journal is closed");
        }
    }

    private void ensureWritable() throws IOException {
        ensureOpen();
        if (failed) {
            throw new IOException("Journal requires close and recovery after a failed write");
        }
    }

    private static JournalCorruptionException corrupt(long offset, String message) {
        return new JournalCorruptionException(offset, message + " at offset " + offset);
    }

    private static IOException combine(IOException first, IOException next) {
        if (first == null) {
            return next;
        }
        first.addSuppressed(next);
        return first;
    }

    private static void closeAfterOpenFailure(FileChannel channel, FileLock lock, Exception failure) {
        if (lock != null && lock.isValid()) {
            try {
                lock.release();
            } catch (IOException exception) {
                failure.addSuppressed(exception);
            }
        }
        try {
            channel.close();
        } catch (IOException exception) {
            failure.addSuppressed(exception);
        }
    }

    private record FrameMetadata(long sequence, UUID transactionId, UUID stepId,
                                 int payloadBytes, long recordBytes) {
    }

    private static final class ValidationBuffers {
        private final ByteBuffer prefix = ByteBuffer.allocate(PREFIX_BYTES);
        private final ByteBuffer metadata = ByteBuffer.allocate(FRAME_METADATA_BYTES);
        private final ByteBuffer checksum = ByteBuffer.allocate(CHECKSUM_BUFFER_BYTES);
        private final ByteBuffer storedChecksum = ByteBuffer.allocate(Integer.BYTES);
    }
}
