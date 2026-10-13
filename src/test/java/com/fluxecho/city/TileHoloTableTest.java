package com.fluxecho.city;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.fluxecho.logic.HoloTableShape;
import com.fluxecho.logic.ModuleCores;
import com.fluxecho.logic.ModuleMap;

/**
 * The Holo Sand Table as its tile sees it in the world (0.15.0), for every facing ({@link OuterDiagTileChecks}): its
 * blueprint sits where StructureLib's facing math looks for it, the console at the table's front under the controller
 * opens the window (the nine layer consoles are units), the table's nine slots show over the whole table, the ring
 * light dissolves and lets players through, a table the campus builder raised on an outer diagonal has its frame where
 * the plan put the blocks and docks there; its spec keeps the campus off its apron and allée, the outer establish keeps
 * off it, it is offered at phase III on the outer ground, the window's plan shows the table and the gallery, and its
 * run-time names are in both lang files.
 */
class TileHoloTableTest {

    static final OuterDiagTileChecks.Building B = new OuterDiagTileChecks.Building(
        TileHoloTable::new,
        CityModule.holoTable(),
        HoloTableShape.BUILD,
        HoloTableShape.UNITS,
        ModuleCores.HOLO_TABLE,
        TileHoloTable.COLOR,
        HoloTableShape.DISSOLVES);

    @Test
    void theBlueprintMapsCellsAsStructureLibDoes() {
        OuterDiagTileChecks.blueprintMapsCellsAsStructureLibDoes(B);
    }

    @Test
    void theControllerStandsAtTheTablesFront() {
        OuterDiagTileChecks.theControllerStandsOnItsConsole(B);
        TileCityModule t = B.at(OuterDiagTileChecks.X, OuterDiagTileChecks.Y, OuterDiagTileChecks.Z, 2);
        assertArrayEquals(
            new int[] { 15, 2, 9 },
            t.local(OuterDiagTileChecks.X, OuterDiagTileChecks.Y, OuterDiagTileChecks.Z));
        assertEquals(10, t.coreMetaOf(), "fluxecho:module_core meta 10");
        assertEquals(10, t.unitCount());
        assertEquals(0x66F0FF, t.moduleColor());
        assertEquals("holo_table", t.moduleKey());
        // the layer consoles are console stands of units: clicking one uses its unit, not the window's console
        int[] layer = t.worldOf(22, 1, 15);
        assertFalse(t.consoleAt(layer[0], layer[1], layer[2]));
        assertEquals(1, t.unitAt(layer[0], layer[1], layer[2]));
    }

    /**
     * The table is one unit of nine slots shown over the whole table (no sockets); the consoles keep nothing: units of
     * no slots, which open the window at their layer.
     */
    @Test
    void theTableKeepsNineAndTheConsolesNone() {
        OuterDiagTileChecks.everySocketIsItsSlotAndEveryUnitItsKind(B);
        TileCityModule t = B.at(OuterDiagTileChecks.X, OuterDiagTileChecks.Y, OuterDiagTileChecks.Z, 3);
        assertEquals("table", t.unitKind(0));
        assertArrayEquals(new int[] { 0, 9 }, t.unitSlots(0));
        for (int u = 1; u < 10; u++) {
            assertEquals("console", t.unitKind(u));
            assertArrayEquals(new int[] { 9, 0 }, t.unitSlots(u), "unit " + u + " keeps nothing");
            assertEquals(-1, t.firstFree(u));
        }
        assertEquals(
            9,
            t.samples()
                .getSlots());
        int[] glass = t.worldOf(15, 1, 15), rim = t.worldOf(19, 1, 15);
        assertEquals(0, t.unitAt(glass[0], glass[1], glass[2]));
        assertEquals(0, t.unitAt(rim[0], rim[1], rim[2]));
        assertEquals(-1, t.takeSlot(0, glass[0], glass[1], glass[2]), "nothing to take from an empty table");
    }

    /**
     * Formed, the ring light's segments give way to the rendered ring of light and let players through (they hang over
     * the table, out of everyone's reach: {@code HoloTableWalkTest}); the drum, the ramps and the table stay.
     */
    @Test
    void theRingLightDissolvesAndLetsPlayersThrough() {
        OuterDiagTileChecks.itsDissolvingCellsLetPlayersThrough(B, "KnWLGwptrdAF");
    }

    @Test
    void localIsTheInverseOfWorldOf() {
        OuterDiagTileChecks.localIsTheInverseOfWorldOf(B);
    }

    @Test
    void aBuiltTablesFrameIsThePlans() {
        OuterDiagTileChecks.aBuiltModulesFrameIsThePlans(B);
    }

    @Test
    void theSpecKeepsTheCampusOffItsSite() {
        OuterDiagTileChecks.theSpecKeepsTheCampusOffItsSite(B, new int[] { 17, 11, 15, 13 });
    }

    @Test
    void itIsOfferedOnTheOuterGroundAtPhaseThree() {
        OuterDiagTileChecks.itIsOfferedOnTheOuterGroundAtPhaseThree(B);
    }

    @Test
    void theOuterEstablishKeepsOffAStandingTable() {
        OuterDiagTileChecks.theOuterEstablishKeepsOffAStandingModule(B);
    }

    /**
     * The window's plan: the floor's storey (the table's glass and rim, the consoles round it, the console at the
     * front) and the gallery shown for the building alone at stand 7 (its deck and rail at the back).
     */
    @Test
    void theWindowsPlanShowsTheTableAndTheGallery() {
        ModuleMap map = OuterDiagTileChecks.theWindowsPlanFits(B);
        assertEquals(2, map.storeys());
        assertEquals(HoloTableShape.GALLERY_STAND, map.level(1));
        assertEquals(ModuleMap.Mark.CONSOLE, map.mark(0, 15, 9), "the controller's console");
        assertEquals(ModuleMap.Mark.CONSOLE, map.mark(0, 22, 15), "a layer console");
        assertEquals(ModuleMap.Mark.GLAZE, map.mark(0, 15, 15), "the table's glass top");
        assertEquals(ModuleMap.Mark.RAIL, map.mark(1, 15, 27), "the gallery's rail");
        assertTrue(map.mark(1, 15, 28) == ModuleMap.Mark.FLOOR, "the gallery's deck: " + map.mark(1, 15, 28));
    }

    @Test
    void itsCoreIsManifestedWithPhaseThreesMaterials() {
        OuterDiagTileChecks.itsCoreIsManifestedWithPhaseThreesMaterials(B, "minecraft:map");
    }

    @Test
    void itsRunTimeKeysAreInBothLangFiles() throws java.io.IOException {
        OuterDiagTileChecks.itsRunTimeKeysAreInBothLangFiles(B);
    }
}
