package com.fluxecho.logic.sky;

/**
 * The two ways a light of the sky (the aurora first) is set into the picture (0.17.0, prototype N0, refactor FINAL
 * F06, facts render §4.5), {@code render.skyLayer}:
 * <ul>
 * <li>{@link #sky}: the sky layer, a sheet that travels with the camera at a fixed distance inside the far plane, like
 * vanilla's sun and moon, so it hangs in the sky at any distance from where it belongs and needs no chunk loaded. The
 * prototype: whether a shader pack keeps it bright, fogs it, and draws the clouds in front of or behind it.</li>
 * <li>{@link #world}: the world-fixed curtain, the default that works with every pack: far geometry hung at a fixed
 * place, seen with parallax, cut by the far plane when the player is far away.</li>
 * </ul>
 * Both are {@link Aurora.Placement}s of the same aurora. The sky layer's coordinates are relative to the camera (it is
 * drawn at the camera, its translation ignored), the world curtain's are world coordinates.
 */
public final class SkySheet {

    /** How far the sky layer lies, as a share of the far clipping distance. */
    public static final double SHARE = 0.45;
    /** The sky layer's half span along the horizon and its foot and top elevation, degrees. */
    public static final double HALF_SPAN = 50, FOOT_ELEVATION = 18, TOP_ELEVATION = 50;
    /** The second curtain's offset: degrees round and up, and how much nearer (share of the radius). */
    static final double SECOND_TURN = 12, SECOND_UP = 4, SECOND_NEAR = 0.04;
    /** How far the wave pushes a curtain in and out (share of the radius). */
    static final double WAVE = 0.04;

    private SkySheet() {}

    /**
     * The far clipping distance of a render distance (chunks): vanilla's projection reaches twice its fog distance,
     * {@code renderDistanceChunks × 16 × 2} (facts render §1.10).
     */
    public static double farClip(int renderChunks) {
        return Math.max(2, renderChunks) * 16 * 2;
    }

    /** The sky layer's distance from the camera at a render distance. */
    public static double radius(int renderChunks) {
        return SHARE * farClip(renderChunks);
    }

    /**
     * The sky layer facing the horizontal direction {@code (dx, dz)} (north is (0, -1)) at a render distance:
     * coordinates relative to the camera.
     */
    public static Aurora.Placement sky(double dx, double dz, int renderChunks) {
        double r = radius(renderChunks), base = Math.atan2(dz, dx);
        return (c, u, v, wave) -> {
            double az = base + Math.toRadians((u - 0.5) * 2 * HALF_SPAN + c * SECOND_TURN);
            double el = Math.toRadians(FOOT_ELEVATION + c * SECOND_UP + v * (TOP_ELEVATION - FOOT_ELEVATION));
            double d = r * (1 - c * SECOND_NEAR + WAVE * wave);
            return new double[] { d * Math.cos(el) * Math.cos(az), d * Math.sin(el), d * Math.cos(el) * Math.sin(az) };
        };
    }

    /** The world curtain's size: its distance from its anchor, width, foot and top over the anchor, wave. */
    public static final double WORLD_DISTANCE = 128, WORLD_WIDTH = 192, WORLD_FOOT = 70, WORLD_TOP = 150,
        WORLD_WAVE = 12, WORLD_SECOND_BACK = 20, WORLD_SECOND_UP = 10;

    /**
     * The world-fixed curtain of an anchor {@code (ax, ay, az)}: {@link #WORLD_DISTANCE} along the horizontal direction
     * {@code (dx, dz)}, across it {@link #WORLD_WIDTH} wide, from {@link #WORLD_FOOT} to {@link #WORLD_TOP} over the
     * anchor; world coordinates.
     */
    public static Aurora.Placement world(double ax, double ay, double az, double dx, double dz) {
        double n = Math.hypot(dx, dz), fx = dx / n, fz = dz / n, rx = -fz, rz = fx;
        return (c, u, v, wave) -> {
            double along = WORLD_DISTANCE + c * WORLD_SECOND_BACK + WORLD_WAVE * wave, across = (u - 0.5) * WORLD_WIDTH;
            double up = WORLD_FOOT + c * WORLD_SECOND_UP + v * (WORLD_TOP - WORLD_FOOT);
            return new double[] { ax + fx * along + rx * across, ay + up, az + fz * along + rz * across };
        };
    }
}
