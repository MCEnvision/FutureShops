package com.enviouse.futureshops.server.escrow.playershop;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public final class PlayerShopItemMutationReceipt {
    private final UUID requestId;
    private final PlayerShopItemTransfer transfer;
    private final FundingKind fundingKind;
    private final byte[] custodyEvidence;
    private final String receiptFingerprint;

    public PlayerShopItemMutationReceipt(
            UUID requestId,
            PlayerShopItemTransfer transfer,
            FundingKind fundingKind,
            byte[] custodyEvidence,
            String receiptFingerprint
    ) {
        this.requestId = PlayerShopBinarySupport.requireUuid(requestId,
                "item receipt request id");
        this.transfer = Objects.requireNonNull(transfer, "transfer");
        this.fundingKind = Objects.requireNonNull(fundingKind, "fundingKind");
        this.custodyEvidence = PlayerShopBinarySupport.requireBytes(
                custodyEvidence, PlayerShopEscrowConstants.MAX_COMPONENT_BYTES,
                "item custody evidence");
        this.receiptFingerprint = PlayerShopBinarySupport.requireString(
                receiptFingerprint, 64, "item receipt fingerprint");
        validateFundingSource();
        if (!computedFingerprint().equals(this.receiptFingerprint)) {
            throw new IllegalArgumentException("Player shop item receipt is invalid");
        }
    }

    public static PlayerShopItemMutationReceipt funded(
            UUID requestId,
            PlayerShopItemTransfer transfer,
            FundingKind fundingKind,
            byte[] custodyEvidence
    ) {
        return new PlayerShopItemMutationReceipt(requestId, transfer,
                fundingKind, custodyEvidence,
                fingerprintOf(requestId, transfer, fundingKind,
                        custodyEvidence));
    }

    private void validateFundingSource() {
        PlayerShopAssetEndpoint.Kind source = transfer.source().kind();
        boolean valid = switch (fundingKind) {
            case INVENTORY_REMOVAL -> source
                    == PlayerShopAssetEndpoint.Kind.ACTOR_INVENTORY;
            case STORAGE_EXTRACTION -> source
                    == PlayerShopAssetEndpoint.Kind.LINKED_STOCK;
            case ADMIN_MINT -> source
                    == PlayerShopAssetEndpoint.Kind.ADMIN_MINT;
        };
        if (!valid) {
            throw new IllegalArgumentException("Player shop item funding source is invalid");
        }
    }

    private String computedFingerprint() {
        return fingerprintOf(requestId, transfer, fundingKind,
                custodyEvidence);
    }

    private static String fingerprintOf(
            UUID requestId,
            PlayerShopItemTransfer transfer,
            FundingKind fundingKind,
            byte[] custodyEvidence
    ) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeUTF("futureshops player shop item receipt v1");
            PlayerShopBinarySupport.writeUuid(output, requestId);
            PlayerShopIntentCodec.writeItemTransfer(output, transfer);
            output.writeByte(fundingKind.ordinal());
            PlayerShopBinarySupport.writeBytes(output, custodyEvidence,
                    PlayerShopEscrowConstants.MAX_COMPONENT_BYTES);
            output.flush();
            return PlayerShopBinarySupport.sha256(bytes.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to fingerprint player shop item receipt", exception);
        }
    }

    public UUID requestId() {
        return requestId;
    }

    public PlayerShopItemTransfer transfer() {
        return transfer;
    }

    public FundingKind fundingKind() {
        return fundingKind;
    }

    public byte[] custodyEvidence() {
        return custodyEvidence.clone();
    }

    public String receiptFingerprint() {
        return receiptFingerprint;
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof PlayerShopItemMutationReceipt other
                && requestId.equals(other.requestId)
                && transfer.equals(other.transfer)
                && fundingKind == other.fundingKind
                && Arrays.equals(custodyEvidence, other.custodyEvidence)
                && receiptFingerprint.equals(other.receiptFingerprint);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hash(requestId, transfer, fundingKind,
                receiptFingerprint) + Arrays.hashCode(custodyEvidence);
    }

    public enum FundingKind {
        INVENTORY_REMOVAL,
        STORAGE_EXTRACTION,
        ADMIN_MINT
    }
}
