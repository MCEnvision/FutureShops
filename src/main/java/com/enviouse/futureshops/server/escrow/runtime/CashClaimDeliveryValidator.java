package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.custody.CashClaimCustodySupport;
import com.enviouse.futureshops.server.escrow.custody.CustodyAdapterCapability;
import com.enviouse.futureshops.server.escrow.custody.CustodyEndpointEvidence;
import com.enviouse.futureshops.server.escrow.custody.CustodyLot;
import com.enviouse.futureshops.server.escrow.custody.CustodyLotState;
import com.enviouse.futureshops.server.escrow.custody.CustodyMutation;
import com.enviouse.futureshops.server.escrow.custody.CustodyPreparedBatch;
import com.enviouse.futureshops.server.escrow.custody.CustodyPreparedOperation;
import com.enviouse.futureshops.server.escrow.inventory.PlayerInventoryDeliveryToken;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintSavedData;

import java.util.Objects;
import java.util.UUID;

final class CashClaimDeliveryValidator {
    private CashClaimDeliveryValidator() {
    }

    static void validate(
            EscrowClaim claim,
            CashClaimDeliveryCommit commit,
            ProtectedMintSavedData protectedMints
    ) {
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(commit, "commit");
        Objects.requireNonNull(protectedMints, "protectedMints");
        ClaimDeliveryCommit delivery = commit.delivery();
        CustodyPreparedBatch batch = commit.custody().batch();
        CustodyPreparedOperation operation = batch.operations().get(0);
        CustodyMutation mutation = commit.custody().mutations().get(0);
        CustodyLot held = operation.lotSnapshot();
        if (!claim.claimId().equals(delivery.claimId())
                || !claim.ownerId().equals(delivery.ownerId())
                || !claim.transactionId().equals(batch.transactionId())
                || delivery.units() != claim.originalUnits()
                || (claim.status() != ClaimStatus.PENDING
                && claim.status() != ClaimStatus.COMPLETED)
                || (claim.status() == ClaimStatus.PENDING
                && claim.remainingUnits() != claim.originalUnits())
                || (claim.status() == ClaimStatus.COMPLETED
                && (!claim.updatedAt().equals(delivery.deliveredAt())
                || claim.remainingUnits() != 0L))) {
            throw new EscrowRuntimeException(
                    "Cash claim delivery does not match its claim");
        }
        requireDeliveryRequest(claim.claimId(), delivery.requestKey());
        CustodyLot expected = CashClaimDeliveryPlanner.expectedLot(
                claim, protectedMints);
        if (!held.equals(expected)
                || held.state() != CustodyLotState.HELD
                || !batch.batchId().equals(
                CustodyPreparedBatch.deterministicId(
                        claim.transactionId(), delivery.requestKey()))
                || !mutation.resultingLot().equals(
                expected.transition(CustodyLotState.RELEASED,
                        delivery.deliveredAt()))) {
            throw new EscrowRuntimeException(
                    "Cash claim custody does not match its payload");
        }
        PlayerInventoryDeliveryToken token;
        try {
            token = PlayerInventoryDeliveryToken.decode(
                    operation.simulationToken());
        } catch (RuntimeException exception) {
            throw new EscrowRuntimeException(
                    "Cash claim inventory proof token is invalid", exception);
        }
        CustodyEndpointEvidence destination =
                operation.plannedEvidence().destination();
        if (!token.playerId().equals(claim.ownerId())
                || !token.claimId().equals(claim.claimId())
                || !token.transactionId().equals(claim.transactionId())
                || !token.batchId().equals(batch.batchId())
                || !token.lotId().equals(held.lotId())
                || !token.matches(delivery.requestKey(),
                held.assetFingerprint())
                || !destination.adapterId().equals(
                CashClaimCustodySupport.PLAYER_INVENTORY_ADAPTER_ID)
                || destination.capability()
                != CustodyAdapterCapability.RECONCILABLE
                || !destination.ownerKey().equals(
                claim.ownerId().toString())
                || !destination.locationKey().equals("inventory.main")
                || !destination.mutationToken().equals(
                operation.simulationToken())
                || !java.security.MessageDigest.isEqual(
                destination.beforeStateHash(),
                token.beforeInventoryHash())
                || !java.security.MessageDigest.isEqual(
                destination.afterStateHash(), token.afterInventoryHash())
                || !operation.plannedEvidence().source().equals(
                held.holdEvidence().destination())
                || !mutation.receipt().evidence().equals(
                operation.plannedEvidence())) {
            throw new EscrowRuntimeException(
                    "Cash claim inventory proof does not match custody");
        }
    }

    private static void requireDeliveryRequest(
            UUID claimId,
            String requestKey
    ) {
        String prefix = "cash.claim.delivery." + claimId + ".";
        if (!requestKey.startsWith(prefix)) {
            throw new EscrowRuntimeException(
                    "Cash claim delivery request key is invalid");
        }
        try {
            UUID attemptId = UUID.fromString(
                    requestKey.substring(prefix.length()));
            if (!CashClaimDeliveryPlanner.deliveryRequestKey(
                    claimId, attemptId).equals(requestKey)) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new EscrowRuntimeException(
                    "Cash claim delivery request key is invalid", exception);
        }
    }
}
