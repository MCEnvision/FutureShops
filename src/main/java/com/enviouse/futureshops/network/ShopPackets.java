package com.enviouse.futureshops.network;

import com.enviouse.futureshops.Futureshops;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ShopPackets {
    private static final String PROTOCOL_VERSION = "2";

    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
        .named(new ResourceLocation(Futureshops.MODID, "main"))
        .networkProtocolVersion(() -> PROTOCOL_VERSION)
        .clientAcceptedVersions(PROTOCOL_VERSION::equals)
        .serverAcceptedVersions(PROTOCOL_VERSION::equals)
        .simpleChannel();

    private static int packetId = 0;

    private ShopPackets() {
    }

    public static void register() {
        packetId = 0;
        // Packet registrations are added here as feature slices are implemented.
    }

    public static int nextId() {
        return packetId++;
    }
}

