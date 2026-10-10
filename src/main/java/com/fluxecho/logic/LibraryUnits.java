package com.fluxecho.logic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The shelf units of the Echo Archive ({@link ArchiveShape}). A unit is one bookcase: three columns of shelf bodies
 * three high between two posts, with a plinth below and a crown above, holding nine books. Players use a unit as a
 * whole: clicking any part of it (or a post, by the side clicked) selects the unit, and a sample is filed into its
 * first free place.
 * <p>
 * Units are found in the blueprint the way the design generator found them (a run of three bodies after a post, at
 * the middle body row of each storey, along either axis), then put in a fixed reading order: the ground storey first
 * (front wall; the ranges from the door backwards, left before right, the face toward the door before the back face,
 * the nave-side unit before the wall-side one; the side walls; the back wall), then the upper storey in the same
 * order. Each unit gets a call number such as {@code G-L3a-2} (storey, zone, range and face, number within the run).
 * <p>
 * A unit's book face is the horizontal side whose neighbour is not a post, body, wall or glaze. Slots number
 * {@code unit * 9 + row * 3 + col} with row 0 at the top and col 0 at the left of someone facing the books. Because
 * the Archive's local frame is mirrored relative to the world (see {@link ArchiveShape}), "left" here is computed in
 * that frame's terms, so it is the viewer's real left once the shape is placed.
 */
public final class LibraryUnits {

    /** One bookcase of the Archive. */
    public static final class Unit {

        /** The unit's place in {@link #UNITS}; its slots are {@code index * 9 .. index * 9 + 8}. */
        public final int index;
        /** 0 for the ground storey (bodies y2..4), 1 for the upper storey (bodies y8..10). */
        public final int storey;
        /** Whether the three bodies run along x (else along z). */
        public final boolean alongX;
        /** The first body column: the lowest x (or z) of the three bodies, and the other coordinate. */
        public final int x0, z0;
        /** The lowest body row (2 or 8); the plinth is one below it, the crown three above. */
        public final int yBody;
        /** The local direction the book face looks (a horizontal unit vector across the run). */
        public final int faceX, faceZ;
        /** The call number, such as {@code G-L3a-2}. */
        public final String call;

        Unit(int index, int storey, boolean alongX, int x0, int z0, int yBody, int faceX, int faceZ, String call) {
            this.index = index;
            this.storey = storey;
            this.alongX = alongX;
            this.x0 = x0;
            this.z0 = z0;
            this.yBody = yBody;
            this.faceX = faceX;
            this.faceZ = faceZ;
            this.call = call;
        }

        /** The x of the body column {@code along} (0..2) cells along the run from the first. */
        public int x(int along) {
            return alongX ? x0 + along : x0;
        }

        /** The z of the body column {@code along} (0..2) cells along the run from the first. */
        public int z(int along) {
            return alongX ? z0 : z0 + along;
        }

        /** Whether column 0 (the viewer's left) is the lowest coordinate along the run. */
        boolean leftIsLow() {
            // the viewer looks along -face; in the blueprint frame their left is (faceZ, -faceX)
            return alongX ? faceZ < 0 : faceX > 0;
        }

        @Override
        public String toString() {
            return call + "#"
                + index
                + "("
                + (alongX ? "x" : "z")
                + " "
                + x0
                + ","
                + yBody
                + ","
                + z0
                + " face "
                + faceX
                + ","
                + faceZ
                + ")";
        }
    }

    /** Every unit, in reading order. */
    public static final List<Unit> UNITS;
    /** Nine places per unit. */
    public static final int SLOTS = 702;

    private static final int W = ArchiveShape.WIDTH, H = ArchiveShape.HEIGHT, D = ArchiveShape.DEPTH;
    /** The unit of every plinth, body and crown cell, or -1. */
    private static final int[] CELL_UNIT = new int[W * H * D];

    static {
        UNITS = Collections.unmodifiableList(build());
        if (UNITS.size() * 9 != SLOTS) throw new IllegalStateException("the Archive has " + UNITS.size() + " units");
    }

    private LibraryUnits() {}

    /** The unit a body, plinth or crown cell belongs to, or -1 (posts, everything else, outside the box). */
    public static int unitOfCell(int x, int y, int z) {
        if (x < 0 || x >= W || y < 0 || y >= H || z < 0 || z >= D) return -1;
        return CELL_UNIT[index(x, y, z)];
    }

    /**
     * The unit beside a post on the given side along the post's run ({@code -1} toward lower x or z, {@code +1}
     * toward higher), or -1 when the cell is not a post or no unit stands on that side.
     */
    public static int unitOfPost(int x, int y, int z, int side) {
        if (side != -1 && side != 1 || ArchiveShape.cell(x, y, z) != ArchiveShape.POST) return -1;
        int u = unitOfCell(x + side, y, z);
        if (u >= 0 && UNITS.get(u).alongX) return u;
        u = unitOfCell(x, y, z + side);
        if (u >= 0 && !UNITS.get(u).alongX) return u;
        return -1;
    }

    /**
     * The slot of a body cell of a unit: {@code unit * 9 + row * 3 + col}, row 0 at the top, col 0 at the left of
     * someone facing the books; -1 when the cell is not one of that unit's bodies.
     */
    public static int slot(int unit, int x, int y, int z) {
        if (unit < 0 || unit >= UNITS.size()) return -1;
        Unit u = UNITS.get(unit);
        if (y < u.yBody || y > u.yBody + 2) return -1;
        int along;
        if (u.alongX) {
            if (z != u.z0 || x < u.x0 || x > u.x0 + 2) return -1;
            along = x - u.x0;
        } else {
            if (x != u.x0 || z < u.z0 || z > u.z0 + 2) return -1;
            along = z - u.z0;
        }
        int row = u.yBody + 2 - y, col = u.leftIsLow() ? along : 2 - along;
        return unit * 9 + row * 3 + col;
    }

    /** The body cell of a slot, {x, y, z}, or null when the slot is out of range. */
    public static int[] cellOfSlot(int slot) {
        if (slot < 0 || slot >= SLOTS) return null;
        Unit u = UNITS.get(slot / 9);
        int row = slot % 9 / 3, col = slot % 3;
        int along = u.leftIsLow() ? col : 2 - col;
        return new int[] { u.x(along), u.yBody + 2 - row, u.z(along) };
    }

    // ---------------------------------------------------------------------------------------------------------------

    private static int index(int x, int y, int z) {
        return (y * D + z) * W + x;
    }

    /** A unit found in the blueprint, before it is put in order. */
    private static final class Found {

        int storey, x0, z0, yBody, faceX, faceZ, rank, range, side, face, nave;
        boolean alongX;
        String zone;
    }

    private static List<Unit> build() {
        List<Found> found = new ArrayList<>();
        for (int yb : new int[] { 3, 9 }) {
            boolean[] seen = new boolean[W * D];
            for (int x = 0; x < W; x++) for (int z = 0; z < D; z++) {
                if (ArchiveShape.cell(x, yb, z) != ArchiveShape.BODY || seen[z * W + x]) continue;
                Found f = new Found();
                if (body(x + 1, yb, z) && body(x + 2, yb, z) && post(x - 1, yb, z)) f.alongX = true;
                else if (body(x, yb, z + 1) && body(x, yb, z + 2) && post(x, yb, z - 1)) f.alongX = false;
                else throw new IllegalStateException("a body outside any unit at " + x + "," + yb + "," + z);
                for (int k = 0; k < 3; k++) seen[f.alongX ? z * W + x + k : (z + k) * W + x] = true;
                f.storey = yb == 3 ? 0 : 1;
                f.x0 = x;
                f.z0 = z;
                f.yBody = yb - 1;
                check(f);
                face(f);
                classify(f);
                found.add(f);
            }
        }
        Collections.sort(found, (a, b) -> {
            int[] ka = { a.storey, a.rank, a.range, a.side, a.face, a.nave, a.x0, a.z0 };
            int[] kb = { b.storey, b.rank, b.range, b.side, b.face, b.nave, b.x0, b.z0 };
            for (int i = 0; i < ka.length; i++) if (ka[i] != kb[i]) return Integer.compare(ka[i], kb[i]);
            return 0;
        });
        Arrays.fill(CELL_UNIT, -1);
        Map<String, Integer> numbers = new HashMap<>();
        List<Unit> out = new ArrayList<>();
        for (Found f : found) {
            String run = (f.storey == 0 ? "G" : "U") + "-"
                + f.zone
                + (f.range > 0 ? f.range + (f.face == 0 ? "a" : "b") : "");
            int n = numbers.merge(run, 1, Integer::sum);
            Unit u = new Unit(out.size(), f.storey, f.alongX, f.x0, f.z0, f.yBody, f.faceX, f.faceZ, run + "-" + n);
            for (int k = 0; k < 3; k++) for (int y = u.yBody - 1; y <= u.yBody + 3; y++) {
                int i = index(u.x(k), y, u.z(k));
                if (CELL_UNIT[i] >= 0) {
                    throw new IllegalStateException("two units share " + u.x(k) + "," + y + "," + u.z(k));
                }
                CELL_UNIT[i] = u.index;
            }
            out.add(u);
        }
        return out;
    }

    private static boolean body(int x, int y, int z) {
        return ArchiveShape.cell(x, y, z) == ArchiveShape.BODY;
    }

    private static boolean post(int x, int y, int z) {
        return ArchiveShape.cell(x, y, z) == ArchiveShape.POST;
    }

    /** Throws unless the unit is whole: plinths, three rows of bodies and crowns between posts at both ends. */
    private static void check(Found f) {
        int dx = f.alongX ? 1 : 0, dz = f.alongX ? 0 : 1;
        for (int y = f.yBody - 1; y <= f.yBody + 3; y++) {
            char want = y == f.yBody - 1 ? ArchiveShape.PLINTH
                : y == f.yBody + 3 ? ArchiveShape.CROWN : ArchiveShape.BODY;
            for (int k = 0; k < 3; k++) {
                if (ArchiveShape.cell(f.x0 + dx * k, y, f.z0 + dz * k) != want) {
                    throw new IllegalStateException("broken unit at " + f.x0 + "," + y + "," + f.z0);
                }
            }
            if (!post(f.x0 - dx, y, f.z0 - dz) || !post(f.x0 + 3 * dx, y, f.z0 + 3 * dz)) {
                throw new IllegalStateException("unit without posts at " + f.x0 + "," + y + "," + f.z0);
            }
        }
    }

    /** Whether a neighbour hides a book face: a post, a body, a wall or glaze. */
    private static boolean covers(char ch) {
        return ch == ArchiveShape.POST || ch == ArchiveShape.BODY
            || ch == ArchiveShape.PANEL
            || ch == ArchiveShape.PILASTER
            || ch == ArchiveShape.COURSE
            || ch == ArchiveShape.CORNICE
            || ch == ArchiveShape.GLAZE;
    }

    /** Finds the one open side shared by all nine bodies, and throws if there is not exactly one. */
    private static void face(Found f) {
        int[][] dirs = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        int open = -1;
        for (int k = 0; k < 3; k++) for (int y = f.yBody; y <= f.yBody + 2; y++) {
            int x = f.alongX ? f.x0 + k : f.x0, z = f.alongX ? f.z0 : f.z0 + k;
            int here = -1;
            for (int d = 0; d < dirs.length; d++) {
                if (covers(ArchiveShape.cell(x + dirs[d][0], y, z + dirs[d][1]))) continue;
                if (here >= 0) throw new IllegalStateException("a body with two faces at " + x + "," + y + "," + z);
                here = d;
            }
            if (here < 0 || open >= 0 && open != here) {
                throw new IllegalStateException("no single face for the unit at " + f.x0 + "," + y + "," + f.z0);
            }
            open = here;
        }
        f.faceX = dirs[open][0];
        f.faceZ = dirs[open][1];
    }

    /** Sets the zone, range, side, face and nave keys that order and name the unit. */
    private static void classify(Found f) {
        f.side = f.x0 < ArchiveShape.MID ? 0 : 1;
        if (!f.alongX) {
            f.zone = f.side == 0 ? "WL" : "WR";
            f.rank = 2;
        } else if (f.z0 == 1) {
            f.zone = "F";
            f.rank = 0;
        } else if (f.z0 == D - 2) {
            f.zone = "B";
            f.rank = 3;
        } else {
            for (int r = 0; r < ArchiveShape.RANGES.length; r++) {
                if (f.z0 == ArchiveShape.RANGES[r][0] || f.z0 == ArchiveShape.RANGES[r][1]) f.range = r + 1;
            }
            if (f.range == 0) throw new IllegalStateException("a unit outside the ranges at z" + f.z0);
            f.zone = f.side == 0 ? "L" : "R";
            f.rank = 1;
            f.face = f.faceZ < 0 ? 0 : 1;
            f.nave = Math.abs(f.x0 + 1 - ArchiveShape.MID);
        }
    }
}
