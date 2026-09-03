package com.enviouse.futureshopsp.money;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Locked-in guards for the coin anti-dupe ledger ({@link SpentMintsSavedData}).
 *
 * <p>This is the one part of the mod where a silent regression is a community-trust
 * catastrophe, and the economy services are built on top of it — so these run as part
 * of every tranche gate from here forward. The {@link net.minecraft.core.HolderLookup.Provider}
 * argument to load/save is unused by this SavedData (all fields are primitive/UUID/String),
 * so the tests pass {@code null} for it.
 */
class SpentMintsSavedDataTest {

    /** Proves serialization is stable: load(save(x)) reproduces the ledger exactly, and re-saving is byte-identical. */
    @Test
    void roundTrip_preservesLedgerAndIsStable() {
        SpentMintsSavedData original = new SpentMintsSavedData();
        original.registerMint("mint-A", new UUID(1L, 2L), 100L, 64, 1000L, "srv");
        original.registerMint("mint-B", new UUID(3L, 4L), 500L, 10, 2000L, "srv");
        // Partially consume A so remaining (44) != authorized (64) — exercises the non-trivial field.
        original.consume("mint-A", 20, 100L, 64);

        CompoundTag saved = original.save(new CompoundTag(), null);
        SpentMintsSavedData reloaded = SpentMintsSavedData.load(saved, null);

        // Whole-registry value equality (MoneyMintRecord is a record).
        assertEquals(original.snapshotRegistry(), reloaded.snapshotRegistry(), "ledger registry must survive save->load");
        assertEquals(44, reloaded.remainingCount("mint-A"));
        assertEquals(10, reloaded.remainingCount("mint-B"));

        // Re-saving the reloaded ledger yields byte-identical NBT (serialization stability).
        CompoundTag savedAgain = reloaded.save(new CompoundTag(), null);
        assertEquals(saved, savedAgain, "re-serialized NBT must be identical");
    }

    /** The core anti-dupe invariant: N cloned stacks sharing one mint_id cannot redeem more than authorized_count total. */
    @Test
    void antiDupe_clonedStacksCannotExceedAuthorizedCount() {
        SpentMintsSavedData ledger = new SpentMintsSavedData();
        ledger.registerMint("mint-X", new UUID(5L, 6L), 100L, 10, 1000L, "srv"); // authorized 10

        // Five cloned stacks, each claiming the full batch of 10 of the SAME mint_id (a dupe attempt).
        int totalAccepted = 0;
        for (int i = 0; i < 5; i++) {
            totalAccepted += ledger.consume("mint-X", 10, 100L, 10).accepted();
        }
        assertEquals(10, totalAccepted, "ledger must cap total redemption at authorized_count regardless of clones");
        assertEquals(0, ledger.remainingCount("mint-X"));

        // Tampered authorized_count is rejected outright.
        assertEquals(0, ledger.consume("mint-X", 1, 100L, 9999).accepted());

        // Unknown mint id is rejected.
        SpentMintsSavedData.ConsumeResult unknown = ledger.consume("mint-Y", 5, 100L, 10);
        assertEquals(0, unknown.accepted());
        assertEquals("UNKNOWN_MINT", unknown.errorCode());
    }

    @Test
    void restoreReopensOnlyPreviouslyConsumedCount() {
        SpentMintsSavedData ledger = new SpentMintsSavedData();
        ledger.registerMint("mint-restore", new UUID(7L, 8L), 100L, 4, 1000L, "srv");

        assertEquals(2, ledger.consume("mint-restore", 2, 100L, 4).accepted());
        ledger.restore("mint-restore", 2, 100L, 4);

        assertEquals(4, ledger.remainingCount("mint-restore"));
    }

    @Test
    void malformedAndDuplicateRecordsAreDiscardedDuringLoad() {
        CompoundTag saved = new CompoundTag();
        saved.putInt("schemaVersion", 2);
        ListTag entries = new ListTag();

        CompoundTag valid = new CompoundTag();
        valid.putString("id", "mint-valid");
        valid.putUUID("player", new UUID(9L, 10L));
        valid.putLong("denomination", 100L);
        valid.putInt("authorized_count", 4);
        valid.putInt("remaining_count", 4);
        valid.putLong("minted_at", 1000L);
        valid.putLong("consumed_at", 0L);
        valid.putString("server", "srv");
        entries.add(valid);

        CompoundTag malformed = valid.copy();
        malformed.putString("id", "mint-malformed");
        malformed.putInt("remaining_count", 5);
        entries.add(malformed);

        CompoundTag duplicate = valid.copy();
        entries.add(duplicate);

        CompoundTag oversizedId = valid.copy();
        oversizedId.putString("id", "x".repeat(257));
        entries.add(oversizedId);
        saved.put("mints", entries);

        SpentMintsSavedData loaded = SpentMintsSavedData.load(saved, null);

        assertEquals(1, loaded.snapshotRegistry().size());
        assertEquals(4, loaded.remainingCount("mint-valid"));
        assertEquals(0, loaded.remainingCount("mint-malformed"));
    }

    @Test
    void concurrentConsumersCannotExceedAuthorizedCount() throws Exception {
        SpentMintsSavedData ledger = new SpentMintsSavedData();
        ledger.registerMint("mint-concurrent", new UUID(11L, 12L), 100L, 32, 1000L, "srv");

        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch ready = new CountDownLatch(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        try {
            for (int i = 0; i < 8; i++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return ledger.consume("mint-concurrent", 8, 100L, 32).accepted();
                }));
            }
            ready.await();
            start.countDown();
            int accepted = 0;
            for (Future<Integer> result : results) {
                accepted += result.get();
            }
            assertEquals(32, accepted);
            assertEquals(0, ledger.remainingCount("mint-concurrent"));
        } finally {
            executor.shutdownNow();
        }
    }
}
