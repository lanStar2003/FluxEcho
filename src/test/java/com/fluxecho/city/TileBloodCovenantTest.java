package com.fluxecho.city;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.fluxecho.logic.BloodCovenantShape;
import com.fluxecho.logic.ModuleCores;
import com.fluxecho.logic.ModuleMap;
import com.fluxecho.logic.ShapeKit;

/**
 * The Blood Covenant Altar as its tile sees it in the world (0.13.0), for every facing ({@link PavilionTileChecks}):
 * its blueprint sits where StructureLib's facing math looks for it, the altar's console on the dais under the
 * controller opens the window, every socket is its slot and every unit its kind, nothing dissolves, an altar the campus
 * builder raised on a diagonal has its frame where the plan put the blocks and docks there; its spec keeps the campus
 * off its apron and allee, the window's plan is the floor with the dais, and its run-time names are in both lang files.
 */
class TileBloodCovenantTest {

    static final PavilionTileChecks.Pavilion P = new PavilionTileChecks.Pavilion(
        TileBloodCovenant::new,
        CityModule.bloodCovenant(),
        BloodCovenantShape.BUILD,
        BloodCovenantShape.UNITS,
        ModuleCores.BLOOD_COVENANT,
        TileBloodCovenant.COLOR,
        BloodCovenantShape.DISSOLVES);

    @Test
    void theBlueprintMapsCellsAsStructureLibDoes() {
        PavilionTileChecks.blueprintMapsCellsAsStructureLibDoes(P);
    }

    /** The controller at y3 over the altar's console on the dais, three over the foundation; core meta 3. */
    @Test
    void theControllerStandsOverTheAltar() {
        PavilionTileChecks.theControllerStandsOnItsConsole(P);
        TileCityModule t = P.at(PavilionTileChecks.X, PavilionTileChecks.Y, PavilionTileChecks.Z, 2);
        assertEquals(3, t.coreMetaOf(), "fluxecho:module_core meta 3");
        assertEquals(12, t.unitCount());
        assertEquals(0xE0344A, t.moduleColor());
    }

    @Test
    void everySocketIsItsSlotAndEveryUnitItsKind() {
        PavilionTileChecks.everySocketIsItsSlotAndEveryUnitItsKind(P);
        TileCityModule t = P.at(PavilionTileChecks.X, PavilionTileChecks.Y, PavilionTileChecks.Z, 4);
        for (int u = 0; u < 12; u++) assertEquals(u < 8 ? "cradle" : "ritual", t.unitKind(u), "unit " + u);
    }

    /** Nothing of the altar dissolves: every block stays drawn and solid once it forms. */
    @Test
    void nothingDissolves() {
        TileCityModule t = P.at(PavilionTileChecks.X, PavilionTileChecks.Y, PavilionTileChecks.Z, 3);
        for (char ch : "FBKtWLwnrAopC Z~".toCharArray()) assertEquals(0, t.flags(ch), "'" + ch + "'");
    }

    @Test
    void localIsTheInverseOfWorldOf() {
        PavilionTileChecks.localIsTheInverseOfWorldOf(P);
    }

    @Test
    void aBuiltAltarsFrameIsThePlans() {
        PavilionTileChecks.aBuiltPavilionsFrameIsThePlans(P);
    }

    @Test
    void theSpecKeepsTheCampusOffItsApronAndAllee() {
        PavilionTileChecks.theSpecKeepsTheCampusOffItsApronAndAllee(P, new int[] { 5, 3, 7, 1 });
    }

    @Test
    void theRefreshKeepsOffAStandingAltar() {
        PavilionTileChecks.theRefreshKeepsOffAStandingPavilion(P);
    }

    /**
     * The window's plan: the floor the units stand on, what stands at y1 over the floor: the dais's block ring and its
     * treads round the altar's console (shown over the dais's open middle cell), the cradles' plinths, the ritual
     * stones' sockets in their bays, the wall and the portals.
     */
    @Test
    void theWindowsPlanIsTheFloorWithTheDais() {
        ModuleMap map = PavilionTileChecks.theWindowsPlanFits(P);
        assertEquals(ShapeKit.NONE, BloodCovenantShape.cell(12, 1, 12), "the dais's middle cell is open");
        assertEquals(ModuleMap.Mark.CONSOLE, map.mark(0, 12, 12), "the altar's console over it");
        assertEquals(ModuleMap.Mark.WALL, map.mark(0, 12, 11), "the dais's block ring");
        assertEquals(ModuleMap.Mark.STAIR, map.mark(0, 12, 9), "its treads");
        assertEquals(ModuleMap.Mark.FIXTURE, map.mark(0, 12, 3), "a ritual stone");
        assertEquals(ModuleMap.Mark.FLOOR, map.mark(0, 12, 7), "the floor");
        assertEquals(ModuleMap.Mark.WALL, map.mark(0, 12, 6), "a cradle's plinth");
        assertEquals(ModuleMap.Mark.DOOR, map.mark(0, 4, 4), "a portal");
    }

    /**
     * M10's "blood floor/tiers": after the floor the plan page shows the two upper tiers, which no unit stands on: tier
     * 2 at y8, its walls (a lit rib on each 0/45 degree line) on the terrace of the y7 slab with its rail, and tier 3
     * at y13 on the slab at y12; both open over the funnel in the middle. A page of the building alone has a tab of
     * its own in both lang files.
     */
    @Test
    void theWindowsPlanShowsTheTiers() throws java.io.IOException {
        ModuleMap map = PavilionTileChecks.theWindowsPlanFits(P);
        assertArrayEquals(new int[] { 8, 13 }, BloodCovenantShape.planLevels());
        assertArrayEquals(
            BloodCovenantShape.planLevels(),
            P.at(PavilionTileChecks.X, PavilionTileChecks.Y, PavilionTileChecks.Z, 2)
                .planLevels());
        assertEquals(3, map.storeys());
        // tier 2: its wall ring with a lit rib, the terrace and its rail outside it, the funnel open inside
        assertEquals(ModuleMap.Mark.PILASTER, map.mark(1, 12, 3), "tier 2's rib");
        assertEquals(ModuleMap.Mark.WALL, map.mark(1, 11, 3), "tier 2's wall");
        assertEquals(ModuleMap.Mark.FLOOR, map.mark(1, 12, 1), "the terrace on the slab at y7");
        assertEquals(ModuleMap.Mark.RAIL, map.mark(1, 12, 0), "its rail");
        assertEquals(ModuleMap.Mark.NONE, map.mark(1, 12, 6), "the funnel");
        assertEquals(ModuleMap.Mark.NONE, map.mark(1, 12, 12));
        // tier 3: its walls over the slab at y12, nothing past tier 2's walls
        assertEquals(ModuleMap.Mark.PILASTER, map.mark(2, 12, 6), "tier 3's rib");
        assertEquals(ModuleMap.Mark.FLOOR, map.mark(2, 12, 4), "the slab at y12");
        assertEquals(ModuleMap.Mark.NONE, map.mark(2, 12, 12), "the funnel under the oculus");
        assertEquals(ModuleMap.Mark.NONE, map.mark(2, 0, 12), "outside tier 2");
        // someone on the terrace stands on tier 2's page, someone on the dais on the floor's
        assertEquals(1, map.storeyAt(8));
        assertEquals(0, map.storeyAt(2));
        java.util.Properties en = PavilionTileChecks.lang("en_US"), zh = PavilionTileChecks.lang("zh_CN");
        for (String k : new String[] { "fluxecho.city.gui.level_short", "fluxecho.city.gui.level.tip" }) {
            assertTrue(en.containsKey(k) && zh.containsKey(k), k);
            assertEquals(
                1,
                en.getProperty(k)
                    .split("%s", -1).length - 1,
                k);
            assertEquals(
                1,
                zh.getProperty(k)
                    .split("%s", -1).length - 1,
                k);
        }
    }

    @Test
    void itsRunTimeKeysAreInBothLangFiles() throws java.io.IOException {
        PavilionTileChecks.itsRunTimeKeysAreInBothLangFiles(P);
        assertTrue(P.spec.hasSite(5));
    }
}
