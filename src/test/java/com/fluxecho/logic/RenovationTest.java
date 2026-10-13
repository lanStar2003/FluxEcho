package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;

import org.junit.jupiter.api.Test;

/**
 * The one-time renovations of built city modules (0.14.1): the Eco Dome's revision 1 turns a dome as 0.11.0 to
 * 0.14.0 built it into the dome as it is built now, touching one soft cell and only when it holds exactly the old
 * grass; every other kind has nothing to do.
 */
class RenovationTest {

    /** A world in the module's frame: parts by cell, the cells whose chunk is not loaded, and what was placed. */
    private static final class World implements Renovation.Cells {

        final Map<Long, Integer> parts = new HashMap<>();
        final Set<Long> unloaded = new HashSet<>();
        int placed;

        static long k(int x, int y, int z) {
            return ShapeKit.Shape.pos(x, y, z);
        }

        @Override
        public boolean loaded(int x, int y, int z) {
            return !unloaded.contains(k(x, y, z));
        }

        @Override
        public boolean holds(int x, int y, int z, int part) {
            return parts.getOrDefault(k(x, y, z), Parts.AIR) == part;
        }

        @Override
        public void place(int x, int y, int z, int part) {
            parts.put(k(x, y, z), part);
            placed++;
        }
    }

    @Test
    void theEcoDomesFirstRevisionOpensTheCrownStair() {
        assertEquals(1, Renovation.current(EcoDomeShape.KEY));
        List<Renovation.Change> due = Renovation.due(EcoDomeShape.KEY, 0);
        assertEquals(1, due.size());
        Renovation.Change c = due.get(0);
        assertEquals(1, c.revision);
        assertEquals(12, c.x);
        assertEquals(EcoDomeShape.CROWN_Y, c.y);
        assertEquals(22, c.z);
        assertEquals(Parts.GRASS, c.from);
        assertEquals(Parts.AIR, c.to);
        assertTrue(
            Renovation.due(EcoDomeShape.KEY, 1)
                .isEmpty());
        for (String kind : ModuleCores.KEYS) if (!kind.equals(EcoDomeShape.KEY)) {
            assertEquals(0, Renovation.current(kind), kind);
            assertTrue(
                Renovation.due(kind, 0)
                    .isEmpty(),
                kind);
        }
    }

    /**
     * The dome as 0.11.0 to 0.14.0 built it (its generator's checksum then) is today's dome with grass in the changed
     * cell, a soft cell the structure check never looked at: the renovation makes one into the other and built domes
     * keep forming.
     */
    @Test
    void itTurnsTheOldDomeIntoTodaysAndTouchesNothingChecked() {
        ShapeKit.Shape now = EcoDomeShape.SHAPE;
        ShapeKit.Shape old = new ShapeKit.Shape("eco_dome_0140", now.W, now.H, now.D);
        for (int y = 0; y < now.H; y++)
            for (int z = 0; z < now.D; z++) for (int x = 0; x < now.W; x++) old.set(x, y, z, now.get(x, y, z));
        for (Renovation.Change c : Renovation.due(EcoDomeShape.KEY, 0)) {
            assertEquals(ShapeKit.NONE, now.get(c.x, c.y, c.z), "today's dome leaves the cell empty");
            assertEquals(c.to, Parts.AIR);
            old.set(c.x, c.y, c.z, ',');
            assertEquals(c.from, ShapeKit.partOf(','));
            assertTrue(ShapeKit.soft(','), "a soft cell: the structure check never looked at it");
            int[] b = EcoDomeShape.cellOf(c.x, c.y, c.z);
            assertEquals(' ', EcoDomeShape.BLUEPRINT.at(b[0], b[1], b[2]), "and the blueprint has nothing there");
        }
        CRC32 crc = new CRC32();
        crc.update(
            String.join("\n", ShapeKit.layers(old))
                .getBytes(StandardCharsets.UTF_8));
        assertEquals(2037949574L, crc.getValue(), "0.14.0's dome");
    }

    @Test
    void onlyTheExactOldBlockGivesWay() {
        // a dome built before: its grass goes, and it is brought to revision 1
        World built = new World();
        built.parts.put(World.k(12, 11, 22), Parts.GRASS);
        built.parts.put(World.k(11, 11, 22), Parts.GRASS);
        assertEquals(1, Renovation.apply(EcoDomeShape.KEY, 0, built));
        assertEquals(Parts.AIR, (int) built.parts.get(World.k(12, 11, 22)));
        assertEquals(Parts.GRASS, (int) built.parts.get(World.k(11, 11, 22)), "the rest of the crown's grass stays");
        assertEquals(1, built.placed);
        // once is enough
        assertEquals(1, Renovation.apply(EcoDomeShape.KEY, 1, built));
        assertEquals(1, built.placed);

        // something a player put there, or a dome built by today's plan, is left alone and still brought up
        for (int part : new int[] { Parts.DIRT, Parts.deck(Parts.D_DECK), Parts.fitting(Parts.F_TREAD), Parts.AIR }) {
            World w = new World();
            w.parts.put(World.k(12, 11, 22), part);
            assertEquals(1, Renovation.apply(EcoDomeShape.KEY, 0, w));
            assertEquals(part, (int) w.parts.get(World.k(12, 11, 22)));
            assertEquals(0, w.placed);
        }

        // a cell whose chunk is not loaded: nothing is touched and it is tried again later
        World far = new World();
        far.parts.put(World.k(12, 11, 22), Parts.GRASS);
        far.unloaded.add(World.k(12, 11, 22));
        assertEquals(0, Renovation.apply(EcoDomeShape.KEY, 0, far));
        assertEquals(0, far.placed);
        far.unloaded.clear();
        assertEquals(1, Renovation.apply(EcoDomeShape.KEY, 0, far));
        assertEquals(1, far.placed);

        // a kind with nothing to renovate is at revision 0 and touches nothing
        World gate = new World();
        assertEquals(0, Renovation.apply(ResonanceGateShape.KEY, 0, gate));
        assertEquals(0, gate.placed);
        assertThrows(
            UnsupportedOperationException.class,
            () -> Renovation.due(EcoDomeShape.KEY, 0)
                .add(null),
            "nobody changes the list through it");
    }
}
