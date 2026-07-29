package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.catalog.AdminShopOfferConfigWriter;
import com.enviouse.futureshops.catalog.offer.OfferValidationIssue;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferListing;
import com.enviouse.futureshops.client.ShopClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public record S2CPlayerShopOfferSaveResultPacket(
        UUID requestId,
        AdminShopOfferConfigWriter.Status status,
        boolean success,
        long revision,
        Optional<ServerShopOfferListing> snapshot,
        List<OfferValidationIssue> issues
) {
    public S2CPlayerShopOfferSaveResultPacket {
        S2CAdminOfferSaveResultPacket validation =
                asAdminResult(requestId, status, success, revision,
                        snapshot, issues);
        requestId = validation.requestId();
        status = validation.status();
        snapshot = validation.snapshot();
        issues = validation.issues();
    }

    public S2CAdminOfferSaveResultPacket asAdminResult() {
        return asAdminResult(requestId, status, success,
                revision, snapshot, issues);
    }

    public static void encode(
            S2CPlayerShopOfferSaveResultPacket packet,
            FriendlyByteBuf buffer
    ) {
        S2CAdminOfferSaveResultPacket.encode(
                packet.asAdminResult(), buffer);
    }

    public static S2CPlayerShopOfferSaveResultPacket decode(
            FriendlyByteBuf buffer
    ) {
        S2CAdminOfferSaveResultPacket result =
                S2CAdminOfferSaveResultPacket.decode(buffer);
        return new S2CPlayerShopOfferSaveResultPacket(
                result.requestId(), result.status(),
                result.success(), result.revision(),
                result.snapshot(), result.issues());
    }

    public static void handle(
            S2CPlayerShopOfferSaveResultPacket packet,
            Supplier<NetworkEvent.Context> contextSupplier
    ) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> ShopClientPacketHandler
                                .handlePlayerShopOfferSaveResult(packet)));
        context.setPacketHandled(true);
    }

    private static S2CAdminOfferSaveResultPacket asAdminResult(
            UUID requestId,
            AdminShopOfferConfigWriter.Status status,
            boolean success,
            long revision,
            Optional<ServerShopOfferListing> snapshot,
            List<OfferValidationIssue> issues
    ) {
        return new S2CAdminOfferSaveResultPacket(
                requestId, status, success, revision,
                snapshot, issues);
    }
}
