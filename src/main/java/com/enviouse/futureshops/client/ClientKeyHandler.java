package com.enviouse.futureshops.client;

import com.enviouse.futureshops.Futureshops;
import com.enviouse.futureshops.block.ShopBlock;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.C2SPlayerShopActionPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Futureshops.MODID, value = Dist.CLIENT)
public final class ClientKeyHandler {
    private ClientKeyHandler() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        while (ModKeyMappings.OPEN_SHOP.consumeClick()) {
            if (mc.player == null || mc.level == null || mc.screen != null) {
                continue;
            }
            if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
                continue;
            }
            BlockPos pos = hit.getBlockPos();
            if (!(mc.level.getBlockState(pos).getBlock() instanceof ShopBlock)) {
                continue;
            }
            // Sneak mirrors right-click semantics: owners can preview their own
            // shop as a visitor. The server re-validates distance and view.
            int sneakFlag = mc.player.isShiftKeyDown() ? 1 : 0;
            ShopPackets.CHANNEL.sendToServer(new C2SPlayerShopActionPacket(pos, "OPEN_LOOKING", 0, sneakFlag));
        }
    }
}
