package com.fluxecho.city;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fluxecho.campus.ModuleSpec;
import com.fluxecho.library.LibraryModule;
import com.fluxecho.logic.CampusBand;
import com.fluxecho.logic.DomeGeneratorShape;
import com.fluxecho.logic.RootShape;

/**
 * Every building is buildable at every height a campus may be laid out at (0.14.1, CITY.md §1.3 Vertical): over the
 * whole campus band no module's spec says it has no room (0.14.0 refused the zenith over Y0 203, the Tidal Well under
 * Y0 32 and the Arcane Court at Y0 224 and 225); the axis modules name the shape the base gives them, which the
 * builder hands to the tile it commissions.
 */
class CityHeightsTest {

    private static List<ModuleSpec> specs() {
        List<ModuleSpec> out = new ArrayList<>();
        out.add(LibraryModule.spec());
        out.add(CityModule.ecoDome());
        out.add(CityModule.domeGenerator());
        out.add(CityModule.root());
        out.add(CityModule.arcaneCourt());
        out.add(CityModule.bloodCovenant());
        out.add(CityModule.huntingGround());
        out.add(CityModule.manaFountain());
        out.add(CityModule.resonanceGate());
        out.add(CityModule.dualFoilVault());
        out.add(CityModule.sophonArray());
        out.add(CityModule.holoTable());
        return out;
    }

    @Test
    void everyBuildingHasRoomAcrossTheCampusBand() {
        List<ModuleSpec> all = specs();
        assertEquals(12, all.size());
        for (ModuleSpec spec : all) for (int y0 = CampusBand.LOWEST; y0 <= CampusBand.HIGHEST; y0++)
            assertNull(spec.noRoom(y0), spec.key + " at Y0 " + y0);
        // out of the band (a 0.9.2 nexus may stand anywhere), the reasons stay
        assertEquals(
            "build.module.why.depth",
            CityModule.root()
                .noRoom(13));
        assertEquals(
            "build.module.why.height",
            CityModule.domeGenerator()
                .noRoom(226));
        assertEquals(
            "build.module.why.height",
            CityModule.arcaneCourt()
                .noRoom(251));
    }

    @Test
    void theAxisModulesNameTheirShapeByTheBase() {
        ModuleSpec zenith = CityModule.domeGenerator(), root = CityModule.root();
        for (int y0 = 0; y0 <= 255; y0++) {
            assertEquals(DomeGeneratorShape.shapeFor(y0), zenith.shapeFor(y0));
            assertEquals(RootShape.shapeFor(y0), root.shapeFor(y0));
        }
        assertEquals(DomeGeneratorShape.MOORED, zenith.shapeFor(79));
        assertEquals(DomeGeneratorShape.MOORED_28, zenith.shapeFor(220));
        assertEquals(RootShape.WELL, root.shapeFor(79));
        assertEquals(RootShape.WELL_14, root.shapeFor(22));
        for (ModuleSpec spec : specs()) {
            if (spec.axis()) continue;
            assertNull(spec.shapeFor(79), spec.key + " has one shape for every height");
        }
    }

    /**
     * The in-game texts say where the axis modules fit since they adapt (0.14.1): the Tidal Well's structure text names
     * the band's bottom, Y {@link RootShape#MIN_Y0}, in both lang files, and neither they nor the well's quest say the
     * base must be at Y 32 any more (0.14.0's did, and the well's tooltip gave a fixed 26 blocks); the zenith's and the
     * Arcane Court's quests no longer say they do not fit high up.
     */
    @Test
    void theTextsSayWhereTheAxisModulesFit() throws java.io.IOException {
        java.nio.file.Path res = java.nio.file.Paths.get("src", "main", "resources", "assets", "fluxecho");
        for (String file : new String[] { "en_US.lang", "zh_CN.lang" }) {
            java.util.Map<String, String> lang = new java.util.HashMap<>();
            for (String line : java.nio.file.Files
                .readAllLines(res.resolve("lang/" + file), java.nio.charset.StandardCharsets.UTF_8)) {
                int eq = line.indexOf('=');
                if (eq > 0 && !line.startsWith("#")) lang.put(line.substring(0, eq), line.substring(eq + 1));
            }
            String root = lang.get("fluxecho.root.structure");
            org.junit.jupiter.api.Assertions.assertTrue(root.contains("Y " + RootShape.MIN_Y0), file + ": " + root);
            for (java.util.Map.Entry<String, String> e : lang.entrySet()) {
                String v = e.getValue();
                org.junit.jupiter.api.Assertions
                    .assertFalse(v.contains("Y 32 or higher") || v.contains("Y 32 以上"), file + " " + e.getKey());
            }
        }
        java.nio.file.Path quests = res.resolve("quests/DefaultQuests/Quests");
        java.util.Map<String, String> quest = new java.util.HashMap<>();
        try (java.util.stream.Stream<java.nio.file.Path> all = java.nio.file.Files.walk(quests)) {
            for (java.nio.file.Path q : (Iterable<java.nio.file.Path>) all::iterator) {
                String name = q.getFileName()
                    .toString();
                for (String k : new String[] { "Root-", "DomeGenerator-", "ArcaneCourt-" })
                    if (name.startsWith(k)) quest.put(k, new String(java.nio.file.Files.readAllBytes(q), "UTF-8"));
            }
        }
        assertEquals(
            3,
            quest.size(),
            quest.keySet()
                .toString());
        org.junit.jupiter.api.Assertions.assertFalse(
            quest.get("Root-")
                .contains("Y 32 以上"));
        org.junit.jupiter.api.Assertions.assertTrue(
            quest.get("Root-")
                .contains("Y " + RootShape.MIN_Y0 + " 以上"));
        for (String k : new String[] { "DomeGenerator-", "ArcaneCourt-" }) org.junit.jupiter.api.Assertions.assertFalse(
            quest.get(k)
                .contains("放不下"),
            k + " still says it does not fit");
    }
}
