package com.fluxecho.logic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
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
 * server builds and a job that is saved with those inputs can be recomputed after a restart. Three jobs have plans:
 * <ul>
 * <li>{@link #establish}: grade the campus disc, raise the phase-I nexus round the player's core, pave the forum, the
 * gate and the promenade ring, then stand the gate pylons and the supply port;</li>
 * <li>{@link #forum}: the same without the nexus, for a 0.9.2 nexus that already stands, leaving the halls docked
 * round it alone;</li>
 * <li>{@link #archive}: an Echo Archive ({@link ArchiveShape}) on a hall site, its forecourt and apron, its interior
 * floor, its shell from the bottom up, its fittings and shelf units, the canopy, and the controller last.</li>
 * </ul>
 * Steps come in stage order. Within a stage they rise bottom-up by layer and spread outward from the nexus, a cell
 * and its mirror image across the job's axis side by side, so the build reads as an ordered band. Each shelf unit of
 * the Archive is one group: its fifteen cells share a group id (the unit's index in {@link LibraryUnits#UNITS}), are
 * contiguous, and launch and land together. Every other step has {@link #NO_GROUP}.
 * <p>
 * Kinds: a {@link #HARD} step is a cell the structure check looks at (the nexus or the Archive blueprint); a
 * {@link #SOFT} step is a cell it does not (paving, light wells, gate pylons, the supply port, the canopy); an
 * {@link #AIR} step is a cell that must become air (door openings and the top of a light well). Clear columns overlap
 * step cells on purpose (an Archive's box holds the whole building): the builder decides a step's own cell with
 * {@link TerrainRule#forBuild} and only clears the other cells of a column.
 */
public final class BuildPlan {

    /** A checked structure cell, an unchecked cell (paving, fixtures, decoration), a cell that must become air. */
    public static final byte HARD = 0, SOFT = 1, AIR = 2;
    /** The group of a step that launches on its own. */
    public static final int NO_GROUP = -1;

    /** The stages of the establish and forum jobs; FORM has no steps (the builder forms the nexus there). */
    public static final int E_CLEAR = 0, E_CORE = 1, E_FORM = 2, E_FLOOR = 3, E_FIXTURE = 4, E_STAGES = 5;
    /** The stages of a module job. */
    public static final int M_CLEAR = 0, M_FLOOR = 1, M_SHELL = 2, M_DECK = 3, M_UPPER = 4, M_ROOF = 5, M_FIT = 6,
        M_DECOR = 7, M_COMMISSION = 8, M_STAGES = 9;

    /** The job keys of the plans. */
    public static final String ESTABLISH = "establish", FORUM = "forum", LIBRARY = "library";

    /** Grading and dais columns clear from Y0 + 1 up to Y0 + this (the builder clips it to the config). */
    public static final int CLEAR_UP = 24;
    /**
     * The Archive's clear box: its footprint and {@code ARCHIVE_MARGIN} cells round it, from Y0 + 1 up to Y0 +
     * {@link ArchiveShape#HEIGHT} + {@code ARCHIVE_HEADROOM}. The ridge (the top layer) is at Y0 + HEIGHT - 1, so the
     * box reaches three cells above it.
     */
    public static final int ARCHIVE_MARGIN = 1, ARCHIVE_HEADROOM = 2;
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
        /** {@link #HARD}, {@link #SOFT} or {@link #AIR}. */
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
     * A column to clear before the job places anything: natural blocks from {@code yTo} down to {@code yFrom}. A
     * grading column ({@code grade}) also fills the ground under the paving level.
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

    /** A job's whole build: its steps in order (stage by stage), its clear columns, its stage count and its key. */
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

        Plan(String key, int stages, List<List<Step>> byStage, List<Column> clear) {
            this.key = key;
            this.stages = stages;
            List<Step> all = new ArrayList<>();
            starts = new int[stages + 1];
            for (int s = 0; s < stages; s++) {
                starts[s] = all.size();
                all.addAll(byStage.get(s));
            }
            starts[stages] = all.size();
            steps = Collections.unmodifiableList(all);
            this.clear = Collections.unmodifiableList(new ArrayList<>(clear));
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
     * <li>FLOOR: {@link CampusPlan#establishFloor()} at Y0 outward from the centre; a light well is air at Y0 over a
     * deck WELL at Y0 - 1.</li>
     * <li>FIXTURE: the four gate pylons (two frame pillars and a conduit each, Y0 + 1 to Y0 + 3), then the supply
     * port at Y0 + 1.</li>
     * </ul>
     * The grading columns, the floor cells, the pylons and the supply port that fall inside a {@code keepOut} box are
     * left out, so the job never touches a module that already stands round the nexus (a 0.9.2 hall docked on the
     * inner ring: slot 0 lies on the gate, and every slot lies in the graded disc). The dais columns and the nexus
     * itself are never left out; the core placement has to refuse a spot whose dais overlaps a module.
     *
     * @param cx      the nexus centre (the middle of its base), with {@code y0} the base layer
     * @param fx      the nexus front, a horizontal unit vector
     * @param nexus   the nexus blueprint, normally {@link NexusShape#PHASE_1}
     * @param ctrlX   the nexus controller (the core the player placed)
     * @param keepOut world boxes {x0, z0, x1, z1} (inclusive, any corner order) whose columns the campus work leaves
     *                alone, normally one {@link #moduleKeepOut} per module standing near the nexus when the job was
     *                made; the job must keep the list so that a recomputed plan (and the client's) is the same
     * @throws IllegalArgumentException if the front is not a horizontal unit vector or a box is not four numbers
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
     * @param keepOut world boxes {x0, z0, x1, z1} (inclusive, any corner order) the job leaves alone; the job must
     *                keep the list so that a recomputed plan (and the client's) is the same
     * @throws IllegalArgumentException if the front is not a horizontal unit vector or a box is not four numbers
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
     * The stages the establish and forum jobs share: grading columns, the floor and the fixtures, less every column
     * the keep-out boxes hold.
     */
    private static List<List<Step>> campus(CampusPlan plan, Frame f, List<Clearing> clear, KeepOut out) {
        for (long k : plan.gradeArea()) {
            int[] w = f.world(CampusPlan.keyA(k), CampusPlan.keyR(k));
            if (out.holds(w[0], w[1])) continue;
            clear.add(new Clearing(f, w[0], f.y0 + 1, w[1], f.y0 + CLEAR_UP, true));
        }
        clear.sort(RIPPLE);

        List<Draft> floor = new ArrayList<>();
        Set<Long> wells = plan.wells();
        for (Map.Entry<Long, Integer> e : plan.establishFloor()
            .entrySet()) {
            long k = e.getKey();
            int[] w = f.world(CampusPlan.keyA(k), CampusPlan.keyR(k));
            if (out.holds(w[0], w[1])) continue;
            paving(floor, f, CampusPlan.keyA(k), CampusPlan.keyR(k), e.getValue(), wells.contains(k));
        }
        floor.sort(RIPPLE);

        List<Draft> pylons = new ArrayList<>();
        for (int[] p : plan.pylons()) {
            int[] w = f.world(p[0], p[1]);
            if (out.holds(w[0], w[1])) continue;
            for (int i = 1; i <= PYLON_PILLARS + 1; i++) {
                int part = Parts.frame(i <= PYLON_PILLARS ? Parts.FR_PILLAR : Parts.FR_CONDUIT);
                pylons.add(new Draft(f, w[0], f.y0 + i, w[1], part, SOFT, NO_GROUP));
            }
        }
        pylons.sort(BOTTOM_UP);
        int[] port = plan.supplyPort();
        int[] pw = f.world(port[0], port[1]);
        if (!out.holds(pw[0], pw[1])) {
            pylons.add(new Draft(f, pw[0], f.y0 + 1, pw[1], Parts.SUPPLY_PORT, SOFT, NO_GROUP));
        }

        List<List<Step>> stages = empty(E_STAGES);
        stages.set(E_FLOOR, steps(floor, E_FLOOR));
        stages.set(E_FIXTURE, steps(pylons, E_FIXTURE));
        return stages;
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
            default:
                throw new IllegalArgumentException("not a nexus character: '" + ch + "'");
        }
    }

    /** The octagon distance scaled by {@link #DAIS_K}: the dais {@code oct(5, 7)} is everything up to 35. */
    private static int octagon(int a, int r) {
        int aa = Math.abs(a), ar = Math.abs(r);
        return Math.max(DAIS_K * Math.max(aa, ar), DAIS_R * (aa + ar));
    }

    // ---- the Archive ----

    /**
     * Where the controller of an Archive on a hall site stands, and which way it faces: {x, y, z, facing ordinal}.
     * The Archive's front row lies at radial {@link CampusPlan#HALL_FRONT} on the site axis with the controller on
     * the axis at Y0 + {@link ArchiveShape#CTRL_Y}, and its front faces the nexus (the facing is the site axis
     * reversed, as a ForgeDirection ordinal from 2 to 5), so {@link Blueprint#world} puts the Archive's centre on
     * {@link CampusPlan#moduleCentre}.
     *
     * @throws IllegalArgumentException if the site is not a hall site or the front not a horizontal unit vector
     */
    public static int[] archiveController(int site, int cx, int y0, int cz, int fx, int fz) {
        facingOrdinal(fx, fz);
        if (!hallSite(site)) throw new IllegalArgumentException("not a hall site: " + site);
        int[] ax = CampusPlan.siteAxis(site);
        int[] out = CampusPlan.toWorld(ax[0], ax[1], fx, fz);
        int t = CampusPlan.HALL_FRONT + ArchiveShape.CTRL_Z;
        return new int[] { cx + out[0] * t, y0 + ArchiveShape.CTRL_Y, cz + out[1] * t,
            facingOrdinal(-out[0], -out[1]) };
    }

    /**
     * The module job of an Echo Archive on a hall site.
     * <ul>
     * <li>CLEAR: the footprint and {@link #ARCHIVE_MARGIN} round it, from Y0 + 1 to Y0 + {@link ArchiveShape#HEIGHT}
     * + {@link #ARCHIVE_HEADROOM}, outward from the nexus.</li>
     * <li>FLOOR: {@link CampusPlan#siteFloor} and the interior soft floor (deck, a dashed lit spine on the axis from
     * z1 to z28), outward from the nexus.</li>
     * <li>SHELL: the outer walls y1..y5 (panels, pilasters, glazing, the plinth course, the trumeau and the lintel)
     * and every must-be-air door cell.</li>
     * <li>DECK: everything at y6 (gallery slab, lit edge, landings, the walls' y6 band), the rails, and both spiral
     * stairs with their cores.</li>
     * <li>UPPER: the outer walls y7..y11.</li>
     * <li>ROOF: everything from y12 to y15.</li>
     * <li>FIT: the unit posts; then every shelf unit as one group of fifteen (three plinths, nine bodies, three
     * crowns), in {@link LibraryUnits#UNITS} order; then the pedestals, the lecterns, the desk and the
     * foundations.</li>
     * <li>DECOR: the entrance canopy ({@link ArchiveShape#decor()}).</li>
     * <li>COMMISSION: the controller (part {@link Parts#LIBRARY_CORE}), the last step.</li>
     * </ul>
     * Every blueprint cell is placed where {@link Blueprint#world} puts it for {@link #archiveController}, so the
     * HARD steps are exactly the cells the structure check looks at.
     *
     * @throws IllegalArgumentException if the site is not a hall site or the front not a horizontal unit vector
     */
    public static Plan archive(CampusPlan plan, int site, int cx, int y0, int cz, int fx, int fz) {
        int[] ctrl = archiveController(site, cx, y0, cz, fx, fz);
        int[] ax = CampusPlan.siteAxis(site);
        Frame f = new Frame(cx, y0, cz, fx, fz, ax[0], ax[1]);
        Local at = new Local(ctrl);
        final int w = ArchiveShape.WIDTH, d = ArchiveShape.DEPTH, h = ArchiveShape.HEIGHT;

        List<Clearing> clear = new ArrayList<>();
        for (int z = -ARCHIVE_MARGIN; z < d + ARCHIVE_MARGIN; z++) {
            for (int x = -ARCHIVE_MARGIN; x < w + ARCHIVE_MARGIN; x++) {
                int[] p = at.world(x, 0, z);
                clear.add(new Clearing(f, p[0], y0 + 1, p[2], y0 + h + ARCHIVE_HEADROOM, false));
            }
        }
        clear.sort(RIPPLE);

        List<Draft> floor = new ArrayList<>(), shell = new ArrayList<>(), deck = new ArrayList<>(),
            upper = new ArrayList<>(), roof = new ArrayList<>(), posts = new ArrayList<>(),
            pedestals = new ArrayList<>(), lecterns = new ArrayList<>(), desk = new ArrayList<>(),
            foundations = new ArrayList<>(), decor = new ArrayList<>(), commission = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : plan.siteFloor(site, w, d)
            .entrySet()) {
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
        int unitCells = 0;
        for (int y = 0; y < h; y++) for (int z = 0; z < d; z++) for (int x = 0; x < w; x++) {
            char ch = ArchiveShape.cell(x, y, z);
            if (ch == ArchiveShape.ANY) continue;
            int[] p = at.world(x, y, z);
            if (ch == ArchiveShape.SOFT) {
                boolean spine = x == ArchiveShape.MID && z >= SPINE_FROM && z <= SPINE_TO && z % 2 == 0;
                floor.add(new Draft(f, p[0], p[1], p[2], spine ? LIT : DECK, SOFT, NO_GROUP));
                continue;
            }
            if (ch == ArchiveShape.PLINTH || ch == ArchiveShape.BODY || ch == ArchiveShape.CROWN) {
                // the units are added whole below, in their reading order
                unitCells++;
                continue;
            }
            byte kind = ch == ArchiveShape.AIR ? AIR : HARD;
            Draft dr = new Draft(f, p[0], p[1], p[2], ArchiveShape.partOf(ch), kind, NO_GROUP);
            boolean wall = x == 0 || x == w - 1 || z == 0 || z == d - 1;
            if (ch == ArchiveShape.CONTROLLER) commission.add(dr);
            else if (kind == AIR) shell.add(dr);
            else if (y == 0) {
                if (ch != ArchiveShape.FOUNDATION) throw new IllegalStateException("'" + ch + "' in the floor layer");
                foundations.add(dr);
            } else if (ArchiveShape.isStair(x, y, z) || ch == ArchiveShape.POST && stairCore(x, z)) deck.add(dr);
            else if (y >= ArchiveShape.ROOF_Y) roof.add(dr);
            else if (y == ArchiveShape.DECK_Y || ch == ArchiveShape.RAIL) deck.add(dr);
            else if (wall) (y < ArchiveShape.DECK_Y ? shell : upper).add(dr);
            else if (ch == ArchiveShape.POST) posts.add(dr);
            else if (ch == ArchiveShape.PEDESTAL) pedestals.add(dr);
            else if (ch == ArchiveShape.CONSOLE) {
                boolean isDesk = x == ArchiveShape.DESK[0] && y == ArchiveShape.DESK[1] && z == ArchiveShape.DESK[2];
                (isDesk ? desk : lecterns).add(dr);
            } else {
                throw new IllegalStateException("no stage for '" + ch + "' at " + x + "," + y + "," + z);
            }
        }
        for (int[] c : ArchiveShape.decor()) {
            int[] p = at.world(c[0], c[1], c[2]);
            decor.add(new Draft(f, p[0], p[1], p[2], c[3], SOFT, NO_GROUP));
        }

        // every unit as one contiguous group of fifteen: plinths, bodies and crowns from the bottom up
        List<Draft> units = new ArrayList<>();
        for (LibraryUnits.Unit u : LibraryUnits.UNITS) {
            List<Draft> group = new ArrayList<>();
            for (int y = u.yBody - 1; y <= u.yBody + 3; y++) for (int k = 0; k < 3; k++) {
                int[] p = at.world(u.x(k), y, u.z(k));
                char ch = ArchiveShape.cell(u.x(k), y, u.z(k));
                group.add(new Draft(f, p[0], p[1], p[2], ArchiveShape.partOf(ch), HARD, u.index));
            }
            group.sort(BOTTOM_UP);
            units.addAll(group);
        }
        if (units.size() != unitCells) {
            throw new IllegalStateException("the units hold " + units.size() + " cells, the shape " + unitCells);
        }

        for (List<Draft> l : Arrays
            .asList(shell, deck, upper, roof, posts, pedestals, lecterns, desk, foundations, decor)) {
            l.sort(BOTTOM_UP);
        }
        floor.sort(RIPPLE);
        List<Draft> fit = new ArrayList<>(posts);
        fit.addAll(units);
        fit.addAll(pedestals);
        fit.addAll(lecterns);
        fit.addAll(desk);
        fit.addAll(foundations);

        List<List<Step>> stages = empty(M_STAGES);
        stages.set(M_FLOOR, steps(floor, M_FLOOR));
        stages.set(M_SHELL, steps(shell, M_SHELL));
        stages.set(M_DECK, steps(deck, M_DECK));
        stages.set(M_UPPER, steps(upper, M_UPPER));
        stages.set(M_ROOF, steps(roof, M_ROOF));
        stages.set(M_FIT, steps(fit, M_FIT));
        stages.set(M_DECOR, steps(decor, M_DECOR));
        stages.set(M_COMMISSION, steps(commission, M_COMMISSION));
        return new Plan(moduleKey(LIBRARY, site), M_STAGES, stages, columns(clear));
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

    /** A paved campus cell: a deck at Y0, or for a light well air at Y0 over a deck WELL at Y0 - 1. */
    private static void paving(List<Draft> out, Frame f, int a, int r, int code, boolean well) {
        int[] w = f.world(a, r);
        if (well || code == WELL) {
            out.add(new Draft(f, w[0], f.y0 - 1, w[1], WELL, SOFT, NO_GROUP));
            out.add(new Draft(f, w[0], f.y0, w[1], Parts.AIR, AIR, NO_GROUP));
        } else {
            out.add(new Draft(f, w[0], f.y0, w[1], code, SOFT, NO_GROUP));
        }
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

    /** World boxes whose columns a campus job leaves alone (the footprints of modules that already stand). */
    private static final class KeepOut {

        /** {x0, z0, x1, z1} per box, with x0 <= x1 and z0 <= z1. */
        private final List<int[]> boxes = new ArrayList<>();

        KeepOut(List<int[]> keepOut) {
            if (keepOut == null) return;
            for (int[] b : keepOut) {
                if (b == null || b.length != 4) {
                    throw new IllegalArgumentException("a keep-out box is {x0, z0, x1, z1}");
                }
                boxes.add(
                    new int[] { Math.min(b[0], b[2]), Math.min(b[1], b[3]), Math.max(b[0], b[2]),
                        Math.max(b[1], b[3]) });
            }
        }

        /** Whether the world column (x, z) lies in a box. */
        boolean holds(int x, int z) {
            for (int[] b : boxes) if (x >= b[0] && x <= b[2] && z >= b[1] && z <= b[3]) return true;
            return false;
        }
    }

    /** The nexus centre and front, and the axis a job orders its steps along (the gate, or a hall site's axis). */
    private static final class Frame {

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

    /** The Archive's local frame placed in the world: its controller cell and front from {@link #archiveController}. */
    private static final class Local {

        final int x, y, z, fx, fz;

        Local(int[] ctrl) {
            x = ctrl[0];
            y = ctrl[1];
            z = ctrl[2];
            int[] f = front(ctrl[3]);
            fx = f[0];
            fz = f[1];
        }

        /** The world {x, y, z} of the Archive's local cell, the way the structure check maps it. */
        int[] world(int lx, int ly, int lz) {
            int[] c = ArchiveShape.cellOf(lx, ly, lz);
            return ArchiveShape.BLUEPRINT.world(c[0], c[1], c[2], x, y, z, fx, fz);
        }
    }

    /**
     * A world cell seen from the nexus: its campus cell (a, r), the same cell along ({@code t}) and across
     * ({@code s}) the job's axis, and its squared distance from the centre.
     */
    private static class Place {

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
    private static final class Draft extends Place {

        final int part, group;
        final byte kind;

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
