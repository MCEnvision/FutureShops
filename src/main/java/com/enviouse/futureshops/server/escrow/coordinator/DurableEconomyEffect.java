package com.enviouse.futureshops.server.escrow.coordinator;

import java.util.Optional;

/** Narrow provider seam used by the durable coordinator and deterministic fixtures. */
public interface DurableEconomyEffect {
    DispatchResult dispatch(BoundLeg leg);

    Optional<DispatchReceipt> lookup(BoundLeg leg);
}
