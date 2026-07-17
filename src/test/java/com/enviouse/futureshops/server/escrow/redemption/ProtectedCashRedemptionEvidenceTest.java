package com.enviouse.futureshops.server.escrow.redemption;

import com.enviouse.futureshops.MinecraftTestBootstrap;
import com.enviouse.futureshops.money.InternalBillInventoryPlanner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProtectedCashRedemptionEvidenceTest {
    @TempDir
    Path temporaryDirectory;

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void canonicalEvidenceRoundTripsForEveryDurablePhase() {
        ProtectedCashRedemptionTestFixtures.ProductionScenario scenario =
                ProtectedCashRedemptionTestFixtures.productionScenario();
        ProtectedCashRedemptionEvidence intent =
                ProtectedCashRedemptionEvidence.intent(
                        scenario.reservation(), scenario.before());
        ProtectedCashRedemptionEvidence settlement =
                ProtectedCashRedemptionEvidence.settlement(
                        scenario.settlement(), scenario.after());
        ProtectedCashRedemptionEvidence cancellation =
                ProtectedCashRedemptionEvidence.cancellation(
                        scenario.cancellation(), scenario.before());

        assertEvidenceRoundTrip(intent);
        assertEvidenceRoundTrip(settlement);
        assertEvidenceRoundTrip(cancellation);
        assertEquals(scenario.reservation(), intent.reservation());
        assertEquals(scenario.settlement(),
                settlement.settlement().orElseThrow());
        assertEquals(scenario.cancellation(),
                cancellation.cancellation().orElseThrow());
    }

    @Test
    void inventoryStateCoversMainAndOffhandAndRejectsAmbiguity() {
        ProtectedCashRedemptionTestFixtures.ProductionScenario scenario =
                ProtectedCashRedemptionTestFixtures.productionScenario();
        InternalBillInventoryPlanner.SlotIdentity offhand =
                new InternalBillInventoryPlanner.SlotIdentity(
                        InternalBillInventoryPlanner.Container.OFFHAND, 0);

        assertEquals(2, scenario.before().stack(offhand).getCount());
        assertEquals(1, scenario.after().stack(offhand).getCount());
        assertEquals(scenario.before(), ProtectedCashInventoryState.decode(
                scenario.before().encode()));
        assertArrayEquals(scenario.before().encode(),
                ProtectedCashInventoryState.decode(
                        scenario.before().encode()).encode());

        ListTag duplicate = ProtectedCashRedemptionTestFixtures
                .playerInventoryTag(
                        ProtectedCashRedemptionTestFixtures.plan(), false);
        duplicate.add(duplicate.getCompound(duplicate.size() - 1).copy());
        assertThrows(IllegalArgumentException.class, () ->
                ProtectedCashInventoryState.fromPlayerInventoryTag(
                        duplicate));
        assertThrows(IllegalArgumentException.class, () ->
                ProtectedCashInventoryState.decode(Arrays.copyOf(
                        scenario.before().encode(),
                        scenario.before().encode().length + 1)));
    }

    @Test
    void strictOfflineInspectionRecognizesOnlyExactInventoryEvidence()
            throws Exception {
        ProtectedCashRedemptionTestFixtures.ProductionScenario scenario =
                ProtectedCashRedemptionTestFixtures.productionScenario();
        ProtectedCashRedemptionIntentStore store =
                new ProtectedCashRedemptionIntentStore();
        Path playerFile = temporaryDirectory.resolve(
                scenario.reservation().playerId() + ".dat");

        writePlayerFile(playerFile,
                ProtectedCashRedemptionTestFixtures.playerInventoryTag(
                        ProtectedCashRedemptionTestFixtures.plan(), false),
                ProtectedCashRedemptionEvidence.intent(
                        scenario.reservation(), scenario.before()).encode());
        assertEquals(ProtectedCashRedemptionIntentStore.InspectionStatus
                        .INTENT_UNCHANGED,
                store.inspect(playerFile,
                        scenario.reservation().playerId(),
                        scenario.reservation().transactionId()).status());

        byte[] settlement = ProtectedCashRedemptionEvidence.settlement(
                scenario.settlement(), scenario.after()).encode();
        writePlayerFile(playerFile,
                ProtectedCashRedemptionTestFixtures.playerInventoryTag(
                        ProtectedCashRedemptionTestFixtures.plan(), true),
                settlement);
        assertEquals(ProtectedCashRedemptionIntentStore.InspectionStatus
                        .SETTLEMENT_PROVED,
                store.inspect(playerFile,
                        scenario.reservation().playerId(),
                        scenario.reservation().transactionId()).status());

        writePlayerFile(playerFile,
                ProtectedCashRedemptionTestFixtures.playerInventoryTag(
                        ProtectedCashRedemptionTestFixtures.plan(), false),
                settlement);
        assertEquals(ProtectedCashRedemptionIntentStore.InspectionStatus
                        .UNKNOWN,
                store.inspect(playerFile,
                        scenario.reservation().playerId(),
                        scenario.reservation().transactionId()).status());

        byte[] corrupt = settlement.clone();
        corrupt[corrupt.length - 1] ^= 1;
        writePlayerFile(playerFile,
                ProtectedCashRedemptionTestFixtures.playerInventoryTag(
                        ProtectedCashRedemptionTestFixtures.plan(), true),
                corrupt);
        assertEquals(ProtectedCashRedemptionIntentStore.InspectionStatus
                        .UNKNOWN,
                store.inspect(playerFile,
                        scenario.reservation().playerId(),
                        scenario.reservation().transactionId()).status());
    }

    @Test
    void evidenceRejectsTamperingTruncationAndTrailingData() {
        ProtectedCashRedemptionTestFixtures.ProductionScenario scenario =
                ProtectedCashRedemptionTestFixtures.productionScenario();
        byte[] encoded = ProtectedCashRedemptionEvidence.settlement(
                scenario.settlement(), scenario.after()).encode();
        byte[] tampered = encoded.clone();
        tampered[tampered.length - 1] ^= 1;

        assertThrows(IllegalArgumentException.class, () ->
                ProtectedCashRedemptionEvidence.decode(tampered));
        assertThrows(IllegalArgumentException.class, () ->
                ProtectedCashRedemptionEvidence.decode(Arrays.copyOf(
                        encoded, encoded.length - 1)));
        assertThrows(IllegalArgumentException.class, () ->
                ProtectedCashRedemptionEvidence.decode(Arrays.copyOf(
                        encoded, encoded.length + 1)));
    }

    private static void assertEvidenceRoundTrip(
            ProtectedCashRedemptionEvidence evidence
    ) {
        ProtectedCashRedemptionEvidence decoded =
                ProtectedCashRedemptionEvidence.decode(evidence.encode());
        assertEquals(evidence, decoded);
        assertArrayEquals(evidence.encode(), decoded.encode());
    }

    private static void writePlayerFile(
            Path file,
            ListTag inventory,
            byte[] evidence
    ) throws Exception {
        CompoundTag root = new CompoundTag();
        root.put("Inventory", inventory);
        CompoundTag forge = new CompoundTag();
        forge.putByteArray(
                ProtectedCashRedemptionIntentStore.EVIDENCE_KEY, evidence);
        root.put("ForgeData", forge);
        NbtIo.writeCompressed(root, file.toFile());
    }
}
