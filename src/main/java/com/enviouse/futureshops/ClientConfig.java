package com.enviouse.futureshops;

import net.minecraftforge.common.ForgeConfigSpec;

/** Client-only presentation settings. */
public final class ClientConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.BooleanValue USE_12_HOUR_TIME = BUILDER
            .comment("Display transaction and settlement timestamps with 12-hour AM/PM time instead of 24-hour time.")
            .define("ui.use_12_hour_time", false);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    private ClientConfig() {
    }

    public static boolean use12HourTime() {
        return USE_12_HOUR_TIME.get();
    }
}
