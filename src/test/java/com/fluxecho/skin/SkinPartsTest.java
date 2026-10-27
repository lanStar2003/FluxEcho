package com.fluxecho.skin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.fluxecho.campus.PartBlocks;
import com.fluxecho.logic.LiveParts;
import com.fluxecho.logic.Parts;
import com.fluxecho.logic.shape.Family;
import com.fluxecho.logic.shape.FormCodes;
import com.fluxecho.logic.shape.SkinBlocks;

/**
 * The new families' blocks in the part table and as items (0.17.0, style FINAL §3.8.1; ids.md §1.4, §3.1): every code
 * of the batch maps to its family's whole-block or shape-block id at the shape number's meta and back; any other meta
 * of theirs is no part (a foreign block on a site); the live materials are placed at their metas and never read back
 * as ours. Each item is named after its family and shape in both languages, and the creative tab lists the coded
 * shapes only.
 */
class SkinPartsTest {

    @BeforeAll
    static void setUp() {
        SkinModule.create();
    }

    @Test
    void everyCodeGoesBothWays() {
        int n = 0;
        for (int code = 0; code < 256; code++) {
            Family f = FormCodes.family(code);
            if (f == null) continue;
            int meta = FormCodes.number(code);
            BlockSkin b = (BlockSkin) PartBlocks.block(code);
            assertSame(SkinModule.block(f, SkinBlocks.inWhole(f, meta)), b, "code " + code);
            assertEquals(meta, PartBlocks.meta(code), "code " + code);
            assertEquals(code, PartBlocks.code(b, meta), "code " + code + " back");
            n++;
        }
        assertEquals(36, n);
        // the other metas of the eight blocks are no part
        int coded = 0;
        for (BlockSkin b : SkinModule.all()) for (int meta = 0; meta < 16; meta++) {
            int code = PartBlocks.code(b, meta);
            if (code < 0) continue;
            coded++;
            assertEquals(b.family, FormCodes.family(code));
            assertEquals(meta, FormCodes.number(code));
        }
        assertEquals(36, coded, "each code from one block and meta");
        assertEquals(-1, PartBlocks.code(SkinModule.hull, 2), "the round lives in the shape id");
        assertEquals(-1, PartBlocks.code(SkinModule.hullForm, 8), "the hull's window has no code yet");
        assertEquals(98, PartBlocks.code(SkinModule.hullForm, 2));
    }

    @Test
    void liveMaterialsArePlacedButNeverOurs() {
        for (int code = Parts.BIRCH; code <= Parts.FLOWER; code++)
            assertEquals(LiveParts.meta(code), PartBlocks.meta(code), "code " + code);
        for (BlockSkin b : SkinModule.all())
            for (int meta = 0; meta < 16; meta++) assertTrue(!Parts.isLive(PartBlocks.code(b, meta)));
        assertEquals(-1, PartBlocks.code(null, 0));
    }

    @Test
    void theCreativeTabListsTheCodedShapes() {
        Item item = new Item();
        int listed = 0;
        for (BlockSkin b : SkinModule.all()) {
            List<ItemStack> l = new ArrayList<>();
            b.getSubBlocks(item, null, l);
            for (ItemStack s : l)
                assertTrue(b.code(s.getItemDamage()) >= 0, b.registryName() + " " + s.getItemDamage());
            listed += l.size();
        }
        assertEquals(36, listed);
        Set<String> names = new HashSet<>();
        for (BlockSkin b : SkinModule.all()) assertTrue(names.add(b.registryName()));
        assertEquals(
            new HashSet<>(
                java.util.Arrays.asList(
                    "hull",
                    "hull_form",
                    "footing",
                    "footing_form",
                    "skin_garden",
                    "skin_garden_form",
                    "skin_script",
                    "skin_script_form")),
            names,
            "the registry names of ids.md §3.1");
    }

    /**
     * Every shape number of every family has its item name in both languages (also the uncoded ones a command can
     * give), the same whichever id holds it, and every material its tip.
     */
    @Test
    void everyItemIsNamedInBothLanguages() throws IOException {
        Map<String, Map<String, String>> langs = new HashMap<>();
        for (String lang : new String[] { "en_US", "zh_CN" }) {
            Map<String, String> m = new HashMap<>();
            for (String line : Files.readAllLines(
                Paths.get("src/main/resources/assets/fluxecho/lang/" + lang + ".lang"),
                StandardCharsets.UTF_8)) {
                int eq = line.indexOf('=');
                if (eq > 0 && !line.startsWith("#")) m.put(line.substring(0, eq), line.substring(eq + 1));
            }
            langs.put(lang, m);
        }
        Item item = new Item();
        Set<String> names = new HashSet<>();
        for (BlockSkin b : SkinModule.all()) {
            ItemBlockSkin ib = new ItemBlockSkin(b);
            for (int meta = 0; meta < 16; meta++) {
                String key = ib.getUnlocalizedName(new ItemStack(item, 1, meta)) + ".name";
                for (Map<String, String> m : langs.values()) assertTrue(m.containsKey(key), key);
                if (b.whole) names.add(key);
                String tip = "fluxecho.skin." + SkinBlocks.material(b.family, meta) + ".tip";
                for (Map<String, String> m : langs.values()) assertTrue(m.containsKey(tip), tip);
            }
        }
        assertEquals(64, names.size(), "one name a shape number of each family");
        assertEquals(
            "舟壳「圆」",
            langs.get("zh_CN")
                .get("tile.fluxecho.hull.round.name"));
        assertEquals(
            "宣「灯笼板」",
            langs.get("zh_CN")
                .get("tile.fluxecho.skin_script.paper_lantern.name"));
        assertEquals(
            "苔铜「铜框窗」",
            langs.get("zh_CN")
                .get("tile.fluxecho.skin_garden.moss_window.name"));
    }
}
