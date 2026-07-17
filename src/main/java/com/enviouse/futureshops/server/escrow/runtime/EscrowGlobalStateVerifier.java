package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.checkpoint.EscrowCheckpointStore;

import com.enviouse.futureshops.server.escrow.admin.MaintenanceStateFingerprint;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Objects;

public final class EscrowGlobalStateVerifier {
    private EscrowGlobalStateVerifier() {
    }

    public static EscrowGlobalVerificationSnapshot verify(
            long journalSequence,
            Map<EscrowCheckpointStore, byte[]> snapshots
    ) {
        Objects.requireNonNull(snapshots, "snapshots");
        if (journalSequence < 0L
                || snapshots.size() != EscrowCheckpointStore.values().length) {
            throw new EscrowRuntimeException(
                    "Escrow global verification input is incomplete");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(ByteBuffer.allocate(Integer.BYTES * 2 + Long.BYTES)
                    .putInt(0x46534756)
                    .putInt(1)
                    .putLong(journalSequence)
                    .array());
            for (EscrowCheckpointStore store : EscrowCheckpointStore.values()) {
                byte[] snapshot = Objects.requireNonNull(snapshots.get(store),
                        "snapshot");
                digest.update(ByteBuffer.allocate(Integer.BYTES * 2)
                        .putInt(store.wireId())
                        .putInt(snapshot.length)
                        .array());
                digest.update(snapshot);
            }
            return new EscrowGlobalVerificationSnapshot(journalSequence,
                    MaintenanceStateFingerprint.of(digest.digest()));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "Unable to fingerprint escrow global state", exception);
        }
    }
}
