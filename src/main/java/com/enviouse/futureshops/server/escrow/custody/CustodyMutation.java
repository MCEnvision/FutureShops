package com.enviouse.futureshops.server.escrow.custody;

import java.time.Instant;
import java.util.Objects;

public record CustodyMutation(
        CustodyLot resultingLot,
        CustodyOperationReceipt receipt
) {
    public CustodyMutation {
        Objects.requireNonNull(resultingLot, "resultingLot");
        Objects.requireNonNull(receipt, "receipt");
        if (!resultingLot.lotId().equals(receipt.lotId())
                || !resultingLot.transactionId().equals(receipt.transactionId())
                || resultingLot.units() != receipt.units()
                || resultingLot.state() != receipt.resultingState()
                || !CustodyHashes.equal(resultingLot.assetFingerprint(), receipt.assetFingerprint())) {
            throw new IllegalArgumentException("Custody mutation lot and receipt do not match");
        }
        if (receipt.operation() == CustodyOperation.RESERVE) {
            if (resultingLot.revision() != 0L
                    || !resultingLot.reserveRequestKey().equals(receipt.requestKey())
                    || !resultingLot.holdEvidence().equals(receipt.evidence())) {
                throw new IllegalArgumentException("Custody reserve mutation is inconsistent");
            }
        } else if (resultingLot.revision() < 1L) {
            throw new IllegalArgumentException("Custody terminal mutation lacks a revision");
        }
    }

    public static CustodyMutation reserve(CustodyLot heldLot) {
        Objects.requireNonNull(heldLot, "heldLot");
        return new CustodyMutation(heldLot, CustodyOperationReceipt.reserve(heldLot));
    }

    public static CustodyMutation terminal(CustodyLot heldLot,
                                           CustodyOperation operation,
                                           String requestKey,
                                           CustodyTransferEvidence evidence,
                                           Instant now) {
        Objects.requireNonNull(heldLot, "heldLot");
        CustodyOperationReceipt receipt = CustodyOperationReceipt.terminal(
                heldLot, operation, requestKey, evidence, now);
        return new CustodyMutation(heldLot.transition(receipt.resultingState(), now), receipt);
    }
}
