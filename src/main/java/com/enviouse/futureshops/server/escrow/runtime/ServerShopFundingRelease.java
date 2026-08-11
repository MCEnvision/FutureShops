package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public record ServerShopFundingRelease(
        UUID releaseId,
        UUID purchaseRequestId,
        UUID playerId,
        UUID fundingTransactionId,
        UUID fundingClaimId,
        long amountMinorUnits,
        Instant releasedAt,
        EscrowTransaction completedTransaction,
        ClaimDeliveryCommit fundingClaimDelivery,
        EscrowClaim refundClaim,
        LedgerTransaction ledgerTransaction
) {
    public static final String REFUND_LABEL =
            "Server shop physical payment refund";

    private static final UUID ZERO_UUID = new UUID(0L, 0L);

    public ServerShopFundingRelease {
        releaseId = requireUuid(releaseId, "releaseId");
        purchaseRequestId = requireUuid(purchaseRequestId,
                "purchaseRequestId");
        playerId = requireUuid(playerId, "playerId");
        fundingTransactionId = requireUuid(fundingTransactionId,
                "fundingTransactionId");
        fundingClaimId = requireUuid(fundingClaimId, "fundingClaimId");
        releasedAt = Objects.requireNonNull(releasedAt, "releasedAt");
        completedTransaction = Objects.requireNonNull(
                completedTransaction, "completedTransaction");
        fundingClaimDelivery = Objects.requireNonNull(
                fundingClaimDelivery, "fundingClaimDelivery");
        refundClaim = Objects.requireNonNull(refundClaim, "refundClaim");
        ledgerTransaction = Objects.requireNonNull(
                ledgerTransaction, "ledgerTransaction");
        if (amountMinorUnits <= 0L
                || purchaseRequestId.equals(fundingTransactionId)
                || purchaseRequestId.equals(fundingClaimId)
                || fundingTransactionId.equals(fundingClaimId)) {
            throw new IllegalArgumentException(
                    "Server shop funding release identity is invalid");
        }
        Canonical expected = canonical(purchaseRequestId, playerId,
                fundingTransactionId, fundingClaimId, amountMinorUnits,
                releasedAt);
        if (!releaseId.equals(expected.releaseId())
                || !completedTransaction.equals(expected.transaction())
                || !fundingClaimDelivery.equals(expected.delivery())
                || !refundClaim.equals(expected.refundClaim())
                || !ledgerTransaction.equals(expected.ledger())) {
            throw new IllegalArgumentException(
                    "Server shop funding release evidence conflicts");
        }
    }

    public static ServerShopFundingRelease create(
            UUID purchaseRequestId,
            UUID playerId,
            UUID fundingTransactionId,
            UUID fundingClaimId,
            long amountMinorUnits,
            Instant releasedAt
    ) {
        Canonical value = canonical(purchaseRequestId, playerId,
                fundingTransactionId, fundingClaimId, amountMinorUnits,
                releasedAt);
        return new ServerShopFundingRelease(value.releaseId(),
                purchaseRequestId, playerId, fundingTransactionId,
                fundingClaimId, amountMinorUnits, releasedAt,
                value.transaction(), value.delivery(), value.refundClaim(),
                value.ledger());
    }

    public static ServerShopFundingRelease create(
            UUID playerId,
            ServerShopPurchaseCommit.PhysicalFunding funding,
            Instant releasedAt
    ) {
        Objects.requireNonNull(funding, "funding");
        return create(funding.purchaseRequestId(), playerId,
                funding.transactionId(), funding.claimId(),
                funding.amountMinorUnits(), releasedAt);
    }

    public static UUID releaseId(
            UUID purchaseRequestId,
            UUID fundingClaimId
    ) {
        return deterministicUuid("release", purchaseRequestId,
                fundingClaimId);
    }

    public static UUID refundClaimId(
            UUID purchaseRequestId,
            UUID fundingClaimId
    ) {
        return deterministicUuid("refund claim", purchaseRequestId,
                fundingClaimId);
    }

    public static UUID fundingRequestId(UUID purchaseRequestId) {
        return UUID.nameUUIDFromBytes((
                "futureshops server shop physical funding v1 "
                        + requireUuid(purchaseRequestId,
                        "purchaseRequestId"))
                .getBytes(StandardCharsets.UTF_8));
    }

    public static String deliveryKey(
            UUID purchaseRequestId,
            UUID fundingClaimId
    ) {
        return "server.shop.physical.release."
                + requireUuid(purchaseRequestId, "purchaseRequestId") + "."
                + requireUuid(fundingClaimId, "fundingClaimId");
    }

    public static String refundSourceKey(
            UUID purchaseRequestId,
            UUID fundingClaimId
    ) {
        return "server.shop.physical.refund."
                + requireUuid(purchaseRequestId, "purchaseRequestId") + "."
                + requireUuid(fundingClaimId, "fundingClaimId");
    }

    private static Canonical canonical(
            UUID purchaseRequestId,
            UUID playerId,
            UUID fundingTransactionId,
            UUID fundingClaimId,
            long amountMinorUnits,
            Instant releasedAt
    ) {
        purchaseRequestId = requireUuid(purchaseRequestId,
                "purchaseRequestId");
        playerId = requireUuid(playerId, "playerId");
        fundingTransactionId = requireUuid(fundingTransactionId,
                "fundingTransactionId");
        fundingClaimId = requireUuid(fundingClaimId, "fundingClaimId");
        releasedAt = Objects.requireNonNull(releasedAt, "releasedAt");
        if (amountMinorUnits <= 0L) {
            throw new IllegalArgumentException(
                    "Server shop funding release amount is invalid");
        }
        UUID releaseId = releaseId(purchaseRequestId, fundingClaimId);
        UUID refundClaimId = refundClaimId(purchaseRequestId,
                fundingClaimId);
        EscrowParty player = EscrowParty.player(playerId);
        EscrowParty custody = EscrowParty.system(
                "server.shop.physical.custody");
        Set<EscrowParticipant> participants = Set.of(
                new EscrowParticipant(player, Set.of(
                        EscrowParticipantRole.INITIATOR,
                        EscrowParticipantRole.RECIPIENT,
                        EscrowParticipantRole.BENEFICIARY)),
                new EscrowParticipant(custody, Set.of(
                        EscrowParticipantRole.PAYER,
                        EscrowParticipantRole.CUSTODIAN)));
        EscrowAssetLot money = new EscrowAssetLot(
                deterministicUuid("money lot", purchaseRequestId,
                        fundingClaimId),
                EscrowAssetLotType.WALLET_MONEY,
                EscrowProtectionLevel.PROTECTED, custody, player, 1L,
                Optional.of(new MoneyAmount(
                        ServerShopPurchaseCommit.CURRENCY_ID,
                        amountMinorUnits)), new byte[0], Map.of(
                "purchase_request_id", purchaseRequestId.toString(),
                "funding_transaction_id", fundingTransactionId.toString(),
                "funding_claim_id", fundingClaimId.toString(),
                "refund_claim_id", refundClaimId.toString(),
                "amount", Long.toString(amountMinorUnits)));
        EscrowTransaction created = EscrowTransaction.create(
                new EscrowTransactionId(releaseId), Optional.empty(),
                new EscrowRequestKey("server.shop.funding.release."
                        + purchaseRequestId + "." + fundingClaimId),
                EscrowOperation.SERVER_SHOP_FUNDING_RELEASE,
                participants, List.of(money), releasedAt, 0L,
                Optional.empty());
        EscrowTransaction completed = complete(created, releasedAt);
        ClaimDeliveryCommit delivery = new ClaimDeliveryCommit(playerId,
                fundingClaimId, deliveryKey(purchaseRequestId,
                fundingClaimId), amountMinorUnits, releasedAt);
        EscrowClaim refund = new EscrowClaim(refundClaimId, releaseId,
                playerId, refundSourceKey(purchaseRequestId,
                fundingClaimId), ClaimKind.MONEY, amountMinorUnits,
                amountMinorUnits, new byte[0], ClaimStatus.PENDING,
                REFUND_LABEL, releasedAt, releasedAt);
        LedgerTransaction ledger = new LedgerTransaction(releaseId,
                "server.shop.funding.release." + releaseId,
                "Server shop funding release", List.of(
                new LedgerLeg(ServerShopPurchaseCommit.claimAccount(
                        fundingClaimId), Math.negateExact(amountMinorUnits)),
                new LedgerLeg(ServerShopPurchaseCommit.claimAccount(
                        refundClaimId), amountMinorUnits)));
        return new Canonical(releaseId, completed, delivery, refund,
                ledger);
    }

    private static EscrowTransaction complete(
            EscrowTransaction transaction,
            Instant at
    ) {
        return transaction.transitionTo(EscrowState.VALIDATED, at)
                .transitionTo(EscrowState.HOLDING, at)
                .transitionTo(EscrowState.HELD, at)
                .transitionTo(EscrowState.COMMIT_DECIDED, at)
                .transitionTo(EscrowState.COMMITTED, at)
                .transitionTo(EscrowState.CLAIMS_CREATED, at)
                .transitionTo(EscrowState.COMPLETED, at);
    }

    private static UUID deterministicUuid(
            String purpose,
            UUID purchaseRequestId,
            UUID fundingClaimId
    ) {
        return UUID.nameUUIDFromBytes((
                "futureshops server shop funding release v1\u0000"
                        + purpose + "\u0000"
                        + requireUuid(purchaseRequestId,
                        "purchaseRequestId") + "\u0000"
                        + requireUuid(fundingClaimId, "fundingClaimId"))
                .getBytes(StandardCharsets.UTF_8));
    }

    private static UUID requireUuid(UUID value, String name) {
        UUID safe = Objects.requireNonNull(value, name);
        if (safe.equals(ZERO_UUID)) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return safe;
    }

    private record Canonical(
            UUID releaseId,
            EscrowTransaction transaction,
            ClaimDeliveryCommit delivery,
            EscrowClaim refundClaim,
            LedgerTransaction ledger
    ) {
    }
}
