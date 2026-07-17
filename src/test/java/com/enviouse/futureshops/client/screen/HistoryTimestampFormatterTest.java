package com.enviouse.futureshops.client.screen;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HistoryTimestampFormatterTest {
    private static final ZoneId UTC = ZoneId.of("UTC");

    @Test
    void formatsMidnightInBothClockModes() {
        long timestamp = Instant.parse("2026-07-17T00:05:00Z")
                .getEpochSecond();

        assertEquals("07-17 00:05",
                HistoryTimestampFormatter.format(timestamp, false, UTC));
        assertEquals("07-17 12:05 AM",
                HistoryTimestampFormatter.format(timestamp, true, UTC));
    }

    @Test
    void formatsAfternoonInBothClockModes() {
        long timestamp = Instant.parse("2026-07-17T13:45:00Z")
                .getEpochSecond();

        assertEquals("07-17 13:45",
                HistoryTimestampFormatter.format(timestamp, false, UTC));
        assertEquals("07-17 1:45 PM",
                HistoryTimestampFormatter.format(timestamp, true, UTC));
    }

    @Test
    void appliesTheRequestedTimeZoneBeforeFormatting() {
        long timestamp = Instant.parse("2026-07-17T02:30:00Z")
                .getEpochSecond();
        ZoneId chicago = ZoneId.of("America/Chicago");

        assertEquals("07-16 21:30",
                HistoryTimestampFormatter.format(timestamp, false, chicago));
        assertEquals("07-16 9:30 PM",
                HistoryTimestampFormatter.format(timestamp, true, chicago));
    }
}
