package com.enviouse.futureshops.network;

import com.enviouse.futureshops.Futureshops;
import com.enviouse.futureshops.network.packets.C2SOpenShopPacket;
import com.enviouse.futureshops.network.packets.S2CShopDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ShopPackets {
    private static final String PROTOCOL_VERSION = "2";

    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
        .named(ResourceLocation.parse(Futureshops.MODID + ":main"))
        .networkProtocolVersion(() -> PROTOCOL_VERSION)
        .clientAcceptedVersions(PROTOCOL_VERSION::equals)
        .serverAcceptedVersions(PROTOCOL_VERSION::equals)
        .simpleChannel();

    private static int packetId = 0;

    private ShopPackets() {
    }

    public static void register() {
        packetId = 0;

        CHANNEL.messageBuilder(C2SOpenShopPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .decoder(C2SOpenShopPacket::decode)
            .encoder(C2SOpenShopPacket::encode)
            .consumerMainThread(C2SOpenShopPacket::handle)
            .add();

        CHANNEL.messageBuilder(S2CShopDataPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .decoder(S2CShopDataPacket::decode)
            .encoder(S2CShopDataPacket::encode)
            .consumerMainThread(S2CShopDataPacket::handle)
            .add();
    }

    public static int nextId() {
        return packetId++;
    }

    public static void sendToPlayer(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}
