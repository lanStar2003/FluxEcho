package com.fluxecho.logic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The research star map of the Flux Nexus (blueprint 3.5): six constellations, one per branch, around the anchor in
 * the middle. A node costs flux compute (run off the nexus over time) and items (taken when it starts); some also want
 * a number of entries in the team's Echo Codex, and some only open with a later phase of the nexus.
 * <p>
 * Map positions are in star-map units: {@link #WIDTH} x {@link #HEIGHT}, the anchor in the middle.
 */
public final class ResearchTree {

    public static final int WIDTH = 360, HEIGHT = 220;

    /** The six constellations: their lang key, colour and where their name sits on the map. */
    public enum Branch {

        FRAME("frame", 0x4FE3FF, 180, 14),
        ECHO("echo", 0x8A5CFF, 318, 40),
        COMPUTE("compute", 0xFF8CE6, 318, 186),
        GUARD("guard", 0x7DB8FF, 180, 210),
        ENERGY("energy", 0xFFD27A, 42, 186),
        DEPTHS("depths", 0x5FD3A8, 42, 40);

        public final String key;
        public final int color, labelX, labelY;

        Branch(String key, int color, int labelX, int labelY) {
            this.key = key;
            this.color = color;
            this.labelX = labelX;
            this.labelY = labelY;
        }
    }

    public static final class Node {

        public final String id;
        public final Branch branch;
        public final int x, y;
        /** Nodes that must be done first. */
        public final List<String> requires;
        /** Flux compute it takes, before {@code researchCostScale}. */
        public final long compute;
        /** Items taken when it starts: {@link Cost} specs. */
        public final List<Cost> costs;
        /** Entries the team's Echo Codex must hold. */
        public final int minRecords;
        /**
         * The nexus phase it belongs to; above {@link #PHASE_NOW} it shows on the map but cannot be started yet. A
         * phase's own node ({@code phase_N}, 0.12.0) is the phase it opens: it is researched on a nexus of the phase
         * below ({@link #nexusPhase}).
         */
        public final int phase;

        Node(String id, Branch branch, int x, int y, String[] requires, long compute, String[] costs, int minRecords,
            int phase) {
            this.id = id;
            this.branch = branch;
            this.x = x;
            this.y = y;
            List<String> r = new ArrayList<>();
            Collections.addAll(r, requires);
            this.requires = Collections.unmodifiableList(r);
            this.compute = compute;
            List<Cost> c = new ArrayList<>();
            for (String s : costs) c.add(Cost.parse(s));
            this.costs = Collections.unmodifiableList(c);
            this.minRecords = minRecords;
            this.phase = phase;
        }

        /** Granted with the first formed nexus: the root of the map. */
        public boolean root() {
            return requires.isEmpty();
        }

        /**
         * The phase the nexus must have grown to before the node can be started: its {@link #phase}, but one less for
         * a phase's own node ({@code phase_2} is researched at phase I and opens phase II).
         */
        public int nexusPhase() {
            return phaseOf(id) > 0 ? phase - 1 : phase;
        }
    }

    /**
     * An item cost: {@code ore:<name>*n} for anything in the ore dictionary under that name, or
     * {@code item:<mod>:<name>[@meta]*n} for one item.
     */
    public static final class Cost {

        public final boolean ore;
        public final String name;
        public final int meta, count;

        private Cost(boolean ore, String name, int meta, int count) {
            this.ore = ore;
            this.name = name;
            this.meta = meta;
            this.count = count;
        }

        public static Cost parse(String spec) {
            int star = spec.lastIndexOf('*');
            int count = star < 0 ? 1 : Integer.parseInt(spec.substring(star + 1));
            String body = star < 0 ? spec : spec.substring(0, star);
            if (count <= 0) throw new IllegalArgumentException("count must be positive: " + spec);
            if (body.startsWith("ore:")) return new Cost(true, body.substring(4), 0, count);
            if (!body.startsWith("item:")) throw new IllegalArgumentException("ore: or item: expected: " + spec);
            String item = body.substring(5);
            int at = item.indexOf('@');
            int meta = at < 0 ? 0 : Integer.parseInt(item.substring(at + 1));
            String name = at < 0 ? item : item.substring(0, at);
            if (name.indexOf(':') <= 0) throw new IllegalArgumentException("mod:name expected: " + spec);
            return new Cost(false, name, meta, count);
        }
    }

    /** The highest phase a nexus reaches in this version (0.12.0: phase II, CITY.md §5.1). */
    public static final int PHASE_NOW = 2;

    public static final String ANCHOR = "anchor", INNER_RING = "inner_ring", PHASE_2 = "phase_2",
        ECHO_CRYSTAL = "echo_crystal", LIBRARY = "library", LIBRARY_REACH = "library_reach", COMPUTE_1 = "compute_1",
        UPKEEP_1 = "upkeep_1", TIDAL_WELL = "tidal_well", GRIT_YIELD = "grit_yield", DOME = "dome",
        ECO_DOME = "eco_dome";

    private static final Map<String, Node> NODES = new LinkedHashMap<>();

    static {
        add(ANCHOR, Branch.FRAME, 180, 110, new String[0], 0, new String[0], 0, 1);
        add(
            INNER_RING,
            Branch.FRAME,
            180,
            66,
            new String[] { ANCHOR },
            3_000,
            new String[] { "ore:gemFluxCrystal*16", "ore:plateStainlessSteel*8" },
            0,
            1);
        // 0.12.0: the nexus grows to phase II (EV: titanium hull plates, EV circuits and the phase's own alloy; CITY.md
        // §5.2, Appendix B, BLUEPRINT 3.6)
        add(
            PHASE_2,
            Branch.FRAME,
            180,
            30,
            new String[] { INNER_RING },
            40_000,
            new String[] { "ore:plateTitanium*32", "ore:circuitEV*8", "ore:ingotPhaseAlloy*16",
                "ore:gemFluxCrystal*32" },
            0,
            2);
        add(
            ECHO_CRYSTAL,
            Branch.ECHO,
            228,
            88,
            new String[] { ANCHOR },
            6_000,
            new String[] { "ore:gemFluxCrystal*8", "item:minecraft:ender_pearl*4" },
            5,
            1);
        add(
            LIBRARY,
            Branch.ECHO,
            266,
            62,
            new String[] { ECHO_CRYSTAL, INNER_RING },
            12_000,
            new String[] { "ore:gemEchoCrystal*4", "item:minecraft:book*16" },
            0,
            1);
        add(
            LIBRARY_REACH,
            Branch.ECHO,
            306,
            76,
            new String[] { LIBRARY },
            24_000,
            new String[] { "ore:gemEchoCrystal*8", "item:minecraft:ender_eye*4" },
            0,
            1);
        add(
            COMPUTE_1,
            Branch.COMPUTE,
            228,
            136,
            new String[] { ANCHOR },
            4_000,
            new String[] { "ore:circuitHV*4", "ore:gemFluxCrystal*4" },
            0,
            1);
        add(
            UPKEEP_1,
            Branch.ENERGY,
            132,
            136,
            new String[] { ANCHOR },
            4_000,
            new String[] { "ore:gemFluxCrystal*4", "ore:plateStainlessSteel*8" },
            0,
            1);
        // 0.12.0: the root's first shape, the Tidal Well under the nexus (CITY.md §4.9, Appendix B)
        add(
            TIDAL_WELL,
            Branch.ENERGY,
            92,
            160,
            new String[] { PHASE_2, UPKEEP_1 },
            30_000,
            new String[] { "ore:plateTitanium*16", "ore:circuitEV*4", "ore:ingotPhaseAlloy*8", "ore:glassResonant*8",
                "ore:gemFluxCrystal*16" },
            0,
            2);
        add(
            GRIT_YIELD,
            Branch.DEPTHS,
            132,
            84,
            new String[] { ANCHOR },
            8_000,
            new String[] { "ore:dustFluxGrit*32", "ore:gemFluxCrystal*4" },
            0,
            1);
        // 0.12.0: the Echo Dome Generator at the zenith, moored on six legs (CITY.md §4.8, Appendix B)
        add(
            DOME,
            Branch.GUARD,
            180,
            168,
            new String[] { PHASE_2 },
            30_000,
            new String[] { "ore:plateTitanium*16", "ore:circuitEV*4", "ore:ingotPhaseAlloy*8", "ore:glassResonant*16",
                "ore:gemEchoCrystal*4", "item:minecraft:ender_eye*8" },
            0,
            2);
        // 0.11.0: the second module the nexus builds, the Eco Dome (CITY.md Appendix B)
        add(
            ECO_DOME,
            Branch.ECHO,
            226,
            38,
            new String[] { INNER_RING, ECHO_CRYSTAL },
            16_000,
            new String[] { "ore:gemEchoCrystal*4", "ore:treeSapling*16", "ore:blockGlass*32" },
            0,
            1);
    }

    private ResearchTree() {}

    private static void add(String id, Branch b, int x, int y, String[] requires, long compute, String[] costs,
        int minRecords, int phase) {
        NODES.put(id, new Node(id, b, x, y, requires, compute, costs, minRecords, phase));
    }

    public static Node get(String id) {
        return id == null ? null : NODES.get(id);
    }

    public static Collection<Node> all() {
        return Collections.unmodifiableCollection(NODES.values());
    }

    /**
     * Whether a node can be researched at all in this version: its phase is reached ({@link #PHASE_NOW}). The others
     * are the placeholders of later phases the star map shows greyed out ({@link Block#LATER_PHASE}).
     */
    public static boolean availableNow(Node n) {
        return n != null && n.phase <= PHASE_NOW;
    }

    /** Every node that can be researched in this version ({@link #availableNow}), in the tree's order. */
    public static List<Node> availableNow() {
        List<Node> out = new ArrayList<>();
        for (Node n : NODES.values()) if (availableNow(n)) out.add(n);
        return out;
    }

    /** Why a node cannot be started, or {@link Block#NONE}. */
    public enum Block {
        NONE,
        DONE,
        LATER_PHASE,
        REQUIRES,
        RECORDS
    }

    /**
     * Whether the node can be started.
     *
     * @param done    nodes done (the union over everyone bound to the nexus)
     * @param phase   the nexus's phase
     * @param records entries in the team's Echo Codex
     */
    public static Block check(Node n, Set<String> done, int phase, int records) {
        if (done.contains(n.id)) return Block.DONE;
        if (n.nexusPhase() > phase || !availableNow(n)) return Block.LATER_PHASE;
        for (String r : n.requires) if (!done.contains(r)) return Block.REQUIRES;
        if (records < n.minRecords) return Block.RECORDS;
        return Block.NONE;
    }

    /**
     * The id of the node that opens a phase: {@code phase_N} (0.12.0: {@code phase_2}).
     */
    public static String phaseResearch(int phase) {
        return "phase_" + phase;
    }

    /**
     * The phase a phase's own node opens ({@code phase_N}), or 0 for any other id.
     */
    public static int phaseOf(String id) {
        if (id == null || !id.startsWith("phase_")) return 0;
        try {
            return Integer.parseInt(id.substring("phase_".length()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Compute a node takes with the config's scale; at least 1 for anything but the root. */
    public static long computeFor(Node n, double scale) {
        if (n.compute <= 0) return 0;
        return Math.max(1, Math.round(n.compute * scale));
    }
}
