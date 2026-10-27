package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fluxecho.logic.BuildPlan.Plan;
import com.fluxecho.logic.BuildPlan.Step;

/**
 * The live materials in a module's plan (0.17.0, style FINAL §3.7, refactor FINAL §1.1): whatever stage a building
 * gives its birch logs, leaves, vines, lily pads and flowers, they are soft steps laid at the very end of the last
 * stage before the commissioning, bottom-up, so the water a lily floats on, the wall a vine hangs from, the grass a
 * flower stands in and the trunk under the leaves are all there before them; the commissioning stays the controller
 * alone. With a stage order of its own (the Sophon Array's kind) it is still the last stage before the commissioning.
 */
class PlantOrderTest {

    private static final int CX = -400, Y0 = 64, CZ = 250;

    /**
     * A 31 x 31 octagon on an outer diagonal (as {@code OuterDiagSiteTest}'s stand-in) with a garden: a birch with
     * leaves on it, a pool with lily pads, a flower bed on grass and a vine under a pillar's coping; the garden's
     * characters are the building's own legend, each given the SHELL stage like everything else.
     */
    private static CityBuild garden(int[] stageOrder) {
        ShapeKit.Shape s = new ShapeKit.Shape("garden_test", 31, 5, 31);
        s.legend('l', Parts.BIRCH)
            .legend('j', Parts.OAK_LEAVES)
            .legend('v', Parts.VINE)
            .legend('y', Parts.LILY)
            .legend('b', Parts.FLOWER);
        for (int x = 0; x < 31; x++) for (int z = 0; z < 31; z++) {
            int du = Math.abs(x - 15), dv = Math.abs(z - 15);
            if (du + dv > 20) continue;
            s.set(x, 0, z, 'F');
            if (du + dv >= 19 || du == 15 || dv == 15) for (int y = 1; y < 4; y++) s.set(x, y, z, 'P');
        }
        // the birch: two logs and leaves on top
        s.set(10, 1, 10, 'l');
        s.set(10, 2, 10, 'l');
        s.set(10, 3, 10, 'j');
        // the pool and its lily pads
        for (int x = 18; x <= 20; x++) {
            s.set(x, 1, 18, '=');
            s.set(x, 2, 18, 'y');
        }
        // the flower bed
        for (int x = 9; x <= 11; x++) {
            s.set(x, 1, 20, ',');
            s.set(x, 2, 20, 'b');
        }
        // a vine under a pillar's coping
        s.set(15, 3, 25, 'P');
        s.set(15, 2, 25, 'v');
        s.set(15, 2, 15, ShapeKit.CONTROLLER);
        s.controller = new int[] { 15, 2, 15 };
        return new CityBuild(
            "garden_test",
            s,
            5,
            ShapeKit.blueprint(s, 5),
            new UnitLayout(s),
            Parts.moduleCore(0),
            new CityBuild.Stages() {

                @Override
                public int stage(int x, int y, int z, char ch) {
                    return ch == ShapeKit.CONTROLLER ? BuildPlan.M_COMMISSION : BuildPlan.M_SHELL;
                }

                @Override
                public int fitRank(int x, int y, int z, char ch) {
                    return 1;
                }

                @Override
                public int[] stageOrder() {
                    return stageOrder;
                }
            });
    }

    private static Plan plan(int[] stageOrder) {
        CampusPlan cp = new CampusPlan(Mix.seed(CX, Y0 + 2, CZ));
        return BuildPlan.module(cp, OuterRing.DIAG_SITES[0], CX, Y0, CZ, 0, -1, garden(stageOrder));
    }

    @Test
    void plantsAreLaidLastBeforeTheCommissioning() {
        check(plan(null));
    }

    @Test
    void soWithAStageOrderOfItsOwn() {
        // FIT before DECOR's place: the stage at the position before the commissioning is FIT now
        check(
            plan(
                new int[] { BuildPlan.M_CLEAR, BuildPlan.M_FLOOR, BuildPlan.M_SHELL, BuildPlan.M_DECK,
                    BuildPlan.M_UPPER, BuildPlan.M_ROOF, BuildPlan.M_DECOR, BuildPlan.M_FIT, BuildPlan.M_COMMISSION }));
    }

    private static void check(Plan p) {
        int last = BuildPlan.M_COMMISSION - 1;
        List<Step> live = new ArrayList<>();
        int firstLive = -1, lastOther = -1;
        for (int i = 0; i < p.steps.size(); i++) {
            Step s = p.steps.get(i);
            if (Parts.isLive(s.part)) {
                live.add(s);
                if (firstLive < 0) firstLive = i;
                assertEquals(BuildPlan.SOFT, s.kind, "a live material is a soft step");
                assertEquals(last, s.stage, "a live material is laid in the last stage before the commissioning");
            } else if (s.stage <= last) lastOther = i;
        }
        assertEquals(3 + 3 + 3 + 1, live.size(), "every plant of the garden");
        assertTrue(firstLive > lastOther, "the plants come after every other step of their stage and before");
        assertEquals(p.end(last), firstLive + live.size(), "they end their stage");
        for (int i = 1; i < live.size(); i++)
            assertTrue(live.get(i - 1).y <= live.get(i).y, "bottom-up: " + live.get(i - 1).y + ", " + live.get(i).y);
        // the commissioning is the controller alone
        assertEquals(1, p.end(BuildPlan.M_COMMISSION) - p.start(BuildPlan.M_COMMISSION));
        assertEquals(
            Parts.moduleCore(0),
            p.steps.get(p.start(BuildPlan.M_COMMISSION)).part,
            "the controller is the last step");
        // what each plant stands on, floats on or hangs from is placed before it
        for (Step plant : live) {
            int below = -1, above = -1, at = p.steps.indexOf(plant);
            for (int i = 0; i < p.steps.size(); i++) {
                Step s = p.steps.get(i);
                if (s.x != plant.x || s.z != plant.z) continue;
                if (s.y == plant.y - 1 && s.kind != BuildPlan.AIR) below = i;
                if (s.y == plant.y + 1 && s.kind != BuildPlan.AIR) above = i;
            }
            if (plant.part == Parts.VINE) assertTrue(above >= 0 && above < at, "the vine's coping first");
            else assertTrue(below >= 0 && below < at, "what the plant at y " + plant.y + " stands on first");
        }
    }
}
