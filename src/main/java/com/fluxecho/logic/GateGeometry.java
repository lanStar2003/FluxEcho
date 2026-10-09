package com.fluxecho.logic;

/**
 * Light gates without Minecraft: where a gate's membrane stands, when a step goes through it, where the step comes out
 * at the paired gate, what part of the far side a gate shows, and where that lands on the screen.
 * <p>
 * A gate stands on one block and faces one of four sides, its front: you walk in from there. Sides are Minecraft's
 * yaw quarters: 0 south (+z), 1 west (-x), 2 north (-z), 3 east (+x). The membrane is a 3 x 3 pane through the middle
 * of the block, across the facing, from the block's bottom up.
 */
public final class GateGeometry {

    public static final double HALF_WIDTH = 1.5, HEIGHT = 3;

    private GateGeometry() {}

    public static int dx(int side) {
        return side == 1 ? -1 : side == 3 ? 1 : 0;
    }

    public static int dz(int side) {
        return side == 0 ? 1 : side == 2 ? -1 : 0;
    }

    /** The side a player looks to at this yaw. */
    public static int sideOfYaw(float yaw) {
        return (int) Math.floor(yaw * 4 / 360 + 0.5) & 3;
    }

    /** Turns {@code (x, z)} by {@code quarters} quarter-turns of yaw: one turn takes south to west. */
    public static double[] rotate(int quarters, double x, double z) {
        double a = x, b = z;
        for (int i = 0; i < Math.floorMod(quarters, 4); i++) {
            double t = a;
            a = -b;
            b = t;
        }
        return new double[] { a, b };
    }

    /** The angle for {@code glRotatef(angle, 0, 1, 0)} that turns like {@link #rotate}. */
    public static float glDegrees(int quarters) {
        return -90f * Math.floorMod(quarters, 4);
    }

    /** One gate: the block it stands on and its front side. */
    public static final class Gate {

        public final int x, y, z, facing;

        public Gate(int x, int y, int z, int facing) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.facing = facing & 3;
        }

        public double cx() {
            return x + 0.5;
        }

        public double cz() {
            return z + 0.5;
        }

        /** How far a point is in front of the membrane (behind it when negative). */
        public double front(double px, double pz) {
            return (px - cx()) * dx(facing) + (pz - cz()) * dz(facing);
        }

        /** How far a point is to the side of the membrane's middle line. */
        public double across(double px, double pz) {
            return -(px - cx()) * dz(facing) + (pz - cz()) * dx(facing);
        }

        /** Whether a point on the membrane's plane is on the pane (with half a block to spare below). */
        public boolean onPane(double px, double py, double pz) {
            return Math.abs(across(px, pz)) <= HALF_WIDTH && py >= y - 0.5 && py <= y + HEIGHT;
        }

        /** Whether a step from {@code (x0, y0, z0)} to {@code (x1, y1, z1)} goes into the gate from its front. */
        public boolean entered(double x0, double y0, double z0, double x1, double y1, double z1) {
            double d0 = front(x0, z0), d1 = front(x1, z1);
            if (!(d0 > 0 && d1 <= 0)) return false;
            double t = d0 / (d0 - d1);
            return onPane(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, z0 + (z1 - z0) * t);
        }
    }

    /** The quarter-turns that take a step into {@code from} to a step out of {@code to}. */
    public static int turns(Gate from, Gate to) {
        return Math.floorMod(to.facing - (from.facing + 2), 4);
    }

    /**
     * Where a point near {@code from} is near {@code to}: walking into {@code from} comes out of {@code to}, and the
     * view through {@code to} is the space in front of {@code from} carried over with the same map, the other way
     * round. Each direction undoes the other.
     */
    public static double[] carry(Gate from, Gate to, double px, double py, double pz) {
        double[] r = rotate(turns(from, to), px - from.cx(), pz - from.cz());
        return new double[] { to.cx() + r[0], py - from.y + to.y, to.cz() + r[1] };
    }

    /** As {@link #carry}, then at least {@code margin} in front of {@code to}, so the step does not go back in. */
    public static double[] exit(Gate from, Gate to, double px, double py, double pz, double margin) {
        double[] p = carry(from, to, px, py, pz);
        double d = to.front(p[0], p[2]);
        if (d < margin) {
            p[0] += dx(to.facing) * (margin - d);
            p[2] += dz(to.facing) * (margin - d);
        }
        return p;
    }

    /** The yaw after walking into {@code from} and out of {@code to}. */
    public static float exitYaw(Gate from, Gate to, float yaw) {
        return yaw + 90f * turns(from, to);
    }

    /** A box of blocks, both corners inside. */
    public static final class Box {

        public final int minX, minY, minZ, maxX, maxY, maxZ;

        public Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            this.minX = Math.min(minX, maxX);
            this.minY = Math.min(minY, maxY);
            this.minZ = Math.min(minZ, maxZ);
            this.maxX = Math.max(minX, maxX);
            this.maxY = Math.max(minY, maxY);
            this.maxZ = Math.max(minZ, maxZ);
        }

        public boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }

        /** Whether the 16-block section at section coordinates {@code (sx, sy, sz)} touches the box. */
        public boolean touchesSection(int sx, int sy, int sz) {
            return sx * 16 <= maxX && sx * 16 + 15 >= minX
                && sy * 16 <= maxY
                && sy * 16 + 15 >= minY
                && sz * 16 <= maxZ
                && sz * 16 + 15 >= minZ;
        }
    }

    /**
     * What a gate shows of its own side when seen through its partner: {@code depth} blocks in front of it,
     * {@code halfWidth} to each side, from {@code below} under its block to {@code above} over it, inside the world.
     */
    public static Box view(Gate g, int depth, int halfWidth, int below, int above) {
        int fx = dx(g.facing), fz = dz(g.facing);
        int x0 = g.x - Math.abs(fz) * halfWidth, x1 = g.x + Math.abs(fz) * halfWidth + fx * depth;
        int z0 = g.z - Math.abs(fx) * halfWidth, z1 = g.z + Math.abs(fx) * halfWidth + fz * depth;
        return new Box(x0, Math.max(0, g.y - below), z0, x1, Math.min(255, g.y + above), z1);
    }

    /**
     * A point relative to the camera, through OpenGL's column-major projection and modelview matrices: x and y on the
     * screen from -1 to 1, and w, which is not positive for a point behind the camera.
     */
    public static double[] project(float[] projection, float[] modelview, double x, double y, double z) {
        double[] eye = mul(modelview, x, y, z, 1);
        double[] clip = mul(projection, eye[0], eye[1], eye[2], eye[3]);
        double w = clip[3];
        return new double[] { clip[0] / w, clip[1] / w, w };
    }

    private static double[] mul(float[] m, double x, double y, double z, double w) {
        return new double[] { m[0] * x + m[4] * y + m[8] * z + m[12] * w, m[1] * x + m[5] * y + m[9] * z + m[13] * w,
            m[2] * x + m[6] * y + m[10] * z + m[14] * w, m[3] * x + m[7] * y + m[11] * z + m[15] * w };
    }
}
