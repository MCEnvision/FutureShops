package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountType;
import com.enviouse.futureshops.server.escrow.ledger.LedgerLeg;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

final class ForeignAtmWithdrawalTestFixtures {
    static final UUID REQUEST_ID = UUID.fromString(
            "73000000-0000-0000-0000-000000000001");
    static final UUID PLAYER_ID = UUID.fromString(
            "74000000-0000-0000-0000-000000000001");
    static final Instant CREATED = Instant.parse(
            "2026-07-17T13:00:00.123456789Z");
    static final Instant COMMITTED = CREATED.plusSeconds(4);
    static final String PROVIDER = "examplecurrency";
    static final String SIGNATURE = "0123456789abcdef".repeat(4);
    static final String CURRENCY = "futureshops:credits";

    private ForeignAtmWithdrawalTestFixtures() {
    }

    static ForeignAtmWithdrawalCommit commit() {
        return commit(List.of(
                new PayloadSpec(0, 0, 2, 64,
                        "examplecurrency:banknote", 100L,
                        nbt("banknote", 64, 0)),
                new PayloadSpec(0, 1, 2, 6,
                        "examplecurrency:banknote", 100L,
                        nbt("banknote", 6, 1)),
                new PayloadSpec(1, 0, 1, 4,
                        "examplecurrency:coin", 25L,
                        nbt("coin", 4, 0))));
    }

    static ForeignAtmWithdrawalCommit commit(List<PayloadSpec> specs) {
        List<EscrowClaim> claims = claims(specs);
        long total = total(claims);
        EscrowTransaction committed = heldTransaction(claims, total)
                .transitionTo(EscrowState.COMMIT_DECIDED, COMMITTED);
        LedgerTransaction ledger = new LedgerTransaction(
                REQUEST_ID,
                ForeignAtmWithdrawalCommit.ledgerIdempotencyKey(REQUEST_ID),
                ForeignAtmWithdrawalCommit.LEDGER_REASON,
                List.of(
                        new LedgerLeg(new LedgerAccountId(
                                LedgerAccountType.PLAYER_WALLET,
                                PLAYER_ID.toString()), Math.negateExact(total)),
                        new LedgerLeg(LedgerAccountId.system(
                                LedgerAccountType.FOREIGN_CURRENCY_SINK), total)));
        return new ForeignAtmWithdrawalCommit(
                REQUEST_ID, PLAYER_ID, committed, ledger, claims);
    }

    static List<EscrowClaim> claims(List<PayloadSpec> specs) {
        List<EscrowClaim> claims = new ArrayList<>();
        for (PayloadSpec spec : specs) {
            ForeignCashClaimPayload payload = ForeignCashClaimPayload.capture(
                    PROVIDER, SIGNATURE, spec.registryItemId(),
                    spec.denominationMinorUnits(), spec.stackCount(),
                    spec.denominationIndex(), spec.portionIndex(),
                    spec.portionCount(), spec.itemStackNbt());
            byte[] encoded = ForeignCashClaimPayloadCodec.encode(payload);
            long claimUnits = Math.multiplyExact(
                    spec.denominationMinorUnits(),
                    (long) spec.stackCount());
            claims.add(new EscrowClaim(
                    ForeignAtmWithdrawalCommit.claimId(REQUEST_ID,
                            spec.denominationIndex(), spec.portionIndex()),
                    REQUEST_ID,
                    PLAYER_ID,
                    ForeignAtmWithdrawalCommit.claimSourceKey(REQUEST_ID,
                            spec.denominationIndex(), spec.portionIndex()),
                    ClaimKind.FOREIGN_CASH,
                    claimUnits,
                    claimUnits,
                    encoded,
                    ClaimStatus.PENDING,
                    "Foreign cash " + spec.registryItemId(),
                    COMMITTED,
                    COMMITTED));
        }
        claims.sort(Comparator
                .comparingInt((EscrowClaim claim) -> payload(claim)
                        .denominationIndex())
                .thenComparingInt(claim -> payload(claim).portionIndex()));
        return List.copyOf(claims);
    }

    static EscrowTransaction heldTransaction(List<EscrowClaim> claims,
                                             long total) {
        EscrowParty player = EscrowParty.player(PLAYER_ID);
        EscrowParty system = EscrowParty.system(
                ForeignAtmWithdrawalCommit.FOREIGN_CURRENCY_SYSTEM_PARTY_ID);
        Set<EscrowParticipant> participants = Set.of(
                new EscrowParticipant(player, EnumSet.of(
                        EscrowParticipantRole.INITIATOR,
                        EscrowParticipantRole.PAYER,
                        EscrowParticipantRole.RECIPIENT)),
                new EscrowParticipant(system, EnumSet.of(
                        EscrowParticipantRole.BENEFICIARY,
                        EscrowParticipantRole.CUSTODIAN)));
        List<EscrowAssetLot> lots = new ArrayList<>();
        List<ForeignAtmStackSelection> stacks = claims.stream()
                .map(ForeignAtmWithdrawalTestFixtures::payload)
                .map(payload -> new ForeignAtmStackSelection(
                        payload.denominationIndex(),
                        payload.registryItemId(),
                        payload.denominationMinorUnits(),
                        payload.stackCount(),
                        payload.portionIndex(),
                        payload.portionCount(),
                        payload.serializedItemStackNbt()))
                .toList();
        ForeignAtmWithdrawalRequest request =
                new ForeignAtmWithdrawalRequest(
                        REQUEST_ID, PLAYER_ID, PROVIDER, SIGNATURE,
                        stacks, CREATED);
        lots.add(new EscrowAssetLot(
                ForeignAtmWithdrawalCommit.walletAssetLotId(REQUEST_ID),
                EscrowAssetLotType.WALLET_MONEY,
                EscrowProtectionLevel.PROTECTED,
                player,
                system,
                1L,
                Optional.of(new MoneyAmount(CURRENCY, total)),
                new byte[0],
                Map.of(
                        ProtectedAtmWithdrawalPlan
                                .REQUEST_FINGERPRINT_ATTRIBUTE,
                        request.fingerprint(),
                        ProtectedAtmWithdrawalPlan.PROVIDER_ATTRIBUTE,
                        request.providerId(),
                        ProtectedAtmWithdrawalPlan.SIGNATURE_ATTRIBUTE,
                        request.currencySignature(),
                        ProtectedAtmWithdrawalPlan
                                .SELECTION_SHAPE_ATTRIBUTE,
                        AtmRequestSemantics.foreignShape(
                                request.stacks()))));
        for (EscrowClaim claim : claims) {
            ForeignCashClaimPayload payload = payload(claim);
            lots.add(new EscrowAssetLot(
                    ForeignAtmWithdrawalCommit.cashAssetLotId(REQUEST_ID,
                            payload.denominationIndex(), payload.portionIndex()),
                    EscrowAssetLotType.FOREIGN_PHYSICAL_CURRENCY,
                    EscrowProtectionLevel.EXTERNAL,
                    system,
                    player,
                    payload.stackCount(),
                    Optional.of(new MoneyAmount(CURRENCY,
                            payload.denominationMinorUnits())),
                    payload.serializedItemStackNbt(),
                    Map.of()));
        }
        return EscrowTransaction.create(
                        new EscrowTransactionId(REQUEST_ID),
                        Optional.empty(),
                        new EscrowRequestKey(
                                ForeignAtmWithdrawalCommit.requestKey(REQUEST_ID)),
                        EscrowOperation.ATM_WITHDRAWAL,
                        participants,
                        lots,
                        CREATED,
                        1L,
                        Optional.empty())
                .transitionTo(EscrowState.VALIDATED,
                        CREATED.plusSeconds(1))
                .transitionTo(EscrowState.HOLDING,
                        CREATED.plusSeconds(2))
                .transitionTo(EscrowState.HELD,
                        CREATED.plusSeconds(3));
    }

    static long total(List<EscrowClaim> claims) {
        long total = 0L;
        for (EscrowClaim claim : claims) {
            ForeignCashClaimPayload payload = payload(claim);
            total = Math.addExact(total, Math.multiplyExact(
                    payload.denominationMinorUnits(),
                    (long) payload.stackCount()));
        }
        return total;
    }

    static ForeignCashClaimPayload payload(EscrowClaim claim) {
        return ForeignCashClaimPayloadCodec.decode(claim.payload());
    }

    static byte[] nbt(String item, int count, int portion) {
        return ("item=" + item + ",count=" + count + ",portion=" + portion)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    record PayloadSpec(int denominationIndex,
                       int portionIndex,
                       int portionCount,
                       int stackCount,
                       String registryItemId,
                       long denominationMinorUnits,
                       byte[] itemStackNbt) {
        PayloadSpec {
            itemStackNbt = itemStackNbt.clone();
        }

        @Override
        public byte[] itemStackNbt() {
            return itemStackNbt.clone();
        }
    }
}
