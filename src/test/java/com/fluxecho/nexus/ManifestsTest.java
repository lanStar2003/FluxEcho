package com.fluxecho.nexus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fluxecho.logic.ResearchTree;
import com.fluxecho.matter.MatterModule;

/**
 * The manifestation table's own recipes for the phases' materials (0.12.0 resonant glass, 0.15.0 memory filament,
 * 0.16.0 manifest matter and the anti-gravity lattice): memory filament is the wiremill's phase alloy wire coated with
 * an echo crystal, manifest matter draws power of its own and waits for a nexus of phase III, and every research that
 * takes a material only the table makes needs the research that opens its recipe first, so it can always be paid for.
 */
class ManifestsTest {

    /** Every node a node needs, however far back. */
    private static Set<String> needs(ResearchTree.Node n) {
        Set<String> out = new HashSet<>();
        ArrayDeque<String> todo = new ArrayDeque<>(n.requires);
        while (!todo.isEmpty()) {
            String id = todo.pop();
            if (out.add(id)) todo.addAll(ResearchTree.get(id).requires);
        }
        return out;
    }

    @Test
    void memoryFilamentIsCoatedFromTheWiremillsWire() {
        Manifests.init();
        Manifests.Recipe r = Manifests.get(Manifests.MEMORY_FILAMENT);
        assertNotNull(r, "the table coats memory filament");
        assertEquals(ResearchTree.PHASE_2, r.research, "opened by phase II, before phase III asks for it");
        assertEquals(2, r.inputs.size());
        assertTrue(r.inputs.get(0).ore);
        assertEquals(MatterModule.PHASE_ALLOY_WIRE, r.inputs.get(0).name);
        assertEquals("wireFinePhaseAlloy", r.inputs.get(0).name, "GT's name for a fine wire of a material");
        assertEquals(MatterModule.WIRES_PER_INGOT, r.inputs.get(0).count, "one ingot's wire");
        assertTrue(r.inputs.get(1).ore);
        assertEquals(MatterModule.ECHO_CRYSTAL, r.inputs.get(1).name);
        assertEquals(1, r.inputs.get(1).count);
        assertFalse(r.record, "any echo crystal will do; it presses no codex entry");
        assertTrue(r.ticks > 0);
        assertEquals("wireMemoryFilament", MatterModule.MEMORY_FILAMENT);
    }

    /**
     * A research that takes resonant glass or memory filament needs the research that opens the table's recipe for it,
     * however far back, so the material can be made before the research is started.
     */
    @Test
    void everyTableMaterialAResearchTakesIsOpenedBeforeIt() {
        Manifests.init();
        String[][] made = { { MatterModule.RESONANT_GLASS, "resonant_glass" },
            { MatterModule.MEMORY_FILAMENT, Manifests.MEMORY_FILAMENT },
            { MatterModule.MANIFEST_MATTER, Manifests.MANIFEST_MATTER },
            { MatterModule.ANTI_GRAVITY_LATTICE, Manifests.ANTI_GRAVITY_LATTICE } };
        int asked = 0;
        for (String[] m : made) {
            Manifests.Recipe r = Manifests.get(m[1]);
            assertNotNull(r, m[1]);
            for (ResearchTree.Node n : ResearchTree.all()) {
                boolean takes = false;
                for (ResearchTree.Cost c : n.costs) takes |= c.ore && m[0].equals(c.name);
                if (!takes) continue;
                asked++;
                assertTrue(needs(n).contains(r.research), n.id + " takes " + m[0] + " before " + r.research);
            }
        }
        assertTrue(asked >= 17, "the dome, the well and the phase III and IV nodes ask for them");
    }

    /**
     * Manifest matter (0.16.0, BLUEPRINT 3.6): phase alloy, an echo crystal and an LuV circuit, opened by the Sophon
     * Array's research, made only by a nexus of phase III or later, for a long time and at an LuV power of its own on
     * top of the table's share; the anti-gravity lattice is manifest matter and resonant glass, opened by the sky ring.
     */
    @Test
    void manifestMatterAndTheLattice() {
        Manifests.init();
        Manifests.Recipe m = Manifests.get(Manifests.MANIFEST_MATTER);
        assertNotNull(m, "the table makes manifest matter");
        assertEquals(ResearchTree.SOPHON_ARRAY, m.research, "the nexus and the Sophon Array come first");
        Set<String> in = new HashSet<>();
        for (ResearchTree.Cost c : m.inputs) {
            assertTrue(c.ore, c.name);
            in.add(c.name);
        }
        assertEquals(
            new HashSet<>(java.util.Arrays.asList(MatterModule.PHASE_ALLOY, MatterModule.ECHO_CRYSTAL, "circuitLuV")),
            in);
        assertFalse(m.record, "any echo crystal will do");
        assertEquals(MatterModule.MANIFEST_MATTER_TICKS, m.ticks);
        assertTrue(m.ticks > Manifests.get(Manifests.MEMORY_FILAMENT).ticks, "long ticks");
        assertEquals(3, m.minPhase, "from phase III of the nexus on");
        assertFalse(m.runsAt(1));
        assertFalse(m.runsAt(2));
        assertTrue(m.runsAt(3));
        assertTrue(m.runsAt(4));
        assertEquals(30_720, m.extraEut, "LuV power of its own");
        assertEquals(480 + 30_720, m.tableEut(480), "on top of the table's share");
        assertEquals("ingotManifestMatter", MatterModule.MANIFEST_MATTER);

        Manifests.Recipe l = Manifests.get(Manifests.ANTI_GRAVITY_LATTICE);
        assertNotNull(l, "the table makes the anti-gravity lattice");
        assertEquals(ResearchTree.SKY_RING, l.research);
        assertEquals(2, l.inputs.size());
        assertEquals(MatterModule.MANIFEST_MATTER, l.inputs.get(0).name);
        assertEquals(MatterModule.RESONANT_GLASS, l.inputs.get(1).name);
        assertEquals(1, l.minPhase);
        assertEquals(0, l.extraEut);
        assertEquals("latticeAntiGravity", MatterModule.ANTI_GRAVITY_LATTICE);
        // the sky ring needs the Sophon Array, so the lattice's matter can be made when the lattice opens
        assertTrue(needs(ResearchTree.get(l.research)).contains(m.research));
    }

    /**
     * The recipes before 0.16.0 draw nothing of their own and run on any nexus (the old constructor's defaults), and
     * the table draws its share only while a recipe runs: nothing idle, the share and the recipe's own power while one
     * runs, and the share alone for a recipe no longer registered (a save from a game with other recipes).
     */
    @Test
    void theTableDrawsItsShareAndTheRecipesOwn() {
        Manifests.init();
        for (String id : new String[] { "echo_crystal", "resonant_glass", Manifests.MEMORY_FILAMENT }) {
            Manifests.Recipe r = Manifests.get(id);
            assertEquals(0, r.extraEut, id);
            assertEquals(1, r.minPhase, id);
            assertTrue(r.runsAt(1), id);
            assertEquals(480, Manifests.tableEut(id, 480), id);
        }
        assertEquals(0, Manifests.tableEut("", 480), "idle");
        assertEquals(0, Manifests.tableEut(null, 480));
        assertEquals(480, Manifests.tableEut("no_such_recipe", 480));
        assertEquals(480 + 30_720, Manifests.tableEut(Manifests.MANIFEST_MATTER, 480));
    }

    /**
     * Every circuit the table's recipes ask for (its materials, the module cores, the library's) is a tier the costs'
     * table translates (0.16.0): GT 5.09.51 registers no {@code circuitLuV}, so manifest matter, and with it the whole
     * phase IV, could otherwise never be paid. (GT's materials do not load on the test classpath, so the GT names
     * themselves, {@code circuitMaster} for LuV, are checked against the GT jar, not here.)
     */
    @Test
    void everyCircuitTheTableTakesIsGtsOwnOreName() {
        Manifests.init();
        com.fluxecho.city.CityModule.postInit();
        com.fluxecho.library.LibraryModule.postInit();
        int asked = 0;
        for (Manifests.Recipe r : Manifests.all()) for (ResearchTree.Cost c : r.inputs) {
            if (!c.ore || !c.name.startsWith("circuit")) continue;
            asked++;
            assertTrue(
                com.fluxecho.logic.CircuitTiers.tierOf(c.name) >= 0,
                r.id + " takes " + c.name + ", which GT never registers");
        }
        assertTrue(asked >= 15, "manifest matter and every core take circuits");
        ResearchTree.Cost other = ResearchTree.Cost.parse("ore:plateChrome*16");
        assertEquals("plateChrome", Costs.oreName(other), "any other ore cost is looked up as it is");
    }

    /**
     * Every recipe that takes more than one echo crystal leaves the table a spare input slot (0.15.0): crystals carry
     * the codex record they were pressed from and only crystals of one record stack, so with every slot given to a kind
     * of its own all of them would have to be one record, pressed once a day. With a spare slot they come from two
     * records (4 + 4 for the cores of 0.12.0 on). The table's six slots never grow: the output slots' indices after
     * them are saved.
     */
    @Test
    void aRecipeOfSeveralEchoCrystalsLeavesASlotForASecondRecord() {
        Manifests.init();
        com.fluxecho.city.CityModule.postInit();
        com.fluxecho.library.LibraryModule.postInit();
        assertEquals(6, TileNexus.INPUTS);
        int asked = 0;
        for (Manifests.Recipe r : Manifests.all()) {
            int crystals = 0;
            for (ResearchTree.Cost c : r.inputs)
                if (c.ore && MatterModule.ECHO_CRYSTAL.equals(c.name)) crystals += c.count;
            if (crystals <= 1) continue;
            asked++;
            assertTrue(
                r.inputs.size() < TileNexus.INPUTS,
                r.id + " takes " + crystals + " echo crystals in every slot");
        }
        assertTrue(asked >= 10, "every module core takes several crystals");
        assertNotNull(Manifests.get("sophon_array_core"));
        assertNotNull(Manifests.get("holo_table_core"));
    }
}
