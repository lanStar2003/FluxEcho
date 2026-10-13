package com.fluxecho.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Where the Echo Dome Generator's drawing puts its light (0.12.0, CITY.md §4.8 Visuals), worked out from its shape
 * ({@link DomeGeneratorShape}) in the pod's own frame (x across, y up from the pod floor, z from the pod's front):
 * <ul>
 * <li>The legs' ribs: once it forms, the six legs of frame pillars give way to drawn ribs that follow the curve the
 * legs' cells were rounded from ({@link #legPoint}), tapering from the foot to the head ({@link #legHalf}), cut into
 * segments whose joints pulse.</li>
 * <li>The emitter: a crystal floating over the roof ({@link #EMITTER_Y}), threads of light from the six emitter nodes
 * up to it, and six arcs from the nodes out to the dome ({@link #arcEnd}, {@link #arcPoint}).</li>
 * <li>The dome: a hemisphere of radius {@link #shellRadius} over the forum (64 at phase II, CITY.md §6.1 item 7),
 * nearly invisible as CITY.md §4.8 asks: only a soft ring where it meets the ground ({@link #groundPoint}) and a few
 * hexagonal cells shimmering near the apex ({@link #apexCells}, {@link #shimmer}). A landmark seen from up to
 * {@link #LANDMARK} blocks ({@link #landmarkFade}); with the moored legs' far lines ({@link #FAR_LEG_LINES}) and the
 * crystal's glow drawn with it there, never more than {@link #BUDGET} quads ({@link #landmarkQuads}), counted the way
 * the renderer's lines are cut into pieces of at most {@link #PIECE} blocks.</li>
 * </ul>
 * Pure arithmetic, so the tests hold the ribs to the leg cells, the apex cells to the sphere and the dome to its
 * budget at every distance and every radius it grows to.
 * <p>
 * Over high ground the pod stands lower (0.14.1, {@link DomeGeneratorShape#datum}): the forms that take the sky datum
 * F ({@link #cy}, {@link #legRadius(double, int)}, {@link #legPoint(int, double, int)}, {@link #arcEnd(double, double,
 * double, double, int)}, {@link #farLegQuads(int)}) follow the shorter legs and the forum nearer the pod; those without
 * it are the full legs' (F 40).
 */
public final class DomeLook {

    /** How far the dome is drawn (blocks from its centre), and the most quads it may take a frame (CITY.md §1.3). */
    public static final double LANDMARK = 512;
    public static final int BUDGET = 400;
    /** The dome's middle in the pod's frame: the forum's surface (Y0 + 1) under the pod's axis (on its full legs). */
    public static final double CX = DomeGeneratorShape.MID + 0.5, CY = 1 - DomeGeneratorShape.FLOOR_UP,
        CZ = DomeGeneratorShape.MID + 0.5;
    /** The emitter crystal's middle over the roof (Y0 + 52.5: CITY.md §2.5 "dome emitter +52"). */
    public static final double EMITTER_Y = 12.5;
    /** The segments a leg's rib is cut into near, and its half-width at the foot and at the head. */
    public static final int LEG_SEGMENTS = 13;
    public static final double FOOT_HALF = 0.55, HEAD_HALF = 0.3;
    /** How far an arc bows up over the straight line from its node to the dome, as a share of its length. */
    public static final double BOW = 0.12;
    /** The apex's hexagonal cells: their size as a share of the dome's radius, and how many rings round the apex. */
    public static final double HEX = 1 / 9.0;
    public static final int HEX_RINGS = 2;
    /** Quads a lit hexagon's fill takes (two halves); its six edges are lines, cut into pieces as any line. */
    public static final int HEX_FILL_QUADS = 2;
    /**
     * The longest piece a drawn line is cut into, in blocks: the city renderer's ({@code CityFrame.lineRel}), so each
     * piece can be about a pixel wide where it is; a line costs {@link #pieces} quads. The two must stay equal.
     */
    public static final double PIECE = 8;
    /** The lines a moored leg is drawn in far away (and as part of the landmark), from its foot to its head. */
    public static final int FAR_LEG_LINES = 2;
    /** How far before {@link #LANDMARK} the landmark starts fading out, in blocks. */
    public static final double LANDMARK_FADE = 64;

    /** The legs' campus angles in leg order (DomeGeneratorShape's), degrees from +A towards +R. */
    private static final double[] LEG_ANGLE = { 30, 90, 150, -150, -90, -30 };

    private DomeLook() {}

    /** The height of the dome's middle (the forum's surface) in the pod's frame at the sky datum {@code datum}. */
    public static double cy(int datum) {
        return 1 - datum;
    }

    /** The dome's radius at a nexus phase: 64 (II), 104 (III), 128 (IV), 160 (V) (CITY.md §6.1 item 7). */
    public static double shellRadius(int phase) {
        if (phase <= 2) return 64;
        if (phase == 3) return 104;
        if (phase == 4) return 128;
        return 160;
    }

    // ---- the legs

    /** A leg's radius from the axis at the height h (0 at its foot cell, 38 at its head cell), as the generator's. */
    public static double legRadius(double h) {
        return legRadius(h, DomeGeneratorShape.FLOOR_UP);
    }

    /**
     * A leg's radius from the axis at the height h at the sky datum {@code datum} (0 at its foot cell, F - 2 at its
     * head cell), as the generator's {@code dome_legs(F)}.
     */
    public static double legRadius(double h, int datum) {
        double f = 1 - Math.max(0, Math.min(datum - 2, h)) / (double) (datum - 2);
        return DomeGeneratorShape.LEG_HEAD + (DomeGeneratorShape.LEG_FOOT - DomeGeneratorShape.LEG_HEAD)
            * StrictMath.pow(f, DomeGeneratorShape.LEG_CURVE);
    }

    /**
     * The point {@code s} of the way up leg {@code leg}'s axis (0 the bottom of its foot cell on the forum, 1 the top
     * of its head cell under the pod), in the pod's frame: through the middle of every cell the leg was rounded to,
     * give or take the rounding (at most half a block each way). The full legs'.
     */
    public static double[] legPoint(int leg, double s) {
        return legPoint(leg, s, DomeGeneratorShape.FLOOR_UP);
    }

    /** {@link #legPoint(int, double)} for the legs at the sky datum {@code datum} (F - 1 cells high). */
    public static double[] legPoint(int leg, double s, int datum) {
        double h = -0.5 + (datum - 1) * s;
        double r = legRadius(h, datum), th = Math.toRadians(LEG_ANGLE[leg]);
        double a = r * Math.cos(th), rr = r * Math.sin(th);
        return new double[] { CX - rr, h + 0.5 - datum + 1, CZ - a };
    }

    /**
     * A leg rib's half-width {@code s} of the way up: {@link #FOOT_HALF} at the foot to {@link #HEAD_HALF} at the head.
     */
    public static double legHalf(double s) {
        return FOOT_HALF + (HEAD_HALF - FOOT_HALF) * Math.max(0, Math.min(1, s));
    }

    // ---- the arcs

    /**
     * Where the arc from an emitter node {@code (x, y, z)} meets a dome of radius {@code radius}: straight out from the
     * axis at the node's own height, on the sphere (at the apex when the node stands above it). On the full legs.
     */
    public static double[] arcEnd(double x, double y, double z, double radius) {
        return arcEnd(x, y, z, radius, DomeGeneratorShape.FLOOR_UP);
    }

    /** {@link #arcEnd(double, double, double, double)} for a pod at the sky datum {@code datum}. */
    public static double[] arcEnd(double x, double y, double z, double radius, int datum) {
        double cy = cy(datum);
        double dx = x - CX, dz = z - CZ, h = y - cy, len = Math.hypot(dx, dz);
        if (h >= radius) return new double[] { CX, cy + radius, CZ };
        double out = Math.sqrt(radius * radius - h * h);
        if (len < 1e-9) return new double[] { CX + out, y, CZ };
        return new double[] { CX + dx / len * out, y, CZ + dz / len * out };
    }

    /**
     * The point {@code u} of the way along an arc from {@code a} to {@code b}, bowing up by {@link #BOW} of its length.
     */
    public static double[] arcPoint(double[] a, double[] b, double u) {
        double len = Math.sqrt(sq(b[0] - a[0]) + sq(b[1] - a[1]) + sq(b[2] - a[2]));
        double[] m = { (a[0] + b[0]) / 2, (a[1] + b[1]) / 2 + BOW * len, (a[2] + b[2]) / 2 };
        double g = 1 - u;
        double[] p = new double[3];
        for (int i = 0; i < 3; i++) p[i] = g * g * a[i] + 2 * g * u * m[i] + u * u * b[i];
        return p;
    }

    // ---- the dome

    /**
     * Point {@code i} of {@code n} round the dome's ground ring, where a dome of radius {@code radius} meets the forum:
     * {x, z} in the pod's frame (the ring lies at {@link #CY}).
     */
    public static double[] groundPoint(int i, int n, double radius) {
        double phi = i * 2 * Math.PI / n;
        return new double[] { CX + Math.cos(phi) * radius, CZ + Math.sin(phi) * radius };
    }

    /**
     * The hexagonal cells near the apex of a dome of radius {@code radius}, relative to its middle: a hexagon grid
     * {@link #HEX} of the radius across laid on the plane over the apex and lifted onto the sphere, the cells within
     * {@code rings} of the apex's (1, 7, 19, ...). Each cell is seven points, {x, y, z} each: its middle, then its six
     * corners round it.
     */
    public static List<double[][]> apexCells(double radius, int rings) {
        double s = radius * HEX;
        List<double[][]> out = new ArrayList<>();
        for (int q = -rings; q <= rings; q++) for (int r = -rings; r <= rings; r++) {
            if (Math.max(Math.abs(q), Math.max(Math.abs(r), Math.abs(-q - r))) > rings) continue;
            double px = s * Math.sqrt(3) * (q + r / 2.0), pz = s * 1.5 * r;
            double[][] cell = new double[7][];
            cell[0] = lift(px, pz, radius);
            for (int i = 0; i < 6; i++) {
                double a = Math.toRadians(60 * i + 30);
                // a hair inside the cell's edge, so neighbours' outlines do not lie on one another
                cell[i + 1] = lift(px + 0.94 * s * Math.cos(a), pz + 0.94 * s * Math.sin(a), radius);
            }
            out.add(cell);
        }
        return Collections.unmodifiableList(out);
    }

    /** A point {@code (x, z)} of the plane over the apex, lifted along the line from the middle onto the sphere. */
    private static double[] lift(double x, double z, double radius) {
        double l = Math.sqrt(x * x + radius * radius + z * z), k = radius / l;
        return new double[] { x * k, radius * k, z * k };
    }

    /**
     * How brightly an apex cell shimmers at the time {@code t} (ticks): 0 most of the time, rising to 1 for a moment
     * now and then, each cell in its own rhythm.
     */
    public static double shimmer(int cell, double t) {
        double phase = (cell * 0.61803398875 % 1) * 2 * Math.PI, rate = 0.011 + 0.004 * ((cell * 0.4142135 % 1));
        double w = Math.sin(t * rate + phase);
        return w <= 0 ? 0 : Math.pow(w, 8);
    }

    /**
     * How many sides the dome's ground ring is drawn with at a distance from its middle (56 between 192 and 320, so a
     * side of the phase II ring stays one piece long).
     */
    public static int groundSegments(double distance) {
        return distance <= 192 ? 64 : distance <= 320 ? 56 : 32;
    }

    /**
     * How many rings of apex cells round the apex's own are drawn at a distance from its middle, for a dome of radius
     * {@code radius}: {@link #HEX_RINGS} on the phase II dome near and mid, one further out or on a wider dome (its
     * cells grow with it, so one ring already covers about as much sky as two on the phase II dome, and each of their
     * edges costs more pieces).
     */
    public static int hexRings(double distance, double radius) {
        return distance <= 320 && radius <= shellRadius(2) ? HEX_RINGS : 1;
    }

    /** The quads a line {@code length} blocks long is drawn in: the renderer cuts it into pieces of {@link #PIECE}. */
    public static int pieces(double length) {
        return Math.max(1, (int) Math.ceil(length / PIECE));
    }

    /**
     * How the landmark fades out at a distance (blocks from the dome's middle): 1 near, falling over the last
     * {@link #LANDMARK_FADE} blocks to 0 at {@link #LANDMARK} and past it.
     */
    public static float landmarkFade(double distance) {
        return (float) Math.max(0, Math.min(1, (LANDMARK - distance) / LANDMARK_FADE));
    }

    /**
     * The most quads the dome takes a frame at a distance from its middle, for a dome of radius {@code radius}, with
     * all its apex cells lit at once: round the ground ring a soft band a side and the side's line in its pieces, and
     * each apex cell's fill and its six edges in their pieces. Counted from the same points the renderer draws.
     */
    public static int shellQuads(double distance, double radius) {
        int n = groundSegments(distance), quads = 0;
        for (int i = 0; i < n; i++) {
            double[] a = groundPoint(i, n, radius), b = groundPoint(i + 1, n, radius);
            quads += 1 + pieces(Math.hypot(b[0] - a[0], b[1] - a[1]));
        }
        for (double[][] c : apexCells(radius, hexRings(distance, radius))) {
            quads += HEX_FILL_QUADS;
            for (int k = 1; k <= 6; k++) {
                double[] u = c[k], v = c[k % 6 + 1];
                quads += pieces(Math.sqrt(sq(v[0] - u[0]) + sq(v[1] - u[1]) + sq(v[2] - u[2])));
            }
        }
        return quads;
    }

    /**
     * The quads of the six moored legs drawn far away: {@link #FAR_LEG_LINES} lines a leg through
     * {@link #legPoint}, each in its pieces. The full legs', the most there are.
     */
    public static int farLegQuads() {
        return farLegQuads(DomeGeneratorShape.FLOOR_UP);
    }

    /** {@link #farLegQuads()} for the legs at the sky datum {@code datum}: no more than the full legs'. */
    public static int farLegQuads(int datum) {
        int quads = 0;
        for (int leg = 0; leg < DomeGeneratorShape.LEGS; leg++) for (int k = 0; k < FAR_LEG_LINES; k++) {
            double[] a = legPoint(leg, k / (double) FAR_LEG_LINES, datum),
                b = legPoint(leg, (k + 1) / (double) FAR_LEG_LINES, datum);
            quads += pieces(Math.sqrt(sq(b[0] - a[0]) + sq(b[1] - a[1]) + sq(b[2] - a[2])));
        }
        return quads;
    }

    /**
     * The most quads the landmark takes a frame (CITY.md §1.3: at most {@link #BUDGET}) at a distance from the dome's
     * middle, for a dome of radius {@code radius}: the dome ({@link #shellQuads}), the moored legs' far lines when it
     * stands on them ({@link #farLegQuads}) and the glow where the crystal floats.
     */
    public static int landmarkQuads(double distance, double radius, boolean legs) {
        return shellQuads(distance, radius) + (legs ? farLegQuads() : 0) + 1;
    }

    private static double sq(double d) {
        return d * d;
    }
}
