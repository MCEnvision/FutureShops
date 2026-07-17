package com.enviouse.futureshops.server.escrow.claim;

@FunctionalInterface
public interface ClaimDelivery {
    long deliver(EscrowClaim claim, long requestedUnits);
}
