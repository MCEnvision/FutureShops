package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.claim.ClaimSavedData;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.journal.JournalRecord;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountType;
import com.enviouse.futureshops.server.escrow.ledger.LedgerLeg;
import com.enviouse.futureshops.server.escrow.ledger.LedgerSavedData;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;
import com.enviouse.futureshops.server.escrow.store.EscrowTransactionSavedData;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoneyClaimLegacyCodecTest {
    private static final Instant NOW = Instant.parse(
            "2026-07-17T12:00:00Z");

    @Test
    void validLegacyV1WalReplaysWithoutUsingInventedSnapshots()
            throws Exception {
        Fixture fixture = Fixture.create();
        byte[] encoded = encodeLegacy(
                fixture.delivery(), fixture.settlementLedger());
        MoneyClaimSettlement decoded =
                MoneyClaimSettlementCodec.decode(encoded);
        EscrowJournalEvent event = new EscrowJournalEvent(
                EscrowJournalEventType.MONEY_CLAIM_SETTLEMENT,
                encoded);

        assertTrue(decoded.legacyFormat());
        assertEquals(MoneyClaimSettlement.LEGACY_FORMAT_VERSION,
                decoded.formatVersion());
        assertEquals(fixture.requestId(), decoded.requestId());
        assertEquals(EscrowPreflightResult.APPLY,
                fixture.applier().preflight(
                        fixture.requestId(), event));

        fixture.applier().apply(new JournalRecord(
                1L, fixture.requestId(),
                EscrowStepIds.forEvent(fixture.requestId(), event),
                EscrowJournalEventCodec.encode(event)), event);

        assertEquals(90L, fixture.ledger().balance(
                PlayerPaymentCommit.walletAccount(fixture.ownerId())));
        assertEquals(60L, fixture.ledger().balance(
                fixture.claimAccount()));
        assertEquals(60L, fixture.claims().getClaim(
                fixture.claimId()).remainingUnits());
        assertTrue(fixture.claims().attempt(
                fixture.delivery().requestKey()).isPresent());
        assertEquals(EscrowPreflightResult.REPLAY,
                fixture.applier().preflight(
                        fixture.requestId(), event));
    }

    @Test
    void currentEncoderWritesOnlyV2AndWillNotRewriteLegacy()
            throws Exception {
        Fixture fixture = Fixture.create();
        MoneyClaimSettlement current = MoneyClaimSettlement.create(
                UUID.randomUUID(), fixture.ownerId(), fixture.claimId(),
                0L, 0L, 0L, 100L, 100L, 1L, NOW);
        byte[] currentBytes = MoneyClaimSettlementCodec.encode(current);
        MoneyClaimSettlement legacy = MoneyClaimSettlementCodec.decode(
                encodeLegacy(fixture.delivery(),
                        fixture.settlementLedger()));

        assertEquals(MoneyClaimSettlement.CURRENT_FORMAT_VERSION,
                readVersion(currentBytes));
        assertFalse(MoneyClaimSettlementCodec.decode(
                currentBytes).legacyFormat());
        assertThrows(IllegalArgumentException.class,
                () -> MoneyClaimSettlementCodec.encode(legacy));
    }

    @Test
    void malformedAndAmbiguousLegacyV1EvidenceFailsClosed()
            throws Exception {
        Fixture fixture = Fixture.create();
        byte[] valid = encodeLegacy(
                fixture.delivery(), fixture.settlementLedger());
        byte[] trailing = Arrays.copyOf(valid, valid.length + 1);
        LedgerTransaction wrongClaim = new LedgerTransaction(
                fixture.requestId(), fixture.delivery().requestKey(),
                "Legacy wrong claim", List.of(
                new LedgerLeg(new LedgerAccountId(
                        LedgerAccountType.PLAYER_CLAIM,
                        UUID.randomUUID().toString()), -40L),
                new LedgerLeg(PlayerPaymentCommit.walletAccount(
                        fixture.ownerId()), 40L)));
        LedgerTransaction ambiguousSplit = new LedgerTransaction(
                fixture.requestId(), fixture.delivery().requestKey(),
                "Legacy ambiguous split", List.of(
                new LedgerLeg(fixture.claimAccount(), -40L),
                new LedgerLeg(PlayerPaymentCommit.debtAccount(
                        fixture.ownerId()), 20L),
                new LedgerLeg(PlayerPaymentCommit.walletAccount(
                        fixture.ownerId()), 20L)));
        LedgerTransaction zeroRequest = new LedgerTransaction(
                new UUID(0L, 0L), fixture.delivery().requestKey(),
                "Legacy zero request", List.of(
                new LedgerLeg(fixture.claimAccount(), -40L),
                new LedgerLeg(PlayerPaymentCommit.walletAccount(
                        fixture.ownerId()), 40L)));

        assertThrows(IllegalArgumentException.class,
                () -> MoneyClaimSettlementCodec.decode(trailing));
        assertThrows(IllegalArgumentException.class,
                () -> MoneyClaimSettlementCodec.decode(encodeLegacy(
                        fixture.delivery(), wrongClaim)));
        assertThrows(IllegalArgumentException.class,
                () -> MoneyClaimSettlementCodec.decode(encodeLegacy(
                        fixture.delivery(), ambiguousSplit)));
        assertThrows(IllegalArgumentException.class,
                () -> MoneyClaimSettlementCodec.decode(encodeLegacy(
                        fixture.delivery(), zeroRequest)));
        assertThrows(IllegalArgumentException.class,
                () -> MoneyClaimSettlementCodec.decode(
                        Arrays.copyOf(valid, valid.length - 1)));
    }

    @Test
    void legacyRecordIdentityMismatchFailsBeforeApply()
            throws Exception {
        Fixture fixture = Fixture.create();
        EscrowJournalEvent event = new EscrowJournalEvent(
                EscrowJournalEventType.MONEY_CLAIM_SETTLEMENT,
                encodeLegacy(fixture.delivery(),
                        fixture.settlementLedger()));

        assertThrows(EscrowRuntimeException.class,
                () -> fixture.applier().preflight(
                        UUID.randomUUID(), event));
        assertEquals(50L, fixture.ledger().balance(
                PlayerPaymentCommit.walletAccount(fixture.ownerId())));
        assertEquals(100L, fixture.claims().getClaim(
                fixture.claimId()).remainingUnits());
    }

    @Test
    void everyPartialLegacyMaterializationFailsClosed()
            throws Exception {
        Fixture ledgerOnly = Fixture.create();
        EscrowJournalEvent ledgerOnlyEvent = new EscrowJournalEvent(
                EscrowJournalEventType.MONEY_CLAIM_SETTLEMENT,
                encodeLegacy(ledgerOnly.delivery(),
                        ledgerOnly.settlementLedger()));
        ledgerOnly.ledger().applyCommitted(
                ledgerOnly.settlementLedger());

        assertThrows(EscrowRuntimeException.class,
                () -> ledgerOnly.applier().preflight(
                        ledgerOnly.requestId(), ledgerOnlyEvent));
        assertEquals(100L, ledgerOnly.claims().getClaim(
                ledgerOnly.claimId()).remainingUnits());

        Fixture attemptOnly = Fixture.create();
        EscrowJournalEvent attemptOnlyEvent = new EscrowJournalEvent(
                EscrowJournalEventType.MONEY_CLAIM_SETTLEMENT,
                encodeLegacy(attemptOnly.delivery(),
                        attemptOnly.settlementLedger()));
        attemptOnly.claims().deliverCommitted(
                attemptOnly.ownerId(), attemptOnly.claimId(),
                attemptOnly.delivery().requestKey(),
                attemptOnly.delivery().units(),
                attemptOnly.delivery().deliveredAt());

        assertThrows(EscrowRuntimeException.class,
                () -> attemptOnly.applier().preflight(
                        attemptOnly.requestId(), attemptOnlyEvent));
        assertEquals(50L, attemptOnly.ledger().balance(
                PlayerPaymentCommit.walletAccount(
                        attemptOnly.ownerId())));
    }

    private static byte[] encodeLegacy(
            ClaimDeliveryCommit delivery,
            LedgerTransaction ledger
    ) throws Exception {
        byte[] deliveryBytes = ClaimJournalCodec.encodeDelivery(delivery);
        byte[] ledgerBytes = LedgerJournalCodec.encode(ledger);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(MoneyClaimSettlement.LEGACY_FORMAT_VERSION);
            output.writeInt(deliveryBytes.length);
            output.write(deliveryBytes);
            output.writeInt(ledgerBytes.length);
            output.write(ledgerBytes);
        }
        return bytes.toByteArray();
    }

    private static int readVersion(byte[] encoded) {
        return java.nio.ByteBuffer.wrap(encoded).getInt();
    }

    private record Fixture(
            UUID ownerId,
            UUID claimId,
            UUID requestId,
            LedgerAccountId claimAccount,
            ClaimDeliveryCommit delivery,
            LedgerTransaction settlementLedger,
            LedgerSavedData ledger,
            ClaimSavedData claims,
            EscrowSavedDataMutationApplier applier
    ) {
        private static Fixture create() {
            UUID ownerId = UUID.randomUUID();
            UUID claimId = UUID.randomUUID();
            UUID requestId = UUID.randomUUID();
            LedgerAccountId claimAccount = new LedgerAccountId(
                    LedgerAccountType.PLAYER_CLAIM,
                    claimId.toString());
            ClaimSavedData claims = new ClaimSavedData();
            claims.createCommitted(new EscrowClaim(
                    claimId, UUID.randomUUID(), ownerId,
                    "legacy.money.claim." + claimId,
                    ClaimKind.MONEY, 100L, 100L, new byte[0],
                    ClaimStatus.PENDING, "Legacy money claim",
                    NOW, NOW));
            LedgerSavedData ledger = new LedgerSavedData();
            ledger.applyCommitted(new LedgerTransaction(
                    UUID.randomUUID(), "legacy money claim seed",
                    "Legacy seed", List.of(
                    new LedgerLeg(LedgerAccountId.system(
                            LedgerAccountType.ADMIN_SOURCE), -150L),
                    new LedgerLeg(claimAccount, 100L),
                    new LedgerLeg(PlayerPaymentCommit.walletAccount(
                            ownerId), 50L))));
            String requestKey = "legacy.money.collection." + requestId;
            ClaimDeliveryCommit delivery = new ClaimDeliveryCommit(
                    ownerId, claimId, requestKey, 40L,
                    NOW.plusSeconds(1));
            LedgerTransaction settlement = new LedgerTransaction(
                    requestId, requestKey, "Legacy money collection",
                    List.of(
                    new LedgerLeg(claimAccount, -40L),
                    new LedgerLeg(PlayerPaymentCommit.walletAccount(
                            ownerId), 40L)));
            EscrowSavedDataMutationApplier applier =
                    new EscrowSavedDataMutationApplier(
                            new EscrowTransactionSavedData(), ledger,
                            claims);
            return new Fixture(ownerId, claimId, requestId,
                    claimAccount, delivery, settlement, ledger,
                    claims, applier);
        }
    }
}
