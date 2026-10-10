package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.fluxecho.logic.UnitLook.Kind;

class UnitLookTest {

    @Test
    void nicheTruthTable() {
        int niches = 0;
        for (Kind below : Kind.values()) for (Kind above : Kind.values()) {
            boolean expected = (below == Kind.SHELF || below == Kind.PLINTH)
                && (above == Kind.SHELF || above == Kind.CROWN);
            assertEquals(expected, UnitLook.niche(below, above), below + " below, " + above + " above");
            if (expected) niches++;
        }
        assertEquals(4, niches, "two kinds of foot times two kinds of head");
    }

    @Test
    void everyBodyOfAUnitIsANiche() {
        // a unit column from the floor up: plinth, three bodies, crown, then the gallery deck over it
        Kind[] column = { Kind.OPAQUE, Kind.PLINTH, Kind.SHELF, Kind.SHELF, Kind.SHELF, Kind.CROWN, Kind.OPAQUE };
        for (int y = 2; y <= 4; y++)
            assertTrue(UnitLook.niche(column[y - 1], column[y + 1]), "body at y" + y + " of a unit");
    }

    @Test
    void looseShelvesKeepThePlainLook() {
        assertFalse(UnitLook.niche(Kind.OPAQUE, Kind.OTHER), "a single shelf on the floor");
        assertFalse(UnitLook.niche(Kind.OPAQUE, Kind.SHELF), "the bottom of a plain stack");
        assertFalse(UnitLook.niche(Kind.SHELF, Kind.OPAQUE), "the top of a plain stack under a ceiling");
        assertFalse(UnitLook.niche(Kind.SHELF, Kind.OTHER), "the top of a plain stack under air");
        assertFalse(UnitLook.niche(Kind.CROWN, Kind.PLINTH), "upside down");
        assertFalse(UnitLook.niche(Kind.POST, Kind.POST), "posts are not feet or heads");
    }

    @Test
    void bookFaces() {
        assertTrue(UnitLook.bookFace(Kind.OTHER), "open to the aisle");
        assertTrue(UnitLook.bookFace(Kind.PLINTH), "a fitting is not opaque");
        assertTrue(UnitLook.bookFace(Kind.CROWN), "a fitting is not opaque");
        assertFalse(UnitLook.bookFace(Kind.OPAQUE), "against a wall");
        assertFalse(UnitLook.bookFace(Kind.SHELF), "inside a run or back to back");
        assertFalse(UnitLook.bookFace(Kind.POST), "against the upright between units");
    }

    @Test
    void aRunAgainstAWallShowsOneFacePerBody() {
        // three bodies along x between two posts, the aisle at -z and the wall at +z
        Kind[] run = { Kind.POST, Kind.SHELF, Kind.SHELF, Kind.SHELF, Kind.POST };
        for (int i = 1; i <= 3; i++) {
            int faces = 0;
            if (UnitLook.bookFace(run[i - 1])) faces++;
            if (UnitLook.bookFace(run[i + 1])) faces++;
            if (UnitLook.bookFace(Kind.OTHER)) faces++; // the aisle
            if (UnitLook.bookFace(Kind.OPAQUE)) faces++; // the wall
            assertEquals(1, faces, "body " + i + " shows books only to the aisle");
        }
    }

    @Test
    void backToBackRangesShowNoBackFace() {
        // a free-standing range: two units back to back, each body faces its own aisle
        assertTrue(UnitLook.bookFace(Kind.OTHER));
        assertFalse(UnitLook.bookFace(Kind.SHELF), "the other unit's back");
    }
}
