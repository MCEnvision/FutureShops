package com.enviouse.futureshops.server.escrow.playershop;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public final class PlayerShopMutationPreparation {
    private final Kind kind;
    private final UUID mutationId;
    private final String subjectFingerprint;
    private final byte[] backendToken;
    private final String preparationFingerprint;

    public PlayerShopMutationPreparation(
            Kind kind,
            UUID mutationId,
            String subjectFingerprint,
            byte[] backendToken,
            String preparationFingerprint
    ) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.mutationId = PlayerShopBinarySupport.requireUuid(mutationId,
                "prepared mutation id");
        this.subjectFingerprint = PlayerShopBinarySupport.requireString(
                subjectFingerprint, 64, "prepared subject fingerprint");
        this.backendToken = PlayerShopBinarySupport.requireBytes(backendToken,
                PlayerShopEscrowConstants.MAX_COMPONENT_BYTES,
                "prepared backend token");
        this.preparationFingerprint = PlayerShopBinarySupport.requireString(
                preparationFingerprint, 64,
                "mutation preparation fingerprint");
        if (!computedFingerprint().equals(this.preparationFingerprint)) {
            throw new IllegalArgumentException("Player shop mutation preparation is invalid");
        }
    }

    public static PlayerShopMutationPreparation money(
            PlayerShopMoneyTransfer transfer,
            byte[] backendToken
    ) {
        Objects.requireNonNull(transfer, "transfer");
        return create(Kind.MONEY, transfer.transferId(),
                subjectFingerprint(Kind.MONEY, transfer, null, null),
                backendToken);
    }

    public static PlayerShopMutationPreparation item(
            PlayerShopItemTransfer transfer,
            byte[] backendToken
    ) {
        Objects.requireNonNull(transfer, "transfer");
        return create(Kind.ITEM, transfer.transferId(),
                subjectFingerprint(Kind.ITEM, null, transfer, null),
                backendToken);
    }

    public static PlayerShopMutationPreparation storage(
            PlayerShopStorageMutationPlan mutation,
            byte[] backendToken
    ) {
        Objects.requireNonNull(mutation, "mutation");
        return create(Kind.STORAGE, mutation.mutationId(),
                subjectFingerprint(Kind.STORAGE, null, null, mutation),
                backendToken);
    }

    boolean matches(PlayerShopMoneyTransfer transfer) {
        return kind == Kind.MONEY && mutationId.equals(transfer.transferId())
                && subjectFingerprint.equals(subjectFingerprint(kind,
                transfer, null, null));
    }

    boolean matches(PlayerShopItemTransfer transfer) {
        return kind == Kind.ITEM && mutationId.equals(transfer.transferId())
                && subjectFingerprint.equals(subjectFingerprint(kind,
                null, transfer, null));
    }

    boolean matches(PlayerShopStorageMutationPlan mutation) {
        return kind == Kind.STORAGE
                && mutationId.equals(mutation.mutationId())
                && subjectFingerprint.equals(subjectFingerprint(kind,
                null, null, mutation));
    }

    private static PlayerShopMutationPreparation create(
            Kind kind,
            UUID mutationId,
            String subjectFingerprint,
            byte[] backendToken
    ) {
        return new PlayerShopMutationPreparation(kind, mutationId,
                subjectFingerprint, backendToken,
                fingerprintOf(kind, mutationId, subjectFingerprint,
                        backendToken));
    }

    private String computedFingerprint() {
        return fingerprintOf(kind, mutationId, subjectFingerprint,
                backendToken);
    }

    private static String subjectFingerprint(
            Kind kind,
            PlayerShopMoneyTransfer money,
            PlayerShopItemTransfer item,
            PlayerShopStorageMutationPlan storage
    ) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeUTF("futureshops player shop prepared subject v1");
            output.writeByte(kind.ordinal());
            switch (kind) {
                case MONEY -> PlayerShopIntentCodec.writeMoneyTransfer(output,
                        Objects.requireNonNull(money, "money"));
                case ITEM -> PlayerShopIntentCodec.writeItemTransfer(output,
                        Objects.requireNonNull(item, "item"));
                case STORAGE -> PlayerShopIntentCodec.writeStorageMutation(
                        output, Objects.requireNonNull(storage, "storage"));
            }
            output.flush();
            return PlayerShopBinarySupport.sha256(bytes.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to fingerprint player shop subject", exception);
        }
    }

    private static String fingerprintOf(
            Kind kind,
            UUID mutationId,
            String subjectFingerprint,
            byte[] backendToken
    ) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeUTF("futureshops player shop mutation preparation v1");
            output.writeByte(kind.ordinal());
            PlayerShopBinarySupport.writeUuid(output, mutationId);
            PlayerShopBinarySupport.writeString(output, subjectFingerprint, 64);
            PlayerShopBinarySupport.writeBytes(output, backendToken,
                    PlayerShopEscrowConstants.MAX_COMPONENT_BYTES);
            output.flush();
            return PlayerShopBinarySupport.sha256(bytes.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to fingerprint player shop preparation", exception);
        }
    }

    public Kind kind() {
        return kind;
    }

    public UUID mutationId() {
        return mutationId;
    }

    public String subjectFingerprint() {
        return subjectFingerprint;
    }

    public byte[] backendToken() {
        return backendToken.clone();
    }

    public String preparationFingerprint() {
        return preparationFingerprint;
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof PlayerShopMutationPreparation other
                && kind == other.kind && mutationId.equals(other.mutationId)
                && subjectFingerprint.equals(other.subjectFingerprint)
                && Arrays.equals(backendToken, other.backendToken)
                && preparationFingerprint.equals(
                        other.preparationFingerprint);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hash(kind, mutationId, subjectFingerprint,
                preparationFingerprint) + Arrays.hashCode(backendToken);
    }

    public enum Kind {
        MONEY,
        ITEM,
        STORAGE
    }
}
