package com.enviouse.futureshops.server.escrow.mint;

import java.time.Instant;
import java.util.UUID;

@FunctionalInterface
public interface ProtectedMintEvidenceFactory {
    String checksumEvidence(UUID mintId, UUID transactionId, long denominationMinorUnits,
                            int authorizedCount, String serverIdentityEvidence,
                            Instant authorizedAt);
}
