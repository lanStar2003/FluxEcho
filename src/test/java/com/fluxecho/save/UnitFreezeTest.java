package com.fluxecho.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The unit tables of 0.16.0 are frozen (refactor §4.9): every building, in every shape it may stand as, keeps the units
 * it had in the same order with the same names, kinds and slot counts, each at the slot it started at, because the
 * samples are saved by slot number ({@code Samples}). A building may only append units after them (and its handler
 * may only grow); a unit appended has no cell in an old shape, so an old plan never changes. The 0.16.0 tables are
 * the snapshot {@code ids/0.16.0.json} the 0.16.0 code exported (its {@code unit} category); the numbers of §4.9 are
 * checked against it too, so the snapshot and the design say the same.
 */
class UnitFreezeTest {

    private static Map<String, String> frozen, now;

    @BeforeAll
    static void read() throws IOException {
        frozen = IdExport.read(IdFreezeTest.SNAPSHOT)
            .get("unit");
        now = IdExport.units();
    }

    @Test
    void everyUnitOf016KeepsItsPlaceNameKindAndSlots() {
        List<String> wrong = new ArrayList<>();
        for (Map.Entry<String, String> e : frozen.entrySet()) {
            String key = e.getKey(), was = e.getValue(), is = now.get(key);
            if (key.contains("#")) {
                if (!was.equals(is))
                    wrong.add(key + ": \"" + was + "\" is now " + (is == null ? "gone" : "\"" + is + "\""));
            } else if (is == null || Integer.parseInt(is) < Integer.parseInt(was))
                wrong.add(key + ": " + was + " is now " + is + " (units are only appended, a handler only grows)");
        }
        if (!wrong.isEmpty())
            fail(wrong.size() + " unit table entries of 0.16.0 changed:\n  " + String.join("\n  ", wrong));
    }

    @Test
    void theAppendedUnitsComeAfterTheFrozenOnes() {
        // a building's units are numbered from 0 without a gap: what is new starts at the old count
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : frozen.entrySet()) if (e.getKey()
            .endsWith(":units"))
            counts.put(
                e.getKey()
                    .substring(
                        0,
                        e.getKey()
                            .length() - ":units".length()),
                Integer.parseInt(e.getValue()));
        for (Map.Entry<String, Integer> c : counts.entrySet()) {
            int n = Integer.parseInt(now.get(c.getKey() + ":units"));
            for (int i = 0; i < n; i++)
                assertTrue(now.containsKey(String.format("%s#%03d", c.getKey(), i)), c.getKey() + " unit " + i);
            int lastSlot = -1;
            for (int i = 0; i < n; i++) {
                String[] u = now.get(String.format("%s#%03d", c.getKey(), i))
                    .split("\\|");
                int first = Integer.parseInt(u[3]);
                assertTrue(
                    first > lastSlot || Integer.parseInt(u[2]) == 0,
                    c.getKey() + " unit " + i + " slots in order");
                if (Integer.parseInt(u[2]) > 0) lastSlot = first + Integer.parseInt(u[2]) - 1;
            }
        }
    }

    /** The 0.16.0 column of refactor §4.9 (and LEDGER §16): units, slots and the units' names and kinds. */
    @Test
    void theSnapshotIsTheTableOfTheDesign() {
        Object[][] table = { { "eco_dome/main", 12, 36, "G-RB", "planter", "C", "planter" },
            { "dual_foil_vault/main", 43, 43, null, "canvas", "foil", "foil" },
            { "root/well", 8, 16, "tap-1", "tap", "tap-8", "tap" },
            { "root/root", 16, 16, "tap-1", "tap", "bay-8", "bay" },
            { "root/root_in", 16, 16, "tap-1", "tap", "bay-8", "bay" },
            { "blood_covenant/main", 12, 12, "cradle-1", "cradle", "bay-4", "ritual" },
            { "hunting_ground/main", 12, 12, "imprint-1", "imprint", "imprint-12", "imprint" },
            { "sky_sails/main", 6, 6, "mast-1", "mast", "mast-6", "mast" },
            { "resonance_gate/main", 10, 24, "resonator-1", "resonator", null, "rack" },
            { "sophon_array/main", 48, 144, "rack-1", "memory_rack", "rack-48", "memory_rack" },
            { "holo_table/main", 10, 9, "table", "table", "layer-9", "console" },
            { "weather_station/main", 4, 4, "weather-1", "weather", "weather-4", "weather" },
            { "observatory/main", 7, 7, "lens-1", "lens", "lens-7", "lens" },
            { "droplet_hangar/main", 8, 8, "cradle-1", "droplet_cradle", "cradle-8", "droplet_cradle" },
            { "arcane_court/main", 21, 44, "infusion", "infusion", "desk-8", "desk" },
            { "mana_fountain/main", 16, 16, "fountain-1", "fountain", "urn-8", "urn" },
            { "dome_generator/moored", 7, 7, "ward-1", "ward", "ward-7", "ward" },
            { "dome_generator/floating", 7, 7, "ward-1", "ward", "ward-7", "ward" } };
        for (Object[] row : table) {
            String b = "fluxecho:" + row[0];
            int units = (Integer) row[1], slots = (Integer) row[2];
            assertEquals(String.valueOf(units), frozen.get(b + ":units"), b + " units");
            assertEquals(String.valueOf(slots), frozen.get(b + ":handler"), b + " slots");
            String[] first = frozen.get(b + "#000")
                .split("\\|"),
                last = frozen.get(String.format("%s#%03d", b, units - 1))
                    .split("\\|");
            if (row[3] != null) assertEquals(row[3], first[0], b + " first unit");
            assertEquals(row[4], first[1], b + " first kind");
            if (row[5] != null) assertEquals(row[5], last[0], b + " last unit");
            assertEquals(row[6], last[1], b + " last kind");
            int sum = 0;
            for (int i = 0; i < units; i++) sum += Integer.parseInt(
                frozen.get(String.format("%s#%03d", b, i))
                    .split("\\|")[2]);
            // the well keeps sixteen slots for the bays of the deep root it may grow into (0.16.0): its eight taps
            // fill half of them; every other building's units fill its handler
            if (b.startsWith("fluxecho:root/well")) assertEquals(8, sum, b + ": the taps' slots");
            else assertEquals(slots, sum, b + ": the units' slots fill the handler");
        }
        // the Archive: 78 bookcases of nine, 702 books; the 0.9.2 hall's 167 shelves in the same handler
        assertEquals("78", frozen.get("fluxecho:library/main:units"));
        assertEquals("702", frozen.get("fluxecho:library/main:handler"));
        assertEquals("167", frozen.get("fluxecho:library/hall:handler"));
        assertEquals("U-B-6|bookcase|9|693", frozen.get("fluxecho:library/main#077"));
    }
}
