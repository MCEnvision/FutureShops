package com.enviouse.futureshopsp.api.economy;

import net.minecraft.server.MinecraftServer;

import java.util.Objects;

/** Server context supplied when a registered provider is created. */
public record EconomyProviderContext(MinecraftServer server) {
    public EconomyProviderContext {
        Objects.requireNonNull(server, "server");
    }
}
