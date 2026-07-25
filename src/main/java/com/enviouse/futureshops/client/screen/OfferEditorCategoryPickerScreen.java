package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.client.ShopClientState;
import com.enviouse.futureshops.client.ShopColors;
import com.enviouse.futureshops.data.CatalogCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

public final class OfferEditorCategoryPickerScreen extends Screen
        implements ShopScreenMarker {
    private final Screen parent;
    private final Consumer<String> selection;
    private final List<RowButton> rowButtons = new ArrayList<>();
    private List<CatalogCategory> filtered = List.of();
    private EditBox search;
    private String query = "";
    private int selectedIndex;
    private int scroll;
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private int visibleRows;

    public OfferEditorCategoryPickerScreen(
            Screen parent,
            String currentCategoryId,
            Consumer<String> selection
    ) {
        super(Component.translatable(
                "gui.futureshops.offer_editor.category_picker.title"));
        this.parent = Objects.requireNonNull(parent, "parent");
        this.selection = Objects.requireNonNull(
                selection, "selection");
        List<CatalogCategory> categories =
                ShopClientState.getCatalogCategories();
        for (int index = 0; index < categories.size(); index++) {
            if (categories.get(index).id().equals(
                    currentCategoryId)) {
                selectedIndex = index + 1;
                break;
            }
        }
    }

    @Override
    protected void init() {
        panelWidth = Math.min(360, width - 24);
        panelHeight = Math.min(300, height - 24);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        visibleRows = Math.max(1,
                (panelHeight - 102) / 24);

        search = new EditBox(font, left + 12, top + 34,
                panelWidth - 24, 20,
                Component.translatable(
                        "gui.futureshops.offer_editor.picker.search"));
        search.setHint(Component.translatable(
                "gui.futureshops.offer_editor.category_picker.search_hint"));
        search.setMaxLength(96);
        search.setValue(query);
        search.setResponder(value -> {
            query = value;
            selectedIndex = 0;
            scroll = 0;
            rebuildRows();
        });
        search.setTooltip(Tooltip.create(Component.translatable(
                "gui.futureshops.offer_editor.help.category_search")));
        addRenderableWidget(search);

        Button choose = FutureShopsButton.styled(Component.translatable(
                        "gui.futureshops.offer_editor.picker.choose"),
                ignored -> chooseSelected())
                .bounds(left + panelWidth - 156,
                        top + panelHeight - 28, 70, 20).build();
        choose.setTooltip(Tooltip.create(Component.translatable(
                "gui.futureshops.offer_editor.help.category_choose")));
        addRenderableWidget(choose);
        Button cancel = FutureShopsButton.styled(Component.translatable(
                        "gui.futureshops.offer_editor.cancel"),
                ignored -> onClose())
                .bounds(left + panelWidth - 80,
                        top + panelHeight - 28, 68, 20).build();
        cancel.setTooltip(Tooltip.create(Component.translatable(
                "gui.futureshops.offer_editor.help.cancel")));
        addRenderableWidget(cancel);
        rebuildRows();
    }

    private void rebuildRows() {
        for (RowButton row : rowButtons) {
            removeWidget(row.button());
        }
        rowButtons.clear();
        String normalized = query.strip().toLowerCase(Locale.ROOT);
        List<CatalogCategory> categories =
                ShopClientState.getCatalogCategories();
        filtered = categories.stream().filter(category ->
                normalized.isEmpty()
                        || category.id().toLowerCase(Locale.ROOT)
                        .contains(normalized)
                        || category.displayName()
                        .toLowerCase(Locale.ROOT)
                        .contains(normalized)).toList();
        int rowCount = filtered.size() + 1;
        selectedIndex = Math.max(0, Math.min(selectedIndex,
                Math.max(0, rowCount - 1)));
        scroll = Math.max(0, Math.min(scroll,
                Math.max(0, rowCount - visibleRows)));
        for (int visibleIndex = 0;
             visibleIndex < visibleRows; visibleIndex++) {
            int index = scroll + visibleIndex;
            if (index >= rowCount) {
                break;
            }
            String id = index == 0 ? "all"
                    : filtered.get(index - 1).id();
            Component label = index == 0
                    ? Component.translatable(
                    "gui.futureshops.offer_editor.no_category")
                    : Component.literal(
                    filtered.get(index - 1).displayName()
                            + "  " + id);
            Button button = FutureShopsButton.styled(label, ignored -> {
                        selectedIndex = index;
                        chooseSelected();
                    })
                    .bounds(left + 12, top + 62
                                    + visibleIndex * 24,
                            panelWidth - 24, 20).build();
            button.setTooltip(Tooltip.create(index == 0
                    ? Component.translatable(
                    "gui.futureshops.offer_editor.help.no_category")
                    : Component.literal(id)));
            rowButtons.add(new RowButton(index, button));
            addRenderableWidget(button);
        }
    }

    private void moveSelection(int direction) {
        int count = filtered.size() + 1;
        selectedIndex = Math.max(0, Math.min(
                selectedIndex + direction, count - 1));
        if (selectedIndex < scroll) {
            scroll = selectedIndex;
            rebuildRows();
        } else if (selectedIndex >= scroll + visibleRows) {
            scroll = selectedIndex - visibleRows + 1;
            rebuildRows();
        }
    }

    private void chooseSelected() {
        if (minecraft == null) {
            return;
        }
        String id = selectedIndex == 0 ? "all"
                : filtered.get(selectedIndex - 1).id();
        selection.accept(id);
        minecraft.setScreen(parent);
    }

    @Override
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        ShopUiUtil.renderDimBackdrop(graphics, width, height);
        graphics.fill(left, top, left + panelWidth,
                top + panelHeight, ShopColors.SURFACE_BASE);
        ShopUiUtil.drawSoftOutline(graphics, left, top,
                panelWidth, panelHeight, ShopColors.BORDER_STRONG,
                ShopColors.BORDER_SUBTLE);
        ShopUiUtil.renderAccentLine(graphics, left + 2,
                top, panelWidth - 4);
        graphics.drawString(font, title, left + 12,
                top + 10, ShopColors.TEXT_STRONG, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double delta
    ) {
        scroll = Math.max(0, scroll
                + (delta < 0 ? 1 : -1));
        rebuildRows();
        return true;
    }

    @Override
    public boolean keyPressed(
            int keyCode,
            int scanCode,
            int modifiers
    ) {
        if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_F) {
            setFocused(search);
            search.setFocused(true);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (!search.isFocused()
                && keyCode == GLFW.GLFW_KEY_DOWN) {
            moveSelection(1);
            return true;
        }
        if (!search.isFocused()
                && keyCode == GLFW.GLFW_KEY_UP) {
            moveSelection(-1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER
                || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            chooseSelected();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record RowButton(int index, Button button) {
    }
}
