package com.enviouse.futureshops.server.escrow.playershop;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public final class PlayerShopItemLot {
    private final UUID lotId;
    private final UUID sourceTransactionId;
    private final String sourceKey;
    private final int portionIndex;
    private final int portionCount;
    private final String itemId;
    private final int quantity;
    private final PlayerShopItemMatchMode matchMode;
    private final byte[] canonicalOneCountTemplate;
    private final byte[] serializedExactStack;
    private final String fingerprint;

    public PlayerShopItemLot(
            UUID lotId,
            UUID sourceTransactionId,
            String sourceKey,
            int portionIndex,
            int portionCount,
            String itemId,
            int quantity,
            PlayerShopItemMatchMode matchMode,
            byte[] canonicalOneCountTemplate,
            byte[] serializedExactStack,
            String fingerprint
    ) {
        this.sourceTransactionId = PlayerShopBinarySupport.requireUuid(
                sourceTransactionId, "source transaction id");
        this.sourceKey = PlayerShopBinarySupport.requireString(sourceKey,
                PlayerShopEscrowConstants.MAX_TEXT_LENGTH, "source key");
        if (portionCount <= 0 || portionCount > PlayerShopEscrowConstants.MAX_ITEM_PORTIONS
                || portionIndex < 0 || portionIndex >= portionCount) {
            throw new IllegalArgumentException("Player shop item portion is invalid");
        }
        this.portionIndex = portionIndex;
        this.portionCount = portionCount;
        this.itemId = PlayerShopBinarySupport.requireString(itemId,
                PlayerShopEscrowConstants.MAX_IDENTIFIER_LENGTH, "item id");
        if (quantity <= 0 || quantity > Byte.MAX_VALUE) {
            throw new IllegalArgumentException("Player shop item quantity is invalid");
        }
        this.quantity = quantity;
        this.matchMode = Objects.requireNonNull(matchMode, "matchMode");
        this.canonicalOneCountTemplate = PlayerShopBinarySupport.requireBytes(
                canonicalOneCountTemplate, PlayerShopEscrowConstants.MAX_COMPONENT_BYTES,
                "item template");
        this.serializedExactStack = PlayerShopBinarySupport.requireBytes(
                serializedExactStack, PlayerShopEscrowConstants.MAX_COMPONENT_BYTES,
                "item stack");
        this.lotId = PlayerShopBinarySupport.requireUuid(lotId, "lot id");
        this.fingerprint = PlayerShopBinarySupport.requireString(fingerprint, 64,
                "item fingerprint");
        if (!expectedLotId().equals(this.lotId)
                || !computedFingerprint().equals(this.fingerprint)) {
            throw new IllegalArgumentException("Player shop item identity is invalid");
        }
    }

    public static PlayerShopItemLot captureRaw(
            UUID sourceTransactionId,
            String sourceKey,
            int portionIndex,
            int portionCount,
            String itemId,
            int quantity,
            PlayerShopItemMatchMode matchMode,
            byte[] canonicalOneCountTemplate,
            byte[] serializedExactStack
    ) {
        UUID lotId = PlayerShopBinarySupport.deterministicUuid("item lot",
                sourceTransactionId, sourceKey + "." + portionIndex);
        PlayerShopItemLot provisional = new PlayerShopItemLot(
                lotId, sourceTransactionId, sourceKey, portionIndex,
                portionCount, itemId, quantity, matchMode,
                canonicalOneCountTemplate, serializedExactStack,
                fingerprintOf(sourceTransactionId, sourceKey, portionIndex,
                        portionCount, itemId, quantity, matchMode,
                        canonicalOneCountTemplate, serializedExactStack));
        return provisional;
    }

    private UUID expectedLotId() {
        return PlayerShopBinarySupport.deterministicUuid("item lot",
                sourceTransactionId, sourceKey + "." + portionIndex);
    }

    private String computedFingerprint() {
        return fingerprintOf(sourceTransactionId, sourceKey, portionIndex,
                portionCount, itemId, quantity, matchMode,
                canonicalOneCountTemplate, serializedExactStack);
    }

    private static String fingerprintOf(
            UUID transactionId,
            String sourceKey,
            int portionIndex,
            int portionCount,
            String itemId,
            int quantity,
            PlayerShopItemMatchMode matchMode,
            byte[] template,
            byte[] stack
    ) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeUTF("futureshops player shop item lot v1");
            PlayerShopBinarySupport.writeUuid(output, transactionId);
            PlayerShopBinarySupport.writeString(output, sourceKey,
                    PlayerShopEscrowConstants.MAX_TEXT_LENGTH);
            output.writeInt(portionIndex);
            output.writeInt(portionCount);
            PlayerShopBinarySupport.writeString(output, itemId,
                    PlayerShopEscrowConstants.MAX_IDENTIFIER_LENGTH);
            output.writeInt(quantity);
            output.writeByte(matchMode.ordinal());
            PlayerShopBinarySupport.writeBytes(output, template,
                    PlayerShopEscrowConstants.MAX_COMPONENT_BYTES);
            PlayerShopBinarySupport.writeBytes(output, stack,
                    PlayerShopEscrowConstants.MAX_COMPONENT_BYTES);
            output.flush();
            return PlayerShopBinarySupport.sha256(bytes.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to fingerprint player shop item", exception);
        }
    }

    public UUID lotId() {
        return lotId;
    }

    public UUID sourceTransactionId() {
        return sourceTransactionId;
    }

    public String sourceKey() {
        return sourceKey;
    }

    public int portionIndex() {
        return portionIndex;
    }

    public int portionCount() {
        return portionCount;
    }

    public String itemId() {
        return itemId;
    }

    public int quantity() {
        return quantity;
    }

    public PlayerShopItemMatchMode matchMode() {
        return matchMode;
    }

    public byte[] canonicalOneCountTemplate() {
        return canonicalOneCountTemplate.clone();
    }

    public byte[] serializedExactStack() {
        return serializedExactStack.clone();
    }

    public String fingerprint() {
        return fingerprint;
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof PlayerShopItemLot other
                && lotId.equals(other.lotId)
                && sourceTransactionId.equals(other.sourceTransactionId)
                && sourceKey.equals(other.sourceKey)
                && portionIndex == other.portionIndex
                && portionCount == other.portionCount
                && itemId.equals(other.itemId)
                && quantity == other.quantity
                && matchMode == other.matchMode
                && Arrays.equals(canonicalOneCountTemplate,
                        other.canonicalOneCountTemplate)
                && Arrays.equals(serializedExactStack,
                        other.serializedExactStack)
                && fingerprint.equals(other.fingerprint);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(lotId, sourceTransactionId, sourceKey,
                portionIndex, portionCount, itemId, quantity, matchMode,
                fingerprint);
        result = 31 * result + Arrays.hashCode(canonicalOneCountTemplate);
        return 31 * result + Arrays.hashCode(serializedExactStack);
    }
}
