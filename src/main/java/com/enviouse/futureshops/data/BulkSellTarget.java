package com.enviouse.futureshops.data;

import java.util.Locale;

public enum BulkSellTarget {
    ADMIN_SHOP("adminshop"),
    PLAYER_SHOPS("playershops");

    private final String commandName;

    BulkSellTarget(String commandName) {
        this.commandName = commandName;
    }

    public String commandName() {
        return commandName;
    }

    public static BulkSellTarget fromCommandName(String value) {
        String normalized = value == null
                ? "" : value.strip().toLowerCase(Locale.ROOT);
        for (BulkSellTarget target : values()) {
            if (target.commandName.equals(normalized)) {
                return target;
            }
        }
        throw new IllegalArgumentException("Unknown bulk sell target");
    }
}
