package com.fluxecho.logic.occlusion;

/**
 * One piece of the light a building draws after the world (style FINAL §8.4.1: a sail strip, an arc, a patch of a
 * sphere, a membrane, a silhouette, a droplet), as the occlusion gate judges it: an id the renderer asks by, its centre
 * and one to five sample points (the centre and the outer corners), optionally the box of the building that carries it
 * (the coarse test: a line that crosses no building's box and no ground is seen without a cast), optionally an interior
 * box (rule #12: an indoor piece is drawn only while the camera is inside that box, and casts nothing).
 * Immutable.
 */
public final class GatePiece {

    /** The renderer's name for the piece: the same id every round. */
    public final long id;
    /** The sample points {x, y, z}, the centre first. */
    public final double[][] samples;
    /** The box {x0, y0, z0, x1, y1, z1} (inclusive cells) of what carries it, or null. */
    public final int[] host;
    /** For an indoor piece, the box the camera must be in, or null. */
    public final int[] interior;

    private GatePiece(long id, double[][] samples, int[] host, int[] interior) {
        if (samples.length < 1 || samples.length > 5)
            throw new IllegalArgumentException("a piece has 1 to 5 sample points, not " + samples.length);
        this.id = id;
        this.samples = new double[samples.length][];
        for (int i = 0; i < samples.length; i++) this.samples[i] = samples[i].clone();
        this.host = host == null ? null : host.clone();
        this.interior = interior == null ? null : interior.clone();
    }

    /** A piece seen by its sample points, {@code samples[0]} its centre. */
    public static GatePiece of(long id, double[]... samples) {
        return new GatePiece(id, samples, null, null);
    }

    /** The same piece carried by a building whose box is {@code host}. */
    public GatePiece host(int[] box) {
        return new GatePiece(id, samples, box, interior);
    }

    /** An indoor piece: drawn only while the camera stands in {@code box}. */
    public static GatePiece indoor(long id, int[] box, double[] centre) {
        return new GatePiece(id, new double[][] { centre }, null, box);
    }

    /**
     * A flat rectangular piece: its centre and its four corners, {@code centre ± u ± v} ({@code u}, {@code v} the half
     * extents along its two sides).
     */
    public static GatePiece panel(long id, double[] centre, double[] u, double[] v) {
        double[][] s = new double[5][];
        s[0] = centre.clone();
        int i = 1;
        for (int a = -1; a <= 1; a += 2) for (int b = -1; b <= 1; b += 2) s[i++] = new double[] {
            centre[0] + a * u[0] + b * v[0], centre[1] + a * u[1] + b * v[1], centre[2] + a * u[2] + b * v[2] };
        return new GatePiece(id, s, null, null);
    }

    /** The distance from {@code eye} to the centre. */
    public double distance(double[] eye) {
        double dx = samples[0][0] - eye[0], dy = samples[0][1] - eye[1], dz = samples[0][2] - eye[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
