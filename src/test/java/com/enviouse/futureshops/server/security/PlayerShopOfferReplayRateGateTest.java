package com.enviouse.futureshops.server.security;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerShopOfferReplayRateGateTest {
    @Test
    void rateGatePrecedesExistingIntentReplayLookup() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/enviouse/futureshops/server/shop/"
                        + "PlayerShopEscrowTransactionService.java"));
        int method = source.indexOf("static void offer(");
        int gate = source.indexOf(
                "ServerRequestSecurityManager.tryAcquire(", method);
        int lock = source.indexOf(
                "PlayerShopBlockService.transactionLock(", method);
        int replay = source.indexOf(
                "PlayerShopLiveEscrowService.existingIntent(", method);

        assertTrue(method >= 0);
        assertTrue(gate > method);
        assertTrue(lock > gate);
        assertTrue(replay > lock);
    }
}
