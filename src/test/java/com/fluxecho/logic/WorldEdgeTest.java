package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.CRC32;

import org.junit.jupiter.api.Test;

import com.fluxecho.logic.BuildPlan.Plan;
import com.fluxecho.logic.BuildPlan.Step;

/**
 * Plans at the edges of the world (0.14.1, CITY.md §1.3 Vertical): a plan never holds a cell under y 0 or over y 255
 * (the bands of a plan that lose steps begin at their first step left); every module's plan, at every base a core may
 * be placed at, keeps its controller and every cell of its units; the Arcane Court's finial clips over Y0 223 instead
 * of the court being refused; a growth on a legacy forum high up leaves out only what lies over the world; and inside
 * the range every module was offered at before, every plan is the one 0.14.0 made.
 */
class WorldEdgeTest {

    private static final int CX = 100, CZ = -200;

    /** A plan factory of a module, its first preferred site, and its name. */
    private static final class Module {

        final String name;
        final int site;
        final Factory plans;
        final ControllerAt controllers;

        Module(String name, int site, Factory plans, ControllerAt controllers) {
            this.name = name;
            this.site = site;
            this.plans = plans;
            this.controllers = controllers;
        }

        Plan plan(int y0) {
            return plans.plan(new CampusPlan(7L), site, CX, y0, CZ, 0, -1);
        }
    }

    private interface Factory {

        Plan plan(CampusPlan plan, int site, int cx, int y0, int cz, int fx, int fz);
    }

    private interface ControllerAt {

        int[] controller(int site, int cx, int y0, int cz, int fx, int fz);
    }

    private static List<Module> modules() {
        List<Module> out = new ArrayList<>();
        out.add(new Module("eco_dome", 2, EcoDomeShape::plan, EcoDomeShape::controller));
        out.add(new Module("dual_foil_vault", 6, DualFoilVaultShape::plan, DualFoilVaultShape::controller));
        out.add(new Module("arcane_court", 7, ArcaneCourtShape::plan, ArcaneCourtShape::controller));
        out.add(new Module("blood_covenant", 5, BloodCovenantShape::plan, BloodCovenantShape::controller));
        out.add(new Module("hunting_ground", 3, HuntingGroundShape::plan, HuntingGroundShape::controller));
        out.add(new Module("mana_fountain", 1, ManaFountainShape::plan, ManaFountainShape::controller));
        out.add(new Module("resonance_gate", 0, ResonanceGateShape::plan, ResonanceGateShape::controller));
        out.add(new Module("dome_generator", 8, DomeGeneratorShape::plan, DomeGeneratorShape::controller));
        out.add(new Module("root", 9, RootShape::plan, RootShape::controller));
        out.add(new Module("library", 4, BuildPlan::archive, BuildPlan::archiveController));
        return out;
    }

    @Test
    void aPlanLeavesOutTheCellsOutsideTheWorld() {
        List<List<Step>> stages = new ArrayList<>();
        stages.add(steps(10, -1, 11, 300, 12));
        stages.add(steps(255, 256, 0));
        // bands at the second, fourth and fifth step of the first stage; the second of the second
        Plan p = new Plan(
            "t",
            2,
            stages,
            Collections.<BuildPlan.Column>emptyList(),
            Arrays.asList(Arrays.asList(1, 3, 4), Collections.singletonList(1)));
        List<Integer> ys = new ArrayList<>();
        for (Step s : p.steps) ys.add(s.y);
        assertEquals(Arrays.asList(10, 11, 12, 255, 0), ys, "only the cells in the world, in their order");
        assertEquals(3, p.start(1));
        assertEquals(5, p.end(1));
        // the first stage's bands begin at y 11 and y 12 (the band of the cell at y 300 begins at y 12 too); the
        // second's at its first step left after y 256, the cell at y 0
        assertEquals(3, p.bandCount());
        assertEquals(1, p.bandEnd(0));
        assertEquals(2, p.bandEnd(1));
        assertEquals(4, p.bandEnd(2));
        assertEquals(4, p.bandEnd(3));
        assertEquals(5, p.bandEnd(4));
        // a band whose steps all lie outside the world goes with them
        List<List<Step>> one = new ArrayList<>();
        one.add(steps(5, -3, -4, 6));
        Plan q = new Plan(
            "t",
            1,
            one,
            Collections.<BuildPlan.Column>emptyList(),
            Collections.singletonList(Arrays.asList(1, 3)));
        assertEquals(2, q.steps.size());
        assertEquals(1, q.bandCount());
        assertEquals(1, q.bandEnd(0));
    }

    private static List<Step> steps(int... ys) {
        List<Step> out = new ArrayList<>();
        for (int i = 0; i < ys.length; i++) out.add(new Step(i, ys[i], 0, 1, 0, BuildPlan.NO_GROUP, BuildPlan.HARD));
        return out;
    }

    /**
     * Every module's plan at every base a core may be placed at (Y0 14 to 225, the campus band) and at the steps of
     * the sky datum and the well's depth: no cell outside the world, the controller its last step where its spec puts
     * it, and every cell of its units there, so whatever clips at the world's top is never a unit or the controller.
     */
    @Test
    void everyModulesPlanStaysInTheWorldWithItsUnitsAndController() {
        int[] bases = { 14, 19, 20, 25, 26, 31, 32, 64, 203, 204, 208, 212, 218, 219, 222, 224, 225 };
        for (Module m : modules()) {
            int units = 0;
            for (Step s : m.plan(64).steps) if (s.group != BuildPlan.NO_GROUP) units++;
            for (int y0 : bases) {
                Plan p = m.plan(y0);
                int u = 0;
                for (Step s : p.steps) {
                    assertTrue(s.y >= 0 && s.y <= 255, m.name + " at " + y0 + ": " + s);
                    if (s.group != BuildPlan.NO_GROUP) u++;
                }
                assertEquals(units, u, m.name + "'s units at " + y0);
                int[] c = m.controllers.controller(m.site, CX, y0, CZ, 0, -1);
                Step last = p.steps.get(p.steps.size() - 1);
                assertTrue(last.at(c[0], c[1], c[2]), m.name + "'s controller last at " + y0);
                assertTrue(BuildPlan.standTop(p) <= 255, m.name + " stands in the world at " + y0);
            }
        }
    }

    /**
     * The Arcane Court is 33 high: over Y0 223 its finial (a seat over two conduits that dissolve into the drawn
     * crystal) reaches over the world's top, and its plan loses exactly those cells, one at Y0 224 and two at 225,
     * instead of the court being refused; its controller and units stand at Y0 + 5 at most.
     */
    @Test
    void theArcaneCourtsFinialClipsAtTheWorldsTop() {
        Module court = modules().get(2);
        int[] hard = new int[3];
        for (int i = 0; i < 3; i++) for (Step s : court.plan(223 + i).steps) if (s.kind == BuildPlan.HARD) hard[i]++;
        assertEquals(hard[0] - 1, hard[1], "the seat clips at Y0 224");
        assertEquals(hard[0] - 2, hard[2], "and a conduit under it at 225");
        assertEquals(64 + 5, BuildPlan.standTop(court.plan(64)), "its units' tops");
        assertEquals('Z', ArcaneCourtShape.SHAPE.get(12, 32, 12));
        assertEquals('C', ArcaneCourtShape.SHAPE.get(12, 31, 12));
    }

    /**
     * A 0.9.2 nexus may have laid its forum at any height: its growth to phase V high up leaves out the cells over the
     * world's top (the trailing ones), so the job ends instead of waiting on cells nobody can place.
     */
    @Test
    void aGrowthHighUpLeavesOutTheCellsOverTheWorld() {
        for (int phase = 2; phase <= NexusShape.PHASES; phase++) {
            Plan low = BuildPlan.grow(phase, CX, 64, CZ, 0, -1, CX, 66, CZ - 5);
            Plan high = BuildPlan.grow(phase, CX, 240, CZ, 0, -1, CX, 242, CZ - 5);
            int fit = 0;
            for (Step s : low.steps) if (s.y - 64 + 240 <= 255) fit++;
            assertEquals(fit, high.steps.size(), "phase " + phase);
            for (Step s : high.steps) assertTrue(s.y <= 255);
        }
    }

    /**
     * Inside the range every module was offered at before (the zenith to Y0 203, the well from 32), the sky datum is
     * 40 and the well 26 deep, and nothing lies outside the world: every plan is step for step the one 0.14.0 made
     * (checksums of 0.14.0's plans; the Eco Dome's are 0.14.1's, as its crown deck lost a grass cell, §2). The
     * Dual-foil Vault's at Y0 223 is 0.16.0's: under the sky ring's deck at the datum 28 its clear columns stop at
     * Y0 + 26, two cells under it, instead of Y0 + 29; with their old top its checksum is 0.14.0's, so not a step
     * moved.
     */
    @Test
    void insideTheOldRangeEveryPlanIsTheOneBefore() {
        int[] bases = { 32, 64, 79, 203, 223 };
        long[][] crcs = { { 634478072L, 1948775050L, 20940611L, 1041280120L, 2335015903L },
            { 3846001252L, 155109968L, 1935298065L, 3026057490L, 2397870871L },
            { 669525100L, 2341407887L, 3105971571L, 3807362643L, 1835682086L },
            { 1921628738L, 1344598318L, 3482605610L, 2827896150L, 303915443L },
            { 235517079L, 1755700208L, 2193300394L, 1007103592L, 1666261085L },
            { 4083856237L, 3439361750L, 266649444L, 1432011813L, 424722063L },
            { 90591106L, 3320109154L, 1055446731L, 3483528739L, 959762511L },
            { 2987628495L, 2873002651L, 214349722L, 558656160L, -1L },
            { 2117230839L, 3811370417L, 3237891106L, 2043653840L, 796606403L },
            { 4234005002L, 284046927L, 2746771426L, 3372454176L, 523789690L } };
        List<Module> mods = modules();
        for (int i = 0; i < mods.size(); i++) for (int k = 0; k < bases.length; k++) {
            if (crcs[i][k] < 0) continue;
            assertEquals(
                crcs[i][k],
                hash(
                    mods.get(i)
                        .plan(bases[k])),
                mods.get(i).name + " at Y0 " + bases[k]);
        }
        // the vault at Y0 223 (0.16.0): only its clear columns' top moved, from Y0 + 29 to Y0 + 26
        Plan vault = mods.get(1)
            .plan(223);
        for (BuildPlan.Column col : vault.clear) assertEquals(223 + 26, col.yTo);
        assertEquals(3244557013L, hash(vault, 223 + 29), "0.14.0's plan, its clear columns to Y0 + 29");
        assertArrayEquals(
            new int[] { 40, 40 },
            new int[] { DomeGeneratorShape.datum(32), DomeGeneratorShape.datum(203) });
        assertArrayEquals(new int[] { 26, 26 }, new int[] { RootShape.depth(32), RootShape.depth(203) });
    }

    /** Every step, the clear columns, the bands and the key of a plan, as one checksum. */
    private static long hash(Plan p) {
        return hash(p, Integer.MIN_VALUE);
    }

    /** {@link #hash(Plan)} with every clear column's top taken as {@code clearTop} (unless that is MIN_VALUE). */
    private static long hash(Plan p, int clearTop) {
        CRC32 c = new CRC32();
        ByteBuffer b = ByteBuffer.allocate(28);
        for (Step s : p.steps) {
            b.clear();
            b.putInt(s.x)
                .putInt(s.y)
                .putInt(s.z)
                .putInt(s.part)
                .putInt(s.stage)
                .putInt(s.group)
                .putInt(s.kind);
            c.update(b.array(), 0, 28);
        }
        for (BuildPlan.Column col : p.clear) {
            b.clear();
            b.putInt(col.x)
                .putInt(col.z)
                .putInt(col.yFrom)
                .putInt(clearTop == Integer.MIN_VALUE ? col.yTo : clearTop)
                .putInt(col.grade ? 1 : 0);
            c.update(b.array(), 0, 20);
        }
        for (int i = 0; i < p.steps.size(); i++) {
            b.clear();
            b.putInt(p.bandEnd(i));
            c.update(b.array(), 0, 4);
        }
        c.update(p.key.getBytes(StandardCharsets.UTF_8));
        return c.getValue();
    }
}
