package com.enviouse.futureshopsp.command;

import com.enviouse.futureshopsp.server.debug.DebugDiagnostics;
import com.enviouse.futureshopsp.server.debug.DebugModule;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.concurrent.CompletableFuture;

public final class DebugCommand {
    private DebugCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("futureshops")
                .then(Commands.literal("debug")
                        .then(Commands.literal("on")
                                .then(Commands.argument("module", StringArgumentType.word())
                                        .suggests((context, builder) -> suggestModules(builder))
                                        .executes(context -> enable(context.getSource(),
                                                StringArgumentType.getString(context, "module")))))
                        .then(Commands.literal("off")
                                .executes(context -> disable(context.getSource())))
                        .then(Commands.literal("status")
                                .executes(context -> status(context.getSource())))));
    }

    private static int enable(CommandSourceStack source, String value) {
        if (!authorized(source)) {
            return 0;
        }
        DebugModule module = DebugModule.parse(value).orElse(null);
        if (module == null) {
            DebugDiagnostics.invalidModule(value);
            source.sendFailure(Component.literal("invalid FutureShops debug module"));
            return 0;
        }
        DebugDiagnostics.DebugToggleResult result = DebugDiagnostics.enable(module);
        source.sendSuccess(() -> Component.literal("FutureShops debug on, module " + module.id()
                + ", session " + result.session().sessionId()), true);
        return 1;
    }

    private static int disable(CommandSourceStack source) {
        if (!authorized(source)) {
            return 0;
        }
        DebugDiagnostics.disable();
        source.sendSuccess(() -> Component.literal("FutureShops debug off"), true);
        return 1;
    }

    private static int status(CommandSourceStack source) {
        if (!authorized(source)) {
            return 0;
        }
        source.sendSuccess(() -> Component.literal("FutureShops " + DebugDiagnostics.statusLine()), false);
        return 1;
    }

    private static boolean authorized(CommandSourceStack source) {
        if (source.hasPermission(2)) {
            return true;
        }
        DebugDiagnostics.unauthorized();
        source.sendFailure(Component.literal("operator permission is required"));
        return false;
    }

    private static CompletableFuture<Suggestions> suggestModules(
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        for (DebugModule module : DebugModule.values()) {
            builder.suggest(module.id());
        }
        return builder.buildFuture();
    }
}
