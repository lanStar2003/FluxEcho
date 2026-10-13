package com.fluxecho.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The ordered build of a campus job (0.10.0): every block the nexus places for the job, in world coordinates and in
 * the order it places them, grouped into stages, plus the columns it clears before it places anything.
 * <p>
 * A plan is pure data computed from the job's inputs alone (the {@link CampusPlan} of the nexus, its centre, its
 * front, for a module the site, and for the establish and forum jobs the keep-out boxes of the modules that stood near
 * the nexus when the job was made), never from the config or the world, so the client draws the same projection the
 * server builds and a job that is saved with those inputs can be recomputed after a restart. These jobs have plans:
 * <ul>
 * <li>{@link #establish}: grade the campus disc, raise the phase-I nexus round the player's core, pave the forum, the
 * gate and the promenade ring, then stand the gate pylons and the supply port;</li>
 * <li>{@link #forum}: the same without the nexus, for a 0.9.2 nexus that already stands, leaving the halls docked
 * round it alone;</li>
 * <li>{@link #module}: a module on a hall site (0.11.0, the Echo Archive's plan made general: the Archive through
 * {@link #ARCHIVE}, every city module through its {@link CityBuild}) or (0.13.0) a pavilion on a diagonal site: its
 * forecourt and apron (a pavilion's apron and allee), its interior floor, its shell from the bottom up, its decks,
 * upper walls and roof, its fittings and units, the canopy (and for a city module its seeded ornaments), and the
 * controller last.</li>
 * <li>{@link #grow}: the next stage of a formed nexus (0.12.0, CITY.md §3), at phase II with the light-lift pad;</li>
 * <li>{@link #zenith}: the Echo Dome Generator over the nexus (0.12.0, {@link DomeGeneratorShape}): its six legs from
 * the forum, then its pod in the air.</li>
 * <li>{@link #root}: the Tidal Well under the nexus (0.12.0, {@link RootShape}), sunk as a caisson: layer by layer from
 * the lid down, each layer's wall ring before its inside is dug.</li>
 * <li>{@link #retire}: a docked 0.9.2 library hall taken down and its ground graded and paved as the forum's (0.14.1),
 * so the city module whose site it stood on can be built.</li>
 * <li>{@link #outer}: the outer establish (0.15.0): grade the outer ring's annulus and pave its streets, promenade,
 * avenues, outer gate and allées, then stand the outer gate's pylons, leaving out what stands there and what a
 * neighbouring campus owns.</li>
 * </ul>
 * Steps come in stage order. Within a stage they rise bottom-up by layer and spread outward from the nexus, a cell
 * and its mirror image across the job's axis side by side, so the build reads as an ordered band. Each shelf unit of
 * the Archive is one group: its fifteen cells share a group id (the unit's index in {@link LibraryUnits#UNITS}), are
 * contiguous, and launch and land together; so is every unit of a city module ({@link UnitLayout}). Every other step
 * has {@link #NO_GROUP}.
 * <p>
 * Kinds: a {@link #HARD} step is a cell the structure check looks at (the nexus or the Archive blueprint); a
 * {@link #SOFT} step is a cell it does not (paving, light wells, gate pylons, the supply port, the recycle bin, the
 * canopy); an {@link #AIR} step is a cell that must become air (the Archive's door openings). Clear columns overlap
 * step cells on purpose (an Archive's box holds the whole building): the builder decides a step's own cell with
 * {@link TerrainRule#forBuild} and only clears the other cells of a column.
 * <p>
 * The caisson (0.12.0, the root) adds two kinds of step that only empty their cell, at once and at the clearing's pace
 * as a clear column's cell is, never flown and never checked: a {@link #DIG} step digs out a cell inside the shell,
 * and a {@link #DRAIN} step later takes away a fluid that flowed into it. A dug cell may be filled by a later step of
 * the same plan (the root's floor and fittings): such a plan has more than one step at a cell ({@link Plan#repeat},
 * {@link Plan#placedAfter}). A stage may come in bands ({@link Plan#bandEnd}): the builder works no step of a band
 * before every step of the band before it is done, so the root never digs a layer before the wall ring above it
 * stands.
 */
public final class BuildPlan {

    /** A checked structure cell, an unchecked cell (paving, fixtures, decoration), a cell that must become air. */
    public static final byte HARD = 0, SOFT = 1, AIR = 2;
    /**
     * A cell the caisson digs out (its block taken down as a clear column's is), and a cell it drains (only a fluid
     * that flowed in is taken away) (0.12.0, the root): neither is flown, placed or checked.
     */
    public static final byte DIG = 3, DRAIN = 4;
    /** The group of a step that launches on its own. */
    public static final int NO_GROUP = -1;

    /** The stages of the establish and forum jobs; FORM has no steps (the builder forms the nexus there). */
    public static final int E_CLEAR = 0, E_CORE = 1, E_FORM = 2, E_FLOOR = 3, E_FIXTURE = 4, E_STAGES = 5;
    /** The stages of a module job. */
    public static final int M_CLEAR = 0, M_FLOOR = 1, M_SHELL = 2, M_DECK = 3, M_UPPER = 4, M_ROOF = 5, M_FIT = 6,
        M_DECOR = 7, M_COMMISSION = 8, M_STAGES = 9;

    /** The job keys of the plans. */
    public static final String ESTABLISH = "establish", FORUM = "forum", LIBRARY = "library";
    /**
     * The plan key prefix of a nexus growth job (0.12.0): {@code grow:<phase>} raises the stage of that phase on a
     * formed nexus of the phase below ({@link #grow}).
     */
    public static final String GROW = "grow";

    /** Grading and dais columns clear from Y0 + 1 up to Y0 + this (the builder clips it to the config). */
    public static final int CLEAR_UP = 24;
    /**
     * The Archive's clear box: its footprint and {@code ARCHIVE_MARGIN} cells round it, from Y0 + 1 up to Y0 +
     * {@link ArchiveShape#HEIGHT} + {@code ARCHIVE_HEADROOM}. The ridge (the top layer) is at Y0 + HEIGHT - 1, so the
     * box reaches three cells above it.
     */
    public static final int ARCHIVE_MARGIN = 1, ARCHIVE_HEADROOM = 2;
    /**
     * Every module's clear box (0.11.0, the Archive's rule made general): {@code MODULE_MARGIN} cells round the
     * footprint, up to {@code MODULE_HEADROOM} cells above the shape's top layer (the Archive: Y0 + 18).
     */
    public static final int MODULE_MARGIN = ARCHIVE_MARGIN, MODULE_HEADROOM = ARCHIVE_HEADROOM;
    /** The seeded canopy: the shape's own two rows, or up to two more. */
    static final int CANOPY_MIN = 2, CANOPY_VARIANTS = 3;
    /** The seeded annex: its kinds, how far out of the footprint it stands, and the rows it leaves free at each end. */
    static final int ANNEX_PLANTERS = 0, ANNEX_LAMPS = 1, ANNEX_BENCHES = 2, ANNEX_PYLON = 3, ANNEX_KINDS = 4,
        ANNEX_OFFSET = 2, ANNEX_FROM = 6;
    /**
     * A pavilion's annex (0.13.0) stands on its apron's outer row beside a flat side face, rows 8 to 16: there the row
     * lies inside the apron {@code oct(14, 18)} (14 out across, at most 4 along), clear of the chamfer portals.
     */
    public static final int PAVILION_ANNEX_FROM = 8;
    /**
     * An outer diagonal's annex (0.15.0) stands on its apron's outer row beside a flat side face, rows 10 to 20: there
     * the row lies inside the apron {@code oct(17, 22)} (17 out across, at most 5 along), clear of the chamfer portals.
     */
    public static final int OUTER_ANNEX_FROM = 10;
    /** Hash salts of the ornaments. */
    private static final int S_CANOPY = 41, S_ANNEX_SIDE = 43, S_ANNEX_KIND = 47;
    /**
     * Half the side of the square a module standing near the nexus keeps clear of the establish and forum jobs, round
     * the centre of its foundation: the 13 x 13 foundation of a 0.9.2 module ({@link LibraryShape#SIZE}) and one cell
     * more. See {@link #moduleKeepOut}.
     */
    public static final int MODULE_KEEP_OUT = LibraryShape.SIZE / 2 + 1;
    /** The gate pylons: this many frame pillars from Y0 + 1, a conduit on top. */
    public static final int PYLON_PILLARS = 2;
    /** The Archive's lit floor spine on its axis: every even row from the first to the last. */
    public static final int SPINE_FROM = 1, SPINE_TO = 28;

    private static final int DAIS_R = NexusShape.RADIUS, DAIS_K = 7;
    private static final int WELL = Parts.deck(Parts.D_WELL), DECK = Parts.deck(Parts.D_DECK),
        LIT = Parts.deck(Parts.D_LIT);
    /** ForgeDirection ordinals and offsets of the four horizontal fronts: north, south, west, east. */
    private static final int[][] FRONTS = { { 0, -1 }, { 0, 1 }, { -1, 0 }, { 1, 0 } };
    private static final int FIRST_FRONT = 2;

    private BuildPlan() {}

    /** Whether a step of the kind only empties its cell, at the clearing's pace: {@link #DIG} or {@link #DRAIN}. */
    public static boolean digs(byte kind) {
        return kind == DIG || kind == DRAIN;
    }

    /** Whether a step of the kind leaves its cell empty: {@link #AIR}, {@link #DIG} or {@link #DRAIN}. */
    public static boolean empties(byte kind) {
        return kind == AIR || digs(kind);
    }

    /**
     * Whether the structure check reads the cell of a step of the kind (0.14.2): a {@link #HARD} step's (a part of the
     * structure) and an {@link #AIR} step's (a cell that must be air). The campus builder looks at these again when the
     * structure it raised does not form ({@code Builder.recheck}); a soft cell is built but never checked, and the
     * caisson's {@link #DIG} and {@link #DRAIN} steps empty cells that later steps fill.
     */
    public static boolean formReads(byte kind) {
        return kind == HARD || kind == AIR;
    }

    /**
     * Whether the survey before 开始 marks a step of the kind done when it finds its cell right already. Not a
     * {@link #DIG} or {@link #DRAIN} step of the caisson: a cell found empty may hold a block by the time its layer is
     * sunk (stone where lava met water in a cave, a block an enderman set down), and nothing would look at it again, so
     * those steps are only judged when their band comes (for free, as an empty cell is then).
     */
    public static boolean surveySettles(byte kind) {
        return !digs(kind);
    }

    /** One block to place (or to make air). */
    public static final class Step {

        /** The world cell. */
        public final int x, y, z;
        /** The {@link Parts} code to place there; {@link Parts#AIR} for an {@link #AIR} step. */
        public final int part;
        /** The stage the step belongs to. */
        public final int stage;
        /** The group the step launches with, or {@link #NO_GROUP}. */
        public final int group;
        /** {@link #HARD}, {@link #SOFT}, {@link #AIR}, or for the caisson {@link #DIG} or {@link #DRAIN}. */
        public final byte kind;

        public Step(int x, int y, int z, int part, int stage, int group, byte kind) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.part = part;
            this.stage = stage;
            this.group = group;
            this.kind = kind;
        }

        /** Whether the step is at the cell. */
        public boolean at(int x, int y, int z) {
            return this.x == x && this.y == y && this.z == z;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Step)) return false;
            Step s = (Step) o;
            return x == s.x && y == s.y
                && z == s.z
                && part == s.part
                && stage == s.stage
                && group == s.group
                && kind == s.kind;
        }

        @Override
        public int hashCode() {
            int h = x;
            h = 31 * h + y;
            h = 31 * h + z;
            h = 31 * h + part;
            h = 31 * h + stage;
            h = 31 * h + group;
            return 31 * h + kind;
        }

        @Override
        public String toString() {
            return "Step(" + x
                + ","
                + y
                + ","
                + z
                + " part "
                + part
                + " stage "
                + stage
                + " group "
                + group
                + " kind "
                + kind
                + ")";
        }
    }

    /**
     * A column to clear before the job places anything: from {@code yTo} down to {@code yFrom}. A grading column
     * ({@code grade}) also turns its floor-level cell (Y0, just under {@code yFrom}) into grass where the plan does
     * not pave it, and fills the ground under the paving level.
     */
    public static final class Column {

        public final int x, z, yFrom, yTo;
        public final boolean grade;

        public Column(int x, int z, int yFrom, int yTo, boolean grade) {
            this.x = x;
            this.z = z;
            this.yFrom = yFrom;
            this.yTo = yTo;
            this.grade = grade;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Column)) return false;
            Column c = (Column) o;
            return x == c.x && z == c.z && yFrom == c.yFrom && yTo == c.yTo && grade == c.grade;
        }

        @Override
        public int hashCode() {
            int h = x;
            h = 31 * h + z;
            h = 31 * h + yFrom;
            h = 31 * h + yTo;
            return 31 * h + (grade ? 1 : 0);
        }

        @Override
        public String toString() {
            return "Column(" + x + "," + z + " y " + yFrom + ".." + yTo + (grade ? " grade" : "") + ")";
        }
    }

    /**
     * A job's whole build: its steps in order (stage by stage), its clear columns, its stage count and its key. A plan
     * never holds a cell outside the world (0.14.1, CITY.md §1.3 Vertical): a step under y 0 or over y 255 is left out
     * as the plan is made, so a spire or a pod's top over high ground clips at the world's top instead of waiting
     * forever on a cell nobody can place (StructureLib skips such cells as well, so the module still forms).
     */
    public static final class Plan {

        /** Every step, in build order; the stages never go backwards. Unmodifiable. */
        public final List<Step> steps;
        /** The clear columns, in clearing order (outward from the nexus). Unmodifiable. */
        public final List<Column> clear;
        /** How many stages the job has ({@link #E_STAGES} or {@link #M_STAGES}). */
        public final int stages;
        /** The job key: {@code establish}, {@code forum} or {@code module:<module>@<site>}. */
        public final String key;
        /** The index of the first step of every stage, then the step count. */
        private final int[] starts;
        /** The step indices where a band begins inside a stage (0.12.0, the caisson), ascending; mostly none. */
        private final int[] bands;
        /** What the plan's cells hold more than one step ({@link #repeat}, {@link #placedAfter}), worked out once. */
        private volatile Repeats repeats;

        Plan(String key, int stages, List<List<Step>> byStage, List<Column> clear) {
            this(key, stages, byStage, clear, null);
        }

        /**
         * @param bandStarts per stage, the offsets of its steps where a new band begins (0.12.0); null, or a null or
         *                   empty entry, for a stage that is one band. A band whose steps all lie outside the world is
         *                   left out with them; one that loses its first steps begins at its first step left.
         * @throws IllegalArgumentException if a band would begin at its stage's first step or past its last
         */
        Plan(String key, int stages, List<List<Step>> byStage, List<Column> clear, List<List<Integer>> bandStarts) {
            this.key = key;
            this.stages = stages;
            List<Step> all = new ArrayList<>();
            java.util.TreeSet<Integer> b = new java.util.TreeSet<>();
            starts = new int[stages + 1];
            for (int s = 0; s < stages; s++) {
                starts[s] = all.size();
                List<Step> given = byStage.get(s);
                List<Integer> in = bandStarts == null || s >= bandStarts.size() ? null : bandStarts.get(s);
                if (in != null) for (int o : in) {
                    if (o <= 0 || o >= given.size())
                        throw new IllegalArgumentException("a band starts outside its stage: " + o);
                }
                // the steps in the world, and for each given step how many of those came before it
                int[] before = new int[given.size() + 1];
                int kept = 0;
                for (int i = 0; i < given.size(); i++) {
                    before[i] = kept;
                    Step st = given.get(i);
                    if (st.y < 0 || st.y > WORLD_TOP) continue;
                    all.add(st);
                    kept++;
                }
                before[given.size()] = kept;
                if (in != null) for (int o : in) {
                    int at = before[o];
                    if (at > 0 && at < kept) b.add(starts[s] + at);
                }
            }
            starts[stages] = all.size();
            steps = Collections.unmodifiableList(all);
            this.clear = Collections.unmodifiableList(new ArrayList<>(clear));
            bands = new int[b.size()];
            int i = 0;
            for (int at : b) bands[i++] = at;
        }

        /**
         * One past the last step of the band step {@code i} is in (0.12.0): where the next band begins, or the step
         * count when no band begins after it. A stage's end is not a band's: the builder never works past its stage
         * anyway.
         */
        public int bandEnd(int i) {
            int lo = 0, hi = bands.length;
            while (lo < hi) {
                int mid = (lo + hi) >>> 1;
                if (bands[mid] <= i) lo = mid + 1;
                else hi = mid;
            }
            return lo < bands.length ? bands[lo] : steps.size();
        }

        /** How many bands begin inside the plan's stages (0 for a plan without bands). */
        public int bandCount() {
            return bands.length;
        }

        /**
         * Whether an earlier step of the plan is at step {@code i}'s cell (0.12.0: the caisson digs a cell, drains it,
         * then fills it). The survey looks at a cell's first step only; the later ones are judged in their stage.
         */
        public boolean repeat(int i) {
            return repeats().repeat.get(i);
        }

        /**
         * The part the first later step at step {@code i}'s cell places there (a {@link #HARD} or {@link #SOFT} step),
         * or -1 when none does: what a {@link #DIG} or {@link #DRAIN} step leaves standing when it is there already (a
         * well raised again, or repaired, keeps its floor, its fittings and its controller).
         */
        public int placedAfter(int i) {
            return repeats().after[i];
        }

        private Repeats repeats() {
            Repeats r = repeats;
            if (r == null) repeats = r = new Repeats(steps);
            return r;
        }

        /** The index in {@link #steps} of the stage's first step (the next stage's start when it has none). */
        public int start(int stage) {
            return starts[Math.max(0, Math.min(stages, stage))];
        }

        /** One past the index of the stage's last step. */
        public int end(int stage) {
            return start(stage + 1);
        }

        /** How many steps the stage has. */
        public int count(int stage) {
            return end(stage) - start(stage);
        }
    }

    /** The cells of a plan that hold more than one step: which steps repeat a cell, and what a later step places. */
    private static final class Repeats {

        final java.util.BitSet repeat = new java.util.BitSet();
        final int[] after;

        Repeats(List<Step> steps) {
            int n = steps.size();
            after = new int[n];
            Set<Long> seen = new HashSet<>();
            for (int i = 0; i < n; i++) {
                Step s = steps.get(i);
                if (!seen.add(cellKey(s.x, s.y, s.z))) repeat.set(i);
            }
            Map<Long, Integer> later = new java.util.HashMap<>();
            for (int i = n - 1; i >= 0; i--) {
                Step s = steps.get(i);
                long k = cellKey(s.x, s.y, s.z);
                Integer p = later.get(k);
                after[i] = p == null ? -1 : p;
                if ((s.kind == HARD || s.kind == SOFT) && s.part != Parts.AIR) later.put(k, s.part);
            }
        }

        private static long cellKey(int x, int y, int z) {
            return ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | (y & 0xFFF);
        }
    }

    /**
     * The world columns a plan works, as a box {x0, z0, x1, z1} (inclusive): every step and every clear column. For a
     * module this is everything its job lays on its site: the building, its clear box, the floor round it (forecourt
     * and apron) and the canopy over its door. Null for a plan with neither.
     */
    public static int[] extent(Plan p) {
        if (p == null) return null;
        int x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
        for (Step s : p.steps) {
            x0 = Math.min(x0, s.x);
            z0 = Math.min(z0, s.z);
            x1 = Math.max(x1, s.x);
            z1 = Math.max(z1, s.z);
        }
        for (Column c : p.clear) {
            x0 = Math.min(x0, c.x);
            z0 = Math.min(z0, c.z);
            x1 = Math.max(x1, c.x);
            z1 = Math.max(z1, c.z);
        }
        return x0 > x1 ? null : new int[] { x0, z0, x1, z1 };
    }

    /**
     * The keep-out boxes of a gatehouse's job (0.14.0, CITY.md §2.2) for the nexus centre {@code (cx, y0, cz)} facing
     * {@code (fx, fz)}: every world column its plan works, from the lowest height it works there. A column it works at
     * Y0 or below (the wings, their clear margin, the floor it lays: the apron and the passage cells the road leaves
     * unpaved) is a whole column {x0, z0, x1, z1}; a column of the open passage it works only above Y0 (the bridge
     * from its headroom up, the kiosk from Y0 + 1) is a height-ranged box {x0, z0, x1, z1, yFrom, 255}, so the campus
     * jobs (establish, forum, refresh) still lay the gate road's tiles under it, and the establish pylons, which the
     * gatehouse never works, stay theirs. The boxes run along R row by row, one per run of columns with the same
     * lowest height, in a fixed order.
     */
    public static List<int[]> gateKeepOut(Plan p, int cx, int y0, int cz, int fx, int fz) {
        facingOrdinal(fx, fz);
        // the lowest height the plan works in each campus cell; a grading column works its floor and under it
        Map<Long, Integer> low = new java.util.TreeMap<>();
        for (Step s : p.steps) {
            int[] l = CampusPlan.toLocal(s.x - cx, s.z - cz, fx, fz);
            low.merge(CampusPlan.key(l[0], l[1]), s.y, Math::min);
        }
        for (Column c : p.clear) {
            int[] l = CampusPlan.toLocal(c.x - cx, c.z - cz, fx, fz);
            low.merge(CampusPlan.key(l[0], l[1]), c.grade ? Integer.MIN_VALUE : c.yFrom, Math::min);
        }
        java.util.TreeMap<Integer, Set<Long>> byLow = new java.util.TreeMap<>();
        for (Map.Entry<Long, Integer> e : low.entrySet()) {
            long k = e.getKey();
            boolean ranged = CampusPlan.inGatePassage(CampusPlan.keyA(k), CampusPlan.keyR(k)) && e.getValue() > y0;
            byLow.computeIfAbsent(ranged ? e.getValue() : Integer.MIN_VALUE, x -> new java.util.LinkedHashSet<>())
                .add(k);
        }
        List<int[]> out = new ArrayList<>();
        for (Map.Entry<Integer, Set<Long>> e : byLow.entrySet()) {
            for (int[] b : CampusPlan.rowBoxes(e.getValue())) {
                int[] q = CampusPlan.toWorld(b[0], b[1], fx, fz), r = CampusPlan.toWorld(b[2], b[3], fx, fz);
                int x0 = cx + Math.min(q[0], r[0]), z0 = cz + Math.min(q[1], r[1]), x1 = cx + Math.max(q[0], r[0]),
                    z1 = cz + Math.max(q[1], r[1]);
                out.add(
                    e.getKey() == Integer.MIN_VALUE ? new int[] { x0, z0, x1, z1 }
                        : new int[] { x0, z0, x1, z1, e.getKey(), WORLD_TOP });
            }
        }
        return out;
    }

    /**
     * The gatehouse's keep-out boxes ({@link #gateKeepOut}) as the boxes {x0, y0, z0, x1, y1, z1} in which nothing but
     * its own jobs takes a block down (0.14.0): a whole column from bedrock to the sky, a height-ranged box over its
     * heights, so the gate road's tiles and the establish pylons under the bridge stay the nexus's own.
     */
    public static List<int[]> gateProtect(List<int[]> keepOut) {
        List<int[]> out = new ArrayList<>(keepOut.size());
        for (int[] b : keepOut) {
            int lo = b.length == 6 ? b[4] : 0, hi = b.length == 6 ? b[5] : WORLD_TOP;
            out.add(new int[] { b[0], lo, b[1], b[2], hi, b[3] });
        }
        return out;
    }

    /** The highest y a block can stand at. */
    private static final int WORLD_TOP = 255;

    // ---- keys and fronts ----

    /** The job key of a module job: {@code module:<module>@<site>}, such as {@code module:library@4}. */
    public static String moduleKey(String module, int site) {
        return "module:" + module + "@" + site;
    }

    /**
     * The ForgeDirection ordinal (2 north, 3 south, 4 west, 5 east) of a horizontal front {@code (fx, fz)}.
     *
     * @throws IllegalArgumentException if the vector is not one of the four horizontal unit vectors
     */
    public static int facingOrdinal(int fx, int fz) {
        for (int i = 0; i < FRONTS.length; i++) if (FRONTS[i][0] == fx && FRONTS[i][1] == fz) return FIRST_FRONT + i;
        throw new IllegalArgumentException("not a horizontal front: " + fx + "," + fz);
    }

    /**
     * The horizontal front {fx, fz} of a ForgeDirection ordinal from 2 to 5.
     *
     * @throws IllegalArgumentException for any other ordinal
     */
    public static int[] front(int ordinal) {
        if (ordinal < FIRST_FRONT || ordinal >= FIRST_FRONT + FRONTS.length) {
            throw new IllegalArgumentException("not a horizontal facing: " + ordinal);
        }
        return FRONTS[ordinal - FIRST_FRONT].clone();
    }

    // ---- establish and forum ----

    /**
     * The keep-out box {x0, z0, x1, z1} (world, inclusive) of a 0.9.2 module whose foundation centre is
     * {@code centre} = {x, y, z}, exactly as {@code TileModule.centre()} returns it: {@link #MODULE_KEEP_OUT} cells
     * round the centre both ways, which covers its 13 x 13 foundation and a one-cell margin whichever way it faces.
     *
     * @throws IllegalArgumentException if {@code centre} is not a three-element {x, y, z}
     */
    public static int[] moduleKeepOut(int[] centre) {
        if (centre == null || centre.length != 3) {
            throw new IllegalArgumentException("a module centre is {x, y, z}");
        }
        return new int[] { centre[0] - MODULE_KEEP_OUT, centre[2] - MODULE_KEEP_OUT, centre[0] + MODULE_KEEP_OUT,
            centre[2] + MODULE_KEEP_OUT };
    }

    /**
     * The establish job with nothing kept out: {@link #establish(CampusPlan, int, int, int, int, int, Blueprint, int,
     * int, int, List)} with an empty list.
     */
    public static Plan establish(CampusPlan plan, int cx, int y0, int cz, int fx, int fz, Blueprint nexus, int ctrlX,
        int ctrlY, int ctrlZ) {
        return establish(plan, cx, y0, cz, fx, fz, nexus, ctrlX, ctrlY, ctrlZ, Collections.<int[]>emptyList());
    }

    /**
     * The establish job of a freshly placed nexus core: grading, the phase-I nexus round the core, the floor and the
     * fixtures.
     * <ul>
     * <li>CLEAR: the columns over the dais (from Y0 + 1, over the controller only above it) and the grading columns
     * of {@link CampusPlan#gradeArea()} (Y0 + 1 to Y0 + {@link #CLEAR_UP}, grade), outward from the centre.</li>
     * <li>CORE: every cell of {@code nexus} but its controller, placed where {@link Blueprint#world} puts it for the
     * controller at {@code ctrl}: the console stand first, then the lit spokes from the middle outward, the base by
     * octagon distance, then the ribs, conduit, seat and ring bottom-up, each layer clockwise from the front.</li>
     * <li>FORM: no steps.</li>
     * <li>FLOOR: {@link CampusPlan#establishFloor()} at Y0 outward from the centre, the light wells' deck WELL flush
     * with the rest.</li>
     * <li>FIXTURE: the four gate pylons (two frame pillars and a conduit each, Y0 + 1 to Y0 + 3), then the supply
     * port and the recycle bin beside it at Y0 + 1.</li>
     * </ul>
     * The grading columns, the floor cells, the pylons, the supply port and the recycle bin that fall inside a
     * {@code keepOut} box are left out, so the job never touches a module that already stands round the nexus (a 0.9.2
     * hall docked on the inner ring: slot 0 lies on the gate, and every slot lies in the graded disc). A height-ranged
     * box (0.12.0, a leg of the zenith) leaves out only what reaches its heights: the grading column, not the floor
     * tile and the fixtures under a leg that starts higher. The dais columns and the nexus itself are never left out;
     * the core placement has to refuse a spot whose dais overlaps a module.
     *
     * @param cx      the nexus centre (the middle of its base), with {@code y0} the base layer
     * @param fx      the nexus front, a horizontal unit vector
     * @param nexus   the nexus blueprint, normally {@link NexusShape#PHASE_1}
     * @param ctrlX   the nexus controller (the core the player placed)
     * @param keepOut world boxes {x0, z0, x1, z1} (inclusive, any corner order) whose columns the campus work leaves
     *                alone, normally one {@link #moduleKeepOut} per module standing near the nexus when the job was
     *                made, or {x0, z0, x1, z1, yFrom, yTo} it leaves alone over those heights only; the job must keep
     *                the list so that a recomputed plan (and the client's) is the same
     * @throws IllegalArgumentException if the front is not a horizontal unit vector or a box is not four or six
     *                                  numbers
     */
    public static Plan establish(CampusPlan plan, int cx, int y0, int cz, int fx, int fz, Blueprint nexus, int ctrlX,
        int ctrlY, int ctrlZ, List<int[]> keepOut) {
        facingOrdinal(fx, fz);
        Frame f = new Frame(cx, y0, cz, fx, fz, 1, 0);
        KeepOut out = new KeepOut(keepOut);
        List<Clearing> clear = new ArrayList<>();
        for (int a = -DAIS_R; a <= DAIS_R; a++) for (int r = -DAIS_R; r <= DAIS_R; r++) {
            if (!CampusPlan.inBase(a, r)) continue;
            int[] w = f.world(a, r);
            int from = y0 + 1;
            // never ask to clear the core itself: the dais column under it is the console stand, a step
            if (w[0] == ctrlX && w[1] == ctrlZ && ctrlY >= from) from = ctrlY + 1;
            if (from <= y0 + CLEAR_UP) clear.add(new Clearing(f, w[0], from, w[1], y0 + CLEAR_UP, false));
        }
        List<List<Step>> stages = campus(plan, f, clear, out);
        stages.set(E_CORE, core(f, nexus, ctrlX, ctrlY, ctrlZ));
        return new Plan(ESTABLISH, E_STAGES, stages, columns(clear));
    }

    /**
     * The forum job with nothing kept out: {@link #forum(CampusPlan, int, int, int, int, int, List)} with an empty
     * list. Only right for a 0.9.2 nexus with no module anywhere near it.
     */
    public static Plan forum(CampusPlan plan, int cx, int y0, int cz, int fx, int fz) {
        return forum(plan, cx, y0, cz, fx, fz, Collections.<int[]>emptyList());
    }

    /**
     * The forum job of a 0.9.2 nexus that already stands: the establish job without the nexus and without the dais
     * columns (CLEAR grades the disc, CORE and FORM are empty, FLOOR and FIXTURE as in {@link #establish}), leaving
     * out every column inside a {@code keepOut} box. A 0.9.2 nexus usually has halls docked on its inner ring (the one
     * on slot 0 stands on the gate), so the caller passes a {@link #moduleKeepOut} for every module near the nexus,
     * formed or not.
     *
     * @param keepOut world boxes {x0, z0, x1, z1} (inclusive, any corner order) the job leaves alone, or
     *                {x0, z0, x1, z1, yFrom, yTo} over those heights only; the job must keep the list so that a
     *                recomputed plan (and the client's) is the same
     * @throws IllegalArgumentException if the front is not a horizontal unit vector or a box is not four or six
     *                                  numbers
     */
    public static Plan forum(CampusPlan plan, int cx, int y0, int cz, int fx, int fz, List<int[]> keepOut) {
        facingOrdinal(fx, fz);
        Frame f = new Frame(cx, y0, cz, fx, fz, 1, 0);
        KeepOut out = new KeepOut(keepOut);
        List<Clearing> clear = new ArrayList<>();
        List<List<Step>> stages = campus(plan, f, clear, out);
        return new Plan(FORUM, E_STAGES, stages, columns(clear));
    }

    /**
     * The stages the establish and forum jobs share: grading columns, the floor and the fixtures, less what the
     * keep-out boxes hold: a grading column a box reaches between Y0 + 1 and Y0 + {@link #CLEAR_UP}, a floor cell a
     * box holds at Y0, a pylon, the supply port or the recycle bin a box holds at their heights. A four-number box
     * holds its whole columns, so everything in it is left out as before 0.12.0.
     */
    private static List<List<Step>> campus(CampusPlan plan, Frame f, List<Clearing> clear, KeepOut out) {
        for (long k : plan.gradeArea()) {
            int[] w = f.world(CampusPlan.keyA(k), CampusPlan.keyR(k));
            if (out.holds(w[0], w[1], f.y0 + 1, f.y0 + CLEAR_UP)) continue;
            clear.add(new Clearing(f, w[0], f.y0 + 1, w[1], f.y0 + CLEAR_UP, true));
        }
        clear.sort(RIPPLE);

        List<Draft> floor = new ArrayList<>();
        Set<Long> wells = plan.wells();
        for (Map.Entry<Long, Integer> e : plan.establishFloor()
            .entrySet()) {
            long k = e.getKey();
            int[] w = f.world(CampusPlan.keyA(k), CampusPlan.keyR(k));
            if (out.holds(w[0], w[1], f.y0, f.y0)) continue;
            paving(floor, f, CampusPlan.keyA(k), CampusPlan.keyR(k), e.getValue(), wells.contains(k));
        }
        floor.sort(RIPPLE);

        List<Draft> pylons = new ArrayList<>();
        for (int[] p : plan.pylons()) {
            int[] w = f.world(p[0], p[1]);
            if (out.holds(w[0], w[1], f.y0 + 1, f.y0 + PYLON_PILLARS + 1)) continue;
            for (int i = 1; i <= PYLON_PILLARS + 1; i++) {
                int part = Parts.frame(i <= PYLON_PILLARS ? Parts.FR_PILLAR : Parts.FR_CONDUIT);
                pylons.add(new Draft(f, w[0], f.y0 + i, w[1], part, SOFT, NO_GROUP));
            }
        }
        pylons.sort(BOTTOM_UP);
        int[] port = plan.supplyPort();
        int[] pw = f.world(port[0], port[1]);
        if (!out.holds(pw[0], pw[1], f.y0 + 1, f.y0 + 1)) {
            pylons.add(new Draft(f, pw[0], f.y0 + 1, pw[1], Parts.SUPPLY_PORT, SOFT, NO_GROUP));
        }
        int[] bin = plan.recycleBin();
        int[] bw = f.world(bin[0], bin[1]);
        if (!out.holds(bw[0], bw[1], f.y0 + 1, f.y0 + 1)) {
            pylons.add(new Draft(f, bw[0], f.y0 + 1, bw[1], Parts.RECYCLE_BIN, SOFT, NO_GROUP));
        }

        List<List<Step>> stages = empty(E_STAGES);
        stages.set(E_FLOOR, steps(floor, E_FLOOR));
        stages.set(E_FIXTURE, steps(pylons, E_FIXTURE));
        return stages;
    }

    // ---- a 0.9.2 hall retires (0.14.1) ----

    /** The stages of a retire job: the hall taken down and its ground graded, then the forum's floor and fixtures. */
    public static final int D_CLEAR = 0, D_FLOOR = 1, D_FIXTURE = 2, D_STAGES = 3;
    /** The plan key prefix of a retire job: {@code retire:<slot>}, the 0.9.2 ring slot the hall docks on. */
    public static final String RETIRE = "retire";

    /** The plan key of the retire job of the 0.9.2 hall on an inner ring slot: {@code retire:<slot>}. */
    public static String retireKey(int slot) {
        return RETIRE + ":" + slot;
    }

    /** The ring slot of a retire plan key ({@link #retireKey}), or -1 for any other key. */
    public static int retireSlot(String key) {
        if (key == null || !key.startsWith(RETIRE + ":")) return -1;
        try {
            int slot = Integer.parseInt(key.substring(RETIRE.length() + 1));
            return slot >= 0 && slot < RingSlots.SLOTS ? slot : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * The ground a retire job works for a 0.9.2 hall, {x0, z0, x1, z1, yFrom, yTo}: the keep-out box the campus jobs
     * left round the centre of its foundation ({@link #moduleKeepOut}) together with its bounds {x0, y0, z0, x1, y1,
     * z1}, over the heights of its bounds. A hall docks up to {@link RingSlots#TOLERANCE} blocks off its slot point, so
     * the ring the keep-out box adds round the hall may reach the apron of a neighbour built meanwhile; on each side
     * where that ring meets one of the {@code keep} columns {x0, z0, x1, z1, ...} (the keep-out and protected boxes of
     * the modules standing or being built on the other sites), the box comes back to the hall's bounds, but never
     * inside them. So the job neither works nor marks blocked a cell of its neighbour, and the hall still comes down
     * whole: its bounds meet no other site's ground.
     */
    public static int[] retireBox(int[] centre, int[] bounds, List<int[]> keep) {
        int[] k = moduleKeepOut(centre);
        int bx0 = Math.min(bounds[0], bounds[3]), bx1 = Math.max(bounds[0], bounds[3]);
        int bz0 = Math.min(bounds[2], bounds[5]), bz1 = Math.max(bounds[2], bounds[5]);
        int x0 = Math.min(k[0], bx0), z0 = Math.min(k[1], bz0), x1 = Math.max(k[2], bx1), z1 = Math.max(k[3], bz1);
        // each side's strip is what the box takes beyond the bounds there; a side whose strip meets a neighbour's
        // column comes back to the bounds, and every cell left beyond the bounds lies in a strip that meets none
        boolean west = meets(keep, x0, z0, bx0 - 1, z1), east = meets(keep, bx1 + 1, z0, x1, z1);
        boolean north = meets(keep, x0, z0, x1, bz0 - 1), south = meets(keep, x0, bz1 + 1, x1, z1);
        return new int[] { west ? bx0 : x0, north ? bz0 : z0, east ? bx1 : x1, south ? bz1 : z1,
            Math.min(bounds[1], bounds[4]), Math.max(bounds[1], bounds[4]) };
    }

    /** Whether a column box {x0, z0, x1, z1, ...} of the list meets the columns x0..x1, z0..z1 (none when empty). */
    private static boolean meets(List<int[]> keep, int x0, int z0, int x1, int z1) {
        if (keep == null || x0 > x1 || z0 > z1) return false;
        for (int[] b : keep) {
            if (b == null || b.length < 4) continue;
            if (Math.max(b[0], b[2]) >= x0 && Math.min(b[0], b[2]) <= x1
                && Math.max(b[1], b[3]) >= z0
                && Math.min(b[1], b[3]) <= z1) return true;
        }
        return false;
    }

    /**
     * The retire job of a docked 0.9.2 library hall (0.14.1, M11.1 §3): a hall on the old inner ring stands on a city
     * site (or, on slots 1 and 7, reaches the gate's), so the module that needs that site is offered nowhere while it
     * stands. The job takes the hall down and gives its ground back to the campus:
     * <ul>
     * <li>CLEAR: every column of {@code box} {x0, z0, x1, z1, yFrom, yTo} (the hall's keep-out box round the centre of
     * its foundation kept off its neighbours, {@link #retireBox}, from its lowest to its highest cell), from its top
     * down to Y0 + 1, outward from the nexus; each column is then graded, as the forum's are (the floor cell at Y0
     * grass, the ground under it filled), so the hall's foundation goes and no pit is left where it stood;</li>
     * <li>FLOOR and FIXTURE: what the forum plan lays inside the box ({@link #forum} with nothing kept out): the
     * forum's tiles, and on the gate road the pylons, which the establish job left out round the hall.</li>
     * </ul>
     * Every block it takes down goes to the recycle store; the hall's books go to the team's book vault when its core
     * is broken, and the Echo Archive takes them back.
     *
     * @param key the retire plan key ({@link #retireKey})
     * @throws IllegalArgumentException if the front is not a horizontal unit vector or the box not six numbers
     */
    public static Plan retire(CampusPlan plan, String key, int cx, int y0, int cz, int fx, int fz, int[] box) {
        return retire(plan, key, cx, y0, cz, fx, fz, box, false, null);
    }

    /**
     * {@link #retire(CampusPlan, String, int, int, int, int, int, int[])} on a campus whose outer ring is laid
     * (0.15.0, {@code outerLaid}): the outer establish kept out of the hall's box, which on a diagonal slot of the old
     * ring covers a stretch of the 井 streets, so the retire job also lays the outer ring's floor inside its box
     * ({@link CampusPlan#outerFloor}, in FLOOR after the forum's tiles) and the outer gate's pylons that stand there
     * (in FIXTURE), the cells a neighbouring campus owns ({@link CampusSplit#ours} against {@code neighbours}) left
     * out as the outer establish leaves them. Without the outer ring laid the plan is the 0.14.1 one, step for step.
     */
    public static Plan retire(CampusPlan plan, String key, int cx, int y0, int cz, int fx, int fz, int[] box,
        boolean outerLaid, List<int[]> neighbours) {
        facingOrdinal(fx, fz);
        if (box == null || box.length != 6) throw new IllegalArgumentException("a retire box is six numbers");
        int x0 = Math.min(box[0], box[2]), x1 = Math.max(box[0], box[2]), z0 = Math.min(box[1], box[3]),
            z1 = Math.max(box[1], box[3]);
        int top = Math.max(Math.max(box[4], box[5]), y0 + 1);
        Frame f = new Frame(cx, y0, cz, fx, fz, 1, 0);
        List<Clearing> clear = new ArrayList<>();
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            clear.add(new Clearing(f, x, y0 + 1, z, Math.min(WORLD_TOP, top), true));
        }
        clear.sort(RIPPLE);
        // the forum's own floor and fixtures there, the establish job having left the hall's box alone
        Plan forum = forum(plan, cx, y0, cz, fx, fz);
        List<List<Step>> stages = empty(D_STAGES);
        Set<Long> tiled = new HashSet<>();
        for (Step s : forum.steps) {
            if (s.x < x0 || s.x > x1 || s.z < z0 || s.z > z1) continue;
            int stage = s.stage == E_FIXTURE ? D_FIXTURE : D_FLOOR;
            stages.get(stage)
                .add(new Step(s.x, s.y, s.z, s.part, stage, s.group, s.kind));
            if (s.y == y0) tiled.add(CampusPlan.key(s.x, s.z));
        }
        if (outerLaid) {
            // the outer ring's floor the outer establish left out round the hall (0.15.0), and its pylons
            List<Draft> tiles = new ArrayList<>(), pylons = new ArrayList<>();
            for (Map.Entry<Long, Integer> e : plan.outerFloor()
                .entrySet()) {
                int a = CampusPlan.keyA(e.getKey()), r = CampusPlan.keyR(e.getKey());
                int[] w = f.world(a, r);
                if (w[0] < x0 || w[0] > x1 || w[1] < z0 || w[1] > z1 || tiled.contains(CampusPlan.key(w[0], w[1])))
                    continue;
                if (!CampusSplit.ours(w[0], w[1], cx, cz, neighbours)) continue;
                paving(tiles, f, a, r, e.getValue(), false);
            }
            tiles.sort(RIPPLE);
            for (int[] p : OuterRing.pylons()) {
                int[] w = f.world(p[0], p[1]);
                if (w[0] < x0 || w[0] > x1 || w[1] < z0 || w[1] > z1) continue;
                if (!CampusSplit.ours(w[0], w[1], cx, cz, neighbours)) continue;
                for (int i = 1; i <= PYLON_PILLARS + 1; i++) {
                    int part = Parts.frame(i <= PYLON_PILLARS ? Parts.FR_PILLAR : Parts.FR_CONDUIT);
                    pylons.add(new Draft(f, w[0], y0 + i, w[1], part, SOFT, NO_GROUP));
                }
            }
            pylons.sort(BOTTOM_UP);
            stages.get(D_FLOOR)
                .addAll(steps(tiles, D_FLOOR));
            stages.get(D_FIXTURE)
                .addAll(steps(pylons, D_FIXTURE));
        }
        return new Plan(key, D_STAGES, stages, columns(clear));
    }

    // ---- the outer establish (0.15.0) ----

    /** The stages of the outer establish: grading the annulus, paving the outer ring, the outer gate's pylons. */
    public static final int O_CLEAR = 0, O_FLOOR = 1, O_FIXTURE = 2, O_STAGES = 3;
    /** The plan key (and job key) of the outer establish, {@link SiteFamily#OUTER_GROUND}. */
    public static final String OUTER = SiteFamily.OUTER_GROUND;

    /**
     * The outer establish of a campus (0.15.0, CITY.md §2.3, M12 §3), opened by the research {@code outer_ring} on a
     * nexus of phase III: the ground the outer families stand on.
     * <ul>
     * <li>CLEAR: every column of the annulus {@code oct(97, 147)} less {@code oct(64, 90)} ({@link OuterRing#annulus},
     * 19,836) and of the outer floor that lies in the inner disc (the 井 streets, the promenade's inner half, most of
     * the avenues: 2,099), from Y0 + 1 to Y0 + {@link #CLEAR_UP} (the builder clips it to {@code buildClearHeight}),
     * graded as the establish job grades (the floor cell grass where nothing is paved, the ground under it filled to
     * {@code buildFillDepth}), outward from the nexus: the clearing goes in rings, a column at a time within the
     * builder's limits per tick.</li>
     * <li>FLOOR: {@link CampusPlan#outerFloor} at Y0, outward from the nexus.</li>
     * <li>FIXTURE: the outer gate's four pylons (two frame pillars and a conduit each, Y0 + 1 to Y0 + 3), as the
     * inner gate's.</li>
     * </ul>
     * Like the establish job it leaves out what a {@code keepOut} box holds (the ground of the modules standing on the
     * campus's sites, a stray module's), so it never touches a standing module, its site floor or its keep-out; the
     * establish floor is never in its floor. And it leaves out every column a neighbouring campus owns
     * ({@link CampusSplit#ours} against {@code neighbours}): with another campus nearer than the outer disc needs it
     * grades and paves only its own side of the split, never the other's, which does the same; the split bends round
     * the outer diagonals, and an outer diagonal a neighbour claimed (a building stands there or a job is queued for
     * one) is that neighbour's whole (0.15.0).
     *
     * @param keepOut    world boxes {x0, z0, x1, z1} or {x0, z0, x1, z1, yFrom, yTo} the job leaves alone (saved with
     *                   the job, so a recomputed plan and the client's are the same)
     * @param neighbours the centres {x, z}, or {x, z, claims}, of the other campuses near enough to share the ground
     *                   (saved with the job)
     * @throws IllegalArgumentException if the front is not a horizontal unit vector or a box is not four or six
     *                                  numbers
     */
    public static Plan outer(CampusPlan plan, int cx, int y0, int cz, int fx, int fz, List<int[]> keepOut,
        List<int[]> neighbours) {
        return outer(plan, cx, y0, cz, fx, fz, keepOut, neighbours, null);
    }

    /**
     * {@link #outer(CampusPlan, int, int, int, int, int, List, List)}, or, with {@code regainFrom} (the neighbours
     * the outer ring was laid with, 0.15.0), only the ground that went to those neighbours then and is this campus's
     * now ({@link #outerRegain}): a neighbour's core broken after the ring was laid gives back its side of the split,
     * which the campus then grades and paves without going over the ground it laid before.
     */
    public static Plan outer(CampusPlan plan, int cx, int y0, int cz, int fx, int fz, List<int[]> keepOut,
        List<int[]> neighbours, List<int[]> regainFrom) {
        return outer(plan, cx, y0, cz, fx, fz, keepOut, neighbours, regainFrom, null);
    }

    /**
     * {@link #outer(CampusPlan, int, int, int, int, int, List, List, List)} limited to the columns inside the world
     * boxes {@code only} {x0, z0, x1, z1} (0.15.0; null for no limit): the outer ring laid again where a module that
     * stood on no site of the campus kept it out (a 0.9.2 hall, a stray core, another campus's building) and has gone
     * since, so the streets and the grass it left round that box are laid; every column outside the boxes is left as
     * the ring laid it.
     */
    public static Plan outer(CampusPlan plan, int cx, int y0, int cz, int fx, int fz, List<int[]> keepOut,
        List<int[]> neighbours, List<int[]> regainFrom, List<int[]> only) {
        facingOrdinal(fx, fz);
        Frame f = new Frame(cx, y0, cz, fx, fz, 1, 0);
        KeepOut out = new KeepOut(keepOut);
        Map<Long, Integer> floor = plan.outerFloor();
        Set<Long> columns = new java.util.LinkedHashSet<>(OuterRing.annulus());
        columns.addAll(floor.keySet());
        List<Clearing> clear = new ArrayList<>();
        for (long k : columns) {
            int[] w = f.world(CampusPlan.keyA(k), CampusPlan.keyR(k));
            if (!within(w, only) || !ownsNow(w, cx, cz, neighbours, regainFrom)) continue;
            if (out.holds(w[0], w[1], y0 + 1, y0 + CLEAR_UP)) continue;
            clear.add(new Clearing(f, w[0], y0 + 1, w[1], y0 + CLEAR_UP, true));
        }
        clear.sort(RIPPLE);

        List<Draft> tiles = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : floor.entrySet()) {
            long k = e.getKey();
            int[] w = f.world(CampusPlan.keyA(k), CampusPlan.keyR(k));
            if (!within(w, only) || !ownsNow(w, cx, cz, neighbours, regainFrom) || out.holds(w[0], w[1], y0, y0))
                continue;
            paving(tiles, f, CampusPlan.keyA(k), CampusPlan.keyR(k), e.getValue(), false);
        }
        tiles.sort(RIPPLE);

        List<Draft> pylons = new ArrayList<>();
        for (int[] p : OuterRing.pylons()) {
            int[] w = f.world(p[0], p[1]);
            if (!within(w, only) || !ownsNow(w, cx, cz, neighbours, regainFrom)) continue;
            if (out.holds(w[0], w[1], y0 + 1, y0 + PYLON_PILLARS + 1)) continue;
            for (int i = 1; i <= PYLON_PILLARS + 1; i++) {
                int part = Parts.frame(i <= PYLON_PILLARS ? Parts.FR_PILLAR : Parts.FR_CONDUIT);
                pylons.add(new Draft(f, w[0], y0 + i, w[1], part, SOFT, NO_GROUP));
            }
        }
        pylons.sort(BOTTOM_UP);

        List<List<Step>> stages = empty(O_STAGES);
        stages.set(O_FLOOR, steps(tiles, O_FLOOR));
        stages.set(O_FIXTURE, steps(pylons, O_FIXTURE));
        return new Plan(OUTER, O_STAGES, stages, columns(clear));
    }

    /** Whether the world column {x, z} lies in one of the boxes {x0, z0, x1, z1} (always, with no boxes given). */
    private static boolean within(int[] w, List<int[]> only) {
        if (only == null) return true;
        for (int[] b : only) if (w[0] >= Math.min(b[0], b[2]) && w[0] <= Math.max(b[0], b[2])
            && w[1] >= Math.min(b[1], b[3])
            && w[1] <= Math.max(b[1], b[3])) return true;
        return false;
    }

    /**
     * Whether the outer establish takes the world column {x, z}: it is the campus's own against {@code neighbours}
     * and, for a job that regains ground ({@code regainFrom}), was its neighbours' when the ring was laid.
     */
    private static boolean ownsNow(int[] w, int cx, int cz, List<int[]> neighbours, List<int[]> regainFrom) {
        if (!CampusSplit.ours(w[0], w[1], cx, cz, neighbours)) return false;
        return regainFrom == null || !CampusSplit.ours(w[0], w[1], cx, cz, regainFrom);
    }

    /**
     * How much of the outer ring's ground (its annulus and the floor in the inner disc, the columns of {@link #outer})
     * the neighbouring campuses take from the campus centred at (cx, cz) facing (fx, fz) ({@link CampusSplit#ours}):
     * {columns given up, columns in all, neighbours that take any} (0.15.0: the last counts the campuses each given-up
     * column goes to, {@link CampusSplit#owner}, not every campus near enough to share the ground). The 建造 page says
     * it while the outer establish is the job.
     */
    public static int[] outerTrimmed(CampusPlan plan, int cx, int cz, int fx, int fz, List<int[]> neighbours) {
        Set<Long> columns = new java.util.LinkedHashSet<>(OuterRing.annulus());
        columns.addAll(
            plan.outerFloor()
                .keySet());
        int lost = 0;
        Set<Integer> takers = new HashSet<>();
        if (neighbours != null && !neighbours.isEmpty()) for (long k : columns) {
            int[] d = CampusPlan.toWorld(CampusPlan.keyA(k), CampusPlan.keyR(k), fx, fz);
            int who = CampusSplit.owner(cx + d[0], cz + d[1], cx, cz, 0, neighbours);
            if (who == -1) continue;
            lost++;
            if (who >= 0) takers.add(who);
        }
        return new int[] { lost, columns.size(), takers.size() };
    }

    /**
     * The ground an outer ring laid with the neighbours {@code laidWith} has to regain now that it shares the ground
     * with {@code neighbours} (0.15.0): the columns of {@link #outer} that went to the old neighbours and are the
     * campus's own now, counted. 0 when nothing came back (a neighbour that went had taken nothing, or a new one took
     * its place).
     */
    public static int outerRegain(CampusPlan plan, int cx, int cz, int fx, int fz, List<int[]> laidWith,
        List<int[]> neighbours) {
        if (laidWith == null || laidWith.isEmpty()) return 0;
        Set<Long> columns = new java.util.LinkedHashSet<>(OuterRing.annulus());
        columns.addAll(
            plan.outerFloor()
                .keySet());
        int n = 0;
        for (long k : columns) {
            int[] d = CampusPlan.toWorld(CampusPlan.keyA(k), CampusPlan.keyR(k), fx, fz);
            if (ownsNow(new int[] { cx + d[0], cz + d[1] }, cx, cz, neighbours, laidWith)) n++;
        }
        return n;
    }

    // ---- the nexus grows (0.12.0) ----

    /**
     * The plan key of the establish plan raising the nexus of a phase: {@link #ESTABLISH} for phase I (the plan of
     * every new campus), {@code establish:<phase>} above it, the plan a repair of a grown nexus builds (its stages, its
     * lift pad) so that a repair never takes a grown nexus back to phase I.
     */
    public static String establishKey(int phase) {
        return phase <= 1 ? ESTABLISH : ESTABLISH + ":" + Math.min(NexusShape.PHASES, phase);
    }

    /** The nexus phase an establish plan key raises ({@link #establishKey}), or 0 for any other key. */
    public static int establishPhase(String key) {
        if (ESTABLISH.equals(key)) return 1;
        return phaseAfter(key, ESTABLISH + ":");
    }

    /** The plan key of the growth job to a phase: {@code grow:<phase>}. */
    public static String growKey(int phase) {
        return GROW + ":" + phase;
    }

    /** The phase a growth plan key grows the nexus to ({@link #growKey}), or 0 for any other key. */
    public static int growPhase(String key) {
        int p = phaseAfter(key, GROW + ":");
        return p >= 2 ? p : 0;
    }

    private static int phaseAfter(String key, String prefix) {
        if (key == null || !key.startsWith(prefix)) return 0;
        try {
            int p = Integer.parseInt(key.substring(prefix.length()));
            return p >= 1 && p <= NexusShape.PHASES ? p : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * The growth job of a formed nexus to {@code phase} (0.12.0, CITY.md §3), in the establish job's stage numbers:
     * <ul>
     * <li>CLEAR: the base's columns over the new stage, its five layers and two more (Y0 + 5(p-1) + 1 to Y0 + 5p + 2),
     * outward from the centre; the columns are not graded.</li>
     * <li>CORE: the stage's cells bottom-up, each layer clockwise from the front, as the establish job raises the rest
     * of the nexus; at phase II then the light-lift pad in the middle of the base, last: it takes the place of the lit
     * hub there (a block of ours, so the builder credits it to the ledger), and the moment it lands the phase-I shape
     * no longer holds while the phase-II shape does.</li>
     * <li>FORM: no steps; the builder waits for the nexus to stand as the new phase.</li>
     * <li>FLOOR and FIXTURE: empty.</li>
     * </ul>
     * Every cell is placed where {@link Blueprint#world} puts it for the controller in the phase's own blueprint, so
     * the HARD steps are exactly the cells the structure check of that phase adds.
     *
     * @throws IllegalArgumentException if the phase is not 2 to {@link NexusShape#PHASES} or the front not a
     *                                  horizontal unit vector
     */
    public static Plan grow(int phase, int cx, int y0, int cz, int fx, int fz, int ctrlX, int ctrlY, int ctrlZ) {
        facingOrdinal(fx, fz);
        if (phase < 2 || phase > NexusShape.PHASES) throw new IllegalArgumentException("no growth to phase " + phase);
        Frame f = new Frame(cx, y0, cz, fx, fz, 1, 0);
        Blueprint nexus = NexusShape.phase(phase);
        List<Clearing> clear = new ArrayList<>();
        int from = y0 + NexusShape.STAGE * (phase - 1) + 1, to = y0 + NexusShape.STAGE * phase + 2;
        for (int a = -DAIS_R; a <= DAIS_R; a++) for (int r = -DAIS_R; r <= DAIS_R; r++) {
            if (!CampusPlan.inBase(a, r)) continue;
            int[] w = f.world(a, r);
            clear.add(new Clearing(f, w[0], from, w[1], to, false));
        }
        clear.sort(RIPPLE);
        List<Draft> stage = new ArrayList<>(), pad = new ArrayList<>();
        for (int[] c : NexusShape.stageCells(phase)) {
            // the cell in the phase's blueprint: across, layer from the top, row from the front
            int a = c[1] + NexusShape.RADIUS, b = nexus.height() - 1 - c[0], row = c[2] + NexusShape.RADIUS;
            int[] w = nexus.world(a, b, row, ctrlX, ctrlY, ctrlZ, fx, fz);
            Draft d = new Draft(f, w[0], w[1], w[2], nexusPart((char) c[3]), HARD, NO_GROUP);
            (c[3] == NexusShape.LIFT ? pad : stage).add(d);
        }
        stage.sort(
            Comparator.<Draft>comparingInt(d -> d.y)
                .thenComparingDouble(Draft::angle)
                .thenComparing(BOTTOM_UP));
        stage.addAll(pad);
        List<List<Step>> stages = empty(E_STAGES);
        stages.set(E_CORE, steps(stage, E_CORE));
        return new Plan(growKey(phase), E_STAGES, stages, columns(clear));
    }

    /** The CORE stage: the nexus blueprint but its controller, in its ceremonial order. */
    private static List<Step> core(Frame f, Blueprint nexus, int ctrlX, int ctrlY, int ctrlZ) {
        List<Draft> console = new ArrayList<>(), spokes = new ArrayList<>(), base = new ArrayList<>(),
            rest = new ArrayList<>();
        for (Blueprint.Cell c : nexus.cells()) {
            if (c.ch == '~') continue;
            int[] w = nexus.world(c.a, c.b, c.c, ctrlX, ctrlY, ctrlZ, f.fx, f.fz);
            Draft d = new Draft(f, w[0], w[1], w[2], nexusPart(c.ch), HARD, NO_GROUP);
            if (c.ch == NexusShape.CONSOLE) console.add(d);
            else if (c.ch == NexusShape.LIT) spokes.add(d);
            else if (c.ch == NexusShape.BASE) base.add(d);
            else rest.add(d);
        }
        console.sort(BOTTOM_UP);
        // the spokes shoot out from the middle together, one ring of cells at a time
        spokes.sort(
            Comparator.<Draft>comparingInt(d -> Math.max(Math.abs(d.t), Math.abs(d.s)))
                .thenComparingDouble(Draft::angle)
                .thenComparing(BOTTOM_UP));
        // the base fills in by octagon distance (the dais is oct(5, 7)), each ring clockwise from the front
        base.sort(
            Comparator.<Draft>comparingInt(d -> octagon(d.t, d.s))
                .thenComparingDouble(Draft::angle)
                .thenComparing(BOTTOM_UP));
        rest.sort(
            Comparator.<Draft>comparingInt(d -> d.y)
                .thenComparingDouble(Draft::angle)
                .thenComparing(BOTTOM_UP));
        List<Draft> all = new ArrayList<>(console);
        all.addAll(spokes);
        all.addAll(base);
        all.addAll(rest);
        return steps(all, E_CORE);
    }

    /** The frame part a nexus blueprint character stands for. */
    private static int nexusPart(char ch) {
        switch (ch) {
            case NexusShape.BASE:
                return Parts.frame(Parts.FR_BASE);
            case NexusShape.LIT:
                return Parts.frame(Parts.FR_BASE_LIT);
            case NexusShape.PILLAR:
                return Parts.frame(Parts.FR_PILLAR);
            case NexusShape.CONDUIT:
                return Parts.frame(Parts.FR_CONDUIT);
            case NexusShape.RING:
                return Parts.frame(Parts.FR_RING);
            case NexusShape.SEAT:
                return Parts.frame(Parts.FR_SEAT);
            case NexusShape.CONSOLE:
                return Parts.frame(Parts.FR_CONSOLE);
            case NexusShape.LIFT:
                return Parts.fitting(Parts.F_LIFT);
            default:
                throw new IllegalArgumentException("not a nexus character: '" + ch + "'");
        }
    }

    /** The octagon distance scaled by {@link #DAIS_K}: the dais {@code oct(5, 7)} is everything up to 35. */
    private static int octagon(int a, int r) {
        int aa = Math.abs(a), ar = Math.abs(r);
        return Math.max(DAIS_K * Math.max(aa, ar), DAIS_R * (aa + ar));
    }

    // ---- module plans (the Archive, and every city module since 0.11.0) ----

    /**
     * What the generic module plan ({@link #module}) needs to know about a module's shape (0.11.0): its box and
     * blueprint, the character and part of every cell, which stage each cell is built in, its units (one launch group
     * each), the decor outside the box and whether the DECOR stage adds seeded ornaments. The Echo Archive
     * ({@link #ARCHIVE}) and the city modules ({@link CityBuild}) both describe themselves this way, so one builder
     * lays them all.
     */
    public interface ModuleBuild {

        /** The module key: the {@code <module>} of the job key. */
        String key();

        /** The box across. */
        int width();

        /** The box from the front. */
        int depth();

        /** The box up: the top layer is {@code height() - 1}. */
        int height();

        /** The checked shape, as the structure check reads it. */
        Blueprint blueprint();

        /** The blueprint cell {across, layer from the top, row from the front} of a local point. */
        int[] cellOf(int x, int y, int z);

        /** The controller's local cell {x, y, z}: on the axis, in the front wall. */
        int[] controller();

        /**
         * The local character at a cell: a checked part, a soft character ({@link ShapeKit#soft}: built, never
         * checked), {@code -} (must be air), {@code ~} (the controller) or a space (nothing to build).
         */
        char cell(int x, int y, int z);

        /** The part a cell is built as (the controller's: the module's core). */
        int part(int x, int y, int z, char ch);

        /** The stage ({@code M_FLOOR .. M_COMMISSION}) of a cell that belongs to no unit. */
        int stage(int x, int y, int z, char ch);

        /**
         * The order within the FIT stage of a cell that belongs to no unit: negative ranks come before the units,
         * positive ones after them, each rank bottom-up; never 0.
         */
        int fitRank(int x, int y, int z, char ch);

        /**
         * The order within its stage of a cell that belongs to no unit, in the stages built bottom-up (SHELL, DECK,
         * UPPER, ROOF, DECOR; not FLOOR or FIT): a stage lays its cells by this order first, lower first, each order
         * bottom-up (0.13.0: the Echo Hunting Ground's terraces ring by ring from the arena out, then the vomitoria's
         * bridges). 0 for every cell, as every module before has it, keeps each stage bottom-up as it always was.
         */
        default int order(int x, int y, int z, char ch) {
            return 0;
        }

        /**
         * The order its stages are built in (0.15.0): the stage ({@code M_CLEAR .. M_COMMISSION}) each of the plan's
         * stages holds, by position, a reordering that keeps CLEAR and FLOOR first and COMMISSION last; null for the
         * generic order, every module's before the Sophon Array, which raises its struts and lattice (UPPER) after its
         * racks (FIT) as CITY.md lists them. The job, its pace and the 建造 page count the plan's stages by position.
         */
        default int[] stageOrder() {
            return null;
        }

        /** The cells {x, y, z} of every unit, in unit order: each unit is one launch group (its index the group id). */
        List<List<int[]>> units();

        /** The cells outside the box, {x, y, z, part}: the entrance canopy. */
        List<int[]> decor();

        /** Whether the DECOR stage adds the seeded ornaments ({@link #ornaments}). */
        boolean ornaments();
    }

    /**
     * Where the controller of a hall module stands on a hall site, and which way it faces: {x, y, z, facing ordinal}.
     * The module's front row lies at radial {@link CampusPlan#HALL_FRONT} on the site axis, its controller on the
     * axis {@code ctrlZ} rows in and {@code ctrlY} above Y0, and its front faces the nexus (the facing is the site axis
     * reversed, as a ForgeDirection ordinal from 2 to 5), so {@link Blueprint#world} puts the module's centre on
     * {@link CampusPlan#moduleCentre}.
     *
     * @throws IllegalArgumentException if the site is not a hall site or the front not a horizontal unit vector
     */
    public static int[] hallController(int site, int cx, int y0, int cz, int fx, int fz, int ctrlY, int ctrlZ) {
        facingOrdinal(fx, fz);
        if (!hallSite(site)) throw new IllegalArgumentException("not a hall site: " + site);
        int[] ax = CampusPlan.siteAxis(site);
        int[] out = CampusPlan.toWorld(ax[0], ax[1], fx, fz);
        int t = CampusPlan.HALL_FRONT + ctrlZ;
        return new int[] { cx + out[0] * t, y0 + ctrlY, cz + out[1] * t, facingOrdinal(-out[0], -out[1]) };
    }

    /** Where a module's controller stands on a hall site: {@link #hallController} at its shape's controller cell. */
    public static int[] hallController(ModuleBuild m, int site, int cx, int y0, int cz, int fx, int fz) {
        int[] c = m.controller();
        return hallController(site, cx, y0, cz, fx, fz, c[1], c[2]);
    }

    /**
     * Where the controller of a pavilion stands on a pavilion site (0.13.0, CITY.md §2.1), and which way it faces:
     * {x, y, z, facing ordinal}. The middle column of the module's box stands on the site's octagon centre
     * ({@link CampusPlan#diagonalCentre}, (+-36, +-36)) with its foundation at Y0, and its blueprint's front faces
     * along A toward the nexus ({@link CampusPlan#pavilionFront}: sites 1 and 7 look along -A, sites 3 and 5 along +A).
     * A centrally planned building's controller is that middle column's ({@code (12, 2, 12)}: {@code (+-36, Y0 + 2,
     * +-36)}), so {@link Blueprint#world} puts the module's centre on {@link CampusPlan#moduleCentre}.
     *
     * @throws IllegalArgumentException if the site is not a pavilion site or the front not a horizontal unit vector
     */
    public static int[] pavilionController(ModuleBuild m, int site, int cx, int y0, int cz, int fx, int fz) {
        facingOrdinal(fx, fz);
        if (SiteFamily.of(site) != SiteFamily.PAVILION)
            throw new IllegalArgumentException("not a pavilion site: " + site);
        return centredController(
            m,
            CampusPlan.diagonalCentre(site),
            CampusPlan.pavilionFront(site),
            cx,
            y0,
            cz,
            fx,
            fz);
    }

    /**
     * Where the controller of an outer diagonal's building stands on its site (0.15.0, CITY.md §2.1, §2.3), and which
     * way it faces: {x, y, z, facing ordinal}. As a pavilion's ({@link #pavilionController}): the middle column of the
     * module's box stands on the site's octagon centre ({@link OuterRing#diagCentre}, (+-62, +-62)) with its
     * foundation at Y0, its blueprint's front faces along A toward the nexus ({@link OuterRing#diagFront}), and the
     * controller lies where the building puts it in that frame (the Sophon Array's at the centre, the Holo Sand
     * Table's six cells toward its front).
     *
     * @throws IllegalArgumentException if the site is not an outer diagonal or the front not a horizontal unit vector
     */
    public static int[] outerDiagController(ModuleBuild m, int site, int cx, int y0, int cz, int fx, int fz) {
        facingOrdinal(fx, fz);
        if (SiteFamily.of(site) != SiteFamily.OUTER_DIAG)
            throw new IllegalArgumentException("not an outer diagonal: " + site);
        return centredController(m, OuterRing.diagCentre(site), OuterRing.diagFront(site), cx, y0, cz, fx, fz);
    }

    /**
     * Where a centrally planned building's controller stands, {x, y, z, facing ordinal}: the middle column of its box
     * on {@code centre} (campus frame), its blueprint's front facing {@code front} ({da, dr}).
     */
    private static int[] centredController(ModuleBuild m, int[] centre, int[] front, int cx, int y0, int cz, int fx,
        int fz) {
        int[] fw = CampusPlan.toWorld(front[0], front[1], fx, fz);
        int[] c = m.controller();
        // the controller lies (dx, dz) from the box's middle column in the shape's frame, which Blueprint.world turns
        // into the world as across (fz, -fx) and back (-fx, -fz)
        int dx = c[0] - (m.width() - 1) / 2, dz = c[2] - (m.depth() - 1) / 2;
        int ox = dx * fw[1] - dz * fw[0], oz = -dx * fw[0] - dz * fw[1];
        int[] w = CampusPlan.toWorld(centre[0], centre[1], fx, fz);
        return new int[] { cx + w[0] + ox, y0 + c[1], cz + w[1] + oz, facingOrdinal(fw[0], fw[1]) };
    }

    /**
     * Where the controller of a gatehouse stands on the gate site (0.14.0, CITY.md §2.1), and which way it faces:
     * {x, y, z, facing ordinal}. The module's box straddles the gate road with its middle column on the road's axis
     * ({@code x = MID + R}) and its front row at {@link CampusPlan#GATEHOUSE_FROM} ({@code z = A - 23}), its front
     * facing the nexus (-A); the controller lies where its shape's cell falls in that frame (the Resonance Gate's
     * kiosk {@code (15, 2, 7)}: {@code (A 30, R 0, Y0 + 2)}).
     *
     * @throws IllegalArgumentException if the site is not the gate site or the front not a horizontal unit vector
     */
    public static int[] gateController(ModuleBuild m, int site, int cx, int y0, int cz, int fx, int fz) {
        facingOrdinal(fx, fz);
        if (SiteFamily.of(site) != SiteFamily.GATE || !SiteFamily.ringSite(site))
            throw new IllegalArgumentException("not the gate site: " + site);
        int[] c = m.controller();
        int a = CampusPlan.GATEHOUSE_FROM + c[2], r = c[0] - (m.width() - 1) / 2;
        int[] w = CampusPlan.toWorld(a, r, fx, fz);
        return new int[] { cx + w[0], y0 + c[1], cz + w[1], facingOrdinal(-fx, -fz) };
    }

    /**
     * Where a module's controller stands on a ground site of its family: {@link #pavilionController} on a pavilion
     * site, {@link #gateController} on the gate site (0.14.0), {@link #outerDiagController} on an outer diagonal
     * (0.15.0), {@link #hallController} anywhere else (which takes hall sites only).
     */
    public static int[] moduleController(ModuleBuild m, int site, int cx, int y0, int cz, int fx, int fz) {
        SiteFamily family = SiteFamily.of(site);
        if (family == SiteFamily.PAVILION) return pavilionController(m, site, cx, y0, cz, fx, fz);
        if (family == SiteFamily.GATE) return gateController(m, site, cx, y0, cz, fx, fz);
        if (family == SiteFamily.OUTER_DIAG) return outerDiagController(m, site, cx, y0, cz, fx, fz);
        return hallController(m, site, cx, y0, cz, fx, fz);
    }

    /**
     * Where the controller of an Archive on a hall site stands, and which way it faces: {x, y, z, facing ordinal}
     * ({@link #hallController} at the Archive's {@link ArchiveShape#CTRL_Y} and {@link ArchiveShape#CTRL_Z}).
     *
     * @throws IllegalArgumentException if the site is not a hall site or the front not a horizontal unit vector
     */
    public static int[] archiveController(int site, int cx, int y0, int cz, int fx, int fz) {
        return hallController(site, cx, y0, cz, fx, fz, ArchiveShape.CTRL_Y, ArchiveShape.CTRL_Z);
    }

    /**
     * The module job of an Echo Archive on a hall site, through the generic {@link #module} plan ({@link #ARCHIVE}).
     * <ul>
     * <li>CLEAR: the footprint and {@link #ARCHIVE_MARGIN} round it, from Y0 + 1 to Y0 + {@link ArchiveShape#HEIGHT}
     * + {@link #ARCHIVE_HEADROOM}, outward from the nexus. Since 0.10.1 these columns grade too: the Y0 cell of a
     * column the plan does not pave becomes grass and the ground under it is filled, as on the campus disc.</li>
     * <li>FLOOR: {@link CampusPlan#siteFloor} and the interior soft floor (deck, a dashed lit spine on the axis from
     * z1 to z28), outward from the nexus.</li>
     * <li>SHELL: the foundations of the floor layer y0 first (they carry everything above them), then the outer walls
     * y1..y5 (panels, pilasters, glazing, the plinth course, the trumeau and the lintel) and every must-be-air door
     * cell.</li>
     * <li>DECK: everything at y6 (gallery slab, lit edge, landings, the walls' y6 band), the rails, and both spiral
     * stairs with their cores.</li>
     * <li>UPPER: the outer walls y7..y11.</li>
     * <li>ROOF: everything from y12 to y15.</li>
     * <li>FIT: the unit posts; then every shelf unit as one group of fifteen (three plinths, nine bodies, three
     * crowns), in {@link LibraryUnits#UNITS} order; then the pedestals, the lecterns and the desk.</li>
     * <li>DECOR: the entrance canopy ({@link ArchiveShape#decor()}); no seeded ornaments, so the plan is cell for cell
     * the plan of 0.10.</li>
     * <li>COMMISSION: the controller (part {@link Parts#LIBRARY_CORE}), the last step.</li>
     * </ul>
     * Every blueprint cell is placed where {@link Blueprint#world} puts it for {@link #archiveController}, so the
     * HARD steps are exactly the cells the structure check looks at.
     *
     * @throws IllegalArgumentException if the site is not a hall site or the front not a horizontal unit vector
     */
    public static Plan archive(CampusPlan plan, int site, int cx, int y0, int cz, int fx, int fz) {
        return module(plan, site, cx, y0, cz, fx, fz, ARCHIVE);
    }

    /**
     * The module job of a hall module (0.11.0), a pavilion (0.13.0), the gatehouse (0.14.0) or an outer diagonal's
     * building (0.15.0, laid out as a pavilion is): the generic plan the Archive and every city module on a ground site
     * share.
     * <ul>
     * <li>CLEAR: the footprint and {@link #MODULE_MARGIN} round it (a hall's whole box, a pavilion's or an outer
     * diagonal's octagon), from Y0
     * + 1 to {@link #MODULE_HEADROOM} above the shape's top layer that holds anything, outward from the nexus, grading
     * their Y0 cells. The gatehouse's box less its open passage ({@link CampusPlan#inGatePassage}): over the passage
     * only the columns its bridge stands in, from Y0 + {@link CampusPlan#GATE_HEADROOM} up and never graded, so the
     * gate road, its widenings and the establish pylons under the bridge stay as they are.</li>
     * <li>FLOOR: the site's floor ({@link CampusPlan#siteFloor} for a hall, {@link CampusPlan#pavilionFloor} for a
     * pavilion: its apron and its diagonal allee, {@link CampusPlan#gatehouseFloor} for the gatehouse: its apron,
     * {@link CampusPlan#outerDiagFloor} for an outer diagonal: its apron and its allée from the outer promenade) and
     * the soft cells the module puts in FLOOR, outward from the nexus.</li>
     * <li>SHELL, DECK, UPPER, ROOF: the cells the module puts there, bottom-up by layer (by the module's own order
     * first where it gives one, {@link ModuleBuild#order}, 0.13.0: the Echo Hunting Ground's terraces ring by
     * ring).</li>
     * <li>FIT: the cells ranked before the units, every unit as one contiguous group in unit order (bottom-up within
     * the group), then the cells ranked after them.</li>
     * <li>DECOR: the shape's decor and, for a module that wants them, the seeded ornaments ({@link #ornaments}; a
     * pavilion's annex beside a flat side, {@link #PAVILION_ANNEX_FROM}; an outer diagonal's,
     * {@link #OUTER_ANNEX_FROM}).</li>
     * <li>COMMISSION: the controller, the last step.</li>
     * </ul>
     * A soft cell is a SOFT step, {@code -} an AIR step, every other cell a HARD step placed where
     * {@link Blueprint#world} puts it for the module's controller ({@link #moduleController}), so the HARD steps are
     * exactly the cells the structure check looks at.
     *
     * @throws IllegalArgumentException if the site is neither a hall site, a pavilion site, the gate site nor an outer
     *                                  diagonal, or the front not a horizontal unit vector
     * @throws IllegalStateException    if a cell has no stage, or the units do not hold exactly the shape's unit cells
     */
    public static Plan module(CampusPlan plan, int site, int cx, int y0, int cz, int fx, int fz, ModuleBuild m) {
        SiteFamily family = SiteFamily.of(site);
        boolean pavilion = family == SiteFamily.PAVILION, gate = family == SiteFamily.GATE;
        // an outer diagonal's building (0.15.0) is centrally planned as a pavilion is: its octagon's footprint clears
        boolean outerDiag = family == SiteFamily.OUTER_DIAG, centred = pavilion || outerDiag;
        int[] ctrl = moduleController(m, site, cx, y0, cz, fx, fz);
        int[] ax = CampusPlan.siteAxis(site);
        Frame f = new Frame(cx, y0, cz, fx, fz, ax[0], ax[1]);
        Local at = new Local(ctrl, m);
        final int w = m.width(), d = m.depth(), h = m.height();

        // the clear box reaches MODULE_HEADROOM above the top layer that holds anything
        int top = 0;
        for (int y = 0; y < h; y++) for (int z = 0; z < d; z++) for (int x = 0; x < w; x++) {
            if (m.cell(x, y, z) != ShapeKit.NONE) top = Math.max(top, y);
        }
        // a hall clears its whole box and MODULE_MARGIN round it; a pavilion its footprint (the columns its base layer
        // holds) and MODULE_MARGIN round that, its octagon oct(12, 16) grown to oct(13, 18), inside its apron; the
        // gatehouse its box and MODULE_MARGIN round it but over its open passage, where only the bridge's columns
        // clear, from the passage's headroom up
        Set<Long> footprint = centred ? footprint(m) : null;
        List<Clearing> clear = new ArrayList<>();
        for (int z = -MODULE_MARGIN; z < d + MODULE_MARGIN; z++) {
            for (int x = -MODULE_MARGIN; x < w + MODULE_MARGIN; x++) {
                if (centred && !near(footprint, x, z, MODULE_MARGIN)) continue;
                int[] p = at.world(x, 0, z);
                if (gate) {
                    int[] l = CampusPlan.toLocal(p[0] - cx, p[2] - cz, fx, fz);
                    if (Math.abs(l[1]) <= CampusPlan.GATE_PASSAGE) {
                        // the passage stays open: only the bridge's volume over it, from its headroom up, ungraded
                        if (!overPassage(m, x, z, h)) continue;
                        clear.add(
                            new Clearing(
                                f,
                                p[0],
                                y0 + CampusPlan.GATE_HEADROOM,
                                p[2],
                                y0 + top + 1 + MODULE_HEADROOM,
                                false));
                        continue;
                    }
                }
                clear.add(new Clearing(f, p[0], y0 + 1, p[2], y0 + top + 1 + MODULE_HEADROOM, true));
            }
        }
        if (outerDiag) {
            // an outer diagonal's building clears and grades the rest of its site too, its apron's outer row and its
            // allée (0.15.0): the outer establish graded them only where the split gave them to the campus then, and
            // a site the campus took later by its claim, or got back from a neighbour that went, is graded here
            Set<Long> taken = new HashSet<>();
            for (Clearing c : clear) taken.add(CampusPlan.key(c.x, c.z));
            for (long k : OuterRing.diagSiteCells(site)) {
                int[] at2 = f.world(CampusPlan.keyA(k), CampusPlan.keyR(k));
                if (!taken.add(CampusPlan.key(at2[0], at2[1]))) continue;
                clear.add(new Clearing(f, at2[0], y0 + 1, at2[1], y0 + top + 1 + MODULE_HEADROOM, true));
            }
        }
        clear.sort(RIPPLE);

        List<List<Draft>> stages = new ArrayList<>();
        for (int i = 0; i < M_STAGES; i++) stages.add(new ArrayList<>());
        List<Draft> floor = stages.get(M_FLOOR);
        Map<Long, Integer> siteFloor = pavilion ? plan.pavilionFloor(site)
            : outerDiag ? plan.outerDiagFloor(site) : gate ? plan.gatehouseFloor() : plan.siteFloor(site, w, d);
        for (Map.Entry<Long, Integer> e : siteFloor.entrySet()) {
            long k = e.getKey();
            paving(
                floor,
                f,
                CampusPlan.keyA(k),
                CampusPlan.keyR(k),
                e.getValue(),
                plan.wells()
                    .contains(k));
        }
        // the units' cells: the scan below leaves them to their groups
        Set<Long> unitCells = new HashSet<>();
        for (List<int[]> u : m.units()) for (int[] c : u) unitCells.add(ShapeKit.Shape.pos(c[0], c[1], c[2]));
        List<Draft> before = new ArrayList<>(), after = new ArrayList<>();
        int shapeUnitCells = 0;
        for (int y = 0; y < h; y++) for (int z = 0; z < d; z++) for (int x = 0; x < w; x++) {
            char ch = m.cell(x, y, z);
            if (ch == ShapeKit.NONE) continue;
            if (unitCells.contains(ShapeKit.Shape.pos(x, y, z))) {
                shapeUnitCells++;
                continue;
            }
            int[] p = at.world(x, y, z);
            byte kind = ch == ShapeKit.AIR ? AIR : ShapeKit.soft(ch) ? SOFT : HARD;
            int stage = m.stage(x, y, z, ch);
            if (stage <= M_CLEAR || stage >= M_STAGES)
                throw new IllegalStateException(m.key() + ": no stage for '" + ch + "' at " + x + "," + y + "," + z);
            Draft dr = new Draft(f, p[0], p[1], p[2], m.part(x, y, z, ch), kind, NO_GROUP);
            if (stage == M_FIT) {
                int rank = m.fitRank(x, y, z, ch);
                if (rank == 0) throw new IllegalStateException(m.key() + ": a fitting ranked with the units");
                dr.rank = rank;
                (rank < 0 ? before : after).add(dr);
            } else {
                // its order within a stage built bottom-up (0 for every module but those that give one)
                if (stage != M_FLOOR) dr.rank = m.order(x, y, z, ch);
                stages.get(stage)
                    .add(dr);
            }
        }
        List<Draft> decor = stages.get(M_DECOR);
        for (int[] c : m.decor()) {
            int[] p = at.world(c[0], c[1], c[2]);
            decor.add(new Draft(f, p[0], p[1], p[2], c[3], SOFT, NO_GROUP));
        }
        if (m.ornaments()) ornaments(
            decor,
            f,
            at,
            m,
            Mix.seed(ctrl[0], ctrl[1], ctrl[2]),
            pavilion ? PAVILION_ANNEX_FROM : outerDiag ? OUTER_ANNEX_FROM : ANNEX_FROM);

        // every unit as one contiguous group, bottom-up within it
        List<Draft> units = new ArrayList<>();
        List<List<int[]>> groups = m.units();
        for (int i = 0; i < groups.size(); i++) {
            List<Draft> group = new ArrayList<>();
            for (int[] c : groups.get(i)) {
                char ch = m.cell(c[0], c[1], c[2]);
                if (!ShapeKit.checked(ch) || ch == ShapeKit.CONTROLLER)
                    throw new IllegalStateException(m.key() + ": a unit cell is not a part");
                int[] p = at.world(c[0], c[1], c[2]);
                group.add(new Draft(f, p[0], p[1], p[2], m.part(c[0], c[1], c[2], ch), HARD, i));
            }
            group.sort(BOTTOM_UP);
            units.addAll(group);
        }
        if (units.size() != shapeUnitCells) {
            throw new IllegalStateException("the units hold " + units.size() + " cells, the shape " + shapeUnitCells);
        }

        // FLOOR ripples out from the nexus; the other stages but FIT go by their cells' order, each order bottom-up
        // (with every order 0, as before 0.13.0, that is bottom-up alone: the same comparisons, the same plan)
        Comparator<Draft> ranked = Comparator.<Draft>comparingInt(dr -> dr.rank)
            .thenComparing(BOTTOM_UP);
        for (int s = M_FLOOR; s < M_STAGES; s++) {
            if (s != M_FIT) stages.get(s)
                .sort(s == M_FLOOR ? RIPPLE : ranked);
        }
        before.sort(ranked);
        after.sort(ranked);
        List<Draft> fit = stages.get(M_FIT);
        fit.addAll(before);
        fit.addAll(units);
        fit.addAll(after);

        // the stages in the module's order (0.15.0): by position, each holding the stage its order names there
        int[] order = stageOrder(m);
        List<List<Step>> out = empty(M_STAGES);
        for (int p = M_FLOOR; p < M_STAGES; p++) out.set(p, steps(stages.get(order[p]), p));
        return new Plan(moduleKey(m.key(), site), M_STAGES, out, columns(clear));
    }

    /**
     * A module's stage order ({@link ModuleBuild#stageOrder}), the generic one when it gives none.
     *
     * @throws IllegalStateException when it is no reordering of the module stages keeping CLEAR and FLOOR first and
     *                               COMMISSION last
     */
    static int[] stageOrder(ModuleBuild m) {
        int[] order = m.stageOrder();
        int[] out = new int[M_STAGES];
        for (int i = 0; i < M_STAGES; i++) out[i] = i;
        if (order == null) return out;
        boolean[] seen = new boolean[M_STAGES];
        if (order.length != M_STAGES || order[M_CLEAR] != M_CLEAR
            || order[M_FLOOR] != M_FLOOR
            || order[M_COMMISSION] != M_COMMISSION)
            throw new IllegalStateException(m.key() + ": a stage order keeps CLEAR, FLOOR first and COMMISSION last");
        for (int s : order) {
            if (s < 0 || s >= M_STAGES || seen[s]) throw new IllegalStateException(m.key() + ": not a stage order");
            seen[s] = true;
        }
        return order.clone();
    }

    /**
     * The highest y a module plan's controller (its last stage, the commissioning) or a cell of one of its units
     * takes, or {@link Integer#MIN_VALUE} for a plan with neither (0.14.1): a module on the ground fits a campus while
     * these stay in the world, whatever of its shape over them clips at the world's top.
     */
    public static int standTop(Plan p) {
        int top = Integer.MIN_VALUE, last = p.start(p.stages - 1);
        for (int i = 0; i < p.steps.size(); i++) {
            Step s = p.steps.get(i);
            if (s.group != NO_GROUP || i >= last) top = Math.max(top, s.y);
        }
        return top;
    }

    /**
     * The module job of the Echo Dome Generator on the zenith (0.12.0, CITY.md §4.8), in its own stages
     * ({@link DomeGeneratorShape#Z_CLEAR} ..):
     * <ul>
     * <li>CLEAR: every leg column over the heights its leg takes there (so the forum's tiles and the supply port and
     * recycle bin under a leg stay), and the pod's box with {@link DomeGeneratorShape#POD_MARGIN} round it from Y0 + 40
     * to Y0 + {@link DomeGeneratorShape#CLEAR_TOP}; nothing is graded, outward from the nexus.</li>
     * <li>LEGS: the 234 leg cells, all six legs together, bottom-up by layer.</li>
     * <li>FLOOR: the pod's floor (foundation, soft deck, the lift pad), outward from the axis.</li>
     * <li>SHELL: the walls y1..y5 and the door openings' soft glazing, bottom-up.</li>
     * <li>ROOF: the cornice, the ceiling, the saucer, the shaft's soft cap and the emitter nodes, bottom-up.</li>
     * <li>FIT: the controller's console, then every ward unit as one group (console and socket) in unit order.</li>
     * <li>COMMISSION: the controller (its module core), the last step.</li>
     * </ul>
     * Nothing needs support: the builder places cells in the air as it does any other. Every checked cell is placed
     * where {@link Blueprint#world} puts it for the controller ({@link DomeGeneratorShape#controller}) in the
     * {@code moored} blueprint, so the HARD steps are exactly the cells that shape's structure check looks at.
     *
     * @throws IllegalArgumentException if the site is not the zenith or the front not a horizontal unit vector
     * @throws IllegalStateException    if a cell has no stage, or the units do not hold exactly the pod's unit cells
     */
    public static Plan zenith(int site, int cx, int y0, int cz, int fx, int fz) {
        // the sky datum the base layer gives it (0.14.1): its legs, its pod's height and its clear box follow it
        DomeGeneratorShape.Moor moor = DomeGeneratorShape.forBase(y0);
        CityBuild m = moor.build;
        int[] ctrl = moor.controller(site, cx, y0, cz, fx, fz);
        Frame f = new Frame(cx, y0, cz, fx, fz, 1, 0);
        Local at = new Local(ctrl, m);

        List<Clearing> clear = new ArrayList<>();
        for (int[] c : moor.legColumns()) {
            int[] w = f.world(c[0], c[1]);
            clear.add(new Clearing(f, w[0], y0 + c[2], w[1], y0 + c[3], false));
        }
        int lo = -DomeGeneratorShape.POD_MARGIN, hi = DomeGeneratorShape.SIZE + DomeGeneratorShape.POD_MARGIN;
        for (int z = lo; z < hi; z++) for (int x = lo; x < hi; x++) {
            int[] p = at.world(x, 0, z);
            clear.add(new Clearing(f, p[0], p[1], p[2], y0 + moor.clearTop(), false));
        }
        clear.sort(RIPPLE);

        List<List<Draft>> stages = new ArrayList<>();
        for (int i = 0; i < DomeGeneratorShape.Z_STAGES; i++) stages.add(new ArrayList<>());
        for (int[] c : moor.legPoints()) {
            int[] p = at.world(c[0], c[1], c[2]);
            stages.get(DomeGeneratorShape.Z_LEGS)
                .add(new Draft(f, p[0], p[1], p[2], ShapeKit.partOf(DomeGeneratorShape.LEG), HARD, NO_GROUP));
        }
        Set<Long> unitCells = new HashSet<>();
        for (List<int[]> u : m.units()) for (int[] c : u) unitCells.add(ShapeKit.Shape.pos(c[0], c[1], c[2]));
        List<Draft> before = new ArrayList<>();
        int shapeUnitCells = 0;
        for (int y = 0; y < m.height(); y++) for (int z = 0; z < m.depth(); z++) for (int x = 0; x < m.width(); x++) {
            char ch = m.cell(x, y, z);
            if (ch == ShapeKit.NONE) continue;
            if (unitCells.contains(ShapeKit.Shape.pos(x, y, z))) {
                shapeUnitCells++;
                continue;
            }
            int[] p = at.world(x, y, z);
            byte kind = ch == ShapeKit.AIR ? AIR : ShapeKit.soft(ch) ? SOFT : HARD;
            int stage = m.stage(x, y, z, ch);
            if (stage <= DomeGeneratorShape.Z_LEGS || stage >= DomeGeneratorShape.Z_STAGES)
                throw new IllegalStateException(m.key() + ": no stage for '" + ch + "' at " + x + "," + y + "," + z);
            Draft dr = new Draft(f, p[0], p[1], p[2], m.part(x, y, z, ch), kind, NO_GROUP);
            if (stage == DomeGeneratorShape.Z_FIT) {
                dr.rank = m.fitRank(x, y, z, ch);
                before.add(dr);
            } else stages.get(stage)
                .add(dr);
        }
        List<Draft> units = new ArrayList<>();
        List<List<int[]>> groups = m.units();
        for (int i = 0; i < groups.size(); i++) {
            List<Draft> group = new ArrayList<>();
            for (int[] c : groups.get(i)) {
                char ch = m.cell(c[0], c[1], c[2]);
                if (!ShapeKit.checked(ch) || ch == ShapeKit.CONTROLLER)
                    throw new IllegalStateException(m.key() + ": a unit cell is not a part");
                int[] p = at.world(c[0], c[1], c[2]);
                group.add(new Draft(f, p[0], p[1], p[2], m.part(c[0], c[1], c[2], ch), HARD, i));
            }
            group.sort(BOTTOM_UP);
            units.addAll(group);
        }
        if (units.size() != shapeUnitCells) {
            throw new IllegalStateException("the units hold " + units.size() + " cells, the shape " + shapeUnitCells);
        }
        for (int s = DomeGeneratorShape.Z_LEGS; s < DomeGeneratorShape.Z_STAGES; s++) {
            if (s != DomeGeneratorShape.Z_FIT) stages.get(s)
                .sort(s == DomeGeneratorShape.Z_FLOOR ? RIPPLE : BOTTOM_UP);
        }
        before.sort(
            Comparator.<Draft>comparingInt(dr -> dr.rank)
                .thenComparing(BOTTOM_UP));
        List<Draft> fit = stages.get(DomeGeneratorShape.Z_FIT);
        fit.addAll(before);
        fit.addAll(units);

        List<List<Step>> out = empty(DomeGeneratorShape.Z_STAGES);
        for (int s = DomeGeneratorShape.Z_LEGS; s < DomeGeneratorShape.Z_STAGES; s++)
            out.set(s, steps(stages.get(s), s));
        return new Plan(moduleKey(m.key(), site), DomeGeneratorShape.Z_STAGES, out, columns(clear));
    }

    /**
     * The module job of the root's Tidal Well (0.12.0, CITY.md §4.9), sunk as a caisson, in its own stages
     * ({@link RootShape#R_SINK} ..):
     * <ul>
     * <li>SINK: layer by layer from the lid (Y0 - 1) down to the floor slab (Y0 - 26), every layer in two bands: first
     * its shell (the lid with its open centre, the wall ring, or the slab), then the cells inside the wall dug out
     * ({@link #DIG}). The lid comes first, so the forum and the nexus never stand over an open hole, and no layer is
     * dug before the wall ring of the layer above it stands: water from outside meets a wall, never the open
     * shaft.</li>
     * <li>DRAIN: every cell inside the shell once more, from the top down, taking away what fluid flowed in
     * ({@link #DRAIN}).</li>
     * <li>FLOOR: the floor level's island with its lift pad, the bridges, the pool's rim and the walkway, outward from
     * the axis.</li>
     * <li>FIT: the controller's console, the tidal rings, the helix ramp (soft), the eight tide taps (a group each, in
     * unit order), then the pool's water once its rim is whole.</li>
     * <li>COMMISSION: the controller (its module core), the last step.</li>
     * </ul>
     * There are no clear columns: the caisson digs exactly its inside, and nothing of the plan reaches the base layer
     * (Y0). Every checked cell is placed where {@link Blueprint#world} puts it for the controller
     * ({@link RootShape#controller}), so the HARD steps are exactly the cells the structure check looks at.
     *
     * @throws IllegalArgumentException if the site is not the root's or the front not a horizontal unit vector
     * @throws IllegalStateException    if a cell has no stage, or the units do not hold exactly the well's unit cells
     */
    public static Plan root(int site, int cx, int y0, int cz, int fx, int fz) {
        // the depth the base layer gives the well (0.14.1): its lid stays at Y0 - 1, its floor slab at Y0 - D
        RootShape.Well well = RootShape.forBase(y0);
        CityBuild m = well.build;
        int[] ctrl = well.controller(site, cx, y0, cz, fx, fz);
        Frame f = new Frame(cx, y0, cz, fx, fz, 1, 0);
        Local at = new Local(ctrl, m);
        Set<Long> unitCells = new HashSet<>();
        for (List<int[]> u : m.units()) for (int[] c : u) unitCells.add(ShapeKit.Shape.pos(c[0], c[1], c[2]));

        List<Draft> sink = new ArrayList<>(), drain = new ArrayList<>(), floor = new ArrayList<>(),
            before = new ArrayList<>(), after = new ArrayList<>(), commission = new ArrayList<>();
        List<Integer> sinkBands = new ArrayList<>();
        int shapeUnitCells = 0;
        for (int y = m.height() - 1; y >= 0; y--) {
            List<Draft> shell = new ArrayList<>(), dig = new ArrayList<>(), drained = new ArrayList<>();
            for (int z = 0; z < m.depth(); z++) for (int x = 0; x < m.width(); x++) {
                int[] p = at.world(x, y, z);
                if (well.inside(x, y, z)) {
                    dig.add(new Draft(f, p[0], p[1], p[2], Parts.AIR, DIG, NO_GROUP));
                    drained.add(new Draft(f, p[0], p[1], p[2], Parts.AIR, DRAIN, NO_GROUP));
                }
                char ch = m.cell(x, y, z);
                if (ch == ShapeKit.NONE) continue;
                if (unitCells.contains(ShapeKit.Shape.pos(x, y, z))) {
                    shapeUnitCells++;
                    continue;
                }
                byte kind = ch == ShapeKit.AIR ? AIR : ShapeKit.soft(ch) ? SOFT : HARD;
                Draft d = new Draft(f, p[0], p[1], p[2], kind == AIR ? Parts.AIR : m.part(x, y, z, ch), kind, NO_GROUP);
                int stage = m.stage(x, y, z, ch);
                if (stage == RootShape.R_SINK && well.shell(x, y, z)) shell.add(d);
                else if (stage == RootShape.R_FLOOR) floor.add(d);
                else if (stage == RootShape.R_FIT) {
                    d.rank = m.fitRank(x, y, z, ch);
                    if (d.rank == 0) throw new IllegalStateException(m.key() + ": FIT rank 0 for '" + ch + "'");
                    (d.rank < 0 ? before : after).add(d);
                } else if (stage == RootShape.R_COMMISSION) commission.add(d);
                else throw new IllegalStateException(
                    m.key() + ": no stage for '" + ch + "' at " + x + "," + y + "," + z);
            }
            // the layer's shell, then its inside: a band each, so the builder digs nothing before the ring stands
            shell.sort(RIPPLE);
            dig.sort(RIPPLE);
            drained.sort(RIPPLE);
            for (List<Draft> band : java.util.Arrays.asList(shell, dig)) {
                if (band.isEmpty()) continue;
                if (!sink.isEmpty()) sinkBands.add(sink.size());
                sink.addAll(band);
            }
            drain.addAll(drained);
        }
        floor.sort(RIPPLE);
        Comparator<Draft> byRank = Comparator.<Draft>comparingInt(dr -> dr.rank)
            .thenComparing(BOTTOM_UP);
        before.sort(byRank);
        after.sort(byRank);
        List<Draft> fit = new ArrayList<>(before);
        List<List<int[]>> groups = m.units();
        int unitSteps = 0;
        for (int i = 0; i < groups.size(); i++) {
            List<Draft> group = new ArrayList<>();
            for (int[] c : groups.get(i)) {
                char ch = m.cell(c[0], c[1], c[2]);
                if (!ShapeKit.checked(ch) || ch == ShapeKit.CONTROLLER)
                    throw new IllegalStateException(m.key() + ": a unit cell is not a part");
                int[] p = at.world(c[0], c[1], c[2]);
                group.add(new Draft(f, p[0], p[1], p[2], m.part(c[0], c[1], c[2], ch), HARD, i));
            }
            group.sort(BOTTOM_UP);
            fit.addAll(group);
            unitSteps += group.size();
        }
        if (unitSteps != shapeUnitCells) {
            throw new IllegalStateException("the units hold " + unitSteps + " cells, the shape " + shapeUnitCells);
        }
        fit.addAll(after);

        List<List<Step>> out = empty(RootShape.R_STAGES);
        out.set(RootShape.R_SINK, steps(sink, RootShape.R_SINK));
        out.set(RootShape.R_DRAIN, steps(drain, RootShape.R_DRAIN));
        out.set(RootShape.R_FLOOR, steps(floor, RootShape.R_FLOOR));
        out.set(RootShape.R_FIT, steps(fit, RootShape.R_FIT));
        out.set(RootShape.R_COMMISSION, steps(commission, RootShape.R_COMMISSION));
        List<List<Integer>> bands = new ArrayList<>();
        for (int i = 0; i < RootShape.R_STAGES; i++) bands.add(Collections.<Integer>emptyList());
        bands.set(RootShape.R_SINK, sinkBands);
        return new Plan(moduleKey(m.key(), site), RootShape.R_STAGES, out, Collections.<Column>emptyList(), bands);
    }

    /**
     * The seeded ornaments of a module's DECOR stage (CITY.md §1.2), the asymmetric wings that keep a city of mirrored
     * blueprints from looking stamped: SOFT steps outside the blueprint, so the module forms whether they stand or
     * not. From the seed of the module's controller:
     * <ul>
     * <li>the canopy is 2, 3 or 4 rows deep (the shape's canopy is two; its outermost row is repeated outward);</li>
     * <li>one side's apron (its outer row, {@link #ANNEX_OFFSET} out from the footprint, at Y0 + 1, from row
     * {@code annexFrom} to row depth - 1 - {@code annexFrom}: {@link #ANNEX_FROM} for a hall,
     * {@link #PAVILION_ANNEX_FROM} for a pavilion) takes an annex: a planter wall (grass beds between
     * basin rims every fourth cell), a lamp row (a post two high with a lit crown cap every third cell), a bench row
     * (stair treads with a gap every fifth cell) or a holo-sign pylon (a pedestal and two posts in the middle of the
     * side).</li>
     * </ul>
     */
    static void ornaments(List<Draft> decor, Frame f, Local at, ModuleBuild m, long seed, int annexFrom) {
        int depth = canopyDepth(seed);
        List<int[]> canopy = m.decor();
        if (!canopy.isEmpty() && depth > CANOPY_MIN) {
            int outer = Integer.MAX_VALUE;
            for (int[] c : canopy) outer = Math.min(outer, c[2]);
            for (int extra = 1; extra <= depth - CANOPY_MIN; extra++) for (int[] c : canopy) {
                if (c[2] != outer) continue;
                int[] p = at.world(c[0], c[1], outer - extra);
                decor.add(new Draft(f, p[0], p[1], p[2], c[3], SOFT, NO_GROUP));
            }
        }
        for (int[] c : annex(m.width(), m.depth(), annexFrom, seed)) {
            int[] p = at.world(c[0], c[1], c[2]);
            decor.add(new Draft(f, p[0], p[1], p[2], c[3], SOFT, NO_GROUP));
        }
    }

    /** The columns {x, z} (as {@link ShapeKit#key}) a module's base layer holds: its footprint. */
    private static Set<Long> footprint(ModuleBuild m) {
        Set<Long> out = new HashSet<>();
        for (int x = 0; x < m.width(); x++) for (int z = 0; z < m.depth(); z++) {
            if (m.cell(x, 0, z) != ShapeKit.NONE) out.add(ShapeKit.key(x, z));
        }
        return out;
    }

    /**
     * Whether the gatehouse's column (x, z) holds a cell at its passage headroom ({@link CampusPlan#GATE_HEADROOM}) or
     * above: the bridge over the open passage, whose volume alone the gatehouse's job clears there.
     */
    private static boolean overPassage(ModuleBuild m, int x, int z, int h) {
        if (x < 0 || x >= m.width() || z < 0 || z >= m.depth()) return false;
        for (int y = CampusPlan.GATE_HEADROOM; y < h; y++) if (m.cell(x, y, z) != ShapeKit.NONE) return true;
        return false;
    }

    /** Whether a column lies within {@code margin} (either way, diagonals too) of a column of the set. */
    private static boolean near(Set<Long> cells, int x, int z, int margin) {
        for (int dx = -margin; dx <= margin; dx++) for (int dz = -margin; dz <= margin; dz++) {
            if (cells.contains(ShapeKit.key(x + dx, z + dz))) return true;
        }
        return false;
    }

    /** The canopy's depth in rows for a module seed: 2, 3 or 4. */
    public static int canopyDepth(long seed) {
        return CANOPY_MIN + Math.min(CANOPY_VARIANTS - 1, (int) (Mix.unit(seed, S_CANOPY) * CANOPY_VARIANTS));
    }

    /** The annex's kind for a module seed: {@link #ANNEX_PLANTERS}, lamps, benches or the pylon. */
    public static int annexKind(long seed) {
        return Math.min(ANNEX_KINDS - 1, (int) (Mix.unit(seed, S_ANNEX_KIND) * ANNEX_KINDS));
    }

    /** The annex's cells in the module's local frame, {x, y, z, part}, for a hall {@code width x depth}. */
    public static List<int[]> annex(int width, int depth, long seed) {
        return annex(width, depth, ANNEX_FROM, seed);
    }

    /**
     * The annex's cells in the module's local frame, {x, y, z, part}, for a module {@code width x depth} whose annex
     * leaves {@code from} rows free at each end: a hall {@link #ANNEX_FROM}, a pavilion
     * {@link #PAVILION_ANNEX_FROM} (0.13.0).
     */
    public static List<int[]> annex(int width, int depth, int from, long seed) {
        int x = Mix.unit(seed, S_ANNEX_SIDE) < 0.5 ? -ANNEX_OFFSET : width - 1 + ANNEX_OFFSET;
        int kind = annexKind(seed);
        int z0 = from, z1 = depth - 1 - from;
        List<int[]> cells = new ArrayList<>();
        for (int z = z0; z <= z1; z++) {
            int i = z - z0;
            switch (kind) {
                case ANNEX_PLANTERS:
                    cells.add(new int[] { x, 1, z, i % 4 == 0 ? Parts.fitting(Parts.F_BASIN) : Parts.GRASS });
                    break;
                case ANNEX_LAMPS:
                    if (i % 3 == 0) {
                        cells.add(new int[] { x, 1, z, Parts.fitting(Parts.F_POST) });
                        cells.add(new int[] { x, 2, z, Parts.fitting(Parts.F_POST) });
                        cells.add(new int[] { x, 3, z, Parts.fitting(Parts.F_CROWN) });
                    }
                    break;
                case ANNEX_BENCHES:
                    if (i % 5 != 4) cells.add(new int[] { x, 1, z, Parts.fitting(Parts.F_TREAD) });
                    break;
                default:
                    if (z == (z0 + z1) / 2) {
                        cells.add(new int[] { x, 1, z, Parts.fitting(Parts.F_PEDESTAL) });
                        cells.add(new int[] { x, 2, z, Parts.fitting(Parts.F_POST) });
                        cells.add(new int[] { x, 3, z, Parts.fitting(Parts.F_POST) });
                    }
            }
        }
        return cells;
    }

    /** The Echo Archive as the generic module plan reads it: its 0.10 stages, exactly. */
    public static final ModuleBuild ARCHIVE = new ModuleBuild() {

        private final List<List<int[]>> units = archiveUnits();

        @Override
        public String key() {
            return LIBRARY;
        }

        @Override
        public int width() {
            return ArchiveShape.WIDTH;
        }

        @Override
        public int depth() {
            return ArchiveShape.DEPTH;
        }

        @Override
        public int height() {
            return ArchiveShape.HEIGHT;
        }

        @Override
        public Blueprint blueprint() {
            return ArchiveShape.BLUEPRINT;
        }

        @Override
        public int[] cellOf(int x, int y, int z) {
            return ArchiveShape.cellOf(x, y, z);
        }

        @Override
        public int[] controller() {
            return new int[] { ArchiveShape.CTRL_X, ArchiveShape.CTRL_Y, ArchiveShape.CTRL_Z };
        }

        @Override
        public char cell(int x, int y, int z) {
            return ArchiveShape.cell(x, y, z);
        }

        @Override
        public int part(int x, int y, int z, char ch) {
            if (ch == ArchiveShape.SOFT) {
                boolean spine = x == ArchiveShape.MID && z >= SPINE_FROM && z <= SPINE_TO && z % 2 == 0;
                return spine ? LIT : DECK;
            }
            return ArchiveShape.partOf(ch);
        }

        @Override
        public int stage(int x, int y, int z, char ch) {
            if (ch == ArchiveShape.SOFT) return M_FLOOR;
            if (ch == ArchiveShape.CONTROLLER) return M_COMMISSION;
            if (ch == ArchiveShape.AIR) return M_SHELL;
            if (y == 0) {
                if (ch != ArchiveShape.FOUNDATION) throw new IllegalStateException("'" + ch + "' in the floor layer");
                // the foundations open the shell: being the lowest layer, the bottom-up order puts them first
                return M_SHELL;
            }
            boolean wall = x == 0 || x == ArchiveShape.WIDTH - 1 || z == 0 || z == ArchiveShape.DEPTH - 1;
            if (ArchiveShape.isStair(x, y, z) || ch == ArchiveShape.POST && stairCore(x, z)) return M_DECK;
            if (y >= ArchiveShape.ROOF_Y) return M_ROOF;
            if (y == ArchiveShape.DECK_Y || ch == ArchiveShape.RAIL) return M_DECK;
            if (wall) return y < ArchiveShape.DECK_Y ? M_SHELL : M_UPPER;
            if (ch == ArchiveShape.POST || ch == ArchiveShape.PEDESTAL || ch == ArchiveShape.CONSOLE) return M_FIT;
            throw new IllegalStateException("no stage for '" + ch + "' at " + x + "," + y + "," + z);
        }

        @Override
        public int fitRank(int x, int y, int z, char ch) {
            // the posts before the units; after them the pedestals, the lecterns and the desk
            if (ch == ArchiveShape.POST) return -1;
            if (ch == ArchiveShape.PEDESTAL) return 1;
            boolean desk = x == ArchiveShape.DESK[0] && y == ArchiveShape.DESK[1] && z == ArchiveShape.DESK[2];
            return desk ? 3 : 2;
        }

        @Override
        public List<List<int[]>> units() {
            return units;
        }

        @Override
        public List<int[]> decor() {
            return ArchiveShape.decor();
        }

        @Override
        public boolean ornaments() {
            return false;
        }
    };

    /** The Archive's bookcases as unit cell lists: plinths, bodies and crowns, in {@link LibraryUnits#UNITS} order. */
    private static List<List<int[]>> archiveUnits() {
        List<List<int[]>> out = new ArrayList<>();
        for (LibraryUnits.Unit u : LibraryUnits.UNITS) {
            List<int[]> cells = new ArrayList<>();
            for (int y = u.yBody - 1; y <= u.yBody + 3; y++)
                for (int k = 0; k < 3; k++) cells.add(new int[] { u.x(k), y, u.z(k) });
            out.add(Collections.unmodifiableList(cells));
        }
        return Collections.unmodifiableList(out);
    }

    private static boolean stairCore(int x, int z) {
        for (int[] s : ArchiveShape.STAIRS) if (s[0] == x && s[1] == z) return true;
        return false;
    }

    private static boolean hallSite(int site) {
        for (int k : CampusPlan.HALL_SITES) if (k == site) return true;
        return false;
    }

    // ---- shared helpers ----

    /**
     * A paved campus cell: its deck at Y0. A light well's deck WELL lies flush in the Y0 layer like every other tile
     * (0.10.1; 0.10.0 sank it one cell with air above, which players took for pits).
     */
    private static void paving(List<Draft> out, Frame f, int a, int r, int code, boolean well) {
        int[] w = f.world(a, r);
        out.add(new Draft(f, w[0], f.y0, w[1], well ? WELL : code, SOFT, NO_GROUP));
    }

    private static List<List<Step>> empty(int stages) {
        List<List<Step>> out = new ArrayList<>();
        for (int i = 0; i < stages; i++) out.add(new ArrayList<>());
        return out;
    }

    private static List<Step> steps(List<Draft> drafts, int stage) {
        List<Step> out = new ArrayList<>(drafts.size());
        for (Draft d : drafts) out.add(new Step(d.x, d.y, d.z, d.part, stage, d.group, d.kind));
        return out;
    }

    private static List<Column> columns(List<Clearing> clear) {
        List<Column> out = new ArrayList<>(clear.size());
        for (Clearing c : clear) out.add(new Column(c.x, c.z, c.y, c.yTo, c.grade));
        return out;
    }

    /**
     * World boxes a campus job leaves alone: the footprints of modules that already stand, as whole columns
     * {x0, z0, x1, z1}, and (0.12.0) height-ranged boxes {x0, z0, x1, z1, yFrom, yTo}, the zenith's legs.
     */
    private static final class KeepOut {

        /** {x0, z0, x1, z1, yFrom, yTo} per box, with x0 <= x1, z0 <= z1 and yFrom <= yTo; whole columns for four. */
        private final List<int[]> boxes = new ArrayList<>();

        KeepOut(List<int[]> keepOut) {
            if (keepOut == null) return;
            for (int[] b : keepOut) {
                if (b == null || b.length != 4 && b.length != 6) {
                    throw new IllegalArgumentException(
                        "a keep-out box is {x0, z0, x1, z1} or {x0, z0, x1, z1, yFrom, yTo}");
                }
                int lo = b.length == 6 ? Math.min(b[4], b[5]) : Integer.MIN_VALUE;
                int hi = b.length == 6 ? Math.max(b[4], b[5]) : Integer.MAX_VALUE;
                boxes.add(
                    new int[] { Math.min(b[0], b[2]), Math.min(b[1], b[3]), Math.max(b[0], b[2]), Math.max(b[1], b[3]),
                        lo, hi });
            }
        }

        /** Whether a box holds a cell of the world column (x, z) between the heights {@code y0} and {@code y1}. */
        boolean holds(int x, int z, int y0, int y1) {
            for (int[] b : boxes)
                if (x >= b[0] && x <= b[2] && z >= b[1] && z <= b[3] && y0 <= b[5] && b[4] <= y1) return true;
            return false;
        }
    }

    /** The nexus centre and front, and the axis a job orders its steps along (the gate, or a hall site's axis). */
    static final class Frame {

        final int cx, y0, cz, fx, fz, da, dr;

        Frame(int cx, int y0, int cz, int fx, int fz, int da, int dr) {
            this.cx = cx;
            this.y0 = y0;
            this.cz = cz;
            this.fx = fx;
            this.fz = fz;
            this.da = da;
            this.dr = dr;
        }

        /** The world {x, z} of the campus cell (a, r). */
        int[] world(int a, int r) {
            int[] d = CampusPlan.toWorld(a, r, fx, fz);
            return new int[] { cx + d[0], cz + d[1] };
        }
    }

    /** A module's local frame placed in the world: its controller cell and front ({@link #hallController}). */
    static final class Local {

        final int x, y, z, fx, fz;
        final ModuleBuild m;

        Local(int[] ctrl, ModuleBuild m) {
            x = ctrl[0];
            y = ctrl[1];
            z = ctrl[2];
            int[] f = front(ctrl[3]);
            fx = f[0];
            fz = f[1];
            this.m = m;
        }

        /** The world {x, y, z} of the module's local cell, the way the structure check maps it. */
        int[] world(int lx, int ly, int lz) {
            int[] c = m.cellOf(lx, ly, lz);
            return m.blueprint()
                .world(c[0], c[1], c[2], x, y, z, fx, fz);
        }
    }

    /**
     * A world cell seen from the nexus: its campus cell (a, r), the same cell along ({@code t}) and across
     * ({@code s}) the job's axis, and its squared distance from the centre.
     */
    static class Place {

        final int x, y, z, t, s;
        final long d2;

        Place(Frame f, int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
            int[] l = CampusPlan.toLocal(x - f.cx, z - f.cz, f.fx, f.fz);
            int a = l[0], r = l[1];
            t = a * f.da + r * f.dr;
            s = -a * f.dr + r * f.da;
            d2 = (long) a * a + (long) r * r;
        }

        /** The angle clockwise from the job's axis, in [0, 2 pi). */
        double angle() {
            double v = StrictMath.atan2(s, t);
            return v < 0 ? v + 2 * Math.PI : v;
        }
    }

    /** A step before it has its stage. */
    static final class Draft extends Place {

        final int part, group;
        final byte kind;
        /**
         * Its rank within the FIT stage ({@link ModuleBuild#fitRank}), or its order within a stage built bottom-up
         * ({@link ModuleBuild#order}, 0.13.0).
         */
        int rank;

        Draft(Frame f, int x, int y, int z, int part, byte kind, int group) {
            super(f, x, y, z);
            this.part = part;
            this.kind = kind;
            this.group = group;
        }
    }

    /** A column before it is a {@link Column}: {@code y} is its lowest cell. */
    private static final class Clearing extends Place {

        final int yTo;
        final boolean grade;

        Clearing(Frame f, int x, int yFrom, int z, int yTo, boolean grade) {
            super(f, x, yFrom, z);
            this.yTo = yTo;
            this.grade = grade;
        }
    }

    /**
     * Outward from the nexus, a cell and its mirror image across the axis side by side, the lower cell first where
     * two share a column. Total over distinct cells, so the order never depends on how the cells were collected.
     */
    private static final Comparator<Place> RIPPLE = (p, q) -> {
        int c = Long.compare(p.d2, q.d2);
        if (c == 0) c = Integer.compare(p.t, q.t);
        if (c == 0) c = Integer.compare(Math.abs(p.s), Math.abs(q.s));
        if (c == 0) c = Integer.compare(p.s, q.s);
        if (c == 0) c = Integer.compare(p.y, q.y);
        return c;
    };

    /** Bottom-up by layer, then as {@link #RIPPLE}. */
    private static final Comparator<Place> BOTTOM_UP = (p, q) -> {
        int c = Integer.compare(p.y, q.y);
        return c != 0 ? c : RIPPLE.compare(p, q);
    };
}
