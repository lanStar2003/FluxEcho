package com.fluxecho.logic;

import java.util.List;

/**
 * Where a player is seen from on the far side of the light gates near them. Looking into a gate is looking out of
 * its partner from the place the player would be after walking through, so what is near that place (a machine's
 * hologram, a mob, another player) counts as near the player, as if the gate were not there.
 */
public final class GateSight {

    /** One gate the player can see through: walking into {@code from} comes out of {@code to}. */
    public static final class Line {

        public final GateGeometry.Gate from, to;

        public Line(GateGeometry.Gate from, GateGeometry.Gate to) {
            this.from = from;
            this.to = to;
        }
    }

    private GateSight() {}

    /** The player's own place first, then where they are seen from through each line. */
    public static double[][] viewpoints(double px, double py, double pz, List<Line> lines) {
        double[][] v = new double[lines.size() + 1][];
        v[0] = new double[] { px, py, pz };
        for (int i = 0; i < lines.size(); i++) {
            Line l = lines.get(i);
            v[i + 1] = GateGeometry.carry(l.from, l.to, px, py, pz);
        }
        return v;
    }

    /**
     * The viewpoint closest to {@code (x, z)} by the larger of the two flat distances, which is how the server's entity
     * tracker measures.
     */
    public static double[] nearestFlat(double[][] viewpoints, double x, double z) {
        double[] best = viewpoints[0];
        double d = Double.MAX_VALUE;
        for (double[] v : viewpoints) {
            double e = Math.max(Math.abs(v[0] - x), Math.abs(v[2] - z));
            if (e < d) {
                d = e;
                best = v;
            }
        }
        return best;
    }

    /** The square of the distance from the closest viewpoint. */
    public static double distanceSq(double[][] viewpoints, double x, double y, double z) {
        double d = Double.MAX_VALUE;
        for (double[] v : viewpoints) {
            double dx = v[0] - x, dy = v[1] - y, dz = v[2] - z;
            d = Math.min(d, dx * dx + dy * dy + dz * dz);
        }
        return d;
    }
}
