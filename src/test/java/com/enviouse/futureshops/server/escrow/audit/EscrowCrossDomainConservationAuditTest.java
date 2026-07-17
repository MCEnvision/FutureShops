package com.enviouse.futureshops.server.escrow.audit;

import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.claim.ClaimLiabilityCategory;
import com.enviouse.futureshops.server.escrow.claim.ClaimLiabilityEntry;
import com.enviouse.futureshops.server.escrow.claim.ClaimLiabilitySnapshot;
import com.enviouse.futureshops.server.escrow.claim.ClaimSavedData;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.custody.CustodyAdapterCapability;
import com.enviouse.futureshops.server.escrow.custody.CustodyAssetType;
import com.enviouse.futureshops.server.escrow.custody.CustodyEndpointEvidence;
import com.enviouse.futureshops.server.escrow.custody.CustodyItemSnapshot;
import com.enviouse.futureshops.server.escrow.custody.CustodyLiabilitySnapshot;
import com.enviouse.futureshops.server.escrow.custody.CustodyLot;
import com.enviouse.futureshops.server.escrow.custody.CustodyProtectionTier;
import com.enviouse.futureshops.server.escrow.custody.CustodySavedData;
import com.enviouse.futureshops.server.escrow.custody.CustodyTransferEvidence;
import com.enviouse.futureshops.server.escrow.custody.ProtectedCurrencyProvenance;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountType;
import com.enviouse.futureshops.server.escrow.ledger.LedgerLeg;
import com.enviouse.futureshops.server.escrow.ledger.LedgerSavedData;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintBatch;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintEvidenceFactory;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintLiabilitySnapshot;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintSavedData;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EscrowCrossDomainConservationAuditTest {
    private static final Instant NOW = Instant.parse("2026-07-17T12:00:00Z");
    private static final UUID MINT_TRANSACTION = uuid(1L);
    private static final UUID MINT_BATCH = uuid(2L);
    private static final UUID HOLD_TRANSACTION = uuid(3L);
    private static final String SERVER_EVIDENCE = "audit test server";
    private static final ProtectedMintEvidenceFactory EVIDENCE =
            (batchId, transactionId, denomination, authorizedCount, server, authorizedAt) ->
                    "checksum." + batchId + "." + transactionId + "." + denomination
                            + "." + authorizedCount + "." + server + "." + authorizedAt;

    @Test
    void emptyStateConservesAndHasStableFingerprint() {
        LedgerSavedData ledger = new LedgerSavedData();
        ClaimSavedData claims = new ClaimSavedData();
        CustodySavedData custody = new CustodySavedData();
        ProtectedMintSavedData mints = new ProtectedMintSavedData();

        EscrowConservationReport first = EscrowCrossDomainConservationAudit.verify(
                ledger, claims, custody, mints);
        EscrowConservationReport second = EscrowCrossDomainConservationAudit.verify(
                ledger, claims, custody, mints);

        assertTrue(first.conserved());
        assertEquals(0L, first.ledgerNetMinorUnits().orElseThrow());
        assertEquals(3, first.liabilityComparisons().size());
        assertEquals(7, first.unverifiedCategories().size());
        assertEquals(first.deterministicFingerprint(), second.deterministicFingerprint());
    }

    @Test
    void balancedStateCouplesAllAuthoritativeMoneyLiabilities() {
        ProtectedFixture fixture = protectedFixture(2);
        CustodySavedData custody = new CustodySavedData();
        custody.reserveCommitted(walletLot("wallet.reserve", 500L));
        custody.reserveCommitted(protectedLot("cash.reserve", fixture.batch(),
                HOLD_TRANSACTION, 2, fixture.batch().checksumEvidence()));
        custody.reserveCommitted(itemLot("item.reserve", 3));
        custody.reserveCommitted(foreignLot("foreign.reserve", 700L, 7));
        ClaimSavedData claims = new ClaimSavedData();
        createClaim(claims, ClaimKind.MONEY, 30L, new byte[0], false, 10L);
        createClaim(claims, ClaimKind.REFUND, 20L, new byte[0], true, 11L);
        createClaim(claims, ClaimKind.ITEM, 4L, new byte[]{1}, false, 12L);
        LedgerSavedData ledger = balancedLedger(500L, 50L, 1_000L);

        EscrowConservationReport report = EscrowCrossDomainConservationAudit.verify(
                ledger, claims, custody, fixture.mints());

        assertTrue(report.conserved(), () -> report.violations().toString());
        assertTrue(comparison(report,
                EscrowCrossDomainConservationAudit.PLAYER_RESERVED).matches());
        assertTrue(comparison(report,
                EscrowCrossDomainConservationAudit.PLAYER_CLAIM).matches());
        assertTrue(comparison(report,
                EscrowCrossDomainConservationAudit.PROTECTED_CURRENCY_OUTSTANDING).matches());
        ProtectedReservationComparison reservation = report.protectedReservations().get(
                new ProtectedReservationKey(MINT_BATCH, HOLD_TRANSACTION));
        assertEquals(2L, reservation.mintReservedQuantity().orElseThrow());
        assertEquals(2L, reservation.custodyHeldQuantity().orElseThrow());
        assertTrue(reservation.evidenceValid());
        assertEquals(3L, unverified(report, "CUSTODY_ITEMS").units().orElseThrow());
        assertEquals(700L, unverified(report,
                "CUSTODY_FOREIGN_CURRENCY").units().orElseThrow());
        assertEquals(4L, unverified(report, "CLAIM_ITEMS").units().orElseThrow());
    }

    @Test
    void ledgerLiabilityMismatchFailsConservation() {
        CustodySavedData custody = new CustodySavedData();
        custody.reserveCommitted(walletLot("wallet.mismatch", 500L));

        EscrowConservationReport report = EscrowCrossDomainConservationAudit.verify(
                balancedLedger(499L, 0L, 0L), new ClaimSavedData(), custody,
                new ProtectedMintSavedData());

        assertFalse(report.conserved());
        assertFalse(comparison(report,
                EscrowCrossDomainConservationAudit.PLAYER_RESERVED).matches());
        assertTrue(hasCode(report, EscrowConservationViolationCode.LIABILITY_MISMATCH));
    }

    @Test
    void aggregateDoubleReservationAcrossLotsIsDetected() {
        ProtectedFixture fixture = protectedFixture(2);
        CustodySavedData custody = new CustodySavedData();
        custody.reserveCommitted(protectedLot("cash.double.one", fixture.batch(),
                HOLD_TRANSACTION, 2, fixture.batch().checksumEvidence()));
        custody.reserveCommitted(protectedLot("cash.double.two", fixture.batch(),
                HOLD_TRANSACTION, 2, fixture.batch().checksumEvidence()));

        EscrowConservationReport report = EscrowCrossDomainConservationAudit.verify(
                balancedLedger(0L, 0L, 1_000L), new ClaimSavedData(), custody,
                fixture.mints());

        ProtectedReservationComparison reservation = report.protectedReservations().get(
                new ProtectedReservationKey(MINT_BATCH, HOLD_TRANSACTION));
        assertFalse(report.conserved());
        assertEquals(2L, reservation.mintReservedQuantity().orElseThrow());
        assertEquals(4L, reservation.custodyHeldQuantity().orElseThrow());
        assertFalse(reservation.quantityMatches());
        assertTrue(hasCode(report,
                EscrowConservationViolationCode.PROTECTED_RESERVATION_QUANTITY_MISMATCH));
    }

    @Test
    void tamperedProtectedCustodyEvidenceIsDetected() {
        ProtectedFixture fixture = protectedFixture(2);
        CustodySavedData custody = new CustodySavedData();
        custody.reserveCommitted(protectedLot("cash.tampered", fixture.batch(),
                HOLD_TRANSACTION, 2, "tampered checksum"));

        EscrowConservationReport report = EscrowCrossDomainConservationAudit.verify(
                balancedLedger(0L, 0L, 1_000L), new ClaimSavedData(), custody,
                fixture.mints());

        assertFalse(report.conserved());
        assertFalse(report.protectedReservations().get(
                new ProtectedReservationKey(MINT_BATCH, HOLD_TRANSACTION)).evidenceValid());
        assertTrue(hasCode(report,
                EscrowConservationViolationCode.PROTECTED_CUSTODY_CHECKSUM_MISMATCH));
    }

    @Test
    void quarantinedMoneyAndMoneyRefundRemainClaimLiabilities() {
        ClaimSavedData claims = new ClaimSavedData();
        createClaim(claims, ClaimKind.MONEY, 40L, new byte[0], true, 20L);
        createClaim(claims, ClaimKind.REFUND, 10L, new byte[0], true, 21L);

        EscrowConservationReport report = EscrowCrossDomainConservationAudit.verify(
                balancedLedger(0L, 50L, 0L), claims, new CustodySavedData(),
                new ProtectedMintSavedData());

        EscrowLiabilityComparison comparison = comparison(report,
                EscrowCrossDomainConservationAudit.PLAYER_CLAIM);
        assertTrue(report.conserved(), () -> report.violations().toString());
        assertEquals(50L, comparison.authoritativeMinorUnits().orElseThrow());
        assertTrue(comparison.matches());
    }

    @Test
    void aggregationOverflowFailsClosedWithoutWrappedAmount() {
        ClaimLiabilitySnapshot claims = new ClaimLiabilitySnapshot(List.of(
                new ClaimLiabilityEntry(uuid(100L), uuid(101L),
                        ClaimLiabilityCategory.MONEY, ClaimStatus.QUARANTINED,
                        Long.MAX_VALUE),
                new ClaimLiabilityEntry(uuid(102L), uuid(103L),
                        ClaimLiabilityCategory.MONEY_REFUND, ClaimStatus.PENDING,
                        Long.MAX_VALUE)));

        EscrowConservationReport report = EscrowCrossDomainConservationAudit.verify(
                Map.of(), claims, conservedCustodySnapshot(), conservedMintSnapshot());

        assertFalse(report.conserved());
        assertTrue(hasCode(report, EscrowConservationViolationCode.ARITHMETIC_OVERFLOW));
        assertTrue(comparison(report,
                EscrowCrossDomainConservationAudit.PLAYER_CLAIM)
                .authoritativeMinorUnits().isEmpty());
    }

    @Test
    void localConservationCorruptionFailsGlobalAudit() {
        CustodyLiabilitySnapshot custody = new CustodyLiabilitySnapshot(
                List.of(), false, List.of("Custody receipt lineage is corrupt"));
        ProtectedMintLiabilitySnapshot mints = new ProtectedMintLiabilitySnapshot(
                List.of(), false, List.of("Protected mint lineage is corrupt"));

        EscrowConservationReport report = EscrowCrossDomainConservationAudit.verify(
                Map.of(), new ClaimLiabilitySnapshot(List.of()), custody, mints);

        assertFalse(report.conserved());
        assertFalse(report.custodyLocallyConserved());
        assertFalse(report.protectedMintLocallyConserved());
        assertTrue(hasCode(report,
                EscrowConservationViolationCode.CUSTODY_LOCAL_CORRUPTION));
        assertTrue(hasCode(report,
                EscrowConservationViolationCode.PROTECTED_MINT_LOCAL_CORRUPTION));
    }

    private static ProtectedFixture protectedFixture(int reservedQuantity) {
        ProtectedMintBatch batch = ProtectedMintBatch.plan(
                MINT_BATCH, MINT_TRANSACTION, "mint.authorize.audit", 100L, 10,
                SERVER_EVIDENCE, NOW, EVIDENCE);
        ProtectedMintSavedData mints = new ProtectedMintSavedData();
        mints.authorizeCommitted(batch);
        mints.materializeCommitted(MINT_TRANSACTION, MINT_BATCH,
                "mint.materialize.audit", 4, NOW.plusSeconds(1));
        mints.reserveCommitted(HOLD_TRANSACTION, MINT_BATCH,
                "mint.reserve.audit", reservedQuantity, NOW.plusSeconds(2));
        return new ProtectedFixture(mints, batch);
    }

    private static LedgerSavedData balancedLedger(long walletReserved,
                                                  long playerClaim,
                                                  long protectedOutstanding) {
        long total = Math.addExact(walletReserved,
                Math.addExact(playerClaim, protectedOutstanding));
        LedgerSavedData ledger = new LedgerSavedData();
        if (total == 0L) {
            return ledger;
        }
        List<LedgerLeg> legs = new ArrayList<>();
        legs.add(new LedgerLeg(LedgerAccountId.system(LedgerAccountType.ADMIN_SOURCE),
                -total));
        if (walletReserved > 0L) {
            legs.add(new LedgerLeg(new LedgerAccountId(
                    LedgerAccountType.PLAYER_RESERVED, "player"), walletReserved));
        }
        if (playerClaim > 0L) {
            legs.add(new LedgerLeg(new LedgerAccountId(
                    LedgerAccountType.PLAYER_CLAIM, "player"), playerClaim));
        }
        if (protectedOutstanding > 0L) {
            legs.add(new LedgerLeg(LedgerAccountId.system(
                    LedgerAccountType.PROTECTED_CURRENCY_OUTSTANDING),
                    protectedOutstanding));
        }
        ledger.applyCommitted(new LedgerTransaction(uuid(200L + total),
                "audit.ledger." + total, "audit", legs));
        return ledger;
    }

    private static void createClaim(ClaimSavedData claims,
                                    ClaimKind kind,
                                    long units,
                                    byte[] payload,
                                    boolean quarantined,
                                    long id) {
        UUID claimId = uuid(id);
        EscrowClaim claim = new EscrowClaim(
                claimId, uuid(id + 1_000L), uuid(9_000L), "audit.claim." + id,
                kind, units, units, payload, ClaimStatus.PENDING,
                "Audit claim", NOW, NOW);
        claims.createCommitted(claim);
        if (quarantined) {
            claims.quarantineCommitted(claim.ownerId(), claimId, NOW.plusSeconds(1));
        }
    }

    private static CustodyLot walletLot(String requestKey, long units) {
        return CustodyLot.held(uuid(requestKey.hashCode()), uuid(requestKey.hashCode() + 1L),
                requestKey, CustodyAssetType.WALLET_RESERVE,
                CustodyProtectionTier.PROTECTED, units,
                CustodyLot.BUILT_IN_CURRENCY_PROVIDER, List.of(), List.of(),
                evidence(CustodyAdapterCapability.TRANSACTIONAL_PROTECTED, requestKey), NOW);
    }

    private static CustodyLot protectedLot(String requestKey,
                                          ProtectedMintBatch batch,
                                          UUID transactionId,
                                          int billCount,
                                          String checksum) {
        CustodyItemSnapshot item = CustodyItemSnapshot.capture(
                "futureshops:money", billCount, new byte[]{10, 0, 1, 2});
        ProtectedCurrencyProvenance provenance = new ProtectedCurrencyProvenance(
                batch.batchId(), batch.denominationMinorUnits(), batch.authorizedCount(),
                billCount, batch.serverIdentityEvidence(), checksum);
        return CustodyLot.held(uuid(requestKey.hashCode()), transactionId, requestKey,
                CustodyAssetType.PROTECTED_PHYSICAL_CURRENCY,
                CustodyProtectionTier.PROTECTED,
                Math.multiplyExact(batch.denominationMinorUnits(), billCount),
                CustodyLot.BUILT_IN_CURRENCY_PROVIDER, List.of(item), List.of(provenance),
                evidence(CustodyAdapterCapability.RECONCILABLE, requestKey), NOW);
    }

    private static CustodyLot itemLot(String requestKey, int count) {
        CustodyItemSnapshot item = CustodyItemSnapshot.capture(
                "minecraft:diamond", count, new byte[]{10, 0, 3, 4});
        return CustodyLot.held(uuid(requestKey.hashCode()), uuid(requestKey.hashCode() + 1L),
                requestKey, CustodyAssetType.ITEM_STACK, CustodyProtectionTier.RECONCILED,
                count, "", List.of(item), List.of(),
                evidence(CustodyAdapterCapability.RECONCILABLE, requestKey), NOW);
    }

    private static CustodyLot foreignLot(String requestKey, long units, int count) {
        CustodyItemSnapshot item = CustodyItemSnapshot.capture(
                "coinmod:coin", count, new byte[]{10, 0, 5, 6});
        return CustodyLot.held(uuid(requestKey.hashCode()), uuid(requestKey.hashCode() + 1L),
                requestKey, CustodyAssetType.FOREIGN_PHYSICAL_CURRENCY,
                CustodyProtectionTier.UNPROTECTED_FOREIGN, units, "coinmod:coin",
                List.of(item), List.of(),
                evidence(CustodyAdapterCapability.UNPROTECTED_EXTERNAL, requestKey), NOW);
    }

    private static CustodyTransferEvidence evidence(CustodyAdapterCapability capability,
                                                    String token) {
        CustodyEndpointEvidence source = CustodyEndpointEvidence.captured(
                "source", capability, "player", "inventory",
                new byte[]{1}, new byte[]{2}, token + ".source");
        CustodyEndpointEvidence destination = CustodyEndpointEvidence.captured(
                "escrow", CustodyAdapterCapability.TRANSACTIONAL_PROTECTED,
                "escrow", "vault", new byte[]{3}, new byte[]{4},
                token + ".destination");
        return new CustodyTransferEvidence(source, destination);
    }

    private static EscrowLiabilityComparison comparison(EscrowConservationReport report,
                                                        String name) {
        return report.liabilityComparisons().stream()
                .filter(value -> value.liability().equals(name))
                .findFirst().orElseThrow();
    }

    private static EscrowUnverifiedCategory unverified(EscrowConservationReport report,
                                                       String name) {
        return report.unverifiedCategories().stream()
                .filter(value -> value.category().equals(name))
                .findFirst().orElseThrow();
    }

    private static boolean hasCode(EscrowConservationReport report,
                                   EscrowConservationViolationCode code) {
        return report.violations().stream().anyMatch(value -> value.code() == code);
    }

    private static CustodyLiabilitySnapshot conservedCustodySnapshot() {
        return new CustodyLiabilitySnapshot(List.of(), true, List.of());
    }

    private static ProtectedMintLiabilitySnapshot conservedMintSnapshot() {
        return new ProtectedMintLiabilitySnapshot(List.of(), true, List.of());
    }

    private static UUID uuid(long value) {
        return new UUID(0L, value);
    }

    private record ProtectedFixture(ProtectedMintSavedData mints,
                                    ProtectedMintBatch batch) {
    }
}
