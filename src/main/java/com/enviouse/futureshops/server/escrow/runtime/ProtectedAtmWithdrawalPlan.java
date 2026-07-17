package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.money.ProtectedMoneyMintBridge;
import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountType;
import com.enviouse.futureshops.server.escrow.ledger.LedgerLeg;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintBatch;
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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public record ProtectedAtmWithdrawalPlan(
        ProtectedAtmWithdrawalRequest request,
        EscrowTransaction createdTransaction,
        List<ProtectedMintJournalEvent> mintIssues,
        List<EscrowClaim> cashClaims
) {
    public static final String CURRENCY_ID = "futureshops:wallet";
    public static final String REQUEST_FINGERPRINT_ATTRIBUTE =
            "request_fingerprint";
    public static final String PROVIDER_ATTRIBUTE = "provider_id";
    public static final String SIGNATURE_ATTRIBUTE = "currency_signature";
    public static final String SELECTION_SHAPE_ATTRIBUTE =
            "selection_shape";

    public ProtectedAtmWithdrawalPlan {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(createdTransaction, "createdTransaction");
        mintIssues = List.copyOf(Objects.requireNonNull(
                mintIssues, "mintIssues"));
        cashClaims = List.copyOf(Objects.requireNonNull(
                cashClaims, "cashClaims"));
        if (!createdTransaction.transactionId().value()
                .equals(request.requestId())
                || createdTransaction.state() != EscrowState.CREATED
                || createdTransaction.operation()
                != EscrowOperation.ATM_WITHDRAWAL) {
            throw new IllegalArgumentException(
                    "Protected ATM plan transaction is invalid");
        }
        AtmWithdrawalCommit commit = commitFor(heldTransaction(
                createdTransaction, request.requestedAt()),
                mintIssues, cashClaims, request.playerId());
        if (commit.amountMinorUnits() != request.amountMinorUnits()) {
            throw new IllegalArgumentException(
                    "Protected ATM plan amount does not match");
        }
    }

    public static ProtectedAtmWithdrawalPlan create(
            ProtectedAtmWithdrawalRequest request
    ) {
        Objects.requireNonNull(request, "request");
        List<ProtectedMintJournalEvent> issues = new ArrayList<>();
        for (AtmBillSelection selection : request.selections()) {
            String requestKey = mintRequestKey(request.requestId(),
                    selection.denominationIndex());
            ProtectedMintBatch batch = ProtectedMoneyMintBridge.plan(
                    request.requestId(), requestKey,
                    selection.denominationMinorUnits(), selection.billCount(),
                    request.requestedAt()).materialize(
                    selection.billCount(), request.requestedAt());
            issues.add(ProtectedMintJournalEvent.issue(batch));
        }
        if (issues.size() > AtmWithdrawalCommit.MAX_MINT_ISSUES) {
            throw new IllegalArgumentException(
                    "Protected ATM plan has too many mint batches");
        }
        List<EscrowClaim> claims = claims(
                request, issues, request.requestedAt());
        EscrowTransaction created = createdTransaction(request, claims);
        return new ProtectedAtmWithdrawalPlan(
                request, created, issues, claims);
    }

    public EscrowTransaction heldTransaction() {
        return heldTransaction(createdTransaction, request.requestedAt());
    }

    public AtmWithdrawalCommit commitFor(EscrowTransaction held) {
        requireImmutableTransaction(held);
        return commitFor(held, mintIssues, cashClaims, request.playerId());
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
                    "Protected ATM retry conflicts with its original plan");
        }
    }

    private static EscrowTransaction createdTransaction(
            ProtectedAtmWithdrawalRequest request,
            List<EscrowClaim> claims
    ) {
        EscrowParty player = EscrowParty.player(request.playerId());
        EscrowParty system = EscrowParty.system(
                AtmWithdrawalCommit.SYSTEM_PARTY_ID);
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
                deterministicId(request.requestId(), "wallet"),
                EscrowAssetLotType.WALLET_MONEY,
                EscrowProtectionLevel.PROTECTED,
                player,
                system,
                1L,
                Optional.of(new MoneyAmount(
                        CURRENCY_ID, request.amountMinorUnits())),
                new byte[0],
                Map.of(
                        REQUEST_FINGERPRINT_ATTRIBUTE,
                        request.fingerprint(),
                        PROVIDER_ATTRIBUTE,
                        request.providerId(),
                        SIGNATURE_ATTRIBUTE,
                        request.currencySignature(),
                        SELECTION_SHAPE_ATTRIBUTE,
                        AtmRequestSemantics.shape(request.selections()))));
        for (EscrowClaim claim : claims) {
            ProtectedCashClaimPayload payload =
                    ProtectedCashClaimPayloadCodec.decode(claim.payload());
            lots.add(new EscrowAssetLot(
                    deterministicId(request.requestId(),
                            "cash " + claim.claimId()),
                    EscrowAssetLotType.PROTECTED_PHYSICAL_CURRENCY,
                    EscrowProtectionLevel.PROTECTED,
                    system,
                    player,
                    payload.billCount(),
                    Optional.of(new MoneyAmount(
                            CURRENCY_ID,
                            payload.denominationMinorUnits())),
                    claim.payload(),
                    Map.of()));
        }
        return EscrowTransaction.create(
                new EscrowTransactionId(request.requestId()),
                Optional.empty(),
                new EscrowRequestKey(requestKey(request.requestId())),
                EscrowOperation.ATM_WITHDRAWAL,
                participants,
                lots,
                request.requestedAt(),
                configRevision(request.currencySignature()),
                Optional.empty());
    }

    private static List<EscrowClaim> claims(
            ProtectedAtmWithdrawalRequest request,
            List<ProtectedMintJournalEvent> issues,
            Instant createdAt
    ) {
        List<EscrowClaim> claims = new ArrayList<>();
        for (ProtectedMintJournalEvent issue : issues) {
            ProtectedMintBatch batch = issue.batch().orElseThrow();
            int portionCount = Math.floorDiv(
                    Math.addExact(batch.authorizedCount(),
                            ProtectedCashClaimPayload.MAX_STACK_BILLS - 1),
                    ProtectedCashClaimPayload.MAX_STACK_BILLS);
            int remaining = batch.authorizedCount();
            for (int portionIndex = 0;
                 portionIndex < portionCount;
                 portionIndex++) {
                int billCount = Math.min(
                        remaining,
                        ProtectedCashClaimPayload.MAX_STACK_BILLS);
                ProtectedCashClaimPayload payload =
                        ProtectedCashClaimPayload.fromBatch(
                                batch, portionIndex, portionCount, billCount);
                byte[] encoded = ProtectedCashClaimPayloadCodec.encode(payload);
                long claimUnits = Math.multiplyExact(
                        batch.denominationMinorUnits(), (long) billCount);
                claims.add(new EscrowClaim(
                        AtmWithdrawalCommit.claimId(
                                request.requestId(), batch.batchId(),
                                portionIndex),
                        request.requestId(),
                        request.playerId(),
                        AtmWithdrawalCommit.claimSourceKey(
                                request.requestId(), batch.batchId(),
                                portionIndex),
                        ClaimKind.PROTECTED_CASH,
                        claimUnits,
                        claimUnits,
                        encoded,
                        ClaimStatus.PENDING,
                        "Protected cash "
                                + batch.denominationMinorUnits(),
                        createdAt,
                        createdAt));
                remaining = Math.subtractExact(remaining, billCount);
            }
            if (remaining != 0) {
                throw new IllegalStateException(
                        "Protected ATM claim plan is incomplete");
            }
        }
        claims.sort(Comparator
                .comparing((EscrowClaim claim) ->
                        ProtectedCashClaimPayloadCodec.decode(
                                claim.payload()).batchId().toString())
                .thenComparingInt(claim ->
                        ProtectedCashClaimPayloadCodec.decode(
                                claim.payload()).portionIndex()));
        return List.copyOf(claims);
    }

    private static EscrowTransaction heldTransaction(
            EscrowTransaction created, Instant at
    ) {
        return created.transitionTo(EscrowState.VALIDATED, at)
                .transitionTo(EscrowState.HOLDING, at)
                .transitionTo(EscrowState.HELD, at);
    }

    private static AtmWithdrawalCommit commitFor(
            EscrowTransaction held,
            List<ProtectedMintJournalEvent> issues,
            List<EscrowClaim> claims,
            UUID playerId
    ) {
        long total = 0L;
        for (ProtectedMintJournalEvent issue : issues) {
            ProtectedMintBatch batch = issue.batch().orElseThrow();
            total = Math.addExact(total, Math.multiplyExact(
                    batch.denominationMinorUnits(),
                    (long) batch.authorizedCount()));
        }
        EscrowTransaction committed = held.transitionTo(
                EscrowState.COMMIT_DECIDED,
                held.timestamps().updatedAt());
        LedgerTransaction ledger = new LedgerTransaction(
                held.transactionId().value(),
                AtmWithdrawalCommit.ledgerIdempotencyKey(
                        held.transactionId().value()),
                AtmWithdrawalCommit.LEDGER_REASON,
                List.of(
                        new LedgerLeg(new LedgerAccountId(
                                LedgerAccountType.PLAYER_WALLET,
                                playerId.toString()),
                                Math.negateExact(total)),
                        new LedgerLeg(LedgerAccountId.system(
                                LedgerAccountType
                                        .PROTECTED_CURRENCY_OUTSTANDING),
                                total)));
        return new AtmWithdrawalCommit(
                playerId, committed, ledger, issues, claims);
    }

    private static String requestKey(UUID requestId) {
        return "atm.withdrawal." + requestId;
    }

    private static String mintRequestKey(UUID requestId,
                                         int denominationIndex) {
        return requestKey(requestId) + ".mint."
                + denominationIndex;
    }

    private static UUID deterministicId(UUID requestId, String suffix) {
        return UUID.nameUUIDFromBytes(("futureshops protected atm lot "
                + requestId + " " + suffix)
                .getBytes(StandardCharsets.UTF_8));
    }

    private static long configRevision(String signature) {
        byte[] bytes = HexFormatSupport.decodePrefix(signature);
        long value = 0L;
        for (int index = 0; index < Math.min(Long.BYTES, bytes.length); index++) {
            value = value << 8 | bytes[index] & 255L;
        }
        return value & Long.MAX_VALUE;
    }

    private static final class HexFormatSupport {
        private HexFormatSupport() {
        }

        private static byte[] decodePrefix(String value) {
            try {
                return java.util.HexFormat.of().parseHex(value);
            } catch (IllegalArgumentException exception) {
                return value.getBytes(StandardCharsets.UTF_8);
            }
        }
    }
}
