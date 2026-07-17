package com.enviouse.futureshops.server.escrow.model;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record EscrowAssetLot(
        UUID lotId,
        EscrowAssetLotType type,
        EscrowProtectionLevel protectionLevel,
        EscrowParty source,
        EscrowParty destination,
        long quantity,
        Optional<MoneyAmount> money,
        byte[] serializedPayload,
        Map<String, String> attributes
) {
    public static final int MAX_ATTRIBUTE_COUNT = 64;
    public static final int MAX_ATTRIBUTE_KEY_LENGTH = 128;
    public static final int MAX_ATTRIBUTE_VALUE_LENGTH = 2048;

    public EscrowAssetLot {
        Objects.requireNonNull(lotId, "lotId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(protectionLevel, "protectionLevel");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(money, "money");
        Objects.requireNonNull(serializedPayload, "serializedPayload");
        Objects.requireNonNull(attributes, "attributes");
        if (source.equals(destination)) {
            throw new IllegalArgumentException("Escrow asset source and destination must differ");
        }
        if (quantity <= 0L) {
            throw new IllegalArgumentException("Escrow asset quantity must be positive");
        }
        serializedPayload = serializedPayload.clone();
        attributes = Map.copyOf(attributes);
        validateAttributes(attributes);
        validateShape(type, protectionLevel, quantity, money, serializedPayload, attributes);
    }

    @Override
    public byte[] serializedPayload() {
        return serializedPayload.clone();
    }

    private static void validateAttributes(Map<String, String> attributes) {
        if (attributes.size() > MAX_ATTRIBUTE_COUNT) {
            throw new IllegalArgumentException("Too many escrow asset attributes");
        }
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            if (entry.getKey().isBlank()
                    || entry.getValue().isBlank()
                    || entry.getKey().length() > MAX_ATTRIBUTE_KEY_LENGTH
                    || entry.getValue().length() > MAX_ATTRIBUTE_VALUE_LENGTH) {
                throw new IllegalArgumentException("Escrow asset attributes cannot be blank");
            }
        }
    }

    private static void validateShape(
            EscrowAssetLotType type,
            EscrowProtectionLevel protectionLevel,
            long quantity,
            Optional<MoneyAmount> money,
            byte[] serializedPayload,
            Map<String, String> attributes
    ) {
        if (type.isMoneyBacked() != money.isPresent()) {
            throw new IllegalArgumentException("Escrow asset money shape does not match its type");
        }
        if (money.isPresent() && money.orElseThrow().minorUnits() <= 0L) {
            throw new IllegalArgumentException("Escrow money asset must have positive value");
        }
        if ((type == EscrowAssetLotType.WALLET_MONEY
                || type == EscrowAssetLotType.FEE
                || type == EscrowAssetLotType.TAX) && quantity != 1L) {
            throw new IllegalArgumentException("Ledger money asset quantity must be one");
        }
        if (type.requiresSerializedPayload() && serializedPayload.length == 0) {
            throw new IllegalArgumentException("Escrow asset payload is required");
        }
        if (!type.requiresSerializedPayload() && serializedPayload.length != 0) {
            throw new IllegalArgumentException("Escrow asset payload is not allowed for this type");
        }
        if (type.isReservation() && !attributes.containsKey("resource_id")) {
            throw new IllegalArgumentException("Escrow reservation requires a resource id");
        }
        if ((type == EscrowAssetLotType.WALLET_MONEY
                || type == EscrowAssetLotType.PROTECTED_PHYSICAL_CURRENCY
                || type == EscrowAssetLotType.FEE
                || type == EscrowAssetLotType.TAX)
                && protectionLevel != EscrowProtectionLevel.PROTECTED) {
            throw new IllegalArgumentException("Protected money asset requires protected custody");
        }
        if (type == EscrowAssetLotType.FOREIGN_PHYSICAL_CURRENCY
                && protectionLevel != EscrowProtectionLevel.EXTERNAL) {
            throw new IllegalArgumentException("Foreign currency requires external protection level");
        }
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof EscrowAssetLot other)) {
            return false;
        }
        return quantity == other.quantity
                && lotId.equals(other.lotId)
                && type == other.type
                && protectionLevel == other.protectionLevel
                && source.equals(other.source)
                && destination.equals(other.destination)
                && money.equals(other.money)
                && Arrays.equals(serializedPayload, other.serializedPayload)
                && attributes.equals(other.attributes);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(lotId, type, protectionLevel, source, destination, quantity, money, attributes);
        return 31 * result + Arrays.hashCode(serializedPayload);
    }
}
