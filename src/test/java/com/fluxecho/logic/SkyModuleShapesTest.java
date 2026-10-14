package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.CRC32;

import org.junit.jupiter.api.Test;

/**
 * The sky ring's four modules against their generator ({@code city/gen/b_sky.py}, 0.16.0): the same cells (a checksum
 * of every layer), the same counts, mirror-symmetric, the platform of floating foundation railed round but for the
 * catwalk's mouth, the controller where the generator puts it, the units in the generator's order (the Sky Sail
 * Array's masts as their plinth and socket only, 0.16.0's generator; the Droplet Hangar's cradles of the new kind
 * {@code droplet_cradle}), the generator's walk from the catwalk's mouth, a stage for every cell, and the cells that
 * dissolve.
 */
class SkyModuleShapesTest {

    /** One module and what its generator says of it. */
    private static final class Want {

        final String key;
        final ShapeKit.Shape shape;
        final Blueprint blueprint;
        final CityBuild build;
        final long crc;
        final Object[] counts;
        final int checked, air, steps, reached, height, top;
        final int[] controller;

        Want(String key, ShapeKit.Shape shape, Blueprint blueprint, CityBuild build, long crc, Object[] counts,
            int checked, int air, int steps, int reached, int height, int top, int[] controller) {
            this.key = key;
            this.shape = shape;
            this.blueprint = blueprint;
            this.build = build;
            this.crc = crc;
            this.counts = counts;
            this.checked = checked;
            this.air = air;
            this.steps = steps;
            this.reached = reached;
            this.height = height;
            this.top = top;
            this.controller = controller;
        }
    }

    private static List<Want> all() {
        List<Want> out = new ArrayList<>();
        out.add(
            new Want(
                WeatherStationShape.KEY,
                WeatherStationShape.SHAPE,
                WeatherStationShape.BLUEPRINT,
                WeatherStationShape.BUILD,
                709618030L,
                new Object[] { 'f', 357, 'r', 73, '-', 57, 'W', 42, 'P', 25, 'G', 24, 'n', 24, 'w', 14, 'K', 5, 'o', 4,
                    'R', 2, 'q', 2, 'C', 1, 'Z', 1, '~', 1 },
                575,
                57,
                632,
                262,
                14,
                13,
                new int[] { 10, 2, 1 }));
        out.add(
            new Want(
                SkySailsShape.KEY,
                SkySailsShape.SHAPE,
                SkySailsShape.BLUEPRINT,
                SkySailsShape.BUILD,
                722889937L,
                new Object[] { 'f', 357, 'r', 75, 'q', 62, '-', 57, 'W', 51, 'G', 28, 'n', 26, 'w', 16, 'c', 6, 'o', 6,
                    'p', 6, 'h', 2, 'K', 1, '~', 1 },
                637,
                57,
                694,
                257,
                15,
                13,
                new int[] { 10, 2, 1 }));
        out.add(
            new Want(
                ObservatoryShape.KEY,
                ObservatoryShape.SHAPE,
                ObservatoryShape.BLUEPRINT,
                ObservatoryShape.BUILD,
                3382660860L,
                new Object[] { 'f', 357, 'W', 336, 'n', 77, 'r', 75, 'w', 57, '-', 48, 'L', 44, 'G', 13, 'o', 7, 'p', 7,
                    'P', 5, 'K', 2, 'q', 2, 'Z', 1, '~', 1 },
                984,
                48,
                1032,
                142,
                13,
                11,
                new int[] { 10, 2, 1 }));
        out.add(
            new Want(
                DropletHangarShape.KEY,
                DropletHangarShape.SHAPE,
                DropletHangarShape.BLUEPRINT,
                DropletHangarShape.BUILD,
                3087750020L,
                new Object[] { 'f', 357, 'r', 77, 'u', 64, 'R', 12, 'o', 8, 'K', 1, '~', 1 },
                520,
                0,
                520,
                207,
                7,
                5,
                new int[] { 10, 2, 2 }));
        return out;
    }

    @Test
    void theCellsAreTheGenerators() {
        for (Want w : all()) {
            CRC32 crc = new CRC32();
            crc.update(
                String.join("\n", ShapeKit.layers(w.shape))
                    .getBytes(StandardCharsets.UTF_8));
            // zlib.crc32 of the generator's layers, y0 first, rows from the front, each row x 0..20
            assertEquals(w.crc, crc.getValue(), w.key);
        }
    }

    @Test
    void theGeneratorsCountsAndWalk() {
        for (Want w : all()) {
            Map<Character, Integer> want = new TreeMap<>();
            for (int i = 0; i < w.counts.length; i += 2) want.put((Character) w.counts[i], (Integer) w.counts[i + 1]);
            assertEquals(want, w.shape.counts(), w.key);
            assertEquals(w.checked, w.shape.checkedCount(), w.key);
            assertEquals(0, w.shape.softCount(), w.key + ": every cell of a sky module is checked");
            assertEquals(w.air, w.shape.airCount(), w.key);
            assertEquals(0, w.shape.decor.size(), w.key);
            assertEquals(w.steps, w.shape.steps(), w.key);
            ShapeKit.Summary sum = ShapeKit.summary(w.shape, SkyRing.walkStarts());
            assertTrue(sum.symmetric, w.key);
            // the generator's walk from the catwalk's mouth over the floating deck (no ground round it)
            assertEquals(w.reached, sum.reached, w.key);
            assertTrue(sum.controller, w.key + ": the controller is reached at eye height");
            assertTrue(sum.unreachable.isEmpty(), w.key + ": " + sum.unreachable);
            assertTrue(sum.idleTreads.isEmpty(), w.key);
        }
    }

    @Test
    void theBoxTheControllerAndTheBlueprint() {
        for (Want w : all()) {
            assertEquals(21, w.shape.W, w.key);
            assertEquals(21, w.shape.D, w.key);
            assertEquals(w.height, w.shape.H, w.key);
            assertArrayEquals(w.controller, w.shape.controller, w.key);
            assertArrayEquals(w.controller, w.build.controller(), w.key);
            assertEquals(ShapeKit.CONTROLLER, w.shape.get(w.controller[0], w.controller[1], w.controller[2]));
            assertEquals('K', w.shape.get(w.controller[0], 1, w.controller[2]), w.key + ": on its console");
            int top = 0;
            for (int y = 0; y < w.shape.H; y++) for (int z = 0; z < 21; z++)
                for (int x = 0; x < 21; x++) if (w.shape.get(x, y, z) != ShapeKit.NONE) top = Math.max(top, y);
            assertEquals(w.top, top, w.key);
            assertEquals(w.height, w.blueprint.height(), w.key);
            for (int y = 0; y < w.shape.H; y++) for (int z = 0; z < 21; z++) for (int x = 0; x < 21; x++) {
                char ch = w.shape.get(x, y, z);
                int[] c = w.build.cellOf(x, y, z);
                assertEquals(ch, w.blueprint.at(c[0], c[1], c[2]), w.key + " " + x + "," + y + "," + z);
            }
            assertFalse(w.shape.groundOutside, w.key + ": the deck floats, nothing round it to stand on");
        }
    }

    @Test
    void everyPlatformIsTheFloatingOctagonRailedButForItsMouth() {
        for (Want w : all()) {
            for (int x = 0; x < 21; x++) for (int z = 0; z < 21; z++) {
                boolean in = ShapeKit.octo(x - 10, z - 10, 10, 14);
                assertEquals(in, SkyRing.onPlatform(x, z));
                assertEquals(in ? 'f' : ' ', w.shape.get(x, 0, z), w.key + " " + x + "," + z);
                char rail = w.shape.get(x, 1, z);
                boolean mouth = z <= 1 && x >= 9 && x <= 11;
                if (!SkyRing.onEdge(x, z)) assertTrue(rail != 'r', w.key + ": a rail off the edge");
                else if (mouth) assertTrue(rail != 'r', w.key + ": the catwalk's mouth is open at " + x + "," + z);
            }
            // the mouth: open at y1 and y2, the catwalk walks in
            for (int x = 9; x <= 11; x++)
                for (int y = 1; y <= 2; y++) assertEquals(ShapeKit.NONE, w.shape.get(x, y, 0), w.key);
        }
    }

    @Test
    void theUnitsInTheGeneratorsOrder() {
        // the weather consoles round the mast at (10, 12): (+3, 0), (-3, 0), (0, +3), (0, -3)
        int[][] weather = { { 13, 12 }, { 7, 12 }, { 10, 15 }, { 10, 9 } };
        checkPairs(WeatherStationShape.UNITS, "weather-", "weather", weather, 'K');
        int[][] masts = { { 4, 10 }, { 16, 10 }, { 4, 15 }, { 16, 15 }, { 7, 18 }, { 13, 18 } };
        checkPairs(SkySailsShape.UNITS, "mast-", "mast", masts, 'p');
        // a mast's posts and crown are plain cells (0.16.0's generator): so no unit cell leaves the world over high
        // ground
        for (int[] m : masts) {
            for (int y = 3; y <= 12; y++) assertEquals('q', SkySailsShape.SHAPE.get(m[0], y, m[1]));
            assertEquals('c', SkySailsShape.SHAPE.get(m[0], 13, m[1]));
        }
        int[][] lenses = { { 16, 12 }, { 4, 12 }, { 14, 16 }, { 6, 16 }, { 10, 18 }, { 14, 8 }, { 6, 8 } };
        checkPairs(ObservatoryShape.UNITS, "lens-", "lens", lenses, 'p');
        int[][] cradles = { { 4, 8 }, { 16, 8 }, { 4, 13 }, { 16, 13 }, { 7, 17 }, { 13, 17 }, { 7, 4 }, { 13, 4 } };
        UnitLayout u = DropletHangarShape.UNITS;
        assertEquals(8, u.size());
        assertEquals(8, u.slots());
        for (int i = 0; i < cradles.length; i++) {
            UnitLayout.Unit unit = u.units()
                .get(i);
            assertEquals("cradle-" + (i + 1), unit.name);
            // a kind of its own: "cradle" is the Blood Covenant Altar's orb cradle
            assertEquals("droplet_cradle", unit.kind);
            assertEquals(1, unit.slots);
            assertEquals(9, unit.cells.size());
            int k = 0;
            for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) {
                int[] c = unit.cells.get(k++);
                assertArrayEquals(new int[] { cradles[i][0] + a, 1, cradles[i][1] + b }, c);
                assertEquals(a == 0 && b == 0 ? 'o' : 'u', DropletHangarShape.SHAPE.get(c[0], c[1], c[2]));
            }
        }
    }

    /** Units of a plinth (or console) at y1 and a socket at y2, in order, one slot each. */
    private static void checkPairs(UnitLayout u, String prefix, String kind, int[][] at, char base) {
        assertEquals(at.length, u.size());
        assertEquals(at.length, u.slots());
        for (int i = 0; i < at.length; i++) {
            UnitLayout.Unit unit = u.units()
                .get(i);
            assertEquals(prefix + (i + 1), unit.name);
            assertEquals(kind, unit.kind);
            assertEquals(1, unit.slots);
            assertEquals(2, unit.cells.size());
            assertArrayEquals(new int[] { at[i][0], 1, at[i][1] }, unit.cells.get(0));
            assertArrayEquals(new int[] { at[i][0], 2, at[i][1] }, unit.cells.get(1));
        }
    }

    @Test
    void everyCellHasAStage() {
        for (Want w : all()) {
            java.util.Set<Long> units = new java.util.HashSet<>();
            for (List<int[]> cells : w.build.units())
                for (int[] c : cells) units.add(ShapeKit.Shape.pos(c[0], c[1], c[2]));
            int[] per = new int[SkyRing.K_STAGES];
            for (int y = 0; y < w.shape.H; y++) for (int z = 0; z < 21; z++) for (int x = 0; x < 21; x++) {
                char ch = w.shape.get(x, y, z);
                if (ch == ShapeKit.NONE || units.contains(ShapeKit.Shape.pos(x, y, z))) continue;
                int s = w.build.stage(x, y, z, ch);
                assertTrue(s > SkyRing.K_CLEAR && s < SkyRing.K_STAGES && s != SkyRing.K_DECOR, w.key + " " + ch);
                per[s]++;
                if (y == 0) assertEquals(SkyRing.K_FLOAT, s, w.key);
                if (ch == ShapeKit.CONTROLLER) assertEquals(SkyRing.K_COMMISSION, s);
                if (ch == 'r') assertEquals(SkyRing.K_FLOAT, s, w.key + ": the platform's rail floats with it");
                if (s == SkyRing.K_FIT) assertTrue(w.build.fitRank(x, y, z, ch) != 0, w.key);
            }
            assertEquals(1, per[SkyRing.K_COMMISSION], w.key);
            assertEquals(
                357 + w.shape.counts()
                    .get('r'),
                per[SkyRing.K_FLOAT],
                w.key + ": the platform and its rail");
        }
        // the hangar has no walls; the others' portals and walls are SHELL, their roofs ROOF
        assertEquals(SkyRing.K_ROOF, DropletHangarShape.stage(10, 5, 1, 'R'));
        assertEquals(SkyRing.K_SHELL, WeatherStationShape.stage(6, 1, 1, 'w'));
        assertEquals(SkyRing.K_ROOF, WeatherStationShape.stage(6, 5, 1, 'n'));
        assertEquals(SkyRing.K_FIT, WeatherStationShape.stage(10, 13, 12, 'Z'));
        assertEquals(SkyRing.K_ROOF, ObservatoryShape.stage(10, 11, 12, 'G'));
        assertEquals(SkyRing.K_FIT, ObservatoryShape.stage(10, 4, 12, 'P'));
        assertEquals(SkyRing.K_FIT, SkySailsShape.stage(4, 13, 10, 'c'));
    }

    @Test
    void theCellsThatDissolve() {
        // the weather station's mast (its column alone) gives way to the vanes; the pylons stay
        int mast = 0;
        for (int y = 0; y < WeatherStationShape.HEIGHT; y++) for (int z = 0; z < 21; z++) for (int x = 0; x < 21; x++) {
            char ch = WeatherStationShape.SHAPE.get(x, y, z);
            boolean dissolving = WeatherStationShape.DISSOLVES.indexOf(ch) >= 0;
            if (WeatherStationShape.mast(x, y, z)) {
                assertTrue(dissolving, x + "," + y + "," + z);
                mast++;
            }
        }
        assertEquals(13, mast);
        assertEquals('P', WeatherStationShape.SHAPE.get(4, 7, 17), "a pylon's pillar");
        assertFalse(WeatherStationShape.mast(4, 7, 17));
        assertEquals('R', WeatherStationShape.SHAPE.get(16, 8, 17));
        // the observatory's whole pier, its console, pillars and seat (CITY.md §4.12); the portal's console under the
        // controller, of the same character, is no pier's and stays
        for (int y = 1; y <= 7; y++) assertTrue(ObservatoryShape.pier(10, y, 12));
        assertFalse(ObservatoryShape.pier(10, 8, 12));
        assertEquals("KPZ", ObservatoryShape.DISSOLVES);
        assertEquals("KPZ", ObservatoryShape.PASSES);
        assertEquals('K', ObservatoryShape.SHAPE.get(10, 1, 12));
        assertEquals('K', ObservatoryShape.SHAPE.get(10, 1, 1), "the portal's console");
        assertFalse(ObservatoryShape.pier(10, 1, 1));
        for (int y = 0; y < ObservatoryShape.HEIGHT; y++) for (int z = 0; z < 21; z++) for (int x = 0; x < 21; x++) {
            char ch = ObservatoryShape.SHAPE.get(x, y, z);
            if (ch == 'P' || ch == 'Z')
                assertTrue(ObservatoryShape.pier(x, y, z), "every pillar and seat is the pier's");
        }
        // the hangar's canopy ring; the sails keep everything
        assertEquals("R", DropletHangarShape.DISSOLVES);
        assertEquals("", SkySailsShape.DISSOLVES);
    }
}
