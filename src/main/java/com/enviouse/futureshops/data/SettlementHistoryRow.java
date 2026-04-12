package com.enviouse.futureshops.data;

import net.minecraft.network.FriendlyByteBuf;

import java.util.Locale;

public record SettlementHistoryRow(long timestampEpochSeconds, long amountMinor, String type) {
    public static void encode(FriendlyByteBuf buffer, SettlementHistoryRow row) {
        buffer.writeLong(row.timestampEpochSeconds());
        buffer.writeLong(row.amountMinor());
        buffer.writeUtf(row.type());
    }

    public static SettlementHistoryRow decode(FriendlyByteBuf buffer) {
        return new SettlementHistoryRow(buffer.readLong(), buffer.readLong(), buffer.readUtf());
    }

    public enum SettlementFilter {
        ALL,
        SALE,
        CLAIM,
        ROLLBACK;

        public boolean matches(String typeValue) {
            if (this == ALL) {
                return true;
            }
            return typeValue != null && typeValue.equalsIgnoreCase(name());
        }

        public static SettlementFilter fromWire(String value) {
            if (value == null || value.isBlank()) {
                return ALL;
            }
            try {
                return SettlementFilter.valueOf(value.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return ALL;
            }
        }
    }
}

