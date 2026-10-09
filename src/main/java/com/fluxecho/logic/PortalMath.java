package com.fluxecho.logic;

/**
 * The view through a light gate, without Minecraft: the camera's matrices, the pyramid from the camera through a
 * membrane, and the projection whose near plane lies on the membrane, so that what stands between the camera and the
 * far gate is cut away. Matrices are OpenGL's: 4 x 4, column-major ({@code m[column * 4 + row]}).
 */
public final class PortalMath {

    private PortalMath() {}

    public static double[] identity() {
        double[] m = new double[16];
        m[0] = m[5] = m[10] = m[15] = 1;
        return m;
    }

    public static double[] multiply(double[] a, double[] b) {
        double[] r = new double[16];
        for (int c = 0; c < 4; c++) for (int row = 0; row < 4; row++) {
            double s = 0;
            for (int k = 0; k < 4; k++) s += a[k * 4 + row] * b[c * 4 + k];
            r[c * 4 + row] = s;
        }
        return r;
    }

    public static double[] transform(double[] m, double x, double y, double z, double w) {
        return new double[] { m[0] * x + m[4] * y + m[8] * z + m[12] * w, m[1] * x + m[5] * y + m[9] * z + m[13] * w,
            m[2] * x + m[6] * y + m[10] * z + m[14] * w, m[3] * x + m[7] * y + m[11] * z + m[15] * w };
    }

    /** The inverse, or null when there is none. */
    public static double[] invert(double[] m) {
        double[] inv = new double[16];
        inv[0] = m[5] * m[10] * m[15] - m[5] * m[11] * m[14]
            - m[9] * m[6] * m[15]
            + m[9] * m[7] * m[14]
            + m[13] * m[6] * m[11]
            - m[13] * m[7] * m[10];
        inv[4] = -m[4] * m[10] * m[15] + m[4] * m[11] * m[14]
            + m[8] * m[6] * m[15]
            - m[8] * m[7] * m[14]
            - m[12] * m[6] * m[11]
            + m[12] * m[7] * m[10];
        inv[8] = m[4] * m[9] * m[15] - m[4] * m[11] * m[13]
            - m[8] * m[5] * m[15]
            + m[8] * m[7] * m[13]
            + m[12] * m[5] * m[11]
            - m[12] * m[7] * m[9];
        inv[12] = -m[4] * m[9] * m[14] + m[4] * m[10] * m[13]
            + m[8] * m[5] * m[14]
            - m[8] * m[6] * m[13]
            - m[12] * m[5] * m[10]
            + m[12] * m[6] * m[9];
        inv[1] = -m[1] * m[10] * m[15] + m[1] * m[11] * m[14]
            + m[9] * m[2] * m[15]
            - m[9] * m[3] * m[14]
            - m[13] * m[2] * m[11]
            + m[13] * m[3] * m[10];
        inv[5] = m[0] * m[10] * m[15] - m[0] * m[11] * m[14]
            - m[8] * m[2] * m[15]
            + m[8] * m[3] * m[14]
            + m[12] * m[2] * m[11]
            - m[12] * m[3] * m[10];
        inv[9] = -m[0] * m[9] * m[15] + m[0] * m[11] * m[13]
            + m[8] * m[1] * m[15]
            - m[8] * m[3] * m[13]
            - m[12] * m[1] * m[11]
            + m[12] * m[3] * m[9];
        inv[13] = m[0] * m[9] * m[14] - m[0] * m[10] * m[13]
            - m[8] * m[1] * m[14]
            + m[8] * m[2] * m[13]
            + m[12] * m[1] * m[10]
            - m[12] * m[2] * m[9];
        inv[2] = m[1] * m[6] * m[15] - m[1] * m[7] * m[14]
            - m[5] * m[2] * m[15]
            + m[5] * m[3] * m[14]
            + m[13] * m[2] * m[7]
            - m[13] * m[3] * m[6];
        inv[6] = -m[0] * m[6] * m[15] + m[0] * m[7] * m[14]
            + m[4] * m[2] * m[15]
            - m[4] * m[3] * m[14]
            - m[12] * m[2] * m[7]
            + m[12] * m[3] * m[6];
        inv[10] = m[0] * m[5] * m[15] - m[0] * m[7] * m[13]
            - m[4] * m[1] * m[15]
            + m[4] * m[3] * m[13]
            + m[12] * m[1] * m[7]
            - m[12] * m[3] * m[5];
        inv[14] = -m[0] * m[5] * m[14] + m[0] * m[6] * m[13]
            + m[4] * m[1] * m[14]
            - m[4] * m[2] * m[13]
            - m[12] * m[1] * m[6]
            + m[12] * m[2] * m[5];
        inv[3] = -m[1] * m[6] * m[11] + m[1] * m[7] * m[10]
            + m[5] * m[2] * m[11]
            - m[5] * m[3] * m[10]
            - m[9] * m[2] * m[7]
            + m[9] * m[3] * m[6];
        inv[7] = m[0] * m[6] * m[11] - m[0] * m[7] * m[10]
            - m[4] * m[2] * m[11]
            + m[4] * m[3] * m[10]
            + m[8] * m[2] * m[7]
            - m[8] * m[3] * m[6];
        inv[11] = -m[0] * m[5] * m[11] + m[0] * m[7] * m[9]
            + m[4] * m[1] * m[11]
            - m[4] * m[3] * m[9]
            - m[8] * m[1] * m[7]
            + m[8] * m[3] * m[5];
        inv[15] = m[0] * m[5] * m[10] - m[0] * m[6] * m[9]
            - m[4] * m[1] * m[10]
            + m[4] * m[2] * m[9]
            + m[8] * m[1] * m[6]
            - m[8] * m[2] * m[5];
        double det = m[0] * inv[0] + m[1] * inv[4] + m[2] * inv[8] + m[3] * inv[12];
        if (Math.abs(det) < 1e-12) return null;
        for (int i = 0; i < 16; i++) inv[i] /= det;
        return inv;
    }

    /**
     * A plane {@code (a, b, c, d)}, its kept side where {@code a x + b y + c z + d > 0}, carried from the space
     * {@code m} maps from into the space it maps to. Null when {@code m} has no inverse.
     */
    public static double[] carryPlane(double[] m, double[] plane) {
        double[] inv = invert(m);
        if (inv == null) return null;
        double[] r = new double[4];
        for (int i = 0; i < 4; i++) {
            double s = 0;
            for (int j = 0; j < 4; j++) s += inv[i * 4 + j] * plane[j];
            r[i] = s;
        }
        return r;
    }

    /**
     * The projection with its near plane moved onto {@code clip}, a plane in eye space whose kept side is positive
     * and which the camera is not on (Lengyel's oblique near plane). The screen does not move; only depth changes.
     * The projection itself when there is no such matrix.
     */
    public static double[] oblique(double[] projection, double[] clip) {
        double[] inv = invert(projection);
        if (inv == null) return projection;
        double[] q = transform(inv, Math.signum(clip[0]), Math.signum(clip[1]), 1, 1);
        double dot = clip[0] * q[0] + clip[1] * q[1] + clip[2] * q[2] + clip[3] * q[3];
        if (Math.abs(dot) < 1e-12) return projection;
        double wq = projection[3] * q[0] + projection[7] * q[1] + projection[11] * q[2] + projection[15] * q[3];
        double k = 2 * wq / dot;
        double[] r = projection.clone();
        for (int i = 0; i < 4; i++) r[i * 4 + 2] = k * clip[i] - projection[i * 4 + 3];
        return r;
    }

    /** As {@code gluPerspective}. */
    public static double[] perspective(double fovyDegrees, double aspect, double near, double far) {
        double f = 1 / Math.tan(Math.toRadians(fovyDegrees) / 2);
        double[] m = new double[16];
        m[0] = f / aspect;
        m[5] = f;
        m[10] = (far + near) / (near - far);
        m[11] = -1;
        m[14] = 2 * far * near / (near - far);
        return m;
    }

    private static double[] rotation(double degrees, double x, double y, double z) {
        double a = Math.toRadians(degrees), c = Math.cos(a), s = Math.sin(a), t = 1 - c;
        double[] m = identity();
        m[0] = t * x * x + c;
        m[1] = t * x * y + s * z;
        m[2] = t * x * z - s * y;
        m[4] = t * x * y - s * z;
        m[5] = t * y * y + c;
        m[6] = t * y * z + s * x;
        m[8] = t * x * z + s * y;
        m[9] = t * y * z - s * x;
        m[10] = t * z * z + c;
        return m;
    }

    /** Minecraft's first-person camera turned to {@code yaw} and {@code pitch}, at the origin. */
    public static double[] view(float yaw, float pitch) {
        return multiply(rotation(pitch, 1, 0, 0), rotation(yaw + 180f, 0, 1, 0));
    }

    /** Where Minecraft looks at {@code yaw} and {@code pitch}, as a unit vector. */
    public static double[] look(float yaw, float pitch) {
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        return new double[] { -Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p) };
    }

    /**
     * The corners of a gate's membrane, {@code margin} wider all round, relative to {@code (cx, cy, cz)}, in order
     * round the pane.
     */
    public static double[][] corners(GateGeometry.Gate g, double cx, double cy, double cz, double margin) {
        double lx = -GateGeometry.dz(g.facing), lz = GateGeometry.dx(g.facing);
        double w = GateGeometry.HALF_WIDTH + margin, y0 = g.y - margin - cy,
            y1 = g.y + GateGeometry.HEIGHT + margin - cy;
        double x = g.cx() - cx, z = g.cz() - cz;
        return new double[][] { { x - w * lx, y0, z - w * lz }, { x + w * lx, y0, z + w * lz },
            { x + w * lx, y1, z + w * lz }, { x - w * lx, y1, z - w * lz } };
    }

    /**
     * The four planes of the pyramid from the origin through {@code corners} (in order round a flat convex pane), each
     * moved {@code pad} outwards; inside is positive. What can be seen through the pane from the origin is inside.
     */
    public static double[][] pyramid(double[][] corners, double pad) {
        int n = corners.length;
        double mx = 0, my = 0, mz = 0;
        for (double[] c : corners) {
            mx += c[0] / n;
            my += c[1] / n;
            mz += c[2] / n;
        }
        double[][] planes = new double[n][];
        for (int i = 0; i < n; i++) {
            double[] a = corners[i], b = corners[(i + 1) % n];
            double x = a[1] * b[2] - a[2] * b[1], y = a[2] * b[0] - a[0] * b[2], z = a[0] * b[1] - a[1] * b[0];
            double len = Math.sqrt(x * x + y * y + z * z);
            if (len < 1e-9) return null;
            if (x * mx + y * my + z * mz < 0) len = -len;
            planes[i] = new double[] { x / len, y / len, z / len, pad };
        }
        return planes;
    }

    public static boolean inside(double[][] planes, double x, double y, double z) {
        for (double[] p : planes) if (p[0] * x + p[1] * y + p[2] * z + p[3] <= 0) return false;
        return true;
    }

    /**
     * A gate's membrane as a plane relative to a camera at {@code (cx, cz)}: positive in front of the gate, where one
     * comes out of it.
     */
    public static double[] membranePlane(GateGeometry.Gate g, double cx, double cz) {
        return new double[] { GateGeometry.dx(g.facing), 0, GateGeometry.dz(g.facing), g.front(cx, cz) };
    }

    /**
     * The part of a {@code width} x {@code height} screen that the points can cover, as OpenGL's scissor box
     * {@code (x, y, w, h)} from the bottom left, padded by {@code pad} of the screen's size: all of it when some
     * point is behind or right at the camera, null when they all are or when they are all off one side.
     */
    public static int[] screenRect(double[] projection, double[] view, double[][] points, int width, int height,
        double pad) {
        double x0 = Double.MAX_VALUE, y0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, y1 = -Double.MAX_VALUE;
        double[] mvp = multiply(projection, view);
        int behind = 0;
        for (double[] p : points) {
            double[] c = transform(mvp, p[0], p[1], p[2], 1);
            if (c[3] <= 0.05) {
                behind++;
                continue;
            }
            double nx = c[0] / c[3], ny = c[1] / c[3];
            x0 = Math.min(x0, nx);
            x1 = Math.max(x1, nx);
            y0 = Math.min(y0, ny);
            y1 = Math.max(y1, ny);
        }
        if (behind == points.length) return null;
        if (behind > 0) return new int[] { 0, 0, width, height };
        if (x1 < -1 || x0 > 1 || y1 < -1 || y0 > 1) return null;
        double l = Math.max(0, ((x0 + 1) / 2 - pad) * width), r = Math.min(width, ((x1 + 1) / 2 + pad) * width);
        double b = Math.max(0, ((y0 + 1) / 2 - pad) * height), t = Math.min(height, ((y1 + 1) / 2 + pad) * height);
        if (r - l < 1 || t - b < 1) return null;
        return toInts(new double[] { l, b, r - l, t - b });
    }

    private static int[] toInts(double[] r) {
        int x = (int) Math.floor(r[0]), y = (int) Math.floor(r[1]);
        return new int[] { x, y, (int) Math.ceil(r[0] + r[2]) - x, (int) Math.ceil(r[1] + r[3]) - y };
    }
}
