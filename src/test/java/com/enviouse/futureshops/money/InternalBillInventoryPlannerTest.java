package com.enviouse.futureshops.money;

import com.enviouse.futureshops.Config;
import com.enviouse.futureshops.MinecraftTestBootstrap;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintBatch;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintEvidenceFactory;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintSavedData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InternalBillInventoryPlannerTest {
    private static final Instant NOW = Instant.parse("2026-07-17T18:00:00Z");
    private static final Instant ISSUED_AT = NOW.minusSeconds(10);
    private static final String SERVER = "authority-test-server";
    private static final String SALT = "authority-test-salt";
    private static final ProtectedMintEvidenceFactory EVIDENCE =
            (batchId, transactionId, denominationMinorUnits, authorizedCount,
             serverIdentityEvidence, authorizedAt) -> MoneyChecksumService.createChecksum(
                    denominationMinorUnits, batchId.toString(),
                    authorizedAt.getEpochSecond(), transactionId.toString(),
                    serverIdentityEvidence, authorizedCount);

    private String priorSalt;
    private int priorMaximumAgeDays;

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.initialize();
    }

    @BeforeEach
    void configureMoneyValidation() {
        priorSalt = Config.moneyChecksumSalt;
        priorMaximumAgeDays = Config.moneyMaxAgeDays;
        Config.moneyChecksumSalt = SALT;
        Config.moneyMaxAgeDays = 30;
    }

    @AfterEach
    void restoreMoneyValidation() {
        Config.moneyChecksumSalt = priorSalt;
        Config.moneyMaxAgeDays = priorMaximumAgeDays;
    }

    @Test
    void protectedLookupPrecedesLegacyFallback() {
        ProtectedFixture fixture = protectedFixture("router", 100L, 5);
        SpentMintsSavedData legacy = new SpentMintsSavedData();
        String legacyId = "51000000-0000-0000-0000-000000000001";
        UUID legacyPlayer = UUID.fromString(
                "52000000-0000-0000-0000-000000000001");
        legacy.registerMint(legacyId, legacyPlayer, 25L, 3,
                ISSUED_AT.getEpochSecond(), SERVER);
        InternalBillAuthorityRouter router = router(fixture.mints(), legacy);

        InternalBillAuthorityRouter.Resolution protectedResult = router.resolve(
                protectedBill(fixture.batch(), 4));
        InternalBillAuthorityRouter.Resolution legacyResult = router.resolve(
                bill(legacyId, legacyPlayer.toString(), 25L, 3, 2,
                        ISSUED_AT.getEpochSecond(), SERVER));

        assertEquals(InternalBillAuthorityRouter.Authority.PROTECTED,
                protectedResult.authority());
        assertEquals(InternalBillAuthorityRouter.Status.VALID,
                protectedResult.status());
        assertEquals(5, protectedResult.authorityAvailableCount());
        assertEquals(InternalBillAuthorityRouter.Authority.LEGACY,
                legacyResult.authority());
        assertEquals(InternalBillAuthorityRouter.Status.VALID,
                legacyResult.status());
        assertEquals(3, legacyResult.authorityAvailableCount());
    }

    @Test
    void collisionAndKnownProtectedMismatchFailClosed() {
        ProtectedFixture collisionFixture = protectedFixture("collision", 100L, 4);
        SpentMintsSavedData collisionLegacy = new SpentMintsSavedData();
        ProtectedMintBatch collisionBatch = collisionFixture.batch();
        collisionLegacy.registerMint(collisionBatch.batchId().toString(),
                collisionBatch.transactionId(), collisionBatch.denominationMinorUnits(),
                collisionBatch.authorizedCount(), collisionBatch.authorizedAt().getEpochSecond(),
                collisionBatch.serverIdentityEvidence());
        InternalBillAuthorityRouter.Resolution collision = router(
                collisionFixture.mints(), collisionLegacy).resolve(
                protectedBill(collisionBatch, 1));

        assertEquals(InternalBillAuthorityRouter.Authority.NONE,
                collision.authority());
        assertEquals(InternalBillAuthorityRouter.Status.CROSS_STORE_COLLISION,
                collision.status());
        assertFalse(collision.spendable());

        ProtectedFixture mismatchFixture = protectedFixture("mismatch", 100L, 4);
        ProtectedMintBatch mismatchBatch = mismatchFixture.batch();
        ItemStack mismatched = bill(mismatchBatch.batchId().toString(),
                mismatchBatch.transactionId().toString(), 200L,
                mismatchBatch.authorizedCount(), 1,
                mismatchBatch.authorizedAt().getEpochSecond(),
                mismatchBatch.serverIdentityEvidence());
        InternalBillAuthorityRouter.Resolution mismatch = router(
                mismatchFixture.mints(), new SpentMintsSavedData()).resolve(mismatched);

        assertEquals(InternalBillAuthorityRouter.Authority.PROTECTED,
                mismatch.authority());
        assertEquals(InternalBillAuthorityRouter.Status.EVIDENCE_MISMATCH,
                mismatch.status());
        assertFalse(mismatch.spendable());
    }

    @Test
    void protectedEvidenceSurvivesSaltRotationAndAlterationStillFails() {
        ProtectedFixture fixture = protectedFixture("salt", 100L, 4);
        ItemStack original = protectedBill(fixture.batch(), 2);
        ItemStack altered = original.copy();
        altered.getTag().getCompound(MoneyNbtKeys.ROOT)
                .putString(MoneyNbtKeys.MINT_PLAYER, UUID.randomUUID().toString());

        Config.moneyChecksumSalt = "rotated-authority-test-salt";
        InternalBillAuthorityRouter router = router(
                fixture.mints(), new SpentMintsSavedData());
        InternalBillAuthorityRouter.Resolution accepted = router.resolve(original);
        InternalBillAuthorityRouter.Resolution rejected = router.resolve(altered);

        assertEquals(InternalBillAuthorityRouter.Status.VALID, accepted.status());
        assertEquals(InternalBillAuthorityRouter.Authority.PROTECTED,
                accepted.authority());
        assertEquals(InternalBillAuthorityRouter.Status.EVIDENCE_MISMATCH,
                rejected.status());
        assertEquals(InternalBillAuthorityRouter.Authority.PROTECTED,
                rejected.authority());
        assertFalse(rejected.spendable());
    }

    @Test
    void protectedBudgetCapsClonesAcrossMainInventoryAndOffhand() {
        ProtectedFixture fixture = protectedFixture("budget", 100L, 5);
        ItemStack mainStack = protectedBill(fixture.batch(), 4);
        ItemStack offhandStack = protectedBill(fixture.batch(), 4);
        InternalBillInventoryPlanner planner = new InternalBillInventoryPlanner(
                router(fixture.mints(), new SpentMintsSavedData()));

        InternalBillInventoryPlanner.ExactPlan first = planner.planExact(
                List.of(mainStack), List.of(offhandStack), 500L);
        InternalBillInventoryPlanner.ExactPlan second = planner.planExact(
                List.of(mainStack), List.of(offhandStack), 500L);
        InternalBillInventoryPlanner.ExactPlan overBudget = planner.planExact(
                List.of(mainStack), List.of(offhandStack), 600L);

        assertTrue(first.successful());
        assertEquals(first, second);
        assertEquals(InternalBillAuthorityRouter.Authority.PROTECTED,
                first.authority());
        assertEquals(2, first.portions().size());
        assertEquals(new InternalBillInventoryPlanner.SlotIdentity(
                        InternalBillInventoryPlanner.Container.MAIN, 0),
                first.portions().get(0).slot());
        assertEquals(4, first.portions().get(0).selectedCount());
        assertEquals(new InternalBillInventoryPlanner.SlotIdentity(
                        InternalBillInventoryPlanner.Container.OFFHAND, 0),
                first.portions().get(1).slot());
        assertEquals(1, first.portions().get(1).selectedCount());
        assertEquals(4, first.portions().get(1).originalStackCount());

        ItemStack capturedOffhand = ItemStackSnapshotCodec.decode(
                first.portions().get(1).exactStackSnapshot());
        assertEquals(4, capturedOffhand.getCount());
        assertEquals(offhandStack.getTag(), capturedOffhand.getTag());
        assertEquals(InternalBillInventoryPlanner.PlanStatus.NO_EXACT_SELECTION,
                overBudget.status());
        assertTrue(overBudget.portions().isEmpty());
        assertEquals(4, mainStack.getCount());
        assertEquals(4, offhandStack.getCount());
    }

    @Test
    void mixedAuthorityExactSelectionReturnsAnEmptyFailure() {
        ProtectedFixture fixture = protectedFixture("mixed", 100L, 1);
        String legacyId = "53000000-0000-0000-0000-000000000001";
        UUID legacyPlayer = UUID.fromString(
                "54000000-0000-0000-0000-000000000001");
        SpentMintsSavedData legacy = new SpentMintsSavedData();
        legacy.registerMint(legacyId, legacyPlayer, 50L, 1,
                ISSUED_AT.getEpochSecond(), SERVER);
        ItemStack protectedStack = protectedBill(fixture.batch(), 1);
        ItemStack legacyStack = bill(legacyId, legacyPlayer.toString(),
                50L, 1, 1, ISSUED_AT.getEpochSecond(), SERVER);
        InternalBillInventoryPlanner planner = new InternalBillInventoryPlanner(
                router(fixture.mints(), legacy));

        InternalBillInventoryPlanner.ExactPlan plan = planner.planExact(
                List.of(protectedStack, legacyStack), List.of(), 150L);

        assertEquals(InternalBillInventoryPlanner.PlanStatus.MIXED_AUTHORITIES_REQUIRED,
                plan.status());
        assertFalse(plan.successful());
        assertTrue(plan.portions().isEmpty());
        assertEquals(0L, plan.selectedMinorUnits());
        assertEquals(1, protectedStack.getCount());
        assertEquals(1, legacyStack.getCount());
    }

    private static InternalBillAuthorityRouter router(
            ProtectedMintSavedData protectedMints,
            SpentMintsSavedData legacyMints) {
        return new InternalBillAuthorityRouter(
                protectedMints, legacyMints, Items.PAPER, NOW);
    }

    private static ProtectedFixture protectedFixture(String key,
                                                       long denomination,
                                                       int count) {
        UUID batchId = UUID.nameUUIDFromBytes(("batch." + key).getBytes(
                java.nio.charset.StandardCharsets.UTF_8));
        UUID transactionId = UUID.nameUUIDFromBytes(("transaction." + key).getBytes(
                java.nio.charset.StandardCharsets.UTF_8));
        String requestKey = "authority." + key;
        ProtectedMintBatch batch = ProtectedMintBatch.plan(batchId, transactionId,
                requestKey, denomination, count, SERVER, ISSUED_AT, EVIDENCE);
        ProtectedMintSavedData mints = new ProtectedMintSavedData();
        mints.authorizeCommitted(batch);
        mints.materializeCommitted(transactionId, batchId,
                requestKey + ".materialize", count, ISSUED_AT.plusSeconds(1));
        return new ProtectedFixture(mints, mints.getBatch(batchId));
    }

    private static ItemStack protectedBill(ProtectedMintBatch batch, int count) {
        return bill(batch.batchId().toString(), batch.transactionId().toString(),
                batch.denominationMinorUnits(), batch.authorizedCount(), count,
                batch.authorizedAt().getEpochSecond(), batch.serverIdentityEvidence());
    }

    private static ItemStack bill(String mintId,
                                  String mintPlayer,
                                  long denomination,
                                  int authorizedCount,
                                  int count,
                                  long mintedAt,
                                  String server) {
        ItemStack stack = new ItemStack(Items.PAPER, count);
        CompoundTag data = new CompoundTag();
        data.putLong(MoneyNbtKeys.DENOMINATION, denomination);
        data.putString(MoneyNbtKeys.MINT_ID, mintId);
        data.putLong(MoneyNbtKeys.MINT_TIMESTAMP, mintedAt);
        data.putString(MoneyNbtKeys.MINT_PLAYER, mintPlayer);
        data.putString(MoneyNbtKeys.MINT_SERVER, server);
        data.putInt(MoneyNbtKeys.AUTHORIZED_COUNT, authorizedCount);
        data.putString(MoneyNbtKeys.CHECKSUM, MoneyChecksumService.createChecksum(
                denomination, mintId, mintedAt, mintPlayer, server, authorizedCount));
        stack.getOrCreateTag().put(MoneyNbtKeys.ROOT, data);
        return stack;
    }

    private record ProtectedFixture(ProtectedMintSavedData mints,
                                    ProtectedMintBatch batch) {
    }
}
