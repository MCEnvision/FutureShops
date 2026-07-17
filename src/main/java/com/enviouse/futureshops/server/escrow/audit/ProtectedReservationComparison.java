package com.enviouse.futureshops.server.escrow.audit;

import java.util.Objects;
import java.util.OptionalLong;

public record ProtectedReservationComparison(ProtectedReservationKey key,
                                             OptionalLong mintReservedQuantity,
                                             OptionalLong custodyHeldQuantity,
                                             boolean evidenceValid,
                                             boolean quantityMatches) {
    public ProtectedReservationComparison {
        Objects.requireNonNull(key, "key");
        mintReservedQuantity = Objects.requireNonNull(
                mintReservedQuantity, "mintReservedQuantity");
        custodyHeldQuantity = Objects.requireNonNull(
                custodyHeldQuantity, "custodyHeldQuantity");
        boolean exactMatch = mintReservedQuantity.isPresent()
                && custodyHeldQuantity.isPresent()
                && mintReservedQuantity.getAsLong() == custodyHeldQuantity.getAsLong();
        if (quantityMatches != exactMatch) {
            throw new IllegalArgumentException(
                    "Protected reservation comparison is inconsistent");
        }
    }
}
