package com.enviouse.futureshops.server.escrow.playershop;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public final class PlayerShopMoneyMutationReceipt {
    private final UUID requestId;
    private final PlayerShopMoneyTransfer transfer;
    private final long sourceBalanceAfterMinorUnits;
    private final long destinationBalanceAfterMinorUnits;
    private final byte[] providerEvidence;
    private final String receiptFingerprint;

    public PlayerShopMoneyMutationReceipt(
            UUID requestId,
            PlayerShopMoneyTransfer transfer,
            long sourceBalanceAfterMinorUnits,
            long destinationBalanceAfterMinorUnits,
            byte[] providerEvidence,
            String receiptFingerprint
    ) {
        this.requestId = PlayerShopBinarySupport.requireUuid(requestId,
                "money receipt request id");
        this.transfer = Objects.requireNonNull(transfer, "transfer");
        this.sourceBalanceAfterMinorUnits = sourceBalanceAfterMinorUnits;
        this.destinationBalanceAfterMinorUnits = destinationBalanceAfterMinorUnits;
        this.providerEvidence = PlayerShopBinarySupport.requireBytes(
                providerEvidence, PlayerShopEscrowConstants.MAX_COMPONENT_BYTES,
                "money provider evidence");
        this.receiptFingerprint = PlayerShopBinarySupport.requireString(
                receiptFingerprint, 64, "money receipt fingerprint");
        requireExpectedBalance(transfer.sourceBalanceBeforeMinorUnits(),
                sourceBalanceAfterMinorUnits, true);
        requireExpectedBalance(transfer.destinationBalanceBeforeMinorUnits(),
                destinationBalanceAfterMinorUnits, false);
        if (!computedFingerprint().equals(this.receiptFingerprint)) {
            throw new IllegalArgumentException("Player shop money receipt is invalid");
        }
    }

    public static PlayerShopMoneyMutationReceipt applied(
            UUID requestId,
            PlayerShopMoneyTransfer transfer,
            long sourceBalanceAfterMinorUnits,
            long destinationBalanceAfterMinorUnits,
            byte[] providerEvidence
    ) {
        return new PlayerShopMoneyMutationReceipt(requestId, transfer,
                sourceBalanceAfterMinorUnits, destinationBalanceAfterMinorUnits,
                providerEvidence, fingerprintOf(requestId, transfer,
                        sourceBalanceAfterMinorUnits,
                        destinationBalanceAfterMinorUnits, providerEvidence));
    }

    private void requireExpectedBalance(long before, long actual,
                                        boolean debit) {
        if (before == PlayerShopMoneyTransfer.BALANCE_NOT_APPLICABLE) {
            if (actual != PlayerShopMoneyTransfer.BALANCE_NOT_APPLICABLE) {
                throw new IllegalArgumentException("Player shop money receipt balance is invalid");
            }
            return;
        }
        long expected = debit ? Math.subtractExact(before,
                transfer.amountMinorUnits()) : Math.addExact(before,
                transfer.amountMinorUnits());
        if (actual != expected || actual < 0L) {
            throw new IllegalArgumentException("Player shop money receipt balance is invalid. Expected "
                    + expected + " and found " + actual);
        }
    }

    private String computedFingerprint() {
        return fingerprintOf(requestId, transfer, sourceBalanceAfterMinorUnits,
                destinationBalanceAfterMinorUnits, providerEvidence);
    }

    private static String fingerprintOf(
            UUID requestId,
            PlayerShopMoneyTransfer transfer,
            long sourceAfter,
            long destinationAfter,
            byte[] providerEvidence
    ) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeUTF("futureshops player shop money receipt v1");
            PlayerShopBinarySupport.writeUuid(output, requestId);
            PlayerShopIntentCodec.writeMoneyTransfer(output, transfer);
            output.writeLong(sourceAfter);
            output.writeLong(destinationAfter);
            PlayerShopBinarySupport.writeBytes(output, providerEvidence,
                    PlayerShopEscrowConstants.MAX_COMPONENT_BYTES);
            output.flush();
            return PlayerShopBinarySupport.sha256(bytes.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to fingerprint player shop money receipt", exception);
        }
    }

    public UUID requestId() {
        return requestId;
    }

    public PlayerShopMoneyTransfer transfer() {
        return transfer;
    }

    public long sourceBalanceAfterMinorUnits() {
        return sourceBalanceAfterMinorUnits;
    }

    public long destinationBalanceAfterMinorUnits() {
        return destinationBalanceAfterMinorUnits;
    }

    public byte[] providerEvidence() {
        return providerEvidence.clone();
    }

    public String receiptFingerprint() {
        return receiptFingerprint;
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof PlayerShopMoneyMutationReceipt other
                && requestId.equals(other.requestId)
                && transfer.equals(other.transfer)
                && sourceBalanceAfterMinorUnits
                        == other.sourceBalanceAfterMinorUnits
                && destinationBalanceAfterMinorUnits
                        == other.destinationBalanceAfterMinorUnits
                && Arrays.equals(providerEvidence, other.providerEvidence)
                && receiptFingerprint.equals(other.receiptFingerprint);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hash(requestId, transfer,
                sourceBalanceAfterMinorUnits,
                destinationBalanceAfterMinorUnits, receiptFingerprint)
                + Arrays.hashCode(providerEvidence);
    }
}
