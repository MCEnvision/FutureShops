package com.enviouse.futureshops.server.market.bazaar.escrow;

import java.util.Objects;
import java.util.UUID;

public record BazaarPhysicalFundingEvidence(
        UUID depositRequestId,
        UUID depositTransactionId,
        String currencySignature,
        long depositedMinor,
        long walletCreditMinor,
        long overflowClaimMinor,
        long resultingWalletMinor
) {
    public BazaarPhysicalFundingEvidence {
        depositRequestId = BazaarEscrowIds.requireId(depositRequestId,
                "depositRequestId");
        depositTransactionId = BazaarEscrowIds.requireId(
                depositTransactionId, "depositTransactionId");
        currencySignature = Objects.requireNonNull(currencySignature,
                "currencySignature");
        if (!currencySignature.matches("[0-9a-f]{64}")
                || depositedMinor <= 0L || walletCreditMinor < 0L
                || overflowClaimMinor < 0L || resultingWalletMinor < 0L
                || Math.addExact(walletCreditMinor, overflowClaimMinor)
                != depositedMinor) {
            throw new IllegalArgumentException(
                    "Bazaar physical funding evidence is invalid");
        }
    }
}
