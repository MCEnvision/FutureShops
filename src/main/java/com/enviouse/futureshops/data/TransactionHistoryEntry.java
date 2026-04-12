package com.enviouse.futureshops.data;

import net.minecraft.network.FriendlyByteBuf;

import java.time.Instant;
import java.util.Locale;

/** One transaction-history row synced to the client history screen. */
public record TransactionHistoryEntry(
        long timestampEpochSeconds,
        String type,
        String itemId,
        int quantity,
        long totalMinorUnits,
        String note) {

    public static void encode(FriendlyByteBuf buffer, TransactionHistoryEntry entry) {
        buffer.writeLong(entry.timestampEpochSeconds);
        buffer.writeUtf(entry.type);
        buffer.writeUtf(entry.itemId);
        buffer.writeVarInt(entry.quantity);
        buffer.writeLong(entry.totalMinorUnits);
        buffer.writeUtf(entry.note);
    }

    public static TransactionHistoryEntry decode(FriendlyByteBuf buffer) {
        return new TransactionHistoryEntry(
                buffer.readLong(),
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readVarInt(),
                buffer.readLong(),
                buffer.readUtf());
    }

    public enum HistoryFilter {
        ALL,
        BUY,
        SELL,
        BARTER;

        public boolean matches(TransactionHistoryEntry entry) {
            if (this == ALL) {
                return true;
            }
            return entry.type() != null && entry.type().equalsIgnoreCase(name());
        }

        public static HistoryFilter fromWire(String value) {
            if (value == null || value.isBlank()) {
                return ALL;
            }
            try {
                return HistoryFilter.valueOf(value.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return ALL;
            }
        }
    }

    public enum SortOrder {
        NEWEST,
        OLDEST;

        public static SortOrder fromWire(String value) {
            if (value == null || value.isBlank()) {
                return NEWEST;
            }
            try {
                return SortOrder.valueOf(value.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return NEWEST;
            }
        }
    }

    public enum TimeWindow {
        ALL,
        DAY,
        WEEK,
        MONTH;

        public boolean matches(long epochSeconds) {
            if (this == ALL) {
                return true;
            }
            long now = Instant.now().getEpochSecond();
            long delta = now - epochSeconds;
            return switch (this) {
                case DAY -> delta <= 86_400L;
                case WEEK -> delta <= 604_800L;
                case MONTH -> delta <= 2_592_000L;
                case ALL -> true;
            };
        }

        public static TimeWindow fromWire(String value) {
            if (value == null || value.isBlank()) {
                return ALL;
            }
            try {
                return TimeWindow.valueOf(value.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return ALL;
            }
        }
    }
}

