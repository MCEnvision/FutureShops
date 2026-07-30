package com.enviouse.futureshops;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HandshakePresentationSourceTest {
    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    @Test
    void serverListCompatibilityUsesTheNetworkProtocol() throws Exception {
        String mod = read("src/main/java/com/enviouse/futureshops/Futureshops.java");
        String metadata = read("src/main/resources/META-INF/mods.toml");
        assertTrue(mod.contains("context.registerDisplayTest(ShopPackets.PROTOCOL_VERSION"));
        assertTrue(metadata.contains("displayTest=\"NONE\""));
    }

    @Test
    void handshakeUsesSpecificMessagesInsteadOfAssumingTheClientIsOld()
            throws Exception {
        String mixin = read("src/main/java/com/enviouse/futureshops/mixin/HandshakeHandlerMixin.java");
        String policy = read("src/main/java/com/enviouse/futureshops/network/HandshakeCompatibilityPolicy.java");
        assertTrue(mixin.contains("PeerSide.SERVER"));
        assertTrue(mixin.contains("PeerSide.CLIENT"));
        assertTrue(policy.contains("MISSING_ON_CLIENT"));
        assertTrue(policy.contains("MISSING_ON_SERVER"));
        assertTrue(policy.contains("CLIENT_OUTDATED"));
        assertTrue(policy.contains("SERVER_OUTDATED"));
        assertFalse(mixin.contains("latest version"));
    }
}
