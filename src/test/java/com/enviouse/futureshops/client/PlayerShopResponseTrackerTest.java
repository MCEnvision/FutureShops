package com.enviouse.futureshops.client;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerShopResponseTrackerTest {
    @Test
    void matchingResponseConsumesExactlyOnePendingRequest() {
        PlayerShopResponseTracker tracker =
                new PlayerShopResponseTracker();
        PlayerShopResponseTracker.PendingRequest request = tracker.begin(
                PlayerShopResponseTracker.Operation.PURCHASE, 17);

        assertEquals(PlayerShopResponseTracker.Match.MATCHED,
                tracker.consume(request.requestId(), 17));
        assertEquals(PlayerShopResponseTracker.Match.STALE,
                tracker.consume(request.requestId(), 17));
        assertEquals(0, tracker.size());
    }

    @Test
    void tokenMismatchDoesNotConsumeThePendingRequest() {
        PlayerShopResponseTracker tracker =
                new PlayerShopResponseTracker();
        PlayerShopResponseTracker.PendingRequest request = tracker.begin(
                PlayerShopResponseTracker.Operation.BUYBACK, 31);

        assertEquals(PlayerShopResponseTracker.Match.TOKEN_MISMATCH,
                tracker.consume(request.requestId(), 30));
        assertTrue(tracker.pending(request.requestId()).isPresent());
        assertEquals(PlayerShopResponseTracker.Match.MATCHED,
                tracker.consume(request.requestId(), 31));
    }

    @Test
    void trackerEvictsOnlyTheOldestRequestAtItsBound() {
        PlayerShopResponseTracker tracker =
                new PlayerShopResponseTracker();
        List<UUID> requests = new ArrayList<>();
        for (int index = 0;
             index < PlayerShopResponseTracker.MAXIMUM_PENDING + 1;
             index++) {
            requests.add(tracker.begin(
                    PlayerShopResponseTracker.Operation.SETTLEMENT,
                    index).requestId());
        }

        assertEquals(PlayerShopResponseTracker.MAXIMUM_PENDING,
                tracker.size());
        assertFalse(tracker.pending(requests.get(0)).isPresent());
        assertTrue(tracker.pending(requests.get(1)).isPresent());
    }

    @Test
    void responseTokensAreBoundedToThePlayerShopWireRange() {
        PlayerShopResponseTracker tracker =
                new PlayerShopResponseTracker();

        tracker.begin(PlayerShopResponseTracker.Operation.PURCHASE,
                PlayerShopResponseTracker.MAXIMUM_RESPONSE_TOKEN);
        assertThrows(IllegalArgumentException.class,
                () -> tracker.begin(
                        PlayerShopResponseTracker.Operation.PURCHASE, -1));
        assertThrows(IllegalArgumentException.class,
                () -> tracker.begin(
                        PlayerShopResponseTracker.Operation.PURCHASE,
                        PlayerShopResponseTracker.MAXIMUM_RESPONSE_TOKEN
                                + 1));
    }
}
