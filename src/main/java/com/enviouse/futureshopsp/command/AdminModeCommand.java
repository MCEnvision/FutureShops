package com.enviouse.futureshopsp.command;

import com.enviouse.futureshopsp.block.ShopBlockEntity;
import com.enviouse.futureshopsp.server.shop.PlayerShopBlockService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Registers {@code /shopadmin on} and {@code /shopadmin off} for toggling
 * Admin Shop mode on the player shop block the executing player is currently
 * looking at. Eligibility (creative-mode placement) is enforced by the block
 * entity setter.
 */
public final class AdminModeCommand {
    private static final double LOOK_RANGE = 8.0D;

    private AdminModeCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // Brigadier merges children when the same literal is registered again,
        // so this adds on/off to the existing /shopadmin tree without
        // disturbing ShopAdminCommand.
        dispatcher.register(Commands.literal("shopadmin")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("on").executes(ctx -> toggle(ctx, true)))
                .then(Commands.literal("off").executes(ctx -> toggle(ctx, false))));
    }

    private static int toggle(CommandContext<CommandSourceStack> ctx, boolean enable) {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.translatable("command.futureshops.admin.adminshop.player_only")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        BlockHitResult hit = pickShopBlock(player);
        if (hit == null || hit.getType() != HitResult.Type.BLOCK
                || !(player.level().getBlockEntity(hit.getBlockPos()) instanceof ShopBlockEntity shop)) {
            source.sendFailure(Component.translatable("command.futureshops.shopadmin.no_shop")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        if (!shop.isPlacedByCreative()) {
            source.sendFailure(Component.translatable("command.futureshops.shopadmin.not_eligible")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        boolean ok = shop.setAdminShopMode(enable);
        if (!ok) {
            source.sendFailure(Component.translatable("command.futureshops.shopadmin.not_eligible")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        shop.setChanged();

        // Resync any open shop UIs at this block by re-running openFor for
        // nearby online players. Cheapest: re-open for the toggling player;
        // other viewers will refresh on their next interaction. (Per-block
        // session resync isn't exposed for player shops.)
        PlayerShopBlockService.openFor(player, hit.getBlockPos(), false);

        source.sendSuccess(() -> Component.translatable(enable
                        ? "command.futureshops.shopadmin.enabled"
                        : "command.futureshops.shopadmin.disabled")
                .withStyle(enable ? ChatFormatting.GREEN : ChatFormatting.YELLOW), true);
        return 1;
    }

    private static BlockHitResult pickShopBlock(ServerPlayer player) {
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getLookAngle().scale(LOOK_RANGE));
        return player.level().clip(new ClipContext(start, end,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
    }
}
