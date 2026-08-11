package com.enviouse.futureshops.server.market.bazaar;

import java.util.Objects;
import java.util.Optional;

public record BazaarRequestReceipt(
        String fingerprint,
        BazaarOperationResult result,
        Optional<CreateBazaarOrderCommand> createCommand,
        Optional<CancelBazaarOrderCommand> cancelCommand,
        Optional<ExpireBazaarOrderCommand> expireCommand
) {
    public BazaarRequestReceipt {
        Objects.requireNonNull(fingerprint, "fingerprint");
        Objects.requireNonNull(result, "result");
        createCommand = Objects.requireNonNull(createCommand, "createCommand");
        cancelCommand = Objects.requireNonNull(cancelCommand, "cancelCommand");
        expireCommand = Objects.requireNonNull(expireCommand, "expireCommand");
        int commandCount = (createCommand.isPresent() ? 1 : 0)
                + (cancelCommand.isPresent() ? 1 : 0)
                + (expireCommand.isPresent() ? 1 : 0);
        java.util.UUID commandRequestId = null;
        java.util.UUID commandOrderId = null;
        String expectedFingerprint = "";
        if (createCommand.isPresent()) {
            CreateBazaarOrderCommand command = createCommand.orElseThrow();
            commandRequestId = command.requestId();
            commandOrderId = command.orderId();
            expectedFingerprint = BazaarRequestFingerprints.create(command);
        } else if (cancelCommand.isPresent()) {
            CancelBazaarOrderCommand command = cancelCommand.orElseThrow();
            commandRequestId = command.requestId();
            commandOrderId = command.orderId();
            expectedFingerprint = BazaarRequestFingerprints.cancel(command);
        } else if (expireCommand.isPresent()) {
            ExpireBazaarOrderCommand command = expireCommand.orElseThrow();
            commandRequestId = command.requestId();
            commandOrderId = command.orderId();
            expectedFingerprint = BazaarRequestFingerprints.expire(command);
        }
        if (!fingerprint.matches("[0-9a-f]{64}") || commandCount != 1
                || result.replayed()
                || createCommand.isPresent()
                && result.operation() != BazaarOperationType.CREATE
                || cancelCommand.isPresent()
                && result.operation() != BazaarOperationType.CANCEL
                || expireCommand.isPresent()
                && result.operation() != BazaarOperationType.EXPIRE
                || !result.requestId().equals(commandRequestId)
                || !result.orderId().equals(commandOrderId)
                || !fingerprint.equals(expectedFingerprint)) {
            throw new IllegalArgumentException("Bazaar request fingerprint is invalid");
        }
    }

    public static BazaarRequestReceipt create(String fingerprint,
                                               CreateBazaarOrderCommand command,
                                               BazaarOperationResult result) {
        return new BazaarRequestReceipt(fingerprint, result, Optional.of(command),
                Optional.empty(), Optional.empty());
    }

    public static BazaarRequestReceipt cancel(String fingerprint,
                                               CancelBazaarOrderCommand command,
                                               BazaarOperationResult result) {
        return new BazaarRequestReceipt(fingerprint, result, Optional.empty(),
                Optional.of(command), Optional.empty());
    }

    public static BazaarRequestReceipt expire(String fingerprint,
                                               ExpireBazaarOrderCommand command,
                                               BazaarOperationResult result) {
        return new BazaarRequestReceipt(fingerprint, result, Optional.empty(),
                Optional.empty(), Optional.of(command));
    }

    public java.util.UUID requestId() {
        return createCommand.map(CreateBazaarOrderCommand::requestId)
                .or(() -> cancelCommand.map(CancelBazaarOrderCommand::requestId))
                .or(() -> expireCommand.map(ExpireBazaarOrderCommand::requestId))
                .orElseThrow();
    }

    public java.util.UUID orderId() {
        return createCommand.map(CreateBazaarOrderCommand::orderId)
                .or(() -> cancelCommand.map(CancelBazaarOrderCommand::orderId))
                .or(() -> expireCommand.map(ExpireBazaarOrderCommand::orderId))
                .orElseThrow();
    }

    public String canonicalFingerprint() {
        return createCommand.map(BazaarRequestFingerprints::create)
                .or(() -> cancelCommand.map(BazaarRequestFingerprints::cancel))
                .or(() -> expireCommand.map(BazaarRequestFingerprints::expire))
                .orElseThrow();
    }
}
