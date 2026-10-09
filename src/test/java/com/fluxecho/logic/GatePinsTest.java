package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fluxecho.logic.GatePins.Linked;

class GatePinsTest {

    private static final int VIEW = 10, RANGE = 48;

    /** An outside gate somewhere in the world and its room in the zone, linked both ways. */
    private static final class Pair {

        final GateGeometry.Gate outside;
        final RoomPlan plan = new RoomPlan("spring");
        final int cx = FoldedZone.centerX(3), cz = FoldedZone.centerZ(3);
        final GateGeometry.Gate inside = plan.gate(cx, FoldedZone.FLOOR_Y, cz);
        final GateGeometry.Box room = plan.box(cx, FoldedZone.FLOOR_Y, cz);
        final List<Linked> gates;

        Pair(GateGeometry.Gate outside) {
            this.outside = outside;
            gates = Arrays.asList(new Linked(outside, room), new Linked(inside, GatePins.outsideView(outside)));
        }

        Set<Long> wanted(double[] p) {
            boolean in = FoldedZone.contains(p[0], p[2]);
            return GatePins.wanted(p[0], p[1], p[2], gates, RANGE, in ? room : null, in ? outside : null, VIEW + 1);
        }
    }

    private static Set<Long> square(double[] p) {
        Set<Long> s = new HashSet<>();
        GatePins.addSquare(s, (int) Math.floor(p[0]) >> 4, (int) Math.floor(p[2]) >> 4, VIEW);
        return s;
    }

    private static Set<Long> union(Set<Long> a, Set<Long> b) {
        Set<Long> u = new HashSet<>(a);
        u.addAll(b);
        return u;
    }

    @Test
    void keysAreMinecraftsChunkKeys() {
        for (int[] c : new int[][] { { 0, 0 }, { -1, 5 }, { 65536, -65536 }, { -1875000, 1875000 } }) {
            long k = GatePins.key(c[0], c[1]);
            assertEquals(c[0], GatePins.keyX(k));
            assertEquals(c[1], GatePins.keyZ(k));
            // ChunkCoordIntPair.chunkXZ2Int
            assertEquals((long) c[0] & 4294967295L | ((long) c[1] & 4294967295L) << 32, k);
        }
    }

    @Test
    void walkingInAndOutDropsNothing() {
        for (int facing = 0; facing < 4; facing++) {
            Pair pair = new Pair(new GateGeometry.Gate(-37 + facing * 100, 70, 15 - facing * 300, facing));
            GateGeometry.Gate o = pair.outside;
            double[] atOutside = { o.cx() + GateGeometry.dx(o.facing) * 1.5, 70,
                o.cz() + GateGeometry.dz(o.facing) * 1.5 };
            double[] atInside = GateGeometry.exit(o, pair.inside, o.cx(), 70, o.cz(), 0.35);

            Set<Long> before = union(square(atOutside), pair.wanted(atOutside));
            assertTrue(before.containsAll(chunks(pair.room)), "the room is loaded before walking in");

            // in: everything that was loaded stays loaded
            Set<Long> inside = union(square(atInside), pair.wanted(atInside));
            assertTrue(inside.containsAll(before), "facing " + facing + ": walking in drops nothing");

            // and out again
            double[] back = GateGeometry.exit(pair.inside, o, atInside[0], atInside[1], atInside[2], 0.35);
            Set<Long> after = union(square(back), pair.wanted(back));
            assertTrue(after.containsAll(chunks(pair.room)), "the room stays loaded behind the gate");
            assertTrue(inside.containsAll(square(back)), "facing " + facing + ": walking out needs nothing new");
        }
    }

    @Test
    void farFromAnyGateNothingIsKept() {
        Pair pair = new Pair(new GateGeometry.Gate(0, 64, 0, 2));
        assertTrue(
            pair.wanted(new double[] { 500, 64, 500 })
                .isEmpty());
        assertTrue(GatePins.inSquare(GatePins.key(3, -4), 0, 0, 4));
        assertTrue(!GatePins.inSquare(GatePins.key(5, 0), 0, 0, 4));
    }

    @Test
    void theRoomsGateShowsTheOutsideInFrontOfItsPartner() {
        GateGeometry.Gate o = new GateGeometry.Gate(100, 64, 100, 0);
        GateGeometry.Box v = GatePins.outsideView(o);
        assertTrue(v.contains(100, 64, 100 + GatePins.VIEW_DEPTH));
        assertTrue(!v.contains(100, 64, 99), "not behind it");
    }

    private static Set<Long> chunks(GateGeometry.Box b) {
        Set<Long> s = new HashSet<>();
        GatePins.addBox(s, b);
        return s;
    }
}
