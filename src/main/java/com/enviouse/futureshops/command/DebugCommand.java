package com.enviouse.futureshops.command;

import com.enviouse.futureshops.server.debug.DebugDiagnostics;
import com.enviouse.futureshops.server.debug.DebugModule;
import com.enviouse.futureshops.server.debug.DebugSelector;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.concurrent.CompletableFuture;

/** Operator controls for bounded server diagnostics. */
public final class DebugCommand {
    private DebugCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("futureshops")
                .then(Commands.literal("debug")
                        .then(Commands.literal("on")
                                .then(Commands.argument("module", StringArgumentType.word())
                                        .suggests((context, builder) -> suggestModules(builder))
                                        .executes(context -> enable(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "module"),
                                                DebugSelector.none()))
                                        .then(Commands.literal("request")
                                                .then(Commands.argument("root", StringArgumentType.word())
                                                        .executes(context -> enable(
                                                                context.getSource(),
                                                                StringArgumentType.getString(context, "module"),
                                                                DebugSelector.request(StringArgumentType.getString(context, "root"))))))
                                        .then(Commands.literal("actor")
                                                .then(Commands.argument("player", EntityArgument.player())
                                                        .executes(context -> enable(
                                                                context.getSource(),
                                                                StringArgumentType.getString(context, "module"),
                                                                DebugSelector.actor(EntityArgument.getPlayer(context, "player").getUUID())))))))
                        .then(Commands.literal("off")
                                .executes(context -> disable(context.getSource())))
                        .then(Commands.literal("status")
                                .executes(context -> status(context.getSource())))));
    }

    private static int enable(CommandSourceStack source, String value,
                               DebugSelector selector) {
        if (!authorized(source)) {
            return 0;
        }
        DebugModule module = DebugModule.parse(value).orElse(null);
        if (module == null) {
            DebugDiagnostics.invalidModule(value);
            source.sendFailure(Component.translatable(
                    "command.futureshops.debug.invalid_module"));
            return 0;
        }
        DebugDiagnostics.DebugToggleResult result = DebugDiagnostics.enable(module, selector);
        source.sendSuccess(() -> Component.translatable(
                result.changed()
                        ? "command.futureshops.debug.enabled"
                        : "command.futureshops.debug.already_enabled",
                module.id(), result.session().captureId().toString()), true);
        return 1;
    }

    private static int disable(CommandSourceStack source) {
        if (!authorized(source)) {
            return 0;
        }
        DebugDiagnostics.disable();
        source.sendSuccess(() -> Component.translatable(
                "command.futureshops.debug.disabled"), true);
        return 1;
    }

    private static int status(CommandSourceStack source) {
        if (!authorized(source)) {
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(
                "command.futureshops.debug.status",
                DebugDiagnostics.statusLine()), false);
        return 1;
    }

    private static boolean authorized(CommandSourceStack source) {
        if (source.hasPermission(2)) {
            return true;
        }
        DebugDiagnostics.unauthorized();
        source.sendFailure(Component.translatable(
                "command.futureshops.debug.unauthorized"));
        return false;
    }

    private static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestModules(
            SuggestionsBuilder builder) {
        for (DebugModule module : DebugModule.values()) {
            builder.suggest(module.id());
        }
        return builder.buildFuture();
    }
}
