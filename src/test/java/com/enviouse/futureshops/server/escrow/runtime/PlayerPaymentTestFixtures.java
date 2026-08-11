package com.enviouse.futureshops.server.escrow.runtime;

import java.time.Instant;
import java.util.UUID;

final class PlayerPaymentTestFixtures {
    static final UUID REQUEST_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000001");
    static final UUID PAYER_ID = UUID.fromString(
            "20000000-0000-0000-0000-000000000002");
    static final UUID RECIPIENT_ID = UUID.fromString(
            "30000000-0000-0000-0000-000000000003");
    static final Instant NOW = Instant.parse("2026-07-17T12:00:00Z");

    private PlayerPaymentTestFixtures() {
    }

    static PlayerPaymentCommit direct() {
        return PlayerPaymentCommit.create(
                REQUEST_ID, PAYER_ID, RECIPIENT_ID,
                250L, 1_000L, 0L,
                100L, 0L, 0L,
                1_000L, "Coins", 2, NOW);
    }

    static PlayerPaymentCommit overflow() {
        return PlayerPaymentCommit.create(
                REQUEST_ID, PAYER_ID, RECIPIENT_ID,
                1_000L, 2_000L, 0L,
                0L, -50L, 0L,
                100L, "Coins", 2, NOW);
    }

    static PlayerPaymentCommit reservedOverflow() {
        return PlayerPaymentCommit.create(
                REQUEST_ID, PAYER_ID, RECIPIENT_ID,
                500L, 2_000L, 0L,
                100L, 0L, 850L,
                1_000L, "Coins", 2, NOW);
    }
}
