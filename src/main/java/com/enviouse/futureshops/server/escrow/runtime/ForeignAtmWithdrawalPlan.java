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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public record ForeignAtmWithdrawalPlan(
        ForeignAtmWithdrawalRequest request,
        EscrowTransaction createdTransaction,
        List<EscrowClaim> cashClaims
) {
    public static final String CURRENCY_ID = "futureshops:wallet";

    public ForeignAtmWithdrawalPlan {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(createdTransaction, "createdTransaction");
        cashClaims = List.copyOf(Objects.requireNonNull(
                cashClaims, "cashClaims"));
        List<EscrowClaim> expectedClaims = claims(request);
        EscrowTransaction expectedCreated = createdTransaction(
                request, expectedClaims);
        if (!cashClaims.equals(expectedClaims)
                || !createdTransaction.equals(expectedCreated)) {
            throw new IllegalArgumentException(
                    "Foreign ATM plan is not the canonical request plan");
        }
        if (!createdTransaction.transactionId().value()
                .equals(request.requestId())
                || createdTransaction.state() != EscrowState.CREATED
                || createdTransaction.operation()
                != EscrowOperation.ATM_WITHDRAWAL) {
            throw new IllegalArgumentException(
                    "Foreign ATM plan transaction is invalid");
        }
        ForeignAtmWithdrawalCommit commit = commitFor(
                heldTransaction(createdTransaction, request.requestedAt()),
                request.playerId(), cashClaims);
        if (commit.amountMinorUnits() != request.amountMinorUnits()
                || !commit.providerId().equals(request.providerId())
                || !commit.configSignature().equals(
                request.currencySignature())) {
            throw new IllegalArgumentException(
                    "Foreign ATM plan does not match its request");
        }
    }

    public static ForeignAtmWithdrawalPlan create(
            ForeignAtmWithdrawalRequest request
    ) {
        Objects.requireNonNull(request, "request");
        List<EscrowClaim> claims = claims(request);
        EscrowTransaction created = createdTransaction(request, claims);
        return new ForeignAtmWithdrawalPlan(request, created, claims);
    }

    public EscrowTransaction heldTransaction() {
        return heldTransaction(createdTransaction, request.requestedAt());
    }

    public ForeignAtmWithdrawalCommit commitFor(EscrowTransaction held) {
        requireImmutableTransaction(held);
        return commitFor(held, request.playerId(), cashClaims);
    }

    public void requireImmutableTransaction(EscrowTransaction transaction) {
        Objects.requireNonNull(transaction, "transaction");
        if (!transaction.transactionId().equals(
                createdTransaction.transactionId())
                || !transaction.requestKey().equals(
                createdTransaction.requestKey())
                || transaction.operation() != createdTransaction.operation()
                || !transaction.participants().equals(
                createdTransaction.participants())
                || !transaction.assetLots().equals(
                createdTransaction.assetLots())
                || !transaction.timestamps().createdAt().equals(
                createdTransaction.timestamps().createdAt())
                || transaction.configRevision()
                != createdTransaction.configRevision()
                || !transaction.shopReference().equals(
                createdTransaction.shopReference())) {
            throw new IllegalArgumentException(
                    "Foreign ATM retry conflicts with its original plan");
        }
    }

    private static List<EscrowClaim> claims(
            ForeignAtmWithdrawalRequest request
    ) {
        List<EscrowClaim> claims = new ArrayList<>();
        for (ForeignAtmStackSelection stack : request.stacks()) {
            ForeignCashClaimPayload payload =
                    ForeignCashClaimPayload.capture(
                            request.providerId(), request.currencySignature(),
                            stack.registryItemId(),
                            stack.denominationMinorUnits(),
                            stack.stackCount(), stack.denominationIndex(),
                            stack.portionIndex(), stack.portionCount(),
                            stack.serializedItemStackNbt());
            byte[] encoded = ForeignCashClaimPayloadCodec.encode(payload);
            long claimUnits = Math.multiplyExact(
                    payload.denominationMinorUnits(),
                    (long) payload.stackCount());
            claims.add(new EscrowClaim(
                    ForeignAtmWithdrawalCommit.claimId(
                            request.requestId(),
                            stack.denominationIndex(),
                            stack.portionIndex()),
                    request.requestId(),
                    request.playerId(),
                    ForeignAtmWithdrawalCommit.claimSourceKey(
                            request.requestId(),
                            stack.denominationIndex(),
                            stack.portionIndex()),
                    ClaimKind.FOREIGN_CASH,
                    claimUnits,
                    claimUnits,
                    encoded,
                    ClaimStatus.PENDING,
                    "Foreign cash " + stack.registryItemId(),
                    request.requestedAt(),
                    request.requestedAt()));
        }
        return List.copyOf(claims);
    }

    private static EscrowTransaction createdTransaction(
            ForeignAtmWithdrawalRequest request,
            List<EscrowClaim> claims
    ) {
        EscrowParty player = EscrowParty.player(request.playerId());
        EscrowParty system = EscrowParty.system(
                ForeignAtmWithdrawalCommit
                        .FOREIGN_CURRENCY_SYSTEM_PARTY_ID);
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
                ForeignAtmWithdrawalCommit.walletAssetLotId(
                        request.requestId()),
                EscrowAssetLotType.WALLET_MONEY,
                EscrowProtectionLevel.PROTECTED,
                player,
                system,
                1L,
                Optional.of(new MoneyAmount(
                        CURRENCY_ID, request.amountMinorUnits())),
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
            ForeignCashClaimPayload payload =
                    ForeignCashClaimPayloadCodec.decode(claim.payload());
            lots.add(new EscrowAssetLot(
                    ForeignAtmWithdrawalCommit.cashAssetLotId(
                            request.requestId(),
                            payload.denominationIndex(),
                            payload.portionIndex()),
                    EscrowAssetLotType.FOREIGN_PHYSICAL_CURRENCY,
                    EscrowProtectionLevel.EXTERNAL,
                    system,
                    player,
                    payload.stackCount(),
                    Optional.of(new MoneyAmount(
                            CURRENCY_ID,
                            payload.denominationMinorUnits())),
                    payload.serializedItemStackNbt(),
                    Map.of()));
        }
        return EscrowTransaction.create(
                new EscrowTransactionId(request.requestId()),
                Optional.empty(),
                new EscrowRequestKey(
                        ForeignAtmWithdrawalCommit.requestKey(
                                request.requestId())),
                EscrowOperation.ATM_WITHDRAWAL,
                participants,
                lots,
                request.requestedAt(),
                configRevision(request.currencySignature()),
                Optional.empty());
    }

    private static EscrowTransaction heldTransaction(
            EscrowTransaction created,
            Instant at
    ) {
        return created.transitionTo(EscrowState.VALIDATED, at)
                .transitionTo(EscrowState.HOLDING, at)
                .transitionTo(EscrowState.HELD, at);
    }

    private static ForeignAtmWithdrawalCommit commitFor(
            EscrowTransaction held,
            UUID playerId,
            List<EscrowClaim> claims
    ) {
        long total = 0L;
        for (EscrowClaim claim : claims) {
            ForeignCashClaimPayload payload =
                    ForeignCashClaimPayloadCodec.decode(claim.payload());
            total = Math.addExact(total, Math.multiplyExact(
                    payload.denominationMinorUnits(),
                    (long) payload.stackCount()));
        }
        EscrowTransaction committed = held.transitionTo(
                EscrowState.COMMIT_DECIDED,
                held.timestamps().updatedAt());
        LedgerTransaction ledger = new LedgerTransaction(
                held.transactionId().value(),
                ForeignAtmWithdrawalCommit.ledgerIdempotencyKey(
                        held.transactionId().value()),
                ForeignAtmWithdrawalCommit.LEDGER_REASON,
                List.of(
                        new LedgerLeg(new LedgerAccountId(
                                LedgerAccountType.PLAYER_WALLET,
                                playerId.toString()),
                                Math.negateExact(total)),
                        new LedgerLeg(LedgerAccountId.system(
                                LedgerAccountType.FOREIGN_CURRENCY_SINK),
                                total)));
        return new ForeignAtmWithdrawalCommit(
                held.transactionId().value(), playerId,
                committed, ledger, claims);
    }

    private static long configRevision(String signature) {
        byte[] bytes;
        try {
            bytes = HexFormat.of().parseHex(signature);
        } catch (IllegalArgumentException exception) {
            bytes = signature.getBytes(StandardCharsets.UTF_8);
        }
        long value = 0L;
        for (int index = 0;
             index < Math.min(Long.BYTES, bytes.length);
             index++) {
            value = value << 8 | bytes[index] & 255L;
        }
        return value & Long.MAX_VALUE;
    }
}
