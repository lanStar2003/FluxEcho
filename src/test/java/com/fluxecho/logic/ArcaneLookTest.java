package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The Arcane Court's drawing (0.13.0, CITY.md §4.3 Visuals): the ink rings orbit the spire clear of it, at their own
 * heights, tilts and speeds, with three brush gaps each; the crystal floats where the finial dissolved; the runes lie
 * on the floor's lit ring; the motes stay in the hall's air and the bubbles over the cauldrons' rims; the logograms
 * sink inside the hollow spire; every motion repeats with its period; the court keeps CITY.md §1.3's budgets.
 */
class ArcaneLookTest {

    private static final CityBuild B = ArcaneCourtShape.BUILD;

    @Test
    void theInkRingsOrbitClearOfTheSpire() {
        for (int r = 0; r < ArcaneLook.RINGS; r++) {
            int period = ArcaneLook.ringPeriod(r);
            for (int k = 0; k < 64; k++) {
                double t = period * k / 64.0 + 0.37;
                for (int i = 0; i < 96; i++) {
                    double s = i / 96.0;
                    double[] p = ArcaneLook.inkPoint(r, s, t);
                    double room = LookCells.clearance(B, p[0], p[1], p[2], LookCells.OPEN, 2);
                    assertTrue(
                        room >= ArcaneLook.inkHalf(r, s) + 0.3,
                        "ring " + r + " at " + s + ", t " + t + ": " + room + " from the spire");
                    assertTrue(p[1] > ArcaneCourtShape.WALL_TOP + 1, "round the spire, over the hall");
                    assertTrue(p[1] < ArcaneCourtShape.HEIGHT, "under the finial's top");
                    double out = Math.hypot(p[0] - ArcaneLook.AXIS, p[2] - ArcaneLook.AXIS);
                    assertTrue(out <= ArcaneCourtShape.MID + 0.5, "over the court's own ground: " + out);
                }
            }
        }
    }

    @Test
    void theRingsDifferInHeightTiltAndSpeed() {
        for (int a = 0; a < ArcaneLook.RINGS; a++) for (int b = a + 1; b < ArcaneLook.RINGS; b++) {
            assertTrue(ArcaneLook.RING_Y[a] < ArcaneLook.RING_Y[b], "higher one above another");
            assertTrue(ArcaneLook.RING_R[a] > ArcaneLook.RING_R[b], "narrower as they rise, as the spire does");
            assertTrue(ArcaneLook.RING_TILT[a] != ArcaneLook.RING_TILT[b], "tilts");
            assertTrue(Math.abs(ArcaneLook.RING_SPIN_T[a]) != Math.abs(ArcaneLook.RING_SPIN_T[b]), "speeds");
        }
        assertTrue(
            ArcaneLook.RING_SPIN_T[0] > 0 && ArcaneLook.RING_SPIN_T[1] < 0,
            "the middle one turns the other way");
        // the rings never meet: one always further out from the axis than the one above it
        for (int r = 0; r + 1 < ArcaneLook.RINGS; r++) assertTrue(
            ArcaneLook.RING_R[r] * Math.cos(ArcaneLook.RING_TILT[r]) > ArcaneLook.RING_R[r + 1] + 1,
            "rings " + r + " and " + (r + 1));
    }

    @Test
    void eachRingHasThreeBrushGaps() {
        for (int r = 0; r < ArcaneLook.RINGS; r++) {
            int n = 2000, gaps = 0;
            boolean inGap = ArcaneLook.inkHalf(r, (n - 1) / (double) n) == 0;
            double most = 0;
            for (int i = 0; i < n; i++) {
                double h = ArcaneLook.inkHalf(r, i / (double) n);
                assertTrue(h >= 0 && h <= ArcaneLook.INK_HALF[r] + 1e-12, "a brush's width");
                most = Math.max(most, h);
                if (h == 0 && !inGap) gaps++;
                inGap = h == 0;
            }
            assertEquals(3, gaps, "ring " + r);
            assertTrue(most > ArcaneLook.INK_HALF[r] * 0.9, "full pressure somewhere");
            int drawn = ArcaneLook.inkDrawn(r, ModuleLook.NEAR);
            assertTrue(drawn < ArcaneLook.INK_PIECES[ModuleLook.NEAR] && drawn > 36, "pieces in the gaps left out");
        }
    }

    @Test
    void theMotionsRepeat() {
        for (double t : new double[] { 0, 123.5, 98765.25 }) {
            for (int r = 0; r < ArcaneLook.RINGS; r++) for (double s : new double[] { 0, 0.3, 0.77 }) {
                double[] a = ArcaneLook.inkPoint(r, s, t), b = ArcaneLook.inkPoint(r, s, t + ArcaneLook.ringPeriod(r));
                for (int i = 0; i < 3; i++) assertEquals(a[i], b[i], 1e-6, "ring " + r);
                assertTrue(distance(a, ArcaneLook.inkPoint(r, s, t + 40)) > 0.05, "and moves in between: ring " + r);
            }
            for (int g = 0; g < ArcaneLook.GLYPHS; g++) {
                double[] a = ArcaneLook.glyph(g, t), b = ArcaneLook.glyph(g, t + ArcaneLook.GLYPH_T);
                for (int i = 0; i < a.length; i++) assertEquals(a[i], b[i], 1e-6, "logogram " + g);
            }
            for (int i = 0; i < ArcaneLook.RUNES; i++) for (int k = 0; k < ArcaneLook.STROKES; k++) {
                double[] a = ArcaneLook.runeStroke(i, k, t),
                    b = ArcaneLook.runeStroke(i, k, t + ArcaneLook.RUNE_TURN_T);
                for (int j = 0; j < 4; j++) assertEquals(a[j], b[j], 1e-6, "rune " + i);
            }
            assertEquals(ArcaneLook.crystalY(t), ArcaneLook.crystalY(t + ArcaneLook.CRYSTAL_BOB_T), 1e-9);
            assertEquals(
                Math.cos(ArcaneLook.crystalTurn(t)),
                Math.cos(ArcaneLook.crystalTurn(t + ArcaneLook.CRYSTAL_TURN_T)),
                1e-9);
            // the same time, the same frame: nothing is counted from one call to the next
            for (int p = 0; p < ArcaneLook.pillars()
                .size(); p++) {
                double[] a = ArcaneLook.pillarMote(p, 2, t), b = ArcaneLook.pillarMote(p, 2, t);
                for (int i = 0; i < a.length; i++) assertEquals(a[i], b[i], 0);
            }
        }
    }

    @Test
    void theCrystalFloatsWhereTheFinialDissolved() {
        assertTrue(ArcaneCourtShape.DISSOLVES.contains("C") && ArcaneCourtShape.DISSOLVES.contains("Z"));
        String open = LookCells.OPEN + ArcaneCourtShape.DISSOLVES;
        for (int k = 0; k < 40; k++) {
            double t = ArcaneLook.CRYSTAL_BOB_T * k / 40.0;
            double y = ArcaneLook.crystalY(t);
            assertTrue(y - ArcaneLook.CRYSTAL_H >= ArcaneCourtShape.CAP_Y + 1, "over the lantern's cap");
            assertTrue(y + ArcaneLook.CRYSTAL_H <= ArcaneCourtShape.HEIGHT, "within the court's box");
            for (double[] o : new double[][] { { 0, ArcaneLook.CRYSTAL_H, 0 }, { 0, -ArcaneLook.CRYSTAL_H, 0 },
                { ArcaneLook.CRYSTAL_R, 0, 0 }, { -ArcaneLook.CRYSTAL_R, 0, 0 }, { 0, 0, ArcaneLook.CRYSTAL_R },
                { 0, 0, -ArcaneLook.CRYSTAL_R } }) {
                double x = ArcaneLook.AXIS + o[0], yy = y + o[1], z = ArcaneLook.AXIS + o[2];
                assertTrue(open.indexOf(LookCells.at(B, x, yy, z)) >= 0, "in the finial's air at " + yy);
            }
        }
        for (int y = ArcaneCourtShape.FINIAL_FROM; y <= ArcaneCourtShape.FINIAL_TO; y++) assertTrue(
            ArcaneCourtShape.DISSOLVES.indexOf(ArcaneCourtShape.cell(ArcaneCourtShape.MID, y, ArcaneCourtShape.MID))
                >= 0);
    }

    @Test
    void theRunesLieOnTheFloorsLitRing() {
        int lit = 0, all = 0;
        for (int i = 0; i < 360; i++) {
            double a = Math.toRadians(i);
            for (double r : new double[] { ArcaneLook.RUNE_IN, ArcaneLook.RUNE_R, ArcaneLook.RUNE_OUT }) {
                double x = ArcaneLook.AXIS + r * Math.cos(a), z = ArcaneLook.AXIS + r * Math.sin(a);
                onTheOpenFloor(x, z);
                if (r == ArcaneLook.RUNE_R) {
                    all++;
                    if (LookCells.at(B, x, 0, z) == '+') lit++;
                }
            }
        }
        assertTrue(lit >= all * 0.7, "mostly over the rune ring's lit tiles: " + lit + " of " + all);
        for (double t : new double[] { 0, 777, 1999.5 })
            for (int i = 0; i < ArcaneLook.RUNES; i++) for (int k = 0; k < ArcaneLook.STROKES; k++) {
                double[] s = ArcaneLook.runeStroke(i, k, t);
                onTheOpenFloor(s[0], s[1]);
                onTheOpenFloor(s[2], s[3]);
                double r = Math.hypot(s[0] - ArcaneLook.AXIS, s[1] - ArcaneLook.AXIS);
                assertTrue(r > ArcaneLook.RUNE_IN - 0.05 && r < ArcaneLook.RUNE_OUT + 0.05, "between the lines");
            }
        assertTrue(ArcaneLook.RUNE_Y > 1 && ArcaneLook.RUNE_Y < 1.05, "just over the floor's top");
    }

    /** A point of the floor's top: soft floor under it, the air over it open (no unit or stand covers it). */
    private static void onTheOpenFloor(double x, double z) {
        char floor = LookCells.at(B, x, 0, z), over = LookCells.at(B, x, 1, z);
        assertTrue(floor == '.' || floor == '+', "on the soft floor at " + x + "," + z + ": '" + floor + "'");
        assertEquals(ShapeKit.NONE, over, "nothing standing on it at " + x + "," + z);
    }

    @Test
    void theMotesStayInTheHallsAir() {
        assertEquals(
            8,
            ArcaneLook.pillars()
                .size());
        for (int p = 0; p < 8; p++) for (int k = 0; k < ArcaneLook.MOTES; k++) for (int j = 0; j < 400; j++) {
            double t = j * 3.7;
            double[] m = ArcaneLook.pillarMote(p, k, t);
            assertTrue(
                LookCells.clearance(B, m[0], m[1], m[2], LookCells.OPEN, 1) >= 0.12,
                "pillar " + p + " mote " + k + " at t " + t + ": " + m[0] + "," + m[1] + "," + m[2]);
            assertTrue(m[3] >= 0 && m[3] <= 1);
            assertTrue(m[1] < ArcaneCourtShape.WALL_TOP, "in the hall");
        }
        // each mote leaves its own pillar's face, into the hall
        for (int p = 0; p < 8; p++) {
            UnitLayout.Unit u = ArcaneLook.pillars()
                .get(p);
            double[] start = ArcaneLook.pillarMote(p, 0, 0);
            double[] later = ArcaneLook.pillarMote(p, 0, 40);
            double in0 = (start[0] - ArcaneLook.AXIS) * u.face[0] + (start[2] - ArcaneLook.AXIS) * u.face[1];
            double in1 = (later[0] - ArcaneLook.AXIS) * u.face[0] + (later[2] - ArcaneLook.AXIS) * u.face[1];
            assertTrue(in0 < 0, "the face looks at the axis");
            assertTrue(in1 > in0 || later[3] < start[3], "drifting in, or a new one starting");
        }
    }

    @Test
    void theCauldronsBubbleOverTheirRims() {
        assertEquals(
            4,
            ArcaneLook.cauldrons()
                .size());
        for (int c = 0; c < 4; c++) for (int k = 0; k < ArcaneLook.BUBBLES; k++) for (int j = 0; j < 300; j++) {
            double t = j * 1.3;
            double[] b = ArcaneLook.bubble(c, k, t);
            assertEquals('u', LookCells.at(B, b[0], 1, b[2]), "over a rim cell");
            assertTrue(b[1] >= 1 + ModuleLook.LIP_TOP - 1e-9, "over the lip");
            assertTrue(b[1] <= 1 + ModuleLook.LIP_TOP + ArcaneLook.BUBBLE_RISE + 1e-9, "bursting low");
            assertEquals(ShapeKit.NONE, LookCells.at(B, b[0], 2, b[2]), "the air over the rim");
            assertTrue(b[4] >= 0 && b[4] <= 1 + 1e-9);
        }
    }

    @Test
    void theLogogramsSinkInsideTheHollowSpire() {
        for (int g = 0; g < ArcaneLook.GLYPHS; g++) for (int j = 0; j < 120; j++) {
            double t = ArcaneLook.GLYPH_T * j / 120.0 + 0.5;
            double[] gl = ArcaneLook.glyph(g, t);
            assertTrue(gl[1] <= ArcaneLook.GLYPH_TOP && gl[1] >= ArcaneLook.GLYPH_BOTTOM);
            assertTrue(gl[1] > ArcaneCourtShape.WALL_TOP + 1, "in the spire, over the hall");
            for (int i = 0; i < 48; i++) {
                double a = 2 * Math.PI * i / 48;
                double[] p = ArcaneLook.glyphPoint(gl, a, gl[3] + ArcaneLook.TENDRIL + ArcaneLook.GLYPH_HALF);
                double room = LookCells.clearance(B, p[0], p[1], p[2], LookCells.OPEN, 2);
                assertTrue(room >= 0.3, "logogram " + g + " at t " + t + ": " + room + " from the spire's wall");
            }
        }
        for (int g = 0; g < ArcaneLook.GLYPHS; g++) {
            int drawn = ArcaneLook.glyphDrawn(g);
            assertTrue(drawn < ArcaneLook.GLYPH_PIECES && drawn >= ArcaneLook.GLYPH_PIECES - 6, "two brush gaps");
            for (int k = 0; k < ArcaneLook.TENDRILS; k++)
                assertTrue(ArcaneLook.tendrilReach(g, k) <= ArcaneLook.TENDRIL && ArcaneLook.tendrilReach(g, k) > 0);
        }
    }

    @Test
    void theCourtKeepsItsBudget() {
        // the shared drawing as drawn: the forming sweep through the 25 x 25 box is a plane and four rims of four
        // pieces
        assertEquals(17, ModuleLook.sweepQuads(B));
        assertEquals(17, ModuleLook.baseQuads(B, ArcaneCourtShape.UNITS, ModuleLook.FAR));
        int[] budget = { 2000, 300, 64 };
        for (int tier = ModuleLook.NEAR; tier <= ModuleLook.FAR; tier++) {
            int base = ModuleLook.baseQuads(B, ArcaneCourtShape.UNITS, tier);
            for (boolean docked : new boolean[] { false, true }) for (boolean on : new boolean[] { false, true }) {
                if (on && !docked) continue;
                int own = ArcaneLook.quads(tier, docked, on);
                assertTrue(
                    base + own <= budget[tier],
                    "tier " + tier + (docked ? " docked" : "") + (on ? " powered" : "") + ": " + base + " + " + own);
            }
        }
        // the count is the drawing's: the rings' pieces less those wholly in a gap, the inside only near
        int rings = 0;
        for (int r = 0; r < ArcaneLook.RINGS; r++) rings += ArcaneLook.inkDrawn(r, ModuleLook.FAR);
        assertEquals(ArcaneLook.CRYSTAL_QUADS + 1 + rings, ArcaneLook.quads(ModuleLook.FAR, true, true));
        assertEquals(ArcaneLook.CRYSTAL_QUADS + 1, ArcaneLook.quads(ModuleLook.FAR, false, false));
        assertTrue(ArcaneLook.quads(ModuleLook.NEAR, true, true) > ArcaneLook.quads(ModuleLook.NEAR, true, false));
    }

    private static double distance(double[] a, double[] b) {
        return Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2]));
    }
}
