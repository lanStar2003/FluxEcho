package com.fluxecho.logic.sky;

import java.util.ArrayList;
import java.util.List;

/**
 * The first aurora (0.17.0 prototype N0, narrative FINAL §9.1 E5, §9.2 N0: "the first aurora of the first full-moon
 * night after phase II", at most {@value #QUADS} quads): two curtains of light, each {@value #SEGMENTS} segments along
 * and {@value #BANDS} bands up (a bright green lower band with a sharp foot, a violet upper band fading out), waving
 * slowly and rippling in brightness. A pure function of the time: the same time gives the same quads, however many
 * times a frame draws it (behind light gates too).
 * <p>
 * The aurora is made in its own frame (u along a curtain 0 to 1, v up 0 to 1, a wave across) and set into the world by
 * a {@link Placement}: the sky layer's ({@link SkySheet#sky}: a sheet that keeps its distance from the camera, inside
 * the far plane, like the sun) or the world-fixed one ({@link SkySheet#world}: a curtain hung at a fixed place, the
 * default, {@code render.skyLayer = world}). Every vertex carries a normal, towards the camera, so a shader pack lights
 * it as a glow.
 */
public final class Aurora {

    public static final int CURTAINS = 2, SEGMENTS = 24, BANDS = 2;
    /** The quads of one aurora: its whole budget. */
    public static final int QUADS = CURTAINS * SEGMENTS * BANDS;
    /** The heights (v) of the band edges: the foot, the brightest line, the top. */
    static final double[] LEVELS = { 0, 0.3, 1 };
    /** The colours at the foot, the brightest line and the top. */
    public static final int FOOT = 0x6CFFB0, BRIGHT = 0x9BFFC8, TOP = 0xC9B8FF;
    /** The alphas at the foot, the brightest line and the top (times the ripple and the fade at the ends). */
    static final float[] ALPHA = { 0.35f, 0.8f, 0f };

    /** Where the aurora's frame lies in the world (or relative to the camera). */
    public interface Placement {

        /**
         * The point of curtain {@code curtain} at {@code u} along, {@code v} up, pushed {@code wave} (-1 to 1) across.
         */
        double[] point(int curtain, double u, double v, double wave);
    }

    private Aurora() {}

    /**
     * The quads at time {@code t} (ticks, with the partial tick), each four vertices {x, y, z, nx, ny, nz, rgb, alpha},
     * with the normals pointing from the vertex to {@code camera} (in the placement's coordinates).
     */
    public static List<double[][]> quads(double t, Placement place, double[] camera) {
        List<double[][]> out = new ArrayList<>(QUADS);
        for (int c = 0; c < CURTAINS; c++) for (int s = 0; s < SEGMENTS; s++) {
            double u0 = s / (double) SEGMENTS, u1 = (s + 1) / (double) SEGMENTS;
            for (int b = 0; b < BANDS; b++) {
                double v0 = LEVELS[b], v1 = LEVELS[b + 1];
                out.add(
                    new double[][] { vertex(t, place, camera, c, u0, v0, b), vertex(t, place, camera, c, u1, v0, b),
                        vertex(t, place, camera, c, u1, v1, b + 1), vertex(t, place, camera, c, u0, v1, b + 1) });
            }
        }
        return out;
    }

    private static double[] vertex(double t, Placement place, double[] camera, int c, double u, double v, int level) {
        double[] p = place.point(c, u, v, wave(t, c, u));
        double nx = camera[0] - p[0], ny = camera[1] - p[1], nz = camera[2] - p[2];
        double n = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (n < 1e-9) {
            nx = 0;
            ny = 1;
            nz = 0;
            n = 1;
        }
        int rgb = level == 0 ? FOOT : level == 1 ? BRIGHT : TOP;
        double a = ALPHA[level] * ripple(t, c, u) * Math.sin(Math.PI * u);
        return new double[] { p[0], p[1], p[2], nx / n, ny / n, nz / n, rgb, Math.max(0, Math.min(1, a)) };
    }

    /** How far the curtain is pushed across at {@code u}, -1 to 1: two slow waves. */
    static double wave(double t, int c, double u) {
        return 0.6 * Math.sin(u * 3 * Math.PI + t * 0.015 + c * 1.3) + 0.4 * Math.sin(u * 7 - t * 0.03 + c);
    }

    /** The brightness along the curtain, 0.2 to 1: rays rippling sideways. */
    static double ripple(double t, int c, double u) {
        double a = 0.5 + 0.5 * Math.sin(u * 11 + t * 0.045 + c * 1.7), b = 0.6 + 0.4 * Math.sin(u * 3.1 - t * 0.02);
        return 0.2 + 0.8 * a * b;
    }
}
