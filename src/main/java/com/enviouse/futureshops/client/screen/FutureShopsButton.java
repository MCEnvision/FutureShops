package com.enviouse.futureshops.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.Objects;

public final class FutureShopsButton extends Button {
    private final ShopUiUtil.ButtonStyle style;

    private FutureShopsButton(
            int x,
            int y,
            int width,
            int height,
            Component message,
            OnPress onPress,
            ShopUiUtil.ButtonStyle style
    ) {
        super(x, y, width, height, message, onPress,
                DEFAULT_NARRATION);
        this.style = Objects.requireNonNull(style, "style");
    }

    public static Builder styled(
            Component message,
            OnPress onPress
    ) {
        return new Builder(message, onPress);
    }

    @Override
    protected void renderWidget(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        int renderMouseX = mouseX;
        int renderMouseY = mouseY;
        if (active && isFocused() && !isHovered()) {
            renderMouseX = getX();
            renderMouseY = getY();
        }
        ShopUiUtil.button(graphics,
                Minecraft.getInstance().font, null,
                renderMouseX, renderMouseY,
                getX(), getY(), getWidth(), getHeight(),
                getMessage(), style, active, null);
    }

    public static final class Builder {
        private final Component message;
        private final OnPress onPress;
        private int x;
        private int y;
        private int width = DEFAULT_WIDTH;
        private int height = DEFAULT_HEIGHT;
        private ShopUiUtil.ButtonStyle style =
                ShopUiUtil.ButtonStyle.SECONDARY;

        private Builder(
                Component message,
                OnPress onPress
        ) {
            this.message = Objects.requireNonNull(
                    message, "message");
            this.onPress = Objects.requireNonNull(
                    onPress, "onPress");
        }

        public Builder bounds(
                int x,
                int y,
                int width,
                int height
        ) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            return this;
        }

        public Builder style(ShopUiUtil.ButtonStyle style) {
            this.style = Objects.requireNonNull(style, "style");
            return this;
        }

        public FutureShopsButton build() {
            return new FutureShopsButton(
                    x, y, width, height,
                    message, onPress, style);
        }
    }
}
