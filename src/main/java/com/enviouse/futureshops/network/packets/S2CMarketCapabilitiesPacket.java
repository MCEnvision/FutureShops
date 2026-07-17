package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.client.market.MarketCapabilitiesSnapshot;
import com.enviouse.futureshops.client.ShopClientPacketHandler;
import com.enviouse.futureshops.client.market.MarketModule;
import com.enviouse.futureshops.client.market.MarketModuleAvailability;
import com.enviouse.futureshops.client.market.MarketModuleCapability;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public record S2CMarketCapabilitiesPacket(
        MarketCapabilitiesSnapshot snapshot
) {
    public static final int MODULE_COUNT = MarketModule.values().length;
    public static final int MAXIMUM_DISPLAY_NAME_LENGTH = 64;
    public static final int MAXIMUM_ACCENT_LENGTH = 9;
    public static final int MAXIMUM_CURRENCY_NAME_LENGTH =
            MarketCapabilitiesSnapshot.MAXIMUM_CURRENCY_NAME_LENGTH;

    public S2CMarketCapabilitiesPacket {
        snapshot = Objects.requireNonNull(snapshot, "snapshot");
        validateCompleteModuleSet(snapshot.modules());
    }

    public static void encode(
            S2CMarketCapabilitiesPacket packet,
            FriendlyByteBuf buffer
    ) {
        Objects.requireNonNull(packet, "packet");
        Objects.requireNonNull(buffer, "buffer");
        MarketCapabilitiesSnapshot snapshot = packet.snapshot();
        buffer.writeUUID(snapshot.requestId());
        buffer.writeLong(snapshot.revision());
        buffer.writeBoolean(snapshot.showNavigation());
        buffer.writeEnum(snapshot.defaultModule());
        buffer.writeVarInt(snapshot.modules().size());
        for (MarketModuleCapability capability : snapshot.modules()) {
            buffer.writeEnum(capability.module());
            buffer.writeEnum(capability.availability());
            buffer.writeUtf(capability.displayName(),
                    MAXIMUM_DISPLAY_NAME_LENGTH);
            buffer.writeUtf(capability.accentHex(),
                    MAXIMUM_ACCENT_LENGTH);
            buffer.writeVarLong(capability.openClaims());
            buffer.writeLong(capability.revision());
        }
        buffer.writeLong(snapshot.walletBalanceMinorUnits());
        buffer.writeBoolean(snapshot.walletBalanceKnown());
        buffer.writeUtf(snapshot.currencyName(),
                MAXIMUM_CURRENCY_NAME_LENGTH);
        buffer.writeVarInt(snapshot.currencyDecimals());
    }

    public static S2CMarketCapabilitiesPacket decode(
            FriendlyByteBuf buffer
    ) {
        try {
            UUID requestId = buffer.readUUID();
            long revision = buffer.readLong();
            boolean showNavigation = buffer.readBoolean();
            MarketModule defaultModule = buffer.readEnum(
                    MarketModule.class);
            int moduleCount = requireModuleCount(buffer.readVarInt());
            List<MarketModuleCapability> modules =
                    new ArrayList<>(moduleCount);
            for (int index = 0; index < moduleCount; index++) {
                modules.add(new MarketModuleCapability(
                        buffer.readEnum(MarketModule.class),
                        buffer.readEnum(MarketModuleAvailability.class),
                        buffer.readUtf(MAXIMUM_DISPLAY_NAME_LENGTH),
                        requireAccent(buffer.readUtf(
                                MAXIMUM_ACCENT_LENGTH)),
                        buffer.readVarLong(), buffer.readLong()));
            }
            long walletBalance = buffer.readLong();
            boolean walletBalanceKnown = buffer.readBoolean();
            String currencyName = buffer.readUtf(
                    MAXIMUM_CURRENCY_NAME_LENGTH);
            int currencyDecimals = buffer.readVarInt();
            S2CMarketCapabilitiesPacket result =
                    new S2CMarketCapabilitiesPacket(
                            new MarketCapabilitiesSnapshot(requestId,
                                    revision, showNavigation,
                                    defaultModule, walletBalance,
                                    walletBalanceKnown, currencyName,
                                    currencyDecimals, modules));
            requireFullyRead(buffer);
            return result;
        } catch (RuntimeException exception) {
            throw new DecoderException(
                    "Market capability response is invalid", exception);
        }
    }

    public static void handle(
            S2CMarketCapabilitiesPacket packet,
            Supplier<NetworkEvent.Context> supplier
    ) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT, () -> () ->
                        ShopClientPacketHandler
                                .handleMarketCapabilities(packet)));
        context.setPacketHandled(true);
    }

    private static int requireModuleCount(int count) {
        if (count != MODULE_COUNT) {
            throw new IllegalArgumentException(
                    "Market capability module count is invalid");
        }
        return count;
    }

    private static String requireAccent(String accent) {
        String value = Objects.requireNonNull(accent, "accent");
        if (!value.equals(value.strip())
                || value.length() > MAXIMUM_ACCENT_LENGTH) {
            throw new IllegalArgumentException(
                    "Market capability accent is invalid");
        }
        return value;
    }

    private static void validateCompleteModuleSet(
            List<MarketModuleCapability> capabilities
    ) {
        if (capabilities.size() != MODULE_COUNT) {
            throw new IllegalArgumentException(
                    "Market capability module set is incomplete");
        }
        EnumSet<MarketModule> modules = EnumSet.noneOf(
                MarketModule.class);
        for (MarketModuleCapability capability : capabilities) {
            Objects.requireNonNull(capability, "capability");
            if (!modules.add(capability.module())) {
                throw new IllegalArgumentException(
                        "Market capability module set is duplicated");
            }
            requireAccent(capability.accentHex());
        }
        if (modules.size() != MODULE_COUNT) {
            throw new IllegalArgumentException(
                    "Market capability module set is incomplete");
        }
    }

    private static void requireFullyRead(FriendlyByteBuf buffer) {
        if (buffer.isReadable()) {
            throw new IllegalArgumentException(
                    "Market capability response has trailing data");
        }
    }
}
