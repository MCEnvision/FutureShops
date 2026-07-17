package com.enviouse.futureshops.server.escrow.checkpoint;

import java.io.BufferedInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class EscrowCheckpointManager {
    private static final Pattern GENERATION_FILE = Pattern.compile(
            "checkpoint-([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\\.fscp");

    private final Path directory;
    private final EscrowPersistenceFaultInjector faultInjector;

    public EscrowCheckpointManager(Path directory) throws IOException {
        this(directory, EscrowPersistenceFaultInjector.NONE);
    }

    public EscrowCheckpointManager(Path directory,
                                   EscrowPersistenceFaultInjector faultInjector) throws IOException {
        this.directory = Objects.requireNonNull(directory, "directory")
                .toAbsolutePath().normalize();
        this.faultInjector = Objects.requireNonNull(faultInjector, "faultInjector");
        Files.createDirectories(this.directory);
        if (!Files.isDirectory(this.directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Escrow checkpoint directory is invalid");
        }
    }

    public EscrowCheckpointManifest writeGeneration(EscrowCheckpoint checkpoint) throws IOException {
        Objects.requireNonNull(checkpoint, "checkpoint");
        Path destination = generationPath(checkpoint.checkpointId());
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
            DecodedGeneration existing = readGeneration(destination);
            if (!existing.checkpoint().equals(checkpoint)) {
                throw new EscrowCheckpointException("Escrow checkpoint identity already exists");
            }
            return existing.manifest();
        }

        Path temporary = directory.resolve(
                ".checkpoint-" + checkpoint.checkpointId() + "-" + UUID.randomUUID() + ".tmp");
        MessageDigest digest = sha256();
        long fileBytes;
        try (FileChannel channel = FileChannel.open(temporary,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            OutputStream channelOutput = Channels.newOutputStream(channel);
            DigestOutputStream digestOutput = new DigestOutputStream(channelOutput, digest);
            DataOutputStream output = new DataOutputStream(digestOutput);
            EscrowCheckpointCodec.write(output, checkpoint);
            output.flush();
            fileBytes = channel.size();
            if (fileBytes != EscrowCheckpointCodec.encodedSize(checkpoint)
                    || fileBytes > EscrowCheckpointCodec.MAX_FILE_BYTES) {
                throw new EscrowCheckpointException("Escrow checkpoint encoded size does not match");
            }
            faultInjector.at(EscrowPersistencePhase.CHECKPOINT_AFTER_TEMP_WRITE);
            channel.force(true);
            faultInjector.at(EscrowPersistencePhase.CHECKPOINT_AFTER_TEMP_FORCE);
        }
        byte[] checksum = digest.digest();

        try {
            EscrowDurableFiles.moveNewAtomically(temporary, destination);
        } catch (FileAlreadyExistsException exception) {
            DecodedGeneration existing = readGeneration(destination);
            if (!existing.checkpoint().equals(checkpoint)) {
                throw new EscrowCheckpointException("Escrow checkpoint identity already exists", exception);
            }
            Files.deleteIfExists(temporary);
            return existing.manifest();
        }
        faultInjector.at(EscrowPersistencePhase.CHECKPOINT_AFTER_GENERATION_MOVE);
        EscrowDurableFiles.forceDirectory(directory);
        faultInjector.at(EscrowPersistencePhase.CHECKPOINT_AFTER_DIRECTORY_FORCE);
        return new EscrowCheckpointManifest(
                checkpoint.checkpointId(), checkpoint.sourceJournalLineageId(),
                checkpoint.replacementJournalLineageId(), checkpoint.baseJournalSequence(),
                checkpoint.createdAt(), fileBytes, checksum);
    }

    public EscrowCheckpoint readVerified(EscrowCheckpointManifest manifest) throws IOException {
        Objects.requireNonNull(manifest, "manifest");
        DecodedGeneration decoded = readGeneration(generationPath(manifest.checkpointId()));
        if (!manifest.equals(decoded.manifest())) {
            throw new EscrowCheckpointException("Escrow checkpoint manifest does not match generation");
        }
        return decoded.checkpoint();
    }

    public TrustedEscrowCheckpoint loadTrusted(EscrowCheckpointReference reference,
                                               UUID activeJournalLineageId) throws IOException {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(activeJournalLineageId, "activeJournalLineageId");
        if (!reference.replacementJournalLineageId().equals(activeJournalLineageId)) {
            throw new EscrowCheckpointException("Checkpoint replacement lineage does not match journal");
        }
        EscrowCheckpoint checkpoint = readVerified(reference.manifest());
        if (!reference.manifest().describes(checkpoint)) {
            throw new EscrowCheckpointException("Checkpoint reference metadata does not match generation");
        }
        return new TrustedEscrowCheckpoint(
                checkpoint, reference, generationPath(reference.checkpointId()));
    }

    public Path generationPath(UUID checkpointId) {
        Objects.requireNonNull(checkpointId, "checkpointId");
        return directory.resolve("checkpoint-" + checkpointId + ".fscp");
    }

    public Path directory() {
        return directory;
    }

    public void cleanupOrphans(Set<UUID> retainedCheckpointIds) throws IOException {
        Objects.requireNonNull(retainedCheckpointIds, "retainedCheckpointIds");
        Set<UUID> retained = new HashSet<>(retainedCheckpointIds);
        if (retained.contains(null)) {
            throw new IllegalArgumentException("Retained checkpoint identity is invalid");
        }
        boolean changed = false;
        try (Stream<Path> entries = Files.list(directory)) {
            for (Path entry : entries.toList()) {
                String fileName = entry.getFileName().toString();
                Matcher matcher = GENERATION_FILE.matcher(fileName);
                if (matcher.matches()) {
                    UUID checkpointId = UUID.fromString(matcher.group(1));
                    if (!retained.contains(checkpointId)) {
                        changed |= Files.deleteIfExists(entry);
                    }
                } else if (fileName.startsWith(".checkpoint-") && fileName.endsWith(".tmp")) {
                    changed |= Files.deleteIfExists(entry);
                }
            }
        }
        if (changed) {
            EscrowDurableFiles.forceDirectory(directory);
        }
    }

    private DecodedGeneration readGeneration(Path path) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new EscrowCheckpointException("Escrow checkpoint generation is missing");
        }
        long fileBytes = Files.size(path);
        if (fileBytes <= 0L || fileBytes > EscrowCheckpointCodec.MAX_FILE_BYTES) {
            throw new EscrowCheckpointException("Escrow checkpoint file size is invalid");
        }
        MessageDigest digest = sha256();
        EscrowCheckpoint checkpoint;
        try (DigestInputStream digestInput = new DigestInputStream(
                new BufferedInputStream(Files.newInputStream(path)), digest)) {
            checkpoint = EscrowCheckpointCodec.read(digestInput, fileBytes);
        }
        EscrowCheckpointManifest manifest = new EscrowCheckpointManifest(
                checkpoint.checkpointId(), checkpoint.sourceJournalLineageId(),
                checkpoint.replacementJournalLineageId(), checkpoint.baseJournalSequence(),
                checkpoint.createdAt(), fileBytes, digest.digest());
        return new DecodedGeneration(checkpoint, manifest);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA256 is unavailable", exception);
        }
    }

    private record DecodedGeneration(EscrowCheckpoint checkpoint,
                                     EscrowCheckpointManifest manifest) {
    }
}
