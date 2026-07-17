package com.enviouse.futureshops.server.escrow.mint;

import com.enviouse.futureshops.server.escrow.custody.CustodyAdapterCapability;
import com.enviouse.futureshops.server.escrow.custody.CustodyAssetType;
import com.enviouse.futureshops.server.escrow.custody.CustodyEndpointEvidence;
import com.enviouse.futureshops.server.escrow.custody.CustodyItemSnapshot;
import com.enviouse.futureshops.server.escrow.custody.CustodyLot;
import com.enviouse.futureshops.server.escrow.custody.CustodyProtectionTier;
import com.enviouse.futureshops.server.escrow.custody.CustodyTransferEvidence;
import com.enviouse.futureshops.server.escrow.custody.ProtectedCurrencyProvenance;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtectedCurrencyCustodyValidatorTest {
    @Test
    void custodyUsesExactMintBatchQuantityAndChecksumEvidence() {
        ProtectedMintBatch batch = ProtectedMintTestFixtures.batch();
        ProtectedMintSavedData mints = new ProtectedMintSavedData();
        mints.authorizeCommitted(batch);
        mints.materializeCommitted(batch.transactionId(), batch.batchId(),
                "custody materialize", batch.authorizedCount(),
                batch.authorizedAt().plusSeconds(1));
        CustodyLot lot = protectedLot(batch, 4, batch.checksumEvidence());

        ProtectedCustodyValidationResult available =
                ProtectedCurrencyCustodyValidator.validate(lot, mints, Optional.empty());

        assertTrue(available.valid());
        assertEquals(4, available.validatedBillCount());
        assertEquals(400L, available.validatedMinorUnits());

        mints.reserveCommitted(ProtectedMintTestFixtures.HOLD_TRANSACTION,
                batch.batchId(), "custody reserve", 4,
                batch.authorizedAt().plusSeconds(2));
        assertFalse(ProtectedCurrencyCustodyValidator.validate(
                protectedLot(batch, 7, batch.checksumEvidence()),
                mints, Optional.empty()).valid());
        assertTrue(ProtectedCurrencyCustodyValidator.validate(lot, mints,
                Optional.of(ProtectedMintTestFixtures.HOLD_TRANSACTION)).valid());
        ProtectedCustodyValidationResult tampered =
                ProtectedCurrencyCustodyValidator.validate(
                        protectedLot(batch, 4, "wrong checksum"), mints,
                        Optional.of(ProtectedMintTestFixtures.HOLD_TRANSACTION));
        assertEquals(ProtectedMintValidationCode.CHECKSUM_MISMATCH,
                tampered.resultsByBatch().get(batch.batchId()));
    }

    private static CustodyLot protectedLot(ProtectedMintBatch batch, int quantity,
                                           String checksumEvidence) {
        CustodyItemSnapshot snapshot = CustodyItemSnapshot.capture(
                "futureshops:money", quantity, new byte[]{10, 0, 1});
        ProtectedCurrencyProvenance provenance = new ProtectedCurrencyProvenance(
                batch.batchId(), batch.denominationMinorUnits(), batch.authorizedCount(),
                quantity, batch.serverIdentityEvidence(), checksumEvidence);
        CustodyEndpointEvidence source = CustodyEndpointEvidence.captured(
                "player_inventory", CustodyAdapterCapability.RECONCILABLE,
                "player", "inventory", new byte[]{1}, new byte[]{2}, "source mutation");
        CustodyEndpointEvidence destination = CustodyEndpointEvidence.captured(
                "escrow_vault", CustodyAdapterCapability.TRANSACTIONAL_PROTECTED,
                "server", "vault", new byte[]{3}, new byte[]{4}, "destination mutation");
        return CustodyLot.held(UUID.randomUUID(), UUID.randomUUID(), "protected custody",
                CustodyAssetType.PROTECTED_PHYSICAL_CURRENCY,
                CustodyProtectionTier.PROTECTED,
                Math.multiplyExact(batch.denominationMinorUnits(), (long) quantity),
                CustodyLot.BUILT_IN_CURRENCY_PROVIDER, List.of(snapshot),
                List.of(provenance), new CustodyTransferEvidence(source, destination),
                batch.authorizedAt().plusSeconds(2));
    }
}
