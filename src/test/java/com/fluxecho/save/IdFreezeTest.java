package com.fluxecho.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.fluxecho.campus.ModuleSpecs;

/**
 * The first of the four guards of old saves (refactor §5.1 ①): every permanent ID 0.16.0 shipped, exported from the
 * 0.16.0 code itself into {@code ids/0.16.0.json}, is still there in the current code with the same value. New IDs are
 * fine; one gone, renumbered or renamed is a red build, with every such ID named at once. "IDs are only ever added"
 * stops being a rule to remember and becomes a test.
 * <p>
 * Values are compared as they are, except where the ID is a promise about parsing rather than about a table: a module
 * job key of the snapshot must still parse to its module and site (whether or not that site is still offered: an old
 * campus keeps its sites), and a quest key's id must still be shipped. The unit tables have their own test with prefix
 * rules ({@link UnitFreezeTest}); the current export is written to {@code build/tmp/ids-current.json} to look at.
 * <p>
 * Tagged {@code slow}: the export reads every main source and loads most of the mod's classes (about four seconds).
 */
@Tag("slow")
class IdFreezeTest {

    static final Path SNAPSHOT = Paths.get("src/test/resources/ids/0.16.0.json");
    private static Map<String, Map<String, String>> frozen, now;

    @BeforeAll
    static void export() throws IOException {
        now = IdExport.export();
        IdExport.write(Paths.get("build/tmp/ids-current.json"), "current", "", now);
        frozen = IdExport.read(SNAPSHOT);
    }

    /**
     * The snapshot is the whole of 0.16.0 (a snapshot cut short would freeze nothing): its categories and how many IDs
     * each holds, which also match the ledger where it counts them (29 research ids, 83 quests in 10 lines, the 23
     * tile entities of FluxEcho and FluxLite's 2, 101 config keys of FluxEcho, the part codes, 9 saved data names).
     */
    @Test
    void theSnapshotIsWhole() {
        assertEquals(Arrays.asList(IdExport.CATEGORIES), new ArrayList<>(frozen.keySet()), "the categories");
        Object[][] counts = { { "block", 16 }, { "item", 19 }, { "tile_entity", 25 }, { "meta", 53 }, { "part", 61 },
            { "site", 49 }, { "plan_key", 72 }, { "step_kind", 5 }, { "plan_stage", 22 }, { "job_state", 15 },
            { "module_status", 6 }, { "net", 45 }, { "config", 142 }, { "world_data", 9 }, { "unit", 509 },
            { "shape", 52 }, { "research", 29 }, { "manifest", 22 }, { "quest_id", 93 }, { "quest_key", 93 },
            { "ore", 9 }, { "lang_en_US", 1509 }, { "lang_zh_CN", 1509 }, { "gt_machine", 15 }, { "codex_category", 8 },
            { "gate", 15 } };
        for (Object[] c : counts) assertEquals(
            c[1],
            frozen.get((String) c[0])
                .size(),
            "IDs of " + c[0]);
        int config016 = 0;
        for (String k : frozen.get("config")
            .keySet()) if (k.startsWith("fluxecho:")) config016++;
        assertEquals(101, config016, "FluxEcho's config keys (ConfigTest's 0.16.0 file)");
        assertEquals(
            Arrays.asList(
                "fluxecho_aspects",
                "fluxecho_campus",
                "fluxecho_gates",
                "fluxecho_ledger",
                "fluxecho_recycle",
                "fluxecho_research",
                "fluxecho_vault",
                "fluxlite_registry",
                "fluxlite_steam"),
            new ArrayList<>(
                frozen.get("world_data")
                    .keySet()),
            "the saved data names");
    }

    @Test
    void everyIdOf016IsStillThereWithItsValue() {
        List<String> wrong = new ArrayList<>();
        for (Map.Entry<String, Map<String, String>> c : frozen.entrySet()) {
            String cat = c.getKey();
            if (cat.equals("unit")) continue;
            Map<String, String> cur = now.get(cat);
            for (Map.Entry<String, String> e : c.getValue()
                .entrySet()) {
                String key = e.getKey(), was = e.getValue(), is = current(cat, key, cur);
                if (is == null) wrong.add(cat + " " + key + ": gone (was \"" + was + "\")");
                else if (!is.equals(was)) wrong.add(cat + " " + key + ": \"" + was + "\" is now \"" + is + "\"");
            }
        }
        if (!wrong.isEmpty())
            fail(wrong.size() + " IDs of 0.16.0 changed (IDs are only ever added):\n  " + String.join("\n  ", wrong));
    }

    /** What the current code has for an ID of the snapshot, or null when it has none. */
    private static String current(String cat, String key, Map<String, String> cur) {
        if (cat.equals("plan_key") && key.startsWith("module:"))
            return ModuleSpecs.moduleOf(key) == null ? null : ModuleSpecs.moduleOf(key) + "@" + ModuleSpecs.siteOf(key);
        if (cat.equals("quest_key")) {
            String id = frozen.get(cat)
                .get(key);
            return now.get("quest_id")
                .containsKey(id) ? id : null;
        }
        return cur == null ? null : cur.get(key);
    }
}
