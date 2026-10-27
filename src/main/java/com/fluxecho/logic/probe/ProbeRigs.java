package com.fluxecho.logic.probe;

import java.util.ArrayList;
import java.util.List;

import com.fluxecho.logic.occlusion.GatePiece;
import com.fluxecho.logic.skin.SkinEntry;

/**
 * Where the three probes that set blocks put them, and where the client draws their light (0.17.0, refactor FINAL F06
 * and §7.1), in the {@link ProbeFrame} of the operator who set them out. The server and the client read the same
 * cells.
 * <ul>
 * <li>{@link Depth}, P0: a hull wall eight cells ahead with a two-by-two window; behind it a piece drawn with the depth
 * test (seen only if the shader pack's depth buffer is not the world's), a piece hidden by the occlusion gate, and one
 * behind the window the gate lets through; before it a piece with the depth test and one without.</li>
 * <li>{@link Sun}: a hull mast with a line from its top to the sun, a footing light well with a line from its floor, a
 * sail turned about the north-south axis with its normal and the true line to the sun.</li>
 * <li>{@link Bed}, S-P4: rows of planting beds (fitting 14) ahead, one Botania flower on each.</li>
 * </ul>
 */
public final class ProbeRigs {

    private ProbeRigs() {}

    /** A piece's id: its rig's sign and its number in the rig. */
    public static long pieceId(int[] sign, int index) {
        return SkinEntry.pack(sign[0], sign[1], sign[2]) * 31 + index;
    }

    /** A box {x0, y0, z0, x1, y1, z1} of the frame's cells from (along, right, up) to (along, right, up), inclusive. */
    public static int[] box(ProbeFrame f, int a0, int r0, int u0, int a1, int r1, int u1) {
        int[] p = f.at(a0, r0, u0), q = f.at(a1, r1, u1);
        return new int[] { Math.min(p[0], q[0]), Math.min(p[1], q[1]), Math.min(p[2], q[2]), Math.max(p[0], q[0]),
            Math.max(p[1], q[1]), Math.max(p[2], q[2]) };
    }

    /** P0: the wall and its five pieces. */
    public static final class Depth {

        /** The wall: this far ahead, from right {@link #LEFT} to {@link #RIGHT}, up to {@link #TOP}. */
        public static final int WALL = 8, LEFT = -4, RIGHT = 4, TOP = 4;
        /** The window: right {@link #WINDOW_LEFT} and the next, up 1 and 2. */
        public static final int WINDOW_LEFT = 2, WINDOW_LOW = 1, WINDOW_HIGH = 2;
        /** The volume cleared to air before the wall is set: ahead 2 to 11, right -5 to 5, up 0 to 5. */
        public static final int[] VOLUME = { 2, -5, 0, 11, 5, 5 };
        /** The pieces' half size. */
        public static final double HALF = 0.75;

        /** What a piece is: drawn with the depth test on or off, through the gate or not. */
        public enum Piece {

            /** Before the wall, depth test on: always seen (a depth buffer full of rubbish would hide it). */
            FRONT_DEPTH(5.5, -3, 0.75, true, false),
            /** Before the wall, depth test off: always seen. */
            FRONT_PLAIN(5.5, 0, 0.75, false, false),
            /** Behind the wall, depth test on, no gate: seen only if the depth buffer is not the world's (P0 fails). */
            BACK_DEPTH(10.5, -3, 3, true, false),
            /** Behind the wall, depth test off, through the gate: the gate hides it. */
            BACK_GATE(10.5, 0, 3, false, true),
            /** Behind the window, depth test off, through the gate: the gate lets it through the glass. */
            BACK_WINDOW(10.5, 3.5, 2, false, true);

            /** Its centre in the frame. */
            public final double along, right, up;
            public final boolean depthTest, gated;

            Piece(double along, double right, double up, boolean depthTest, boolean gated) {
                this.along = along;
                this.right = right;
                this.up = up;
                this.depthTest = depthTest;
                this.gated = gated;
            }

            /** Whether it stands behind the wall. */
            public boolean behind() {
                return along > WALL;
            }
        }

        private Depth() {}

        /** Whether the wall's cell at (right, up) is the window. */
        public static boolean window(int right, int up) {
            return right >= WINDOW_LEFT && right <= WINDOW_LEFT + 1 && up >= WINDOW_LOW && up <= WINDOW_HIGH;
        }

        /** A piece's centre in the world. */
        public static double[] centre(ProbeFrame f, Piece p) {
            return f.at(p.along, p.right, p.up);
        }

        /** A piece as the gate sees it: a panel facing the operator, carried by the wall. */
        public static GatePiece gatePiece(ProbeFrame f, int[] sign, Piece p) {
            double[] c = centre(f, p), u = { f.rx() * HALF, 0, f.rz() * HALF }, v = { 0, HALF, 0 };
            return GatePiece.panel(pieceId(sign, p.ordinal()), c, u, v)
                .host(box(f, WALL, LEFT, 0, WALL, RIGHT, TOP));
        }
    }

    /** The sun probe's three anchors. */
    public static final class Sun {

        /** The mast: a hull column this far ahead, this tall. */
        public static final int MAST_ALONG = 6, MAST_HEIGHT = 12;
        /** The light well: a footing ring round (ahead, right), this tall. */
        public static final int WELL_ALONG = 6, WELL_RIGHT = 8, WELL_HEIGHT = 5;
        /** The sail: its centre (ahead, right, up) and half size along the north-south axis and across. */
        public static final double SAIL_ALONG = 6, SAIL_RIGHT = -8, SAIL_UP = 7, SAIL_HALF_AXIS = 1.5,
            SAIL_HALF_ACROSS = 2.5;
        /** How long the lines are: from the mast top, from the well floor, the sail's normal and its sun line. */
        public static final double MAST_LINE = 48, WELL_LINE = 24, SAIL_NORMAL = 8, SAIL_LINE = 20;
        /** The volume cleared to air: ahead 2 to 9, right -11 to 11, up 0 to 12. */
        public static final int[] VOLUME = { 2, -11, 0, 9, 11, 12 };

        private Sun() {}

        /** The mast's cells (ahead, right, up). */
        public static List<int[]> mast() {
            List<int[]> out = new ArrayList<>();
            for (int u = 0; u < MAST_HEIGHT; u++) out.add(new int[] { MAST_ALONG, 0, u });
            return out;
        }

        /** The well's ring cells (ahead, right, up): the eight round its open middle, {@link #WELL_HEIGHT} high. */
        public static List<int[]> well() {
            List<int[]> out = new ArrayList<>();
            for (int u = 0; u < WELL_HEIGHT; u++) for (int a = -1; a <= 1; a++) for (int r = -1; r <= 1; r++)
                if (a != 0 || r != 0) out.add(new int[] { WELL_ALONG + a, WELL_RIGHT + r, u });
            return out;
        }

        /** Where the mast's line starts: the middle of the top of the mast. */
        public static double[] mastTop(ProbeFrame f) {
            return f.at((double) MAST_ALONG, 0.0, (double) MAST_HEIGHT);
        }

        /** Where the well's line starts: the middle of its floor. */
        public static double[] wellFloor(ProbeFrame f) {
            return f.at((double) WELL_ALONG, (double) WELL_RIGHT, 0.05);
        }

        /** The sail's centre. */
        public static double[] sail(ProbeFrame f) {
            return f.at(SAIL_ALONG, SAIL_RIGHT, SAIL_UP);
        }
    }

    /** The bed probe's rows. */
    public static final class Bed {

        /** Beds a row, the first row's distance ahead, the step between rows (a walkway between). */
        public static final int PER_ROW = 16, FIRST = 3, STEP = 2;
        /** The first bed's offset to the right. */
        public static final int LEFT = -8;

        private Bed() {}

        /** Where bed {@code i} stands (ahead, right, up 0); its flower goes one higher. */
        public static int[] bed(int i) {
            return new int[] { FIRST + STEP * (i / PER_ROW), LEFT + i % PER_ROW, 0 };
        }

        /** The volume cleared to air for {@code n} beds: their rows, three cells high over them, and the walkways. */
        public static int[] volume(int n) {
            int rows = Math.max(1, (n + PER_ROW - 1) / PER_ROW);
            return new int[] { FIRST, LEFT, 0, FIRST + STEP * (rows - 1), LEFT + PER_ROW - 1, 3 };
        }
    }
}
