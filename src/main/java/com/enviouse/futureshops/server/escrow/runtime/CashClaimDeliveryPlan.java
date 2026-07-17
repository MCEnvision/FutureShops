package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.custody.CustodyBatchPlan;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

public final class CashClaimDeliveryPlan {
    private final EscrowClaim claim;
    private final ItemStack deliveredStack;
    private final CustodyBatchPlan custodyPlan;

    CashClaimDeliveryPlan(
            EscrowClaim claim,
            ItemStack deliveredStack,
            CustodyBatchPlan custodyPlan
    ) {
        this.claim = Objects.requireNonNull(claim, "claim");
        this.deliveredStack = Objects.requireNonNull(
                deliveredStack, "deliveredStack").copy();
        this.custodyPlan = Objects.requireNonNull(
                custodyPlan, "custodyPlan");
    }

    public EscrowClaim claim() {
        return claim;
    }

    public ItemStack deliveredStack() {
        return deliveredStack.copy();
    }

    public CustodyBatchPlan custodyPlan() {
        return custodyPlan;
    }
}
