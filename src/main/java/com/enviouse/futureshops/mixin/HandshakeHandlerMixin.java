package com.enviouse.futureshops.mixin;

import com.enviouse.futureshops.network.HandshakeCompatibilityPolicy;
import com.enviouse.futureshops.network.HandshakeCompatibilityPolicy.PeerSide;
import com.enviouse.futureshops.network.HandshakeCompatibilityPolicy.Result;
import com.enviouse.futureshops.network.ShopPackets;
import com.mojang.logging.LogUtils;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import net.minecraftforge.network.HandshakeHandler;
import net.minecraftforge.network.HandshakeMessages;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.function.Supplier;

@Mixin(value = HandshakeHandler.class, remap = false)
public abstract class HandshakeHandlerMixin {
    private static final Logger FUTURESHOPS$LOGGER = LogUtils.getLogger();
    private static final ResourceLocation FUTURESHOPS$CHANNEL =
            ResourceLocation.fromNamespaceAndPath("futureshops", "main");

    private static Result futureshops$evaluate(
            Map<ResourceLocation, String> peerChannels, PeerSide peerSide) {
        return HandshakeCompatibilityPolicy.evaluate(
                ShopPackets.PROTOCOL_VERSION,
                peerChannels.get(FUTURESHOPS$CHANNEL), peerSide);
    }

    private static Component futureshops$message(Result result) {
        return Component.literal(result.message());
    }

    @Inject(method = "handleServerModListOnClient",
            at = @At("HEAD"), cancellable = true)
    private void futureshops$replaceClientDisconnect(
            HandshakeMessages.S2CModList serverModList,
            Supplier<NetworkEvent.Context> context,
            CallbackInfo callback) {
        Result result = futureshops$evaluate(
                serverModList.getChannels(), PeerSide.SERVER);
        if (result.compatible()) {
            return;
        }

        FUTURESHOPS$LOGGER.info(
                "futureshops handshake rejected on the client, {}.", result);
        context.get().setPacketHandled(true);
        context.get().getNetworkManager().disconnect(
                futureshops$message(result));
        callback.cancel();
    }

    @Inject(method = "handleClientModListOnServer",
            at = @At("HEAD"), cancellable = true)
    private void futureshops$replaceServerDisconnect(
            HandshakeMessages.C2SModListReply clientModList,
            Supplier<NetworkEvent.Context> context,
            CallbackInfo callback) {
        Result result = futureshops$evaluate(
                clientModList.getChannels(), PeerSide.CLIENT);
        if (result.compatible()) {
            return;
        }

        FUTURESHOPS$LOGGER.info(
                "futureshops handshake rejected on the server, {}.", result);
        context.get().setPacketHandled(true);
        Connection connection = context.get().getNetworkManager();
        PacketListener listener = connection.getPacketListener();
        Component message = futureshops$message(result);
        if (listener instanceof ServerLoginPacketListenerImpl loginListener) {
            loginListener.disconnect(message);
        } else {
            connection.disconnect(message);
        }
        callback.cancel();
    }
}
