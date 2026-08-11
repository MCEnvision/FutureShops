package com.enviouse.futureshops.server.escrow.admin.balance;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

public final class AdministrativeBalanceRequestIds {
    private static final String DOMAIN =
            "futureshops.admin.balance.ids.v1";

    private AdministrativeBalanceRequestIds() {
    }

    public static UUID intent(UUID mutationRequestId) {
        return named("intent", mutationRequestId.toString());
    }

    public static UUID outcome(UUID mutationRequestId) {
        return named("outcome", mutationRequestId.toString());
    }

    public static UUID target(
            UUID batchRequestId,
            AdministrativeBalanceOperation operation,
            UUID targetPlayerId
    ) {
        Objects.requireNonNull(operation, "operation");
        return named("target", batchRequestId.toString(),
                operation.name(), targetPlayerId.toString());
    }

    private static UUID named(String... values) {
        StringBuilder encoded = new StringBuilder(DOMAIN);
        for (String value : values) {
            encoded.append('\u0000').append(
                    Objects.requireNonNull(value, "value"));
        }
        return UUID.nameUUIDFromBytes(encoded.toString()
                .getBytes(StandardCharsets.UTF_8));
    }
}
