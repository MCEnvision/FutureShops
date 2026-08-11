package com.enviouse.futureshops.server.market.control;

import com.enviouse.futureshops.server.SavedDataMigrations;
import com.enviouse.futureshops.server.escrow.runtime.EscrowManagedSavedData;
import com.enviouse.futureshops.server.escrow.runtime.EscrowRuntimeStoreBinding;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.UUID;

public final class MarketControlSavedData extends EscrowManagedSavedData {
    public static final String DATA_NAME = "futureshops_market_control";
    public static final int CURRENT_VERSION = 1;

    private static final String STATE_KEY = "state";
    private static final String STATE_DIGEST_KEY = "stateDigest";

    private MarketControlState state = MarketControlState.initial(0L);

    public static MarketControlSavedData load(CompoundTag tag) {
        Objects.requireNonNull(tag, "tag");
        if (tag.contains("schemaVersion")
                && !tag.contains("schemaVersion", Tag.TAG_INT)) {
            throw new IllegalStateException(
                    "Market control schema has the wrong type");
        }
        int version = SavedDataMigrations.readVersion(tag);
        if (version > CURRENT_VERSION) {
            throw new IllegalStateException(
                    "Market control schema is newer than this build");
        }
        if (version < 0) {
            throw new IllegalStateException(
                    "Market control schema is invalid");
        }
        SavedDataMigrations.needsMigration(DATA_NAME, version,
                CURRENT_VERSION);
        MarketControlSavedData data = new MarketControlSavedData();
        if (version == 0 && !tag.contains(STATE_KEY)) {
            data.setDirty();
            return data;
        }
        if (!tag.contains(STATE_KEY, Tag.TAG_BYTE_ARRAY)
                || !tag.contains(STATE_DIGEST_KEY,
                Tag.TAG_BYTE_ARRAY)) {
            throw new IllegalStateException(
                    "Market control snapshot is incomplete");
        }
        try {
            byte[] encoded = tag.getByteArray(STATE_KEY);
            byte[] digest = tag.getByteArray(STATE_DIGEST_KEY);
            if (digest.length != 32 || !MessageDigest.isEqual(
                    digest, sha256(encoded))) {
                throw new IllegalArgumentException(
                        "Market control snapshot digest is invalid");
            }
            data.state = MarketControlStateCodec.decode(encoded);
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "Market control snapshot failed validation",
                    exception);
        }
        return data;
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag) {
        Objects.requireNonNull(tag, "tag");
        byte[] encoded = MarketControlStateCodec.encode(state);
        SavedDataMigrations.writeVersion(tag, CURRENT_VERSION);
        tag.putByteArray(STATE_KEY, encoded);
        tag.putByteArray(STATE_DIGEST_KEY, sha256(encoded));
        return tag;
    }

    public static MarketControlSavedData get(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return EscrowRuntimeStoreBinding.bind(server,
                server.overworld().getDataStorage().computeIfAbsent(
                        MarketControlSavedData::load,
                        MarketControlSavedData::new,
                        DATA_NAME));
    }

    public synchronized MarketControlApplyResult planStandalone(
            MarketControlTransitionCommand command
    ) {
        requireStandaloneTarget(Objects.requireNonNull(
                command, "command").targetStatus());
        return MarketControlRepository.transition(state, command);
    }

    public synchronized MarketControlApplyResult preflightCommitted(
            MarketControlMutation mutation
    ) {
        MarketControlMutation value = Objects.requireNonNull(
                mutation, "mutation");
        requireStandaloneTarget(value.nextModule().status());
        return MarketControlRepository.applyMutation(state, value);
    }

    public synchronized MarketControlApplyResult applyCommitted(
            MarketControlMutation mutation
    ) {
        requireEscrowMutationPermit();
        MarketControlApplyResult result = preflightCommitted(mutation);
        if (!result.replayed()) {
            state = result.state();
            setDirty();
        }
        return result;
    }

    public synchronized void replaceFromValidated(
            MarketControlSavedData source
    ) {
        requireEscrowMutationPermit();
        Objects.requireNonNull(source, "source");
        if (source == this) {
            return;
        }
        state = MarketControlStateCodec.decode(
                MarketControlStateCodec.encode(source.snapshot()));
        setDirty();
    }

    public synchronized MarketControlState snapshot() {
        return state;
    }

    public synchronized MarketControlAuditProjection auditProjection() {
        return MarketControlAuditProjection.from(state);
    }

    public synchronized MarketControlRequestReceipt receipt(
            UUID requestId
    ) {
        return state.requestReceipts().get(
                Objects.requireNonNull(requestId, "requestId"));
    }

    public synchronized boolean hasMaterializedState() {
        return state.hasMaterializedHistory();
    }

    private static void requireStandaloneTarget(
            MarketModuleStatus status
    ) {
        if (status == MarketModuleStatus.CANCEL_AND_REFUND) {
            throw new IllegalArgumentException(
                    "Cancel and refund requires a composite cancellation plan");
        }
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "Market control hashing is unavailable", exception);
        }
    }
}
