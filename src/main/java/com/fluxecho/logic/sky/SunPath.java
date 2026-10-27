package com.fluxecho.logic.sky;

/**
 * Where the sun stands (0.17.0, refactor FINAL §7.1 "日轨", critique feasibility #2): vanilla's sun crosses the zenith
 * along the east-west line, but the user's shader pack tilts its path ({@code sunPathRotation}; Complementary's default
 * {@value #COMPLEMENTARY} degrees), so at noon the sun stands 50 degrees high in the south, while it still rises due
 * east and sets due west. The city's sun-facing pictures (the mast-top full moon, the eco dome's noon light well, the
 * sky sails turned to the sun) are drawn for the tilted path; {@code /fluxecho probe sun} draws lines along both
 * paths, so the user can see which one their pack shows.
 * <p>
 * The sky is drawn turned by -90 degrees about y, then by the path rotation about z, then by the celestial angle about
 * x (vanilla's {@code RenderGlobal.renderSky}, with the shader's rotation between the two, as OptiFine and Iris do), so
 * the sun, at +y before the turns, ends up at {@code (-sin θ, cos θ cos φ, -cos θ sin φ)} in world axes (x east, y up,
 * z south), θ the celestial angle and φ the path rotation.
 */
public final class SunPath {

    /** Complementary Unbound's default {@code sunPathRotation}, degrees. */
    public static final double COMPLEMENTARY = -40;
    /** Vanilla's: no tilt. */
    public static final double VANILLA = 0;

    private SunPath() {}

    /**
     * Vanilla's celestial angle of a world time (0 at noon, tick 6000; 0.25 at sunset; 0.5 at midnight), the same as
     * {@code WorldProvider.calculateCelestialAngle}.
     */
    public static double celestialAngle(long worldTime, float partialTicks) {
        int j = (int) (worldTime % 24000L);
        double f = (j + partialTicks) / 24000.0 - 0.25;
        if (f < 0) f += 1;
        if (f > 1) f -= 1;
        double g = f;
        f = 1 - (Math.cos(f * Math.PI) + 1) / 2;
        return g + (f - g) / 3;
    }

    /** The unit direction to the sun {x, y, z} at a celestial angle, its path turned by {@code rotation} degrees. */
    public static double[] sun(double celestialAngle, double rotation) {
        double t = celestialAngle * 2 * Math.PI, p = Math.toRadians(rotation);
        return new double[] { -Math.sin(t), Math.cos(t) * Math.cos(p), -Math.cos(t) * Math.sin(p) };
    }

    /** How high a direction stands over the horizon, degrees. */
    public static double elevation(double[] d) {
        return Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, d[1]))));
    }

    /**
     * The normal of a sail that turns only about the north-south axis (sky sails, buildings FINAL: "帆只绕南北轴转")
     * as near the sun as it can: the sun's direction projected on the east-west upright plane. Null when the sun stands
     * due north or south (no projection).
     */
    public static double[] sailNormal(double[] sun) {
        double n = Math.hypot(sun[0], sun[1]);
        if (n < 1e-9) return null;
        return new double[] { sun[0] / n, sun[1] / n, 0 };
    }

    /** The angle between two unit directions, degrees. */
    public static double angle(double[] a, double[] b) {
        double d = a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
        return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, d))));
    }
}
