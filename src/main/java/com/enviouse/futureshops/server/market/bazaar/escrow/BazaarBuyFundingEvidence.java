package com.enviouse.futureshops.server.market.bazaar.escrow;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record BazaarBuyFundingEvidence(
        UUID requestId,
        UUID orderId,
        UUID ownerId,
        UUID holdAccountId,
        BazaarEscrowPaymentSource source,
        BazaarEscrowWalletSnapshot wallet,
        Optional<BazaarPhysicalFundingEvidence> physicalFunding,
        String currencyId
) {
    public static final int MAX_CURRENCY_ID_LENGTH = 128;

    public BazaarBuyFundingEvidence {
        requestId = BazaarEscrowIds.requireId(requestId, "requestId");
        orderId = BazaarEscrowIds.requireId(orderId, "orderId");
        ownerId = BazaarEscrowIds.requireId(ownerId, "ownerId");
        holdAccountId = BazaarEscrowIds.requireId(holdAccountId,
                "holdAccountId");
        source = Objects.requireNonNull(source, "source");
        wallet = Objects.requireNonNull(wallet, "wallet");
        physicalFunding = Objects.requireNonNull(physicalFunding,
                "physicalFunding");
        currencyId = requireCurrencyId(currencyId);
        boolean physicalBalanceDiffers = physicalFunding.isPresent()
                && physicalFunding.orElseThrow().resultingWalletMinor()
                != wallet.walletMinor();
        if (!wallet.ownerId().equals(ownerId)
                || source == BazaarEscrowPaymentSource.WALLET
                && physicalFunding.isPresent()
                || source == BazaarEscrowPaymentSource.INVENTORY_CASH
                && physicalFunding.isEmpty()
                || physicalBalanceDiffers) {
            throw new IllegalArgumentException(
                    "Bazaar buy funding shape is invalid");
        }
    }

    public void requireReserve(long reserveMinor) {
        wallet.requireAvailable(reserveMinor);
        if (source == BazaarEscrowPaymentSource.INVENTORY_CASH
                && physicalFunding.orElseThrow().walletCreditMinor()
                < reserveMinor) {
            throw new IllegalArgumentException(
                    "Bazaar physical funding did not credit the reserve");
        }
    }

    public static String requireCurrencyId(String value) {
        String result = Objects.requireNonNull(value, "currencyId").strip();
        if (result.isEmpty() || result.length() > MAX_CURRENCY_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "Bazaar currency identifier is invalid");
        }
        return result;
    }
}
