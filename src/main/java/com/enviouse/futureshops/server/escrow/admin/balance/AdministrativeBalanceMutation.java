package com.enviouse.futureshops.server.escrow.admin.balance;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record AdministrativeBalanceMutation(
        UUID requestId,
        String actor,
        AdministrativeBalanceOperation operation,
        UUID targetPlayerId,
        Optional<UUID> counterpartyPlayerId,
        long amountMinor,
        boolean allowNegative,
        String reason,
        AdministrativeBalanceConfirmation confirmation
) {
    private static final byte[] FINGERPRINT_DOMAIN =
            "futureshops.admin.balance.mutation.v1"
                    .getBytes(StandardCharsets.UTF_8);

    public AdministrativeBalanceMutation {
        requestId = requireUuid(requestId, "requestId");
        actor = requireText(actor, "actor", 160);
        operation = Objects.requireNonNull(operation, "operation");
        targetPlayerId = requireUuid(targetPlayerId, "targetPlayerId");
        counterpartyPlayerId = Objects.requireNonNull(
                counterpartyPlayerId, "counterpartyPlayerId");
        reason = requireText(reason, "reason", 1024);
        confirmation = Objects.requireNonNull(
                confirmation, "confirmation");
        if (operation == AdministrativeBalanceOperation.TRANSFER) {
            UUID counterparty = requireUuid(
                    counterpartyPlayerId.orElseThrow(() ->
                            new IllegalArgumentException(
                                    "Balance transfer requires a counterparty")),
                    "counterpartyPlayerId");
        } else if (counterpartyPlayerId.isPresent()) {
            throw new IllegalArgumentException(
                    "Balance mutation has an unexpected counterparty");
        }
    }

    public String semanticFingerprint() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(FINGERPRINT_DOMAIN);
            updateUuid(digest, requestId);
            updateText(digest, actor);
            updateText(digest, operation.name());
            updateUuid(digest, targetPlayerId);
            digest.update((byte) (counterpartyPlayerId.isPresent() ? 1 : 0));
            counterpartyPlayerId.ifPresent(value -> updateUuid(digest, value));
            digest.update(ByteBuffer.allocate(Long.BYTES)
                    .putLong(amountMinor).array());
            digest.update((byte) (allowNegative ? 1 : 0));
            updateText(digest, reason);
            updateText(digest, confirmation.name());
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable", exception);
        }
    }

    private static UUID requireUuid(UUID value, String name) {
        UUID result = Objects.requireNonNull(value, name);
        if (result.getMostSignificantBits() == 0L
                && result.getLeastSignificantBits() == 0L) {
            throw new IllegalArgumentException("Invalid " + name);
        }
        return result;
    }

    private static String requireText(
            String value,
            String name,
            int maximumLength
    ) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty() || result.length() > maximumLength) {
            throw new IllegalArgumentException("Invalid " + name);
        }
        return result;
    }

    private static void updateUuid(MessageDigest digest, UUID value) {
        digest.update(ByteBuffer.allocate(Long.BYTES * 2)
                .putLong(value.getMostSignificantBits())
                .putLong(value.getLeastSignificantBits()).array());
    }

    private static void updateText(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES)
                .putInt(bytes.length).array());
        digest.update(bytes);
    }
}
