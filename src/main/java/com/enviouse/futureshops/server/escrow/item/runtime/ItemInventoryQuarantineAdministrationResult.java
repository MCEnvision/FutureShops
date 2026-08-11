package com.enviouse.futureshops.server.escrow.item.runtime;

import java.util.Objects;

public record ItemInventoryQuarantineAdministrationResult(
        ItemInventoryQuarantineAdministration administration,
        boolean replayed
) {
    public ItemInventoryQuarantineAdministrationResult {
        Objects.requireNonNull(administration, "administration");
    }
}
