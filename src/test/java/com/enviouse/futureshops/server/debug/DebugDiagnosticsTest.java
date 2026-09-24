package com.enviouse.futureshops.server.debug;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DebugDiagnosticsTest {
    private static final UUID ACTOR = UUID.fromString(
            "00000000-0000-0000-0000-000000000001");

    @AfterEach
    void reset() {
        DebugDiagnostics.reset();
    }

    @Test
    void startsOffAndParsesOnlyTheFrozenModules() {
        assertEquals("debug=off", DebugDiagnostics.statusLine());
        assertFalse(DebugDiagnostics.enabled(DebugModule.ECONOMY));
        assertEquals(DebugModule.PERSISTENCE,
                DebugModule.parse(" PERSISTENCE ").orElseThrow());
        assertTrue(DebugModule.parse("all").isPresent());
        assertTrue(DebugModule.parse("provider").isEmpty());
    }

    @Test
    void selectorAndCapturePseudonymsAreBounded() {
        DebugDiagnostics.DebugToggleResult enabled = DebugDiagnostics.enable(
                DebugModule.ECONOMY, DebugSelector.request("root-1"));
        assertTrue(enabled.changed());
        assertTrue(DebugDiagnostics.enabledFor(DebugModule.ECONOMY, "root-1", null));
        assertFalse(DebugDiagnostics.enabledFor(DebugModule.ECONOMY, "root-2", null));

        DebugDiagnostics.record(DebugModule.ECONOMY, "transfer", "root-1",
                "leg-1", ACTOR, "internal", "ready", "prepared", "prepared",
                "none", "balance=false", "prepared", "none", "none", "none",
                "flush intent");
        assertEquals(1, DebugDiagnostics.snapshot().size());
        DiagnosticEventV2 event = DebugDiagnostics.snapshot().get(0);
        assertNotEquals("root-1", event.rootRef());
        assertNotEquals(ACTOR.toString(), event.actorRef());
        assertEquals(1L, event.sequence());
        assertEquals(DebugModule.ECONOMY, event.module());
        assertTrue(DebugDiagnostics.statusLine().contains("module=economy"));
        assertFalse(DebugDiagnostics.statusLine().contains("root-1"));

        DebugDiagnostics.record(DebugModule.ECONOMY, "observe", "root-1", "leg-2",
                null, "internal", "ready", "none", "none",
                "balance:100 amount:2", "none", "none", "none", "none", "none",
                "observe");
        assertTrue(DebugDiagnostics.snapshot().get(1).reason().contains("redacted"));
    }

    @Test
    void disabledCaptureDoesNoEventWorkAndOffIsIdempotent() {
        DebugDiagnostics.record(DebugModule.SHOP, "buy", "root", "leg", ACTOR,
                "internal", "ready", "prepared", "confirmed", "none", "none",
                "confirmed", "confirmed", "released", "none", "complete");
        assertTrue(DebugDiagnostics.snapshot().isEmpty());
        DebugDiagnostics.disable();
        DebugDiagnostics.disable();
        assertEquals("debug=off", DebugDiagnostics.statusLine());
    }

    @Test
    void captureRateIsBounded() {
        DebugDiagnostics.enable(DebugModule.ALL);
        for (int index = 0; index < DebugDiagnostics.MAX_EVENTS_PER_SECOND + 20; index++) {
            DebugDiagnostics.record(DebugModule.SHOP, "observe", "root-" + index,
                    "leg", null, "internal", "ready", "none", "none", "none",
                    "none", "none", "none", "none", "none", "observe");
        }
        assertTrue(DebugDiagnostics.droppedEvents() > 0);
        assertTrue(DebugDiagnostics.snapshot().size()
                <= DebugDiagnostics.MAX_EVENTS_PER_SECOND);
    }
}
