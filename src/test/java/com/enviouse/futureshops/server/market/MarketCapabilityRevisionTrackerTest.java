package com.enviouse.futureshops.server.market;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketCapabilityRevisionTrackerTest {
    @Test
    void stableStateKeepsRevisionAndChangesAdvanceIt() {
        MarketCapabilityRevisionTracker tracker =
                new MarketCapabilityRevisionTracker(8);
        UUID player = UUID.randomUUID();

        long first = tracker.revision(player, "state one");
        assertEquals(first, tracker.revision(player, "state one"));
        long changed = tracker.revision(player, "state two");

        assertTrue(changed > first);
    }

    @Test
    void subjectCacheIsBoundedWithoutReusingRevisionNumbers() {
        MarketCapabilityRevisionTracker tracker =
                new MarketCapabilityRevisionTracker(1);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        long original = tracker.revision(first, "one");
        tracker.revision(second, "two");
        long restored = tracker.revision(first, "one");

        assertEquals(1, tracker.size());
        assertTrue(restored > original);
    }

    @Test
    void unsafeIdentityAndFingerprintBoundsAreRejected() {
        MarketCapabilityRevisionTracker tracker =
                new MarketCapabilityRevisionTracker(1);

        assertThrows(IllegalArgumentException.class,
                () -> tracker.revision(new UUID(0L, 0L), "state"));
        assertThrows(IllegalArgumentException.class,
                () -> tracker.revision(UUID.randomUUID(), ""));
        assertThrows(IllegalArgumentException.class,
                () -> tracker.revision(UUID.randomUUID(),
                        "x".repeat(MarketCapabilityRevisionTracker
                                .MAXIMUM_FINGERPRINT_LENGTH + 1)));
    }
}
