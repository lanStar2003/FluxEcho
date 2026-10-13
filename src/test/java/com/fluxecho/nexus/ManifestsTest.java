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
 * The manifestation table's own recipes for the phases' materials (0.12.0 resonant glass, 0.15.0 memory filament):
 * memory filament is the wiremill's phase alloy wire coated with an echo crystal, and every research that takes a
 * material only the table makes needs the research that opens its recipe first, so it can always be paid for.
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
            { MatterModule.MEMORY_FILAMENT, Manifests.MEMORY_FILAMENT } };
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
        assertTrue(asked >= 6, "the dome, the well and the phase III nodes ask for them");
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
