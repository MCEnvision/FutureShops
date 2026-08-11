package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.catalog.offer.OfferItemComponent;
import com.enviouse.futureshops.client.ShopColors;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

public final class OfferEditorItemPickerScreen extends Screen
        implements ShopScreenMarker {
    public enum Source {
        INVENTORY,
        REGISTRY
    }

    private final Screen parent;
    private final Source source;
    private final Component destination;
    private final Consumer<OfferItemComponent> selection;
    private final Function<OfferItemComponent, Screen> completionScreen;
    private List<Entry> allEntries = List.of();
    private List<Entry> filteredEntries = List.of();
    private EditBox search;
    private Button choose;
    private String query = "";
    private int selectedIndex;
    private int scroll;
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private int rowTop;
    private int rowHeight;
    private int visibleRows;
    private int hoveredIndex = -1;

    public OfferEditorItemPickerScreen(
            Screen parent,
            Source source,
            Component destination,
            Consumer<OfferItemComponent> selection
    ) {
        this(parent, source, destination, selection, null);
    }

    public static OfferEditorItemPickerScreen forNewOffer(
            Screen parent,
            String categoryId
    ) {
        return new OfferEditorItemPickerScreen(
                parent,
                Source.REGISTRY,
                Component.translatable(
                        "gui.futureshops.offer_editor.picker.destination.output"),
                ignored -> {
                },
                component -> AdminOfferEditorScreen.create(
                        parent, component, categoryId));
    }

    private OfferEditorItemPickerScreen(
            Screen parent,
            Source source,
            Component destination,
            Consumer<OfferItemComponent> selection,
            Function<OfferItemComponent, Screen> completionScreen
    ) {
        super(Component.translatable(
                source == Source.INVENTORY
                        ? "gui.futureshops.offer_editor.picker.inventory_title"
                        : "gui.futureshops.offer_editor.picker.registry_title"));
        this.parent = Objects.requireNonNull(parent, "parent");
        this.source = Objects.requireNonNull(source, "source");
        this.destination = Objects.requireNonNull(
                destination, "destination");
        this.selection = Objects.requireNonNull(
                selection, "selection");
        this.completionScreen = completionScreen;
    }

    @Override
    protected void init() {
        allEntries = source == Source.INVENTORY
                ? inventoryEntries() : registryEntries();
        panelWidth = Math.min(430, width - 24);
        panelHeight = Math.min(340, height - 24);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        rowTop = top + 64;
        rowHeight = 30;
        visibleRows = Math.max(1,
                (panelHeight - 104) / rowHeight);

        search = new EditBox(font, left + 12, top + 38,
                panelWidth - 24, 20,
                Component.translatable(
                        "gui.futureshops.offer_editor.picker.search"));
        search.setHint(Component.translatable(
                "gui.futureshops.offer_editor.picker.search_hint"));
        search.setMaxLength(96);
        search.setValue(query);
        search.setResponder(value -> {
            query = value;
            selectedIndex = 0;
            scroll = 0;
            filter();
        });
        search.setTooltip(Tooltip.create(Component.translatable(
                "gui.futureshops.offer_editor.help.picker_search")));
        addRenderableWidget(search);

        filter();
        choose = FutureShopsButton.styled(Component.translatable(
                        "gui.futureshops.offer_editor.picker.choose"),
                ignored -> chooseSelected())
                .bounds(left + panelWidth - 156,
                        top + panelHeight - 28, 70, 20).build();
        choose.active = !filteredEntries.isEmpty();
        choose.setTooltip(Tooltip.create(Component.translatable(
                "gui.futureshops.offer_editor.help.picker_choose")));
        addRenderableWidget(choose);
        Button cancel = FutureShopsButton.styled(Component.translatable(
                        "gui.futureshops.offer_editor.cancel"),
                ignored -> onClose())
                .bounds(left + panelWidth - 80,
                        top + panelHeight - 28, 68, 20).build();
        cancel.setTooltip(Tooltip.create(Component.translatable(
                "gui.futureshops.offer_editor.help.cancel")));
        addRenderableWidget(cancel);
    }

    private List<Entry> inventoryEntries() {
        if (minecraft == null || minecraft.player == null) {
            return List.of();
        }
        LinkedHashMap<String, Entry> entries =
                new LinkedHashMap<>();
        List<ItemStack> stacks = new ArrayList<>(
                minecraft.player.getInventory().items);
        stacks.addAll(minecraft.player.getInventory().offhand);
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }
            ResourceLocation identifier =
                    ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (identifier == null) {
                continue;
            }
            String nbt = stack.hasTag()
                    ? stack.getTag().toString() : "";
            String key = identifier + "\u0000" + nbt;
            entries.putIfAbsent(key, new Entry(
                    identifier.toString(), nbt,
                    stack.getHoverName().getString(),
                    identifier + " "
                            + stack.getHoverName().getString()));
        }
        return List.copyOf(entries.values());
    }

    private List<Entry> registryEntries() {
        List<Entry> entries = new ArrayList<>();
        for (Item item : ForgeRegistries.ITEMS) {
            if (item == Items.AIR) {
                continue;
            }
            ResourceLocation identifier =
                    ForgeRegistries.ITEMS.getKey(item);
            if (identifier == null) {
                continue;
            }
            String modName = ModList.get()
                    .getModContainerById(identifier.getNamespace())
                    .map(container -> container.getModInfo()
                            .getDisplayName())
                    .orElse(identifier.getNamespace());
            String tags = item.builtInRegistryHolder().tags()
                    .map(tag -> tag.location().toString())
                    .collect(java.util.stream.Collectors.joining(" "));
            String name = item.getDescription().getString();
            entries.add(new Entry(identifier.toString(), "",
                    name, identifier + " " + name + " "
                    + modName + " " + tags));
        }
        entries.sort(Comparator.comparing(Entry::itemId));
        return List.copyOf(entries);
    }

    private void filter() {
        String normalized = query.strip()
                .toLowerCase(Locale.ROOT);
        filteredEntries = allEntries.stream()
                .filter(entry -> normalized.isEmpty()
                        || entry.searchText()
                        .toLowerCase(Locale.ROOT)
                        .contains(normalized))
                .toList();
        clampSelection();
        if (choose != null) {
            choose.active = !filteredEntries.isEmpty();
        }
    }

    private void clampSelection() {
        selectedIndex = Math.max(0, Math.min(selectedIndex,
                Math.max(0, filteredEntries.size() - 1)));
        int maximumScroll = Math.max(0,
                filteredEntries.size() - visibleRows);
        scroll = Math.max(0, Math.min(scroll, maximumScroll));
        if (selectedIndex < scroll) {
            scroll = selectedIndex;
        } else if (selectedIndex >= scroll + visibleRows) {
            scroll = selectedIndex - visibleRows + 1;
        }
    }

    @Override
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        hoveredIndex = -1;
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
        graphics.drawString(font, destination, left + 12,
                top + 23, ShopColors.TEXT_MUTED, false);

        if (filteredEntries.isEmpty()) {
            graphics.drawCenteredString(font,
                    Component.translatable(
                            "gui.futureshops.offer_editor.picker.no_results"),
                    width / 2, rowTop + 20,
                    ShopColors.TEXT_FAINT);
        }
        int last = Math.min(filteredEntries.size(),
                scroll + visibleRows);
        for (int index = scroll; index < last; index++) {
            int y = rowTop + (index - scroll) * rowHeight;
            boolean selected = index == selectedIndex;
            boolean hovered = mouseX >= left + 10
                    && mouseX < left + panelWidth - 10
                    && mouseY >= y && mouseY < y + rowHeight - 2;
            if (hovered) {
                hoveredIndex = index;
            }
            graphics.fill(left + 10, y,
                    left + panelWidth - 10, y + rowHeight - 2,
                    selected ? ShopColors.SURFACE_PRESSED
                            : hovered ? ShopColors.SURFACE_OVERLAY
                            : ShopColors.SURFACE_RAISED);
            ShopUiUtil.drawBorder(graphics, left + 10, y,
                    panelWidth - 20, rowHeight - 2,
                    selected ? ShopColors.BORDER_GLOW
                            : ShopColors.BORDER_MUTED);
            Entry entry = filteredEntries.get(index);
            ShopUiUtil.renderItemIconWithNbt(graphics, font,
                    entry.itemId(), entry.exactNbt(),
                    left + 16, y + 5);
            graphics.drawString(font,
                    font.plainSubstrByWidth(entry.name(),
                            panelWidth - 80),
                    left + 40, y + 5,
                    ShopColors.TEXT_STRONG, false);
            graphics.drawString(font,
                    font.plainSubstrByWidth(entry.itemId(),
                            panelWidth - 80),
                    left + 40, y + 17,
                    ShopColors.TEXT_MUTED, false);
        }
        ShopUiUtil.renderScrollIndicators(graphics, font,
                left + 10, rowTop, panelWidth - 20,
                visibleRows * rowHeight, scroll,
                visibleRows, filteredEntries.size());
        super.render(graphics, mouseX, mouseY, partialTick);
        if (hoveredIndex >= 0
                && hoveredIndex < filteredEntries.size()) {
            Entry entry = filteredEntries.get(hoveredIndex);
            ShopUiUtil.renderItemTooltip(graphics, font,
                    entry.itemId(), entry.exactNbt(),
                    mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(
            double mouseX,
            double mouseY,
            int button
    ) {
        if (button == 0 && hoveredIndex >= 0) {
            selectedIndex = hoveredIndex;
            if (hasShiftDown()) {
                chooseSelected();
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double delta
    ) {
        scroll = Math.max(0, scroll
                + (delta < 0 ? 1 : -1));
        clampSelection();
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
                && (keyCode == GLFW.GLFW_KEY_DOWN
                || keyCode == GLFW.GLFW_KEY_UP)) {
            selectedIndex += keyCode == GLFW.GLFW_KEY_DOWN ? 1 : -1;
            clampSelection();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER
                || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            chooseSelected();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void chooseSelected() {
        if (minecraft == null || filteredEntries.isEmpty()) {
            return;
        }
        Entry entry = filteredEntries.get(selectedIndex);
        OfferItemComponent component = new OfferItemComponent(
                "", entry.itemId(), 1, entry.exactNbt());
        selection.accept(component);
        minecraft.setScreen(completionScreen == null
                ? parent : completionScreen.apply(component));
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

    private record Entry(
            String itemId,
            String exactNbt,
            String name,
            String searchText
    ) {
    }
}
