package com.enviouse.futureshops.network;

import org.junit.jupiter.api.Test;

import static com.enviouse.futureshops.network.HandshakeCompatibilityPolicy.PeerSide.CLIENT;
import static com.enviouse.futureshops.network.HandshakeCompatibilityPolicy.PeerSide.SERVER;
import static com.enviouse.futureshops.network.HandshakeCompatibilityPolicy.Result.CLIENT_OUTDATED;
import static com.enviouse.futureshops.network.HandshakeCompatibilityPolicy.Result.INCOMPATIBLE;
import static com.enviouse.futureshops.network.HandshakeCompatibilityPolicy.Result.MATCH;
import static com.enviouse.futureshops.network.HandshakeCompatibilityPolicy.Result.MISSING_ON_CLIENT;
import static com.enviouse.futureshops.network.HandshakeCompatibilityPolicy.Result.MISSING_ON_SERVER;
import static com.enviouse.futureshops.network.HandshakeCompatibilityPolicy.Result.SERVER_OUTDATED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HandshakeCompatibilityPolicyTest {
    @Test
    void matchingProtocolsAreCompatible() {
        assertEquals(MATCH,
                HandshakeCompatibilityPolicy.evaluate("49", "49", SERVER));
        assertTrue(MATCH.compatible());
    }

    @Test
    void identifiesWhichSideIsMissingTheMod() {
        assertEquals(MISSING_ON_SERVER,
                HandshakeCompatibilityPolicy.evaluate("49", null, SERVER));
        assertEquals(MISSING_ON_CLIENT,
                HandshakeCompatibilityPolicy.evaluate("49", "", CLIENT));
    }

    @Test
    void identifiesOlderClientFromEitherHandshakeDirection() {
        assertEquals(CLIENT_OUTDATED,
                HandshakeCompatibilityPolicy.evaluate("49", "48", CLIENT));
        assertEquals(CLIENT_OUTDATED,
                HandshakeCompatibilityPolicy.evaluate("48", "49", SERVER));
    }

    @Test
    void identifiesOlderServerFromEitherHandshakeDirection() {
        assertEquals(SERVER_OUTDATED,
                HandshakeCompatibilityPolicy.evaluate("49", "48", SERVER));
        assertEquals(SERVER_OUTDATED,
                HandshakeCompatibilityPolicy.evaluate("48", "49", CLIENT));
    }

    @Test
    void unknownProtocolFormatsUseNeutralMessage() {
        assertEquals(INCOMPATIBLE,
                HandshakeCompatibilityPolicy.evaluate("49", "preview", SERVER));
        assertFalse(INCOMPATIBLE.compatible());
        assertTrue(INCOMPATIBLE.message().contains("matching futureshops versions"));
    }

    @Test
    void everyFailureHasSpecificGuidance() {
        assertTrue(MISSING_ON_CLIENT.message().contains("missing from your client"));
        assertTrue(MISSING_ON_SERVER.message().contains("missing from the server"));
        assertTrue(CLIENT_OUTDATED.message().contains("client"));
        assertTrue(SERVER_OUTDATED.message().contains("server owner"));
    }
}
