package com.fluxecho.logic.shape;

/**
 * The cell's six faces, its ring of four horizontal sides, and the trigonometry every shape shares.
 * <p>
 * Faces are numbered in Forge's {@code ForgeDirection} order (down, up, north, south, west, east) so a renderer can
 * hand a quad's face straight to the block's side tests. The horizontal ring lists the sides in turning order (east,
 * south, west, north): a quarter turn about +y takes ring side {@code k} to {@code k + 1}, and corner {@code k} lies
 * between ring sides {@code k} and {@code k + 1}. Every shape builds its geometry in the frame of a corner or a side
 * of this ring, so a neighbourhood turned a quarter gives the same quads turned a quarter (ShapeGeoTest's rotation
 * check). Arcs are cut into four segments of 22.5 degrees whose cosines and sines come from one table, exact at 0 and
 * 90 degrees, so two pieces that share an arc share its vertices bit for bit.
 */
final class Dir {

    /** The faces in {@code ForgeDirection} order. */
    static final int DOWN = 0, UP = 1, NORTH = 2, SOUTH = 3, WEST = 4, EAST = 5;
    /** Each face's unit offset. */
    static final int[] FX = { 0, 0, 0, 0, -1, 1 }, FY = { -1, 1, 0, 0, 0, 0 }, FZ = { 0, 0, -1, 1, 0, 0 };
    /** Each face's opposite. */
    static final int[] OPP = { 1, 0, 3, 2, 5, 4 };

    /** The ring of horizontal sides in turning order, as faces and as offsets. */
    static final int[] RING = { EAST, SOUTH, WEST, NORTH };
    private static final int[] RX = { 1, 0, -1, 0 }, RZ = { 0, 1, 0, -1 };

    /** The radius every rounding uses: half a cell. */
    static final double R = 0.5;
    /** cos and sin of {@code i * 22.5} degrees for {@code i = 0..4}, exact at both ends and mirrored in the middle. */
    static final double[] C = { 1, Math.cos(Math.PI / 8), Math.sqrt(0.5), Math.sin(Math.PI / 8), 0 },
        S = { 0, Math.sin(Math.PI / 8), Math.sqrt(0.5), Math.cos(Math.PI / 8), 1 };
    /** One arc segment in radians. */
    static final double STEP = Math.PI / 8;

    private Dir() {}

    /** Ring side {@code k} (any integer, taken modulo 4) as a face. */
    static int ring(int k) {
        return RING[k & 3];
    }

    /** The x offset of ring side {@code k}. */
    static int rx(int k) {
        return RX[k & 3];
    }

    /** The z offset of ring side {@code k}. */
    static int rz(int k) {
        return RZ[k & 3];
    }

    /** The ring index of a horizontal face, or -1 for up and down. */
    static int ringOf(int face) {
        switch (face) {
            case EAST:
                return 0;
            case SOUTH:
                return 1;
            case WEST:
                return 2;
            case NORTH:
                return 3;
            default:
                return -1;
        }
    }

    /**
     * A point in local cell coordinates (0..1) from centred coordinates: {@code u} along ring side {@code ka},
     * {@code w} along ring side {@code kb}, {@code h} up.
     */
    static double[] pt(int ka, double u, int kb, double w, double h) {
        return new double[] { 0.5 + u * rx(ka) + w * rx(kb), 0.5 + h, 0.5 + u * rz(ka) + w * rz(kb) };
    }

    /** A direction from components along ring sides {@code ka}, {@code kb} and up, not normalised. */
    static double[] vec(int ka, double a, int kb, double b, double up) {
        return new double[] { a * rx(ka) + b * rx(kb), up, a * rz(ka) + b * rz(kb) };
    }

    /** The unit vector of a direction. */
    static double[] unit(double[] v) {
        double l = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return new double[] { v[0] / l, v[1] / l, v[2] / l };
    }
}
