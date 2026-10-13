package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The Resonance Gate's drawing (0.14.0, CITY.md §4.7 Visuals): the floating crystal spire stands over the dissolved
 * spire core, three wide and sixteen tall from {@code Y0+14} to {@code Y0+30}, clear of the bridge's roof; its shards
 * orbit its waist clear of it and of the gate; the column of light rises through the dissolved core and the glass over
 * it; a pulse runs out every eight seconds, every four powered, in the air over the wings; every motion repeats; the
 * gate keeps CITY.md §1.3's budgets and its landmark its 400 quads at any distance.
 */
class GateLookTest {

    private static final CityBuild B = ResonanceGateShape.BUILD;
    private static final String OPEN = LookCells.OPEN + ResonanceGateShape.DISSOLVES;

    @Test
    void theSpireFloatsOverTheBridge() {
        assertEquals(ResonanceGateShape.BRIDGE_ROOF + 1, GateLook.SPIRE_FROM, 0, "Y0 + 14, the roof's top");
        assertEquals(30, GateLook.SPIRE_TO, 0);
        assertEquals(22, GateLook.WAIST, 0);
        assertEquals(1.5, GateLook.SPIRE_HALF_W, 0, "three wide");
        assertEquals(8, GateLook.SPIRE_HALF_H, 0, "sixteen tall");
        // over the spire core: the conduits and the seat that dissolve, the glass over them
        int x = (int) Math.floor(GateLook.AXIS_X), z = (int) Math.floor(GateLook.AXIS_Z);
        assertEquals(ResonanceGateShape.MID, x);
        assertEquals(ResonanceGateShape.CORE_Z, z);
        for (int y = ResonanceGateShape.CORE_FROM; y <= ResonanceGateShape.SEAT_Y; y++)
            assertTrue(ResonanceGateShape.DISSOLVES.indexOf(B.cell(x, y, z)) >= 0, "the core at y" + y);
        assertEquals('G', B.cell(x, ResonanceGateShape.BRIDGE_ROOF, z), "the glass over it");
        double low = Double.MAX_VALUE, high = -1;
        for (int j = 0; j < 400; j++) {
            double t = j * 3.1;
            double[] s = GateLook.spire(t);
            double foot = s[0] - GateLook.SPIRE_HALF_H, tip = s[0] + GateLook.SPIRE_HALF_H;
            low = Math.min(low, foot);
            high = Math.max(high, tip);
            assertTrue(s[1] >= 0 && s[1] < 2 * Math.PI);
            // the crystal's corners and edges stay in the air, clear of every block
            for (int k = 0; k <= 16; k++) {
                double y = foot + k, w = GateLook.halfWidth(y, s[0]);
                assertEquals(GateLook.SPIRE_HALF_W * (1 - Math.abs(y - s[0]) / GateLook.SPIRE_HALF_H), w, 1e-9);
                for (int c = 0; c < 4; c++) {
                    double a = s[1] + c * Math.PI / 2;
                    double px = GateLook.AXIS_X + w * Math.cos(a), pz = GateLook.AXIS_Z + w * Math.sin(a);
                    assertTrue(LookCells.clearance(B, px, y, pz, OPEN, 1) >= (k == 0 ? 0.1 : 0.5), "spire at " + y);
                }
            }
        }
        assertTrue(low > GateLook.SPIRE_FROM + 0.1, "hovering over the roof: " + low);
        assertTrue(high < GateLook.SPIRE_TO + 0.5);
        // nothing at all stands in the spire's column over the roof
        for (int y = ResonanceGateShape.BRIDGE_ROOF + 1; y < B.height(); y++)
            for (int i = x - 2; i <= x + 2; i++) for (int k = z - 2; k <= z + 2; k++)
                assertEquals(ShapeKit.NONE, B.cell(i, y, k), "over the bridge at " + i + "," + y + "," + k);
    }

    @Test
    void theShardsOrbitItsWaistClearOfItAndTheGate() {
        for (boolean on : new boolean[] { false, true })
            for (int k = 0; k < GateLook.SHARDS; k++) for (int j = 0; j < 300; j++) {
                double t = j * 4.7;
                double[] q = GateLook.shard(k, t, on), s = GateLook.spire(t);
                double r = Math.hypot(q[0] - GateLook.AXIS_X, q[2] - GateLook.AXIS_Z);
                assertEquals(GateLook.SHARD_R, r, 1e-9, "on its orbit");
                // clear of the spire at every height the shard spans
                for (double y = q[1] - GateLook.SHARD_HALF_H; y <= q[1] + GateLook.SHARD_HALF_H; y += 0.1)
                    assertTrue(r - GateLook.SHARD_HALF_W > GateLook.halfWidth(y, s[0]) + 1, "shard " + k + " at " + y);
                assertTrue(
                    LookCells.clearance(B, q[0], q[1] - GateLook.SHARD_HALF_H, q[2], OPEN, 2) >= 2,
                    "clear of the gate");
                if (on) assertEquals(s[0], q[1], 1e-9, "powered, in line round the waist");
            }
        // idle they are scattered about the waist
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (int k = 0; k < GateLook.SHARDS; k++) {
            double y = GateLook.shard(k, 0, false)[1];
            lo = Math.min(lo, y);
            hi = Math.max(hi, y);
        }
        assertTrue(hi - lo > 4, "scattered");
        // powered they orbit twice as fast
        double[] a = GateLook.shard(0, 0, true), b = GateLook.shard(0, GateLook.ORBIT_ON_T / 4.0, true);
        assertEquals(GateLook.AXIS_X, b[0], 1e-9);
        assertEquals(GateLook.AXIS_Z + GateLook.SHARD_R, b[2], 1e-9);
        assertEquals(GateLook.AXIS_X + GateLook.SHARD_R, a[0], 1e-9);
    }

    @Test
    void aPulseRunsOutEveryEightSecondsAndQuickensPowered() {
        assertEquals(160, GateLook.PULSE_T, "eight seconds");
        assertEquals(80, GateLook.PULSE_ON_T);
        for (boolean on : new boolean[] { false, true }) {
            int period = on ? GateLook.PULSE_ON_T : GateLook.PULSE_T;
            int rings = 0;
            double was = 0;
            for (int t = 0; t < 4 * period; t++) {
                double[] r = GateLook.pulse(t + 0.5, on);
                if (t % period < GateLook.RING_T) {
                    assertNotNull(r, "a ring at " + t);
                    if (t % period == 0) rings++;
                    else assertTrue(r[0] > was && r[0] <= GateLook.RING_TO, "running out");
                    assertTrue(r[1] > 0 && r[1] <= 1);
                    was = r[0];
                } else assertNull(r, "none between pulses at " + t);
            }
            assertEquals(4, rings);
        }
        assertArrayEquals(GateLook.pulse(12, false), GateLook.pulse(12 + GateLook.PULSE_T, false), 1e-12);
        assertTrue(GateLook.RING_FROM > GateLook.SPIRE_HALF_W, "out of the spire from the start");
        // the ring runs over the wings, in the air
        double y = GateLook.spire(0)[0];
        for (double r = GateLook.RING_FROM; r <= GateLook.RING_TO; r += 0.5) for (int i = 0; i < 64; i++) {
            double[] p = GateLook.onRing(r, 2 * Math.PI * i / 64);
            assertTrue(LookCells.clearance(B, p[0], y - GateLook.BOB, p[1], OPEN, 3) >= 3, "the ring at " + r);
        }
    }

    @Test
    void theColumnRisesThroughTheDissolvedCore() {
        assertEquals(ResonanceGateShape.DECK_Y + 1, GateLook.BEAM_FROM, 0, "from the chamber's floor");
        int x = ResonanceGateShape.MID, z = ResonanceGateShape.CORE_Z;
        assertEquals('d', B.cell(x, ResonanceGateShape.DECK_Y, z), "on the bridge's deck");
        assertTrue(GateLook.BEAM_R < 0.5, "within the core's column");
        for (int j = 0; j < 50; j++) {
            double t = j * 11.3, top = GateLook.spire(t)[0] - GateLook.SPIRE_HALF_H;
            for (int k = 0; k < GateLook.BEAM_MOTES; k++) {
                double[] m = GateLook.beamMote(k, t, top);
                assertTrue(m[0] >= GateLook.BEAM_FROM && m[0] <= top);
                assertTrue(m[1] >= 0 && m[1] <= 1);
                char ch = LookCells.at(B, GateLook.AXIS_X, m[0], GateLook.AXIS_Z);
                assertTrue(OPEN.indexOf(ch) >= 0 || ch == 'G', "through the core and the glass: " + ch);
            }
        }
    }

    @Test
    void theMotionsRepeat() {
        for (double t : new double[] { 0, 77.25, 123456.5 }) {
            int all = GateLook.TURN_T * GateLook.BOB_T;
            assertArrayEquals(GateLook.spire(t), GateLook.spire(t + all), 1e-6);
            for (int k = 0; k < GateLook.SHARDS; k++) for (boolean on : new boolean[] { false, true }) {
                int every = GateLook.ORBIT_T * GateLook.BOB_T * GateLook.SHARD_TURN_T;
                double[] a = GateLook.shard(k, t, on), b = GateLook.shard(k, t + every, on);
                for (int i = 0; i < 3; i++) assertEquals(a[i], b[i], 1e-6);
            }
            assertArrayEquals(GateLook.pulse(t, true), GateLook.pulse(t + GateLook.PULSE_ON_T, true), 1e-9);
        }
    }

    @Test
    void thePlinthLineRunsAlongThePassageToo() {
        // the passage under the bridge is the road's, open to the outside, though the gate's floor runs under it: the
        // line runs along the wings' walls onto it as well as round their outsides, never into a wing's halls
        java.util.List<int[]> course = ModuleLook.course(B);
        assertEquals(64 + 2 * 14, course.size(), "both wings all round, the passage doors' rows left out");
        int passage = 0;
        for (int[] c : course) {
            int nx = c[0] + c[2], nz = c[1] + c[3];
            boolean hall = ResonanceGateShape.inWing(nx) && nx != 0
                && nx != 8
                && nx != 22
                && nx != 30
                && nz >= 1
                && nz <= 15;
            assertTrue(!hall, "into a wing's hall at " + c[0] + "," + c[1]);
            if (c[0] == ResonanceGateShape.LEFT_TO && c[2] == 1 || c[0] == ResonanceGateShape.RIGHT_FROM && c[2] == -1)
                passage++;
        }
        assertEquals(28, passage, "the two walls along the passage, but for the doors onto it");
        // the rule changes no other building's line
        assertEquals(
            125,
            ModuleLook.course(EcoDomeShape.BUILD)
                .size());
        assertEquals(
            64,
            ModuleLook.course(DomeGeneratorShape.BUILD)
                .size());
        assertEquals(
            0,
            ModuleLook.course(RootShape.BUILD)
                .size());
        for (CityBuild p : new CityBuild[] { ArcaneCourtShape.BUILD, BloodCovenantShape.BUILD, HuntingGroundShape.BUILD,
            ManaFountainShape.BUILD })
            assertEquals(
                76,
                ModuleLook.course(p)
                    .size(),
                p.key());
        assertEquals(
            51,
            ModuleLook.course(DualFoilVaultShape.BUILD)
                .size());
    }

    @Test
    void theGateKeepsItsBudget() {
        int[] budget = { 2000, 300, 64 };
        for (int tier = ModuleLook.NEAR; tier <= ModuleLook.FAR; tier++) {
            int base = ModuleLook.baseQuads(B, ResonanceGateShape.UNITS, tier);
            for (boolean docked : new boolean[] { false, true }) for (boolean on : new boolean[] { false, true }) {
                if (on && !docked) continue;
                int own = GateLook.quads(tier, docked, on);
                assertTrue(
                    base + own <= budget[tier],
                    "tier " + tier + (docked ? " docked" : "") + (on ? " powered" : "") + ": " + base + " + " + own);
            }
        }
        // the ring: each piece one quad wide of the pixel rule, near with a halo
        assertEquals(96, GateLook.ringQuads(ModuleLook.NEAR));
        assertEquals(24, GateLook.ringQuads(ModuleLook.MID));
        assertEquals(16, GateLook.ringQuads(ModuleLook.FAR));
        // far: the spire and its glow, six points of light, the ring
        assertEquals(8 + 1 + 6 + 16, GateLook.quads(ModuleLook.FAR, true, true));
        // the landmark is the far drawing, within its budget at any distance, gone past 512
        assertEquals(GateLook.quads(ModuleLook.FAR, true, false), GateLook.landmarkQuads(true, false));
        assertTrue(GateLook.landmarkQuads(true, true) <= GateLook.BUDGET);
        assertEquals(1, GateLook.landmarkFade(200), 0);
        assertEquals(1, GateLook.landmarkFade(GateLook.LANDMARK - GateLook.LANDMARK_FADE), 0);
        assertEquals(0.5, GateLook.landmarkFade(GateLook.LANDMARK - GateLook.LANDMARK_FADE / 2), 1e-6);
        assertEquals(0, GateLook.landmarkFade(GateLook.LANDMARK), 0);
        assertEquals(0, GateLook.landmarkFade(600), 0);
    }
}
