package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Where a city module's generic lights go ({@link ModuleLook}), held to the Eco Dome: the plinth light runs on the
 * outside of every plinth-course block of its walls and nowhere else (never across a doorway, never inside), each face
 * has its own place round the building starting at the front middle, and the name plate floats over the middle of the
 * portal's canopy on the controller's axis. Also the drawing tiers, a socket's sample seen over a planter's rim, the
 * pond's ripples kept on its water, and which samples are bees.
 */
class ModuleLookTest {

    private static final CityBuild B = EcoDomeShape.BUILD;

    @Test
    void theCourseRunsOnTheOutsideOfThePlinth() {
        List<int[]> course = ModuleLook.course(B);
        assertTrue(course.size() > 80, "the course runs all round the dome and the portal block: " + course.size());
        Set<Integer> places = new HashSet<>();
        Set<Long> cells = new HashSet<>();
        for (int[] c : course) {
            assertEquals(
                ModuleLook.COURSE,
                ShapeKit.checkedTwin(B.cell(c[0], ModuleLook.COURSE_Y, c[1])),
                "a course block at " + c[0] + "," + c[1]);
            assertEquals(1, Math.abs(c[2]) + Math.abs(c[3]), "one way out");
            int nx = c[0] + c[2], nz = c[1] + c[3];
            assertFalse(ModuleLook.inside(B, nx, nz), "the light faces out of the building at " + c[0] + "," + c[1]);
            assertTrue(places.add(c[4]), "each face has its own place");
            cells.add(ShapeKit.key(c[0], c[1]));
        }
        for (int i = 0; i < course.size(); i++) assertTrue(places.contains(i));
        // every plinth-course block on the building's edge has its light, and no doorway has any
        for (int x = 0; x < B.width(); x++) for (int z = 0; z < B.depth(); z++) {
            boolean edge = EcoDomeShape.onEdge(x, z);
            if (edge && ShapeKit.checkedTwin(B.cell(x, ModuleLook.COURSE_Y, z)) == ModuleLook.COURSE)
                assertTrue(cells.contains(ShapeKit.key(x, z)), "no light at " + x + "," + z);
            if (B.cell(x, ModuleLook.COURSE_Y, z) == ShapeKit.AIR)
                assertFalse(cells.contains(ShapeKit.key(x, z)), "light in the doorway at " + x + "," + z);
        }
        // the gate niche's soft course is the same block: the line runs on under it until the niche opens
        for (int x = EcoDomeShape.NICHE_X0; x <= EcoDomeShape.NICHE_X1; x++) {
            assertEquals('[', B.cell(x, ModuleLook.COURSE_Y, EcoDomeShape.NICHE_Z));
            assertTrue(cells.contains(ShapeKit.key(x, EcoDomeShape.NICHE_Z)), "the niche at " + x);
        }
    }

    @Test
    void theCourseStartsAtTheFrontMiddleAndRunsToTheRight() {
        List<int[]> course = ModuleLook.course(B);
        int n = course.size(), maxX = -1;
        int[] first = null;
        for (int[] c : course) {
            if (c[4] == 0) first = c;
            if (c[2] == 1) maxX = Math.max(maxX, c[0]);
        }
        assertTrue(first != null);
        // the first face is on the front (looking out at -z) just right of the doors
        assertEquals(-1, first[3]);
        assertTrue(first[0] > EcoDomeShape.MID, "right of the middle: " + first[0]);
        // the faces looking out of the right side's middle come about a quarter round
        for (int[] c : course) if (c[2] == 1 && c[0] == maxX && Math.abs(c[1] - EcoDomeShape.TRUNK_Z) <= 1)
            assertTrue(c[4] > n / 8 && c[4] < 3 * n / 8, "a quarter round: " + c[4] + " of " + n);
    }

    @Test
    void thePlateFloatsOverTheCanopy() {
        double[] p = ModuleLook.plate(B);
        int[] ctrl = B.controller();
        assertEquals(ctrl[0] + 0.5, p[0], 1e-9, "on the controller's axis");
        int top = Integer.MIN_VALUE, front = 0;
        for (int[] d : B.decor()) if (d[2] < 0) {
            top = Math.max(top, d[1]);
            front = Math.min(front, d[2]);
        }
        assertEquals(5, top, "the canopy at y5");
        assertTrue(p[1] > top + 1, "over the canopy's top");
        assertTrue(p[2] < 0 && p[2] > front, "over the canopy, in front of the wall");
        assertEquals(-1.0, p[2], 1e-9);
    }

    /**
     * A centrally planned module (0.13.0, the pavilions) has no canopy and no front wall for its controller: its plate
     * floats over the central console, three and a half blocks over the controller, or over the top of a column that
     * stands on the controller (the Mana Fountain's obelisk) so it never floats inside it.
     */
    @Test
    void aCentralPlansPlateFloatsOverItsCentre() {
        CityBuild court = ArcaneCourtShape.BUILD;
        assertTrue(ModuleLook.central(court));
        assertFalse(ModuleLook.central(B), "the Eco Dome's controller is in its front wall");
        double[] p = ModuleLook.plate(court);
        assertEquals(12.5, p[0], 1e-9);
        assertEquals(2 + 3.6, p[1], 1e-9, "the hall is open over the altar");
        assertEquals(12.5, p[2], 1e-9);
        assertEquals(
            1,
            ModuleLook.plates(court)
                .size());
        // a column on the controller: the plate floats over its top
        ShapeKit.Shape s = new ShapeKit.Shape("column", 5, 8, 5);
        for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) s.set(x, 0, z, 'd');
        s.set(2, 1, 2, ShapeKit.CONTROLLER);
        s.controller = new int[] { 2, 1, 2 };
        for (int y = 2; y <= 4; y++) s.set(2, y, 2, 'W');
        CityBuild column = new CityBuild(
            "column",
            s,
            8,
            ShapeKit.blueprint(s, 8),
            new UnitLayout(s),
            Parts.moduleCore(ModuleCores.ARCANE_COURT),
            new CityBuild.Stages() {

                @Override
                public int stage(int x, int y, int z, char ch) {
                    return BuildPlan.M_SHELL;
                }

                @Override
                public int fitRank(int x, int y, int z, char ch) {
                    return 0;
                }
            });
        assertTrue(ModuleLook.central(column));
        double[] q = ModuleLook.plate(column);
        assertEquals(2.5, q[0], 1e-9);
        assertEquals(4 + 1 + ModuleLook.OVER_COLUMN, q[1], 1e-9);
        assertEquals(2.5, q[2], 1e-9);
    }

    /**
     * The other three pavilions' plates (0.13.0): the Blood Covenant Altar's 3.6 over its controller on the dais, open
     * to the oculus; the Echo Hunting Ground's 3.6 over the emitter, open to the sky; the Mana Fountain Array's over
     * the
     * top of the obelisk (its seat at y7), so it never floats inside the spout.
     */
    @Test
    void thePavilionsPlatesFloatOverTheirCentres() {
        double[] blood = ModuleLook.plate(BloodCovenantShape.BUILD);
        assertArrayEquals(new double[] { 12.5, 3 + 3.6, 12.5 }, blood, 1e-9);
        double[] hunting = ModuleLook.plate(HuntingGroundShape.BUILD);
        assertArrayEquals(new double[] { 12.5, 2 + 3.6, 12.5 }, hunting, 1e-9);
        double[] mana = ModuleLook.plate(ManaFountainShape.BUILD);
        assertArrayEquals(new double[] { 12.5, 7 + 1 + ModuleLook.OVER_COLUMN, 12.5 }, mana, 1e-9);
        for (CityBuild b : new CityBuild[] { BloodCovenantShape.BUILD, HuntingGroundShape.BUILD,
            ManaFountainShape.BUILD }) {
            assertTrue(ModuleLook.central(b), b.key());
            assertEquals(
                1,
                ModuleLook.plates(b)
                    .size(),
                b.key());
        }
    }

    /** Near inside or within the near distance of the box, mid within the effect range, far beyond (CITY.md §1.3). */
    @Test
    void theTiers() {
        assertEquals(ModuleLook.NEAR, ModuleLook.tier(true, 0, 24, 128));
        assertEquals(ModuleLook.NEAR, ModuleLook.tier(false, 24, 24, 128));
        assertEquals(ModuleLook.MID, ModuleLook.tier(false, 24.5, 24, 128));
        assertEquals(ModuleLook.MID, ModuleLook.tier(false, 128, 24, 128));
        assertEquals(ModuleLook.FAR, ModuleLook.tier(false, 130, 24, 128), "past the effect range");
        assertEquals(ModuleLook.FAR, ModuleLook.tier(false, 175, 24, 128), "still drawn by the nexus, at 64 quads");
    }

    /**
     * A socket's sample, high in its recess, shows over the basin rim beside it (its slab and its lip) to a player
     * standing on the planter's floor, looking square at the socket from right by the rim out to the near distance of
     * the module drawing (24 blocks): its middle all the way, the whole picture within eight blocks. The recess holds
     * the picture and lies under the column's cap.
     */
    @Test
    void aSocketsSampleShowsOverTheRim() {
        assertTrue(ModuleLook.PICTURE_MID - ModuleLook.PICTURE_HALF >= ModuleLook.RECESS_LOW);
        assertTrue(ModuleLook.PICTURE_MID + ModuleLook.PICTURE_HALF <= ModuleLook.RECESS_HIGH);
        assertTrue(ModuleLook.RECESS_HIGH < 1);
        for (double d = 0.3; d <= 24; d += 0.1) {
            assertTrue(seen(d, ModuleLook.PICTURE_MID), "the middle at " + d);
            assertTrue(seen(d, ModuleLook.PICTURE_MID + ModuleLook.PICTURE_HALF), "the top at " + d);
            if (d <= 8) assertTrue(seen(d, ModuleLook.PICTURE_MID - ModuleLook.PICTURE_HALF), "the foot at " + d);
        }
    }

    /**
     * Whether a point of the picture {@code h} up the socket is seen from an eye {@code d} out from the rim's outer
     * edge: the sight line clears the slab where it meets the socket and the lip's edge nearer the socket.
     */
    private static boolean seen(double d, double h) {
        double eye = ModuleLook.EYE, to = d + 1 + ModuleLook.RECESS_IN;
        double overSlab = eye - (eye - h) * (d + 1) / to, overLip = eye - (eye - h) * (d + ModuleLook.LIP_MAX) / to;
        return overSlab > ModuleLook.BASIN_TOP && overLip > ModuleLook.LIP_TOP;
    }

    /**
     * The pond's water cells and how far a ripple may spread from each: a ring that wide round the cell's middle lies
     * on water all round (never on the rim, the trunk or the moat round it), and every cell leaves room for a ring.
     */
    @Test
    void ripplesStayOnTheWater() {
        List<double[]> pond = ModuleLook.water(B, 1, 0.8);
        assertEquals(28, pond.size(), "the pond's 28 cells of water");
        for (double[] w : pond) {
            assertTrue(w[2] >= 0.45 && w[2] <= 0.8, "room " + w[2] + " at " + w[0] + "," + w[1]);
            for (int k = 0; k < 64; k++) {
                double a = k * Math.PI / 32, x = w[0] + Math.cos(a) * w[2] * 0.999,
                    z = w[1] + Math.sin(a) * w[2] * 0.999;
                assertEquals(
                    '=',
                    B.cell((int) Math.floor(x), 1, (int) Math.floor(z)),
                    "off the water at " + x + "," + z);
            }
        }
        assertTrue(1 + ModuleLook.WATER_TOP < 1.9, "the rings float over the still water's top");
    }

    @Test
    void beesAreKnownByName() {
        assertTrue(ModuleLook.beeName("Forestry:beeDroneGE"));
        assertTrue(ModuleLook.beeName("Forestry:beePrincessGE"));
        assertTrue(ModuleLook.beeName("Forestry:beeQueenGE"));
        assertTrue(ModuleLook.beeName("Forestry:beeLarvaeGE"));
        assertTrue(ModuleLook.beeName("somemod:bee"));
        assertTrue(ModuleLook.beeName("somemod:bee_queen"));
        assertFalse(ModuleLook.beeName("minecraft:beef"));
        assertFalse(ModuleLook.beeName("minecraft:cooked_beef"));
        assertFalse(ModuleLook.beeName("Forestry:beeswax"));
        assertFalse(ModuleLook.beeName("Forestry:beeCombs"));
        assertFalse(ModuleLook.beeName("Forestry:beehouse"));
        assertFalse(ModuleLook.beeName("minecraft:wheat_seeds"));
        assertFalse(ModuleLook.beeName(null));
    }

    /**
     * A socket's sample shows only on the sides turned to the camera (0.15.0): of two opposite sides one at most, none
     * while the camera stands level with the recess's plane or behind it, where the column hides it.
     */
    @Test
    void aSocketShowsOnTheSidesTurnedToTheCamera() {
        int[][] sides = { { 0, -1 }, { 0, 1 }, { -1, 0 }, { 1, 0 } };
        for (int i = 0; i < 200; i++) {
            double a = i * 0.0314159 * 2, d = 0.4 + (i % 7) * 3;
            double dx = d * Math.cos(a), dz = d * Math.sin(a);
            int seen = 0;
            for (int[] o : sides) if (ModuleLook.socketSideFacing(o[0], o[1], dx, dz)) seen++;
            assertTrue(seen <= ModuleLook.SOCKET_SIDES_SEEN, "at most two sides at " + dx + "," + dz);
            assertFalse(
                ModuleLook.socketSideFacing(1, 0, dx, dz) && ModuleLook.socketSideFacing(-1, 0, dx, dz),
                "never two opposite sides");
        }
        // straight in front of the east side, a little off and far away: the east side only
        assertTrue(ModuleLook.socketSideFacing(1, 0, 5, 0.2));
        assertFalse(ModuleLook.socketSideFacing(0, 1, 5, 0.2));
        // level with the picture's plane or behind it: hidden by the column
        assertFalse(ModuleLook.socketSideFacing(1, 0, 0.5 - ModuleLook.RECESS_IN, 3));
        assertFalse(ModuleLook.socketSideFacing(1, 0, -4, 0));
    }

    @Test
    void theSharedDrawingsQuadsAreCountedByTier() {
        // the Eco Dome: 125 plinth faces, one plate, 36 sockets, each seen from two sides at most (0.15.0: the column
        // hides the far ones; CityRender's own sums)
        int faces = ModuleLook.course(B)
            .size();
        assertEquals(125, faces);
        // the forming sweep as CityRender draws it through the 27 x 35 box: a plane and its rim's four lines cut into
        // pieces of 8 blocks, 4 + 5 + 4 + 5
        int sweep = 19;
        assertEquals(sweep, ModuleLook.sweepQuads(B));
        assertEquals(sweep, ModuleLook.baseQuads(B, EcoDomeShape.UNITS, ModuleLook.FAR), "far");
        assertEquals(
            sweep + ModuleLook.PLATE_QUADS + faces,
            ModuleLook.baseQuads(B, EcoDomeShape.UNITS, ModuleLook.MID),
            "mid");
        assertEquals(
            sweep + ModuleLook.PLATE_QUADS + 3 * faces + 36 * ModuleLook.SOCKET_SIDES_SEEN * 3,
            ModuleLook.baseQuads(B, EcoDomeShape.UNITS, ModuleLook.NEAR),
            "near");
        // a 25 x 25 pavilion's sweep: four pieces a side
        CityBuild[] pavilions = { ArcaneCourtShape.BUILD, BloodCovenantShape.BUILD, HuntingGroundShape.BUILD,
            ManaFountainShape.BUILD };
        UnitLayout[] layouts = { ArcaneCourtShape.UNITS, BloodCovenantShape.UNITS, HuntingGroundShape.UNITS,
            ManaFountainShape.UNITS };
        for (int i = 0; i < pavilions.length; i++) {
            assertEquals(17, ModuleLook.sweepQuads(pavilions[i]), pavilions[i].key());
            assertEquals(17, ModuleLook.baseQuads(pavilions[i], layouts[i], ModuleLook.FAR), pavilions[i].key());
        }
        // a module the lift enters and hangs in the air carries two plates
        assertEquals(
            ModuleLook.sweepQuads(DomeGeneratorShape.BUILD) + 2 * ModuleLook.PLATE_QUADS
                + ModuleLook.course(DomeGeneratorShape.BUILD)
                    .size(),
            ModuleLook.baseQuads(DomeGeneratorShape.BUILD, DomeGeneratorShape.UNITS, ModuleLook.MID));
    }
}
