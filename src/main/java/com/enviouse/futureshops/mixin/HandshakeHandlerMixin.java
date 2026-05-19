package com.enviouse.futureshops.mixin;

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

/**
 * Replaces Forge's default channel-mismatch disconnect text with a friendlier
 * message when the FutureShops channel is what failed validation.
 *
 * <p>Both injection sites short-circuit Forge's normal flow only when the
 * mismatch involves our channel ({@code futureshops:main}). When other mods
 * mismatch, Forge's standard {@code ModMismatchDisconnectedScreen} (with its
 * channel/version table) is used unchanged.
 *
 * <p>Important caveat: this only helps clients that already ship this mixin.
 * Players running an older jar that doesn't include this code will continue to
 * see Forge's default screen on the v21 → v22 upgrade. From v22 onward any
 * future protocol bump is presented with the friendly message.
 */
@Mixin(value = HandshakeHandler.class, remap = false)
public abstract class HandshakeHandlerMixin {

    private static final Logger FUTURESHOPS$LOGGER = LogUtils.getLogger();
    private static final ResourceLocation FUTURESHOPS$CHANNEL = new ResourceLocation("futureshops", "main");

    /**
     * Returns true when the channel map (sent by the peer) carries our channel with a version
     * different from the local jar's {@link ShopPackets#PROTOCOL_VERSION}, or omits it entirely.
     * Mirrors the strict-equality predicate registered on {@code ShopPackets.CHANNEL}.
     */
    private static boolean futureshops$ourChannelMismatched(Map<ResourceLocation, String> peerChannels) {
        String peerVersion = peerChannels.get(FUTURESHOPS$CHANNEL);
        return peerVersion == null || !ShopPackets.PROTOCOL_VERSION.equals(peerVersion);
    }

    private static Component futureshops$buildMessage() {
        return Component.literal(
                "There's nothing wrong with your game — you just need to update " +
                "the FutureShops mod to the latest version to join.\n\n" +
                "It's simple to do, and if you need help, ask in Discord."
        );
    }

    /**
     * Client-side path. The client validates the server's channel list when it arrives;
     * if our channel mismatched, skip Forge's hardcoded "Connection closed - mismatched mod
     * channel list" + the FML_MOD_MISMATCH_DATA attribute, and just close the connection
     * with our message. With FML_MOD_MISMATCH_DATA unset, the regular DisconnectedScreen
     * displays our Component cleanly instead of the channel/version table screen.
     */
    @Inject(method = "handleServerModListOnClient",
            at = @At("HEAD"),
            cancellable = true)
    private void futureshops$replaceClientDisconnect(
            HandshakeMessages.S2CModList serverModList,
            Supplier<NetworkEvent.Context> c,
            CallbackInfo ci) {
        if (!futureshops$ourChannelMismatched(serverModList.getChannels())) {
            return;
        }

        FUTURESHOPS$LOGGER.info("[FutureShops] Channel version mismatch detected on client — showing friendly disconnect message.");
        c.get().setPacketHandled(true);
        c.get().getNetworkManager().disconnect(futureshops$buildMessage());
        ci.cancel();
    }

    /**
     * Server-side path. Defensive coverage for the inverse case (newer client, older server).
     * Disconnects via {@link ServerLoginPacketListenerImpl#disconnect(Component)} so the older
     * client receives a {@code ClientboundLoginDisconnectPacket} carrying our message and
     * displays it on its standard disconnect screen.
     */
    @Inject(method = "handleClientModListOnServer",
            at = @At("HEAD"),
            cancellable = true)
    private void futureshops$replaceServerDisconnect(
            HandshakeMessages.C2SModListReply clientModList,
            Supplier<NetworkEvent.Context> c,
            CallbackInfo ci) {
        if (!futureshops$ourChannelMismatched(clientModList.getChannels())) {
            return;
        }

        FUTURESHOPS$LOGGER.info("[FutureShops] Rejecting client with stale FutureShops channel version — sending friendly disconnect message.");
        c.get().setPacketHandled(true);
        Connection connection = c.get().getNetworkManager();
        PacketListener listener = connection.getPacketListener();
        Component message = futureshops$buildMessage();
        if (listener instanceof ServerLoginPacketListenerImpl loginListener) {
            loginListener.disconnect(message);
        } else {
            connection.disconnect(message);
        }
        ci.cancel();
    }
}
