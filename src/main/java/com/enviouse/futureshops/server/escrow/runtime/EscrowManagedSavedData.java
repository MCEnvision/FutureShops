package com.enviouse.futureshops.server.escrow.runtime;

import net.minecraft.world.level.saveddata.SavedData;

import java.util.Objects;

public abstract class EscrowManagedSavedData extends SavedData {
    private EscrowMutationPermit managedMutationPermit;

    public final synchronized void bindManagedMutationPermit(
            EscrowMutationPermit mutationPermit
    ) {
        Objects.requireNonNull(mutationPermit, "mutationPermit");
        if (managedMutationPermit != null && managedMutationPermit != mutationPermit) {
            throw new IllegalStateException(
                    "Escrow saved data is bound to a different mutation permit");
        }
        managedMutationPermit = mutationPermit;
    }

    public final synchronized boolean isManagedRuntimeData() {
        return managedMutationPermit != null;
    }

    protected final synchronized void requireEscrowMutationPermit() {
        if (managedMutationPermit != null && !managedMutationPermit.isActive()) {
            throw new IllegalStateException(
                    "Managed escrow saved data requires a journal mutation permit");
        }
    }
}
