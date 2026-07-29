package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.MinecraftTestBootstrap;
import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.claim.ClaimSavedData;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.item.ExactItemClaimPayload;
import com.enviouse.futureshops.server.escrow.item.ExactItemClaimPayloadCodec;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryBatchEntry;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryBatchPlanner;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationPlan;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationReceipt;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationToken;
import com.enviouse.futureshops.server.escrow.item.ItemInventorySlot;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExactItemClaimDeliveryPlannerTest {
    private static final Instant NOW =
            Instant.parse("2026-07-24T21:32:29.350Z");
    private static final UUID TRANSACTION_ID = UUID.fromString(
            "e05cfde7-cbd8-4e17-b6c1-a7061e57baf2");
    private static final UUID OWNER_ID = UUID.fromString(
            "14722418-c8ac-46d8-bff0-c0021453b213");
    private static final UUID COMPLETED_FIRST_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000001");
    private static final UUID TARGET_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000002");

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void completedUnrelatedClaimDoesNotBlockTheNextClaimReceipt() {
        EscrowClaim completed = claim(
                COMPLETED_FIRST_ID, "output.0", Items.APPLE,
                0L, ClaimStatus.COMPLETED);
        EscrowClaim target = claim(
                TARGET_ID, "output.1", Items.EMERALD,
                1L, ClaimStatus.PENDING);
        ClaimSavedData claims = claims(completed, target);
        ItemInventoryMutationReceipt receipt = receipt(target, 0);

        ExactItemClaimDeliveryCommit matched =
                ExactItemClaimDeliveryPlanner.match(receipt, claims)
                        .orElseThrow();

        assertEquals(target.claimId(), matched.delivery().claimId());
        assertEquals(receipt.token().requestId(), matched.requestId());
    }

    @Test
    void completedTargetStillMatchesItsIdempotentReceipt() {
        EscrowClaim unrelated = claim(
                COMPLETED_FIRST_ID, "output.0", Items.APPLE,
                0L, ClaimStatus.COMPLETED);
        EscrowClaim target = claim(
                TARGET_ID, "output.1", Items.EMERALD,
                1L, ClaimStatus.PENDING);
        ClaimSavedData claims = claims(unrelated, target);
        ItemInventoryMutationReceipt receipt = receipt(target, 0);
        String requestKey = ExactItemClaimDeliveryPlanner.requestKey(
                target.claimId(), receipt.token().requestId());
        claims.deliverCommitted(
                target.ownerId(), target.claimId(), requestKey, 1L, NOW);

        ExactItemClaimDeliveryCommit replay =
                ExactItemClaimDeliveryPlanner.match(receipt, claims)
                        .orElseThrow();

        assertEquals(target.claimId(), replay.delivery().claimId());
        assertEquals(1L, replay.remainingBefore());
    }

    private static ClaimSavedData claims(EscrowClaim... values) {
        ClaimSavedData claims = new ClaimSavedData();
        for (EscrowClaim value : values) {
            if (value.status() == ClaimStatus.COMPLETED) {
                EscrowClaim pending = new EscrowClaim(
                        value.claimId(), value.transactionId(),
                        value.ownerId(), value.sourceKey(), value.kind(),
                        value.originalUnits(), value.originalUnits(),
                        value.payload(), ClaimStatus.PENDING, value.label(),
                        value.createdAt(), value.createdAt());
                claims.createCommitted(pending);
                claims.deliverCommitted(
                        pending.ownerId(), pending.claimId(),
                        "test.completed." + pending.claimId(),
                        pending.originalUnits(), NOW);
            } else {
                claims.createCommitted(value);
            }
        }
        return claims;
    }

    private static EscrowClaim claim(
            UUID claimId,
            String sourceKey,
            net.minecraft.world.item.Item item,
            long remaining,
            ClaimStatus status
    ) {
        ItemStack stack = new ItemStack(item, 1);
        ExactItemClaimPayload payload = ExactItemClaimPayload.capture(
                TRANSACTION_ID, sourceKey, 0, 1, stack);
        return new EscrowClaim(
                claimId, TRANSACTION_ID, OWNER_ID,
                "player.shop." + TRANSACTION_ID + "." + claimId,
                ClaimKind.ITEM, 1L, remaining,
                ExactItemClaimPayloadCodec.encode(payload), status,
                "Server shop offer output", NOW, NOW);
    }

    private static ItemInventoryMutationReceipt receipt(
            EscrowClaim claim,
            int retryIndex
    ) {
        UUID requestId = ExactItemClaimDeliveryPlanner.requestId(
                claim, claim.remainingUnits(), retryIndex);
        ItemStack stack = ExactItemClaimDeliveryPlanner.payload(claim)
                .resolve().resolvedStack().orElseThrow();
        ItemInventoryMutationPlan plan = ItemInventoryBatchPlanner.plan(
                emptyInventory(),
                List.of(ItemInventoryBatchEntry.insert(
                        ExactItemClaimDeliveryPlanner.entryId(requestId),
                        stack)));
        ItemInventoryMutationToken token =
                ItemInventoryMutationToken.create(
                        claim.ownerId(), claim.transactionId(),
                        requestId, plan);
        return ItemInventoryMutationReceipt.create(token, plan, NOW);
    }

    private static ItemInventoryState emptyInventory() {
        return ItemInventoryState.of(
                new ArrayList<>(Collections.nCopies(
                        ItemInventorySlot.MAIN_SLOT_COUNT,
                        ItemStack.EMPTY)),
                ItemStack.EMPTY);
    }
}
