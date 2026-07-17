package com.enviouse.futureshops.server.escrow.runtime;

import net.minecraft.server.MinecraftServer;

import java.util.Objects;

public final class EscrowRuntimeStoreBinding {
    private EscrowRuntimeStoreBinding() {
    }

    public static <T extends EscrowManagedSavedData> T bind(
            MinecraftServer server,
            T store
    ) {
        Objects.requireNonNull(server, "server");
        T managedStore = Objects.requireNonNull(store, "store");
        EscrowMutationPermit permit = EscrowRuntimeSavedData.get(server)
                .acquireManagedMutationPermit();
        managedStore.bindManagedMutationPermit(permit);
        return managedStore;
    }
}
