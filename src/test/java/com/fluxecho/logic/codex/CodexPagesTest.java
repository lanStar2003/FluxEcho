package com.fluxecho.logic.codex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fluxecho.logic.ResearchTree;
import com.fluxecho.logic.voyage.ScoreBridge;

/**
 * The Codex's narrative pages in 0.17 (narrative §1.7, §9.2 N1): volumes 0 to III open from events and states that
 * 0.16.0 already has, a cabin log for each building 0.16.0 ships (rows 01 to 17), and the texts behind them: both
 * languages have every key the pages build at run time (LangTest cannot see those), and the Chinese keeps to the
 * narrative's sizes (a cabin log at most 180 characters, a star note at most 60, one line of setting and one of
 * effect). The renames of narrative §2.4 and the items' one line of setting (§1.7.5) are checked too.
 */
class CodexPagesTest {

    private static Map<String, String> lang(String file) throws IOException {
        Map<String, String> m = new HashMap<>();
        for (String line : Files.readAllLines(
            Paths.get("src", "main", "resources", "assets", "fluxecho", "lang", file),
            StandardCharsets.UTF_8)) {
            int eq = line.indexOf('=');
            if (eq > 0 && !line.startsWith("#")) m.put(line.substring(0, eq), line.substring(eq + 1));
        }
        return m;
    }

    @Test
    void theVolumesOpenFromWhatTheTeamHasDone() {
        assertEquals(4, CodexPages.VOLUMES, "volumes 0 to III in 0.17 (narrative §9.2 N1)");
        assertEquals(CodexPages.Gate.OPEN, CodexPages.gate(0), "volume 0: there is no capsule yet, it is always open");
        assertEquals(CodexPages.Gate.OPEN, CodexPages.gate(1), "volume I: every player starts aground");
        assertEquals(CodexPages.Gate.FIRST_RECORD, CodexPages.gate(2), "volume II: the first entry of the codex");
        assertEquals(CodexPages.Gate.ANCHOR, CodexPages.gate(3), "volume III: the anchor, the nexus's first forming");

        assertTrue(CodexPages.open(0, 0, Collections.emptySet()));
        assertTrue(CodexPages.open(1, 0, Collections.emptySet()));
        assertFalse(CodexPages.open(2, 0, Collections.emptySet()));
        assertTrue(CodexPages.open(2, 1, Collections.emptySet()));
        assertFalse(CodexPages.open(3, 500, new HashSet<>(Arrays.asList("library", "inner_ring"))));
        assertTrue(CodexPages.open(3, 0, Collections.singleton(ResearchTree.ANCHOR)));
        assertFalse(CodexPages.open(4, 999, Collections.singleton(ResearchTree.ANCHOR)), "no volume IV yet");
        assertFalse(CodexPages.open(-1, 999, Collections.singleton(ResearchTree.ANCHOR)));
    }

    @Test
    void theCabinLogsAreTheShippedBuildingsInRowOrder() {
        List<String> want = new ArrayList<>();
        for (int row = 1; row <= 17; row++) want.add(ScoreBridge.buildingKey(row));
        assertEquals(want, CodexPages.HALLS);
        assertEquals("codex.hall.library", CodexPages.hallKey("library"));
        assertEquals("tile.fluxecho.nexus.name", CodexPages.nameKey("nexus"));
        assertEquals("fluxecho.module.root", CodexPages.nameKey("root"));
    }

    @Test
    void everyPageHasItsTextInBothLanguages() throws IOException {
        for (String file : new String[] { "zh_CN.lang", "en_US.lang" }) {
            Map<String, String> l = lang(file);
            for (int v = 0; v < CodexPages.VOLUMES; v++) {
                assertNotNull(l.get("fluxecho." + CodexPages.titleKey(v)), file + " volume " + v + " title");
                assertNotNull(l.get("fluxecho." + CodexPages.bodyKey(v)), file + " volume " + v);
                assertEquals(
                    CodexPages.gate(v) != CodexPages.Gate.OPEN,
                    l.containsKey("fluxecho." + CodexPages.whenKey(v)),
                    file + ": a volume that opens later says when");
            }
            for (String h : CodexPages.HALLS) {
                String text = l.get("fluxecho." + CodexPages.hallKey(h));
                assertNotNull(text, file + " cabin log of " + h);
                assertTrue(text.contains("\\n"), file + " " + h + ": a heading, then the record");
                assertNotNull(l.get(CodexPages.nameKey(h)), file + " name of " + h);
            }
            for (String k : new String[] { "codex.tab.records", "codex.tab.volumes", "codex.tab.halls",
                "codex.halls.count", "codex.halls.locked" }) assertNotNull(l.get("fluxecho." + k), file + " " + k);
        }
    }

    @Test
    void theChineseKeepsToTheNarrativesSizes() throws IOException {
        Map<String, String> zh = lang("zh_CN.lang");
        for (String h : CodexPages.HALLS) {
            String text = zh.get("fluxecho." + CodexPages.hallKey(h));
            assertTrue(CodexPages.shown(text) <= 180, h + ": a cabin log is at most 180 characters (narrative §1.7.1)");
            String heading = text.substring(0, text.indexOf("\\n"));
            assertTrue(heading.matches("相位[一二三四五]年 · .+"), h + ": the city's heading, " + heading);
        }
        for (ResearchTree.Node n : ResearchTree.all()) {
            String note = zh.get("fluxecho.research.node." + n.id + ".desc");
            assertTrue(
                CodexPages.shown(note) <= 60,
                n.id + ": a star note is at most 60 characters (narrative §1.7.1)");
            assertEquals(2, note.split("｜", -1).length, n.id + ": one line of setting｜one line of effect, " + note);
            String en = lang("en_US.lang").get("fluxecho.research.node." + n.id + ".desc");
            assertEquals(2, en.split(" \\| ", -1).length, n.id + ": setting | effect, " + en);
        }
        assertEquals(
            29,
            ResearchTree.all()
                .size(),
            "the 29 shipped nodes all have their note");
    }

    /**
     * The star map formats a note ({@code EchoText.t}, {@code String.format} underneath), and Minecraft shows a text
     * that does not format as "Format error: ..."; 0.16.0's notes of the compute calibration, the throttling and the
     * grit enrichment ("50%") did. A percent sign in a note is written "%%".
     */
    @Test
    void theStarNotesFormatAsTheStarMapShowsThem() throws IOException {
        for (String file : new String[] { "zh_CN.lang", "en_US.lang" }) {
            Map<String, String> l = lang(file);
            for (ResearchTree.Node n : ResearchTree.all()) {
                String note = l.get("fluxecho.research.node." + n.id + ".desc");
                String shown = String.format(note);
                assertFalse(shown.contains("%%"), file + " " + n.id);
            }
        }
        assertEquals(
            "让长庚想得快一点。｜中枢的算力提高 50%。",
            String.format(lang("zh_CN.lang").get("fluxecho.research.node.compute_1.desc")));
    }

    @Test
    void theRenamesAndTheItemsLineOfSetting() throws IOException {
        Map<String, String> zh = lang("zh_CN.lang");
        // narrative §2.4: only the shown names change, the keys stay
        assertEquals("龙骨", zh.get("fluxecho.research.node.inner_ring"));
        assertEquals("外舷", zh.get("fluxecho.research.node.outer_ring"));
        assertEquals("立桅", zh.get("fluxecho.research.node.sky_ring"));
        assertEquals("回响档案馆", zh.get("fluxecho.research.node.library"));
        assertEquals("回响档案馆", zh.get("fluxecho.module.library"));
        for (Map.Entry<String, String> e : zh.entrySet()) assertFalse(
            e.getValue()
                .contains("回响书库"),
            e.getKey() + " still says 回响书库");
        // narrative §1.7.5: the first line of the tooltip is the setting
        String[][] first = { { "flux_grit", "中枢沉在海底的灰" }, { "flux_crystal", "船的灰重新长成的骨" },
            { "echo_crystal", "这个世界替你记住的一件事" }, { "phase_alloy", "船壳" }, { "resonant_glass", "舷窗" },
            { "phase_alloy_wire", "还没学会记事的神经" }, { "memory_filament", "神经：记得流过它的东西" },
            { "manifest_matter", "第一次学会了船的形状" }, { "anti_gravity_lattice", "第一次学会了浮起来" }, { "codex", "你的航海日志" } };
        for (String[] f : first) {
            String tip = zh.get("fluxecho." + f[0] + ".tip");
            assertTrue(tip.startsWith("§3") && tip.split("\\\\n")[0].contains(f[1]), f[0] + ": " + tip);
        }
    }

    @Test
    void shownCountsWhatThePlayerReads() {
        assertEquals(4, CodexPages.shown("§b长庚§r\\n来了"));
        assertEquals(0, CodexPages.shown(""));
        assertEquals(0, CodexPages.shown(null));
        assertEquals(
            Arrays.asList("相位一年 · 档案馆", "日志舱也。", "", "末行"),
            CodexPages.paragraphs("相位一年 · 档案馆\\n日志舱也。\\n\\n末行"));
    }
}
