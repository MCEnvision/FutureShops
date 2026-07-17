package com.enviouse.futureshops.server.escrow.playershop;

import java.util.Objects;
import java.util.UUID;

public record PlayerShopMoneyTransfer(
        UUID transferId,
        PlayerShopAssetEndpoint source,
        PlayerShopAssetEndpoint destination,
        long amountMinorUnits,
        PlayerShopPaymentSource paymentSource,
        long sourceBalanceBeforeMinorUnits,
        long destinationBalanceBeforeMinorUnits
) {
    public static final long BALANCE_NOT_APPLICABLE = Long.MIN_VALUE;

    public PlayerShopMoneyTransfer {
        transferId = PlayerShopBinarySupport.requireUuid(transferId, "money transfer id");
        source = Objects.requireNonNull(source, "source");
        destination = Objects.requireNonNull(destination, "destination");
        paymentSource = Objects.requireNonNull(paymentSource, "paymentSource");
        if (amountMinorUnits <= 0L || source.equals(destination)) {
            throw new IllegalArgumentException("Player shop money transfer is invalid");
        }
        Math.addExact(amountMinorUnits, 0L);
        requireBalance(sourceBalanceBeforeMinorUnits, "source balance");
        requireBalance(destinationBalanceBeforeMinorUnits, "destination balance");
        validateSource(source, paymentSource);
    }

    public long sourceBalanceAfterMinorUnits() {
        return sourceBalanceBeforeMinorUnits == BALANCE_NOT_APPLICABLE
                ? BALANCE_NOT_APPLICABLE
                : Math.subtractExact(sourceBalanceBeforeMinorUnits, amountMinorUnits);
    }

    public long destinationBalanceAfterMinorUnits() {
        return destinationBalanceBeforeMinorUnits == BALANCE_NOT_APPLICABLE
                ? BALANCE_NOT_APPLICABLE
                : Math.addExact(destinationBalanceBeforeMinorUnits, amountMinorUnits);
    }

    private static void validateSource(
            PlayerShopAssetEndpoint source,
            PlayerShopPaymentSource paymentSource
    ) {
        switch (paymentSource) {
            case WALLET -> {
                if (source.kind() != PlayerShopAssetEndpoint.Kind.ACTOR_WALLET
                        && source.kind() != PlayerShopAssetEndpoint.Kind.OWNER_WALLET) {
                    throw new IllegalArgumentException("Player shop wallet source is invalid");
                }
            }
            case INVENTORY_CASH -> {
                if (source.kind() != PlayerShopAssetEndpoint.Kind.ACTOR_CASH) {
                    throw new IllegalArgumentException("Player shop cash source is invalid");
                }
            }
            case NONE -> {
                if (source.kind() == PlayerShopAssetEndpoint.Kind.ACTOR_WALLET
                        || source.kind() == PlayerShopAssetEndpoint.Kind.ACTOR_CASH) {
                    throw new IllegalArgumentException("Player shop system source is invalid");
                }
            }
        }
    }

    private static void requireBalance(long value, String label) {
        if (value != BALANCE_NOT_APPLICABLE && value < 0L) {
            throw new IllegalArgumentException("Player shop " + label + " is invalid");
        }
    }
}
