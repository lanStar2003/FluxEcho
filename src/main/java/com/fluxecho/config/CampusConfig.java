package com.fluxecho.config;

import net.minecraftforge.common.config.Configuration;

/**
 * The nexus section of config/fluxecho.cfg, campus builder part (0.10.0 on): what the nexus builds round itself, how
 * fast, what it costs, what it may clear and how its projection is drawn. These keys stay in the nexus category, where
 * 0.10.0 put them. Since 0.17.0 each section of the config file is its own class, listed in Config.SECTIONS, so that
 * work on different subsystems (and later on each building) never edits the same file; the keys, defaults, bounds and
 * comments are the ones 0.16.0 shipped, word for word.
 */
public final class CampusConfig {

    public static final String CATEGORY = "nexus";

    public static boolean campusEnabled = true, archiveEnabled = true;
    public static double buildBlocksPerTick = 2.0, buildCostScale = 1.0;
    public static int buildClearPerTick = 8, buildEuPerBlock = 256, buildEuPerClear = 64, buildClearHeight = 24,
        buildFillDepth = 4, buildMinSpacing = 312;
    public static boolean buildRequireSky = true, buildKeepSpoils = true;
    /** 0.10.1: the campus builder empties every cell it clears (machines and chests too, into the recycle store). */
    public static boolean buildClearAll = true;
    public static String[] buildClearable = {}, buildBlocked = {};
    /** Client: how the projection of a job is drawn, {@code full}, {@code outline} or {@code off}. */
    public static String buildProjection = "full";
    public static int buildGhostRange = 96, buildGhostCells = 6000, buildArcs = 48;
    public static boolean buildEffects = true;

    private CampusConfig() {}

    public static void load(Configuration c) {
        campusEnabled = c.getBoolean(
            "campusEnabled",
            CATEGORY,
            campusEnabled,
            "The campus builder: a nexus core placed on the ground (not sneaking) builds the nexus round itself, grades and paves its campus, and builds the modules its teams research (the Echo Archive) from the materials and EU it is given, once a member presses Start. Off: nothing is built or touched and no campus is made (a core clicked on the ground still goes two blocks up, but it is a nexus to build by hand, as in 0.9.2).");
        archiveEnabled = c.getBoolean(
            "archiveEnabled",
            CATEGORY,
            archiveEnabled,
            "The Echo Archive, the walk-in library the campus builder raises once the library is researched. Off: no Archive is projected or built, and a library forms only as the 0.9.2 hall.");
        buildBlocksPerTick = c.get(
            CATEGORY,
            "buildBlocksPerTick",
            buildBlocksPerTick,
            "Blocks the campus builder launches per tick, on average (some stages go faster or slower). Each costs buildEuPerBlock EU.",
            0.25,
            16)
            .getDouble();
        buildClearPerTick = c.getInt(
            "buildClearPerTick",
            CATEGORY,
            buildClearPerTick,
            1,
            256,
            "Blocks the campus builder clears or fills per tick where it grades the ground and makes room for a building (what it may clear: see buildClearAll).");
        buildEuPerBlock = c.getInt(
            "buildEuPerBlock",
            CATEGORY,
            buildEuPerBlock,
            0,
            Integer.MAX_VALUE,
            "EU the campus builder draws from the owner's team wireless network for each block it places.");
        buildEuPerClear = c.getInt(
            "buildEuPerClear",
            CATEGORY,
            buildEuPerClear,
            0,
            Integer.MAX_VALUE,
            "EU it draws for each natural block it clears, and for each cell of ground it fills with grass or dirt.");
        buildCostScale = c.get(
            CATEGORY,
            "buildCostScale",
            buildCostScale,
            "Multiplies the materials every block the campus builder places costs (the same as its crafting recipe at 1). 0 makes everything free, the Crystal Seat and the Echo Library Core included.",
            0,
            10)
            .getDouble();
        buildClearHeight = c.getInt(
            "buildClearHeight",
            CATEGORY,
            buildClearHeight,
            0,
            64,
            "How high above the campus floor the builder clears (hills, trees, and with buildClearAll everything else in the way) when it grades the campus. A building's own box is always cleared to its full height.");
        buildFillDepth = c.getInt(
            "buildFillDepth",
            CATEGORY,
            buildFillDepth,
            0,
            16,
            "How deep below the campus floor the builder fills when it grades: from just under the floor down this far, air, water, plants, logs and leaves become dirt (lava too when buildClearAll is on), stopping at the first solid block; whatever lies deeper stays under that crust. The floor itself becomes grass wherever the campus is not paved.");
        buildMinSpacing = c.getInt(
            "buildMinSpacing",
            CATEGORY,
            buildMinSpacing,
            0,
            100_000,
            "Blocks (along x or z) a new nexus core placed on the ground must keep from the centre of another nexus's campus, so two campuses never overlap: 312 leaves room for both campuses' outer rings. A core put back where it stood, and an older nexus laying its forum, keep only the old 144 (their outer rings share the ground between them). 0 turns the check off.");
        buildRequireSky = c.getBoolean(
            "buildRequireSky",
            CATEGORY,
            buildRequireSky,
            "A nexus core placed on the ground needs open sky above it (no building it underground or under a roof).");
        buildKeepSpoils = c.getBoolean(
            "buildKeepSpoils",
            CATEGORY,
            buildKeepSpoils,
            "Keep what the campus builder takes down: the drops of every block it clears and the contents of the chests and machines it dismantles go to the nexus's recycle store (taken out at the recycle bin or with Withdraw). Off: they are voided; our own campus blocks are still credited to the build.");
        buildClearAll = c.getBoolean(
            "buildClearAll",
            CATEGORY,
            buildClearAll,
            "The campus builder empties every cell it clears or builds in, natural or not, machines, chests and other tile entities included (their contents go to the recycle store, see buildKeepSpoils). It still never touches another campus, a module or another structure, unbreakable blocks, the buildBlocked list, or what a protection mod refuses. Off: the cautious rule of 0.10.0, which only clears natural terrain and ores and leaves anything a player may have built (marked red).");
        buildClearable = c.getStringList(
            "buildClearable",
            CATEGORY,
            buildClearable,
            "Blocks the campus builder may clear even if it would not count them as natural terrain, as modid:name or modid:* for a whole mod.");
        buildBlocked = c.getStringList(
            "buildBlocked",
            CATEGORY,
            buildBlocked,
            "Blocks the campus builder never breaks, as modid:name or modid:* for a whole mod; it goes round them (or waits, for a cell of a building).");
        buildProjection = c.getString(
            "buildProjection",
            CATEGORY,
            buildProjection,
            "Client: how a build job's projection is drawn: full (the blocks to come, as ghosts), outline (only their outline) or off.",
            new String[] { "full", "outline", "off" });
        if (!"outline".equals(buildProjection) && !"off".equals(buildProjection)) buildProjection = "full";
        buildGhostRange = c.getInt(
            "buildGhostRange",
            CATEGORY,
            buildGhostRange,
            0,
            256,
            "Client: blocks within which the projection draws its ghost blocks; further away only outlines.");
        buildGhostCells = c.getInt(
            "buildGhostCells",
            CATEGORY,
            buildGhostCells,
            0,
            100_000,
            "Client: the most ghost blocks the projection draws (the nearest first).");
        buildEffects = c.getBoolean(
            "buildEffects",
            CATEGORY,
            buildEffects,
            "Client: draw the builder at work (the arcs from the core to each block it places, the blocks printing in, the ground it clears breaking up) and the light rising over the campus's light wells.");
        buildArcs = c.getInt("buildArcs", CATEGORY, buildArcs, 0, 512, "Client: the most launch arcs drawn at once.");
    }
}
