package com.enviouse.futureshops.server.escrow.inventory;

import com.enviouse.futureshops.MinecraftTestBootstrap;
import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.custody.CustodyAdapterCapability;
import com.enviouse.futureshops.server.escrow.custody.CustodyAdapterInspectionStatus;
import com.enviouse.futureshops.server.escrow.custody.CustodyEndpointEvidence;
import com.enviouse.futureshops.server.escrow.custody.CustodyTransferEvidence;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerInventoryDeliveryTest {
    @TempDir
    Path temporaryDirectory;

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void deterministicInsertionMergesBeforeUsingEmptySlots() {
        List<ItemStack> inventory = emptyInventory();
        ItemStack existing = new ItemStack(Items.EMERALD, 60);
        existing.getOrCreateTag().putString("foreign", "preserved");
        inventory.set(0, existing);
        ItemStack incoming = new ItemStack(Items.EMERALD, 8);
        incoming.getOrCreateTag().putString("foreign", "preserved");

        PlayerInventoryInsertionPlan plan =
                PlayerInventoryInsertionPlan.plan(inventory, incoming);

        assertTrue(plan.fullyFits());
        assertEquals(2, plan.changes().size());
        assertEquals(64, plan.resultSlots().get(0).getCount());
        assertEquals(4, plan.resultSlots().get(1).getCount());
        assertEquals(incoming.getTag(),
                plan.resultSlots().get(1).getTag());
        assertFalse(plan.resultSlots().get(1).getTag()
                .contains("futureshops"));
    }

    @Test
    void fullInventoryRejectsWithoutAChangedSlot() {
        List<ItemStack> inventory = new ArrayList<>();
        for (int index = 0;
             index < PlayerInventoryHashes.MAIN_SLOT_COUNT;
             index++) {
            inventory.add(new ItemStack(Items.STONE, 64));
        }

        PlayerInventoryInsertionPlan plan =
                PlayerInventoryInsertionPlan.plan(inventory,
                        new ItemStack(Items.EMERALD, 1));

        assertFalse(plan.fullyFits());
        assertEquals(0, plan.insertedCount());
        assertTrue(plan.changes().isEmpty());
    }

    @Test
    void persistedInventoryAndReceiptJointlyProveDelivery()
            throws Exception {
        List<ItemStack> inventory = emptyInventory();
        ItemStack incoming = new ItemStack(Items.EMERALD, 7);
        incoming.getOrCreateTag().putString("foreign", "exact nbt");
        PlayerInventoryInsertionPlan insertion =
                PlayerInventoryInsertionPlan.plan(inventory, incoming);
        UUID playerId = UUID.randomUUID();
        UUID claimId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        UUID lotId = UUID.randomUUID();
        String requestKey = "cash.claim.delivery." + claimId + "."
                + UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        byte[] assetFingerprint = PlayerInventoryHashes.hashText(
                "foreign asset");
        PlayerInventoryDeliveryToken token =
                PlayerInventoryDeliveryToken.create(playerId, claimId,
                        transactionId, batchId, lotId, requestKey,
                        assetFingerprint, insertion.beforeHash(),
                        insertion.afterHash());
        CustodyEndpointEvidence source = new CustodyEndpointEvidence(
                "futureshops.cash_claim",
                CustodyAdapterCapability.RECONCILABLE,
                playerId.toString(), claimId.toString(),
                PlayerInventoryHashes.hashText("held before"),
                PlayerInventoryHashes.hashText("held after"),
                "held token");
        CustodyEndpointEvidence destination =
                new CustodyEndpointEvidence(
                        "futureshops.player_inventory",
                        CustodyAdapterCapability.RECONCILABLE,
                        playerId.toString(), "inventory.main",
                        insertion.beforeHash(), insertion.afterHash(),
                        token.encode());
        CustodyTransferEvidence evidence = new CustodyTransferEvidence(
                source, destination);
        PlayerInventoryDeliveryReceipt receipt =
                PlayerInventoryDeliveryReceipt.create(token, requestKey,
                        insertion.changes(), evidence,
                        Instant.parse("2026-07-18T12:00:00Z"));
        assertEquals(receipt,
                PlayerInventoryDeliveryReceipt.fromTag(receipt.toTag()));

        CompoundTag root = playerData(insertion.resultSlots(), receipt);
        Path playerFile = temporaryDirectory.resolve(playerId + ".dat");
        NbtIo.writeCompressed(root, playerFile.toFile());
        PlayerInventoryReceiptInspection inspection =
                new PlayerInventoryReceiptStore().inspect(
                        playerFile, token);

        assertEquals(CustodyAdapterInspectionStatus.APPLIED,
                inspection.status());
        assertEquals(receipt, inspection.receipt().orElseThrow());

        CompoundTag tampered = receipt.toTag();
        byte[] digest = tampered.getByteArray("digest");
        digest[0] ^= 1;
        tampered.putByteArray("digest", digest);
        assertThrows(IllegalArgumentException.class,
                () -> PlayerInventoryDeliveryReceipt.fromTag(tampered));
    }

    @Test
    void pruningRemovesOnlyExactCompletedCashClaimReceipts() {
        UUID playerId = UUID.randomUUID();
        Instant deliveredAt = Instant.parse("2026-07-18T13:00:00Z");
        Map<UUID, EscrowClaim> claims = new HashMap<>();
        ListTag receipts = new ListTag();

        EscrowClaim protectedCompleted = pendingClaim(
                playerId, ClaimKind.PROTECTED_CASH, 10L)
                .deliver(10L, deliveredAt);
        EscrowClaim foreignCompleted = pendingClaim(
                playerId, ClaimKind.FOREIGN_CASH, 20L)
                .deliver(20L, deliveredAt);
        EscrowClaim pending = pendingClaim(
                playerId, ClaimKind.PROTECTED_CASH, 30L);
        EscrowClaim partial = pendingClaim(
                playerId, ClaimKind.FOREIGN_CASH, 40L)
                .deliver(10L, deliveredAt);
        EscrowClaim quarantined = pendingClaim(
                playerId, ClaimKind.PROTECTED_CASH, 50L)
                .quarantine(deliveredAt);
        EscrowClaim missing = pendingClaim(
                playerId, ClaimKind.FOREIGN_CASH, 60L);
        EscrowClaim wrongTransaction = pendingClaim(
                playerId, ClaimKind.PROTECTED_CASH, 70L)
                .deliver(70L, deliveredAt);
        EscrowClaim wrongTime = pendingClaim(
                playerId, ClaimKind.FOREIGN_CASH, 80L)
                .deliver(80L, deliveredAt);
        EscrowClaim nonCash = pendingClaim(
                playerId, ClaimKind.ITEM, 90L)
                .deliver(90L, deliveredAt);
        EscrowClaim corruptCompleted = pendingClaim(
                playerId, ClaimKind.PROTECTED_CASH, 100L)
                .deliver(100L, deliveredAt);
        for (EscrowClaim claim : List.of(protectedCompleted,
                foreignCompleted, pending, partial, quarantined,
                wrongTransaction, wrongTime, nonCash,
                corruptCompleted)) {
            claims.put(claim.claimId(), claim);
        }

        receipts.add(receipt(protectedCompleted, playerId,
                protectedCompleted.transactionId(), deliveredAt).toTag());
        receipts.add(receipt(foreignCompleted, playerId,
                foreignCompleted.transactionId(), deliveredAt).toTag());
        receipts.add(receipt(pending, playerId,
                pending.transactionId(), deliveredAt).toTag());
        receipts.add(receipt(partial, playerId,
                partial.transactionId(), deliveredAt).toTag());
        receipts.add(receipt(quarantined, playerId,
                quarantined.transactionId(), deliveredAt).toTag());
        receipts.add(receipt(missing, playerId,
                missing.transactionId(), deliveredAt).toTag());
        receipts.add(receipt(wrongTransaction, playerId,
                UUID.randomUUID(), deliveredAt).toTag());
        receipts.add(receipt(wrongTime, playerId,
                wrongTime.transactionId(), deliveredAt.plusSeconds(1))
                .toTag());
        receipts.add(receipt(nonCash, playerId,
                nonCash.transactionId(), deliveredAt).toTag());
        CompoundTag corrupt = receipt(corruptCompleted, playerId,
                corruptCompleted.transactionId(), deliveredAt).toTag();
        byte[] corruptDigest = corrupt.getByteArray("digest");
        corruptDigest[0] ^= 1;
        corrupt.putByteArray("digest", corruptDigest);
        receipts.add(corrupt);

        CompoundTag persistent = new CompoundTag();
        persistent.put(PlayerInventoryReceiptStore.RECEIPTS_KEY, receipts);
        int pruned = new PlayerInventoryReceiptStore()
                .pruneCompletedCashClaims(
                        persistent, playerId, claims::get);

        assertEquals(2, pruned);
        ListTag retained = persistent.getList(
                PlayerInventoryReceiptStore.RECEIPTS_KEY,
                net.minecraft.nbt.Tag.TAG_COMPOUND);
        assertEquals(8, retained.size());
        Set<UUID> retainedClaims = new HashSet<>();
        boolean corruptRetained = false;
        for (int index = 0; index < retained.size(); index++) {
            CompoundTag tag = retained.getCompound(index);
            retainedClaims.add(tag.getUUID("claim"));
            corruptRetained |= tag.equals(corrupt);
        }
        assertFalse(retainedClaims.contains(
                protectedCompleted.claimId()));
        assertFalse(retainedClaims.contains(
                foreignCompleted.claimId()));
        assertTrue(retainedClaims.contains(pending.claimId()));
        assertTrue(retainedClaims.contains(partial.claimId()));
        assertTrue(retainedClaims.contains(quarantined.claimId()));
        assertTrue(retainedClaims.contains(missing.claimId()));
        assertTrue(retainedClaims.contains(
                wrongTransaction.claimId()));
        assertTrue(retainedClaims.contains(wrongTime.claimId()));
        assertTrue(retainedClaims.contains(nonCash.claimId()));
        assertTrue(corruptRetained);
    }

    @Test
    void pruningPreservesMalformedReceiptStorage() {
        CompoundTag persistent = new CompoundTag();
        persistent.putString(PlayerInventoryReceiptStore.RECEIPTS_KEY,
                "malformed evidence");

        int pruned = new PlayerInventoryReceiptStore()
                .pruneCompletedCashClaims(persistent, UUID.randomUUID(),
                        ignored -> null);

        assertEquals(0, pruned);
        assertEquals("malformed evidence", persistent.getString(
                PlayerInventoryReceiptStore.RECEIPTS_KEY));
    }

    private static CompoundTag playerData(
            List<ItemStack> inventory,
            PlayerInventoryDeliveryReceipt receipt
    ) {
        CompoundTag root = new CompoundTag();
        ListTag slots = new ListTag();
        for (int index = 0; index < inventory.size(); index++) {
            ItemStack stack = inventory.get(index);
            if (stack.isEmpty()) {
                continue;
            }
            CompoundTag entry = stack.save(new CompoundTag());
            entry.putByte("Slot", (byte) index);
            slots.add(entry);
        }
        root.put("Inventory", slots);
        CompoundTag forgeData = new CompoundTag();
        ListTag receipts = new ListTag();
        receipts.add(receipt.toTag());
        forgeData.put(PlayerInventoryReceiptStore.RECEIPTS_KEY, receipts);
        root.put("ForgeData", forgeData);
        return root;
    }

    private static List<ItemStack> emptyInventory() {
        List<ItemStack> inventory = new ArrayList<>();
        for (int index = 0;
             index < PlayerInventoryHashes.MAIN_SLOT_COUNT;
             index++) {
            inventory.add(ItemStack.EMPTY);
        }
        return inventory;
    }

    private static EscrowClaim pendingClaim(
            UUID playerId,
            ClaimKind kind,
            long units
    ) {
        UUID claimId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-07-18T12:00:00Z");
        return new EscrowClaim(claimId, UUID.randomUUID(), playerId,
                "receipt.test." + claimId, kind, units, units,
                new byte[]{1}, ClaimStatus.PENDING, "Receipt test claim",
                createdAt, createdAt);
    }

    private static PlayerInventoryDeliveryReceipt receipt(
            EscrowClaim claim,
            UUID playerId,
            UUID transactionId,
            Instant deliveredAt
    ) {
        ItemStack incoming = new ItemStack(Items.EMERALD, 1);
        PlayerInventoryInsertionPlan insertion =
                PlayerInventoryInsertionPlan.plan(
                        emptyInventory(), incoming);
        UUID batchId = UUID.randomUUID();
        UUID lotId = UUID.randomUUID();
        String requestKey = "cash.claim.delivery."
                + claim.claimId() + "." + UUID.randomUUID();
        PlayerInventoryDeliveryToken token =
                PlayerInventoryDeliveryToken.create(playerId,
                        claim.claimId(), transactionId, batchId, lotId,
                        requestKey, PlayerInventoryHashes.hashText(
                                "receipt asset " + claim.claimId()),
                        insertion.beforeHash(), insertion.afterHash());
        CustodyEndpointEvidence source = new CustodyEndpointEvidence(
                "futureshops.cash_claim",
                CustodyAdapterCapability.RECONCILABLE,
                playerId.toString(), claim.claimId().toString(),
                PlayerInventoryHashes.hashText("receipt held before"),
                PlayerInventoryHashes.hashText("receipt held after"),
                "receipt held token");
        CustodyEndpointEvidence destination =
                new CustodyEndpointEvidence(
                        "futureshops.player_inventory",
                        CustodyAdapterCapability.RECONCILABLE,
                        playerId.toString(), "inventory.main",
                        insertion.beforeHash(), insertion.afterHash(),
                        token.encode());
        return PlayerInventoryDeliveryReceipt.create(token, requestKey,
                insertion.changes(), new CustodyTransferEvidence(
                        source, destination), deliveredAt);
    }
}
