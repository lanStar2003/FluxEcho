package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * How the two axis modules of 0.12.0 show themselves in the shared parts (CITY.md §1.1, §1.6 items 7 and 8): their
 * name plates float over their controllers inside (the pod's also under its floor, for the forum below), a hall's
 * plate stays over its canopy; their window's floor plans show the lift's pad and, for the well whose taps lie in the
 * pool's rim, the walkway they are used from; their sites are named, not numbered.
 */
class AxisModulesLookTest {

    @Test
    void thePodsPlates() {
        List<double[]> p = ModuleLook.plates(DomeGeneratorShape.BUILD);
        assertEquals(2, p.size());
        double[] in = p.get(0), belly = p.get(1);
        assertEquals(DomeGeneratorShape.CTRL_X + 0.5, in[0], 1e-9);
        assertEquals(DomeGeneratorShape.CTRL_Z + 0.5, in[2], 1e-9);
        assertTrue(in[1] > DomeGeneratorShape.CTRL_Y + 1 && in[1] < 6 - 0.3, "over the controller, under the ceiling");
        assertTrue(DomeGeneratorShape.inPod((int) in[0], (int) in[2]));
        assertTrue(belly[1] < 0, "under the pod's floor");
        assertEquals(DomeGeneratorShape.PAD_X + 0.5, belly[0], 1e-9, "over the lift's shaft");
        assertNotNull(ModuleLook.liftPad(DomeGeneratorShape.BUILD));
    }

    @Test
    void theWellsPlate() {
        List<double[]> p = ModuleLook.plates(RootShape.BUILD);
        assertEquals(1, p.size(), "it hangs in nothing: one plate");
        double[] in = p.get(0);
        assertEquals(RootShape.CTRL_X + 0.5, in[0], 1e-9);
        assertTrue(in[1] > RootShape.CTRL_Y + 1, "over the controller");
        assertTrue(in[1] + 0.25 < RootLook.ringLowest(0), "under the lowest ring at its lowest");
        assertEquals(RootShape.PAD_Y, ModuleLook.liftPad(RootShape.BUILD)[1]);
    }

    @Test
    void aHallKeepsItsCanopyPlate() {
        List<double[]> p = ModuleLook.plates(EcoDomeShape.BUILD);
        assertEquals(1, p.size());
        double[] want = ModuleLook.plate(EcoDomeShape.BUILD);
        for (int i = 0; i < 3; i++) assertEquals(want[i], p.get(0)[i], 0);
    }

    @Test
    void thePodsPlanShowsThePadAndTheWards() {
        ModuleMap m = new ModuleMap(
            DomeGeneratorShape.BUILD,
            DomeGeneratorShape.UNITS,
            DomeGeneratorShape.DISSOLVES,
            128,
            112);
        assertEquals(1, m.storeys());
        assertEquals(1, m.level(0), "the ring room");
        assertEquals(ModuleMap.Mark.LIFT, m.mark(0, DomeGeneratorShape.PAD_X, DomeGeneratorShape.PAD_Z));
        assertEquals(ModuleMap.Mark.CONSOLE, m.mark(0, DomeGeneratorShape.CTRL_X, DomeGeneratorShape.CTRL_Z));
        assertEquals(
            7,
            m.units(0)
                .size());
        assertTrue(m.w <= 128 && m.h <= 112);
    }

    @Test
    void theWellsPlanIsItsWalkway() {
        ModuleMap m = new ModuleMap(RootShape.BUILD, RootShape.UNITS, RootShape.DISSOLVES, 128, 112);
        assertEquals(1, m.storeys());
        assertEquals(RootShape.FLOOR_Y, m.level(0), "the taps' level");
        assertTrue(ModuleMap.flush(RootShape.BUILD, RootShape.UNITS, RootShape.FLOOR_Y), "set into the rim");
        assertEquals(ModuleMap.Mark.LIFT, m.mark(0, RootShape.PAD_X, RootShape.PAD_Z));
        assertEquals(ModuleMap.Mark.CONSOLE, m.mark(0, RootShape.CTRL_X, RootShape.CTRL_Z));
        int water = 0, floor = 0, fixture = 0, wall = 0;
        for (int x = 0; x < m.width; x++) for (int z = 0; z < m.depth; z++) {
            switch (m.mark(0, x, z)) {
                case WATER:
                    water++;
                    break;
                case FLOOR:
                    floor++;
                    break;
                case FIXTURE:
                    fixture++;
                    break;
                case WALL:
                case PILASTER:
                case SOLID:
                    wall++;
                    break;
                default:
                    break;
            }
        }
        assertTrue(water > 50, "the pool: " + water);
        assertTrue(floor > 100, "the island, the bridges and the walkway: " + floor);
        assertTrue(fixture > 20, "the pool's rim: " + fixture);
        assertTrue(wall > 50 && wall < 200, "the shaft's wall, not its floor: " + wall);
        assertEquals(
            8,
            m.units(0)
                .size());
        assertFalse(ModuleMap.flush(EcoDomeShape.BUILD, EcoDomeShape.UNITS, 1), "the dome's planters stand on it");
    }

    @Test
    void theAxisSitesAreNamed() {
        assertEquals("build.gui.site", SiteFamily.labelKey(4));
        assertEquals("build.gui.site", SiteFamily.labelKey(0));
        assertEquals("build.gui.site_zenith", SiteFamily.labelKey(SiteFamily.ZENITH_SITE));
        assertEquals("build.gui.site_root", SiteFamily.labelKey(SiteFamily.ROOT_SITE));
    }
}
