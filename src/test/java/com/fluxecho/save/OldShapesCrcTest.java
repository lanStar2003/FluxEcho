package com.fluxecho.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * The fourth guard of old saves (refactor §5.1 ④): the checksums of every shape and every plan of 0.16.0, written down
 * here as the 0.16.0 code gave them ({@link ShapeCrc}, run by {@code save.gen.GoldenSaveGen} in a work tree of the
 * tag). A shape keeps its name, its blueprint (every layer, every character, the controller's cell), the parts its
 * characters are built as and its unit table; a plan keeps every step (cell, part, stage, group, kind) and every
 * column it clears, at a fixed campus. So an old building still forms where it stands, and an old job resumes on the
 * very plan it started with (which is why {@code BuildJob.V} never has to move).
 * <p>
 * New shapes and new plans are fine; an old one changed is a red build. What a building looks like once formed (what
 * dissolves, its light and colours) is not summed. Should a shape ever truly have to change, that is a new shape name
 * and an old-shape mode (refactor §5.2), never an edit of this table.
 */
class OldShapesCrcTest {

    /** {@code <tile id>:<shape>}: blueprint, parts, units (hex CRC-32) — (0.16.0, 1cc50f2). */
    static final String[][] SHAPES = { { "fluxecho:arcane_court:main", "1f130d90", "97e31e5", "32f5efd8" },
        { "fluxecho:blood_covenant:main", "4e17759e", "ab6d2f3b", "f1d88978" },
        { "fluxecho:dome_generator:moored", "f7b9be82", "6bd0c32", "b3b64fdb" },
        { "fluxecho:dome_generator:floating", "f57abf74", "2d113b26", "b3b64fdb" },
        { "fluxecho:dome_generator:moored_36", "2d238fd6", "83136f45", "b3b64fdb" },
        { "fluxecho:dome_generator:moored_32", "6fac1b73", "4e6957b2", "b3b64fdb" },
        { "fluxecho:dome_generator:moored_28", "bb74ea13", "f927432c", "b3b64fdb" },
        { "fluxecho:droplet_hangar:main", "8e7c7a53", "820399a1", "732df1d5" },
        { "fluxecho:dual_foil_vault:main", "f67787dc", "559e81", "1b7708d4" },
        { "fluxecho:eco_dome:main", "2966e300", "ccd829ee", "f26b7fb0" },
        { "fluxecho:holo_table:main", "dc1b82e0", "c1a072db", "7b9852b4" },
        { "fluxecho:hunting_ground:main", "105b9718", "326fffc9", "82d97388" },
        { "fluxecho:library:main", "b8a73ba9", "0", "86fffd8b" },
        { "fluxecho:library:hall", "be5232c2", "0", "e2cf0092" },
        { "fluxecho:mana_fountain:main", "1696d84d", "eabfb6c7", "41f8f97a" },
        { "fluxecho:nexus:phase1", "74acc4f8", "0", "0" }, { "fluxecho:nexus:phase2", "fe51a3d1", "0", "0" },
        { "fluxecho:nexus:phase3", "23c2232", "0", "0" }, { "fluxecho:nexus:phase4", "64968422", "0", "0" },
        { "fluxecho:nexus:phase5", "cfe4d263", "0", "0" },
        { "fluxecho:observatory:main", "b9b084c5", "58dba4ef", "1fbc24d9" },
        { "fluxecho:resonance_gate:main", "cfecd4b9", "e6de89b0", "4b5700d9" },
        { "fluxecho:root:well", "a9d62cab", "d9498d41", "3dbe6306" },
        { "fluxecho:root:well_20", "3718fb86", "e47b30c2", "3dbe6306" },
        { "fluxecho:root:well_14", "e0cccf04", "c46025e9", "3dbe6306" },
        { "fluxecho:root:well_8", "21c96f7f", "e269a224", "3dbe6306" },
        { "fluxecho:root:root", "9cb42832", "48bdd5ca", "8d47d52d" },
        { "fluxecho:root:root_7", "c3491439", "e1121f08", "b50c84bd" },
        { "fluxecho:root:root_in", "45f2e679", "8af12426", "44c30e7d" },
        { "fluxecho:root:root_in_20", "7cc092dd", "ba857d02", "44c30e7d" },
        { "fluxecho:root:root_in_14", "949d2869", "9a53d5c3", "44c30e7d" },
        { "fluxecho:root:root_in_8", "78700e39", "cd1d4233", "44c30e7d" },
        { "fluxecho:sky_sails:main", "4baf6e6e", "338d06e5", "40311259" },
        { "fluxecho:sophon_array:main", "a54923db", "ab22b2a7", "159b67e7" },
        { "fluxecho:weather_station:main", "6ea3a6b6", "bcabaf50", "f9c1cb8a" }, };

    /** {@code plan <key>[ <form>]}: steps, clearing columns (hex CRC-32) — (0.16.0, 1cc50f2). */
    static final String[][] PLANS = { { "plan establish", "9f884d63", "60cd1389" },
        { "plan establish:2", "f19faca7", "60cd1389" }, { "plan establish:3", "469b2547", "60cd1389" },
        { "plan establish:4", "3180ea64", "60cd1389" }, { "plan establish:5", "3247fb3a", "60cd1389" },
        { "plan grow:2", "4752bbb4", "d2c07499" }, { "plan grow:3", "34554205", "3e5f9992" },
        { "plan grow:4", "817f69be", "81a555ae" }, { "plan grow:5", "607c5355", "59dcc3b6" },
        { "plan forum", "3d219b97", "61c7c9c0" }, { "plan establish:outer", "f4723479", "fa2cbd4f" },
        { "plan release |f40,0", "1b733632", "6522df69" }, { "plan module:arcane_court@7", "e569104a", "ca6df654" },
        { "plan module:arcane_court@1", "f4669c5", "260e9374" },
        { "plan module:arcane_court@5", "89deea4e", "45238174" },
        { "plan module:arcane_court@3", "66b2adf8", "9a195a" },
        { "plan module:blood_covenant@5", "2c26af7c", "8a6bd89f" },
        { "plan module:blood_covenant@3", "15759846", "cfd240b1" },
        { "plan module:blood_covenant@7", "9eae6f2", "525afbf" },
        { "plan module:blood_covenant@1", "e96b6005", "e946ca9f" },
        { "plan module:dome_generator@8", "38188654", "4f786957" },
        { "plan module:dome_generator@8 |f40,0", "2b9c4dd8", "7d47384" },
        { "plan module:dome_generator@8 |f40,255", "4de1e3de", "1867dcf4" },
        { "plan module:droplet_hangar@36", "d8582c46", "e3ee9b41" },
        { "plan module:droplet_hangar@36 |s40", "d8582c46", "e3ee9b41" },
        { "plan module:droplet_hangar@32", "5f0052c1", "133f949a" },
        { "plan module:droplet_hangar@32 |s40", "5f0052c1", "133f949a" },
        { "plan module:dual_foil_vault@6", "af679d02", "a571864e" },
        { "plan module:dual_foil_vault@2", "ff6910eb", "209c805e" },
        { "plan module:dual_foil_vault@4", "9510e70b", "df1972c7" },
        { "plan module:eco_dome@2", "b28477d8", "5a6d2275" }, { "plan module:eco_dome@6", "f16ade08", "df802465" },
        { "plan module:eco_dome@4", "8b64de61", "a5e8d0ec" }, { "plan module:holo_table@17", "5e30bd52", "4933118b" },
        { "plan module:holo_table@11", "5a41a744", "cb6f7a12" },
        { "plan module:holo_table@15", "7f5095a5", "1ad74be8" }, { "plan module:holo_table@13", "9081b0af", "7ee9afb" },
        { "plan module:hunting_ground@3", "a2b948e4", "e5c99530" },
        { "plan module:hunting_ground@5", "12dc727e", "a0700d1e" },
        { "plan module:hunting_ground@1", "8267f0f4", "c35d1f1e" },
        { "plan module:hunting_ground@7", "244fc2d9", "2f3e7a3e" }, { "plan module:library@4", "b743636b", "752882d9" },
        { "plan module:library@2", "c2530b73", "8aad7040" }, { "plan module:library@6", "e0735335", "f407650" },
        { "plan module:mana_fountain@1", "cf5c1737", "9706757b" },
        { "plan module:mana_fountain@7", "17b5caf7", "7b65105b" },
        { "plan module:mana_fountain@3", "db7046b8", "b192ff55" },
        { "plan module:mana_fountain@5", "1d78f0e2", "f42b677b" },
        { "plan module:observatory@34", "b9ae4cd0", "eb2edda1" },
        { "plan module:observatory@34 |s40", "b9ae4cd0", "eb2edda1" },
        { "plan module:resonance_gate@0", "df5b88ab", "ded5dd6f" }, { "plan module:root@9", "655026ef", "6522df69" },
        { "plan module:root@9 |rroot", "7e116f1a", "6522df69" },
        { "plan module:root@9 |rroot_7", "3cb3c53", "6522df69" },
        { "plan module:root@9 |rroot_in", "3b753185", "6522df69" },
        { "plan module:root@9 |rroot_in_14", "1b6de2c", "6522df69" },
        { "plan module:root@9 |rroot_in_20", "971f90f2", "6522df69" },
        { "plan module:root@9 |rroot_in_8", "aadc2a82", "6522df69" },
        { "plan module:sky_sails@32", "40f5fc5c", "f2f733f0" },
        { "plan module:sky_sails@32 |s40", "40f5fc5c", "f2f733f0" },
        { "plan module:sky_sails@36", "210c74b1", "2263c2b" },
        { "plan module:sky_sails@36 |s40", "210c74b1", "2263c2b" },
        { "plan module:sophon_array@11", "a4b81cc2", "3cbe76cf" },
        { "plan module:sophon_array@17", "8b5ae718", "bee21d56" },
        { "plan module:sophon_array@13", "b465b4e9", "f03f9626" },
        { "plan module:sophon_array@15", "cd78b4d9", "ed064735" },
        { "plan module:weather_station@30", "11f90fa7", "8cc9727" },
        { "plan module:weather_station@30 |s40", "11f90fa7", "8cc9727" }, };

    @Test
    void everyShapeOf016IsAsItWas() {
        check(SHAPES, ShapeCrc.shapes());
    }

    /** Every plan of 0.16.0 is made again from its key and form as the current builder makes it. */
    @Test
    void everyPlanOf016IsAsItWas() {
        List<ShapeCrc.Row> rows = new ArrayList<>();
        for (String[] f : PLANS) rows.add(ShapeCrc.planRow(f[0]));
        check(PLANS, rows);
    }

    @Test
    void theTablesHoldEveryShapeAndPlanOf016() {
        // 35 shapes: the nexus's five phases, the library's two, the pod's five, the root's ten, thirteen of one shape
        assertEquals(35, SHAPES.length);
        assertEquals(68, PLANS.length);
    }

    private static void check(String[][] frozen, List<ShapeCrc.Row> rows) {
        Map<String, long[]> now = new LinkedHashMap<>();
        for (ShapeCrc.Row r : rows) now.put(r.what, r.crc);
        List<String> wrong = new ArrayList<>();
        for (String[] f : frozen) {
            long[] c = now.get(f[0]);
            if (c == null) {
                wrong.add(f[0] + ": gone");
                continue;
            }
            if (c[0] == 0 && c[c.length - 1] == 0 && !f[1].equals("0")) {
                wrong.add(f[0] + ": no plan any more");
                continue;
            }
            for (int i = 1; i < f.length; i++) if (Long.parseLong(f[i], 16) != c[i - 1])
                wrong.add(f[0] + ": checksum " + i + " was " + f[i] + ", is " + Long.toHexString(c[i - 1]));
        }
        if (!wrong.isEmpty())
            fail(wrong.size() + " shapes or plans of 0.16.0 changed:\n  " + String.join("\n  ", wrong));
    }
}
