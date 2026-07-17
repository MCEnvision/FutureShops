package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountType;
import com.enviouse.futureshops.server.escrow.ledger.LedgerLeg;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;

import java.util.Objects;

public record MoneyClaimSettlement(ClaimDeliveryCommit delivery, LedgerTransaction ledgerTransaction) {
    public MoneyClaimSettlement {
        Objects.requireNonNull(delivery, "delivery");
        Objects.requireNonNull(ledgerTransaction, "ledgerTransaction");
        if (ledgerTransaction.legs().size() != 2) {
            throw new IllegalArgumentException("Money claim settlement requires two ledger legs");
        }
        if (!ledgerTransaction.idempotencyKey().equals(delivery.requestKey())) {
            throw new IllegalArgumentException("Money claim settlement request keys do not match");
        }
        LedgerLeg claimLeg = ledgerTransaction.legs().stream()
                .filter(leg -> leg.account().type() == LedgerAccountType.PLAYER_CLAIM)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Money claim settlement is missing claim debit"));
        LedgerLeg walletLeg = ledgerTransaction.legs().stream()
                .filter(leg -> leg.account().type() == LedgerAccountType.PLAYER_WALLET)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Money claim settlement is missing wallet credit"));
        if (!claimLeg.account().ownerKey().equals(delivery.claimId().toString())
                || !walletLeg.account().ownerKey().equals(delivery.ownerId().toString())
                || claimLeg.deltaMinor() != -delivery.units()
                || walletLeg.deltaMinor() != delivery.units()) {
            throw new IllegalArgumentException("Money claim settlement ledger legs do not match delivery");
        }
    }
}
