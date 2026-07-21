package com.enviouse.futureshops.network;

import com.enviouse.futureshops.Futureshops;
import com.enviouse.futureshops.network.packets.C2SAdminShopAddItemsPacket;
import com.enviouse.futureshops.network.packets.C2SAdminShopEditPacket;
import com.enviouse.futureshops.network.packets.C2SAtmWithdrawPacket;
import com.enviouse.futureshops.network.packets.C2SAtmCollectCashPacket;
import com.enviouse.futureshops.network.packets.C2SAtmDepositPacket;
import com.enviouse.futureshops.network.packets.C2SBarterRequestPacket;
import com.enviouse.futureshops.network.packets.C2SCloseMarketSessionPacket;
import com.enviouse.futureshops.network.packets.C2SBuyRequestPacket;
import com.enviouse.futureshops.network.packets.C2SFetchDepartmentsPacket;
import com.enviouse.futureshops.network.packets.C2SFetchHistoryPacket;
import com.enviouse.futureshops.network.packets.C2SFetchLocalShopsPacket;
import com.enviouse.futureshops.network.packets.C2SFetchSettlementHistoryPacket;
import com.enviouse.futureshops.network.packets.C2SInventorySyncPacket;
import com.enviouse.futureshops.network.packets.C2SFranchiseActionPacket;
import com.enviouse.futureshops.network.packets.C2SOpenBalTopUiPacket;
import com.enviouse.futureshops.network.packets.C2SOpenAtmPacket;
import com.enviouse.futureshops.network.packets.C2SOpenBalanceUiPacket;
import com.enviouse.futureshops.network.packets.C2SOpenShopPacket;
import com.enviouse.futureshops.network.packets.C2SOpenMarketModulePacket;
import com.enviouse.futureshops.network.packets.C2SMarketPageQueryPacket;
import com.enviouse.futureshops.network.packets.C2SMarketCapabilitiesPacket;
import com.enviouse.futureshops.network.packets.C2SMarketClaimCollectionPacket;
import com.enviouse.futureshops.network.packets.C2SMarketProfileMutationPacket;
import com.enviouse.futureshops.network.packets.C2SPlayerShopActionPacket;
import com.enviouse.futureshops.network.packets.C2SPlayerShopBuyPacket;
import com.enviouse.futureshops.network.packets.C2SPlayerShopBuybackConfigPacket;
import com.enviouse.futureshops.network.packets.C2SPlayerShopConfigPacket;
import com.enviouse.futureshops.network.packets.C2SPlayerShopIconPacket;
import com.enviouse.futureshops.network.packets.C2SPlayerShopSavedConfigPacket;
import com.enviouse.futureshops.network.packets.C2SPlayerShopSettlementClaimPacket;
import com.enviouse.futureshops.network.packets.C2SPlayerShopUnlinkStoragePacket;
import com.enviouse.futureshops.network.packets.C2SPlayerShopPromoPacket;
import com.enviouse.futureshops.network.packets.C2SPlayerShopSellPacket;
import com.enviouse.futureshops.network.packets.C2SSellRequestPacket;
import com.enviouse.futureshops.network.packets.C2SSetDepartmentPacket;
import com.enviouse.futureshops.network.packets.C2SVerifyAdminCartPacket;
import com.enviouse.futureshops.network.packets.C2SAuctionBidPacket;
import com.enviouse.futureshops.network.packets.C2SAuctionBuyNowPacket;
import com.enviouse.futureshops.network.packets.C2SAuctionCancelPacket;
import com.enviouse.futureshops.network.packets.C2SAuctionCreatePacket;
import com.enviouse.futureshops.network.packets.C2SBazaarCancelPacket;
import com.enviouse.futureshops.network.packets.C2SBazaarOrderPacket;
import com.enviouse.futureshops.network.packets.C2SBazaarRegisterProductPacket;
import com.enviouse.futureshops.network.packets.C2SVerifyCartPacket;
import com.enviouse.futureshops.network.packets.S2CAdminEditAckPacket;
import com.enviouse.futureshops.network.packets.S2CMarketActionResponsePacket;
import com.enviouse.futureshops.network.packets.S2CAtmDataPacket;
import com.enviouse.futureshops.network.packets.S2CAtmResultPacket;
import com.enviouse.futureshops.network.packets.S2CAtmCollectCashResultPacket;
import com.enviouse.futureshops.network.packets.S2CAtmDepositResultPacket;
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
import com.enviouse.futureshops.network.packets.S2COpenMarketModulePacket;
import com.enviouse.futureshops.network.packets.S2CMarketPagePacket;
import com.enviouse.futureshops.network.packets.S2CMarketCapabilitiesPacket;
import com.enviouse.futureshops.network.packets.S2CMarketClaimCollectionPacket;
import com.enviouse.futureshops.network.packets.S2CMarketProfileMutationPacket;
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
    // Protocol 39 binds ATM deposits to currency catalog signatures.
    // Protocol 38 adds bounded ATM withdrawal retry timing.
    // Protocol 37 adds correlated ATM cash deposits.
    // Protocol 36 adds bounded ATM cash retry timing.
    // Protocol 35 adds correlated cart checkout responses.
    // Protocol 34 adds bounded correlated ATM cash claim collection.
    // Protocol 33 adds correlated ATM retries, availability, and claim results.
    // 32 adds explicit ATM server open intent.
    // 31 adds separate wallet and inventory cash selection to every purchase packet.
    // 30: added the server-authoritative ATM catalog, exact denomination withdrawal request,
    //     and result packets. v29 peers do not know these appended packet registrations.
    // 29: changed the protocol-28 barter batch from an off-hand-paid atomic add into safe empty
    //     targets followed by searchable ingredient editors. The wire shape is unchanged, but the
    //     message semantics are incompatible, so v28 peers must not silently connect.
    // 26: in-GUI admin shop editor — trailing canEdit on S2CShopDataPacket, trailing configuredStock
    //     on CatalogItem (the admin editor edits the configured max, not live remaining), plus three
    //     new packets registered at the end of the id space (C2SAdminShopEditPacket,
    //     C2SAdminShopAddItemsPacket, S2CAdminEditAckPacket).
    // 25: trailing nbtJson appended to barter ingredients, verify-cart lines (player AND admin),
    //     settlement/history rows, owned-shop summaries and bal-top popular item; barter recipes
    //     gained a trailing targetListingId (blank = unresolved / no NBT keeps legacy behaviour).
    // 24: per-listing id added to CatalogItem + buy/sell/admin-cart wire lines (multi-variant NBT
    //     admin listings). Old v23 clients are refused at handshake so they can't desync on the
    //     new leading listingId field.
    // Protocol 40 adds shared market module opening.
    // Protocol 41 adds durable sell and barter request identities.
    // Protocol 42 adds correlated paged market data.
    // Protocol 43 adds correlated player shop settlements and buybacks.
    // Protocol 44 adds market capability synchronization.
    // Protocol 47 adds Bazaar player product registration and market fee capabilities.
    // Protocol 49 adds market department counts.
    // Protocol 50 selects player Bazaar products by registry item id.
    public static final String PROTOCOL_VERSION = "50";

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

        CHANNEL.messageBuilder(C2SPlayerShopSellPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .decoder(C2SPlayerShopSellPacket::decode)
            .encoder(C2SPlayerShopSellPacket::encode)
            .consumerMainThread(C2SPlayerShopSellPacket::handle)
            .add();

        CHANNEL.messageBuilder(C2SPlayerShopBuybackConfigPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .decoder(C2SPlayerShopBuybackConfigPacket::decode)
            .encoder(C2SPlayerShopBuybackConfigPacket::encode)
            .consumerMainThread(C2SPlayerShopBuybackConfigPacket::handle)
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

        // In-GUI admin shop editor packets (protocol 26)
        CHANNEL.messageBuilder(C2SAdminShopEditPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .decoder(C2SAdminShopEditPacket::decode)
            .encoder(C2SAdminShopEditPacket::encode)
            .consumerMainThread(C2SAdminShopEditPacket::handle)
            .add();

        CHANNEL.messageBuilder(C2SAdminShopAddItemsPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .decoder(C2SAdminShopAddItemsPacket::decode)
            .encoder(C2SAdminShopAddItemsPacket::encode)
            .consumerMainThread(C2SAdminShopAddItemsPacket::handle)
            .add();

        CHANNEL.messageBuilder(S2CAdminEditAckPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .decoder(S2CAdminEditAckPacket::decode)
            .encoder(S2CAdminEditAckPacket::encode)
            .consumerMainThread(S2CAdminEditAckPacket::handle)
            .add();

        // Floating-icon mode config (protocol 27)
        CHANNEL.messageBuilder(C2SPlayerShopIconPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .decoder(C2SPlayerShopIconPacket::decode)
            .encoder(C2SPlayerShopIconPacket::encode)
            .consumerMainThread(C2SPlayerShopIconPacket::handle)
            .add();

        // Identity-based per-storage unlink for the owner Storage tab (protocol 27)
        CHANNEL.messageBuilder(C2SPlayerShopUnlinkStoragePacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .decoder(C2SPlayerShopUnlinkStoragePacket::decode)
            .encoder(C2SPlayerShopUnlinkStoragePacket::encode)
            .consumerMainThread(C2SPlayerShopUnlinkStoragePacket::handle)
            .add();

        // Named saved-config ops for the owner Payouts tab (protocol 27)
        CHANNEL.messageBuilder(C2SPlayerShopSavedConfigPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .decoder(C2SPlayerShopSavedConfigPacket::decode)
            .encoder(C2SPlayerShopSavedConfigPacket::encode)
            .consumerMainThread(C2SPlayerShopSavedConfigPacket::handle)
            .add();

        // Physical-currency ATM (protocol 30). Appended to preserve every prior id.
        CHANNEL.messageBuilder(C2SOpenAtmPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .decoder(C2SOpenAtmPacket::decode)
            .encoder(C2SOpenAtmPacket::encode)
            .consumerMainThread(C2SOpenAtmPacket::handle)
            .add();

        CHANNEL.messageBuilder(S2CAtmDataPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .decoder(S2CAtmDataPacket::decode)
            .encoder(S2CAtmDataPacket::encode)
            .consumerMainThread(S2CAtmDataPacket::handle)
            .add();

        CHANNEL.messageBuilder(C2SAtmWithdrawPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .decoder(C2SAtmWithdrawPacket::decode)
            .encoder(C2SAtmWithdrawPacket::encode)
            .consumerMainThread(C2SAtmWithdrawPacket::handle)
            .add();

        CHANNEL.messageBuilder(S2CAtmResultPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .decoder(S2CAtmResultPacket::decode)
            .encoder(S2CAtmResultPacket::encode)
            .consumerMainThread(S2CAtmResultPacket::handle)
            .add();

        CHANNEL.messageBuilder(C2SAtmCollectCashPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .decoder(C2SAtmCollectCashPacket::decode)
            .encoder(C2SAtmCollectCashPacket::encode)
            .consumerMainThread(C2SAtmCollectCashPacket::handle)
            .add();

        CHANNEL.messageBuilder(S2CAtmCollectCashResultPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .decoder(S2CAtmCollectCashResultPacket::decode)
            .encoder(S2CAtmCollectCashResultPacket::encode)
            .consumerMainThread(S2CAtmCollectCashResultPacket::handle)
            .add();

        CHANNEL.messageBuilder(C2SAtmDepositPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .decoder(C2SAtmDepositPacket::decode)
            .encoder(C2SAtmDepositPacket::encode)
            .consumerMainThread(C2SAtmDepositPacket::handle)
            .add();

        CHANNEL.messageBuilder(S2CAtmDepositResultPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .decoder(S2CAtmDepositResultPacket::decode)
            .encoder(S2CAtmDepositResultPacket::encode)
            .consumerMainThread(S2CAtmDepositResultPacket::handle)
            .add();

        CHANNEL.messageBuilder(C2SOpenMarketModulePacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .decoder(C2SOpenMarketModulePacket::decode)
            .encoder(C2SOpenMarketModulePacket::encode)
            .consumerMainThread(C2SOpenMarketModulePacket::handle)
            .add();

        CHANNEL.messageBuilder(S2COpenMarketModulePacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .decoder(S2COpenMarketModulePacket::decode)
            .encoder(S2COpenMarketModulePacket::encode)
            .consumerMainThread(S2COpenMarketModulePacket::handle)
            .add();

        CHANNEL.messageBuilder(C2SMarketPageQueryPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .decoder(C2SMarketPageQueryPacket::decode)
            .encoder(C2SMarketPageQueryPacket::encode)
            .consumerMainThread(C2SMarketPageQueryPacket::handle)
            .add();

        CHANNEL.messageBuilder(S2CMarketPagePacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .decoder(S2CMarketPagePacket::decode)
            .encoder(S2CMarketPagePacket::encode)
            .consumerMainThread(S2CMarketPagePacket::handle)
            .add();

        CHANNEL.messageBuilder(C2SCloseMarketSessionPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .decoder(C2SCloseMarketSessionPacket::decode)
            .encoder(C2SCloseMarketSessionPacket::encode)
            .consumerMainThread(C2SCloseMarketSessionPacket::handle)
            .add();

        CHANNEL.messageBuilder(C2SPlayerShopSettlementClaimPacket.class,
                        nextId(), NetworkDirection.PLAY_TO_SERVER)
            .decoder(C2SPlayerShopSettlementClaimPacket::decode)
            .encoder(C2SPlayerShopSettlementClaimPacket::encode)
            .consumerMainThread(C2SPlayerShopSettlementClaimPacket::handle)
            .add();

        CHANNEL.messageBuilder(C2SMarketCapabilitiesPacket.class,
                        nextId(), NetworkDirection.PLAY_TO_SERVER)
            .decoder(C2SMarketCapabilitiesPacket::decode)
            .encoder(C2SMarketCapabilitiesPacket::encode)
            .consumerMainThread(C2SMarketCapabilitiesPacket::handle)
            .add();

        CHANNEL.messageBuilder(S2CMarketCapabilitiesPacket.class,
                        nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .decoder(S2CMarketCapabilitiesPacket::decode)
            .encoder(S2CMarketCapabilitiesPacket::encode)
            .consumerMainThread(S2CMarketCapabilitiesPacket::handle)
            .add();

        CHANNEL.messageBuilder(C2SMarketProfileMutationPacket.class,
                        nextId(), NetworkDirection.PLAY_TO_SERVER)
            .decoder(C2SMarketProfileMutationPacket::decode)
            .encoder(C2SMarketProfileMutationPacket::encode)
            .consumerMainThread(C2SMarketProfileMutationPacket::handle)
            .add();

        CHANNEL.messageBuilder(S2CMarketProfileMutationPacket.class,
                        nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .decoder(S2CMarketProfileMutationPacket::decode)
            .encoder(S2CMarketProfileMutationPacket::encode)
            .consumerMainThread(S2CMarketProfileMutationPacket::handle)
            .add();

        CHANNEL.messageBuilder(C2SMarketClaimCollectionPacket.class,
                        nextId(), NetworkDirection.PLAY_TO_SERVER)
            .decoder(C2SMarketClaimCollectionPacket::decode)
            .encoder(C2SMarketClaimCollectionPacket::encode)
            .consumerMainThread(C2SMarketClaimCollectionPacket::handle)
            .add();

        CHANNEL.messageBuilder(S2CMarketClaimCollectionPacket.class,
                        nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .decoder(S2CMarketClaimCollectionPacket::decode)
            .encoder(S2CMarketClaimCollectionPacket::encode)
            .consumerMainThread(S2CMarketClaimCollectionPacket::handle)
            .add();

        // Protocol 46 market mutations.
        // Appended in stable order; registered regardless of module state so disabled
        // modules can still be configured/inspected before enabling.
        CHANNEL.messageBuilder(C2SAuctionCreatePacket.class,
                        nextId(), NetworkDirection.PLAY_TO_SERVER)
            .decoder(C2SAuctionCreatePacket::decode)
            .encoder(C2SAuctionCreatePacket::encode)
            .consumerMainThread(C2SAuctionCreatePacket::handle)
            .add();

        CHANNEL.messageBuilder(C2SAuctionBidPacket.class,
                        nextId(), NetworkDirection.PLAY_TO_SERVER)
            .decoder(C2SAuctionBidPacket::decode)
            .encoder(C2SAuctionBidPacket::encode)
            .consumerMainThread(C2SAuctionBidPacket::handle)
            .add();

        CHANNEL.messageBuilder(C2SAuctionBuyNowPacket.class,
                        nextId(), NetworkDirection.PLAY_TO_SERVER)
            .decoder(C2SAuctionBuyNowPacket::decode)
            .encoder(C2SAuctionBuyNowPacket::encode)
            .consumerMainThread(C2SAuctionBuyNowPacket::handle)
            .add();

        CHANNEL.messageBuilder(C2SAuctionCancelPacket.class,
                        nextId(), NetworkDirection.PLAY_TO_SERVER)
            .decoder(C2SAuctionCancelPacket::decode)
            .encoder(C2SAuctionCancelPacket::encode)
            .consumerMainThread(C2SAuctionCancelPacket::handle)
            .add();

        CHANNEL.messageBuilder(C2SBazaarOrderPacket.class,
                        nextId(), NetworkDirection.PLAY_TO_SERVER)
            .decoder(C2SBazaarOrderPacket::decode)
            .encoder(C2SBazaarOrderPacket::encode)
            .consumerMainThread(C2SBazaarOrderPacket::handle)
            .add();

        CHANNEL.messageBuilder(C2SBazaarCancelPacket.class,
                        nextId(), NetworkDirection.PLAY_TO_SERVER)
            .decoder(C2SBazaarCancelPacket::decode)
            .encoder(C2SBazaarCancelPacket::encode)
            .consumerMainThread(C2SBazaarCancelPacket::handle)
            .add();

        CHANNEL.messageBuilder(S2CMarketActionResponsePacket.class,
                        nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .decoder(S2CMarketActionResponsePacket::decode)
            .encoder(S2CMarketActionResponsePacket::encode)
            .consumerMainThread(S2CMarketActionResponsePacket::handle)
            .add();

        CHANNEL.messageBuilder(C2SBazaarRegisterProductPacket.class,
                        nextId(), NetworkDirection.PLAY_TO_SERVER)
            .decoder(C2SBazaarRegisterProductPacket::decode)
            .encoder(C2SBazaarRegisterProductPacket::encode)
            .consumerMainThread(C2SBazaarRegisterProductPacket::handle)
            .add();
    }

    public static int nextId() {
        return packetId++;
    }

    public static void sendToPlayer(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}
