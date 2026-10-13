package com.fluxecho.city;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.fluxecho.logic.ModuleCores;
import com.fluxecho.logic.ModuleMap;
import com.fluxecho.logic.SophonArrayShape;

/**
 * The Sophon Array as its tile sees it in the world (0.15.0), for every facing ({@link OuterDiagTileChecks}): its
 * blueprint sits where StructureLib's facing math looks for it, the console under the controller at the hall's centre
 * opens the window, every socket is its slot and every rack its kind, the struts and the lattice dissolve and let
 * players through, an array the campus builder raised on an outer diagonal has its frame where the plan put the
 * blocks and docks there; its spec keeps the campus off its apron and allée, the outer establish keeps off it, it is
 * offered at phase III on the outer ground, the window's plan shows the hall, and its run-time names are in both lang
 * files.
 */
class TileSophonArrayTest {

    static final OuterDiagTileChecks.Building B = new OuterDiagTileChecks.Building(
        TileSophonArray::new,
        CityModule.sophonArray(),
        SophonArrayShape.BUILD,
        SophonArrayShape.UNITS,
        ModuleCores.SOPHON_ARRAY,
        TileSophonArray.COLOR,
        SophonArrayShape.DISSOLVES);

    @Test
    void theBlueprintMapsCellsAsStructureLibDoes() {
        OuterDiagTileChecks.blueprintMapsCellsAsStructureLibDoes(B);
    }

    @Test
    void theControllerStandsUnderTheOculus() {
        OuterDiagTileChecks.theControllerStandsOnItsConsole(B);
        TileCityModule t = B.at(OuterDiagTileChecks.X, OuterDiagTileChecks.Y, OuterDiagTileChecks.Z, 4);
        assertEquals(9, t.coreMetaOf(), "fluxecho:module_core meta 9");
        assertEquals(48, t.unitCount());
        assertEquals(0xFF8CE6, t.moduleColor());
        assertEquals("sophon_array", t.moduleKey());
    }

    @Test
    void everySocketIsItsSlotAndEveryRackItsKind() {
        OuterDiagTileChecks.everySocketIsItsSlotAndEveryUnitItsKind(B);
        TileCityModule t = B.at(OuterDiagTileChecks.X, OuterDiagTileChecks.Y, OuterDiagTileChecks.Z, 5);
        for (int u = 0; u < 48; u++) assertEquals("memory_rack", t.unitKind(u), "unit " + u);
        assertEquals("rack-48", t.unitName(47));
    }

    /**
     * Formed, the struts and the lattice sphere's frame pillars give way to the rendered geodesic frame and let players
     * through (they stand over the podium's roof, where nobody walks: {@code SophonArrayWalkTest}); the podium and the
     * racks stay.
     */
    @Test
    void theLatticeDissolvesAndLetsPlayersThrough() {
        OuterDiagTileChecks.itsDissolvingCellsLetPlayersThrough(B, "KnWLGwopcAF");
    }

    @Test
    void localIsTheInverseOfWorldOf() {
        OuterDiagTileChecks.localIsTheInverseOfWorldOf(B);
    }

    @Test
    void aBuiltArraysFrameIsThePlans() {
        OuterDiagTileChecks.aBuiltModulesFrameIsThePlans(B);
    }

    @Test
    void theSpecKeepsTheCampusOffItsSite() {
        OuterDiagTileChecks.theSpecKeepsTheCampusOffItsSite(B, new int[] { 11, 17, 13, 15 });
    }

    @Test
    void itIsOfferedOnTheOuterGroundAtPhaseThree() {
        OuterDiagTileChecks.itIsOfferedOnTheOuterGroundAtPhaseThree(B);
    }

    @Test
    void theOuterEstablishKeepsOffAStandingArray() {
        OuterDiagTileChecks.theOuterEstablishKeepsOffAStandingModule(B);
    }

    /** The window's plan: one storey, the hall's floor, its racks round the console at the centre. */
    @Test
    void theWindowsPlanShowsTheHall() {
        ModuleMap map = OuterDiagTileChecks.theWindowsPlanFits(B);
        assertEquals(1, map.storeys());
        assertEquals(ModuleMap.Mark.CONSOLE, map.mark(0, 15, 15), "the console under the oculus");
    }

    @Test
    void itsCoreIsManifestedWithPhaseThreesMaterials() {
        OuterDiagTileChecks.itsCoreIsManifestedWithPhaseThreesMaterials(B, "minecraft:comparator");
    }

    @Test
    void itsRunTimeKeysAreInBothLangFiles() throws java.io.IOException {
        OuterDiagTileChecks.itsRunTimeKeysAreInBothLangFiles(B);
    }
}
