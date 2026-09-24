package com.enviouse.futureshops.server.debug;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DebugRouteWiringTest {
    @Test
    void packetAndServiceRoutesUseTheBoundedDiagnosticProjection() throws Exception {
        String packet = Files.readString(Path.of(
                "src/main/java/com/enviouse/futureshops/network/packets/C2SServerShopOfferPacket.java"));
        String service = Files.readString(Path.of(
                "src/main/java/com/enviouse/futureshops/server/escrow/runtime/ServerShopOfferService.java"));
        assertTrue(packet.contains("DebugDiagnostics.record"));
        assertTrue(packet.contains("packet_received"));
        assertTrue(service.contains("DebugDiagnostics.record"));
        assertTrue(service.contains("recordDiagnostic"));
    }

    @Test
    void gametestExercisesTheRealServiceRoute() throws Exception {
        String gameTest = Files.readString(Path.of(
                "src/gametest/java/com/enviouse/futureshops/gametest/ServerShopOfferGameTests.java"));
        assertTrue(gameTest.contains(
                "diagnosticsCaptureTheRealOfferServiceRoute"));
        assertTrue(gameTest.contains("DebugSelector.request"));
        assertTrue(gameTest.contains("DebugDiagnostics.snapshot"));
    }
}
