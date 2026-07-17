package com.enviouse.futureshops.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * Client keybinds. OPEN_SHOP exists because some mods (TacZ guns being the
 * canonical case) cancel the vanilla use-item input while their item is held,
 * so right-clicking a shop block never reaches the server. The keybind sends
 * its own packet and therefore works no matter what the held item does to
 * mouse input. Default 'B' — free in vanilla and TacZ.
 */
public final class ModKeyMappings {
    public static final KeyMapping OPEN_SHOP = new KeyMapping(
            "key.futureshops.open_shop",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            "key.categories.futureshops");

    private ModKeyMappings() {
    }
}
