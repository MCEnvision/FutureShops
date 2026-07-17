package com.enviouse.futureshops.client.screen;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

public final class HistoryTimestampFormatter {
    private static final DateTimeFormatter TWENTY_FOUR_HOUR =
            DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.US);
    private static final DateTimeFormatter TWELVE_HOUR =
            DateTimeFormatter.ofPattern("MM-dd h:mm a", Locale.US);

    private HistoryTimestampFormatter() {
    }

    public static String format(long epochSeconds, boolean twelveHourTime,
                                ZoneId zoneId) {
        DateTimeFormatter formatter = twelveHourTime
                ? TWELVE_HOUR : TWENTY_FOUR_HOUR;
        return formatter.withZone(Objects.requireNonNull(zoneId, "zoneId"))
                .format(Instant.ofEpochSecond(epochSeconds));
    }
}
