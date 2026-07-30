package com.enviouse.futureshops.command;

import com.enviouse.futureshops.data.BulkSellTarget;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import net.minecraft.commands.CommandSourceStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SellAllCommandTest {
    @Test
    void supportedTargetsAndConfirmFormsParseCompletely() {
        CommandDispatcher<CommandSourceStack> dispatcher =
                new CommandDispatcher<>();
        SellAllCommand.register(dispatcher);

        assertParses(dispatcher, "sellall adminshop");
        assertParses(dispatcher, "sellall adminshop confirm");
        assertParses(dispatcher, "sellall playershops");
        assertParses(dispatcher, "sellall playershops confirm");
    }

    @Test
    void targetNamesAreExactAndCaseInsensitive() {
        assertEquals(BulkSellTarget.ADMIN_SHOP,
                BulkSellTarget.fromCommandName("ADMINSHOP"));
        assertEquals(BulkSellTarget.PLAYER_SHOPS,
                BulkSellTarget.fromCommandName(" playershops "));
        assertThrows(IllegalArgumentException.class, () ->
                BulkSellTarget.fromCommandName("auctionhouse"));
    }

    private static void assertParses(
            CommandDispatcher<CommandSourceStack> dispatcher,
            String command
    ) {
        ParseResults<CommandSourceStack> result =
                dispatcher.parse(command, null);
        assertEquals(0, result.getReader().getRemainingLength());
        assertTrue(result.getExceptions().isEmpty());
    }
}
