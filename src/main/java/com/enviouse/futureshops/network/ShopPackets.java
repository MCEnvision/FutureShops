package com.enviouse.futureshops.network;

import com.enviouse.futureshops.Futureshops;
import com.enviouse.futureshops.network.packets.C2SBarterRequestPacket;
import com.enviouse.futureshops.network.packets.C2SBuyRequestPacket;
import com.enviouse.futureshops.network.packets.C2SFetchDepartmentsPacket;
import com.enviouse.futureshops.network.packets.C2SFetchHistoryPacket;
import com.enviouse.futureshops.network.packets.C2SFetchLocalShopsPacket;
import com.enviouse.futureshops.network.packets.C2SFetchSettlementHistoryPacket;
import com.enviouse.futureshops.network.packets.C2SInventorySyncPacket;
import com.enviouse.futureshops.network.packets.C2SFranchiseActionPacket;
import com.enviouse.futureshops.network.packets.C2SOpenBalTopUiPacket;
import com.enviouse.futureshops.network.packets.C2SOpenBalanceUiPacket;
import com.enviouse.futureshops.network.packets.C2SOpenShopPacket;
import com.enviouse.futureshops.network.packets.C2SPlayerShopActionPacket;
import com.enviouse.futureshops.network.packets.C2SPlayerShopBuyPacket;
import com.enviouse.futureshops.network.packets.C2SPlayerShopConfigPacket;
import com.enviouse.futureshops.network.packets.C2SPlayerShopPromoPacket;
import com.enviouse.futureshops.network.packets.C2SSellRequestPacket;
import com.enviouse.futureshops.network.packets.C2SSetDepartmentPacket;
import com.enviouse.futureshops.network.packets.C2SVerifyAdminCartPacket;
import com.enviouse.futureshops.network.packets.C2SVerifyCartPacket;
import com.enviouse.futureshops.network.packets.S2CBalTopUiPacket;
import com.enviouse.futureshops.network.packets.S2CBarterResponsePacket;
import com.enviouse.futureshops.network.packets.S2CBalanceUiPacket;
import com.enviouse.futureshops.network.packets.S2CBuyResponsePacket;
import com.enviouse.futureshops.network.packets.S2CDepartmentListPacket;
import com.enviouse.futureshops.network.packets.S2CForceClosePacket;
import com.enviouse.futureshops.network.packets.S2CFranchiseDataPacket;
import com.enviouse.futureshops.network.packets.S2CHistoryResponsePacket;
import com.enviouse.futureshops.network.packets.S2CInventorySyncPacket;
import com.enviouse.futureshops.network.packets.S2CLocalShopsPacket;
import com.enviouse.futureshops.network.packets.S2CPlayerShopDataPacket;
import com.enviouse.futureshops.network.packets.S2CPlayerShopResultPacket;
import com.enviouse.futureshops.network.packets.S2CSellResponsePacket;
import com.enviouse.futureshops.network.packets.S2CSettlementHistoryPacket;
import com.enviouse.futureshops.network.packets.S2CShopDataPacket;
import com.enviouse.futureshops.network.packets.S2CVerifyCartResponsePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ShopPackets {
    private static final String PROTOCOL_VERSION = "21";

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

        CHANNEL.messageBuilder(C2SBuyRequestPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .decoder(C2SBuyRequestPacket::decode)
            .encoder(C2SBuyRequestPacket::encode)
            .consumerMainThread(C2SBuyRequestPacket::handle)
            .add();

        CHANNEL.messageBuilder(C2SSellRequestPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .decoder(C2SSellRequestPacket::decode)
            .encoder(C2SSellRequestPacket::encode)
            .consumerMainThread(C2SSellRequestPacket::handle)
            .add();

        CHANNEL.messageBuilder(C2SBarterRequestPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .decoder(C2SBarterRequestPacket::decode)
            .encoder(C2SBarterRequestPacket::encode)
            .consumerMainThread(C2SBarterRequestPacket::handle)
            .add();

        CHANNEL.messageBuilder(C2SInventorySyncPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .decoder(C2SInventorySyncPacket::decode)
            .encoder(C2SInventorySyncPacket::encode)
            .consumerMainThread(C2SInventorySyncPacket::handle)
            .add();

        CHANNEL.messageBuilder(C2SFetchHistoryPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .decoder(C2SFetchHistoryPacket::decode)
            .encoder(C2SFetchHistoryPacket::encode)
            .consumerMainThread(C2SFetchHistoryPacket::handle)
            .add();

        CHANNEL.messageBuilder(C2SFetchSettlementHistoryPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .decoder(C2SFetchSettlementHistoryPacket::decode)
            .encoder(C2SFetchSettlementHistoryPacket::encode)
            .consumerMainThread(C2SFetchSettlementHistoryPacket::handle)
            .add();

        CHANNEL.messageBuilder(C2SOpenBalanceUiPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .decoder(C2SOpenBalanceUiPacket::decode)
            .encoder(C2SOpenBalanceUiPacket::encode)
            .consumerMainThread(C2SOpenBalanceUiPacket::handle)
            .add();

        CHANNEL.messageBuilder(C2SOpenBalTopUiPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .decoder(C2SOpenBalTopUiPacket::decode)
            .encoder(C2SOpenBalTopUiPacket::encode)
            .consumerMainThread(C2SOpenBalTopUiPacket::handle)
            .add();

        CHANNEL.messageBuilder(C2SPlayerShopActionPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .decoder(C2SPlayerShopActionPacket::decode)
            .encoder(C2SPlayerShopActionPacket::encode)
            .consumerMainThread(C2SPlayerShopActionPacket::handle)
            .add();

        CHANNEL.messageBuilder(C2SPlayerShopBuyPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .decoder(C2SPlayerShopBuyPacket::decode)
            .encoder(C2SPlayerShopBuyPacket::encode)
            .consumerMainThread(C2SPlayerShopBuyPacket::handle)
            .add();

        CHANNEL.messageBuilder(C2SPlayerShopPromoPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .decoder(C2SPlayerShopPromoPacket::decode)
            .encoder(C2SPlayerShopPromoPacket::encode)
            .consumerMainThread(C2SPlayerShopPromoPacket::handle)
            .add();

        CHANNEL.messageBuilder(C2SPlayerShopConfigPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .decoder(C2SPlayerShopConfigPacket::decode)
            .encoder(C2SPlayerShopConfigPacket::encode)
            .consumerMainThread(C2SPlayerShopConfigPacket::handle)
            .add();

        CHANNEL.messageBuilder(S2CBuyResponsePacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .decoder(S2CBuyResponsePacket::decode)
            .encoder(S2CBuyResponsePacket::encode)
            .consumerMainThread(S2CBuyResponsePacket::handle)
            .add();

        CHANNEL.messageBuilder(S2CSellResponsePacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .decoder(S2CSellResponsePacket::decode)
            .encoder(S2CSellResponsePacket::encode)
            .consumerMainThread(S2CSellResponsePacket::handle)
            .add();

        CHANNEL.messageBuilder(S2CBarterResponsePacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .decoder(S2CBarterResponsePacket::decode)
            .encoder(S2CBarterResponsePacket::encode)
            .consumerMainThread(S2CBarterResponsePacket::handle)
            .add();

        CHANNEL.messageBuilder(S2CInventorySyncPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .decoder(S2CInventorySyncPacket::decode)
            .encoder(S2CInventorySyncPacket::encode)
            .consumerMainThread(S2CInventorySyncPacket::handle)
            .add();

        CHANNEL.messageBuilder(S2CHistoryResponsePacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .decoder(S2CHistoryResponsePacket::decode)
            .encoder(S2CHistoryResponsePacket::encode)
            .consumerMainThread(S2CHistoryResponsePacket::handle)
            .add();

        CHANNEL.messageBuilder(S2CBalanceUiPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .decoder(S2CBalanceUiPacket::decode)
            .encoder(S2CBalanceUiPacket::encode)
            .consumerMainThread(S2CBalanceUiPacket::handle)
            .add();

        CHANNEL.messageBuilder(S2CBalTopUiPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .decoder(S2CBalTopUiPacket::decode)
            .encoder(S2CBalTopUiPacket::encode)
            .consumerMainThread(S2CBalTopUiPacket::handle)
            .add();

        CHANNEL.messageBuilder(S2CPlayerShopDataPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .decoder(S2CPlayerShopDataPacket::decode)
            .encoder(S2CPlayerShopDataPacket::encode)
            .consumerMainThread(S2CPlayerShopDataPacket::handle)
            .add();

        CHANNEL.messageBuilder(S2CPlayerShopResultPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .decoder(S2CPlayerShopResultPacket::decode)
            .encoder(S2CPlayerShopResultPacket::encode)
            .consumerMainThread(S2CPlayerShopResultPacket::handle)
            .add();

        CHANNEL.messageBuilder(S2CSettlementHistoryPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .decoder(S2CSettlementHistoryPacket::decode)
            .encoder(S2CSettlementHistoryPacket::encode)
            .consumerMainThread(S2CSettlementHistoryPacket::handle)
            .add();

        CHANNEL.messageBuilder(S2CForceClosePacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .decoder(S2CForceClosePacket::decode)
            .encoder(S2CForceClosePacket::encode)
            .consumerMainThread(S2CForceClosePacket::handle)
            .add();

        // Department system packets
        CHANNEL.messageBuilder(C2SSetDepartmentPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .decoder(C2SSetDepartmentPacket::decode)
            .encoder(C2SSetDepartmentPacket::encode)
            .consumerMainThread(C2SSetDepartmentPacket::handle)
            .add();

        CHANNEL.messageBuilder(C2SFetchDepartmentsPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .decoder(C2SFetchDepartmentsPacket::decode)
            .encoder(C2SFetchDepartmentsPacket::encode)
            .consumerMainThread(C2SFetchDepartmentsPacket::handle)
            .add();

        CHANNEL.messageBuilder(S2CDepartmentListPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .decoder(S2CDepartmentListPacket::decode)
            .encoder(S2CDepartmentListPacket::encode)
            .consumerMainThread(S2CDepartmentListPacket::handle)
            .add();

        // Local shops aggregation packets
        CHANNEL.messageBuilder(C2SFetchLocalShopsPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .decoder(C2SFetchLocalShopsPacket::decode)
            .encoder(C2SFetchLocalShopsPacket::encode)
            .consumerMainThread(C2SFetchLocalShopsPacket::handle)
            .add();

        CHANNEL.messageBuilder(S2CLocalShopsPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .decoder(S2CLocalShopsPacket::decode)
            .encoder(S2CLocalShopsPacket::encode)
            .consumerMainThread(S2CLocalShopsPacket::handle)
            .add();

        // Cart verification packets
        CHANNEL.messageBuilder(C2SVerifyCartPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .decoder(C2SVerifyCartPacket::decode)
            .encoder(C2SVerifyCartPacket::encode)
            .consumerMainThread(C2SVerifyCartPacket::handle)
            .add();

        CHANNEL.messageBuilder(S2CVerifyCartResponsePacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .decoder(S2CVerifyCartResponsePacket::decode)
            .encoder(S2CVerifyCartResponsePacket::encode)
            .consumerMainThread(S2CVerifyCartResponsePacket::handle)
            .add();

        // Admin cart verification packet
        CHANNEL.messageBuilder(C2SVerifyAdminCartPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .decoder(C2SVerifyAdminCartPacket::decode)
            .encoder(C2SVerifyAdminCartPacket::encode)
            .consumerMainThread(C2SVerifyAdminCartPacket::handle)
            .add();

        // Franchise management packets
        CHANNEL.messageBuilder(C2SFranchiseActionPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .decoder(C2SFranchiseActionPacket::decode)
            .encoder(C2SFranchiseActionPacket::encode)
            .consumerMainThread(C2SFranchiseActionPacket::handle)
            .add();

        CHANNEL.messageBuilder(S2CFranchiseDataPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .decoder(S2CFranchiseDataPacket::decode)
            .encoder(S2CFranchiseDataPacket::encode)
            .consumerMainThread(S2CFranchiseDataPacket::handle)
            .add();
    }

    public static int nextId() {
        return packetId++;
    }

    public static void sendToPlayer(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}
