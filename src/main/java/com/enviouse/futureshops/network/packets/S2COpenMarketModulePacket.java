package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.client.ShopClientPacketHandler;
import com.enviouse.futureshops.client.market.MarketModule;
import com.enviouse.futureshops.client.market.MarketThemeColors;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public record S2COpenMarketModulePacket(
        UUID requestId,
        UUID routeNonce,
        String moduleId,
        String view,
        String displayName,
        String accentColor,
        boolean enabled,
        boolean escrowReady,
        boolean showNavigation,
        boolean bazaarEnabled,
        boolean auctionHouseEnabled
) {
    private static final UUID ZERO = new UUID(0L, 0L);

    public S2COpenMarketModulePacket {
        requestId = Objects.requireNonNull(requestId, "requestId");
        routeNonce = Objects.requireNonNull(routeNonce, "routeNonce");
        moduleId = requireText(moduleId, 32);
        view = requireText(view, 32);
        displayName = requireText(displayName, 64);
        accentColor = requireText(accentColor, 16);
        MarketModule module = MarketModule.fromId(moduleId);
        if (requestId.equals(ZERO) || routeNonce.equals(ZERO)
                || module == MarketModule.SHOP) {
            throw new IllegalArgumentException("Market open response identity is invalid");
        }
        MarketThemeColors.parseHex(accentColor);
    }

    public static void encode(S2COpenMarketModulePacket packet,
                              FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.requestId());
        buffer.writeUUID(packet.routeNonce());
        buffer.writeUtf(packet.moduleId(), 32);
        buffer.writeUtf(packet.view(), 32);
        buffer.writeUtf(packet.displayName(), 64);
        buffer.writeUtf(packet.accentColor(), 16);
        buffer.writeBoolean(packet.enabled());
        buffer.writeBoolean(packet.escrowReady());
        buffer.writeBoolean(packet.showNavigation());
        buffer.writeBoolean(packet.bazaarEnabled());
        buffer.writeBoolean(packet.auctionHouseEnabled());
    }

    public static S2COpenMarketModulePacket decode(FriendlyByteBuf buffer) {
        try {
            return new S2COpenMarketModulePacket(buffer.readUUID(),
                    buffer.readUUID(),
                    buffer.readUtf(32), buffer.readUtf(32),
                    buffer.readUtf(64), buffer.readUtf(16),
                    buffer.readBoolean(), buffer.readBoolean(),
                    buffer.readBoolean(), buffer.readBoolean(),
                    buffer.readBoolean());
        } catch (RuntimeException exception) {
            throw new DecoderException("Market open response is invalid", exception);
        }
    }

    public static void handle(S2COpenMarketModulePacket packet,
                              Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ShopClientPacketHandler.handleOpenMarket(packet)));
        context.setPacketHandled(true);
    }

    private static String requireText(String value, int maximum) {
        String normalized = Objects.requireNonNull(value, "value");
        if (normalized.isEmpty() || normalized.length() > maximum
                || !normalized.equals(normalized.strip())) {
            throw new IllegalArgumentException("Market open response text is invalid");
        }
        return normalized;
    }
}
