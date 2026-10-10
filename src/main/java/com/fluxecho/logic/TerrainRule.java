package com.fluxecho.logic;

import java.util.Locale;

/**
 * What the campus builder may do with the block it finds in a cell. The server fills a {@link Probe} with plain facts
 * about the block (from Forge hooks, materials, tile entities, ownership and the config lists) and this class turns
 * them into a {@link Verdict}, so the terrain rules are the same everywhere and can be tested without a world.
 * <p>
 * The rules protect the player's things first: anything with a tile entity (except GT ores), anything built by hand
 * (cobblestone, planks, bricks, smooth stone), another nexus's campus and blocks of ours that belong to another
 * structure are never broken. Natural terrain (stone, dirt, logs, leaves, plants, ores) is cleared, and our own wrong
 * parts in this campus are replaced.
 */
public final class TerrainRule {

    /** The decision for one cell. */
    public enum Verdict {
        /** The cell already holds what it should (or nothing needs doing): skip it for free. */
        ALREADY,
        /** The cell is air, replaceable or a harmless fluid: place directly. */
        PLACE,
        /** The cell holds natural terrain or a GT ore: break it (keeping ore and log drops), then place if needed. */
        CLEAR,
        /**
         * The cell holds one of our own parts of this campus that is the wrong one: swap it, crediting the old part.
         */
        REPLACE,
        /** The builder must not touch the cell: pause (or skip with 跳过受阻) and show it red. */
        BLOCKED
    }

    /**
     * Plain facts about the block in a cell, filled by the server for every cell it classifies. All fields default to
     * false; {@link #clear()} resets them so one probe can be reused.
     */
    public static final class Probe {

        /** The cell is air. */
        public boolean air;
        /** {@code Block.isReplaceable}: tall grass, snow layers, vines, fluids. */
        public boolean replaceable;
        /** The cell already holds the wanted block with the wanted meta. */
        public boolean target;
        /** The block is a fluid (water, lava or a modded fluid). */
        public boolean fluid;
        /** The block is lava (material lava). */
        public boolean lava;
        /** The block has a tile entity. */
        public boolean hasTile;
        /** The block's hardness is below zero (bedrock, portal frames, other mods' indestructible blocks). */
        public boolean unbreakable;
        /** The block counts as natural terrain; see {@link TerrainRule#natural(Nature)}. */
        public boolean natural;
        /** The block is a GT, Bartworks or GT++ ore (some have a tile entity, but they may still be mined). */
        public boolean gtOre;
        /** The block is one of our deck, fitting or frame blocks. */
        public boolean ours;
        /** The block is ours but lies inside another formed structure (for example a 0.9.2 library hall). */
        public boolean oursInOtherStructure;
        /** The cell lies inside another nexus's campus area. */
        public boolean otherNexusArea;
        /** The block was built by a player: cobblestone, mossy cobblestone, stone bricks, planks, bricks, smooth. */
        public boolean crafted;

        /** Resets every fact to false. */
        public Probe clear() {
            air = replaceable = target = fluid = lava = hasTile = unbreakable = false;
            natural = gtOre = ours = oursInOtherStructure = otherNexusArea = crafted = false;
            return this;
        }
    }

    /**
     * The inputs that decide whether a block is natural terrain, filled by the server from Forge hooks and the block's
     * material (never from its name, except through {@link #crafted} and the two config lists). All fields default to
     * false.
     */
    public static final class Nature {

        /** The block has a tile entity. */
        public boolean hasTile;
        /** Forge {@code isWood} (logs in any orientation). */
        public boolean wood;
        /** Forge {@code isLeaves}. */
        public boolean leaves;
        /** {@code Block.isReplaceable}. */
        public boolean replaceable;
        /** The block implements {@code IPlantable} or {@code IShearable}. */
        public boolean plant;
        /** {@code isReplaceableOreGen(w, x, y, z, Blocks.stone)}: stone and the stones ore generation replaces. */
        public boolean oreGenStone;
        /**
         * The material is one of ground, grass, sand, clay, snow, crafted snow, ice, packed ice, leaves, plants, vine,
         * gourd, cactus or coral.
         */
        public boolean softMaterial;
        /** The material is rock. */
        public boolean rock;
        /**
         * The block is not a full opaque cube (stairs, slabs, walls, buttons, pressure plates). Natural rock always is
         * one, so a shaped rock block is treated as built by a player. Left false, rock is judged by name alone.
         */
        public boolean shaped;
        /** The block was built by a player (see {@link TerrainRule#craftedName(String)}); only matters for rock. */
        public boolean crafted;
        /** The block matches the {@code buildClearable} config list. */
        public boolean clearable;
        /**
         * The block matches the {@code buildBlocked} config list. The server should also set {@link Probe#crafted} for
         * such a block, so that neither {@link TerrainRule#forBuild} nor {@link TerrainRule#forClear} breaks it.
         */
        public boolean blocked;
    }

    private TerrainRule() {}

    /**
     * The verdict for the target cell of a HARD or SOFT step, in this order: the target block already → ALREADY; air,
     * replaceable or fluid, but not lava → PLACE; lava, unbreakable, another nexus's area or ours inside another
     * structure → BLOCKED; a GT ore → CLEAR; any other tile entity → BLOCKED; ours → REPLACE; crafted → BLOCKED;
     * natural → CLEAR; anything else → BLOCKED.
     */
    public static Verdict forBuild(Probe p) {
        if (p.target) return Verdict.ALREADY;
        if (!p.lava && (p.air || p.replaceable || p.fluid)) return Verdict.PLACE;
        if (p.lava || p.unbreakable || p.otherNexusArea || p.oursInOtherStructure) return Verdict.BLOCKED;
        if (p.gtOre) return Verdict.CLEAR;
        if (p.hasTile) return Verdict.BLOCKED;
        if (p.ours) return Verdict.REPLACE;
        if (p.crafted) return Verdict.BLOCKED;
        if (p.natural) return Verdict.CLEAR;
        return Verdict.BLOCKED;
    }

    /**
     * The verdict for a cell of a clearance or grading volume, which must end up empty: air or a replaceable block
     * that is not a fluid (tall grass, snow layers) → ALREADY, it may stay; unbreakable, another nexus's area or ours
     * inside another structure → BLOCKED; a GT ore → CLEAR (its drops go to the spoils, as in {@link #forBuild});
     * any other tile entity, our own blocks, crafted blocks and lava → BLOCKED; natural (water included, it is
     * replaceable) → CLEAR; anything else → BLOCKED.
     */
    public static Verdict forClear(Probe p) {
        if (p.air || (p.replaceable && !p.fluid)) return Verdict.ALREADY;
        if (p.unbreakable || p.otherNexusArea || p.oursInOtherStructure) return Verdict.BLOCKED;
        if (p.gtOre) return Verdict.CLEAR;
        if (p.hasTile || p.ours || p.crafted || p.lava) return Verdict.BLOCKED;
        if (p.natural) return Verdict.CLEAR;
        return Verdict.BLOCKED;
    }

    /**
     * Whether a block is natural terrain the builder may break: never when it is on the {@code buildBlocked} list or
     * has a tile entity (GT ores are handled by {@link Probe#gtOre} instead); always when it is on the
     * {@code buildClearable} list; otherwise when a Forge hook or the material says so (wood, leaves, replaceable,
     * plants, ore-generation stone, the soft materials), or when it is rock that was not built by a player (neither
     * crafted nor shaped).
     */
    public static boolean natural(Nature n) {
        if (n.blocked || n.hasTile) return false;
        if (n.clearable) return true;
        if (n.wood || n.leaves || n.replaceable || n.plant || n.oreGenStone || n.softMaterial) return true;
        return n.rock && !n.crafted && !n.shaped;
    }

    /**
     * Whether a registry name looks like a player-built block: its path contains {@code brick}, {@code smooth},
     * {@code cobble} or {@code planks} (case-insensitive). The server also treats vanilla cobblestone, mossy
     * cobblestone and stone bricks as crafted, which these words already cover.
     */
    public static boolean craftedName(String registryName) {
        if (registryName == null) return false;
        String n = registryName.toLowerCase(Locale.ROOT);
        return n.contains("brick") || n.contains("smooth") || n.contains("cobble") || n.contains("planks");
    }

    /**
     * Whether a registry name {@code modid:name} matches a config list: an entry matches when it equals the name, or
     * when it is {@code modid:*} with the same mod id (both case-insensitive). Blank entries and a null list match
     * nothing.
     */
    public static boolean listed(String registryName, Iterable<String> patterns) {
        if (registryName == null || patterns == null) return false;
        String name = registryName.trim()
            .toLowerCase(Locale.ROOT);
        int colon = name.indexOf(':');
        String mod = colon < 0 ? "" : name.substring(0, colon);
        for (String raw : patterns) {
            if (raw == null) continue;
            String pat = raw.trim()
                .toLowerCase(Locale.ROOT);
            if (pat.isEmpty()) continue;
            if (pat.equals(name)) return true;
            if (colon < 0 || !pat.endsWith(":*")) continue;
            String patMod = pat.substring(0, pat.length() - 2);
            if (patMod.equals(mod)) return true;
        }
        return false;
    }
}
