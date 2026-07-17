package com.enviouse.futureshops;

import com.enviouse.futureshops.catalog.ItemDef;
import com.enviouse.futureshops.data.CatalogItem;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the TacZ-gun (and any NBT-model item) icon fix in the GLOBAL admin shop against
 * regressions. BEWLR items resolve their model from stack NBT, so a listing icon rendered from a
 * bare {@code ItemStack} shows the purple/black missing-texture quad. Two things must hold:
 *
 * <ol>
 *   <li>The data path preserves NBT: {@code ItemDef.toCatalogItem(...)} must forward {@code nbtJson}
 *       onto the wire {@link CatalogItem} (the field-order/plumbing guard).</li>
 *   <li>The render path stays NBT-aware: every global-shop screen that draws a listing icon must go
 *       through an NBT-aware helper ({@code renderItemIconWithNbt} / {@code renderLargeItemPreviewWithNbt}),
 *       never the bare {@code renderItemIcon} — the exact break that reverted the fix during a rewrite.</li>
 * </ol>
 */
public class AdminShopIconNbtTest {

    private static final Path SCREEN_ROOT =
            Path.of("src/main/java/com/enviouse/futureshops/client/screen");

    @Test
    void toCatalogItemPreservesNbtJson() {
        String snbt = "{GunId:\"tacz:ak47\",GunFireMode:\"AUTO\"}";
        ItemDef def = new ItemDef(
                "ak47_1", "tacz:modern_kinetic_gun", "AK-47",
                3000L, 0L, 5, false, "weapons", 0, snbt, 0L);

        CatalogItem out = def.toCatalogItem(5, false, 0L, false);

        assertEquals(snbt, out.nbtJson(),
                "ItemDef.toCatalogItem must forward nbtJson to the wire — a dropped tag renders "
                        + "TacZ guns as a missing texture in /shop.");
        assertEquals("tacz:modern_kinetic_gun", out.itemId());
    }

    @Test
    void globalShopScreensRenderIconsNbtAware() throws Exception {
        // Each of these screens draws at least one listing icon; it MUST use the NBT-aware helper.
        for (String screen : new String[]{
                "ShopMainScreen", "ItemDetailScreen", "CartScreen", "BarterScreen"}) {
            String src = Files.readString(SCREEN_ROOT.resolve(screen + ".java"));
            boolean nbtAware = src.contains("renderItemIconWithNbt")
                    || src.contains("renderLargeItemPreviewWithNbt");
            assertTrue(nbtAware,
                    screen + " must render listing icons through an NBT-aware helper "
                            + "(renderItemIconWithNbt / renderLargeItemPreviewWithNbt); a bare "
                            + "renderItemIcon shows BEWLR items like TacZ guns as a missing texture.");
        }
    }
}
