package com.enviouse.futureshops.server.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerRequestSecurityLifecycleTest {
    @Test
    void bindsOneOwnerAndClearsEveryReferenceOnShutdown() {
        ServerRequestSecurityLifecycle<String> lifecycle =
                new ServerRequestSecurityLifecycle<>();
        Object firstServer = new Object();
        Object secondServer = new Object();

        assertEquals("first",
                lifecycle.initialize(firstServer, () -> "first"));
        assertTrue(lifecycle.active());
        assertEquals("first", lifecycle.find(firstServer).orElseThrow());
        assertTrue(lifecycle.find(secondServer).isEmpty());

        assertThrows(IllegalStateException.class,
                () -> lifecycle.initialize(firstServer, () -> "again"));
        assertThrows(IllegalStateException.class,
                () -> lifecycle.initialize(secondServer, () -> "second"));
        assertThrows(IllegalStateException.class,
                () -> lifecycle.clear(secondServer));

        assertEquals("first",
                lifecycle.clear(firstServer).orElseThrow());
        assertFalse(lifecycle.active());
        assertTrue(lifecycle.find(firstServer).isEmpty());
        assertTrue(lifecycle.clear(firstServer).isEmpty());

        assertEquals("second",
                lifecycle.initialize(secondServer, () -> "second"));
        assertEquals("second",
                lifecycle.clear(secondServer).orElseThrow());
        assertFalse(lifecycle.active());
    }

    @Test
    void failedInitializationDoesNotRetainOwnerOrValue() {
        ServerRequestSecurityLifecycle<String> lifecycle =
                new ServerRequestSecurityLifecycle<>();
        Object server = new Object();

        assertThrows(IllegalStateException.class,
                () -> lifecycle.initialize(server, () -> {
                    throw new IllegalStateException("failed");
                }));
        assertFalse(lifecycle.active());
        assertTrue(lifecycle.find(server).isEmpty());
        assertEquals("ready",
                lifecycle.initialize(server, () -> "ready"));
    }
}
