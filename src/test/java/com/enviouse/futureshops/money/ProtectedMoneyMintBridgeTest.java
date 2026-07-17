package com.enviouse.futureshops.money;

import com.enviouse.futureshops.Config;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintBatch;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProtectedMoneyMintBridgeTest {
    @Test
    void plannedBatchUsesTheExistingMoneyItemChecksumRepresentation() {
        String priorServerId = Config.moneyMintServerId;
        Config.moneyMintServerId = "bridge test server";
        try {
            UUID transactionId = UUID.fromString("50000000-0000-0000-0000-000000000001");
            Instant authorizedAt = Instant.parse("2026-07-16T12:34:56Z");
            ProtectedMintBatch batch = ProtectedMoneyMintBridge.plan(transactionId,
                    "bridge plan", 500L, 12, authorizedAt);
            String expected = MoneyChecksumService.createChecksum(500L,
                    batch.batchId().toString(), authorizedAt.getEpochSecond(),
                    transactionId.toString(), "bridge test server", 12);

            assertEquals(expected, batch.checksumEvidence());
            assertEquals("bridge test server", batch.serverIdentityEvidence());
            assertEquals(12, batch.authorizedQuantity());
        } finally {
            Config.moneyMintServerId = priorServerId;
        }
    }

    @Test
    void materializationKeepsCommittedEvidenceAfterSaltRotation() {
        String priorServerId = Config.moneyMintServerId;
        String priorSalt = Config.moneyChecksumSalt;
        Config.moneyMintServerId = "bridge rotation server";
        Config.moneyChecksumSalt = "bridge original salt";
        try {
            ProtectedMintBatch batch = ProtectedMoneyMintBridge.plan(
                    UUID.randomUUID(), "bridge rotation", 250L, 8,
                    Instant.parse("2026-07-18T12:00:00Z"))
                    .materialize(8,
                            Instant.parse("2026-07-18T12:00:00Z"));
            String committedChecksum = batch.checksumEvidence();

            Config.moneyChecksumSalt = "bridge rotated salt";

            assertEquals(committedChecksum,
                    ProtectedMoneyMintBridge.materializedMoneyData(batch, 4)
                            .getString(MoneyNbtKeys.CHECKSUM));
            assertEquals(batch.batchId().toString(),
                    ProtectedMoneyMintBridge.materializedMoneyData(batch, 4)
                            .getString(MoneyNbtKeys.MINT_ID));
        } finally {
            Config.moneyMintServerId = priorServerId;
            Config.moneyChecksumSalt = priorSalt;
        }
    }
}
