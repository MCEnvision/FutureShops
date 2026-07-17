package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountType;
import com.enviouse.futureshops.server.escrow.ledger.LedgerLeg;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintBatch;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintEvidenceFactory;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintJournalEvent;
import com.enviouse.futureshops.server.escrow.model.EscrowAssetLot;
import com.enviouse.futureshops.server.escrow.model.EscrowAssetLotType;
import com.enviouse.futureshops.server.escrow.model.EscrowOperation;
import com.enviouse.futureshops.server.escrow.model.EscrowParticipant;
import com.enviouse.futureshops.server.escrow.model.EscrowParticipantRole;
import com.enviouse.futureshops.server.escrow.model.EscrowParty;
import com.enviouse.futureshops.server.escrow.model.EscrowProtectionLevel;
import com.enviouse.futureshops.server.escrow.model.EscrowRequestKey;
import com.enviouse.futureshops.server.escrow.model.EscrowState;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import com.enviouse.futureshops.server.escrow.model.EscrowTransactionId;
import com.enviouse.futureshops.server.escrow.model.MoneyAmount;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

final class AtmWithdrawalTestFixtures {
    static final UUID TRANSACTION_ID =
            UUID.fromString("71000000-0000-0000-0000-000000000001");
    static final UUID PLAYER_ID =
            UUID.fromString("72000000-0000-0000-0000-000000000001");
    static final Instant CREATED = Instant.parse("2026-07-17T12:00:00.123456789Z");
    static final Instant COMMITTED = CREATED.plusSeconds(4);
    static final String CURRENCY = "futureshops:credits";
    static final String SERVER = "atm-test-server";
    static final ProtectedMintEvidenceFactory EVIDENCE = (batchId, transactionId,
            denomination, authorizedCount, server, issuedAt) ->
            "atm.checksum." + batchId + "." + transactionId + "." + denomination
                    + "." + authorizedCount + "." + server + "." + issuedAt;

    private AtmWithdrawalTestFixtures() {
    }

    static AtmWithdrawalCommit commit() {
        return commit(List.of(issue("hundreds", 100L, 70),
                issue("quarters", 25L, 4)));
    }

    static AtmWithdrawalCommit commit(List<ProtectedMintJournalEvent> issues) {
        List<EscrowClaim> claims = claims(issues);
        long total = issues.stream().map(ProtectedMintJournalEvent::batch)
                .map(Optional::orElseThrow)
                .mapToLong(batch -> Math.multiplyExact(
                        batch.denominationMinorUnits(), (long) batch.authorizedCount()))
                .reduce(0L, Math::addExact);
        EscrowTransaction held = heldTransaction(issues, claims, total);
        EscrowTransaction committed = held.transitionTo(EscrowState.COMMIT_DECIDED, COMMITTED);
        LedgerTransaction ledger = new LedgerTransaction(
                TRANSACTION_ID,
                AtmWithdrawalCommit.ledgerIdempotencyKey(TRANSACTION_ID),
                AtmWithdrawalCommit.LEDGER_REASON,
                List.of(
                        new LedgerLeg(new LedgerAccountId(
                                LedgerAccountType.PLAYER_WALLET,
                                PLAYER_ID.toString()), Math.negateExact(total)),
                        new LedgerLeg(LedgerAccountId.system(
                                LedgerAccountType.PROTECTED_CURRENCY_OUTSTANDING), total)));
        return new AtmWithdrawalCommit(PLAYER_ID, committed, ledger, issues, claims);
    }

    static ProtectedMintJournalEvent issue(String suffix, long denomination, int count) {
        String requestKey = "atm.withdrawal." + TRANSACTION_ID + ".mint." + suffix;
        ProtectedMintBatch batch = ProtectedMintBatch.issue(
                TRANSACTION_ID, requestKey, denomination, count,
                SERVER, COMMITTED, EVIDENCE);
        return ProtectedMintJournalEvent.issue(batch);
    }

    static List<EscrowClaim> claims(List<ProtectedMintJournalEvent> issues) {
        List<EscrowClaim> claims = new ArrayList<>();
        for (ProtectedMintJournalEvent issue : issues) {
            ProtectedMintBatch batch = issue.batch().orElseThrow();
            int portionCount = Math.floorDiv(
                    Math.addExact(batch.authorizedCount(),
                            ProtectedCashClaimPayload.MAX_STACK_BILLS - 1),
                    ProtectedCashClaimPayload.MAX_STACK_BILLS);
            int remaining = batch.authorizedCount();
            for (int index = 0; index < portionCount; index++) {
                int count = Math.min(remaining,
                        ProtectedCashClaimPayload.MAX_STACK_BILLS);
                remaining = Math.subtractExact(remaining, count);
                ProtectedCashClaimPayload payload = ProtectedCashClaimPayload.fromBatch(
                        batch, index, portionCount, count);
                byte[] encoded = ProtectedCashClaimPayloadCodec.encode(payload);
                long claimUnits = Math.multiplyExact(
                        batch.denominationMinorUnits(), (long) count);
                claims.add(new EscrowClaim(
                        AtmWithdrawalCommit.claimId(
                                TRANSACTION_ID, batch.batchId(), index),
                        TRANSACTION_ID,
                        PLAYER_ID,
                        AtmWithdrawalCommit.claimSourceKey(
                                TRANSACTION_ID, batch.batchId(), index),
                        ClaimKind.PROTECTED_CASH,
                        claimUnits,
                        claimUnits,
                        encoded,
                        ClaimStatus.PENDING,
                        "Protected cash " + batch.denominationMinorUnits(),
                        COMMITTED,
                        COMMITTED));
            }
        }
        claims.sort(Comparator
                .comparing((EscrowClaim claim) ->
                        ProtectedCashClaimPayloadCodec.decode(
                                claim.payload()).batchId().toString())
                .thenComparingInt(claim -> ProtectedCashClaimPayloadCodec.decode(
                        claim.payload()).portionIndex()));
        return List.copyOf(claims);
    }

    static EscrowTransaction heldTransaction(List<ProtectedMintJournalEvent> issues,
                                             List<EscrowClaim> claims,
                                             long total) {
        EscrowParty player = EscrowParty.player(PLAYER_ID);
        EscrowParty system = EscrowParty.system("protected_currency");
        Set<EscrowParticipant> participants = Set.of(
                new EscrowParticipant(player, EnumSet.of(
                        EscrowParticipantRole.INITIATOR,
                        EscrowParticipantRole.PAYER,
                        EscrowParticipantRole.RECIPIENT)),
                new EscrowParticipant(system, EnumSet.of(
                        EscrowParticipantRole.BENEFICIARY,
                        EscrowParticipantRole.CUSTODIAN)));
        List<EscrowAssetLot> lots = new ArrayList<>();
        lots.add(new EscrowAssetLot(
                deterministicId("wallet"),
                EscrowAssetLotType.WALLET_MONEY,
                EscrowProtectionLevel.PROTECTED,
                player,
                system,
                1L,
                Optional.of(new MoneyAmount(CURRENCY, total)),
                new byte[0],
                Map.of()));
        for (EscrowClaim claim : claims) {
            ProtectedCashClaimPayload payload =
                    ProtectedCashClaimPayloadCodec.decode(claim.payload());
            lots.add(new EscrowAssetLot(
                    deterministicId("cash " + claim.claimId()),
                    EscrowAssetLotType.PROTECTED_PHYSICAL_CURRENCY,
                    EscrowProtectionLevel.PROTECTED,
                    system,
                    player,
                    payload.billCount(),
                    Optional.of(new MoneyAmount(
                            CURRENCY, payload.denominationMinorUnits())),
                    claim.payload(),
                    Map.of()));
        }
        return EscrowTransaction.create(
                        new EscrowTransactionId(TRANSACTION_ID),
                        Optional.empty(),
                        new EscrowRequestKey("atm withdrawal test request"),
                        EscrowOperation.ATM_WITHDRAWAL,
                        participants,
                        lots,
                        CREATED,
                        1L,
                        Optional.empty())
                .transitionTo(EscrowState.VALIDATED, CREATED.plusSeconds(1))
                .transitionTo(EscrowState.HOLDING, CREATED.plusSeconds(2))
                .transitionTo(EscrowState.HELD, CREATED.plusSeconds(3));
    }

    static LedgerTransaction fundWallet(long amount, String key) {
        UUID transactionId = deterministicId("fund " + key);
        return new LedgerTransaction(transactionId, "fund " + key, "fund", List.of(
                new LedgerLeg(LedgerAccountId.system(LedgerAccountType.ADMIN_SOURCE),
                        Math.negateExact(amount)),
                new LedgerLeg(new LedgerAccountId(
                        LedgerAccountType.PLAYER_WALLET, PLAYER_ID.toString()), amount)));
    }

    static UUID deterministicId(String value) {
        return UUID.nameUUIDFromBytes(("atm test " + value)
                .getBytes(StandardCharsets.UTF_8));
    }
}
