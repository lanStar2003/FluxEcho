package com.fluxecho.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fluxecho.logic.TerrainRule.Nature;
import com.fluxecho.logic.TerrainRule.Probe;
import com.fluxecho.logic.TerrainRule.Verdict;

class TerrainRuleTest {

    /** A probe with the named facts set. */
    private static Probe probe(String... facts) {
        Probe p = new Probe();
        for (String f : facts) {
            switch (f) {
                case "air" -> p.air = true;
                case "replaceable" -> p.replaceable = true;
                case "target" -> p.target = true;
                case "fluid" -> p.fluid = true;
                case "lava" -> p.lava = true;
                case "hasTile" -> p.hasTile = true;
                case "unbreakable" -> p.unbreakable = true;
                case "natural" -> p.natural = true;
                case "gtOre" -> p.gtOre = true;
                case "ours" -> p.ours = true;
                case "oursInOtherStructure" -> p.oursInOtherStructure = true;
                case "otherNexusArea" -> p.otherNexusArea = true;
                case "crafted" -> p.crafted = true;
                default -> throw new IllegalArgumentException(f);
            }
        }
        return p;
    }

    private static Verdict build(String... facts) {
        return TerrainRule.forBuild(probe(facts));
    }

    private static Verdict clear(String... facts) {
        return TerrainRule.forClear(probe(facts));
    }

    @Test
    void buildOnTheEverydayCells() {
        assertEquals(Verdict.PLACE, build("air"));
        assertEquals(Verdict.PLACE, build("replaceable", "natural"), "tall grass, snow layers, vines");
        assertEquals(Verdict.PLACE, build("fluid", "replaceable"), "water is replaced directly");
        assertEquals(Verdict.PLACE, build("fluid"), "a modded fluid that is not replaceable");
        assertEquals(Verdict.CLEAR, build("natural"), "stone, dirt, grass blocks, logs, leaves");
        assertEquals(Verdict.CLEAR, build("gtOre", "hasTile"), "GT ores have a tile entity but are mined");
        assertEquals(Verdict.CLEAR, build("gtOre", "natural"), "GT++ ores have none");
        assertEquals(Verdict.REPLACE, build("ours"), "our own wrong part in this campus");
        assertEquals(Verdict.BLOCKED, build("crafted"), "cobblestone, planks, stone bricks");
        assertEquals(Verdict.BLOCKED, build("hasTile"), "a chest");
        assertEquals(Verdict.BLOCKED, build(), "an unknown block from another mod");
    }

    @Test
    void theTargetIsAlwaysFree() {
        assertEquals(Verdict.ALREADY, build("target"));
        assertEquals(Verdict.ALREADY, build("target", "ours"));
        assertEquals(
            Verdict.ALREADY,
            build("target", "ours", "oursInOtherStructure"),
            "nothing to do, so nothing done");
        assertEquals(Verdict.ALREADY, build("target", "air"), "an AIR step already empty");
    }

    @Test
    void lavaAndUnbreakableAreBlocked() {
        assertEquals(Verdict.BLOCKED, build("lava", "fluid", "replaceable", "natural"), "lava, though replaceable");
        assertEquals(Verdict.BLOCKED, build("unbreakable", "natural"), "bedrock");
        assertEquals(Verdict.BLOCKED, build("unbreakable", "gtOre"), "an indestructible ore");
    }

    @Test
    void otherStructuresAndCampusesAreBlocked() {
        assertEquals(Verdict.BLOCKED, build("ours", "oursInOtherStructure"), "a 0.9.2 library hall's shelf");
        assertEquals(Verdict.BLOCKED, build("natural", "otherNexusArea"), "stone inside another campus");
        assertEquals(Verdict.BLOCKED, build("ours", "otherNexusArea"), "another nexus's deck");
        assertEquals(Verdict.BLOCKED, build("gtOre", "hasTile", "otherNexusArea"), "an ore inside another campus");
    }

    @Test
    void buildPrecedence() {
        // ours beats crafted and natural; crafted beats natural; a tile entity beats ours
        assertEquals(Verdict.REPLACE, build("ours", "crafted", "natural"));
        assertEquals(Verdict.BLOCKED, build("crafted", "natural"));
        assertEquals(Verdict.BLOCKED, build("hasTile", "ours"));
        assertEquals(Verdict.CLEAR, build("gtOre", "crafted"), "an ore is mined before the crafted test");
        // replaceable beats everything below it in the order, lava excepted
        assertEquals(Verdict.PLACE, build("replaceable", "hasTile"));
        assertEquals(Verdict.PLACE, build("air", "otherNexusArea"), "the order of the spec: air is placed first");
    }

    @Test
    void clearingVolumes() {
        assertEquals(Verdict.ALREADY, clear("air"));
        assertEquals(Verdict.ALREADY, clear("replaceable", "natural"), "tall grass may stay");
        assertEquals(Verdict.CLEAR, clear("natural"), "stone, dirt, logs, leaves");
        assertEquals(Verdict.CLEAR, clear("fluid", "replaceable", "natural"), "water is drained");
        assertEquals(Verdict.CLEAR, clear("gtOre", "hasTile"), "a GT ore");
        assertEquals(Verdict.BLOCKED, clear("lava", "fluid", "replaceable", "natural"), "lava");
        assertEquals(Verdict.BLOCKED, clear("crafted"), "a player's wall");
        assertEquals(Verdict.BLOCKED, clear("crafted", "natural"));
        assertEquals(Verdict.BLOCKED, clear("hasTile"), "a chest");
        assertEquals(Verdict.BLOCKED, clear("ours", "natural"), "our blocks are never cleared away");
        assertEquals(Verdict.BLOCKED, clear("unbreakable", "natural"), "bedrock");
        assertEquals(Verdict.BLOCKED, clear("natural", "otherNexusArea"), "another campus");
        assertEquals(Verdict.BLOCKED, clear("gtOre", "otherNexusArea"), "an ore in another campus");
        assertEquals(Verdict.BLOCKED, clear("natural", "oursInOtherStructure"));
        assertEquals(Verdict.BLOCKED, clear(), "an unknown block");
    }

    @Test
    void probesReset() {
        Probe p = probe(
            "air",
            "replaceable",
            "target",
            "fluid",
            "lava",
            "hasTile",
            "unbreakable",
            "natural",
            "gtOre",
            "ours",
            "oursInOtherStructure",
            "otherNexusArea",
            "crafted");
        p.clear();
        assertEquals(Verdict.BLOCKED, TerrainRule.forBuild(p), "a cleared probe is an unknown solid block");
        assertFalse(p.air || p.replaceable || p.target || p.fluid || p.lava || p.hasTile || p.unbreakable);
        assertFalse(p.natural || p.gtOre || p.ours || p.oursInOtherStructure || p.otherNexusArea || p.crafted);
    }

    @Test
    void naturalFromHooksAndMaterials() {
        Nature n = new Nature();
        n.rock = true;
        n.oreGenStone = true;
        assertTrue(TerrainRule.natural(n), "stone");

        n = new Nature();
        n.wood = true;
        assertTrue(TerrainRule.natural(n), "a log, in any orientation");

        n = new Nature();
        n.leaves = true;
        n.plant = true;
        assertTrue(TerrainRule.natural(n), "Biomes O' Plenty leaves");

        n = new Nature();
        n.softMaterial = true;
        assertTrue(TerrainRule.natural(n), "a modded grass or dirt block (material grass or ground)");

        n = new Nature();
        n.rock = true;
        assertTrue(TerrainRule.natural(n), "GT granite or another mod's plain rock");

        n = new Nature();
        n.replaceable = true;
        assertTrue(TerrainRule.natural(n), "a snow layer or water");
    }

    @Test
    void builtRockIsNotNatural() {
        Nature n = new Nature();
        n.rock = true;
        n.crafted = true;
        assertFalse(TerrainRule.natural(n), "cobblestone, stone bricks");

        n = new Nature();
        n.rock = true;
        n.shaped = true;
        assertFalse(TerrainRule.natural(n), "stone stairs and slabs");

        n = new Nature();
        n.rock = true;
        n.crafted = true;
        n.oreGenStone = true;
        assertTrue(TerrainRule.natural(n), "a hook that says stone wins over the name");

        n = new Nature();
        assertFalse(TerrainRule.natural(n), "metal, wood planks, wool, glass: nothing says natural");
    }

    @Test
    void tilesAndConfig() {
        Nature n = new Nature();
        n.rock = true;
        n.hasTile = true;
        assertFalse(TerrainRule.natural(n), "a rock block with a tile entity");

        n = new Nature();
        n.hasTile = true;
        n.clearable = true;
        assertFalse(TerrainRule.natural(n), "the clearable list never reaches a tile entity");

        n = new Nature();
        n.clearable = true;
        assertTrue(TerrainRule.natural(n), "the clearable list adds a block");

        n = new Nature();
        n.wood = true;
        n.blocked = true;
        assertFalse(TerrainRule.natural(n), "the blocked list forbids a block");

        n = new Nature();
        n.clearable = true;
        n.blocked = true;
        assertFalse(TerrainRule.natural(n), "blocked wins over clearable");
    }

    @Test
    void craftedNames() {
        assertTrue(TerrainRule.craftedName("minecraft:cobblestone"));
        assertTrue(TerrainRule.craftedName("minecraft:mossy_cobblestone"));
        assertTrue(TerrainRule.craftedName("minecraft:stonebrick"));
        assertTrue(TerrainRule.craftedName("minecraft:brick_block"));
        assertTrue(TerrainRule.craftedName("minecraft:nether_brick"));
        assertTrue(TerrainRule.craftedName("minecraft:planks"));
        assertTrue(TerrainRule.craftedName("BiomesOPlenty:planks"));
        assertTrue(TerrainRule.craftedName("chisel:SmoothStone"), "case-insensitive");
        assertFalse(TerrainRule.craftedName("minecraft:stone"));
        assertFalse(TerrainRule.craftedName("minecraft:log"));
        assertFalse(TerrainRule.craftedName("gregtech:gt.blockstones"));
        assertFalse(TerrainRule.craftedName(null));
    }

    @Test
    void configPatterns() {
        List<String> list = Arrays.asList("BiomesOPlenty:*", "Natura:tree", " ", "", null, "chisel:*");
        assertTrue(TerrainRule.listed("BiomesOPlenty:grass", list), "modid:* covers the whole mod");
        assertTrue(TerrainRule.listed("biomesoplenty:hardDirt", list), "case-insensitive");
        assertTrue(TerrainRule.listed("Natura:tree", list), "an exact name");
        assertFalse(TerrainRule.listed("Natura:bloodwood", list), "another block of a listed mod");
        assertTrue(TerrainRule.listed("chisel:marble", list));
        assertFalse(TerrainRule.listed("minecraft:stone", list));
        assertFalse(TerrainRule.listed("BiomesOPlentyExtra:grass", list), "a mod id must match whole");
        assertFalse(TerrainRule.listed("plain", list), "a name without a mod id matches no modid:* entry");
        assertFalse(TerrainRule.listed("minecraft:stone", Collections.<String>emptyList()));
        assertFalse(TerrainRule.listed("minecraft:stone", null));
        assertFalse(TerrainRule.listed(null, list));
    }
}
