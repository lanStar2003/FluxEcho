package com.fluxecho.city;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.fluxecho.frame.Formed;
import com.fluxecho.logic.HuntingGroundShape;
import com.fluxecho.logic.ModuleCores;
import com.fluxecho.logic.ModuleMap;

/**
 * The Echo Hunting Ground as its tile sees it in the world (0.13.0), for every facing ({@link PavilionTileChecks}): its
 * blueprint sits where StructureLib's facing math looks for it, the emitter's console under the controller opens the
 * window, every console's socket is its slot, the velarium dissolves and lets players through, a ground the campus
 * builder raised on a diagonal has its frame where the plan put the blocks and docks there; its spec keeps the campus
 * off its apron and allee, the window's plan shows the arena and the terraces, and its run-time names are in both lang
 * files.
 */
class TileHuntingGroundTest {

    static final PavilionTileChecks.Pavilion P = new PavilionTileChecks.Pavilion(
        TileHuntingGround::new,
        CityModule.huntingGround(),
        HuntingGroundShape.BUILD,
        HuntingGroundShape.UNITS,
        ModuleCores.HUNTING_GROUND,
        TileHuntingGround.COLOR,
        HuntingGroundShape.DISSOLVES);

    @Test
    void theBlueprintMapsCellsAsStructureLibDoes() {
        PavilionTileChecks.blueprintMapsCellsAsStructureLibDoes(P);
    }

    @Test
    void theControllerStandsOnTheEmitter() {
        PavilionTileChecks.theControllerStandsOnItsConsole(P);
        TileCityModule t = P.at(PavilionTileChecks.X, PavilionTileChecks.Y, PavilionTileChecks.Z, 5);
        assertEquals(4, t.coreMetaOf(), "fluxecho:module_core meta 4");
        assertEquals(12, t.unitCount());
        assertEquals(0xFF9A3C, t.moduleColor());
    }

    @Test
    void everySocketIsItsSlotAndEveryUnitItsKind() {
        PavilionTileChecks.everySocketIsItsSlotAndEveryUnitItsKind(P);
        TileCityModule t = P.at(PavilionTileChecks.X, PavilionTileChecks.Y, PavilionTileChecks.Z, 2);
        for (int u = 0; u < 12; u++) assertEquals("imprint", t.unitKind(u), "unit " + u);
    }

    /**
     * Formed, the velarium's ring segments give way to the rendered canopy and let players through (they hang over the
     * top terraces, out of everyone's reach: {@code HuntingGroundWalkTest}); the terraces, the bridges and the consoles
     * stay solid.
     */
    @Test
    void theVelariumDissolvesAndLetsPlayersThrough() {
        TileCityModule t = P.at(PavilionTileChecks.X, PavilionTileChecks.Y, PavilionTileChecks.Z, 3);
        assertEquals(Formed.HIDE | Formed.PASS, t.flags('R'));
        for (char ch : "WdtLwnGF:KhpoA".toCharArray()) assertEquals(0, t.flags(ch), "'" + ch + "'");
    }

    @Test
    void localIsTheInverseOfWorldOf() {
        PavilionTileChecks.localIsTheInverseOfWorldOf(P);
    }

    @Test
    void aBuiltGroundsFrameIsThePlans() {
        PavilionTileChecks.aBuiltPavilionsFrameIsThePlans(P);
    }

    @Test
    void theSpecKeepsTheCampusOffItsApronAndAllee() {
        PavilionTileChecks.theSpecKeepsTheCampusOffItsApronAndAllee(P, new int[] { 3, 5, 1, 7 });
    }

    @Test
    void theRefreshKeepsOffAStandingGround() {
        PavilionTileChecks.theRefreshKeepsOffAStandingPavilion(P);
    }

    /**
     * The window's plan: one storey, what stands at y1 over the floor: the arena's holo grid round the emitter's
     * console and its pedestals, the lowest terrace's treads and the seating's blocks round it, the vomitoria open to
     * the floor, the consoles on the arena's rim.
     */
    @Test
    void theWindowsPlanShowsTheArenaAndTheTerraces() {
        ModuleMap map = PavilionTileChecks.theWindowsPlanFits(P);
        assertEquals(ModuleMap.Mark.CONSOLE, map.mark(0, 12, 12), "the emitter's console");
        assertEquals(ModuleMap.Mark.FIXTURE, map.mark(0, 14, 12), "a holo pedestal");
        ModuleMap.Mark grid = map.mark(0, 12, 9);
        assertTrue(grid == ModuleMap.Mark.FLOOR || grid == ModuleMap.Mark.LIT, "the arena's grid: " + grid);
        assertEquals(ModuleMap.Mark.LIT, map.mark(0, 12, 11), "a lit tile of the grid");
        assertEquals(ModuleMap.Mark.STAIR, map.mark(0, 11, 6), "the lowest terrace");
        assertEquals(ModuleMap.Mark.FLOOR, map.mark(0, 6, 6), "a vomitorium, open to the floor");
    }

    @Test
    void itsRunTimeKeysAreInBothLangFiles() throws java.io.IOException {
        PavilionTileChecks.itsRunTimeKeysAreInBothLangFiles(P);
    }
}
