package com.enviouse.futureshops.server.market.control;

import com.enviouse.futureshops.MinecraftTestBootstrap;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketControlSavedDataTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void snapshotRoundTripsWithDigestAndIdempotentReceipt() {
        MarketControlSavedData source = new MarketControlSavedData();
        MarketControlTransitionCommand command = command(id(1),
                MarketControlModule.AUCTION_HOUSE, 0L,
                MarketModuleStatus.FROZEN, Optional.empty());
        MarketControlApplyResult planned =
                source.planStandalone(command);
        source.applyCommitted(planned.mutation().orElseThrow());

        CompoundTag encoded = source.save(new CompoundTag());
        MarketControlSavedData decoded =
                MarketControlSavedData.load(encoded);

        assertEquals(source.snapshot(), decoded.snapshot());
        assertEquals(source.auditProjection(),
                decoded.auditProjection());
        assertEquals(planned.auditEntry(), decoded.receipt(id(1))
                .auditEntry());
        assertTrue(decoded.hasMaterializedState());
        assertTrue(decoded.applyCommitted(
                planned.mutation().orElseThrow()).replayed());
    }

    @Test
    void emptyLegacyStateMigratesWithoutMaterializingHistory() {
        MarketControlSavedData decoded =
                MarketControlSavedData.load(new CompoundTag());

        assertFalse(decoded.hasMaterializedState());
        assertTrue(decoded.isDirty());
        assertEquals(MarketModuleStatus.ENABLED,
                decoded.snapshot().module(MarketControlModule.SHOP)
                        .status());
    }

    @Test
    void digestCorruptionAndNewerSchemaFailClosed() {
        MarketControlSavedData source = new MarketControlSavedData();
        CompoundTag corrupted = source.save(new CompoundTag());
        byte[] state = corrupted.getByteArray("state");
        state[state.length / 2] ^= 1;
        corrupted.putByteArray("state", state);
        assertThrows(IllegalStateException.class,
                () -> MarketControlSavedData.load(corrupted));

        CompoundTag newer = source.save(new CompoundTag());
        newer.putInt("schemaVersion",
                MarketControlSavedData.CURRENT_VERSION + 1);
        assertThrows(IllegalStateException.class,
                () -> MarketControlSavedData.load(newer));
    }

    @Test
    void standalonePlanningAndApplyingRejectCancelAndRefund() {
        MarketControlSavedData data = new MarketControlSavedData();
        MarketControlTransitionCommand command = command(id(10),
                MarketControlModule.BAZAAR, 0L,
                MarketModuleStatus.CANCEL_AND_REFUND,
                Optional.of(id(11)));
        assertThrows(IllegalArgumentException.class,
                () -> data.planStandalone(command));

        MarketControlMutation mutation = MarketControlRepository
                .transition(data.snapshot(), command)
                .mutation().orElseThrow();
        assertThrows(IllegalArgumentException.class,
                () -> data.preflightCommitted(mutation));
        assertThrows(IllegalArgumentException.class,
                () -> data.applyCommitted(mutation));
        assertFalse(data.hasMaterializedState());
    }

    private static MarketControlTransitionCommand command(
            UUID requestId,
            MarketControlModule module,
            long revision,
            MarketModuleStatus status,
            Optional<UUID> cancellationBatch
    ) {
        return new MarketControlTransitionCommand(requestId, module,
                revision, status,
                new MarketControlActor(id(100), "Operator"),
                "Persistence test", 100L, 110L,
                cancellationBatch, Optional.empty());
    }

    private static UUID id(long value) {
        return new UUID(12L, value);
    }
}
