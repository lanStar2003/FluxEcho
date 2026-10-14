package com.fluxecho.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 天环 The sky ring (0.16.0, CITY.md §2.5, §4.12): the SKY family's sites 30 to 37 (S0 to S7) round the floating pod,
 * and what every sky module shares. A sky module stands on a floating platform {@code oct(10, 14)} (21 x 21, the pod's
 * own octagon) of floating foundation (frame FLOAT, {@code f}), railed round its edge but for the catwalk's mouth at
 * its
 * front (x 9..11). The platform's centre lies at radial {@link #CENTRE} on its site's axis, its front row at radial
 * {@link #FRONT} and its front faces the pod; its deck (the platform's y0) is level with the pod's floor, Y0 + F, at
 * the sky datum F of the pod that stands ({@link DomeGeneratorShape#podDatum}), so the shape's y up from the deck is
 * the
 * pod's y up from its floor.
 * <p>
 * Each module's job lays its own catwalk and opens its own door in the pod: the catwalk ({@link #catwalk}) from radial
 * {@link #CATWALK_FROM} to {@link #CATWALK_TO} on the axis, a deck three wide at the deck's height and a rail either
 * side a cell up (the generator's {@code d} and {@code r} as their soft twins {@code .} and {@code |}: built, never
 * checked); the door ({@link #doorCells}) the pod's three door columns on the axis at radial {@link #DOOR_RADIAL},
 * y1..y4 over its floor, emptied as must-be-air steps. The pod's protected box holds every other job; for the job of a
 * sky site exactly its door's twelve cells and its catwalk's first row, which lie inside it, are exempt
 * ({@link #exempt}).
 * <p>
 * The odd sites S1, S3, S5, S7 are the berths of 0.17.0 (their platforms on the diagonals, at {@link #BERTH_CENTRE},
 * reached by diagonal catwalks the orbit job lays): no module is ever offered one ({@link #berth}). The even sites S0,
 * S2, S4, S6 are the four modules' (the weather station over the gate, the sails and the hangar over halls 2 and 6,
 * the observatory over the Archive's hall 4).
 * <p>
 * Nothing of a sky module touches the ground: its keep-out is empty, its clear box is in the air (the platform's box
 * and a margin from a cell under the deck to one more than {@link BuildPlan#MODULE_HEADROOM} over its top layer, and
 * its catwalk's
 * columns, never graded), and its protected boxes are the platform's box with that margin from a cell under the deck to
 * two over its top layer, and its catwalk ({@link #protect}). So the ground modules under the ring stop their clear and
 * protected boxes two cells under the lowest deck the base layer gives ({@link #groundTop}): the Dual-foil Vault's box,
 * the only one that reached so high, at the sky datum 28.
 * <p>
 * Over high ground the plans clip at the world's top as every plan does (0.14.1): a module is refused only when its
 * controller or a unit cell would leave the world ({@link #noRoom}), which never happens over a base layer a core may
 * be
 * placed at, since every sky module's controller and unit cells stand at most {@link #STAND_TOP} over its deck (the
 * highest deck is 253, over Y0 225).
 */
public final class SkyRing {

    /** The first sky site (S0) and the number of sky sites. */
    public static final int SITE0 = 30, SLOTS = DomeGeneratorShape.SKY_SLOTS;
    /** The platform's box (the generator's {@code P}) and its middle ({@code PC}), the octagon {@code oct(10, 14)}. */
    public static final int SIZE = 21, MID = 10, HALF = 10, K = 14;
    /** The radial of a module platform's centre on its axis, and of its front row (the catwalk's mouth). */
    public static final int CENTRE = 32, FRONT = CENTRE - MID;
    /** A berth's centre on its diagonal, (+-23, +-23) (0.17.0). */
    public static final int BERTH_CENTRE = 23;
    /** The catwalk's rows on the axis, its deck's half width and its rails' offset across the axis. */
    public static final int CATWALK_FROM = 11, CATWALK_TO = 21, CATWALK_HALF = 1, CATWALK_RAIL = 2;
    /** The radial of the pod's door columns on an axis (the pod's flat face). */
    public static final int DOOR_RADIAL = DomeGeneratorShape.MID;
    /** How high over its deck every sky module's controller and unit cells stand at most. */
    public static final int STAND_TOP = 2;
    /** The cells a catwalk's clear columns take over its deck: a player walks it standing a cell up. */
    public static final int CATWALK_HEAD = 4;
    /** How far under the lowest deck the ground modules under the ring stop their clear and protected boxes. */
    public static final int GROUND_GAP = 2;

    /**
     * The stages of a sky module's job: CLEAR the air, FLOAT the platform and its rail, SHELL the walls and the
     * must-be-air, ROOF the roofs and domes, FIT the fittings and the units, DECOR the catwalk and then the pod's door,
     * COMMISSION the controller.
     */
    public static final int K_CLEAR = 0, K_FLOAT = 1, K_SHELL = 2, K_ROOF = 3, K_FIT = 4, K_DECOR = 5, K_COMMISSION = 6,
        K_STAGES = 7;

    /** The axis {da, dr} of each slot: S0 the gate's (+A), then clockwise, the generator's {@code catwalk} order. */
    private static final int[][] AXES = { { 1, 0 }, { 1, 1 }, { 0, 1 }, { -1, 1 }, { -1, 0 }, { -1, -1 }, { 0, -1 },
        { 1, -1 } };

    /** The highest y a block can stand at. */
    private static final int WORLD_TOP = 255;

    /** The platform's plan (its octagon's columns, as {@link ShapeKit#key}) and its thick edge. */
    private static final Set<Long> PLAN, EDGE;

    static {
        Set<Long> plan = new LinkedHashSet<>();
        for (int x = 0; x < SIZE; x++) for (int z = 0; z < SIZE; z++) {
            if (ShapeKit.octo(x - MID, z - MID, HALF, K)) plan.add(ShapeKit.key(x, z));
        }
        PLAN = Collections.unmodifiableSet(plan);
        EDGE = Collections.unmodifiableSet(ShapeKit.thickBoundary(plan));
    }

    private SkyRing() {}

    // ---- sites

    /** Whether the site is a sky site, 30 to 37. */
    public static boolean skySite(int site) {
        return site >= SITE0 && site < SITE0 + SLOTS;
    }

    /** The sky slot of a sky site (0 for S0, site 30), or -1 for any other site. */
    public static int slot(int site) {
        return skySite(site) ? site - SITE0 : -1;
    }

    /** Whether the site is one of the berths S1, S3, S5, S7 (31, 33, 35, 37): never offered to a module. */
    public static boolean berth(int site) {
        return skySite(site) && site % 2 == 1;
    }

    /** Whether the site is a module platform's, S0, S2, S4 or S6 (30, 32, 34, 36). */
    public static boolean platformSite(int site) {
        return skySite(site) && site % 2 == 0;
    }

    /**
     * The unit axis {da, dr} of a sky site: S0 the gate's (+A), S2 +R, S4 -A, S6 -R, the berths the diagonals between.
     *
     * @throws IllegalArgumentException for a site that is no sky site
     */
    public static int[] axis(int site) {
        if (!skySite(site)) throw new IllegalArgumentException("not a sky site: " + site);
        int[] a = AXES[site - SITE0];
        return new int[] { a[0], a[1] };
    }

    /**
     * The platform's centre {a, r}: radial {@link #CENTRE} on a module site's axis, {@code (+-23, +-23)} for a berth.
     *
     * @throws IllegalArgumentException for a site that is no sky site
     */
    public static int[] centre(int site) {
        int[] a = axis(site);
        int d = berth(site) ? BERTH_CENTRE : CENTRE;
        return new int[] { a[0] * d, a[1] * d };
    }

    /** Whether (a, r) lies on the site's platform, {@code oct(10, 14)} round its centre (seen from above). */
    public static boolean inPlatform(int site, int a, int r) {
        int[] c = centre(site);
        return CampusPlan.oct(a - c[0], r - c[1], HALF, K);
    }

    /** The bit of a sky site in a mask of sky slots ({@code BuildJob.Form#doors}, the nexus's sky mask). */
    public static int bit(int site) {
        return skySite(site) ? 1 << (site - SITE0) : 0;
    }

    // ---- the platform (b_sky.py's platform, hut, portal)

    /** Whether the column (x, z) of a platform's box lies on its octagon. */
    public static boolean onPlatform(int x, int z) {
        return PLAN.contains(ShapeKit.key(x, z));
    }

    /** Whether the column (x, z) lies on the platform's thick edge (where its rail stands). */
    public static boolean onEdge(int x, int z) {
        return EDGE.contains(ShapeKit.key(x, z));
    }

    /**
     * A new shape on a platform ({@code platform(name, H)}): every column of the octagon a floating foundation at y0,
     * and a rail at y1 round its thick edge but for the catwalk's mouth (z 0..1, x 9..11).
     */
    static ShapeKit.Shape platform(String name, int height) {
        ShapeKit.Shape s = new ShapeKit.Shape(name, SIZE, height, SIZE);
        // the generator's platform has no ground round it: the deck floats
        s.groundOutside = false;
        for (long k : PLAN) s.set(ShapeKit.keyX(k), 0, ShapeKit.keyZ(k), 'f');
        for (long k : EDGE) {
            int x = ShapeKit.keyX(k), z = ShapeKit.keyZ(k);
            if (z <= 1 && x >= MID - 1 && x <= MID + 1) continue;
            s.set(x, 1, z, 'r');
        }
        return s;
    }

    /**
     * A small kit building on a platform ({@code hut}): walls y1..{@code top} round the box x0..x1 by z0..z1 (a dark
     * course at y1, glazing y2..y3 on every other column, panels), its cornice and roof at {@code top + 1}, the kit
     * portal in its front wall z0 (doors x 7..13) and a back door (x 9..11, y1..y3) in its back wall z1.
     */
    static void hut(ShapeKit.Shape s, int x0, int x1, int z0, int z1, int top) {
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
            for (int y = 1; y <= top; y++) {
                if (!edge) continue;
                s.set(x, y, z, y == 1 ? 'w' : (y == 2 || y == 3) && Math.floorMod(x - MID, 2) == 0 ? 'G' : 'W');
            }
            s.set(x, top + 1, z, edge ? 'n' : 'W');
        }
        ShapeKit.portal(s, z0, top + 1, false, MID);
        for (int x = MID - 1; x <= MID + 1; x++) for (int y = 1; y <= 3; y++) s.set(x, y, z1, ShapeKit.AIR);
    }

    /** The standing states {x, z, h} the generator's walk starts from: in the catwalk's mouth, on the deck. */
    public static List<int[]> walkStarts() {
        List<int[]> out = new ArrayList<>();
        out.add(new int[] { MID, 0, 2 });
        out.add(new int[] { MID - 1, 0, 2 });
        out.add(new int[] { MID + 1, 0, 2 });
        return out;
    }

    /**
     * The stage every sky module gives a cell alike ({@link #K_CLEAR} ..): COMMISSION the controller, FLOAT the
     * platform's floating foundation (y0) and its rail (the only rail a sky module has), SHELL the must-be-air; -1 for
     * a cell the module's own rule stages.
     */
    static int commonStage(int y, char ch) {
        if (ch == ShapeKit.CONTROLLER) return K_COMMISSION;
        if (y == 0) return K_FLOAT;
        if (y == 1 && ch == 'r') return K_FLOAT;
        if (ch == ShapeKit.AIR) return K_SHELL;
        return -1;
    }

    // ---- the datum and the controller

    /** The sky datum of a deck a module job builds at (the pod's, {@code datum} > 0), else the base layer's. */
    public static int datum(int y0, int datum) {
        return DomeGeneratorShape.isDatum(datum) ? datum : DomeGeneratorShape.datum(y0);
    }

    /**
     * The sky datum a fresh pod floats at over the base layer {@code y0} (0.16.0): the deck of the first sky module
     * that stands or is being laid ({@code standingDecks}, in the campus's order; anything no sky datum skipped), so
     * the new pod's floor and doors meet the catwalks already there (a sky module laid beside a pod built by hand on
     * the full legs over high ground keeps its deck at 40 after that pod is gone); with none, the datum the base layer
     * gives ({@link DomeGeneratorShape#datum}).
     */
    public static int podDatumFor(int y0, int[] standingDecks) {
        if (standingDecks != null) for (int d : standingDecks) if (DomeGeneratorShape.isDatum(d)) return d;
        return DomeGeneratorShape.datum(y0);
    }

    /**
     * Where a sky module's controller stands on its site at the sky datum F, and which way it faces: {x, y, z, facing
     * ordinal}. The middle column of the module's box stands on the platform's centre ({@link #centre}) with its deck
     * at
     * Y0 + F, its blueprint's front facing the pod (against the site's axis), and the controller lies where the module
     * puts it in that frame ({@code (10, 2, 1)}: radial 23, Y0 + F + 2; the hangar's {@code (10, 2, 2)}: radial 24).
     *
     * @throws IllegalArgumentException if the site is no module platform's, the front not a horizontal unit vector or
     *                                  F not a sky datum
     */
    public static int[] controller(BuildPlan.ModuleBuild m, int site, int cx, int y0, int cz, int fx, int fz,
        int datum) {
        BuildPlan.facingOrdinal(fx, fz);
        if (!platformSite(site)) throw new IllegalArgumentException("not a sky module's site: " + site);
        if (!DomeGeneratorShape.isDatum(datum)) throw new IllegalArgumentException("no sky datum " + datum);
        int[] ax = axis(site);
        int[] fw = CampusPlan.toWorld(-ax[0], -ax[1], fx, fz);
        int[] c = m.controller();
        // the controller lies (dx, dz) from the box's middle column in the shape's frame, which Blueprint.world turns
        // into the world as across (fz, -fx) and back (-fx, -fz)
        int dx = c[0] - (m.width() - 1) / 2, dz = c[2] - (m.depth() - 1) / 2;
        int ox = dx * fw[1] - dz * fw[0], oz = -dx * fw[0] - dz * fw[1];
        int[] centre = centre(site);
        int[] w = CampusPlan.toWorld(centre[0], centre[1], fx, fz);
        return new int[] { cx + w[0] + ox, y0 + datum + c[1], cz + w[1] + oz, BuildPlan.facingOrdinal(fw[0], fw[1]) };
    }

    // ---- the catwalk and the door

    /**
     * The catwalk of a module site ({@code catwalk(slot)} of an axis slot), {a, r, dy, ch} in the campus frame with dy
     * over the deck: row by row from radial 11 to 21, the deck's three cells ({@code .}, dy 0) then the two rails
     * ({@code |}, dy 1): 55 cells. The generator's checked {@code d} and {@code r} are built as their soft twins, never
     * checked: the catwalk belongs to no structure.
     *
     * @throws IllegalArgumentException if the site is no module platform's
     */
    public static List<int[]> catwalk(int site) {
        if (!platformSite(site)) throw new IllegalArgumentException("not a sky module's site: " + site);
        int[] ax = axis(site);
        List<int[]> out = new ArrayList<>();
        for (int t = CATWALK_FROM; t <= CATWALK_TO; t++) {
            for (int sx = -CATWALK_HALF; sx <= CATWALK_HALF; sx++)
                out.add(new int[] { t * ax[0] - sx * ax[1], t * ax[1] + sx * ax[0], 0, '.' });
            for (int sx : new int[] { -CATWALK_RAIL, CATWALK_RAIL })
                out.add(new int[] { t * ax[0] - sx * ax[1], t * ax[1] + sx * ax[0], 1, '|' });
        }
        return out;
    }

    /**
     * The pod's door on a module site's axis at the sky datum F ({@link DomeGeneratorShape#doorCells} of its slot),
     * {a, r, dy} in the campus frame with dy over the base layer: three columns at radial {@link #DOOR_RADIAL}, y1..y4
     * over the pod's floor, 12 cells.
     *
     * @throws IllegalArgumentException if the site is no module platform's or F not a sky datum
     */
    public static List<int[]> doorCells(int site, int datum) {
        if (!platformSite(site)) throw new IllegalArgumentException("not a sky module's site: " + site);
        DomeGeneratorShape.Moor moor = DomeGeneratorShape.moor(datum);
        List<int[]> out = new ArrayList<>();
        for (int[] c : DomeGeneratorShape.doorCells(slot(site))) out.add(moor.podToCampus(c[0], c[1], c[2]));
        return out;
    }

    /**
     * Whether the campus column (a, r) is where an open door's catwalk meets the pod's clear margin (0.16.0): the
     * catwalk's first row (radial {@link #CATWALK_FROM}, the deck and its rails) on the axis of a module site in the
     * mask
     * of sky slots {@code doors}. A floating pod's plan with those doors open clears these columns only from two over
     * its floor, over the catwalk's deck and rails.
     */
    public static boolean catwalkMouth(int doors, int a, int r) {
        for (int site = SITE0; site < SITE0 + SLOTS; site += 2) {
            if ((doors & bit(site)) == 0) continue;
            int[] ax = axis(site);
            int t = a * ax[0] + r * ax[1], s = -a * ax[1] + r * ax[0];
            if (t == CATWALK_FROM && Math.abs(s) <= CATWALK_RAIL) return true;
        }
        return false;
    }

    /**
     * The cells of the pod's protected box a sky site's job works (0.16.0): its door's twelve cells and its catwalk's
     * first row (the deck's three cells at the deck, the two rails a cell up), {x, y, z} in the world. The zenith's
     * protected boxes hold every other cell for it.
     *
     * @throws IllegalArgumentException if the site is no module platform's, the front not a horizontal unit vector or
     *                                  F not a sky datum
     */
    public static List<int[]> exempt(int site, int cx, int y0, int cz, int fx, int fz, int datum) {
        BuildPlan.facingOrdinal(fx, fz);
        List<int[]> out = new ArrayList<>();
        for (int[] c : doorCells(site, datum)) {
            int[] w = CampusPlan.toWorld(c[0], c[1], fx, fz);
            out.add(new int[] { cx + w[0], y0 + c[2], cz + w[1] });
        }
        int[] ax = axis(site);
        for (int[] c : catwalk(site)) {
            if (c[0] * ax[0] + c[1] * ax[1] != CATWALK_FROM) continue;
            int[] w = CampusPlan.toWorld(c[0], c[1], fx, fz);
            out.add(new int[] { cx + w[0], y0 + datum + c[2], cz + w[1] });
        }
        return out;
    }

    // ---- the envelope

    /**
     * A sky module's box seen from above, {x0, z0, x1, z1}: its platform's box and its catwalk, {@code margin} cells
     * round them (from radial {@code 11 - margin} to {@code 42 + margin} along its axis, {@code 10 + margin} either
     * side of it).
     *
     * @throws IllegalArgumentException if the site is no module platform's
     */
    public static int[] footprint(int site, int cx, int cz, int fx, int fz, int margin) {
        if (!platformSite(site)) throw new IllegalArgumentException("not a sky module's site: " + site);
        return box(site, cx, cz, fx, fz, CATWALK_FROM - margin, FRONT + SIZE - 1 + margin, MID + margin);
    }

    /**
     * The boxes {x0, y0, z0, x1, y1, z1} nothing but a sky module's own jobs takes a block down in: its platform's box
     * with {@link BuildPlan#MODULE_MARGIN} round it from a cell under its deck (Y0 + F - 1) to two cells over its top
     * layer ({@code top}), and its catwalk (radial 11 to 21, its deck and rails).
     *
     * @throws IllegalArgumentException if the site is no module platform's
     */
    public static List<int[]> protect(int site, int cx, int y0, int cz, int fx, int fz, int datum, int top) {
        if (!platformSite(site)) throw new IllegalArgumentException("not a sky module's site: " + site);
        int deck = y0 + datum, m = BuildPlan.MODULE_MARGIN;
        List<int[]> out = new ArrayList<>();
        int[] b = box(site, cx, cz, fx, fz, FRONT - m, FRONT + SIZE - 1 + m, MID + m);
        out.add(new int[] { b[0], deck - 1, b[1], b[2], deck + top + 2, b[3] });
        int[] w = box(site, cx, cz, fx, fz, CATWALK_FROM, CATWALK_TO, CATWALK_RAIL);
        out.add(new int[] { w[0], deck, w[1], w[2], deck + 1, w[3] });
        return out;
    }

    /**
     * Why a campus whose base layer is {@code y0} has no room for a sky module ({@code build.module.why.height}: its
     * controller or a unit cell would stand over the world's top at the deck the base layer gives), or null: never over
     * a base layer a core may be placed at (the highest deck is 253, the controllers and units at most two over it).
     */
    public static String noRoom(int y0) {
        return y0 + DomeGeneratorShape.datum(y0) + STAND_TOP > WORLD_TOP ? "build.module.why.height" : null;
    }

    /**
     * The highest y the clear and protected boxes of the ground modules under the sky ring take (the gatehouse on site
     * 0, the halls on 2, 4 and 6): two cells under the deck the base layer gives, Y0 + F - 2, so no sky module's job
     * ever meets their boxes and theirs never takes a platform's. Only the Dual-foil Vault's clear box reached so high
     * (Y0 + 29), at the sky datum 28 (Y0 >= 212).
     */
    public static int groundTop(int y0) {
        return y0 + DomeGeneratorShape.datum(y0) - GROUND_GAP;
    }

    /** The world box {x0, z0, x1, z1} from radial t0 to t1 along the site's axis, {@code half} cells either side. */
    private static int[] box(int site, int cx, int cz, int fx, int fz, int t0, int t1, int half) {
        int[] ax = axis(site);
        int x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
        for (int t : new int[] { t0, t1 }) for (int s : new int[] { -half, half }) {
            // along the axis t, across it s (clockwise): (a, r) = t * axis + s * (axis turned clockwise)
            int a = t * ax[0] - s * ax[1], r = t * ax[1] + s * ax[0];
            int[] d = CampusPlan.toWorld(a, r, fx, fz);
            x0 = Math.min(x0, cx + d[0]);
            z0 = Math.min(z0, cz + d[1]);
            x1 = Math.max(x1, cx + d[0]);
            z1 = Math.max(z1, cz + d[1]);
        }
        return new int[] { x0, z0, x1, z1 };
    }
}
