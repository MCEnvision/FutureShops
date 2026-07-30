package com.enviouse.futureshops.server.escrow.runtime;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerShopOfferAutomaticRecoverySourceTest {
    private static final Path RUNTIME =
            Path.of("src/main/java/com/enviouse/futureshops/server/"
                    + "escrow/runtime");

    @Test
    void trustedRecoveryUsesOnlyPersistedOfferEvidence()
            throws IOException {
        assertTrustedRecovery(
                Files.readString(RUNTIME.resolve(
                        "ServerShopOfferService.java")));
        assertTrustedRecovery(
                Files.readString(RUNTIME.resolve(
                        "ServerShopOfferCartService.java")));
    }

    @Test
    void loginAndTickRecoveryAreBounded() throws IOException {
        String source = Files.readString(RUNTIME.resolve(
                "ServerShopOfferAutomaticRecovery.java"));
        assertTrue(source.contains(
                "PlayerEvent.PlayerLoggedInEvent"));
        assertTrue(source.contains("TickEvent.ServerTickEvent"));
        assertTrue(source.contains("TICK_ATTEMPT_LIMIT = 8"));
        assertTrue(source.contains("PLAYER_TICK_ATTEMPT_LIMIT = 2"));
        assertTrue(source.contains("TICK_INTERVAL = 40"));
        assertTrue(source.contains(
                "ServerShopOfferService.recoverPersisted"));
        assertTrue(source.contains(
                "ServerShopOfferCartService.recoverPersisted"));
    }

    @Test
    void unresolvedQueuesLeaveCompletedOffersBehind()
            throws IOException {
        assertUnresolvedQueue(
                Files.readString(RUNTIME.resolve(
                        "ServerShopOfferPreparedSavedData.java")));
        assertUnresolvedQueue(
                Files.readString(RUNTIME.resolve(
                        "ServerShopOfferCartPreparedSavedData.java")));
    }

    private static void assertTrustedRecovery(String source) {
        int start = source.indexOf(
                "public static Result recoverPersisted");
        int end = source.indexOf(
                "private static Request requestFrom", start);
        assertTrue(start >= 0);
        assertTrue(end > start);
        String recovery = source.substring(start, end);
        String compact = recovery.replaceAll("\\s+", " ");
        assertTrue(compact.contains("preparedEntries.find(requestId)"));
        assertTrue(compact.contains("player.getUUID()"));
        assertTrue(compact.contains("entry::equals"));
        assertTrue(compact.contains("runtime, true)"));
        assertFalse(compact.contains("ShopSessionManager"));
        assertFalse(compact.contains("AdminShopToggleSavedData"));
        assertFalse(compact.contains(
                "ServerRequestSecurityManager.tryAcquire"));
    }

    private static void assertUnresolvedQueue(String source) {
        assertTrue(source.contains("unresolvedByPlayer"));
        assertTrue(source.contains("takeUnresolvedForPlayer"));
        assertTrue(source.contains(
                "removeUnresolved(receipt.playerId(), receipt.requestId())"));
    }
}
