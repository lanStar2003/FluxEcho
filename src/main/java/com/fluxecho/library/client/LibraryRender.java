package com.fluxecho.library.client;

import static com.fluxecho.client.FluxDraw.*;

import net.minecraft.client.renderer.entity.RenderManager;

import org.lwjgl.opengl.GL11;

import com.fluxecho.client.Motes;
import com.fluxecho.client.Motifs;
import com.fluxecho.library.TileLibrary;
import com.fluxecho.render.Shapes;

/**
 * The formed Echo Library (blueprint 3.7, "the five-dimensional bookshelf"): its shelf faces open, and behind each one
 * a corridor of glowing shelves runs on into the depth. The corridors narrow faster than perspective would, so the
 * seven-block cube looks endless from outside; each sits in its own pyramid towards the cube's middle, so they never
 * meet. Above, the codex it embodies floats open, glyphs rising into it. Undocked or without power it dims.
 */
public final class LibraryRender {

    private static final int WALL = 0x0B1424, WALL_DEEP = 0x05070E, GOLD = 0xFFD27A;
    /** Corridor depth and the half-size of its far end, in blocks. */
    private static final double DEPTH = 3.0, END = 0.4;
    private static final int SEGMENTS = 6;

    private LibraryRender() {}

    public static void draw(TileLibrary l, double t, float fade) {
        if (!l.formed()) return;
        int[] b = l.bounds();
        if (b == null) return;
        // the cube stands on the foundation, inset one block all round
        double x0 = b[0] + 1, x1 = b[3], y0 = b[1] + 1, y1 = b[4] + 1, z0 = b[2] + 1, z1 = b[5];
        double cx = (x0 + x1) / 2, cy = (y0 + y1) / 2, cz = (z0 + z1) / 2;
        boolean docked = "docked".equals(l.clientDock), on = docked && l.clientPowered;
        float p = (on ? 1f : 0.35f) * fade;
        double sweep = l.sweepLevel();
        float appear = sweep == Double.MAX_VALUE ? 1f
            : (float) Math.max(0, Math.min(1, (System.currentTimeMillis() - l.formedAt) / 2000.0));
        double camX = RenderManager.renderPosX, camY = RenderManager.renderPosY, camZ = RenderManager.renderPosZ;

        // faces: outward normal, the face's centre
        double[][] faces = { { -1, 0, 0, x0, cy, cz }, { 1, 0, 0, x1, cy, cz }, { 0, 0, -1, cx, cy, z0 },
            { 0, 0, 1, cx, cy, z1 }, { 0, 1, 0, cx, y1, cz } };
        Shapes.begin(false);
        GL11.glDepthMask(true);
        for (int f = 0; f < faces.length; f++) {
            double[] fc = faces[f];
            double side = (camX - fc[3]) * fc[0] + (camY - fc[4]) * fc[1] + (camZ - fc[5]) * fc[2];
            if (side <= 0) continue;
            corridorWalls(fc, appear * fade);
        }
        GL11.glDepthMask(false);
        Shapes.additive(true);
        for (int f = 0; f < faces.length; f++) {
            double[] fc = faces[f];
            double side = (camX - fc[3]) * fc[0] + (camY - fc[4]) * fc[1] + (camZ - fc[5]) * fc[2];
            if (side <= 0) continue;
            corridorLights(fc, f, t, p * appear);
        }
        double top = y1 - camY;
        codex(cx - camX, top + 2.2, cz - camZ, t, p);
        if (sweep != Double.MAX_VALUE) {
            double sy = sweep - camY;
            Shapes.plane(x0 - 1 - camX, sy, z0 - 1 - camZ, x1 + 1 - camX, z1 + 1 - camZ, VIOLET, 0.35f * (1 - appear));
            Shapes.square(cx - camX, sy, cz - camZ, (x1 - x0) / 2 + 1, 0.15, WHITE, 0.8f * (1 - appear));
        }
        float ring = docked ? (l.clientLends > 0 ? 0.5f + 0.3f * (float) Math.sin(t * 0.15) : 0.25f)
            : 0.3f * (float) (0.5 + 0.5 * Math.sin(t * 0.1));
        Shapes.square(
            cx - camX,
            y0 - camY + 0.03,
            cz - camZ,
            (x1 - x0) / 2 + 1,
            0.18,
            docked ? VIOLET : AMBER,
            ring * fade);
        Shapes.end();

        Motes.begin();
        for (int k = 0; k < 10; k++) {
            double f = (t * 0.01 + k / 10.0) % 1;
            double a = k * 2.3 + t * 0.01;
            double r = 1.6 * (1 - f);
            Motes.add(
                cx - camX + Math.cos(a) * r,
                top - 1.5 + f * 3.4,
                cz - camZ + Math.sin(a) * r,
                0.1,
                k % 3 == 0 ? GOLD : VIOLET,
                (float) Math.sin(f * Math.PI) * p);
        }
        Motes.add(cx - camX, top + 2.2, cz - camZ, 1.1, VIOLET, 0.35f * p);
        Motes.end();
    }

    /** A point on a face's corridor: {@code u, v} across the face, {@code d} in from it. */
    private static double[] at(double[] fc, double u, double v, double d) {
        double nx = fc[0], ny = fc[1], nz = fc[2];
        // two directions across the face
        double ux, uy, uz, vx, vy, vz;
        if (ny != 0) {
            ux = 1;
            uy = 0;
            uz = 0;
            vx = 0;
            vy = 0;
            vz = 1;
        } else {
            ux = -nz;
            uy = 0;
            uz = nx;
            vx = 0;
            vy = 1;
            vz = 0;
        }
        return new double[] { fc[3] - nx * d + ux * u + vx * v - RenderManager.renderPosX,
            fc[4] - ny * d + uy * u + vy * v - RenderManager.renderPosY,
            fc[5] - nz * d + uz * u + vz * v - RenderManager.renderPosZ };
    }

    private static double depth(int i) {
        return DEPTH * i / SEGMENTS;
    }

    /** Half-size of the corridor at segment boundary {@code i}: from the 5-wide opening down to the far end. */
    private static double half(int i) {
        double f = i / (double) SEGMENTS;
        return 2.5 + (END - 2.5) * Math.pow(f, 0.8);
    }

    private static void corridorWalls(double[] fc, float a) {
        for (int i = 0; i < SEGMENTS; i++) {
            double d0 = depth(i), d1 = depth(i + 1), h0 = half(i), h1 = half(i + 1);
            int c0 = Shapes.mix(WALL, WALL_DEEP, i / (double) SEGMENTS),
                c1 = Shapes.mix(WALL, WALL_DEEP, (i + 1) / (double) SEGMENTS);
            for (int s = 0; s < 4; s++) {
                double[] p0 = corner(fc, s, h0, d0), p1 = corner(fc, s + 1, h0, d0), p2 = corner(fc, s + 1, h1, d1),
                    p3 = corner(fc, s, h1, d1);
                Shapes.quadShade(p0, p1, p2, p3, c0, c1, a);
            }
        }
        double h = half(SEGMENTS), d = depth(SEGMENTS);
        Shapes.quad3(
            corner(fc, 0, h, d),
            corner(fc, 1, h, d),
            corner(fc, 2, h, d),
            corner(fc, 3, h, d),
            WALL_DEEP,
            a,
            a,
            a,
            a);
    }

    /** Corner {@code k} (0..3, round the square) of the corridor's cross-section at depth {@code d}. */
    private static double[] corner(double[] fc, int k, double h, double d) {
        int[][] signs = { { -1, -1 }, { 1, -1 }, { 1, 1 }, { -1, 1 } };
        int[] sg = signs[Math.floorMod(k, 4)];
        return at(fc, sg[0] * h, sg[1] * h, d);
    }

    /** The shelves' light: a glowing rim at each step in, rows of book spines on the walls, the far end shining. */
    private static void corridorLights(double[] fc, int face, double t, float a) {
        for (int i = 0; i <= SEGMENTS; i++) {
            double h = half(i) - 0.02, d = depth(i);
            float fa = a * (1f - i / (float) (SEGMENTS + 2));
            for (int s = 0; s < 4; s++) {
                double[] p0 = corner(fc, s, h, d), p1 = corner(fc, s + 1, h, d);
                Shapes.string(p0[0], p0[1], p0[2], p1[0], p1[1], p1[2], 0.05, VIOLET, 0.55f * fa, 0.55f * fa);
            }
        }
        // book spines: short bright strokes across the walls, a hue each
        for (int i = 0; i < SEGMENTS; i++) {
            double dm = (depth(i) + depth(i + 1)) / 2, hm = (half(i) + half(i + 1)) / 2 - 0.03;
            float fa = a * (0.6f - 0.08f * i);
            for (int s = 0; s < 4; s++) for (int k = 0; k < 7; k++) {
                double hash = Motifs.hash(face * 997 + i * 131 + s * 17 + k);
                if (hash < 0.25) continue;
                double u = -hm + (k + 0.5) * hm * 2 / 7;
                double len = 0.25 + 0.3 * hash;
                double[] a0 = wallPoint(fc, s, u, hm, dm - len / 2), a1 = wallPoint(fc, s, u, hm, dm + len / 2);
                int col = hash < 0.5 ? VIOLET : hash < 0.8 ? CYAN : GOLD;
                float tw = 0.6f + 0.4f * (float) Math.sin(t * 0.05 + hash * 20);
                Shapes.string(a0[0], a0[1], a0[2], a1[0], a1[1], a1[2], 0.06 * (1 - i * 0.1), col, fa * tw, fa * tw);
            }
        }
        double h = half(SEGMENTS), d = depth(SEGMENTS) - 0.02;
        float pulse = 0.6f + 0.4f * (float) Math.sin(t * 0.07 + face);
        for (int r = 0; r < 3; r++) {
            double rr = h * (0.4 + r * 0.3);
            Shapes.quad3(
                at(fc, -rr, -rr, d),
                at(fc, rr, -rr, d),
                at(fc, rr, rr, d),
                at(fc, -rr, rr, d),
                VIOLET,
                0.25f * a * pulse,
                0.25f * a * pulse,
                0.25f * a * pulse,
                0.25f * a * pulse);
        }
    }

    /**
     * A point on wall {@code s} of the corridor: {@code u} along the wall, at half-size {@code h} and depth {@code d}.
     */
    private static double[] wallPoint(double[] fc, int s, double u, double h, double d) {
        switch (Math.floorMod(s, 4)) {
            case 0:
                return at(fc, u, -h, d);
            case 1:
                return at(fc, h, u, d);
            case 2:
                return at(fc, -u, h, d);
            default:
                return at(fc, -h, -u, d);
        }
    }

    /** The codex floating open above the library, its pages turning. */
    private static void codex(double x, double y, double z, double t, float a) {
        double spin = t * 0.01, open = Math.toRadians(70 + 35 * Math.sin(t * 0.03));
        double ax = Math.cos(spin), az = Math.sin(spin);
        double bob = Math.sin(t * 0.04) * 0.12;
        double[] s0 = { x - ax * 0.5, y + bob, z - az * 0.5 }, s1 = { x + ax * 0.5, y + bob, z + az * 0.5 };
        for (int side = -1; side <= 1; side += 2) {
            double ang = side * open / 2;
            // the page runs out from the spine, tilted up by the open angle
            double ox = -az * Math.cos(ang) * 0.7 * side, oz = ax * Math.cos(ang) * 0.7 * side,
                oy = Math.sin(Math.abs(ang)) * 0.7;
            double[] p2 = { s1[0] + ox, s1[1] + oy, s1[2] + oz }, p3 = { s0[0] + ox, s0[1] + oy, s0[2] + oz };
            Shapes.quad3(s0, s1, p2, p3, VIOLET, 0.5f * a, 0.5f * a, 0.15f * a, 0.15f * a);
            for (int l = 1; l <= 4; l++) {
                double f = l / 5.0;
                double[] q0 = { s0[0] + ox * f, s0[1] + oy * f, s0[2] + oz * f },
                    q1 = { s1[0] + ox * f, s1[1] + oy * f, s1[2] + oz * f };
                Shapes.string(q0[0], q0[1], q0[2], q1[0], q1[1], q1[2], 0.03, GOLD, 0.5f * a, 0.5f * a);
            }
        }
        Shapes.string(s0[0], s0[1], s0[2], s1[0], s1[1], s1[2], 0.08, WHITE, 0.7f * a, 0.7f * a);
    }
}
