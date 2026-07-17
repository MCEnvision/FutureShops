package com.enviouse.futureshops.server.escrow.runtime;

public interface EscrowMaintenanceLiveGuard {
    boolean journalHealthyAndAligned();

    boolean domainMaintenanceActive();

    boolean recoveryClear();

    boolean conservationVerified();

    EscrowGlobalVerificationSnapshot globalVerification();
}
