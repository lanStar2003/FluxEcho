package com.fluxecho.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Where the Tidal Well's drawing puts its light (0.12.0, CITY.md §4.9 Visuals), worked out from its shape
 * ({@link RootShape}) in the well's own frame (x across, y up from the floor slab at Y0 - 26, z from the front):
 * <ul>
 * <li>The three tidal rings: once it forms, the ring segments give way to drawn rings that rise and fall with the tide
 * ({@link Tide}, the well eating it twice over) and turn, the upper ones a little behind the lower ({@link #ringRise},
 * {@link #ringTurn}); never low enough to touch a head on the island or the bridges, never into the ramp.</li>
 * <li>The ribs: a line of light up the inner face of each of the eight lit ribs ({@link #ribs}), broken where the
 * flux-layer bands cross them ({@link #ribRuns}), a pulse climbing them.</li>
 * <li>The bands: the three flux-layer bands (表层, 记忆层, 深层) as lines along the inner faces of their dark courses
 * ({@link #bandFaces}).</li>
 * <li>The pool: its water cells ({@link ModuleLook#water}), for the caustics on its surface.</li>
 * <li>From the surface: the forum's grate band round the dais over the well ({@link #grate}), glowing with the tide,
 * and
 * at the full tide the rings' crests showing through it ({@link #crest}).</li>
 * </ul>
 * Pure, so the tests hold the rings clear of the people and the lines on the walls.
 * <p>
 * Over low ground the well is shallower (0.14.1, {@link RootShape#depth}): the forms that take a {@link RootShape.Well}
 * read its lid, its bands and its cells; those without one are the full well's.
 */
public final class RootLook {

    /** How far the rings rise and fall at the top of a spring tide (blocks). */
    public static final double RISE = 1.25;
    /** How far behind the ring under it each ring follows the tide (ticks of day time). */
    public static final int LAG = 400;
    /** How fast the rings turn (radians a tick), and how far the tide sways them on top of that (radians). */
    public static final double SPIN = 0.004, SWAY = 0.8;
    /** A standing player's head over the floor they stand on, and the room kept over it. */
    public static final double HEAD = 1.8, CLEAR = 0.5;
    /** From how high a tide the rings' crests show through the grate band, and how high they rise at most. */
    public static final double CREST_FROM = 0.75, CREST_HIGH = 0.9;
    /** The forum layer over the lid (Y0) in the full well's frame. */
    public static final int SURFACE_Y = RootShape.HEIGHT;

    private static final int[][] SIDES = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
    /** The eight lines the lit ribs stand on, from the front clockwise seen from above. */
    private static final int[][] LINES = { { 0, -1 }, { 1, -1 }, { 1, 0 }, { 1, 1 }, { 0, 1 }, { -1, 1 }, { -1, 0 },
        { -1, -1 } };

    private RootLook() {}

    /** The tide the well sees at a day time: {@link Tide#norm}, -1 to 1. */
    public static double tide(double dayTime) {
        return Tide.norm(dayTime);
    }

    /**
     * How far ring {@code ring} (0 the lowest) stands over or under its place at a day time: {@link #RISE} each way at
     * a
     * spring tide (the well swings twice as far as the tide, so a neap tide still moves it a quarter as much), a ring
     * {@link #LAG} ticks behind the one under it.
     */
    public static double ringRise(int ring, double dayTime) {
        return RISE * Tide.norm(dayTime - LAG * ring);
    }

    /**
     * How far ring {@code ring} has turned (radians) at the world's total time {@code t} and day time
     * {@code dayTime}: a slow spin, the middle ring the other way, swaying with the tide.
     */
    public static double ringTurn(int ring, double t, double dayTime) {
        double dir = ring % 2 == 0 ? 1 : -1;
        return dir * (SPIN * t + SWAY * Tide.norm(dayTime - LAG * ring));
    }

    /** The lowest a ring's underside comes (its layer less the fall), in the well's frame. */
    public static double ringLowest(int ring) {
        return RootShape.RING_Y[ring] - RISE;
    }

    /** The highest a ring's top comes, in the well's frame. */
    public static double ringHighest(int ring) {
        return RootShape.RING_Y[ring] + 1 + RISE;
    }

    /** The forum layer over the lid (Y0) in a well's frame: its depth. */
    public static int surfaceY(RootShape.Well w) {
        return w.depth;
    }

    /**
     * How high the rings' crests rise through the grate band at a tide ({@link #tide}): nothing below
     * {@link #CREST_FROM}, then up to {@link #CREST_HIGH} blocks over the forum at the top of a spring tide.
     */
    public static double crest(double tide) {
        if (tide <= CREST_FROM) return 0;
        return CREST_HIGH * Math.min(1, (tide - CREST_FROM) / (1 - CREST_FROM));
    }

    /**
     * The eight ribs' inner faces: for each line from the front clockwise, the point {x, z} where a ray from the
     * well's axis along it first enters the wall, and the line's direction {dx, dz} (unit length).
     */
    public static double[][] ribs() {
        double[][] out = new double[LINES.length][];
        double c = RootShape.MID + 0.5;
        for (int i = 0; i < LINES.length; i++) {
            double l = Math.hypot(LINES[i][0], LINES[i][1]), dx = LINES[i][0] / l, dz = LINES[i][1] / l;
            double tau = 0;
            while (tau < RootShape.SIZE) {
                int x = (int) Math.floor(c + dx * tau), z = (int) Math.floor(c + dz * tau);
                if (RootShape.wall(x, z)) break;
                tau += 0.01;
            }
            out[i] = new double[] { c + dx * tau, c + dz * tau, dx, dz };
        }
        return out;
    }

    /**
     * The stretches of rib {@code rib} that are lit, {from, to} layers (to exclusive), bottom up: the rib cells
     * ({@code L}) of the wall where its ray enters it, broken by the flux-layer bands. The full well's.
     */
    public static List<int[]> ribRuns(int rib) {
        return ribRuns(rib, RootShape.well(RootShape.DEPTH));
    }

    /** {@link #ribRuns(int)} in a well of any depth: up to its lid. */
    public static List<int[]> ribRuns(int rib, RootShape.Well w) {
        double[] r = ribs()[rib];
        int x = (int) Math.floor(r[0] + r[2] * 0.02), z = (int) Math.floor(r[1] + r[3] * 0.02);
        List<int[]> out = new ArrayList<>();
        int from = -1;
        for (int y = RootShape.FLOOR_Y; y <= w.lidY; y++) {
            boolean lit = y < w.lidY && w.cell(x, y, z) == 'L';
            if (lit && from < 0) from = y;
            if (!lit && from >= 0) {
                out.add(new int[] { from, y });
                from = -1;
            }
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * The inner faces of a flux-layer band's dark course at layer {@code y} ({@link RootShape#BANDS}): {x, z, inX, inZ}
     * for every course cell of the wall and every side of it that looks into the well. The full well's.
     */
    public static List<int[]> bandFaces(int y) {
        return bandFaces(y, RootShape.well(RootShape.DEPTH));
    }

    /** {@link #bandFaces(int)} in a well of any depth ({@link RootShape.Well#bands}). */
    public static List<int[]> bandFaces(int y, RootShape.Well w) {
        List<int[]> out = new ArrayList<>();
        for (int x = 0; x < RootShape.SIZE; x++) for (int z = 0; z < RootShape.SIZE; z++) {
            if (!RootShape.wall(x, z) || w.cell(x, y, z) != ModuleLook.COURSE) continue;
            for (int[] o : SIDES) {
                int nx = x + o[0], nz = z + o[1];
                if (w.inside(nx, y, nz)) out.add(new int[] { x, z, o[0], o[1] });
            }
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * The forum's grate band over the well, {x, z} in the well's frame (the cells of the forum layer, Y0, at
     * {@link #SURFACE_Y}): what the establish job paved as grate round the dais ({@link CampusPlan#grateBand}).
     */
    public static List<int[]> grate() {
        List<int[]> out = new ArrayList<>();
        int h = RootShape.MID;
        for (int a = -h; a <= h; a++) for (int r = -h; r <= h; r++) {
            if (!CampusPlan.grateBand(a, r)) continue;
            int[] p = RootShape.fromCampus(a, r, 0);
            out.add(new int[] { p[0], p[2] });
        }
        return Collections.unmodifiableList(out);
    }

    // ---- the deep root (0.16.0, CITY.md §4.9 Visuals; d016_final §5)

    /** How long a flowing light takes down one root's way (ticks), and the blocks a way is cut into. */
    public static final int FLOW_T = 140;
    public static final double FLOW_STEP = 1;
    /** A strand's core and its halo (at most 0.3 thick, so it keeps clear of the outer tide ring). */
    public static final double STRAND_CORE = 0.07, STRAND_HALO = 0.3;
    /** How high over the floor slab the light runs along the chamber's floor veins. */
    public static final double VEIN_Y = 1.03;

    /**
     * Whether a deep root's form keeps its plinth line dark (0.16.0): a chamber form's course runs round the chamber's
     * walls at its floor, deep in the ground, where only rock would see it; an in-well form has no course of its own
     * (the well has none), so nothing changes for it. Null (no deep root) is false.
     */
    public static boolean courseBuried(RootShape.Deep d) {
        return d != null && d.h > 0;
    }

    /**
     * The deep root's strands of light where its root conduits dissolve, {x, z, top, bottom} each in the form's own
     * frame, one a conduit column in its line order round the axis: in a chamber at the conduit's column, from the
     * chamber's ceiling (the well's floor slab, y {@code h}) down to the conduit's foot; in-well on its line at
     * {@link RootShape#INWELL_CONDUIT_R} from the axis (on the diagonals at (4.24, 4.24), not the cell's middle at
     * 5.66,
     * so a strand keeps clear of the outer tide ring), from the lid down to the conduit's foot.
     */
    public static List<double[]> strands(RootShape.Deep d) {
        double c = RootShape.MID + 0.5;
        List<double[]> out = new ArrayList<>();
        for (int[] col : conduitColumns(d)) {
            double x = col[0] + 0.5, z = col[1] + 0.5, top = d.h > 0 ? d.h : d.well.lidY;
            if (d.h == 0) {
                double dx = x - c, dz = z - c, l = Math.hypot(dx, dz);
                x = c + dx / l * RootShape.INWELL_CONDUIT_R;
                z = c + dz / l * RootShape.INWELL_CONDUIT_R;
            }
            out.add(new double[] { x, z, top, col[2] });
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * The columns of the form's conduits, {x, z, lowest y}, by their line round the axis (the front first,
     * clockwise seen from above).
     */
    private static List<int[]> conduitColumns(RootShape.Deep d) {
        List<int[]> out = new ArrayList<>();
        int c = RootShape.MID;
        for (int[] line : LINES) {
            int[] found = null;
            for (int x = 0; x < RootShape.SIZE; x++) for (int z = 0; z < RootShape.SIZE; z++) {
                if (Integer.signum(x - c) != line[0] || Integer.signum(z - c) != line[1]) continue;
                if (line[0] != 0 && line[1] != 0 && Math.abs(x - c) != Math.abs(z - c)) continue;
                for (int y = 0; y < d.shape.H; y++) {
                    if (d.cell(x, y, z) != RootShape.CONDUIT) continue;
                    if (found == null || found[0] != x || found[1] != z) found = new int[] { x, z, y };
                    else found[2] = Math.min(found[2], y);
                    break;
                }
            }
            if (found != null) out.add(found);
        }
        return out;
    }

    /**
     * The ways the light flows in the deep root, a polyline of points {x, y, z} each, one a strand in its order
     * ({@link #strands}): in a chamber down the dissolved conduit, then down to the floor under it and out along the
     * lit vein in the floor to the bay on its line; in-well down the strand.
     */
    public static List<double[][]> flows(RootShape.Deep d) {
        List<double[][]> out = new ArrayList<>();
        double c = RootShape.MID + 0.5;
        for (double[] s : strands(d)) {
            if (d.h == 0) {
                out.add(new double[][] { { s[0], s[2], s[1] }, { s[0], s[3], s[1] } });
                continue;
            }
            double[] bay = bayOn(d, s[0] - c, s[1] - c);
            out.add(
                new double[][] { { s[0], s[2], s[1] }, { s[0], s[3], s[1] }, { s[0], VEIN_Y, s[1] },
                    { bay[0], VEIN_Y, bay[1] } });
        }
        return Collections.unmodifiableList(out);
    }

    /** The middle {x, z} of the bay plinth on the line from the axis through (dx, dz). */
    private static double[] bayOn(RootShape.Deep d, double dx, double dz) {
        int c = RootShape.MID;
        for (UnitLayout.Unit u : d.units.units()) {
            int[] p = u.cells.get(0);
            if (!"bay".equals(u.kind)) continue;
            if (Integer.signum(p[0] - c) == (int) Math.signum(dx) && Integer.signum(p[2] - c) == (int) Math.signum(dz))
                return new double[] { p[0] + 0.5, p[2] + 0.5 };
        }
        throw new IllegalStateException("no bay on the line " + dx + ", " + dz + " in " + d.name);
    }

    /** A way's length (blocks), along its polyline. */
    public static double length(double[][] way) {
        double n = 0;
        for (int i = 1; i < way.length; i++) n += dist(way[i - 1], way[i]);
        return n;
    }

    /**
     * Where the light flowing down way {@code k} of {@code ways} has its head at the world time {@code t}, in blocks
     * along it: running from two blocks before its start to two past its end, the ways one after another round the
     * axis.
     */
    public static double flowHead(int k, int ways, double t, double length) {
        double u = ArcaneLook.frac(t / FLOW_T + k / (double) ways);
        return u * (length + 4) - 2;
    }

    /** The grate of a chamber form: its 24 cells {x, z} in the well's floor slab (y {@code h}), lit from below. */
    public static List<int[]> grateFrom(RootShape.Deep d) {
        List<int[]> out = new ArrayList<>();
        if (d.h == 0) return out;
        for (int x = 0; x < RootShape.SIZE; x++)
            for (int z = 0; z < RootShape.SIZE; z++) if (d.cell(x, d.h, z) == ':') out.add(new int[] { x, z });
        return out;
    }

    /**
     * An in-well form's root collars: the bay plinths ({x, z}) standing over a basin rim, whose top lies
     * {@link ModuleLook#BASIN_TOP} up its block, so a gap of the rest of the block shows under the plinth; the collar
     * closes it, from {@link #collarFrom} to the plinth's foot.
     */
    public static List<int[]> collars(RootShape.Deep d) {
        List<int[]> out = new ArrayList<>();
        if (d.h != 0) return out;
        for (UnitLayout.Unit u : d.units.units()) {
            if (!"bay".equals(u.kind)) continue;
            int[] p = u.cells.get(0);
            if (d.cell(p[0], p[1] - 1, p[2]) == 'u') out.add(new int[] { p[0], p[2], p[1] });
        }
        return out;
    }

    /** The height a collar starts at under a plinth standing at layer {@code y}: the basin rim's top under it. */
    public static double collarFrom(int y) {
        return y - 1 + ModuleLook.BASIN_TOP;
    }

    /** The pieces a way is drawn in near: each straight stretch in pieces of at most {@link #FLOW_STEP}. */
    public static int wayPieces(double[][] way) {
        int n = 0;
        for (int i = 1; i < way.length; i++) n += Math.max(1, (int) Math.ceil(dist(way[i - 1], way[i]) / FLOW_STEP));
        return n;
    }

    /**
     * The quads the deep root's own drawing takes a frame at a tier, on top of the well's ({@link #wellQuads}): near
     * every way in its pieces with a halo each and a glow at its head, every grate cell's underside and each collar's
     * four sides and four seams; mid every way's stretches as a line each; far nothing.
     */
    public static int deepQuads(RootShape.Deep d, int tier) {
        if (tier == ModuleLook.FAR) return 0;
        int n = 0;
        for (double[][] way : flows(d)) {
            if (tier == ModuleLook.NEAR) n += 2 * wayPieces(way) + 1;
            else for (int i = 1; i < way.length; i++) n += DomeLook.pieces(dist(way[i - 1], way[i]));
        }
        if (tier == ModuleLook.NEAR) n += grateFrom(d).size() + 8 * collars(d).size();
        return n;
    }

    /**
     * The most quads the well's own drawing ({@code RootRender}) takes a frame at a tier, docked and powered at a high
     * tide, counted as it draws: the tide rings (far {@link #FAR_RING_PIECES} lines each; mid an arc's one piece, its
     * face to the camera and the one band turned to it; near three pieces an arc, each so, the seam's pieces and three
     * motes), the ribs (near a line and a halo a lit block, mid a line a run), near the bands' faces, the pool (a glow
     * a
     * strip of water cells, {@link #poolStrips}, near two caustic lines a cell more) and the grate band's glow with its
     * 16 blades of a core and a halo.
     */
    public static int wellQuads(RootShape.Well w, int tier) {
        int rings = w.ringY().length;
        if (tier == ModuleLook.FAR) return rings * FAR_RING_PIECES;
        boolean near = tier == ModuleLook.NEAR;
        // an arc piece shows its top or its bottom and one of its two bands (never both: the camera is outside the
        // outer one or inside the inner one), near its seam's pieces too and three motes a ring
        int n = near ? rings * (8 * 3 * 2 + 8 * 3 + 3) : rings * 8 * 2;
        for (int i = 0; i < LINES.length; i++)
            for (int[] run : ribRuns(i, w)) n += near ? 2 * (run[1] - run[0]) : DomeLook.pieces(run[1] - run[0]);
        if (near) for (int y : w.bands()) n += bandFaces(y, w).size();
        List<double[]> water = ModuleLook.water(w.build, RootShape.FLOOR_Y, 0.5);
        n += poolStrips(water).size() + (near ? 2 * water.size() : 0);
        n += grate().size() + 2 * 16;
        return n;
    }

    /** The pieces a tide ring is drawn in far, a line each (0.16.0: 12, so the deep root stays within 64 far). */
    public static final int FAR_RING_PIECES = 12;

    /**
     * The pool's glow as strips (0.16.0): its water cells ({@link ModuleLook#water}, middles {x, z}) joined along x
     * where they touch, {x0, z0, x1, z1} cell edges in the frame, from the lowest z and x up: the same cover as a quad
     * a cell, in a dozen quads rather than some eighty.
     */
    public static List<double[]> poolStrips(List<double[]> water) {
        List<double[]> cells = new ArrayList<>(water);
        cells.sort((a, b) -> a[1] != b[1] ? Double.compare(a[1], b[1]) : Double.compare(a[0], b[0]));
        List<double[]> out = new ArrayList<>();
        double[] run = null;
        for (double[] c : cells) {
            double x0 = c[0] - 0.5, z0 = c[1] - 0.5;
            if (run != null && run[1] == z0 && run[2] == x0) {
                run[2] = x0 + 1;
                continue;
            }
            run = new double[] { x0, z0, x0 + 1, z0 + 1 };
            out.add(run);
        }
        return Collections.unmodifiableList(out);
    }

    private static double dist(double[] a, double[] b) {
        double dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * The highest a standing player's head comes under a ring: over every floor cell (a top people stand on, with
     * room above it) within the ring's outer radius, its stand height plus {@link #HEAD}; the floor's level when there
     * is none.
     */
    public static double headUnder(int ring) {
        return headUnder(ring, RootShape.well(RootShape.DEPTH));
    }

    /** {@link #headUnder(int)} in a well of any depth (over the rings it has). */
    public static double headUnder(int ring, RootShape.Well w) {
        double outer = RootShape.RING_R[ring] + 1, c = RootShape.MID;
        double top = RootShape.FLOOR_Y + 1;
        for (int x = 0; x < RootShape.SIZE; x++) for (int z = 0; z < RootShape.SIZE; z++) {
            if (Math.hypot(x - c, z - c) > outer + 0.75) continue;
            for (int y = 0; y < RootShape.RING_Y[ring]; y++) {
                char ch = w.cell(x, y, z);
                // nobody stands in water, on a ring or on the controller
                if (ch == ShapeKit.NONE || ch == ShapeKit.AIR
                    || ch == '='
                    || ch == RootShape.RING
                    || ch == ShapeKit.CONTROLLER) continue;
                char up = w.cell(x, y + 1, z);
                if (up == ShapeKit.NONE || up == ShapeKit.AIR) top = Math.max(top, y + 1 + HEAD);
            }
        }
        return top;
    }
}
