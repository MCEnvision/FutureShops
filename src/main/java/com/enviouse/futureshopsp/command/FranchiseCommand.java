package com.enviouse.futureshopsp.command;

import com.enviouse.futureshopsp.network.packets.C2SFranchiseActionPacket;
import com.enviouse.futureshopsp.server.shop.FranchiseSavedData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Registers the {@code /franchise} command tree.
 * Subcommands: create, invite, accept, decline, kick, promote, manage, leave, disband.
 *
 * <p>All player-visible messages flow through {@code command.futureshops.franchise.*}
 * lang keys — see {@code en_us.json}. Dynamic arguments (player names, franchise names)
 * are passed as {@link Component#translatable(String, Object...)} args.
 */
public final class FranchiseCommand {
    private FranchiseCommand() {
    }

    /** Centralised "player only" error — reused by every subcommand. */
    private static Component playerOnly() {
        return Component.translatable("command.futureshops.player_only");
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("franchise")
                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> create(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("invite")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(ctx -> invite(ctx.getSource(), StringArgumentType.getString(ctx, "player")))))
                .then(Commands.literal("accept")
                        .executes(ctx -> accept(ctx.getSource())))
                .then(Commands.literal("decline")
                        .executes(ctx -> decline(ctx.getSource())))
                .then(Commands.literal("kick")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(ctx -> kick(ctx.getSource(), StringArgumentType.getString(ctx, "player")))))
                .then(Commands.literal("promote")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(ctx -> promote(ctx.getSource(), StringArgumentType.getString(ctx, "player")))))
                .then(Commands.literal("manage")
                        .executes(ctx -> manage(ctx.getSource())))
                .then(Commands.literal("leave")
                        .executes(ctx -> leave(ctx.getSource())))
                .then(Commands.literal("disband")
                        .executes(ctx -> disband(ctx.getSource()))));
    }

    private static int create(CommandSourceStack source, String name) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(EconomyCommandUtil.error(playerOnly()));
            return 0;
        }
        if (name.isBlank() || name.length() > 32) {
            source.sendFailure(EconomyCommandUtil.error(
                    Component.translatable("command.futureshops.franchise.create.invalid_name")));
            return 0;
        }
        FranchiseSavedData data = FranchiseSavedData.get(player.getServer());
        FranchiseSavedData.CreateResult result = data.createFranchise(player.getUUID(), name);
        if (!result.success()) {
            String key = "ALREADY_IN_FRANCHISE".equals(result.code())
                    ? "command.futureshops.franchise.create.already_in"
                    : "command.futureshops.franchise.create.failed";
            source.sendFailure(EconomyCommandUtil.error(Component.translatable(key)));
            return 0;
        }
        source.sendSuccess(() -> EconomyCommandUtil.success(
                Component.translatable("command.futureshops.franchise.create.success", name)), false);
        return 1;
    }

    private static int invite(CommandSourceStack source, String targetName) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(EconomyCommandUtil.error(playerOnly()));
            return 0;
        }
        MinecraftServer server = player.getServer();
        ServerPlayer target = server.getPlayerList().getPlayerByName(targetName);
        if (target == null) {
            source.sendFailure(EconomyCommandUtil.error(
                    Component.translatable("command.futureshops.franchise.invite.offline", targetName)));
            return 0;
        }
        FranchiseSavedData data = FranchiseSavedData.get(server);
        if (!data.invite(player.getUUID(), target.getUUID())) {
            source.sendFailure(EconomyCommandUtil.error(
                    Component.translatable("command.futureshops.franchise.invite.failed")));
            return 0;
        }
        FranchiseSavedData.Franchise franchise = data.getFranchise(player.getUUID());
        String franchiseName = franchise != null ? franchise.name : "";

        source.sendSuccess(() -> EconomyCommandUtil.success(
                Component.translatable("command.futureshops.franchise.invite.sent", targetName)), false);

        // Invitee-side: a decorated multi-part banner. We keep the individual lines as
        // separate lang keys so translators can reword the prompt without touching code,
        // and apply the ChatFormatting styles server-side for consistent visuals.
        target.sendSystemMessage(Component.literal("")
                .append(Component.translatable("command.futureshops.franchise.invite.banner.header")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(Component.literal("\n"))
                .append(Component.translatable("command.futureshops.franchise.invite.banner.line1",
                                player.getGameProfile().getName(), franchiseName)
                        .withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("\n"))
                .append(Component.translatable("command.futureshops.franchise.invite.banner.line2")
                        .withStyle(ChatFormatting.GRAY)));
        return 1;
    }

    private static int accept(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(EconomyCommandUtil.error(playerOnly()));
            return 0;
        }
        FranchiseSavedData data = FranchiseSavedData.get(player.getServer());
        if (!data.acceptInvite(player.getUUID())) {
            source.sendFailure(EconomyCommandUtil.error(
                    Component.translatable("command.futureshops.franchise.invite.none_pending")));
            return 0;
        }
        FranchiseSavedData.Franchise franchise = data.getFranchise(player.getUUID());
        String name = franchise != null ? franchise.name : "";
        source.sendSuccess(() -> EconomyCommandUtil.success(
                Component.translatable("command.futureshops.franchise.accept.success", name)), false);
        return 1;
    }

    private static int decline(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(EconomyCommandUtil.error(playerOnly()));
            return 0;
        }
        FranchiseSavedData data = FranchiseSavedData.get(player.getServer());
        if (!data.declineInvite(player.getUUID())) {
            source.sendFailure(EconomyCommandUtil.error(
                    Component.translatable("command.futureshops.franchise.invite.none_pending")));
            return 0;
        }
        source.sendSuccess(() -> EconomyCommandUtil.success(
                Component.translatable("command.futureshops.franchise.decline.success")), false);
        return 1;
    }

    private static int kick(CommandSourceStack source, String targetName) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(EconomyCommandUtil.error(playerOnly()));
            return 0;
        }
        MinecraftServer server = player.getServer();
        UUID targetUuid = resolveUuid(server, targetName);
        if (targetUuid == null) {
            source.sendFailure(EconomyCommandUtil.error(
                    Component.translatable("command.futureshops.franchise.unknown_player", targetName)));
            return 0;
        }
        FranchiseSavedData data = FranchiseSavedData.get(server);
        if (!data.kick(player.getUUID(), targetUuid)) {
            source.sendFailure(EconomyCommandUtil.error(
                    Component.translatable("command.futureshops.franchise.kick.failed")));
            return 0;
        }
        source.sendSuccess(() -> EconomyCommandUtil.success(
                Component.translatable("command.futureshops.franchise.kick.success", targetName)), false);
        ServerPlayer target = server.getPlayerList().getPlayer(targetUuid);
        if (target != null) {
            target.sendSystemMessage(EconomyCommandUtil.error(
                    Component.translatable("command.futureshops.franchise.kick.notify",
                            player.getGameProfile().getName())));
        }
        return 1;
    }

    private static int promote(CommandSourceStack source, String targetName) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(EconomyCommandUtil.error(playerOnly()));
            return 0;
        }
        MinecraftServer server = player.getServer();
        UUID targetUuid = resolveUuid(server, targetName);
        if (targetUuid == null) {
            source.sendFailure(EconomyCommandUtil.error(
                    Component.translatable("command.futureshops.franchise.unknown_player", targetName)));
            return 0;
        }
        FranchiseSavedData data = FranchiseSavedData.get(server);
        if (!data.promote(player.getUUID(), targetUuid)) {
            source.sendFailure(EconomyCommandUtil.error(
                    Component.translatable("command.futureshops.franchise.promote.failed")));
            return 0;
        }
        source.sendSuccess(() -> EconomyCommandUtil.success(
                Component.translatable("command.futureshops.franchise.promote.success", targetName)), false);
        return 1;
    }

    private static int manage(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(EconomyCommandUtil.error(playerOnly()));
            return 0;
        }
        C2SFranchiseActionPacket.sendFranchiseDataToPlayer(player);
        return 1;
    }

    private static int leave(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(EconomyCommandUtil.error(playerOnly()));
            return 0;
        }
        FranchiseSavedData data = FranchiseSavedData.get(player.getServer());
        if (!data.leave(player.getUUID())) {
            source.sendFailure(EconomyCommandUtil.error(
                    Component.translatable("command.futureshops.franchise.not_in_franchise")));
            return 0;
        }
        source.sendSuccess(() -> EconomyCommandUtil.success(
                Component.translatable("command.futureshops.franchise.leave.success")), false);
        return 1;
    }

    private static int disband(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(EconomyCommandUtil.error(playerOnly()));
            return 0;
        }
        FranchiseSavedData data = FranchiseSavedData.get(player.getServer());
        FranchiseSavedData.Franchise franchise = data.getFranchise(player.getUUID());
        if (franchise == null) {
            source.sendFailure(EconomyCommandUtil.error(
                    Component.translatable("command.futureshops.franchise.not_in_franchise")));
            return 0;
        }
        for (UUID member : franchise.getMembers()) {
            if (member.equals(player.getUUID())) continue;
            ServerPlayer memberPlayer = player.getServer().getPlayerList().getPlayer(member);
            if (memberPlayer != null) {
                memberPlayer.sendSystemMessage(EconomyCommandUtil.error(
                        Component.translatable("command.futureshops.franchise.disband.notify", franchise.name)));
            }
        }
        if (!data.disband(player.getUUID())) {
            source.sendFailure(EconomyCommandUtil.error(
                    Component.translatable("command.futureshops.franchise.disband.not_leader")));
            return 0;
        }
        source.sendSuccess(() -> EconomyCommandUtil.success(
                Component.translatable("command.futureshops.franchise.disband.success")), false);
        return 1;
    }

    // ═══ Helpers ═══

    private static UUID resolveUuid(MinecraftServer server, String name) {
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        if (online != null) return online.getUUID();
        try {
            return server.getProfileCache() != null
                    ? server.getProfileCache().get(name).map(p -> p.getId()).orElse(null)
                    : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}

