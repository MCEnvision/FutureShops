package com.enviouse.futureshops.server.escrow.playershop;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

public record PlayerShopRequestIdentity(
        UUID requestId,
        int responseToken,
        UUID actorId,
        UUID registryShopId,
        long shopIdentityRevision,
        PlayerShopOperation operation,
        PlayerShopPaymentSource paymentSource,
        int requestedUnits,
        String listingRevision,
        String requestFingerprint
) {
    public static final int MAX_RESPONSE_TOKEN = 2_303;

    public PlayerShopRequestIdentity {
        requestId = PlayerShopBinarySupport.requireUuid(requestId, "request id");
        if (responseToken < 0 || responseToken > MAX_RESPONSE_TOKEN) {
            throw new IllegalArgumentException("Player shop response token is invalid");
        }
        actorId = PlayerShopBinarySupport.requireUuid(actorId, "request actor id");
        registryShopId = PlayerShopBinarySupport.requireUuid(registryShopId,
                "request shop id");
        if (shopIdentityRevision < 0L || requestedUnits <= 0) {
            throw new IllegalArgumentException("Player shop request values are invalid");
        }
        operation = Objects.requireNonNull(operation, "operation");
        paymentSource = Objects.requireNonNull(paymentSource, "paymentSource");
        listingRevision = PlayerShopBinarySupport.requireString(listingRevision,
                64, "request listing revision");
        requestFingerprint = PlayerShopBinarySupport.requireString(
                requestFingerprint, 64, "request fingerprint");
        if (!fingerprintOf(requestId, responseToken, actorId,
                registryShopId, shopIdentityRevision, operation,
                paymentSource, requestedUnits, listingRevision)
                .equals(requestFingerprint)) {
            throw new IllegalArgumentException("Player shop request fingerprint is invalid");
        }
    }

    public static PlayerShopRequestIdentity from(
            PlayerShopEscrowIntent intent,
            int responseToken
    ) {
        Objects.requireNonNull(intent, "intent");
        String listingRevision = intent.listing() == null
                ? PlayerShopBinarySupport.sha256(
                "settlement".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                : intent.listing().revisionFingerprint();
        String fingerprint = fingerprintOf(intent.requestId(), responseToken,
                intent.actorId(), intent.shopIdentity().registryShopId(),
                intent.shopIdentity().identityRevision(), intent.operation(),
                intent.paymentSource(), intent.requestedUnits(),
                listingRevision);
        return new PlayerShopRequestIdentity(intent.requestId(), responseToken,
                intent.actorId(), intent.shopIdentity().registryShopId(),
                intent.shopIdentity().identityRevision(), intent.operation(),
                intent.paymentSource(), intent.requestedUnits(),
                listingRevision, fingerprint);
    }

    public boolean matches(PlayerShopEscrowIntent intent) {
        try {
            PlayerShopRequestIdentity expected = from(intent, responseToken);
            return equals(expected);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static String fingerprintOf(
            UUID requestId,
            int responseToken,
            UUID actorId,
            UUID registryShopId,
            long shopIdentityRevision,
            PlayerShopOperation operation,
            PlayerShopPaymentSource source,
            int requestedUnits,
            String listingRevision
    ) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeUTF("futureshops player shop request identity v1");
            PlayerShopBinarySupport.writeUuid(output, requestId);
            output.writeInt(responseToken);
            PlayerShopBinarySupport.writeUuid(output, actorId);
            PlayerShopBinarySupport.writeUuid(output, registryShopId);
            output.writeLong(shopIdentityRevision);
            output.writeByte(operation.ordinal());
            output.writeByte(source.ordinal());
            output.writeInt(requestedUnits);
            PlayerShopBinarySupport.writeString(output, listingRevision, 64);
            output.flush();
            return PlayerShopBinarySupport.sha256(bytes.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to fingerprint player shop request", exception);
        }
    }
}
