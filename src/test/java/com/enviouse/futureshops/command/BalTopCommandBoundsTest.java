package com.enviouse.futureshops.command;

import com.enviouse.futureshops.server.util.PageBounds;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import net.minecraft.commands.CommandSourceStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BalTopCommandBoundsTest {
    @Test
    void commandAcceptsTheDocumentedMaximumAndRejectsLargerPages() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        BalTopCommand.register(dispatcher);

        assertParses(dispatcher, "baltop " + PageBounds.MAX_PAGE_INDEX);
        assertParses(dispatcher, "baltop ui " + PageBounds.MAX_PAGE_INDEX);
        assertRejected(dispatcher, "baltop " + (PageBounds.MAX_PAGE_INDEX + 1));
        assertRejected(dispatcher, "baltop ui " + (PageBounds.MAX_PAGE_INDEX + 1));
    }

    private static void assertParses(CommandDispatcher<CommandSourceStack> dispatcher, String command) {
        ParseResults<CommandSourceStack> result = dispatcher.parse(command, null);
        assertEquals(0, result.getReader().getRemainingLength());
        assertTrue(result.getExceptions().isEmpty());
    }

    private static void assertRejected(CommandDispatcher<CommandSourceStack> dispatcher, String command) {
        ParseResults<CommandSourceStack> result = dispatcher.parse(command, null);
        assertTrue(result.getReader().getRemainingLength() > 0 || !result.getExceptions().isEmpty());
    }
}
