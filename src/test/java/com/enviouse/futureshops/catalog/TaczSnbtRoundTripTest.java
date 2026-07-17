package com.enviouse.futureshops.catalog;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SNBT integrity for TacZ gun tags through the admin.json round trip.
 *
 * <p>Capture path: {@code held.getTag().toString()} (ShopAdminCommand:523, AdminShopWizard:77)
 * → {@code AdminShopConfigWriter.buildItemEntry} writes it as the {@code "nbt"} string via Gson
 * → {@code ShopDefinitionLoader.parseJson} reads it back → client {@code TagParser.parseTag}
 * (ShopUiUtil.buildItemStack / hasNonDefaultNbt) and server TagParser (Buy/Sell services).
 *
 * <p>These tests drive the REAL production write/read code (buildItemEntry + parseJson) with a
 * realistic TacZ modern_kinetic_gun tag, including attachment compounds that hold serialized
 * ItemStacks whose display names are JSON-ish quoted text — the worst case for quote escaping.
 */
public class TaczSnbtRoundTripTest {

    /** Same config as AdminShopConfigWriter.PRETTY. */
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    /** Default Gson (HTML-escaping ON) to prove escaping variants are also lossless. */
    private static final Gson DEFAULT = new Gson();

    /**
     * Builds a realistic TacZ gun tag the way a live capture would see it. Hand-written SNBT for
     * the structural part; pathological string values added programmatically so the exact escape
     * handling of StringTag.quoteAndEscape is exercised rather than our own hand-escaping.
     */
    private static CompoundTag realisticTaczGunTag() throws Exception {
        CompoundTag tag = TagParser.parseTag(
                "{GunId:\"tacz:ak47\",GunFireMode:\"AUTO\",GunCurrentAmmoCount:30,HasBulletInBarrel:1b,"
                        + "AttachmentSCOPE:{id:\"tacz:attachment\",Count:1b,tag:{AttachmentId:\"tacz:scope_acog_ta31\"}},"
                        + "AttachmentEXTENDED_MAG:{id:\"tacz:attachment\",Count:1b,tag:{AttachmentId:\"tacz:extended_mag_3\"}}}");
        // Attachment with a JSON text-component display name — SNBT must single-quote it.
        CompoundTag grip = TagParser.parseTag(
                "{id:\"tacz:attachment\",Count:1b,tag:{AttachmentId:\"tacz:grip_vertical\"}}");
        grip.getCompound("tag").getCompound("display"); // no-op read; build display below
        CompoundTag display = new CompoundTag();
        display.putString("Name", "{\"text\":\"Envy's Grip\",\"color\":\"gold\"}");
        grip.getCompound("tag").put("display", display);
        tag.put("AttachmentGRIP", grip);
        // Gun-level display name: contains BOTH quote kinds and a backslash.
        CompoundTag gunDisplay = new CompoundTag();
        gunDisplay.putString("Name", "{\"text\":\"He said \\\"don't\\\" \\\\ AK\"}");
        tag.put("display", gunDisplay);
        // Number-like string must stay a string after round trip.
        tag.putString("GunOwnerHash", "12345");
        return tag;
    }

    @Test
    void snbtToStringIsAFixpointAndParsesBackEqual() throws Exception {
        CompoundTag original = realisticTaczGunTag();
        String captured = original.toString(); // exactly what ShopAdminCommand:523 stores
        CompoundTag reparsed = TagParser.parseTag(captured);
        assertEquals(original, reparsed, "TagParser.parseTag(tag.toString()) must equal the tag");
        assertEquals(captured, reparsed.toString(), "toString must be a fixpoint (stable across cycles)");
    }

    @Test
    void fullAdminJsonWriteReadRoundTripPreservesTaczTag() throws Exception {
        CompoundTag original = realisticTaczGunTag();
        String captured = original.toString();

        // ── real write path: buildItemEntry + the writer's exact Gson config ──
        AdminShopItemSpec spec = new AdminShopItemSpec(
                "ak47_1", "tacz:modern_kinetic_gun", "Golden AK-47",
                150000L, 50000L, -1, 0, "guns", captured, 0L);
        JsonObject entry = AdminShopConfigWriter.buildItemEntry(spec);
        JsonObject root = new JsonObject();
        root.addProperty("shopId", "default");
        root.addProperty("displayName", "Server Shop");
        root.add("categories", new JsonArray());
        JsonArray items = new JsonArray();
        items.add(entry);
        root.add("items", items);
        String fileContent = PRETTY.toJson(root);

        // ── real read path: ShopDefinitionLoader.parseJson ──
        ShopDefinition def = ShopDefinitionLoader.parseJson(fileContent, "admin.json");
        assertEquals(1, def.items().size());
        ItemDef item = def.items().get(0);
        assertEquals(captured, item.nbtJson(), "SNBT must survive the admin.json write+read byte-for-byte");

        // ── consumer parse (ShopUiUtil.buildItemStack / ShopBuyService both do exactly this) ──
        CompoundTag consumed = TagParser.parseTag(item.nbtJson());
        assertEquals(original, consumed, "tag after full file round trip must equal the captured tag");
        assertEquals("tacz:ak47", consumed.getString("GunId"));
        assertEquals("tacz:scope_acog_ta31",
                consumed.getCompound("AttachmentSCOPE").getCompound("tag").getString("AttachmentId"));
        assertEquals("{\"text\":\"Envy's Grip\",\"color\":\"gold\"}",
                consumed.getCompound("AttachmentGRIP").getCompound("tag").getCompound("display").getString("Name"));
        assertTrue(consumed.get("GunOwnerHash") instanceof net.minecraft.nbt.StringTag,
                "number-like string must stay a StringTag");
    }

    @Test
    void htmlEscapingGsonVariantIsAlsoLossless() throws Exception {
        // Guard against someone dropping disableHtmlEscaping(): default Gson ’escapes’
        // ' < > = & to unicode escapes, which must still unescape identically on read.
        CompoundTag original = realisticTaczGunTag();
        String captured = original.toString();
        JsonObject o = new JsonObject();
        o.addProperty("nbt", captured);
        String written = DEFAULT.toJson(o);
        String readBack = JsonParser.parseString(written).getAsJsonObject().get("nbt").getAsString();
        assertEquals(captured, readBack, "Gson HTML escaping must be lossless for SNBT payloads");
        assertEquals(original, TagParser.parseTag(readBack));
    }

    @Test
    void rawNewlineInsideStringTagSurvives() throws Exception {
        // StringTag.quoteAndEscape does NOT escape control chars — a raw \n lands in the SNBT.
        // Gson escapes it as \n in the file and unescapes on read; TagParser reads quoted
        // strings across newlines. Verify the whole chain.
        CompoundTag tag = new CompoundTag();
        tag.putString("GunId", "tacz:ak47");
        CompoundTag display = new CompoundTag();
        display.putString("Name", "line one\nline two");
        tag.put("display", display);
        String captured = tag.toString();

        JsonObject o = new JsonObject();
        o.addProperty("nbt", captured);
        String written = PRETTY.toJson(o);
        String readBack = JsonParser.parseString(written).getAsJsonObject().get("nbt").getAsString();
        assertEquals(captured, readBack);
        assertEquals(tag, TagParser.parseTag(readBack));
    }
}
