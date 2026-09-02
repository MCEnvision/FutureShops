package com.enviouse.futureshops.network.packets;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketClaimCollectionErrorHandlingSourceTest {
    @Test
    void claimCollectionFailuresAreLoggedAndReturnedToTheClient() throws Exception {
        String packet = Files.readString(Path.of(
                "src/main/java/com/enviouse/futureshops/network/packets/C2SMarketClaimCollectionPacket.java"));
        String service = Files.readString(Path.of(
                "src/main/java/com/enviouse/futureshops/server/market/MarketClaimCollectionService.java"));
        assertTrue(packet.contains("LOGGER.error"));
        assertTrue(packet.contains("MarketClaimCollectionCode.SERVER_ERROR"));
        assertTrue(service.contains("LOGGER.error"));
        assertFalse(packet.contains("catch (RuntimeException ignored)"));
        assertFalse(service.contains("catch (RuntimeException ignored)"));
    }
}
