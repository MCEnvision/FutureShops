package com.enviouse.futureshops.server.escrow.playershop;

import java.util.Objects;
import java.util.UUID;

public record PlayerShopPacketResponseIdentity(
        UUID requestId,
        int responseToken,
        PlayerShopOperation operation
) {
    public PlayerShopPacketResponseIdentity {
        requestId = PlayerShopBinarySupport.requireUuid(requestId,
                "response request id");
        if (responseToken < 0
                || responseToken > PlayerShopRequestIdentity.MAX_RESPONSE_TOKEN) {
            throw new IllegalArgumentException("Player shop response token is invalid");
        }
        operation = Objects.requireNonNull(operation, "operation");
    }

    public static PlayerShopPacketResponseIdentity from(
            PlayerShopRequestIdentity request
    ) {
        Objects.requireNonNull(request, "request");
        return new PlayerShopPacketResponseIdentity(request.requestId(),
                request.responseToken(), request.operation());
    }
}
