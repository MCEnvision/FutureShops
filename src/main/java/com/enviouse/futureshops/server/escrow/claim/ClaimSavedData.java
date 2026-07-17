package com.enviouse.futureshops.server.escrow.claim;

import com.enviouse.futureshops.server.SavedDataMigrations;
import com.enviouse.futureshops.server.escrow.runtime.EscrowManagedSavedData;
import com.enviouse.futureshops.server.escrow.runtime.EscrowRuntimeStoreBinding;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;

import java.time.DateTimeException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class ClaimSavedData extends EscrowManagedSavedData {
    public static final String DATA_NAME = "futureshops_escrow_claims";
    private static final int CURRENT_VERSION = 3;
    private static final int MAX_CLAIMS = 1_000_000;

    private final ClaimRepository repository = new ClaimRepository();

    public static ClaimSavedData load(CompoundTag tag) {
        if (tag.contains("schemaVersion")
                && !tag.contains("schemaVersion", Tag.TAG_INT)) {
            throw new IllegalStateException("Escrow claim schema has the wrong type");
        }
        int version = SavedDataMigrations.readVersion(tag);
        if (version > CURRENT_VERSION) {
            throw new IllegalStateException("Escrow claim schema is newer than this build");
        }
        if (version < 0) {
            throw new IllegalStateException("Escrow claim schema is invalid");
        }
        SavedDataMigrations.needsMigration(DATA_NAME, version, CURRENT_VERSION);
        ClaimSavedData data = new ClaimSavedData();
        Map<UUID, EscrowClaim> claims = new HashMap<>();
        Map<String, ClaimAttemptResult> attempts = new HashMap<>();

        ListTag claimTags = requireList(tag, "claims", version);
        requireBound(claimTags.size());
        for (Tag value : claimTags) {
            CompoundTag entry = (CompoundTag) value;
            if (!entry.hasUUID("claim") || !entry.hasUUID("transaction") || !entry.hasUUID("owner")) {
                throw new IllegalStateException("Escrow claim identity is invalid");
            }
            ClaimKind kind;
            ClaimStatus status;
            try {
                kind = ClaimKind.valueOf(entry.getString("kind"));
                status = ClaimStatus.valueOf(entry.getString("status"));
            } catch (IllegalArgumentException ex) {
                throw new IllegalStateException("Escrow claim type is invalid", ex);
            }
            EscrowClaim claim = new EscrowClaim(
                    entry.getUUID("claim"),
                    entry.getUUID("transaction"),
                    entry.getUUID("owner"),
                    version >= 3
                            ? requireString(entry, "source")
                            : "legacy.claim." + entry.getUUID("claim"),
                    kind,
                    requireLong(entry, "original"),
                    requireLong(entry, "remaining"),
                    requireBytes(entry, "payload"),
                    status,
                    requireString(entry, "label"),
                    readInstant(entry, "created", version),
                    readInstant(entry, "updated", version));
            if (claims.put(claim.claimId(), claim) != null) {
                throw new IllegalStateException("Duplicate escrow claim ID");
            }
        }

        ListTag attemptTags = requireList(tag, "attempts", version);
        requireBound(attemptTags.size());
        for (Tag value : attemptTags) {
            CompoundTag entry = (CompoundTag) value;
            if (!entry.hasUUID("claim")) {
                throw new IllegalStateException("Escrow claim attempt identity is invalid");
            }
            ClaimStatus status;
            try {
                status = ClaimStatus.valueOf(entry.getString("status"));
            } catch (IllegalArgumentException ex) {
                throw new IllegalStateException("Escrow claim attempt status is invalid", ex);
            }
            String key = requireString(entry, "key");
            ClaimAttemptResult attempt = new ClaimAttemptResult(
                    entry.getUUID("claim"), key, requireLong(entry, "delivered"),
                    requireLong(entry, "remaining"), status,
                    version >= 3
                            ? readInstant(entry, "delivered", version)
                            : requireClaim(claims, entry.getUUID("claim")).updatedAt(),
                    false);
            if (key.isBlank() || attempts.put(key, attempt) != null) {
                throw new IllegalStateException("Duplicate escrow claim attempt key");
            }
        }
        data.repository.restore(claims, attempts);
        if (version < CURRENT_VERSION) {
            data.setDirty();
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        SavedDataMigrations.writeVersion(tag, CURRENT_VERSION);
        ListTag claims = new ListTag();
        for (EscrowClaim claim : repository.snapshotClaims().values()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("claim", claim.claimId());
            entry.putUUID("transaction", claim.transactionId());
            entry.putUUID("owner", claim.ownerId());
            entry.putString("source", claim.sourceKey());
            entry.putString("kind", claim.kind().name());
            entry.putLong("original", claim.originalUnits());
            entry.putLong("remaining", claim.remainingUnits());
            entry.putByteArray("payload", claim.payload());
            entry.putString("status", claim.status().name());
            entry.putString("label", claim.label());
            writeInstant(entry, "created", claim.createdAt());
            writeInstant(entry, "updated", claim.updatedAt());
            claims.add(entry);
        }
        tag.put("claims", claims);

        ListTag attempts = new ListTag();
        for (Map.Entry<String, ClaimAttemptResult> value : repository.snapshotAttempts().entrySet()) {
            ClaimAttemptResult attempt = value.getValue();
            CompoundTag entry = new CompoundTag();
            entry.putString("key", value.getKey());
            entry.putUUID("claim", attempt.claimId());
            entry.putLong("delivered", attempt.deliveredUnits());
            entry.putLong("remaining", attempt.remainingUnits());
            entry.putString("status", attempt.status().name());
            writeInstant(entry, "delivered", attempt.deliveredAt());
            attempts.add(entry);
        }
        tag.put("attempts", attempts);
        return tag;
    }

    public static ClaimSavedData get(MinecraftServer server) {
        return EscrowRuntimeStoreBinding.bind(server,
                server.overworld().getDataStorage().computeIfAbsent(
                        ClaimSavedData::load, ClaimSavedData::new, DATA_NAME));
    }

    public synchronized void replaceFromValidated(ClaimSavedData source) {
        requireEscrowMutationPermit();
        Objects.requireNonNull(source, "source");
        if (source == this) {
            return;
        }
        ClaimStateSnapshot snapshot = source.snapshotForRestore();
        repository.restore(snapshot.claims(), snapshot.attempts());
        setDirty();
    }

    public synchronized EscrowClaim createCommitted(EscrowClaim claim) {
        requireEscrowMutationPermit();
        boolean existed = repository.get(claim.claimId()) != null;
        EscrowClaim result = repository.create(claim);
        if (!existed) {
            setDirty();
        }
        return result;
    }

    public synchronized EscrowClaim preflightCreateCommitted(EscrowClaim claim) {
        return repository.preflightCreate(claim);
    }

    public synchronized void preflightCreateBatch(List<EscrowClaim> claims) {
        repository.preflightCreateBatch(claims);
    }

    public synchronized ClaimAttemptResult deliverCommitted(UUID ownerId, UUID claimId,
                                                            String requestKey, long units,
                                                            Instant deliveredAt) {
        requireEscrowMutationPermit();
        ClaimAttemptResult result = repository.deliver(
                ownerId, claimId, requestKey, units, deliveredAt,
                (claim, requested) -> requested);
        if (!result.replayed() && result.deliveredUnits() > 0L) {
            setDirty();
        }
        return result;
    }

    public synchronized ClaimAttemptResult preflightDeliveryCommitted(
            UUID ownerId,
            UUID claimId,
            String requestKey,
            long units,
            Instant deliveredAt
    ) {
        return repository.preflightDeliver(
                ownerId, claimId, requestKey, units, deliveredAt);
    }

    public synchronized EscrowClaim quarantineCommitted(UUID ownerId, UUID claimId, Instant now) {
        requireEscrowMutationPermit();
        EscrowClaim before = repository.get(claimId);
        EscrowClaim result = repository.quarantine(ownerId, claimId, now);
        if (before != result) {
            setDirty();
        }
        return result;
    }

    public synchronized EscrowClaim preflightQuarantineCommitted(
            UUID ownerId,
            UUID claimId,
            Instant now
    ) {
        return repository.preflightQuarantine(ownerId, claimId, now);
    }

    public synchronized EscrowClaim getClaim(UUID claimId) {
        return repository.get(claimId);
    }

    public synchronized ClaimMaintenanceApplyResult preflightMaintenanceReplace(
            EscrowClaim replacement
    ) {
        return repository.preflightMaintenanceReplace(replacement);
    }

    public synchronized ClaimMaintenanceApplyResult applyMaintenanceReplace(
            EscrowClaim replacement
    ) {
        requireEscrowMutationPermit();
        ClaimMaintenanceApplyResult result = repository.applyMaintenanceReplace(replacement);
        if (!result.replayed()) {
            setDirty();
        }
        return result;
    }

    public synchronized boolean maintenanceStateWasApplied(EscrowClaim state) {
        return repository.maintenanceStateWasApplied(state);
    }

    public synchronized java.util.List<EscrowClaim> claimsForTransaction(UUID transactionId) {
        return repository.forTransaction(transactionId);
    }

    public synchronized java.util.List<EscrowClaim> pendingFor(UUID ownerId, int limit) {
        return repository.pendingFor(ownerId, limit);
    }

    public synchronized boolean hasMaterializedState() {
        return repository.hasMaterializedState();
    }

    public synchronized ClaimLiabilitySnapshot liabilitySnapshot() {
        List<ClaimLiabilityEntry> entries = repository.snapshotClaims().values().stream()
                .filter(claim -> claim.remainingUnits() > 0L)
                .map(claim -> new ClaimLiabilityEntry(
                        claim.claimId(), claim.transactionId(), liabilityCategory(claim),
                        claim.status(), claim.remainingUnits()))
                .sorted(Comparator.comparing(value -> value.claimId().toString()))
                .toList();
        return new ClaimLiabilitySnapshot(entries);
    }

    private static void requireBound(int size) {
        if (size < 0 || size > MAX_CLAIMS) {
            throw new IllegalStateException("Escrow claims exceed entry limit");
        }
    }

    private static ListTag requireList(CompoundTag tag, String key, int version) {
        if (!tag.contains(key)) {
            if (version > 0) {
                throw new IllegalStateException("Escrow claim data is missing");
            }
            return new ListTag();
        }
        Tag raw = tag.get(key);
        if (!(raw instanceof ListTag list)
                || (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)) {
            throw new IllegalStateException("Escrow claim data has the wrong type");
        }
        return list;
    }

    private static long requireLong(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_LONG)) {
            throw new IllegalStateException("Escrow claim number is missing");
        }
        return tag.getLong(key);
    }

    private static String requireString(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_STRING)) {
            throw new IllegalStateException("Escrow claim text is missing");
        }
        return tag.getString(key);
    }

    private static byte[] requireBytes(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_BYTE_ARRAY)) {
            throw new IllegalStateException("Escrow claim payload is missing");
        }
        byte[] payload = tag.getByteArray(key);
        if (payload.length > EscrowClaim.MAX_PAYLOAD_BYTES) {
            throw new IllegalStateException("Escrow claim payload exceeds its limit");
        }
        return payload;
    }

    private static EscrowClaim requireClaim(Map<UUID, EscrowClaim> claims, UUID claimId) {
        EscrowClaim claim = claims.get(claimId);
        if (claim == null) {
            throw new IllegalStateException("Escrow claim attempt references a missing claim");
        }
        return claim;
    }

    private static Instant readInstant(CompoundTag tag, String key, int version) {
        if (version < 2) {
            return Instant.ofEpochMilli(requireLong(tag, key));
        }
        long seconds = requireLong(tag, key + "EpochSecond");
        int nanos = requireInt(tag, key + "Nano");
        if (nanos < 0 || nanos > 999999999) {
            throw new IllegalStateException("Escrow claim timestamp nanoseconds are invalid");
        }
        try {
            return Instant.ofEpochSecond(seconds, nanos);
        } catch (DateTimeException exception) {
            throw new IllegalStateException("Escrow claim timestamp is invalid", exception);
        }
    }

    private static void writeInstant(CompoundTag tag, String key, Instant value) {
        tag.putLong(key + "EpochSecond", value.getEpochSecond());
        tag.putInt(key + "Nano", value.getNano());
    }

    private static int requireInt(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_INT)) {
            throw new IllegalStateException("Escrow claim number is missing");
        }
        return tag.getInt(key);
    }

    private static ClaimLiabilityCategory liabilityCategory(EscrowClaim claim) {
        return switch (claim.kind()) {
            case MONEY -> ClaimLiabilityCategory.MONEY;
            case ITEM -> ClaimLiabilityCategory.ITEM;
            case PROTECTED_CASH -> ClaimLiabilityCategory.PROTECTED_CASH;
            case FOREIGN_CASH -> ClaimLiabilityCategory.FOREIGN_CASH;
            case BARTER_ITEM -> ClaimLiabilityCategory.BARTER_ITEM;
            case REFUND -> claim.payload().length == 0
                    ? ClaimLiabilityCategory.MONEY_REFUND
                    : ClaimLiabilityCategory.ITEM_REFUND;
        };
    }

    private synchronized ClaimStateSnapshot snapshotForRestore() {
        return new ClaimStateSnapshot(repository.snapshotClaims(),
                repository.snapshotAttempts());
    }

    private record ClaimStateSnapshot(Map<UUID, EscrowClaim> claims,
                                      Map<String, ClaimAttemptResult> attempts) {
    }
}
