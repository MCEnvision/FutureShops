package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.client.ShopColors;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class BazaarItemBrowserScreen extends Screen
        implements ShopScreenMarker {
    private static final int CELL = 28;
    private static final int CELL_GAP = 4;
    private static final int MOD_ROW_HEIGHT = 18;

    private record ItemEntry(
            String id,
            String name,
            String namespace,
            String modDisplayName,
            String searchText,
            String tagText
    ) {
    }

    private record ModEntry(
            String namespace,
            String displayName,
            int itemCount
    ) {
    }

    private final MarketModuleScreen parent;
    private final List<ShopUiUtil.ClickZone> clickZones = new ArrayList<>();
    private List<ItemEntry> allEntries;
    private List<ItemEntry> filteredEntries = List.of();
    private List<ModEntry> mods = List.of();
    private String selectedNamespace = "";
    private String query = "";
    private int itemScrollRows;
    private int modScrollRows;
    private int guiLeft;
    private int guiTop;
    private int guiWidth;
    private int guiHeight;
    private int railX;
    private int railY;
    private int railWidth;
    private int railHeight;
    private int gridX;
    private int gridY;
    private int gridWidth;
    private int gridHeight;
    private int gridContentX;
    private int gridContentY;
    private int columns;
    private int visibleRows;
    private int visibleModRows;
    private EditBox search;
    private String tooltipItemId;
    private int tooltipX;
    private int tooltipY;

    public BazaarItemBrowserScreen(MarketModuleScreen parent) {
        super(Component.translatable(
                "gui.futureshops.market.bazaar_browser.title"));
        this.parent = java.util.Objects.requireNonNull(parent, "parent");
    }

    @Override
    protected void init() {
        buildRegistryCache();
        guiWidth = Math.max(300, width - 4);
        guiHeight = Math.max(200, height - 4);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;
        int bodyTop = guiTop + 48;
        int footerTop = guiTop + guiHeight - 28;
        railX = guiLeft + 8;
        railY = bodyTop;
        railWidth = Math.min(154, Math.max(98, guiWidth / 5));
        railHeight = footerTop - bodyTop - 6;
        gridX = railX + railWidth + 8;
        gridY = bodyTop;
        gridWidth = guiLeft + guiWidth - gridX - 8;
        gridHeight = railHeight;
        gridContentX = gridX + 8;
        gridContentY = gridY + 8;
        columns = Math.max(2,
                (gridWidth - 16 + CELL_GAP) / (CELL + CELL_GAP));
        visibleRows = Math.max(1,
                (gridHeight - 16 + CELL_GAP) / (CELL + CELL_GAP));
        visibleModRows = Math.max(1,
                (railHeight - 34) / MOD_ROW_HEIGHT);

        search = new EditBox(font, guiLeft + 12, guiTop + 25,
                guiWidth - 24, 14,
                Component.translatable(
                        "gui.futureshops.market.bazaar_browser.search"));
        search.setMaxLength(128);
        search.setHint(Component.translatable(
                "gui.futureshops.market.bazaar_browser.search_hint"));
        search.setValue(query);
        search.setResponder(value -> {
            query = value.toLowerCase(Locale.ROOT);
            itemScrollRows = 0;
            recomputeFiltered();
        });
        addRenderableWidget(search);
        recomputeFiltered();
    }

    private void buildRegistryCache() {
        if (allEntries != null) {
            return;
        }
        List<ItemEntry> items = new ArrayList<>();
        Map<String, Integer> namespaceCounts = new LinkedHashMap<>();
        for (Item item : ForgeRegistries.ITEMS) {
            if (item == Items.AIR) {
                continue;
            }
            ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
            if (key == null) {
                continue;
            }
            String id = key.toString();
            String name = item.getDescription().getString();
            String namespace = key.getNamespace();
            String modName = ModList.get()
                    .getModContainerById(namespace)
                    .map(container -> container.getModInfo()
                            .getDisplayName())
                    .orElse(namespace);
            String tags = item.builtInRegistryHolder().tags()
                    .map(tag -> tag.location().toString())
                    .collect(Collectors.joining(" "));
            items.add(new ItemEntry(id, name, namespace, modName,
                    (id + ' ' + name + ' ' + modName)
                            .toLowerCase(Locale.ROOT), tags));
            namespaceCounts.merge(namespace, 1, Integer::sum);
        }
        items.sort(Comparator.comparing(ItemEntry::name,
                        String.CASE_INSENSITIVE_ORDER)
                .thenComparing(ItemEntry::id));
        allEntries = List.copyOf(items);
        mods = namespaceCounts.entrySet().stream()
                .map(entry -> new ModEntry(entry.getKey(),
                        ModList.get().getModContainerById(entry.getKey())
                                .map(container -> container.getModInfo()
                                        .getDisplayName())
                                .orElse(entry.getKey()),
                        entry.getValue()))
                .sorted(Comparator.comparing(ModEntry::displayName,
                                String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(ModEntry::namespace))
                .toList();
    }

    private void recomputeFiltered() {
        filteredEntries = allEntries.stream()
                .filter(entry -> selectedNamespace.isEmpty()
                        || selectedNamespace.equals(entry.namespace()))
                .filter(entry -> AdminItemSearchPolicy.matches(
                        entry.id(), entry.searchText(),
                        entry.modDisplayName(), entry.tagText(), query))
                .toList();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY,
                       float partialTick) {
        clickZones.clear();
        tooltipItemId = null;
        ShopUiUtil.renderDimBackdrop(graphics, width, height);
        graphics.fill(guiLeft, guiTop, guiLeft + guiWidth,
                guiTop + guiHeight, ShopColors.SURFACE_BASE);
        ShopUiUtil.drawSoftOutline(graphics, guiLeft, guiTop, guiWidth,
                guiHeight, ShopColors.BORDER_STRONG,
                ShopColors.BORDER_SUBTLE);
        graphics.fill(guiLeft, guiTop, guiLeft + guiWidth, guiTop + 2,
                ShopColors.ACCENT_PRIMARY);
        graphics.drawString(font, title, guiLeft + 12, guiTop + 8,
                ShopColors.TEXT_STRONG, false);
        String count = Component.translatable(
                "gui.futureshops.market.bazaar_browser.results",
                filteredEntries.size(), allEntries.size()).getString();
        graphics.drawString(font, count,
                guiLeft + guiWidth - 12 - font.width(count),
                guiTop + 8, ShopColors.TEXT_MUTED, false);
        renderModRail(graphics, mouseX, mouseY);
        renderItemGrid(graphics, mouseX, mouseY);
        renderFooter(graphics, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (tooltipItemId != null) {
            ShopUiUtil.renderItemTooltip(graphics, font, tooltipItemId,
                    "", tooltipX, tooltipY);
        }
    }

    private void renderModRail(GuiGraphics graphics, int mouseX,
                               int mouseY) {
        ShopUiUtil.renderCard(graphics, railX, railY, railWidth,
                railHeight);
        graphics.drawString(font,
                Component.translatable(
                        "gui.futureshops.market.bazaar_browser.mods"),
                railX + 8, railY + 8, ShopColors.TEXT_STRONG, false);
        int listY = railY + 24;
        int totalRows = mods.size() + 1;
        modScrollRows = Math.max(0, Math.min(modScrollRows,
                Math.max(0, totalRows - visibleModRows)));
        int last = Math.min(totalRows,
                modScrollRows + visibleModRows);
        for (int index = modScrollRows; index < last; index++) {
            boolean all = index == 0;
            ModEntry entry = all ? null : mods.get(index - 1);
            String namespace = all ? "" : entry.namespace();
            String label = all
                    ? Component.translatable(
                    "gui.futureshops.market.all").getString()
                    : entry.displayName();
            int amount = all ? allEntries.size() : entry.itemCount();
            int y = listY + (index - modScrollRows) * MOD_ROW_HEIGHT;
            boolean selected = namespace.equals(selectedNamespace);
            boolean hovered = mouseX >= railX + 5
                    && mouseX <= railX + railWidth - 5
                    && mouseY >= y && mouseY <= y + MOD_ROW_HEIGHT - 2;
            if (selected || hovered) {
                graphics.fill(railX + 5, y, railX + railWidth - 5,
                        y + MOD_ROW_HEIGHT - 2,
                        selected ? ShopColors.SURFACE_PRESSED
                                : ShopColors.SURFACE_OVERLAY);
            }
            if (selected) {
                graphics.fill(railX + 5, y, railX + 8,
                        y + MOD_ROW_HEIGHT - 2,
                        ShopColors.ACCENT_PRIMARY);
            }
            graphics.drawString(font, font.plainSubstrByWidth(label,
                            Math.max(8, railWidth - 42)),
                    railX + 12, y + 4,
                    selected ? ShopColors.TEXT_STRONG
                            : ShopColors.TEXT_MUTED, false);
            String number = Integer.toString(amount);
            graphics.drawString(font, number,
                    railX + railWidth - 10 - font.width(number), y + 4,
                    ShopColors.TEXT_FAINT, false);
            ShopUiUtil.ClickZone zone = new ShopUiUtil.ClickZone(
                    railX + 5, y, railWidth - 10,
                    MOD_ROW_HEIGHT - 2,
                    () -> selectNamespace(namespace), true);
            clickZones.add(zone);
        }
        ShopUiUtil.renderScrollIndicators(graphics, font, railX, listY,
                railWidth, railHeight - 24, modScrollRows,
                visibleModRows, totalRows);
    }

    private void selectNamespace(String namespace) {
        selectedNamespace = namespace;
        itemScrollRows = 0;
        recomputeFiltered();
    }

    private void renderItemGrid(GuiGraphics graphics, int mouseX,
                                int mouseY) {
        ShopUiUtil.renderCard(graphics, gridX, gridY, gridWidth,
                gridHeight);
        graphics.fill(gridX, gridY, gridX + gridWidth, gridY + 2,
                ShopColors.ACCENT_PRIMARY);
        if (filteredEntries.isEmpty()) {
            graphics.drawCenteredString(font,
                    Component.translatable(
                            "gui.futureshops.market.bazaar_browser.no_results"),
                    gridX + gridWidth / 2,
                    gridY + gridHeight / 2 - 4,
                    ShopColors.TEXT_FAINT);
            return;
        }
        int totalRows = (filteredEntries.size() + columns - 1) / columns;
        itemScrollRows = Math.max(0, Math.min(itemScrollRows,
                Math.max(0, totalRows - visibleRows)));
        int first = itemScrollRows * columns;
        int last = Math.min(filteredEntries.size(),
                (itemScrollRows + visibleRows) * columns);
        for (int index = first; index < last; index++) {
            int row = index / columns - itemScrollRows;
            int column = index % columns;
            int x = gridContentX + column * (CELL + CELL_GAP);
            int y = gridContentY + row * (CELL + CELL_GAP);
            ItemEntry entry = filteredEntries.get(index);
            boolean hovered = mouseX >= x && mouseX <= x + CELL
                    && mouseY >= y && mouseY <= y + CELL;
            ShopUiUtil.renderPanel(graphics, x, y, CELL, CELL,
                    hovered ? ShopColors.SURFACE_OVERLAY
                            : ShopColors.SURFACE_RAISED,
                    hovered ? ShopColors.BORDER_GLOW
                            : ShopColors.BORDER_MUTED);
            ShopUiUtil.renderItemIconWithNbt(graphics, font, entry.id(),
                    "", x + 6, y + 6);
            clickZones.add(new ShopUiUtil.ClickZone(x, y, CELL, CELL,
                    () -> selectItem(entry.id()), true));
            if (hovered) {
                tooltipItemId = entry.id();
                tooltipX = mouseX;
                tooltipY = mouseY;
            }
        }
        ShopUiUtil.renderScrollIndicators(graphics, font, gridX, gridY,
                gridWidth, gridHeight, itemScrollRows, visibleRows,
                totalRows);
    }

    private void renderFooter(GuiGraphics graphics, int mouseX,
                              int mouseY) {
        int y = guiTop + guiHeight - 26;
        graphics.fill(guiLeft, y, guiLeft + guiWidth,
                guiTop + guiHeight, ShopColors.SURFACE_RAISED);
        graphics.fill(guiLeft, y, guiLeft + guiWidth, y + 1,
                ShopColors.BORDER_SUBTLE);
        String hint = Component.translatable(
                "gui.futureshops.market.bazaar_browser.hint")
                .getString();
        graphics.drawString(font, font.plainSubstrByWidth(hint,
                        Math.max(8, guiWidth - 92)),
                guiLeft + 10, y + 9, ShopColors.TEXT_MUTED, false);
        ShopUiUtil.button(graphics, font, clickZones, mouseX, mouseY,
                guiLeft + guiWidth - 72, y + 5, 62, 16,
                Component.translatable("gui.futureshops.market.back"),
                ShopUiUtil.ButtonStyle.SECONDARY, true, this::onClose);
    }

    private void selectItem(String itemId) {
        parent.selectBazaarRegistryItem(itemId);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY,
                                int button) {
        if (button == 0
                && ShopUiUtil.dispatchClicks(clickZones, mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double delta) {
        if (mouseX >= railX && mouseX <= railX + railWidth
                && mouseY >= railY && mouseY <= railY + railHeight) {
            modScrollRows = Math.max(0,
                    modScrollRows - (int) Math.signum(delta));
            return true;
        }
        if (mouseX >= gridX && mouseX <= gridX + gridWidth
                && mouseY >= gridY && mouseY <= gridY + gridHeight) {
            itemScrollRows = Math.max(0,
                    itemScrollRows - (int) Math.signum(delta));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        parent.returnFromBazaarItemBrowser();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
