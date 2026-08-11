package com.enviouse.futureshops.client.editor;

import com.enviouse.futureshops.catalog.AdminShopOfferConfigWriter;
import com.enviouse.futureshops.network.packets
        .S2CAdminOfferSaveResultPacket;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminOfferSaveResultTrackerTest {
    @Test
    void onlyMatchingAcknowledgementIsConsumed() {
        AdminOfferSaveResultTracker tracker =
                new AdminOfferSaveResultTracker();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        tracker.record(result(first));
        tracker.record(result(second));

        assertTrue(tracker.take(UUID.randomUUID()).isEmpty());
        assertEquals(first, tracker.take(first)
                .orElseThrow().requestId());
        assertEquals(second, tracker.take(second)
                .orElseThrow().requestId());
        assertTrue(tracker.take(first).isEmpty());
    }

    @Test
    void deferredResultsAreBoundedAndDuplicateSafe() {
        AdminOfferSaveResultTracker tracker =
                new AdminOfferSaveResultTracker();
        UUID duplicate = UUID.randomUUID();
        tracker.record(result(duplicate));
        tracker.record(result(duplicate));
        for (int index = 0; index < 32; index++) {
            tracker.record(result(UUID.randomUUID()));
        }

        assertEquals(16, tracker.size());
    }

    private static S2CAdminOfferSaveResultPacket result(
            UUID requestId
    ) {
        return new S2CAdminOfferSaveResultPacket(
                requestId, AdminShopOfferConfigWriter.Status.STALE,
                false, 0L, Optional.empty(), List.of());
    }
}
