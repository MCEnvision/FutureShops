package com.enviouse.futureshops.server.escrow.item;

import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.Optional;

public final class ExactItemClaimResolution {
    private final ExactItemClaimResolutionStatus status;
    private final ItemStack resolvedStack;
    private final MissingItemSnapshot missingSnapshot;

    private ExactItemClaimResolution(
            ExactItemClaimResolutionStatus status,
            ItemStack resolvedStack,
            MissingItemSnapshot missingSnapshot
    ) {
        this.status = Objects.requireNonNull(status, "status");
        this.resolvedStack = resolvedStack == null
                ? null : resolvedStack.copy();
        this.missingSnapshot = missingSnapshot;
        if (status == ExactItemClaimResolutionStatus.RESOLVED
                && (this.resolvedStack == null
                || this.resolvedStack.isEmpty()
                || missingSnapshot != null)
                || status == ExactItemClaimResolutionStatus.MISSING
                && (this.resolvedStack != null || missingSnapshot == null)) {
            throw new IllegalArgumentException(
                    "Exact item claim resolution is invalid");
        }
    }

    static ExactItemClaimResolution resolved(ItemStack stack) {
        return new ExactItemClaimResolution(
                ExactItemClaimResolutionStatus.RESOLVED, stack, null);
    }

    static ExactItemClaimResolution missing(
            ExactItemClaimPayload payload
    ) {
        return new ExactItemClaimResolution(
                ExactItemClaimResolutionStatus.MISSING, null,
                new MissingItemSnapshot(payload));
    }

    public ExactItemClaimResolutionStatus status() {
        return status;
    }

    public Optional<ItemStack> resolvedStack() {
        return resolvedStack == null ? Optional.empty()
                : Optional.of(resolvedStack.copy());
    }

    public Optional<MissingItemSnapshot> missingSnapshot() {
        return Optional.ofNullable(missingSnapshot);
    }
}
