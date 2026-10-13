package com.fluxecho.logic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The research star map of the Flux Nexus (blueprint 3.5): six constellations, one per branch, around the frame's
 * spine. A node costs flux compute (run off the nexus over time) and items (taken when it starts); some also want a
 * number of entries in the team's Echo Codex, and some only open with a later phase of the nexus.
 * <p>
 * Map positions are in star-map units: {@link #WIDTH} x {@link #HEIGHT}. The frame's nodes stand on the spine down the
 * middle, the guard's dome at its foot; since 0.15.0 the spine climbs from the anchor in the lower half through the
 * inner ring and the phases to the outer ring at the top (six stars a name apart fill it), and the other branches stand
 * on either side of it.
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

    /**
     * The highest phase a nexus reaches in this version (0.12.0: phase II; 0.15.0: phase III, CITY.md §3 and §5.1).
     */
    public static final int PHASE_NOW = 3;

    public static final String ANCHOR = "anchor", INNER_RING = "inner_ring", PHASE_2 = "phase_2",
        ECHO_CRYSTAL = "echo_crystal", LIBRARY = "library", LIBRARY_REACH = "library_reach", COMPUTE_1 = "compute_1",
        UPKEEP_1 = "upkeep_1", TIDAL_WELL = "tidal_well", GRIT_YIELD = "grit_yield", DOME = "dome",
        ECO_DOME = "eco_dome", ARCANE_COURT = "arcane_court", BLOOD_COVENANT = "blood_covenant",
        HUNTING_GROUND = "hunting_ground", MANA_FOUNTAIN = "mana_fountain", RESONANCE_GATE = "resonance_gate",
        DUAL_FOIL_VAULT = "dual_foil_vault", PHASE_3 = "phase_3", OUTER_RING = "outer_ring",
        SOPHON_ARRAY = "sophon_array", HOLO_TABLE = "holo_table";

    private static final Map<String, Node> NODES = new LinkedHashMap<>();

    static {
        // 0.15.0: the frame's spine down the middle of the map holds six stars now (the outer ring, phase III, phase
        // II, the inner ring, the anchor and the guard's dome at its foot), each a star and a name below the one over
        // it, as close as the smallest GUI lets them stand. So the anchor moved down from (180, 110), the inner ring
        // from (180, 66), phase II from (180, 30) and the dome from (180, 168), and the stars beside the spine
        // moved out of their names' way and their lines' (ResearchTreeTest): the echo crystal from (232, 88), the
        // library from (268, 62), its reach from (312, 76), compute calibration from (228, 136), the tidal well
        // from (92, 160), grit enrichment from (132, 84) and the Arcane Court from (290, 110).
        add(ANCHOR, Branch.FRAME, 180, 156, new String[0], 0, new String[0], 0, 1);
        add(
            INNER_RING,
            Branch.FRAME,
            180,
            124,
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
            92,
            new String[] { INNER_RING },
            40_000,
            new String[] { "ore:plateTitanium*32", "ore:circuitEV*8", "ore:ingotPhaseAlloy*16",
                "ore:gemFluxCrystal*32" },
            0,
            2);
        // 0.13.0: the echo crystal, the library and its reach stood a little right of where 0.9.0 put them, so the
        // English names under them ("Echo Crystallization", "Echo Library") kept off the anchor's and the reach's arms
        // at the smallest GUI (ResearchTreeTest); 0.15.0 moved all three up and right with the spine
        add(
            ECHO_CRYSTAL,
            Branch.ECHO,
            234,
            72,
            new String[] { ANCHOR },
            6_000,
            new String[] { "ore:gemFluxCrystal*8", "item:minecraft:ender_pearl*4" },
            5,
            1);
        add(
            LIBRARY,
            Branch.ECHO,
            278,
            52,
            new String[] { ECHO_CRYSTAL, INNER_RING },
            12_000,
            new String[] { "ore:gemEchoCrystal*4", "item:minecraft:book*16" },
            0,
            1);
        add(
            LIBRARY_REACH,
            Branch.ECHO,
            322,
            62,
            new String[] { LIBRARY },
            24_000,
            new String[] { "ore:gemEchoCrystal*8", "item:minecraft:ender_eye*4" },
            0,
            1);
        add(
            COMPUTE_1,
            Branch.COMPUTE,
            236,
            134,
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
            152,
            new String[] { PHASE_2, UPKEEP_1 },
            30_000,
            new String[] { "ore:plateTitanium*16", "ore:circuitEV*4", "ore:ingotPhaseAlloy*8", "ore:glassResonant*8",
                "ore:gemFluxCrystal*16" },
            0,
            2);
        add(
            GRIT_YIELD,
            Branch.DEPTHS,
            130,
            82,
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
            186,
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
        // 0.13.0: the pavilions on the diagonals (CITY.md §4.3-4.6, Appendix B), EV costs and one themed vanilla item
        // each: the Arcane Court's is the enchanting table it scales up, the Blood Covenant Altar's nether wart grown
        // on soul sand, the Echo Hunting Ground's bows, the Mana Fountain Array's poppies for its garden. On the map
        // the altar stands at the top, over the library and right of the eco dome, and the other three step down the
        // right-hand side each on a row of its own (the Arcane Court under the library's reach, the Echo Hunting
        // Ground below it, the Mana Fountain Array by the compute branch's name). No line from phase II or the echo
        // crystal runs through another star, and no name, English or Chinese, runs into another name, a star or a
        // constellation's name at any GUI size down to the smallest (ResearchTreeTest). The Echo Hunting Ground stood
        // at (316, 146) until 0.14.0 moved it down and right to make room for the Resonance Gate above it.
        add(
            ARCANE_COURT,
            Branch.ECHO,
            290,
            100,
            new String[] { PHASE_2, ECHO_CRYSTAL },
            30_000,
            new String[] { "ore:plateTitanium*16", "ore:circuitEV*4", "ore:ingotPhaseAlloy*8",
                "item:minecraft:enchanting_table*2" },
            0,
            2);
        add(
            BLOOD_COVENANT,
            Branch.ECHO,
            282,
            16,
            new String[] { PHASE_2, ECHO_CRYSTAL },
            30_000,
            new String[] { "ore:plateTitanium*16", "ore:circuitEV*4", "ore:ingotPhaseAlloy*8",
                "item:minecraft:nether_wart*16" },
            0,
            2);
        add(
            HUNTING_GROUND,
            Branch.ECHO,
            322,
            152,
            new String[] { PHASE_2, ECHO_CRYSTAL },
            30_000,
            new String[] { "ore:plateTitanium*16", "ore:circuitEV*4", "ore:ingotPhaseAlloy*8", "item:minecraft:bow*4" },
            0,
            2);
        add(
            MANA_FOUNTAIN,
            Branch.ECHO,
            272,
            182,
            new String[] { PHASE_2, ECHO_CRYSTAL },
            30_000,
            new String[] { "ore:plateTitanium*16", "ore:circuitEV*4", "ore:ingotPhaseAlloy*8",
                "item:minecraft:red_flower*16" },
            0,
            2);
        // 0.14.0: the gate's Resonance Gate (CITY.md §4.7, Appendix B), a phase II node on the echo branch after phase
        // II, with the phase's EV costs and note blocks, the tuned resonators its chamber holds. It stands among the
        // echo branch's stars on the right, at the map's right edge between the Arcane Court and the Echo Hunting
        // Ground, which moved a little down and right for it: the one slot on the echo branch's side where its name,
        // English or Chinese, keeps off every other name and star at the smallest GUI and its line from phase II
        // passes no star (ResearchTreeTest).
        add(
            RESONANCE_GATE,
            Branch.ECHO,
            340,
            120,
            new String[] { PHASE_2 },
            30_000,
            new String[] { "ore:plateTitanium*16", "ore:circuitEV*4", "ore:ingotPhaseAlloy*8",
                "item:minecraft:noteblock*16" },
            0,
            2);
        // 0.14.0: the Dual-foil Vault, the third hall (CITY.md §4.2, Appendix B), a phase II node on the depths branch
        // after phase II, with the phase's EV costs and paper, the ground of the canvases it paints its items into. It
        // stands on the left, down and left of the depths branch's grit enrichment and over the energy branch, where
        // its line from phase II keeps well off the stars it passes (the grit enrichment's) and its names keep off
        // every other at the smallest GUI (ResearchTreeTest).
        add(
            DUAL_FOIL_VAULT,
            Branch.DEPTHS,
            84,
            104,
            new String[] { PHASE_2 },
            30_000,
            new String[] { "ore:plateTitanium*16", "ore:circuitEV*4", "ore:ingotPhaseAlloy*8",
                "item:minecraft:paper*32" },
            0,
            2);
        // 0.15.0: the nexus grows to phase III (IV: tungstensteel hull plates, IV circuits, the phase alloy and the
        // phase's own material, memory filament, which the table coats from phase II on; CITY.md §3, §5.2, Appendix B,
        // BLUEPRINT 3.6). It continues the frame's spine up the map over phase II, and the outer ring tops it.
        add(
            PHASE_3,
            Branch.FRAME,
            180,
            60,
            new String[] { PHASE_2 },
            80_000,
            new String[] { "ore:plateTungstenSteel*32", "ore:circuitIV*8", "ore:ingotPhaseAlloy*32",
                "ore:wireMemoryFilament*16" },
            0,
            3);
        // 0.15.0: the outer ring (CITY.md §2.3, Appendix B): the campus job that grades and paves the ground beyond
        // the halls and opens the outer diagonal sites, researched on a nexus of phase III with the phase's costs
        add(
            OUTER_RING,
            Branch.FRAME,
            180,
            28,
            new String[] { PHASE_3 },
            60_000,
            new String[] { "ore:plateTungstenSteel*16", "ore:circuitIV*4", "ore:ingotPhaseAlloy*16",
                "ore:wireMemoryFilament*8" },
            0,
            3);
        // 0.15.0: the two buildings of the outer diagonals (CITY.md §4.10, §4.11, Appendix B), compute nodes after the
        // outer ring, with the phase's IV costs, resonant glass (which their cores leave out, so the manifestation
        // table keeps a slot for a second record's echo crystals), memory filament and one themed vanilla item each:
        // the Sophon Array's comparators, the vanilla logic its racks compute with (it also needs compute calibration),
        // the Holo Sand Table's maps, the base it shows on its table. Both stand on the compute branch's side in its
        // lower half: the Sophon Array under compute calibration, right of the dome, the Holo Sand Table in the bottom
        // right corner by the branch's name, the two spots there where their names keep off every other and their long
        // lines from the outer ring at the top pass no star (ResearchTreeTest).
        add(
            SOPHON_ARRAY,
            Branch.COMPUTE,
            224,
            166,
            new String[] { OUTER_RING, COMPUTE_1 },
            50_000,
            new String[] { "ore:plateTungstenSteel*16", "ore:circuitIV*4", "ore:ingotPhaseAlloy*8",
                "ore:glassResonant*8", "ore:wireMemoryFilament*16", "item:minecraft:comparator*16" },
            0,
            3);
        add(
            HOLO_TABLE,
            Branch.COMPUTE,
            342,
            202,
            new String[] { OUTER_RING },
            50_000,
            new String[] { "ore:plateTungstenSteel*16", "ore:circuitIV*4", "ore:ingotPhaseAlloy*8",
                "ore:glassResonant*8", "ore:wireMemoryFilament*8", "item:minecraft:map*16" },
            0,
            3);
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
     * The id of the node that opens a phase: {@code phase_N} (0.12.0: {@code phase_2}; 0.15.0: {@code phase_3}).
     */
    public static String phaseResearch(int phase) {
        return "phase_" + phase;
    }

    /**
     * The highest phase a nexus may stand as with the research its teams have: from phase I up through every
     * {@code phase_N} done in turn, never past {@link #PHASE_NOW} (a phase's node needs the one before it, so a gap
     * stops the count). The nexus tries the shapes up to this one ({@code TileNexus.tries}).
     */
    public static int researchedPhase(Predicate<String> done) {
        int p = 1;
        while (p < PHASE_NOW && done.test(phaseResearch(p + 1))) p++;
        return p;
    }

    /**
     * The phase a formed nexus of {@code phase} is offered to grow to now ({@code grow:<phase>}, CITY.md §3), or 0: the
     * next phase, when this version reaches it ({@link #PHASE_NOW}) and its teams have that phase's research. So a
     * nexus of phase II with {@code phase_3} done is offered {@code grow:3} (0.15.0), and a nexus of phase III is
     * offered nothing until a later version opens phase IV.
     */
    public static int nextGrowth(int phase, Predicate<String> done) {
        int next = phase + 1;
        return next >= 2 && next <= PHASE_NOW && done.test(phaseResearch(next)) ? next : 0;
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
