package com.enviouse.futureshops.server.escrow.mint;

import java.time.Instant;
import java.util.UUID;

final class ProtectedMintTestFixtures {
    static final UUID BATCH_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    static final UUID MINT_TRANSACTION = UUID.fromString("20000000-0000-0000-0000-000000000001");
    static final UUID HOLD_TRANSACTION = UUID.fromString("30000000-0000-0000-0000-000000000001");
    static final UUID SECOND_HOLD_TRANSACTION =
            UUID.fromString("30000000-0000-0000-0000-000000000002");
    static final Instant CREATED = Instant.parse("2026-07-17T10:15:30.123456789Z");
    static final String SERVER = "test-server-identity";
    static final ProtectedMintEvidenceFactory EVIDENCE = (mintId, transactionId,
            denomination, authorizedCount, server, authorizedAt) ->
            "checksum." + mintId + "." + transactionId + "." + denomination
                    + "." + authorizedCount + "." + server + "." + authorizedAt;

    private ProtectedMintTestFixtures() {
    }

    static ProtectedMintBatch batch() {
        return ProtectedMintBatch.plan(BATCH_ID, MINT_TRANSACTION, "mint.authorize.1",
                100L, 10, SERVER, CREATED, EVIDENCE);
    }

    static ProtectedMintRepository availableRepository() {
        ProtectedMintRepository repository = new ProtectedMintRepository();
        ProtectedMintBatch batch = batch();
        repository.authorizeCommitted(batch);
        repository.materializeCommitted(MINT_TRANSACTION, BATCH_ID, "mint.materialize.1",
                batch.authorizedCount(), CREATED.plusSeconds(1));
        return repository;
    }
}
