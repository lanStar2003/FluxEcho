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
                case "port" -> p.port = true;
                case "loose" -> p.loose = true;
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
            "port",
            "loose",
            "oursInOtherStructure",
            "otherNexusArea",
            "crafted");
        p.clear();
        assertEquals(Verdict.BLOCKED, TerrainRule.forBuild(p), "a cleared probe is an unknown solid block");
        assertFalse(p.air || p.replaceable || p.target || p.fluid || p.lava || p.hasTile || p.unbreakable);
        assertFalse(p.natural || p.gtOre || p.ours || p.oursInOtherStructure || p.otherNexusArea || p.crafted);
        assertFalse(p.port || p.loose);
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
        n.rawGtStone = true;
        assertTrue(TerrainRule.natural(n), "GT's raw granite");

        n = new Nature();
        n.rock = true;
        assertFalse(TerrainRule.natural(n), "another mod's plain rock: nothing says it was generated");

        n = new Nature();
        n.replaceable = true;
        assertTrue(TerrainRule.natural(n), "a snow layer or water");
    }

    /** A whole, uncrafted rock cube, as the server fills it for the block named. */
    private static Nature rock(String registryName, int meta, boolean mesa, String className) {
        Nature n = new Nature();
        n.rock = true;
        n.naturalRock = TerrainRule.naturalRock(registryName, meta);
        n.mesaClay = TerrainRule.mesaClay(registryName, mesa);
        n.rawGtStone = TerrainRule.rawGtStone(className, meta);
        return n;
    }

    @Test
    void onlyRockKnownToBeGeneratedIsNatural() {
        String gt = "gregtech.common.blocks.";
        // what the world generates
        assertTrue(TerrainRule.natural(rock("minecraft:netherrack", 0, false, "")));
        assertTrue(TerrainRule.natural(rock("minecraft:end_stone", 0, false, "")));
        assertTrue(TerrainRule.natural(rock("minecraft:sandstone", 0, false, "")), "plain sandstone");
        assertTrue(TerrainRule.natural(rock("minecraft:stone", 0, false, "")));
        assertTrue(TerrainRule.natural(rock("gregtech:gt.blockgranites", 0, false, gt + "BlockGranites")));
        assertTrue(TerrainRule.natural(rock("gregtech:gt.blockgranites", 8, false, gt + "BlockGranites")), "red");
        assertTrue(TerrainRule.natural(rock("gregtech:gt.blockstones", 0, false, gt + "BlockStones")), "marble");
        assertTrue(TerrainRule.natural(rock("gregtech:gt.blockstones", 8, false, gt + "BlockStones")), "basalt");
        assertTrue(TerrainRule.natural(rock("minecraft:hardened_clay", 0, true, "")), "a mesa's clay");
        assertTrue(TerrainRule.natural(rock("minecraft:stained_hardened_clay", 1, true, "")), "a mesa's bands");

        // what a player builds with
        assertFalse(
            TerrainRule.natural(rock("gregtech:gt.blockconcretes", 0, false, gt + "BlockConcretes")),
            "concrete");
        assertFalse(TerrainRule.natural(rock("gregtech:gt.blockconcretes", 8, false, gt + "BlockConcretes")));
        assertFalse(TerrainRule.natural(rock("gregtech:gt.blockgranites", 3, false, gt + "BlockGranites")), "bricks");
        assertFalse(TerrainRule.natural(rock("minecraft:quartz_block", 0, false, "")), "quartz");
        assertFalse(TerrainRule.natural(rock("minecraft:obsidian", 0, false, "")), "a nether portal's frame");
        assertFalse(TerrainRule.natural(rock("minecraft:sandstone", 2, false, "")), "smooth sandstone");
        assertFalse(TerrainRule.natural(rock("minecraft:sandstone", 1, false, "")), "chiselled sandstone");
        assertFalse(TerrainRule.natural(rock("minecraft:hardened_clay", 0, false, "")), "clay baked by a player");
        assertFalse(TerrainRule.natural(rock("minecraft:stained_hardened_clay", 4, false, "")));
        assertFalse(TerrainRule.natural(rock("minecraft:double_stone_slab", 0, false, "")), "a double slab");
        assertFalse(TerrainRule.natural(rock("Thaumcraft:blockCosmeticSolid", 0, false, "")), "arcane stone");
        assertFalse(TerrainRule.natural(rock("Botania:livingrock", 0, false, "")), "livingrock");
        assertFalse(TerrainRule.natural(rock("chisel:marble", 0, false, "")), "another mod's stone");

        // an ore of the ore dictionary is natural; the config lists still decide first
        Nature ore = rock("Thaumcraft:blockCustomOre", 0, false, "");
        ore.ore = true;
        assertTrue(TerrainRule.natural(ore), "cinnabar ore");
        Nature listed = rock("chisel:marble", 0, false, "");
        listed.clearable = true;
        assertTrue(TerrainRule.natural(listed), "a stone the player lists as clearable");
        Nature granite = rock("gregtech:gt.blockgranites", 0, false, gt + "BlockGranites");
        granite.blocked = true;
        assertFalse(TerrainRule.natural(granite), "a stone the player lists as blocked");
        granite = rock("gregtech:gt.blockgranites", 0, false, gt + "BlockGranites");
        granite.shaped = true;
        assertFalse(TerrainRule.natural(granite), "a shaped block is never natural rock");

        // and a block the builder does not take is blocked, never cleared
        Probe quartz = new Probe();
        quartz.natural = TerrainRule.natural(rock("minecraft:quartz_block", 0, false, ""));
        assertEquals(Verdict.BLOCKED, TerrainRule.forClear(quartz));
        assertEquals(Verdict.BLOCKED, TerrainRule.forBuild(quartz));
    }

    @Test
    void rockNames() {
        assertTrue(TerrainRule.naturalRock("minecraft:stone", 0));
        assertFalse(TerrainRule.naturalRock("minecraft:stonebrick", 0));
        assertFalse(TerrainRule.naturalRock(null, 0));
        assertFalse(TerrainRule.mesaClay("minecraft:hardened_clay", false));
        assertFalse(TerrainRule.mesaClay("minecraft:clay", true), "soft clay is judged by its material");
        assertTrue(TerrainRule.rawGtStone(TerrainRule.GT_GRANITES, 0));
        assertFalse(TerrainRule.rawGtStone(TerrainRule.GT_GRANITES, 7), "smooth granite");
        assertFalse(TerrainRule.rawGtStone("gregtech.common.blocks.BlockConcretes", 0));
        assertFalse(TerrainRule.rawGtStone("gregtech.common.blocks.BlockStonesAbstract", 0));
        assertFalse(TerrainRule.rawGtStone(null, 0));
    }

    /** A probe of one of our blocks: a frame, deck or fitting block, or with {@code port} our supply port. */
    private static Probe ours(boolean port, boolean loose) {
        Probe p = new Probe();
        if (port) {
            p.port = true;
            p.hasTile = true;
        } else p.ours = true;
        p.loose = loose;
        return p;
    }

    @Test
    void aModuleJobTakesOurLeftoverBlocks() {
        // a 0.9.2 hall's frame left in an Archive's box after its core was broken
        assertEquals(Verdict.REPLACE, TerrainRule.forClear(ours(false, true), true), "taken away and credited");
        assertEquals(Verdict.BLOCKED, TerrainRule.forClear(ours(false, true), false), "never by the nexus's jobs");
        assertEquals(Verdict.BLOCKED, TerrainRule.forClear(ours(false, true)));
        assertEquals(Verdict.BLOCKED, TerrainRule.forClear(ours(false, false), true), "a formed structure's reach");
        // the supply port has a tile entity: a module job takes it only when it is loose
        assertEquals(Verdict.REPLACE, TerrainRule.forClear(ours(true, true), true));
        assertEquals(Verdict.BLOCKED, TerrainRule.forClear(ours(true, false), true));
        assertEquals(Verdict.BLOCKED, TerrainRule.forClear(ours(true, true), false));
        assertEquals(Verdict.REPLACE, TerrainRule.forBuild(ours(true, true), true), "a step's cell");
        assertEquals(Verdict.BLOCKED, TerrainRule.forBuild(ours(true, true), false));
        assertEquals(Verdict.BLOCKED, TerrainRule.forBuild(ours(true, false), true));
        // building over our frame is a replacement for every job, as before
        assertEquals(Verdict.REPLACE, TerrainRule.forBuild(ours(false, false), false));
        // another campus, or another structure's box, keeps them
        Probe other = ours(false, true);
        other.otherNexusArea = true;
        assertEquals(Verdict.BLOCKED, TerrainRule.forClear(other, true));
        other = ours(true, true);
        other.oursInOtherStructure = true;
        assertEquals(Verdict.BLOCKED, TerrainRule.forClear(other, true));
        assertEquals(Verdict.BLOCKED, TerrainRule.forBuild(other, true));
        // natural terrain and everything else is judged as for any job
        Probe stone = new Probe();
        stone.natural = true;
        assertEquals(Verdict.CLEAR, TerrainRule.forClear(stone, true));
        Probe chest = new Probe();
        chest.hasTile = true;
        chest.loose = true;
        assertEquals(Verdict.BLOCKED, TerrainRule.forClear(chest, true), "loose means nothing for a block not ours");
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
