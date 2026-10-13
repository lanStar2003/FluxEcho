package com.fluxecho.city;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.fluxecho.frame.Formed;
import com.fluxecho.logic.ManaFountainShape;
import com.fluxecho.logic.ModuleCores;
import com.fluxecho.logic.ModuleMap;

/**
 * The Mana Fountain Array as its tile sees it in the world (0.13.0), for every facing ({@link PavilionTileChecks}): its
 * blueprint sits where StructureLib's facing math looks for it, the obelisk's console under the controller opens the
 * window, every socket is its slot and every unit its kind, the obelisk's conduits and seat dissolve and let players
 * through, an array the campus builder raised on a diagonal has its frame where the plan put the blocks (its fountains'
 * water too) and docks there; its spec keeps the campus off its apron and allee, the window's plan shows the court, and
 * its run-time names are in both lang files.
 */
class TileManaFountainTest {

    static final PavilionTileChecks.Pavilion P = new PavilionTileChecks.Pavilion(
        TileManaFountain::new,
        CityModule.manaFountain(),
        ManaFountainShape.BUILD,
        ManaFountainShape.UNITS,
        ModuleCores.MANA_FOUNTAIN,
        TileManaFountain.COLOR,
        ManaFountainShape.DISSOLVES);

    @Test
    void theBlueprintMapsCellsAsStructureLibDoes() {
        PavilionTileChecks.blueprintMapsCellsAsStructureLibDoes(P);
    }

    @Test
    void theControllerStandsOnTheObelisk() {
        PavilionTileChecks.theControllerStandsOnItsConsole(P);
        TileCityModule t = P.at(PavilionTileChecks.X, PavilionTileChecks.Y, PavilionTileChecks.Z, 4);
        assertEquals(5, t.coreMetaOf(), "fluxecho:module_core meta 5");
        assertEquals(16, t.unitCount());
        assertEquals(0x6AA8FF, t.moduleColor());
    }

    @Test
    void everySocketIsItsSlotAndEveryUnitItsKind() {
        PavilionTileChecks.everySocketIsItsSlotAndEveryUnitItsKind(P);
        TileCityModule t = P.at(PavilionTileChecks.X, PavilionTileChecks.Y, PavilionTileChecks.Z, 5);
        for (int u = 0; u < 16; u++) assertEquals(u < 8 ? "fountain" : "urn", t.unitKind(u), "unit " + u);
        // the fountain's water is no unit's; its rim is
        int[] water = t.worldOf(18, 1, 12);
        assertEquals(-1, t.unitAt(water[0], water[1], water[2]));
        int[] rim = t.worldOf(17, 1, 12);
        assertEquals(0, t.unitAt(rim[0], rim[1], rim[2]), "the first fountain's rim");
    }

    /**
     * Formed, the obelisk's conduits and seat give way to the rendered mana spout and let players through (they stand
     * on the controller, over every head: {@code ManaFountainWalkTest}); the console, the colonnade and the fountains
     * stay.
     */
    @Test
    void theObeliskDissolvesAndLetsPlayersThrough() {
        TileCityModule t = P.at(PavilionTileChecks.X, PavilionTileChecks.Y, PavilionTileChecks.Z, 3);
        assertEquals(Formed.HIDE | Formed.PASS, t.flags('C'));
        assertEquals(Formed.HIDE | Formed.PASS, t.flags('Z'));
        for (char ch : "KqnWLGwuopAF".toCharArray()) assertEquals(0, t.flags(ch), "'" + ch + "'");
    }

    @Test
    void localIsTheInverseOfWorldOf() {
        PavilionTileChecks.localIsTheInverseOfWorldOf(P);
    }

    @Test
    void aBuiltArraysFrameIsThePlans() {
        PavilionTileChecks.aBuiltPavilionsFrameIsThePlans(P);
    }

    @Test
    void theSpecKeepsTheCampusOffItsApronAndAllee() {
        PavilionTileChecks.theSpecKeepsTheCampusOffItsApronAndAllee(P, new int[] { 1, 7, 3, 5 });
    }

    @Test
    void theRefreshKeepsOffAStandingArray() {
        PavilionTileChecks.theRefreshKeepsOffAStandingPavilion(P);
    }

    /**
     * The window's plan: one storey, what stands at y1 over the floor: the obelisk's console in the court, the
     * fountains' rims round their water, the urns, the colonnade's posts, the garden in the arcade ring.
     */
    @Test
    void theWindowsPlanShowsTheCourt() {
        ModuleMap map = PavilionTileChecks.theWindowsPlanFits(P);
        assertEquals(ModuleMap.Mark.CONSOLE, map.mark(0, 12, 12), "the obelisk's console");
        assertEquals(ModuleMap.Mark.WATER, map.mark(0, 18, 12), "a fountain's water");
        assertEquals(ModuleMap.Mark.FIXTURE, map.mark(0, 17, 12), "its rim");
        assertEquals(ModuleMap.Mark.POST, map.mark(0, 11, 3), "a post of the colonnade");
        assertEquals(ModuleMap.Mark.GRASS, map.mark(0, 1, 9), "the arcade's garden");
        assertEquals(ModuleMap.Mark.LIT, map.mark(0, 12, 10), "the magic circle");
    }

    @Test
    void itsRunTimeKeysAreInBothLangFiles() throws java.io.IOException {
        PavilionTileChecks.itsRunTimeKeysAreInBothLangFiles(P);
    }
}
