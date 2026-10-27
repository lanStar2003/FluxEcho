package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fluxecho.campus.BlockDeck;
import com.fluxecho.campus.BlockFitting;
import com.fluxecho.logic.shape.Family;
import com.fluxecho.logic.shape.Form;
import com.fluxecho.logic.shape.FormCodes;
import com.fluxecho.logic.shape.SkinBlocks;

/**
 * The part codes are saved and sent, so they are only ever appended: the codes of 0.10 stay where they were and
 * 0.11.0's new parts take the next free ones (the cladding anchor 9, the socket 39, the basin rim 40, water 53, the
 * module cores 64 to 79), all within the one byte a {@code BUILD_FX} packet gives a code. 0.17.0 hands out the first
 * batch of the redesign (style FINAL §3.7, §3.8.1; ids.md §1.2, §1.4): the hull-number tile and the planting bed on
 * their families' bound codes, and all 41 free codes, 54 to 62 and 96 to 127, to the live materials and the new
 * families' first shapes.
 */
class PartsTest {

    @Test
    void theCodesOf010Stay() {
        assertEquals(0, Parts.frame(Parts.FR_BASE));
        assertEquals(8, Parts.frame(Parts.FR_CONSOLE));
        assertEquals(16, Parts.deck(Parts.D_DECK));
        assertEquals(32, Parts.fitting(Parts.F_POST));
        assertEquals(38, Parts.fitting(Parts.F_PEDESTAL));
        assertEquals(48, Parts.LIBRARY_CORE);
        assertEquals(49, Parts.SUPPLY_PORT);
        assertEquals(50, Parts.GRASS);
        assertEquals(51, Parts.DIRT);
        assertEquals(52, Parts.RECYCLE_BIN);
        assertEquals(63, Parts.AIR);
    }

    @Test
    void theCodesOf011() {
        assertEquals(9, Parts.frame(Parts.FR_ANCHOR));
        assertEquals(39, Parts.fitting(Parts.F_SOCKET));
        assertEquals(40, Parts.fitting(Parts.F_BASIN));
        assertEquals(53, Parts.WATER);
        assertEquals(64, Parts.moduleCore(ModuleCores.ECO_DOME));
        assertEquals(79, Parts.moduleCore(Parts.MODULE_CORES - 1));
        assertTrue(Parts.moduleCore(Parts.MODULE_CORES - 1) <= Byte.MAX_VALUE, "one byte a code");
        assertThrows(IllegalArgumentException.class, () -> Parts.moduleCore(Parts.MODULE_CORES));
        assertThrows(IllegalArgumentException.class, () -> Parts.moduleCore(-1));
        assertEquals(Parts.FITTING_TYPES, Parts.FITTING_NAMES.length);
        assertEquals(Parts.FITTING_TYPES, Parts.FITTING_LIGHT.length);
        assertEquals("socket", Parts.FITTING_NAMES[Parts.F_SOCKET]);
        assertEquals("basin", Parts.FITTING_NAMES[Parts.F_BASIN]);
    }

    /** 0.12.0 appends the light-lift pad: fitting 9, code 41, light 12 (CITY.md §1.4, Appendix C). */
    @Test
    void theCodesOf012() {
        assertEquals(41, Parts.fitting(Parts.F_LIFT));
        assertEquals("lift", Parts.FITTING_NAMES[Parts.F_LIFT]);
        assertEquals(12, Parts.FITTING_LIGHT[Parts.F_LIFT]);
        assertEquals(Parts.F_LIFT, Parts.meta(41));
        assertFalse(Parts.euOnly(41));
    }

    /**
     * 0.14.0 appends the canvas: deck meta 12, code 28, no light (CITY.md §1.4), the wall a module's unit paints its
     * item on; the deck family still ends before the fittings' codes.
     */
    @Test
    void theCodesOf014() {
        assertEquals(12, Parts.D_CANVAS);
        assertEquals(28, Parts.deck(Parts.D_CANVAS));
        assertEquals(13, Parts.DECK_TYPES);
        assertEquals(Parts.DECK_TYPES, Parts.DECK_NAMES.length);
        assertEquals(Parts.DECK_TYPES, Parts.DECK_LIGHT.length);
        assertEquals("canvas", Parts.DECK_NAMES[Parts.D_CANVAS]);
        assertEquals(0, Parts.DECK_LIGHT[Parts.D_CANVAS]);
        assertTrue(Parts.isDeck(28));
        assertEquals(Parts.D_CANVAS, Parts.meta(28));
        assertFalse(Parts.euOnly(28));
        assertTrue(Parts.deck(Parts.DECK_TYPES - 1) < Parts.fitting(0), "the deck's codes end before the fittings'");
        // the eleven deck metas before it keep their codes
        assertEquals(27, Parts.deck(Parts.D_CORNICE));
        assertEquals("cornice", Parts.DECK_NAMES[Parts.D_CORNICE]);
    }

    /**
     * 0.16.0 appends the floating foundation: frame meta 10, code 10, light 7, a whole cube (the sky platforms' walking
     * deck), legend {@code f} (CITY.md §1.4, Appendix A and C); the frame's other metas keep their codes.
     */
    @Test
    void theCodesOf016() {
        assertEquals(10, Parts.FR_FLOAT);
        assertEquals(10, Parts.frame(Parts.FR_FLOAT));
        assertTrue(Parts.isFrame(10));
        assertEquals(Parts.FR_FLOAT, Parts.meta(10));
        assertFalse(Parts.euOnly(10));
        assertEquals(Parts.frame(Parts.FR_FLOAT), ShapeKit.partOf('f'));
        assertTrue(ShapeKit.checked('f'));
        assertEquals(11, com.fluxecho.frame.BlockFrame.TYPES);
        assertEquals(com.fluxecho.frame.BlockFrame.TYPES, com.fluxecho.frame.BlockFrame.NAMES.length);
        assertEquals("float", com.fluxecho.frame.BlockFrame.NAMES[com.fluxecho.frame.BlockFrame.FLOAT]);
        assertEquals(Parts.FR_FLOAT, com.fluxecho.frame.BlockFrame.FLOAT);
        assertEquals(7, com.fluxecho.frame.BlockFrame.light(Parts.FR_FLOAT));
        assertTrue(com.fluxecho.frame.BlockFrame.full(Parts.FR_FLOAT), "a deck to walk on");
        assertArrayEquals(
            new float[] { 0, 0, 0, 1, 1, 1 },
            com.fluxecho.frame.BlockFrame.box(Parts.FR_FLOAT),
            "a whole cube");
        int[] light = { 0, 7, 0, 12, 8, 15, 4, 8, 10, 6, 7 };
        for (int m = 0; m < light.length; m++)
            assertEquals(light[m], com.fluxecho.frame.BlockFrame.light(m), "the light of frame meta " + m);
        assertEquals(0, com.fluxecho.frame.BlockFrame.light(com.fluxecho.frame.BlockFrame.TYPES));
        assertEquals(9, Parts.frame(Parts.FR_ANCHOR), "the anchor keeps its code");
    }

    @Test
    void familiesAndMetas() {
        Set<Integer> seen = new HashSet<>();
        for (int code = 0; code < 128; code++) {
            int families = (Parts.isFrame(code) ? 1 : 0) + (Parts.isDeck(code) ? 1 : 0)
                + (Parts.isFitting(code) ? 1 : 0)
                + (Parts.isModuleCore(code) ? 1 : 0);
            assertTrue(families <= 1, "code " + code + " in one family at most");
            if (Parts.isModuleCore(code)) {
                assertTrue(seen.add(Parts.meta(code)));
                assertEquals(code, Parts.moduleCore(Parts.meta(code)));
            }
        }
        assertEquals(Parts.MODULE_CORES, seen.size());
        assertEquals(Parts.F_BASIN, Parts.meta(Parts.fitting(Parts.F_BASIN)));
        assertEquals(Parts.FR_ANCHOR, Parts.meta(Parts.frame(Parts.FR_ANCHOR)));
        assertFalse(Parts.isModuleCore(Parts.LIBRARY_CORE), "the library core keeps its own block");
        assertFalse(Parts.isModuleCore(Parts.WATER));
    }

    @Test
    void waterGrassAndDirtArePaidInEu() {
        assertTrue(Parts.euOnly(Parts.GRASS));
        assertTrue(Parts.euOnly(Parts.DIRT));
        assertTrue(Parts.euOnly(Parts.WATER));
        assertFalse(Parts.euOnly(Parts.AIR));
        assertFalse(Parts.euOnly(Parts.fitting(Parts.F_BASIN)));
        assertFalse(Parts.euOnly(Parts.moduleCore(0)));
    }

    @Test
    void moduleCoreKeys() {
        assertEquals("eco_dome", ModuleCores.key(ModuleCores.ECO_DOME));
        assertEquals(ModuleCores.ECO_DOME, ModuleCores.meta(EcoDomeShape.KEY));
        assertEquals(Parts.MODULE_CORES, ModuleCores.KEYS.length);
        Set<String> keys = new HashSet<>();
        for (String k : ModuleCores.KEYS) assertTrue(keys.add(k), k);
        assertEquals(-1, ModuleCores.meta("archive"), "the Archive keeps the library core");
        assertTrue(ModuleCores.built(ModuleCores.ECO_DOME));
        // 0.12.0: the Echo Dome Generator's core is meta 7 (CITY.md Appendix C)
        assertEquals(7, ModuleCores.DOME_GENERATOR);
        assertEquals("dome_generator", ModuleCores.key(ModuleCores.DOME_GENERATOR));
        assertEquals(ModuleCores.DOME_GENERATOR, ModuleCores.meta(DomeGeneratorShape.KEY));
        assertTrue(ModuleCores.built(ModuleCores.DOME_GENERATOR));
        // and the root's Tidal Well meta 8
        assertEquals(8, ModuleCores.ROOT);
        assertEquals("root", ModuleCores.key(ModuleCores.ROOT));
        assertEquals(ModuleCores.ROOT, ModuleCores.meta(RootShape.KEY));
        assertTrue(ModuleCores.built(ModuleCores.ROOT));
        // 0.13.0: the Arcane Court's core is meta 2
        assertEquals(2, ModuleCores.ARCANE_COURT);
        assertEquals("arcane_court", ModuleCores.key(ModuleCores.ARCANE_COURT));
        assertEquals(ModuleCores.ARCANE_COURT, ModuleCores.meta(ArcaneCourtShape.KEY));
        assertTrue(ModuleCores.built(ModuleCores.ARCANE_COURT));
        assertEquals(66, Parts.moduleCore(ModuleCores.ARCANE_COURT));
        // and the other three pavilions' metas 3, 4 and 5
        assertEquals(3, ModuleCores.BLOOD_COVENANT);
        assertEquals(4, ModuleCores.HUNTING_GROUND);
        assertEquals(5, ModuleCores.MANA_FOUNTAIN);
        assertEquals(ModuleCores.BLOOD_COVENANT, ModuleCores.meta(BloodCovenantShape.KEY));
        assertEquals(ModuleCores.HUNTING_GROUND, ModuleCores.meta(HuntingGroundShape.KEY));
        assertEquals(ModuleCores.MANA_FOUNTAIN, ModuleCores.meta(ManaFountainShape.KEY));
        assertEquals(67, Parts.moduleCore(ModuleCores.BLOOD_COVENANT));
        assertEquals(68, Parts.moduleCore(ModuleCores.HUNTING_GROUND));
        assertEquals(69, Parts.moduleCore(ModuleCores.MANA_FOUNTAIN));
        // 0.14.0: the gate's Resonance Gate, meta 6, code 70
        assertEquals(6, ModuleCores.RESONANCE_GATE);
        assertEquals("resonance_gate", ModuleCores.key(ModuleCores.RESONANCE_GATE));
        assertEquals(ModuleCores.RESONANCE_GATE, ModuleCores.meta(ResonanceGateShape.KEY));
        assertTrue(ModuleCores.built(ModuleCores.RESONANCE_GATE));
        assertEquals(70, Parts.moduleCore(ModuleCores.RESONANCE_GATE));
        // and the Dual-foil Vault, meta 1, code 65
        assertEquals(1, ModuleCores.DUAL_FOIL_VAULT);
        assertEquals("dual_foil_vault", ModuleCores.key(ModuleCores.DUAL_FOIL_VAULT));
        assertEquals(ModuleCores.DUAL_FOIL_VAULT, ModuleCores.meta(DualFoilVaultShape.KEY));
        assertTrue(ModuleCores.built(ModuleCores.DUAL_FOIL_VAULT));
        assertEquals(65, Parts.moduleCore(ModuleCores.DUAL_FOIL_VAULT));
        // 0.15.0: the outer diagonals' Sophon Array, meta 9, code 73, and Holo Sand Table, meta 10, code 74
        assertEquals(9, ModuleCores.SOPHON_ARRAY);
        assertEquals(10, ModuleCores.HOLO_TABLE);
        assertEquals("sophon_array", ModuleCores.key(ModuleCores.SOPHON_ARRAY));
        assertEquals("holo_table", ModuleCores.key(ModuleCores.HOLO_TABLE));
        assertEquals(ModuleCores.SOPHON_ARRAY, ModuleCores.meta(SophonArrayShape.KEY));
        assertEquals(ModuleCores.HOLO_TABLE, ModuleCores.meta(HoloTableShape.KEY));
        assertTrue(ModuleCores.built(ModuleCores.SOPHON_ARRAY));
        assertTrue(ModuleCores.built(ModuleCores.HOLO_TABLE));
        assertEquals(73, Parts.moduleCore(ModuleCores.SOPHON_ARRAY));
        assertEquals(74, Parts.moduleCore(ModuleCores.HOLO_TABLE));
        // the sky ring's four (0.16.0); the orbital anchor's is still reserved
        assertEquals(ModuleCores.SKY_SAILS, ModuleCores.meta(SkySailsShape.KEY));
        assertEquals(ModuleCores.OBSERVATORY, ModuleCores.meta(ObservatoryShape.KEY));
        assertEquals(ModuleCores.DROPLET_HANGAR, ModuleCores.meta(DropletHangarShape.KEY));
        assertEquals(ModuleCores.WEATHER_STATION, ModuleCores.meta(WeatherStationShape.KEY));
        assertEquals(75, Parts.moduleCore(ModuleCores.SKY_SAILS));
        assertEquals(78, Parts.moduleCore(ModuleCores.WEATHER_STATION));
        assertTrue(ModuleCores.built(ModuleCores.WEATHER_STATION));
        assertFalse(ModuleCores.built(ModuleCores.ORBITAL_ANCHOR), "the orbital anchor's is still reserved");
        assertArrayEquals(
            new int[] { ModuleCores.ECO_DOME, ModuleCores.DUAL_FOIL_VAULT, ModuleCores.ARCANE_COURT,
                ModuleCores.BLOOD_COVENANT, ModuleCores.HUNTING_GROUND, ModuleCores.MANA_FOUNTAIN,
                ModuleCores.RESONANCE_GATE, ModuleCores.DOME_GENERATOR, ModuleCores.ROOT, ModuleCores.SOPHON_ARRAY,
                ModuleCores.HOLO_TABLE, ModuleCores.SKY_SAILS, ModuleCores.OBSERVATORY, ModuleCores.DROPLET_HANGAR,
                ModuleCores.WEATHER_STATION },
            ModuleCores.built());
        int last = -1;
        for (int m : ModuleCores.built()) {
            assertTrue(m > last, "in meta order");
            last = m;
        }
    }

    /**
     * 0.17.0 (style FINAL §3.7, §3.8.1): deck meta 13 the hull-number tile at code 29, fitting meta 14 the planting bed
     * at code 46, both on their families' bound codes; the 0.16.0 runs of the deck and the fittings stay as they were.
     */
    @Test
    void theBoundCodesOf017() {
        assertEquals(13, Parts.D_HULL_NUMBER);
        assertEquals(29, Parts.deck(Parts.D_HULL_NUMBER));
        assertTrue(Parts.isDeck(29));
        assertEquals(Parts.D_HULL_NUMBER, Parts.meta(29));
        assertEquals(14, Parts.F_BED);
        assertEquals(46, Parts.fitting(Parts.F_BED));
        assertTrue(Parts.isFitting(46));
        assertEquals(Parts.F_BED, Parts.meta(46));
        assertEquals(13, Parts.DECK_TYPES, "the 0.16.0 run of the deck");
        assertEquals(10, Parts.FITTING_TYPES, "the 0.16.0 run of the fittings");
        assertTrue(BlockDeck.known(Parts.D_HULL_NUMBER));
        assertFalse(BlockDeck.known(14), "deck 14 is the 0.22.0 pad's");
        assertFalse(BlockDeck.known(15));
        assertTrue(BlockFitting.known(Parts.F_BED));
        for (int m = Parts.FITTING_TYPES; m < 16; m++)
            if (m != Parts.F_BED) assertFalse(BlockFitting.known(m), "fitting " + m + " is free");
    }

    /**
     * 0.17.0's free codes (style FINAL §3.8.1): the live materials 54 to 58, the hull 96 to 107, the footing 108 to
     * 117,
     * the script skin 118 to 125 and the garden skin 126, 127 and 59 to 62: each of the 41 used once, none in an old
     * family, each family's shape number back to its code.
     */
    @Test
    void theFreeCodesOf017() {
        assertEquals(54, Parts.BIRCH);
        assertEquals(55, Parts.OAK_LEAVES);
        assertEquals(56, Parts.VINE);
        assertEquals(57, Parts.LILY);
        assertEquals(58, Parts.FLOWER);
        Set<Integer> free = new HashSet<>();
        for (int c = 54; c <= 62; c++) free.add(c);
        for (int c = 96; c <= 127; c++) free.add(c);
        assertEquals(41, free.size());
        Set<Integer> used = new HashSet<>();
        int[] perFamily = new int[Family.values().length];
        for (int code = 0; code < 256; code++) {
            boolean live = Parts.isLive(code);
            Family f = FormCodes.family(code);
            if (!live && f == null) continue;
            assertFalse(live && f != null, "code " + code + " is one thing");
            assertTrue(free.contains(code), "code " + code + " is one of the 41");
            assertFalse(
                Parts.isFrame(code) || Parts.isDeck(code) || Parts.isFitting(code) || Parts.isModuleCore(code),
                "code " + code + " in no old family");
            assertTrue(code <= Byte.MAX_VALUE, "one byte a code (BUILD_FX)");
            used.add(code);
            if (f != null) {
                perFamily[f.ordinal()]++;
                assertEquals(code, FormCodes.code(f, FormCodes.number(code)), "code " + code + " both ways");
                assertNotNull(FormCodes.form(code), "code " + code + " has a shape");
            }
        }
        assertEquals(free, used, "all 41 free codes are used");
        assertEquals(12, perFamily[Family.HULL.ordinal()]);
        assertEquals(10, perFamily[Family.FOOTING.ordinal()]);
        assertEquals(8, perFamily[Family.SKIN_SCRIPT.ordinal()]);
        assertEquals(6, perFamily[Family.SKIN_GARDEN.ordinal()]);
        // the ledger's rows (ids.md §1.4): a few by value
        assertEquals(98, FormCodes.code(Family.HULL, 2));
        assertEquals(105, FormCodes.code(Family.HULL, 11), "the keel");
        assertEquals(115, FormCodes.code(Family.FOOTING, 9), "the pool lip");
        assertEquals(125, FormCodes.code(Family.SKIN_SCRIPT, 13), "the lantern panel");
        assertEquals(126, FormCodes.code(Family.SKIN_GARDEN, 0));
        assertEquals(62, FormCodes.code(Family.SKIN_GARDEN, 7), "the bronze-framed window");
        assertEquals(-1, FormCodes.code(Family.HULL, 8), "the hull's window waits for 0.20.0");
    }

    /**
     * The live materials (style FINAL §3.7): vanilla blocks at fixed metas (a birch log upright, oak leaves that never
     * decay), accepted in a plan's cell in any state the world may leave them in.
     */
    @Test
    void theLiveMaterials() {
        assertEquals(2, LiveParts.meta(Parts.BIRCH));
        assertEquals(4, LiveParts.meta(Parts.OAK_LEAVES));
        assertEquals(0, LiveParts.meta(Parts.VINE));
        assertEquals(0, LiveParts.meta(Parts.LILY));
        assertEquals(0, LiveParts.meta(Parts.FLOWER));
        assertThrows(IllegalArgumentException.class, () -> LiveParts.meta(Parts.WATER));
        assertTrue(LiveParts.accepts(Parts.BIRCH, 2));
        assertFalse(LiveParts.accepts(Parts.BIRCH, 0), "an oak log is not a birch log");
        assertTrue(LiveParts.accepts(Parts.BIRCH, 6), "a birch log on its side is still a birch log");
        assertTrue(LiveParts.accepts(Parts.OAK_LEAVES, 4));
        assertTrue(LiveParts.accepts(Parts.OAK_LEAVES, 8), "a decay check flagged it");
        assertFalse(LiveParts.accepts(Parts.OAK_LEAVES, 2), "birch leaves");
        assertTrue(LiveParts.accepts(Parts.VINE, 5));
        assertTrue(LiveParts.accepts(Parts.FLOWER, 3));
        assertFalse(LiveParts.accepts(Parts.GRASS, 0));
        for (int c = Parts.BIRCH; c <= Parts.FLOWER; c++) {
            assertTrue(Parts.isLive(c));
            assertTrue(LiveParts.is(c));
        }
        assertFalse(Parts.isLive(Parts.WATER));
        assertFalse(Parts.isLive(59), "59 is the garden skin's");
    }

    /**
     * Each family's shapes split between its two block ids by whether the shape is a whole cube; the 36 coded shapes
     * are the listed ones, every other shape of the 64 is registered, unlisted and no part (style FINAL §3.8.1).
     */
    @Test
    void theFamiliesBlockIds() {
        int listed = 0;
        for (Family f : Family.values()) {
            for (int n = 0; n < 16; n++) {
                boolean whole = SkinBlocks.inWhole(f, n);
                assertTrue(SkinBlocks.belongs(f, whole, n));
                assertFalse(SkinBlocks.belongs(f, !whole, n));
                assertEquals(-1, SkinBlocks.code(f, !whole, n), f + " " + n + " in the other id is no part");
                assertEquals(FormCodes.code(f, n), SkinBlocks.code(f, whole, n));
                if (SkinBlocks.listed(f, whole, n)) listed++;
                assertEquals(whole ? f.id : f.id + "_form", SkinBlocks.blockName(f, n));
            }
        }
        assertEquals(36, listed);
        assertEquals(Form.WHOLE, SkinBlocks.geometry(Family.HULL, false, 0), "a stray meta draws a whole cube");
        assertEquals("paper_lantern", SkinBlocks.slug(Family.SKIN_SCRIPT, 13));
        assertEquals("moss_window", SkinBlocks.slug(Family.SKIN_GARDEN, 7));
        assertEquals("pool_lip", SkinBlocks.slug(Family.FOOTING, 9));
        assertEquals("keel", SkinBlocks.slug(Family.HULL, 11));
    }
}
