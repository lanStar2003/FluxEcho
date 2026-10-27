package com.fluxecho.logic.shape;

import java.util.List;

/**
 * The shapes that are whole cubes: the whole-block shapes (whole, seam, light seam, keel, lamp, floor, relief; the
 * footing's pool lip, spout and planter lip) and the see-through window and grate. Their geometry is the cell's six
 * faces; what tells them apart is the texture, and the overlays and continuation bits this class gives their faces
 * (style FINAL §3.2): the light seam's line and the keel's strip on side faces (the keel's top cell ends in a point),
 * the lamp's glowing panel, the pool lip's lip on its top and toward water, the spout's outlet toward the open, and
 * which in-plane neighbours continue a window, lamp, light seam or keel so the frame or line runs on.
 */
final class CubeGeo extends Geo {

    CubeGeo(Form form, Nbhd n) {
        super(form, n);
    }

    @Override
    List<Piece> section(int face) {
        return wholeFace(face);
    }

    @Override
    void interior(Mesh m) {}

    @Override
    Overlay overlay(int face) {
        boolean side = Dir.ringOf(face) >= 0;
        switch (form) {
            case LIGHT_SEAM:
                return side ? Overlay.LINE : Overlay.NONE;
            case KEEL:
                return side ? sameAt(Dir.UP) ? Overlay.KEEL : Overlay.KEEL_TIP : Overlay.NONE;
            case LAMP:
                return side ? Overlay.LAMP : Overlay.NONE;
            case POOL_LIP:
                return face == Dir.UP || side && waterAt(face) ? Overlay.LIP : Overlay.NONE;
            case SPOUT:
                return side && airAt(face) ? Overlay.SPOUT : Overlay.NONE;
            default:
                return Overlay.NONE;
        }
    }

    @Override
    int conn(int face) {
        switch (form) {
            case WINDOW:
            case GRATE:
            case LAMP:
            case LIGHT_SEAM:
            case KEEL:
                return connBits(face, this::sameAt);
            case POOL_LIP:
                return face == Dir.UP ? connBits(face, this::waterAt) : 0;
            default:
                return 0;
        }
    }

    private boolean waterAt(int face) {
        return n.water(Dir.FX[face], Dir.FY[face], Dir.FZ[face]);
    }

    /** A test on the cell across a face. */
    interface FaceTest {

        boolean at(int face);
    }

    /**
     * The continuation bits of face {@code face} ({@link Quad#conn}): which of the four cells beside it in its plane
     * pass {@code test}, in the face's texture frame (bit 1 toward -u, 2 toward +u, 4 toward -v, 8 toward +v).
     */
    static int connBits(int face, FaceTest test) {
        int[] axes = Mesh.uvAxes(new double[] { Dir.FX[face], Dir.FY[face], Dir.FZ[face] });
        int bits = 0;
        for (int f = 0; f < 6; f++) {
            if (f == face || f == Dir.OPP[face] || !test.at(f)) continue;
            int axis = f == Dir.EAST || f == Dir.WEST ? 1 : f == Dir.UP || f == Dir.DOWN ? 2 : 3;
            int sign = f == Dir.EAST || f == Dir.UP || f == Dir.SOUTH ? 1 : -1;
            if (Math.abs(axes[0]) == axis) bits |= sign * Integer.signum(axes[0]) > 0 ? 2 : 1;
            else bits |= sign * Integer.signum(axes[1]) > 0 ? 8 : 4;
        }
        return bits;
    }
}
