package com.fluxecho.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Where the Holo Sand Table's drawing puts its light (0.15.0, CITY.md §4.11 Visuals), worked out in the table's frame
 * ({@link HoloTableShape}: x across, y up from the base layer, z back from the blueprint front) round the round
 * table's middle ({@link #CX}, {@link #CZ}):
 * <ul>
 * <li>Formed: the ring light's segments give way to a rendered ring of light hanging over the table
 * ({@link #RING_Y}, radius {@link #RING_RADIUS}); it glows in the table's colour while docked (a highlight running
 * round it while powered) and burns low undocked.</li>
 * <li>Docked (idle): over the table's glass a miniature of the campus the table is docked to, built from the client's
 * view of that campus ({@link Model}): the graded ground and its rings (the forum, the promenade ring, the disc's
 * edge, and once the outer ring is laid its promenade and edge), the nexus as its column at its phase, every module as
 * a box in its own colour (at most {@link #MINI_BOXES}, the nearest the nexus first), and the zenith's pod and the
 * root's well in section (two planes crossed on the axis, the pod's legs as lines). It turns once every
 * {@link #TURN_T} ticks; a soft column of light carries it up from the glass.</li>
 * <li>Its name plate floats over the middle above the ring light, under the lantern ({@link #plate}), clear of the
 * miniature: the table's console stands at the table's front, not in the middle, so the centrally planned rule
 * ({@link ModuleLook#central}) would put it in front of the drum instead.</li>
 * </ul>
 * The campus is scaled to fit the glass ({@link #MINI_R} for the graded disc's edge) and stood on a ground lifted so
 * the deepest root would still clear the glass ({@link #ground}). Pure arithmetic over the shape and the model, so the
 * tests hold the miniature over the glass and under the ring light and every count to what the renderer draws
 * ({@link #quads}). Every motion is a function of the world time alone.
 */
public final class HoloLook {

    /** The round table's middle across and deep, and the top of its glass (the table stands at y1). */
    public static final double CX = HoloTableShape.MID + 0.5, CZ = HoloTableShape.MID + 0.5, TABLE_TOP = 2;
    /** The ring light: the middle of its segments' layer and of their ring. */
    public static final double RING_Y = HoloTableShape.RING_Y + 0.5,
        RING_RADIUS = (HoloTableShape.RING_IN + HoloTableShape.RING_OUT) / 2;
    /** The pieces the ring light is drawn in near, mid and far; near each with a halo. */
    public static final int[] RING_PIECES = { 48, 24, 16 };
    /** The ticks a powered ring light's highlight takes to run round once. */
    public static final int RING_RUN_T = 160;
    /** How high the name plate floats over the middle: over the ring light, under the stepped dome's open middle. */
    public static final double PLATE_Y = HoloTableShape.RING_Y + 2.6;

    /** The miniature: the radius the graded disc's edge is drawn at, how far its lowest point stays over the glass. */
    public static final double MINI_R = 3.6, LIFT = 0.35;
    /** How deep the root may go (CITY.md §2.6: the ley core's sphere to Y0 - 57): the ground is lifted for it. */
    public static final double ROOT_DEPTH = 57;
    /** The ticks of one turn of the miniature, and the most module boxes it shows. */
    public static final int TURN_T = 1200, MINI_BOXES = 24;
    /** The graded disc's edge (blocks from the nexus's middle) before and after the outer ring is laid. */
    public static final double INNER_EXTENT = CampusPlan.GRADE_R + 0.5, OUTER_EXTENT = OuterRing.OUTER_R + 0.5;

    /** The quads of a box near (its top and four sides, and its top's rim) and mid (its top alone). */
    public static final int BOX_FACES = 5, BOX_RIM = 4;
    /** The quads of a section's plane (its fill and its four edges near, its fill mid); a section is two planes. */
    public static final int SECTION_EDGES = 4;
    /** The quads of the ground's fill (an octagon as three quads) and of the column of light up from the glass. */
    public static final int GROUND_FILL = 3, BEAM_QUADS = 4;

    private HoloLook() {}

    // ---- the ring light and the plate

    /** The point of the ring light at the angle {@code a} (radians) round the table: {x, z} in the table's frame. */
    public static double[] onRing(double a) {
        return new double[] { CX + RING_RADIUS * Math.cos(a), CZ + RING_RADIUS * Math.sin(a) };
    }

    /**
     * How bright the ring light is at the angle {@code a} at the world time {@code t}: an even glow, and while powered
     * a highlight running round once every {@link #RING_RUN_T} ticks.
     */
    public static float ringLight(double a, double t, boolean on) {
        if (!on) return 1f;
        double head = 2 * Math.PI * ArcaneLook.frac(t / RING_RUN_T), d = Math.abs(a - head) % (2 * Math.PI);
        d = Math.min(d, 2 * Math.PI - d);
        return 1f + 0.8f * (float) Math.exp(-d * d * 4);
    }

    /** The table's name plate {x, y, z} in its frame: over the middle above the ring light. */
    public static double[] plate() {
        return new double[] { CX, PLATE_Y, CZ };
    }

    // ---- the miniature

    /**
     * A box of the miniature in the campus: {x0, y0, z0, x1, y1, z1} from the nexus's middle at the ground, a colour.
     */
    public static final class Box {

        public final double x0, y0, z0, x1, y1, z1;
        public final int rgb;

        public Box(double x0, double y0, double z0, double x1, double y1, double z1, int rgb) {
            this.x0 = Math.min(x0, x1);
            this.y0 = Math.min(y0, y1);
            this.z0 = Math.min(z0, z1);
            this.x1 = Math.max(x0, x1);
            this.y1 = Math.max(y0, y1);
            this.z1 = Math.max(z0, z1);
            this.rgb = rgb;
        }

        /** How far its middle lies from the nexus's across the ground. */
        double reach() {
            double x = (x0 + x1) / 2, z = (z0 + z1) / 2;
            return Math.sqrt(x * x + z * z);
        }
    }

    /**
     * A module of the campus as the miniature shows it (0.15.0, from the client's campus view, so the modules whose
     * chunks the client has not loaded show as well): its footprint {x0, z0, x1, z1} (world columns) from the forum's
     * floor up to its {@code height}; or, when the client has the module itself loaded, the bounds
     * {x0, y0, z0, x1, y1, z1} its structure was found in. Relative to the campus centred at (cx, y0, cz): the middle
     * of its centre cell at the forum's floor (Y0 + 1), as {@link Model} has it.
     */
    public static Box siteBox(int[] footprint, int[] bounds, int cx, int y0, int cz, int height, int rgb) {
        double ox = cx + 0.5, oy = y0 + 1, oz = cz + 0.5;
        if (bounds != null && bounds.length == 6) return new Box(
            bounds[0] - ox,
            bounds[1] - oy,
            bounds[2] - oz,
            bounds[3] + 1 - ox,
            bounds[4] + 1 - oy,
            bounds[5] + 1 - oz,
            rgb);
        return new Box(
            footprint[0] - ox,
            -1,
            footprint[1] - oz,
            footprint[2] + 1 - ox,
            height - 1,
            footprint[3] + 1 - oz,
            rgb);
    }

    /**
     * What the miniature shows of one campus, in world blocks from the nexus's middle at the ground (x and z along the
     * world's axes, y up from the forum's floor, Y0 + 1): its scale and the ground's height in the table's frame, the
     * nexus's column, the module boxes, the axis sections, the pod's legs and the rings on the ground (octagons
     * {x, z}, the last one the ground's edge).
     */
    public static final class Model {

        public final double scale, ground;
        public final Box nexus;
        public final List<Box> boxes, sections;
        public final List<double[]> legs;
        public final int legRgb;
        public final List<double[][]> rings;

        Model(double scale, double ground, Box nexus, List<Box> boxes, List<Box> sections, List<double[]> legs,
            int legRgb, List<double[][]> rings) {
            this.scale = scale;
            this.ground = ground;
            this.nexus = nexus;
            this.boxes = Collections.unmodifiableList(boxes);
            this.sections = Collections.unmodifiableList(sections);
            this.legs = Collections.unmodifiableList(legs);
            this.legRgb = legRgb;
            this.rings = Collections.unmodifiableList(rings);
        }
    }

    /**
     * The miniature of a campus: the nexus's column at its phase ({@link NexusShape#height}, on the 11 x 11 base, in
     * {@code nexusRgb}); the module boxes, the nearest the nexus first and at most {@link #MINI_BOXES}; the zenith's
     * pod and the root's well as sections on the axis ({@code pod}, {@code root}, either null when nothing stands
     * there) and the pod's legs ({x0, y0, z0, x1, y1, z1} each); the rings of the ground, the outer ones once the
     * outer ring is laid ({@code outer}), which also scales the ground's edge out to the outer disc's.
     */
    public static Model model(int phase, boolean outer, int nexusRgb, List<Box> modules, Box pod, List<double[]> legs,
        int legRgb, Box root) {
        double extent = outer ? OUTER_EXTENT : INNER_EXTENT, s = MINI_R / extent;
        double half = NexusShape.RADIUS + 0.5;
        Box nexus = new Box(-half, -1, -half, half, NexusShape.height(Math.max(1, phase)) - 1, half, nexusRgb);
        List<Box> boxes = new ArrayList<>(modules == null ? Collections.<Box>emptyList() : modules);
        // the nearest the nexus first (a stable sort: equal reaches keep the order they came in)
        boxes.sort((a, b) -> Double.compare(a.reach(), b.reach()));
        if (boxes.size() > MINI_BOXES) boxes = new ArrayList<>(boxes.subList(0, MINI_BOXES));
        List<Box> sections = new ArrayList<>();
        if (pod != null) sections.add(pod);
        if (root != null) sections.add(root);
        List<double[]> l = new ArrayList<>();
        if (pod != null && legs != null) l.addAll(legs);
        List<double[][]> rings = new ArrayList<>();
        rings.add(OuterLook.octagon(CampusPlan.FORUM_R, CampusPlan.FORUM_K));
        rings.add(OuterLook.octagon(CampusPlan.RING_R, CampusPlan.RING_K));
        if (outer) {
            rings.add(OuterLook.octagon(OuterRing.PROM_IN_R, OuterRing.PROM_IN_K));
            rings.add(OuterLook.octagon(OuterRing.PROM_R, OuterRing.PROM_K));
            rings.add(OuterLook.octagon(CampusPlan.GRADE_R, CampusPlan.GRADE_K));
            rings.add(OuterLook.octagon(OuterRing.OUTER_R, OuterRing.OUTER_K));
        } else rings.add(OuterLook.octagon(CampusPlan.GRADE_R, CampusPlan.GRADE_K));
        return new Model(s, ground(s), nexus, boxes, sections, l, legRgb, rings);
    }

    /**
     * The height of the miniature's ground in the table's frame at a scale: lifted over the glass by the deepest root
     * there may be, so whatever lies under the forum stays over the table.
     */
    public static double ground(double scale) {
        return TABLE_TOP + LIFT + ROOT_DEPTH * scale;
    }

    /** How far the miniature has turned at the world time {@code t} (radians). */
    public static double turn(double t) {
        return 2 * Math.PI * ArcaneLook.frac(t / TURN_T);
    }

    /**
     * A point of the campus ({@code x, y, z} in blocks from the nexus's middle at the ground) on the miniature turned
     * {@code turn}: {dx, y, dz}, {@code dx, dz} along the world's axes from the table's middle (so the campus is
     * never mirrored, whichever way the table faces) and {@code y} the height in the table's frame.
     */
    public static double[] at(Model m, double x, double y, double z, double turn) {
        double c = Math.cos(turn), sn = Math.sin(turn), s = m.scale;
        return new double[] { s * (x * c - z * sn), m.ground + s * y, s * (x * sn + z * c) };
    }

    // ---- the budget

    /** The quads of a line of the miniature {@code length} campus blocks long at its scale, in its pieces. */
    private static int line(Model m, double length) {
        return DomeLook.pieces(length * m.scale);
    }

    /**
     * The quads the miniature takes a frame at a tier, counted as {@code HoloTableRender} draws it from the same
     * model: near the ground's fill and every ring's sides, the nexus and every box with its top's rim, each section's
     * two planes with their edges, the legs and the column of light up from the glass; mid the ground's fill and its
     * edge, the tops of the nexus and the boxes and the sections' planes; far nothing.
     */
    public static int miniQuads(Model m, int tier) {
        if (tier == ModuleLook.FAR) return 0;
        boolean near = tier == ModuleLook.NEAR;
        int n = GROUND_FILL;
        List<double[][]> rings = near ? m.rings : m.rings.subList(m.rings.size() - 1, m.rings.size());
        for (double[][] o : rings) for (int i = 0; i < o.length; i++) {
            double[] a = o[i], b = o[(i + 1) % o.length];
            n += line(m, Math.hypot(b[0] - a[0], b[1] - a[1]));
        }
        List<Box> all = new ArrayList<>(m.boxes);
        all.add(m.nexus);
        for (Box b : all) {
            if (!near) {
                n++;
                continue;
            }
            n += BOX_FACES;
            n += 2 * line(m, b.x1 - b.x0) + 2 * line(m, b.z1 - b.z0);
        }
        for (Box b : m.sections) {
            // two planes crossed on the axis: one across x, one across z, each a fill and near its four edges
            n += 2;
            if (near) n += 2 * line(m, b.x1 - b.x0) + 2 * line(m, b.z1 - b.z0) + 4 * line(m, b.y1 - b.y0);
        }
        if (near) {
            for (double[] l : m.legs) n += line(m, Math.sqrt(sq(l[3] - l[0]) + sq(l[4] - l[1]) + sq(l[5] - l[2])));
            n += BEAM_QUADS;
        }
        return n;
    }

    /** The quads of the ring light at a tier: its pieces, near each with a halo. */
    public static int ringQuads(int tier) {
        int n = 0;
        for (int i = 0; i < RING_PIECES[tier]; i++) {
            double[] a = onRing(2 * Math.PI * i / RING_PIECES[tier]),
                b = onRing(2 * Math.PI * (i + 1) / RING_PIECES[tier]);
            n += DomeLook.pieces(Math.hypot(b[0] - a[0], b[1] - a[1]));
        }
        return tier == ModuleLook.NEAR ? 2 * n : n;
    }

    /**
     * The most quads the table's own drawing takes a frame at a tier for a miniature ({@code m}, null when none is
     * shown: undocked): the ring light and the miniature. The shared drawing ({@link ModuleLook#baseQuads}) comes on
     * top.
     */
    public static int quads(int tier, Model m) {
        return ringQuads(tier) + (m == null ? 0 : miniQuads(m, tier));
    }

    private static double sq(double d) {
        return d * d;
    }
}
