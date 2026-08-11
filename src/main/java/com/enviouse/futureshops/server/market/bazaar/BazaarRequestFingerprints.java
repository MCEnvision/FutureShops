package com.enviouse.futureshops.server.market.bazaar;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

public final class BazaarRequestFingerprints {
    private BazaarRequestFingerprints() {
    }

    public static String create(CreateBazaarOrderCommand command) {
        return hash(output -> {
            text(output, "bazaar_request_v1");
            text(output, "create");
            id(output, command.requestId());
            id(output, command.orderId());
            id(output, command.ownerId());
            id(output, command.activationTransactionId());
            optionalId(output, command.moneyHoldAccountId());
            optionalId(output, command.custodyLotId());
            text(output, command.productId());
            number(output, command.productVersion());
            number(output, command.side().wireCode());
            number(output, command.type().wireCode());
            number(output, command.timeInForce().wireCode());
            number(output, command.limitPriceMinor());
            number(output, command.quantity());
            number(output, command.createdAtMillis());
            number(output, command.expiresAtMillis());
            rules(output, command.rules());
        });
    }

    public static String cancel(CancelBazaarOrderCommand command) {
        return hash(output -> {
            text(output, "bazaar_request_v1");
            text(output, "cancel");
            id(output, command.requestId());
            id(output, command.orderId());
            id(output, command.actorId());
            id(output, command.terminalTransactionId());
            number(output, command.expectedRevision());
            number(output, command.nowMillis());
        });
    }

    public static String expire(ExpireBazaarOrderCommand command) {
        return hash(output -> {
            text(output, "bazaar_request_v1");
            text(output, "expire");
            id(output, command.requestId());
            id(output, command.orderId());
            id(output, command.terminalTransactionId());
            number(output, command.expectedRevision());
            number(output, command.nowMillis());
        });
    }

    private static void rules(DataOutputStream output, BazaarRuleSnapshot rules) throws IOException {
        number(output, rules.makerFeeBasisPoints());
        number(output, rules.takerFeeBasisPoints());
        number(output, rules.maximumOrderQuantity());
        number(output, rules.maximumNotionalMinor());
        number(output, rules.maximumOpenOrdersPerPlayer());
        number(output, rules.maximumOpenOrdersPerProductPerPlayer());
        number(output, rules.maximumEscrowedValuePerPlayerMinor());
        number(output, rules.selfTradePolicy().wireCode());
        number(output, rules.executionPricePolicy().wireCode());
        output.writeBoolean(rules.circuitBreakerEnabled());
        number(output, rules.priceBandBasisPoints());
        number(output, rules.minimumLifetimeMillis());
        number(output, rules.configRevision());
    }

    private static String hash(Writer writer) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                writer.write(output);
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(bytes.toByteArray()));
        } catch (IOException exception) {
            throw new IllegalStateException("Bazaar fingerprint encoding failed", exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Bazaar fingerprint algorithm is unavailable", exception);
        }
    }

    private static void optionalId(DataOutputStream output, Optional<UUID> id) throws IOException {
        output.writeBoolean(id.isPresent());
        if (id.isPresent()) {
            id(output, id.orElseThrow());
        }
    }

    private static void id(DataOutputStream output, UUID id) throws IOException {
        output.writeLong(id.getMostSignificantBits());
        output.writeLong(id.getLeastSignificantBits());
    }

    private static void number(DataOutputStream output, long value) throws IOException {
        output.writeLong(value);
    }

    private static void text(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    @FunctionalInterface
    private interface Writer {
        void write(DataOutputStream output) throws IOException;
    }
}
