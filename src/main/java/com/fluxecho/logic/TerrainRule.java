package com.fluxecho.logic;

import java.util.Locale;
import java.util.function.IntFunction;

/**
 * What the campus builder may do with the block it finds in a cell. The server fills a {@link Probe} with plain facts
 * about the block (from Forge hooks, materials, tile entities, ownership and the config lists) and this class turns
 * them into a {@link Verdict}, so the terrain rules are the same everywhere and can be tested without a world.
 * <p>
 * The rules protect the player's things first: anything with a tile entity (except GT ores), anything built by hand
 * (cobblestone, planks, bricks, smooth stone), another nexus's campus and blocks of ours that belong to another
 * structure are never broken. Natural terrain (stone, dirt, logs, leaves, plants, ores) is cleared, and our own wrong
 * parts in this campus are replaced. Rock is taken for natural terrain only when something positively says so
 * ({@link #natural}): players build with rock far more often than with dirt, and a block the builder is not sure of
 * is left standing.
 * <p>
 * A module job (one that builds or repairs a module on a hall site) also takes down our own frame, deck, fitting and
 * supply port blocks that no formed structure could use ({@link Probe#loose}), such as what is left of a 0.9.2 hall
 * whose core was broken, crediting each to the ledger as its part ({@link Verdict#REPLACE}); the nexus's own jobs
 * never do.
 * <p>
 * That is the conservative rule of 0.10.0. Since 0.10.1 the default is the <i>clear-all</i> rule ({@code all}, the
 * {@code buildClearAll} config): the player asked that the volume a job clears be emptied completely, machines and
 * chests included, everything going into the campus's recycle store. Under it only these stay: another nexus's campus,
 * anything inside another structure's box or a keep-out box ({@link Probe#protectedArea}), our own blocks inside a
 * formed structure's reach, unbreakable blocks, the {@code buildBlocked} list ({@link Probe#listed}), and what a
 * protection mod refuses (the builder learns that when it tries). The grading rules ({@link #forFloor} for the floor
 * cell of a graded column, {@link #forFill} for the ground under it) follow the chosen rule.
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
         * The cell holds one of our own parts of this campus that is the wrong one: swap it (or, in a cell that must be
         * empty, take it away), crediting the old part to the ledger.
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
        /**
         * The block is our supply port or recycle bin (they have a tile entity, so {@link #ours} leaves them out).
         */
        public boolean port;
        /**
         * The block is ours ({@link #ours} or {@link #port}) and lies inside no formed structure's reach (no formed
         * nexus's or module's {@code covers()}): left over, as a 0.9.2 hall's blocks are after its core was broken.
         */
        public boolean loose;
        /** The block is ours but lies inside another formed structure (for example a 0.9.2 library hall). */
        public boolean oursInOtherStructure;
        /** The cell lies inside another nexus's campus area. */
        public boolean otherNexusArea;
        /** The block was built by a player: cobblestone, mossy cobblestone, stone bricks, planks, bricks, smooth. */
        public boolean crafted;
        /**
         * A tile entity that is not an ore: a machine, a chest, an AE part. The clear-all rule takes it down (its
         * contents go to the recycle store); the survey counts these and the projection marks them in yellow.
         */
        public boolean te;
        /**
         * The cell lies inside another structure's box (a loaded multiblock other than the job's own, a module on a
         * recorded hall site other than the job's) or a keep-out box of the job, whatever the block is.
         */
        public boolean protectedArea;
        /** The block matches the {@code buildBlocked} config list: never broken. */
        public boolean listed;
        /**
         * Forge {@code isWood}, {@code isLeaves}; {@code IPlantable} or {@code IShearable}: what grading fills over.
         */
        public boolean wood, leaves, plant;

        /** Resets every fact to false. */
        public Probe clear() {
            air = replaceable = target = fluid = lava = hasTile = unbreakable = false;
            natural = gtOre = ours = port = loose = oursInOtherStructure = otherNexusArea = crafted = false;
            te = protectedArea = listed = wood = leaves = plant = false;
            return this;
        }

        /** Whether something forbids touching the cell under the clear-all rule, whatever the block is. */
        boolean guarded() {
            return unbreakable || otherNexusArea || oursInOtherStructure || protectedArea || listed;
        }
    }

    /**
     * The inputs that decide whether a block is natural terrain, filled by the server from Forge hooks, the block's
     * material, the ore dictionary and a short list of rock known to be generated ({@link TerrainRule#naturalRock},
     * {@link TerrainRule#mesaClay}, {@link TerrainRule#rawGtStone}). All fields default to false.
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
         * one, so a shaped rock block is treated as built by a player.
         */
        public boolean shaped;
        /** The block was built by a player (see {@link TerrainRule#craftedName(String)}); only matters for rock. */
        public boolean crafted;
        /** The block matches the {@code buildClearable} config list. */
        public boolean clearable;
        /** Vanilla rock the world generates: stone, netherrack, end stone, plain sandstone. */
        public boolean naturalRock;
        /** Hardened or stained clay in a mesa biome. */
        public boolean mesaClay;
        /** GT's raw granite, marble or basalt; never its concrete. */
        public boolean rawGtStone;
        /** An ore of the ore dictionary (one of its names starts with {@code ore}). */
        public boolean ore;
        /**
         * The block matches the {@code buildBlocked} config list. The server should also set {@link Probe#crafted} for
         * such a block, so that neither {@link TerrainRule#forBuild} nor {@link TerrainRule#forClear} breaks it.
         */
        public boolean blocked;
    }

    private TerrainRule() {}

    /** {@link #forBuild(Probe, boolean)} for a job of the nexus itself (not a module job). */
    public static Verdict forBuild(Probe p) {
        return forBuild(p, false);
    }

    /**
     * The verdict for the target cell of a HARD or SOFT step, in this order: the target block already → ALREADY; air,
     * replaceable or fluid, but not lava → PLACE; lava, unbreakable, another nexus's area or ours inside another
     * structure → BLOCKED; a GT ore → CLEAR; for a module job, a loose supply port of ours → REPLACE; any other tile
     * entity → BLOCKED; ours → REPLACE; crafted → BLOCKED; natural → CLEAR; anything else → BLOCKED.
     *
     * @param module whether the job builds or repairs a module (not the nexus, its campus or its forum)
     */
    public static Verdict forBuild(Probe p, boolean module) {
        if (p.target) return Verdict.ALREADY;
        if (!p.lava && (p.air || p.replaceable || p.fluid)) return Verdict.PLACE;
        if (p.lava || p.unbreakable || p.otherNexusArea || p.oursInOtherStructure) return Verdict.BLOCKED;
        if (p.gtOre) return Verdict.CLEAR;
        if (module && p.port && p.loose) return Verdict.REPLACE;
        if (p.hasTile) return Verdict.BLOCKED;
        if (p.ours) return Verdict.REPLACE;
        if (p.crafted) return Verdict.BLOCKED;
        if (p.natural) return Verdict.CLEAR;
        return Verdict.BLOCKED;
    }

    /** {@link #forClear(Probe, boolean)} for a job of the nexus itself (not a module job). */
    public static Verdict forClear(Probe p) {
        return forClear(p, false);
    }

    /**
     * The verdict for a cell of a clearance or grading volume, which must end up empty: air or a replaceable block
     * that is not a fluid (tall grass, snow layers) → ALREADY, it may stay; unbreakable, another nexus's area or ours
     * inside another structure → BLOCKED; a GT ore → CLEAR (its drops go to the spoils, as in {@link #forBuild}); for
     * a module job, a loose block of ours (frame, deck, fitting or supply port: {@link Probe#loose}) → REPLACE, taken
     * away and credited as its part; any other tile entity, our own blocks, crafted blocks and lava → BLOCKED; natural
     * (water included, it is replaceable) → CLEAR; anything else → BLOCKED.
     *
     * @param module whether the job builds or repairs a module (not the nexus, its campus or its forum)
     */
    public static Verdict forClear(Probe p, boolean module) {
        if (p.air || (p.replaceable && !p.fluid)) return Verdict.ALREADY;
        if (p.unbreakable || p.otherNexusArea || p.oursInOtherStructure) return Verdict.BLOCKED;
        if (p.gtOre) return Verdict.CLEAR;
        if (module && (p.ours || p.port) && p.loose) return Verdict.REPLACE;
        if (p.hasTile || p.ours || p.crafted || p.lava) return Verdict.BLOCKED;
        if (p.natural) return Verdict.CLEAR;
        return Verdict.BLOCKED;
    }

    /**
     * The verdict for a step's own cell under the chosen rule: {@link #forBuild(Probe, boolean)} when {@code all} is
     * false. Under the clear-all rule: the target block already → ALREADY; air, replaceable or any fluid (lava too:
     * the block simply takes its place) → PLACE; guarded (unbreakable, another nexus's area, another structure's or a
     * keep-out box, ours inside another structure, the blocked list) → BLOCKED; our own frame, deck, fitting, port or
     * bin → REPLACE (credited as its part); anything else, natural or not, tile entities included → CLEAR.
     */
    public static Verdict forBuild(Probe p, boolean module, boolean all) {
        if (!all) return forBuild(p, module);
        if (p.target) return Verdict.ALREADY;
        if (p.air || p.replaceable || p.fluid) return Verdict.PLACE;
        if (p.guarded()) return Verdict.BLOCKED;
        if (p.ours || p.port) return Verdict.REPLACE;
        return Verdict.CLEAR;
    }

    /**
     * The verdict for a ground step of a repair: grass, dirt or water ({@link Parts#euOnly}) laid as a SOFT step, which
     * the structure never checks. Once built, that ground is the players' (the Eco Dome's open grass is for their own
     * hives and crops, CITY.md §4.1), so a repair lays it again only where the cell is empty, as the build would
     * (PLACE), and leaves anything else as it is (ALREADY): farmland, the dirt under a hive, a path, a block a player
     * put there, where a first build would take it away (CLEAR or REPLACE) or report it (BLOCKED).
     */
    public static Verdict forRepairGround(Probe p, boolean module, boolean all) {
        Verdict v = forBuild(p, module, all);
        return v == Verdict.PLACE ? Verdict.PLACE : Verdict.ALREADY;
    }

    /**
     * The verdict for a cell of a clear column under the chosen rule: {@link #forClear(Probe, boolean)} when
     * {@code all} is false. Under the clear-all rule the cell must end up empty, so everything but air goes: guarded →
     * BLOCKED; our own frame, deck, fitting, port or bin → REPLACE when it is loose (no formed structure could use
     * it), else BLOCKED (a formed structure's part); anything else → CLEAR: tall grass, snow layers and fluids (lava
     * too, it becomes air) included, tile entities included.
     */
    public static Verdict forClear(Probe p, boolean module, boolean all) {
        if (!all) return forClear(p, module);
        if (p.air) return Verdict.ALREADY;
        if (p.guarded()) return Verdict.BLOCKED;
        if (p.ours || p.port) return p.loose ? Verdict.REPLACE : Verdict.BLOCKED;
        return Verdict.CLEAR;
    }

    /**
     * The verdict for a cell the root's caisson digs out (0.12.0, a {@code DIG} step), probed against the part a later
     * step of its plan places there: that part already → ALREADY (a well raised again, or repaired, keeps its floor,
     * its fittings and its controller); in a repair only a fluid that flowed in is taken ({@link #forDrain}), as a
     * repair never clears a module's box; otherwise as a cell of a clear column ({@link #forClear(Probe, boolean,
     * boolean)}).
     */
    public static Verdict forDig(Probe p, boolean module, boolean all, boolean repair) {
        if (p.target) return Verdict.ALREADY;
        return repair ? forDrain(p, module, all) : forClear(p, module, all);
    }

    /**
     * The verdict for a cell the root's caisson drains (0.12.0, a {@code DRAIN} step) after it was dug: only a fluid
     * that flowed in is taken away (CLEAR, or BLOCKED where {@link #forClear(Probe, boolean, boolean)} keeps it: lava
     * under the conservative rule, a guarded cell); air, any other block and the part a later step places there (the
     * pool's water once it is laid) → ALREADY.
     */
    public static Verdict forDrain(Probe p, boolean module, boolean all) {
        if (p.target || !p.fluid) return Verdict.ALREADY;
        Verdict v = forClear(p, module, all);
        return v == Verdict.ALREADY ? Verdict.CLEAR : v;
    }

    /**
     * The verdict for the floor-level cell (Y0) of a graded column that the plan does not pave: it becomes grass
     * whatever was there (0.10.1, a unified floor). The grass already → ALREADY; guarded → BLOCKED; air, a replaceable
     * block or a fluid → PLACE (lava only under the clear-all rule; the conservative rule leaves it); anything else as
     * {@link #forClear(Probe, boolean, boolean)} would clear it: CLEAR or REPLACE (taken away, then grass), or BLOCKED.
     * Under the conservative rule only natural ground is turned (dirt, stone, logs, leaves, snow), never a player's
     * block.
     */
    public static Verdict forFloor(Probe p, boolean module, boolean all) {
        if (p.target) return Verdict.ALREADY;
        if (p.guarded()) return Verdict.BLOCKED;
        if (p.air || (p.replaceable || p.fluid) && (all || !p.lava)) return Verdict.PLACE;
        Verdict v = forClear(p, module, all);
        return v == Verdict.ALREADY ? Verdict.PLACE : v;
    }

    /**
     * The verdict for a cell under the floor of a graded column, from Y0 - 1 down to {@code buildFillDepth}: the run
     * of such cells from the top is filled with dirt and stops at the first BLOCKED one (the solid crust; whatever lies
     * below it, a cave or a cellar, stays). Guarded → BLOCKED; air, a replaceable block or a fluid → PLACE (lava only
     * under the clear-all rule); logs, leaves and plants → CLEAR (taken away, then dirt; under the conservative rule
     * only natural ones); under the clear-all rule a block of ours (a tile an older campus buried) → REPLACE, credited
     * as its part; anything else (ground, stone, a chest) → BLOCKED.
     */
    public static Verdict forFill(Probe p, boolean all) {
        if (p.guarded()) return Verdict.BLOCKED;
        if (p.air) return Verdict.PLACE;
        if (p.fluid) return p.lava && !all ? Verdict.BLOCKED : Verdict.PLACE;
        if (p.replaceable) return Verdict.PLACE;
        if (all && (p.ours || p.port)) return Verdict.REPLACE;
        if (p.wood || p.leaves || p.plant) {
            if (all) return p.te ? Verdict.BLOCKED : Verdict.CLEAR;
            return p.natural && !p.crafted ? Verdict.CLEAR : Verdict.BLOCKED;
        }
        return Verdict.BLOCKED;
    }

    /**
     * What grading does down one column, as the builder works it: {@code cells[0]} is the floor cell (Y0), the next
     * ones the cells under it, down to the fill depth. A null cell is the plan's own (a paved tile, left to its step:
     * null in the answer too). The floor cell is judged by {@link #forFloor}, the others by {@link #forFill}; the
     * first BLOCKED cell ends the run (a floor cell that must stay ends it at once), and every cell after it is left
     * alone (null). PLACE fills the cell (grass on the floor, dirt under it); CLEAR and REPLACE take its block away
     * first; ALREADY leaves a floor that is right.
     */
    public static Verdict[] gradeColumn(Probe[] cells, boolean module, boolean all) {
        return gradeColumn(i -> cells[i], cells.length, module, all);
    }

    /**
     * {@link #gradeColumn(Probe[], boolean, boolean)} with the cells probed on demand, from the floor down: a cell
     * after the end of the run is never asked for (the survey probes the world through this).
     */
    public static Verdict[] gradeColumn(IntFunction<Probe> cell, int cells, boolean module, boolean all) {
        Verdict[] out = new Verdict[cells];
        for (int i = 0; i < cells; i++) {
            Probe p = cell.apply(i);
            if (p == null) continue;
            out[i] = i == 0 ? forFloor(p, module, all) : forFill(p, all);
            if (out[i] == Verdict.BLOCKED) break;
        }
        return out;
    }

    /**
     * Whether a block is natural terrain the builder may break: never when it is on the {@code buildBlocked} list or
     * has a tile entity (GT ores are handled by {@link Probe#gtOre} instead); always when it is on the
     * {@code buildClearable} list; otherwise when a Forge hook or the material says so (wood, leaves, replaceable,
     * plants, ore-generation stone, the soft materials). Any other rock is natural only when it is a whole, uncrafted
     * cube known to be generated: vanilla stone, netherrack, end stone or plain sandstone, hardened clay in a mesa,
     * GT's raw granite, marble or basalt, or an ore of the ore dictionary. Everything else made of rock (quartz,
     * smooth sandstone, obsidian, GT concrete, hardened clay outside a mesa, other mods' stones) may have been built
     * by a player and is left standing.
     */
    public static boolean natural(Nature n) {
        if (n.blocked || n.hasTile) return false;
        if (n.clearable) return true;
        if (n.wood || n.leaves || n.replaceable || n.plant || n.oreGenStone || n.softMaterial) return true;
        if (!n.rock || n.crafted || n.shaped) return false;
        return n.naturalRock || n.mesaClay || n.rawGtStone || n.ore;
    }

    /**
     * Whether a vanilla block is rock the world generates as terrain: {@code minecraft:stone}, {@code netherrack},
     * {@code end_stone}, and {@code sandstone} of meta 0 (the plain kind; the chiselled and smooth kinds are crafted).
     */
    public static boolean naturalRock(String registryName, int meta) {
        if (registryName == null) return false;
        switch (registryName) {
            case "minecraft:stone":
            case "minecraft:netherrack":
            case "minecraft:end_stone":
                return true;
            case "minecraft:sandstone":
                return meta == 0;
            default:
                return false;
        }
    }

    /**
     * Whether a block is hardened clay a mesa generates: {@code minecraft:hardened_clay} or
     * {@code minecraft:stained_hardened_clay}, and only in a mesa biome (elsewhere a player baked it).
     */
    public static boolean mesaClay(String registryName, boolean mesaBiome) {
        return mesaBiome && ("minecraft:hardened_clay".equals(registryName)
            || "minecraft:stained_hardened_clay".equals(registryName));
    }

    /** GT's granites (black and red) and stones (marble and basalt): the classes whose raw variants are terrain. */
    public static final String GT_GRANITES = "gregtech.common.blocks.BlockGranites",
        GT_STONES = "gregtech.common.blocks.BlockStones";

    /**
     * Whether a block is one of GT's raw stones: of the class {@link #GT_GRANITES} or {@link #GT_STONES} exactly (GT's
     * concrete, {@code BlockConcretes}, shares their parent class but is always built) and of a raw meta (0 or 8; the
     * others are cobblestone, bricks, chiselled and smooth stone).
     */
    public static boolean rawGtStone(String className, int meta) {
        return (GT_GRANITES.equals(className) || GT_STONES.equals(className)) && meta % 8 == 0;
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
