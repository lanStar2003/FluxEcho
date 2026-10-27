package com.fluxecho.logic.probe;

/**
 * The fixed camera points of the city (0.17.0, masterplan FINAL §7.3, style FINAL §6.1), where {@code /fluxecho cam
 * <A|B|C|D|E>} puts an operator: each point's place in the campus's own frame (A along the nexus's front, the bow; R to
 * its right; heights over the base layer Y0, the nexus's base, {@code TileNexus.centre()}), the way it looks and how
 * far it tilts down. The acceptance card's "camera set" (refactor §2.6, item 12 of every card) is three screenshots at
 * each point.
 * <ul>
 * <li>A, the city gate: the gatehouse bridge (31, 0) at Y0 + 15, looking west (to the stern) down the axis at the mast
 * and through the ring's eye.</li>
 * <li>B, the bridge: the dome pod's ring gallery (0, 0) at Y0 + F + 2, F the pod's sky datum (40 by default, lower over
 * high ground), looking all round; set looking to the stern and a little down.</li>
 * <li>C, the ring pier: the sunward (south) pier's crown walk (-79, 28) at Y0 + 8, looking east at the city.</li>
 * <li>D, the tether deck: the orbital anchor's deck (0, 0) at Y0 + 52, looking down.</li>
 * <li>E, the look-back hill: its top (-110, 0) at Y0 + 9, looking east through the ring.</li>
 * </ul>
 * Heights are where the feet stand (masterplan's "standing" heights; the eye is 1.6 higher, the masterplan's "eye
 * height"). Points whose building is not built yet hang in the air; the command turns flying on.
 */
public final class CamPoints {

    /** The eye's height over the feet, the masterplan's 1.6 (Minecraft's player eye is 1.62). */
    public static final double EYE = 1.6;

    /** One point. */
    public enum Point {

        A("城门", 31, 0, 15, false, Look.STERN, 0),
        B("舰桥", 0, 0, 2, true, Look.STERN, 15),
        C("环墩", -79, 28, 8, false, Look.BOW, 0),
        D("系索台", 0, 0, 52, false, Look.STERN, 60),
        E("回望丘", -110, 0, 9, false, Look.BOW, 0);

        /** The masterplan's name of the point. */
        public final String zh;
        /** Its place in the campus frame: along the bow (A) and to starboard (R). */
        public final int a, r;
        /** The feet's height over Y0; for B over Y0 + F. */
        public final int up;
        /** Whether {@link #up} is measured from the pod's sky datum (B). */
        public final boolean overDatum;
        /** Which way it looks in the campus frame. */
        public final Look look;
        /** How far it tilts down, degrees (Minecraft's pitch: positive is down). */
        public final float pitch;

        Point(String zh, int a, int r, int up, boolean overDatum, Look look, float pitch) {
            this.zh = zh;
            this.a = a;
            this.r = r;
            this.up = up;
            this.overDatum = overDatum;
            this.look = look;
            this.pitch = pitch;
        }

        /** The point a letter names (A to E, either case), or null. */
        public static Point of(String letter) {
            if (letter == null || letter.trim()
                .length() != 1) return null;
            char c = Character.toUpperCase(
                letter.trim()
                    .charAt(0));
            for (Point p : values()) if (p.name()
                .charAt(0) == c) return p;
            return null;
        }
    }

    /** The way a point looks, in the campus frame. */
    public enum Look {

        /** Along +A, the bow (east on a new campus). */
        BOW(1, 0),
        /** Along -A, the stern and the ring (west on a new campus). */
        STERN(-1, 0);

        /** The direction in the campus frame. */
        public final int a, r;

        Look(int a, int r) {
            this.a = a;
            this.r = r;
        }
    }

    /** Where a point puts the operator: their feet's position and Minecraft's yaw and pitch. */
    public static final class Place {

        public final double x, y, z;
        public final float yaw, pitch;

        Place(double x, double y, double z, float yaw, float pitch) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }

        @Override
        public String toString() {
            return String.format(java.util.Locale.ROOT, "(%.1f, %.1f, %.1f) yaw %.0f pitch %.0f", x, y, z, yaw, pitch);
        }
    }

    private CamPoints() {}

    /**
     * The campus frame's world offset of (a, r): masterplan §2.1 {@code toWorld(a, r, fx, fz) = (a·fx − r·fz, a·fz +
     * r·fx)}, (fx, fz) the nexus's front.
     */
    public static int[] toWorld(int a, int r, int fx, int fz) {
        return new int[] { a * fx - r * fz, a * fz + r * fx };
    }

    /**
     * Where point {@code p} puts the operator for a nexus whose base centre is {@code (cx, y0, cz)} (Y0 its level),
     * whose front is {@code (fx, fz)} and whose pod's sky datum is {@code datum}: their feet in the middle of the
     * point's cell, looking the point's way.
     */
    public static Place place(Point p, int cx, int y0, int cz, int fx, int fz, int datum) {
        int[] o = toWorld(p.a, p.r, fx, fz), d = toWorld(p.look.a, p.look.r, fx, fz);
        double feet = y0 + p.up + (p.overDatum ? datum : 0);
        return new Place(cx + o[0] + 0.5, feet, cz + o[1] + 0.5, yaw(d[0], d[1]), p.pitch);
    }

    /** Minecraft's yaw of looking along (dx, dz): 0 south, 90 west, 180 north, 270 (-90) east; 0 to 360. */
    public static float yaw(double dx, double dz) {
        float y = (float) Math.toDegrees(Math.atan2(-dx, dz));
        return y < 0 ? y + 360 : y;
    }
}
