package com.enviouse.futureshops.server.escrow.checkpoint;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

final class EscrowCheckpointTestFixtures {
    static final UUID SOURCE_LINEAGE = UUID.fromString("10000000-0000-0000-0000-000000000001");
    static final UUID FIRST_LINEAGE = UUID.fromString("20000000-0000-0000-0000-000000000002");
    static final UUID SECOND_LINEAGE = UUID.fromString("30000000-0000-0000-0000-000000000003");
    static final UUID FIRST_CHECKPOINT = UUID.fromString("40000000-0000-0000-0000-000000000004");
    static final UUID SECOND_CHECKPOINT = UUID.fromString("50000000-0000-0000-0000-000000000005");
    static final Instant CREATED_AT = Instant.ofEpochSecond(1_900_000_000L, 123_456_789);

    private EscrowCheckpointTestFixtures() {
    }

    static EscrowCheckpoint firstCheckpoint() {
        return checkpoint(FIRST_CHECKPOINT, SOURCE_LINEAGE, FIRST_LINEAGE, 41L, "first");
    }

    static EscrowCheckpoint secondCheckpoint() {
        return checkpoint(SECOND_CHECKPOINT, FIRST_LINEAGE, SECOND_LINEAGE, 2L, "second");
    }

    static EscrowCheckpoint checkpoint(UUID checkpointId, UUID sourceLineage,
                                       UUID replacementLineage, long baseSequence,
                                       String prefix) {
        return new EscrowCheckpoint(
                checkpointId, sourceLineage, replacementLineage, baseSequence, CREATED_AT,
                snapshots(prefix));
    }

    static Map<EscrowCheckpointStore, byte[]> snapshots(String prefix) {
        EnumMap<EscrowCheckpointStore, byte[]> snapshots =
                new EnumMap<>(EscrowCheckpointStore.class);
        for (EscrowCheckpointStore store : EscrowCheckpointStore.values()) {
            snapshots.put(store, (prefix + "." + store.name())
                    .getBytes(StandardCharsets.UTF_8));
        }
        return snapshots;
    }
}
