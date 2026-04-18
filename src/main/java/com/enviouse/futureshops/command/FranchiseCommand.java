package com.enviouse.futureshops.command;

import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.network.packets.C2SFranchiseActionPacket;
import com.enviouse.futureshops.server.shop.FranchiseSavedData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;
import java.util.UUID;

/**
 * Registers the {@code /shop franchise} command tree.
 * Subcommands: create, invite, accept, decline, kick, promote, manage, leave, disband.
 */
public final class FranchiseCommand {
    private FranchiseCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("franchise")
                // /franchise create <name>
                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> create(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                // /franchise invite <player>
                .then(Commands.literal("invite")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(ctx -> invite(ctx.getSource(), StringArgumentType.getString(ctx, "player")))))
                // /franchise accept
                .then(Commands.literal("accept")
                        .executes(ctx -> accept(ctx.getSource())))
                // /franchise decline
                .then(Commands.literal("decline")
                        .executes(ctx -> decline(ctx.getSource())))
                // /franchise kick <player>
                .then(Commands.literal("kick")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(ctx -> kick(ctx.getSource(), StringArgumentType.getString(ctx, "player")))))
                // /franchise promote <player>
                .then(Commands.literal("promote")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(ctx -> promote(ctx.getSource(), StringArgumentType.getString(ctx, "player")))))
                // /franchise manage
                .then(Commands.literal("manage")
                        .executes(ctx -> manage(ctx.getSource())))
                // /franchise leave
                .then(Commands.literal("leave")
                        .executes(ctx -> leave(ctx.getSource())))
                // /franchise disband
                .then(Commands.literal("disband")
                        .executes(ctx -> disband(ctx.getSource()))));
    }

    private static int create(CommandSourceStack source, String name) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(EconomyCommandUtil.error(Component.literal("Player only command")));
            return 0;
        }
        if (name.isBlank() || name.length() > 32) {
            source.sendFailure(EconomyCommandUtil.error(Component.literal("Franchise name must be 1-32 characters.")));
            return 0;
        }
        FranchiseSavedData data = FranchiseSavedData.get(player.getServer());
        FranchiseSavedData.CreateResult result = data.createFranchise(player.getUUID(), name);
        if (!result.success()) {
            source.sendFailure(EconomyCommandUtil.error(Component.literal(
                    "ALREADY_IN_FRANCHISE".equals(result.code())
                            ? "You're already in a franchise! Use /franchise leave first."
                            : "Could not create franchise.")));
            return 0;
        }
        source.sendSuccess(() -> EconomyCommandUtil.success(
                Component.literal("✨ Franchise \"" + name + "\" created! Invite members with /franchise invite <player>")), false);
        return 1;
    }

    private static int invite(CommandSourceStack source, String targetName) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(EconomyCommandUtil.error(Component.literal("Player only command")));
            return 0;
        }
        MinecraftServer server = player.getServer();
        ServerPlayer target = server.getPlayerList().getPlayerByName(targetName);
        if (target == null) {
            source.sendFailure(EconomyCommandUtil.error(Component.literal("Player \"" + targetName + "\" is not online.")));
            return 0;
        }
        FranchiseSavedData data = FranchiseSavedData.get(server);
        if (!data.invite(player.getUUID(), target.getUUID())) {
            source.sendFailure(EconomyCommandUtil.error(Component.literal("Could not invite. Are you a franchise leader? Is the player already in a franchise?")));
            return 0;
        }
        FranchiseSavedData.Franchise franchise = data.getFranchise(player.getUUID());
        String franchiseName = franchise != null ? franchise.name : "Unknown";

        source.sendSuccess(() -> EconomyCommandUtil.success(
                Component.literal("📨 Invitation sent to " + targetName + "!")), false);

        // Notify the invited player
        target.sendSystemMessage(Component.literal("")
                .append(Component.literal("═══ Franchise Invite ═══").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(Component.literal("\n"))
                .append(Component.literal(player.getGameProfile().getName() + " invited you to join ").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("\"" + franchiseName + "\"").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
                .append(Component.literal("\n"))
                .append(Component.literal("Type ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("/franchise accept").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD))
                .append(Component.literal(" or ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("/franchise decline").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)));
        return 1;
    }

    private static int accept(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(EconomyCommandUtil.error(Component.literal("Player only command")));
            return 0;
        }
        FranchiseSavedData data = FranchiseSavedData.get(player.getServer());
        if (!data.acceptInvite(player.getUUID())) {
            source.sendFailure(EconomyCommandUtil.error(Component.literal("No pending franchise invite found.")));
            return 0;
        }
        FranchiseSavedData.Franchise franchise = data.getFranchise(player.getUUID());
        String name = franchise != null ? franchise.name : "Unknown";
        source.sendSuccess(() -> EconomyCommandUtil.success(
                Component.literal("✅ You joined franchise \"" + name + "\"! You can now manage shared shops.")), false);
        return 1;
    }

    private static int decline(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(EconomyCommandUtil.error(Component.literal("Player only command")));
            return 0;
        }
        FranchiseSavedData data = FranchiseSavedData.get(player.getServer());
        if (!data.declineInvite(player.getUUID())) {
            source.sendFailure(EconomyCommandUtil.error(Component.literal("No pending franchise invite found.")));
            return 0;
        }
        source.sendSuccess(() -> EconomyCommandUtil.success(Component.literal("❌ Franchise invite declined.")), false);
        return 1;
    }

    private static int kick(CommandSourceStack source, String targetName) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(EconomyCommandUtil.error(Component.literal("Player only command")));
            return 0;
        }
        MinecraftServer server = player.getServer();
        UUID targetUuid = resolveUuid(server, targetName);
        if (targetUuid == null) {
            source.sendFailure(EconomyCommandUtil.error(Component.literal("Unknown player: " + targetName)));
            return 0;
        }
        FranchiseSavedData data = FranchiseSavedData.get(server);
        if (!data.kick(player.getUUID(), targetUuid)) {
            source.sendFailure(EconomyCommandUtil.error(Component.literal("Could not kick. Are you the franchise leader?")));
            return 0;
        }
        source.sendSuccess(() -> EconomyCommandUtil.success(
                Component.literal("🚫 " + targetName + " has been kicked from the franchise.")), false);
        // Notify kicked player if online
        ServerPlayer target = server.getPlayerList().getPlayer(targetUuid);
        if (target != null) {
            target.sendSystemMessage(EconomyCommandUtil.error(
                    Component.literal("You have been kicked from the franchise by " + player.getGameProfile().getName() + ".")));
        }
        return 1;
    }

    private static int promote(CommandSourceStack source, String targetName) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(EconomyCommandUtil.error(Component.literal("Player only command")));
            return 0;
        }
        MinecraftServer server = player.getServer();
        UUID targetUuid = resolveUuid(server, targetName);
        if (targetUuid == null) {
            source.sendFailure(EconomyCommandUtil.error(Component.literal("Unknown player: " + targetName)));
            return 0;
        }
        FranchiseSavedData data = FranchiseSavedData.get(server);
        if (!data.promote(player.getUUID(), targetUuid)) {
            source.sendFailure(EconomyCommandUtil.error(Component.literal("Could not promote. Are you the franchise leader?")));
            return 0;
        }
        source.sendSuccess(() -> EconomyCommandUtil.success(
                Component.literal("👑 " + targetName + " is now the franchise leader!")), false);
        return 1;
    }

    private static int manage(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(EconomyCommandUtil.error(Component.literal("Player only command")));
            return 0;
        }
        // Open the franchise management GUI by sending franchise data packet
        C2SFranchiseActionPacket.sendFranchiseDataToPlayer(player);
        return 1;
    }

    private static int leave(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(EconomyCommandUtil.error(Component.literal("Player only command")));
            return 0;
        }
        FranchiseSavedData data = FranchiseSavedData.get(player.getServer());
        if (!data.leave(player.getUUID())) {
            source.sendFailure(EconomyCommandUtil.error(Component.literal("You're not in a franchise.")));
            return 0;
        }
        source.sendSuccess(() -> EconomyCommandUtil.success(Component.literal("👋 You have left the franchise.")), false);
        return 1;
    }

    private static int disband(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(EconomyCommandUtil.error(Component.literal("Player only command")));
            return 0;
        }
        FranchiseSavedData data = FranchiseSavedData.get(player.getServer());
        FranchiseSavedData.Franchise franchise = data.getFranchise(player.getUUID());
        if (franchise == null) {
            source.sendFailure(EconomyCommandUtil.error(Component.literal("You're not in a franchise.")));
            return 0;
        }
        // Notify members before disbanding
        for (UUID member : franchise.getMembers()) {
            if (member.equals(player.getUUID())) continue;
            ServerPlayer memberPlayer = player.getServer().getPlayerList().getPlayer(member);
            if (memberPlayer != null) {
                memberPlayer.sendSystemMessage(EconomyCommandUtil.error(
                        Component.literal("The franchise \"" + franchise.name + "\" has been disbanded by the leader.")));
            }
        }
        if (!data.disband(player.getUUID())) {
            source.sendFailure(EconomyCommandUtil.error(Component.literal("Only the franchise leader can disband.")));
            return 0;
        }
        source.sendSuccess(() -> EconomyCommandUtil.success(
                Component.literal("💥 Franchise disbanded. All members have been removed.")), false);
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

    private static String resolvePlayerName(MinecraftServer server, UUID uuid) {
        ServerPlayer online = server.getPlayerList().getPlayer(uuid);
        if (online != null) return online.getGameProfile().getName();
        try {
            return server.getProfileCache() != null
                    ? server.getProfileCache().get(uuid).map(p -> p.getName()).orElse(uuid.toString().substring(0, 8))
                    : uuid.toString().substring(0, 8);
        } catch (Exception ignored) {
            return uuid.toString().substring(0, 8);
        }
    }
}

