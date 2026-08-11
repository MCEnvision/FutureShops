package com.enviouse.futureshops.server.market.bazaar;

import com.enviouse.futureshops.MinecraftTestBootstrap;
import com.enviouse.futureshops.server.market.bazaar.escrow.BazaarEscrowLifecycleEvent;
import com.enviouse.futureshops.server.market.bazaar.escrow.BazaarEscrowLifecycleConflictException;
import com.enviouse.futureshops.server.market.bazaar.escrow.BazaarEscrowLifecyclePlanner;
import com.enviouse.futureshops.server.market.bazaar.escrow.BazaarEscrowLifecycleState;
import com.enviouse.futureshops.server.market.bazaar.escrow.BazaarEscrowTestFixtures;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BazaarSavedDataTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void lifecycleAndOrderBookRoundTripAsOneCheckpointStore() {
        BazaarSavedData data = initializedData();
        var intent = BazaarEscrowTestFixtures.buy(400L,
                BazaarEscrowTestFixtures.id(40L), 2, 100_000L,
                BazaarTimeInForce.GOOD_UNTIL_CANCELLED,
                BazaarEscrowTestFixtures.rules());
        data.applyEscrowLifecycleCommitted(
                new BazaarEscrowLifecycleEvent.Prepare(intent));
        var planned = BazaarEscrowLifecyclePlanner.commitCreate(
                data.snapshot(), data.escrowLifecycleSnapshot(), intent,
                Optional.empty(), BazaarEscrowTestFixtures.NOW);
        data.applyEscrowLifecycleCommitted(
                new BazaarEscrowLifecycleEvent.Commit(
                        Optional.of(planned.terminalIntent()),
                        planned.commit()));

        BazaarSavedData loaded = BazaarSavedData.load(
                data.save(new CompoundTag()));

        assertEquals(data.snapshot(), loaded.snapshot());
        assertEquals(data.escrowLifecycleSnapshot(),
                loaded.escrowLifecycleSnapshot());
        assertEquals(planned.terminalIntent(), loaded.createIntent(
                intent.requestId()));
        assertTrue(loaded.escrowLifecycleSnapshot().activeBackings()
                .containsKey(intent.orderId()));
    }

    @Test
    void corruptOrIncompleteVersionTwoDataFailsClosed() {
        BazaarSavedData data = new BazaarSavedData();
        CompoundTag missing = new CompoundTag();
        missing.putInt("schemaVersion", BazaarSavedData.CURRENT_VERSION);
        assertThrows(IllegalStateException.class,
                () -> BazaarSavedData.load(missing));

        CompoundTag corruptSnapshot = data.save(new CompoundTag());
        byte[] snapshot = corruptSnapshot.getByteArray("snapshot");
        snapshot[snapshot.length / 2] ^= 1;
        corruptSnapshot.putByteArray("snapshot", snapshot);
        assertThrows(IllegalStateException.class,
                () -> BazaarSavedData.load(corruptSnapshot));

        CompoundTag corruptLifecycle = data.save(new CompoundTag());
        byte[] lifecycle = corruptLifecycle.getByteArray(
                "escrowLifecycle");
        lifecycle[lifecycle.length / 2] ^= 1;
        corruptLifecycle.putByteArray("escrowLifecycle", lifecycle);
        assertThrows(IllegalStateException.class,
                () -> BazaarSavedData.load(corruptLifecycle));
    }

    @Test
    void versionOneEmptySnapshotMigratesWithEmptyLifecycle() {
        CompoundTag legacy = new CompoundTag();
        legacy.putInt("schemaVersion", 1);
        legacy.putByteArray("snapshot",
                BazaarOrderBookSnapshotCodec.encode(
                        new BazaarOrderBook().snapshot()));

        BazaarSavedData loaded = BazaarSavedData.load(legacy);

        assertEquals(BazaarEscrowLifecycleState.empty(),
                loaded.escrowLifecycleSnapshot());
        assertFalse(loaded.hasMaterializedState());
        assertTrue(loaded.isDirty());
    }

    @Test
    void legacyMutationLaneCannotCreateAnUnbackedActiveOrder() {
        BazaarSavedData data = initializedData();
        var intent = BazaarEscrowTestFixtures.buy(500L,
                BazaarEscrowTestFixtures.id(50L), 1, 100_000L,
                BazaarTimeInForce.GOOD_UNTIL_CANCELLED,
                BazaarEscrowTestFixtures.rules());
        BazaarOrderBook book = BazaarOrderBook.restore(data.snapshot());
        book.create(intent.command());
        BazaarMutation mutation = BazaarMutation.between(data.snapshot(),
                book.snapshot(), intent.requestId());

        assertThrows(BazaarEscrowLifecycleConflictException.class,
                () -> data.applyCommitted(mutation));
        assertFalse(data.escrowLifecycleSnapshot().activeBackings()
                .containsKey(intent.orderId()));
    }

    private static BazaarSavedData initializedData() {
        BazaarSavedData data = new BazaarSavedData();
        BazaarLifecycleCommand rules =
                BazaarLifecycleCommand.setEffectiveRules(
                        BazaarEscrowTestFixtures.id(900L),
                        BazaarEscrowTestFixtures.rules());
        data.applyCommitted(BazaarMutation.lifecycle(data.snapshot(),
                rules));
        BazaarLifecycleCommand product =
                BazaarLifecycleCommand.registerProduct(
                        BazaarEscrowTestFixtures.id(901L),
                        BazaarEscrowTestFixtures.product());
        data.applyCommitted(BazaarMutation.lifecycle(data.snapshot(),
                product));
        return data;
    }
}
