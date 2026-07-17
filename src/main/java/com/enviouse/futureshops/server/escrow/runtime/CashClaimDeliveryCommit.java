package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.custody.CashClaimCustodySupport;
import com.enviouse.futureshops.server.escrow.custody.CustodyAdapterCapability;
import com.enviouse.futureshops.server.escrow.custody.CustodyBatchCommit;
import com.enviouse.futureshops.server.escrow.custody.CustodyBatchStatus;
import com.enviouse.futureshops.server.escrow.custody.CustodyMutation;
import com.enviouse.futureshops.server.escrow.custody.CustodyOperation;
import com.enviouse.futureshops.server.escrow.custody.CustodyPreparedOperation;

import java.util.Objects;

public record CashClaimDeliveryCommit(
        ClaimDeliveryCommit delivery,
        CustodyBatchCommit custody
) {
    public CashClaimDeliveryCommit {
        Objects.requireNonNull(delivery, "delivery");
        Objects.requireNonNull(custody, "custody");
        if (custody.batch().status() != CustodyBatchStatus.APPLIED
                || custody.batch().operations().size() != 1
                || custody.mutations().size() != 1) {
            throw new IllegalArgumentException(
                    "Cash claim delivery custody batch is invalid");
        }
        CustodyPreparedOperation operation =
                custody.batch().operations().get(0);
        CustodyMutation mutation = custody.mutations().get(0);
        if (operation.operation() != CustodyOperation.RELEASE
                || !operation.adapterId().equals(
                CashClaimCustodySupport.PLAYER_INVENTORY_ADAPTER_ID)
                || operation.adapterCapability()
                != CustodyAdapterCapability.RECONCILABLE
                || !operation.requestKey().equals(delivery.requestKey())
                || delivery.units()
                != operation.lotSnapshot().units()
                || !delivery.deliveredAt().equals(
                custody.batch().updatedAt())
                || !mutation.receipt().requestKey().equals(
                delivery.requestKey())
                || mutation.receipt().operation()
                != CustodyOperation.RELEASE
                || !mutation.receipt().lotId().equals(
                operation.lotSnapshot().lotId())) {
            throw new IllegalArgumentException(
                    "Cash claim delivery does not match its custody proof");
        }
    }

    public CustodyMutation reserveMutation() {
        return CustodyMutation.reserve(
                custody.batch().operations().get(0).lotSnapshot());
    }
}
