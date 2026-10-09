package com.fluxecho.render;

import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;

import org.lwjgl.opengl.GL11;

import com.fluxecho.client.FluxDraw;

/**
 * The render library of the flux world (blueprint 3.13): rings, bands, beams, crystals, discs and light strings for
 * the big multiblocks, drawn after the world. Positions are relative to the camera ({@code RenderManager.renderPos*}),
 * so the light gates can draw them again from their far side.
 * <p>
 * Shader-safe like the holograms: {@link FluxDraw#worldBegin}'s state (no lighting or fog, the shader-aware lightmap,
 * no depth writes), no textures, and every vertex with a normal towards the viewer, so a shader pack lights them as
 * glowing things rather than washing them white. Glows add up ({@link #begin} with {@code additive}); solid parts
 * blend normally.
 */
public final class Shapes {

    private static float nx, ny = 1, nz;
    private static boolean open;

    private Shapes() {}

    /** Starts drawing; pair with {@link #end}. */
    public static void begin(boolean additive) {
        FluxDraw.worldBegin();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, additive ? GL11.GL_ONE : GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        double yaw = Math.toRadians(RenderManager.instance.playerViewY),
            pitch = Math.toRadians(RenderManager.instance.playerViewX);
        nx = (float) (Math.sin(yaw) * Math.cos(pitch));
        ny = (float) Math.sin(pitch);
        nz = (float) (-Math.cos(yaw) * Math.cos(pitch));
        open = true;
    }

    /** Switches between adding up (glows) and normal blending (solid parts) inside a {@link #begin}. */
    public static void additive(boolean on) {
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, on ? GL11.GL_ONE : GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    public static void end() {
        if (!open) return;
        open = false;
        GL11.glShadeModel(GL11.GL_FLAT);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        FluxDraw.worldEnd();
    }

    private static Tessellator start(int mode) {
        Tessellator t = Tessellator.instance;
        t.startDrawing(mode);
        t.setNormal(nx, ny, nz);
        return t;
    }

    private static void color(Tessellator t, int rgb, float a) {
        t.setColorRGBA_I(rgb & 0xFFFFFF, (int) (Math.max(0, Math.min(1, a)) * 255));
    }

    /**
     * A flat ring (or part of one) lying level at height {@code y}: from radius {@code r0} to {@code r1}, angles
     * {@code a0} to {@code a1} (radians, 0 along +x, turning towards +z).
     */
    public static void ring(double cx, double y, double cz, double r0, double r1, double a0, double a1, int rgbIn,
        float aIn, int rgbOut, float aOut) {
        int seg = Math.max(2, (int) Math.ceil(Math.abs(a1 - a0) / (Math.PI / 24)));
        Tessellator t = start(GL11.GL_QUADS);
        for (int i = 0; i < seg; i++) {
            double p = a0 + (a1 - a0) * i / seg, q = a0 + (a1 - a0) * (i + 1) / seg;
            double cp = Math.cos(p), sp = Math.sin(p), cq = Math.cos(q), sq = Math.sin(q);
            color(t, rgbIn, aIn);
            t.addVertex(cx + cp * r0, y, cz + sp * r0);
            t.addVertex(cx + cq * r0, y, cz + sq * r0);
            color(t, rgbOut, aOut);
            t.addVertex(cx + cq * r1, y, cz + sq * r1);
            t.addVertex(cx + cp * r1, y, cz + sp * r1);
        }
        t.draw();
    }

    /** A standing band round a circle of radius {@code r}, from {@code y0} to {@code y1}. */
    public static void band(double cx, double cz, double r, double y0, double y1, double a0, double a1, int rgb,
        float aBottom, float aTop) {
        int seg = Math.max(2, (int) Math.ceil(Math.abs(a1 - a0) / (Math.PI / 24)));
        Tessellator t = start(GL11.GL_QUADS);
        for (int i = 0; i < seg; i++) {
            double p = a0 + (a1 - a0) * i / seg, q = a0 + (a1 - a0) * (i + 1) / seg;
            double cp = Math.cos(p), sp = Math.sin(p), cq = Math.cos(q), sq = Math.sin(q);
            color(t, rgb, aBottom);
            t.addVertex(cx + cp * r, y0, cz + sp * r);
            t.addVertex(cx + cq * r, y0, cz + sq * r);
            color(t, rgb, aTop);
            t.addVertex(cx + cq * r, y1, cz + sq * r);
            t.addVertex(cx + cp * r, y1, cz + sp * r);
        }
        t.draw();
    }

    /** A glowing disc lying level: {@code aIn} in the middle fading to {@code aOut} at the rim. */
    public static void disc(double cx, double y, double cz, double r, int rgb, float aIn, float aOut) {
        // triangles as quads with a doubled corner: Angelica's tessellator takes quads everywhere
        Tessellator t = start(GL11.GL_QUADS);
        int seg = 32;
        for (int i = 0; i < seg; i++) {
            double p = i * Math.PI * 2 / seg, q = (i + 1) * Math.PI * 2 / seg;
            color(t, rgb, aIn);
            t.addVertex(cx, y, cz);
            t.addVertex(cx, y, cz);
            color(t, rgb, aOut);
            t.addVertex(cx + Math.cos(q) * r, y, cz + Math.sin(q) * r);
            t.addVertex(cx + Math.cos(p) * r, y, cz + Math.sin(p) * r);
        }
        t.draw();
    }

    /**
     * A column of light from {@code y0} to {@code y1}: two crossed planes turned to the camera, {@code a0} at the
     * bottom fading to {@code a1} at the top, bright in the middle and fading to the sides.
     */
    public static void beam(double x, double y0, double z, double y1, double r, int rgb, float a0, float a1) {
        double yaw = Math.toRadians(RenderManager.instance.playerViewY);
        for (int k = 0; k < 2; k++) {
            double ang = yaw + k * Math.PI / 2;
            double dx = Math.cos(ang) * r, dz = Math.sin(ang) * r;
            Tessellator t = start(GL11.GL_QUADS);
            // left half: clear at the edge, bright in the middle
            color(t, rgb, 0);
            t.addVertex(x - dx, y0, z - dz);
            color(t, rgb, a0);
            t.addVertex(x, y0, z);
            color(t, rgb, a1);
            t.addVertex(x, y1, z);
            color(t, rgb, 0);
            t.addVertex(x - dx, y1, z - dz);
            color(t, rgb, a0);
            t.addVertex(x, y0, z);
            color(t, rgb, 0);
            t.addVertex(x + dx, y0, z + dz);
            t.addVertex(x + dx, y1, z + dz);
            color(t, rgb, a1);
            t.addVertex(x, y1, z);
            t.draw();
        }
    }

    /** A light string between two points, a band of width {@code w} turned to the camera. */
    public static void string(double x0, double y0, double z0, double x1, double y1, double z1, double w, int rgb,
        float a0, float a1) {
        // the band's sideways direction: across the line and across the line of sight
        double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        double mx = (x0 + x1) / 2, my = (y0 + y1) / 2, mz = (z0 + z1) / 2;
        double sx = dy * mz - dz * my, sy = dz * mx - dx * mz, sz = dx * my - dy * mx;
        double len = Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (len < 1e-6) return;
        sx = sx / len * w / 2;
        sy = sy / len * w / 2;
        sz = sz / len * w / 2;
        Tessellator t = start(GL11.GL_QUADS);
        color(t, rgb, a0);
        t.addVertex(x0 - sx, y0 - sy, z0 - sz);
        t.addVertex(x0 + sx, y0 + sy, z0 + sz);
        color(t, rgb, a1);
        t.addVertex(x1 + sx, y1 + sy, z1 + sz);
        t.addVertex(x1 - sx, y1 - sy, z1 - sz);
        t.draw();
    }

    /**
     * A crystal: an octahedron of half-width {@code r} and half-height {@code h}, turned {@code spin} radians round the
     * vertical. Its faces shade from {@code light} (upper) to {@code dark} (lower), every other one a little darker.
     */
    public static void crystal(double cx, double cy, double cz, double r, double h, double spin, int light, int dark,
        float a) {
        Tessellator t = start(GL11.GL_QUADS);
        for (int i = 0; i < 4; i++) {
            double p = spin + i * Math.PI / 2, q = p + Math.PI / 2;
            double x0 = cx + Math.cos(p) * r, z0 = cz + Math.sin(p) * r, x1 = cx + Math.cos(q) * r,
                z1 = cz + Math.sin(q) * r;
            float shade = i % 2 == 0 ? 1f : 0.8f;
            int mid = mix(light, dark, 0.4);
            // upper face (a triangle: the tip doubled)
            color(t, light, a * shade);
            t.addVertex(cx, cy + h, cz);
            t.addVertex(cx, cy + h, cz);
            color(t, mid, a * shade);
            t.addVertex(x1, cy, z1);
            t.addVertex(x0, cy, z0);
            // lower face
            color(t, mid, a * shade * 0.9f);
            t.addVertex(x0, cy, z0);
            t.addVertex(x1, cy, z1);
            color(t, dark, a * shade * 0.9f);
            t.addVertex(cx, cy - h, cz);
            t.addVertex(cx, cy - h, cz);
        }
        t.draw();
    }

    /** A thin upright bar from {@code y0} to {@code y1}, four sides, its corners at {@code w} from the middle. */
    public static void strut(double x, double y0, double z, double y1, double w, double spin, int rgb, float a) {
        Tessellator t = start(GL11.GL_QUADS);
        for (int i = 0; i < 4; i++) {
            double p = spin + i * Math.PI / 2, q = p + Math.PI / 2;
            double x0 = x + Math.cos(p) * w, z0 = z + Math.sin(p) * w, x1 = x + Math.cos(q) * w,
                z1 = z + Math.sin(q) * w;
            float shade = i % 2 == 0 ? 1f : 0.75f;
            color(t, rgb, a * shade);
            t.addVertex(x0, y0, z0);
            t.addVertex(x1, y0, z1);
            t.addVertex(x1, y1, z1);
            t.addVertex(x0, y1, z0);
        }
        t.draw();
    }

    /** A level square frame (a slot's outline): half-size {@code half}, line width {@code w}. */
    public static void square(double cx, double y, double cz, double half, double w, int rgb, float a) {
        Tessellator t = start(GL11.GL_QUADS);
        color(t, rgb, a);
        double o = half, i = half - w;
        quad(t, cx - o, cz - o, cx + o, cz - i, y);
        quad(t, cx - o, cz + i, cx + o, cz + o, y);
        quad(t, cx - o, cz - i, cx - i, cz + i, y);
        quad(t, cx + i, cz - i, cx + o, cz + i, y);
        t.draw();
    }

    private static void quad(Tessellator t, double x0, double z0, double x1, double z1, double y) {
        t.addVertex(x0, y, z0);
        t.addVertex(x0, y, z1);
        t.addVertex(x1, y, z1);
        t.addVertex(x1, y, z0);
    }

    /** A level quad between two corners, for planes and sweeps. */
    public static void plane(double x0, double y, double z0, double x1, double z1, int rgb, float a) {
        Tessellator t = start(GL11.GL_QUADS);
        color(t, rgb, a);
        quad(t, x0, z0, x1, z1, y);
        t.draw();
    }

    /** Any four corners, in order. */
    public static void quad(double x0, double y0, double z0, double x1, double z1, double x2, double z2, double x3,
        double z3, int rgb, float a) {
        Tessellator t = start(GL11.GL_QUADS);
        color(t, rgb, a);
        t.addVertex(x0, y0, z0);
        t.addVertex(x1, y0, z1);
        t.addVertex(x2, y0, z2);
        t.addVertex(x3, y0, z3);
        t.draw();
    }

    /** Any quad in space, its corners in order, each corner with its own alpha. */
    public static void quad3(double[] p0, double[] p1, double[] p2, double[] p3, int rgb, float a0, float a1, float a2,
        float a3) {
        Tessellator t = start(GL11.GL_QUADS);
        color(t, rgb, a0);
        t.addVertex(p0[0], p0[1], p0[2]);
        color(t, rgb, a1);
        t.addVertex(p1[0], p1[1], p1[2]);
        color(t, rgb, a2);
        t.addVertex(p2[0], p2[1], p2[2]);
        color(t, rgb, a3);
        t.addVertex(p3[0], p3[1], p3[2]);
        t.draw();
    }

    /** A quad in space shading from {@code near} (corners 0 and 1) to {@code far} (corners 2 and 3). */
    public static void quadShade(double[] p0, double[] p1, double[] p2, double[] p3, int near, int far, float a) {
        Tessellator t = start(GL11.GL_QUADS);
        color(t, near, a);
        t.addVertex(p0[0], p0[1], p0[2]);
        t.addVertex(p1[0], p1[1], p1[2]);
        color(t, far, a);
        t.addVertex(p2[0], p2[1], p2[2]);
        t.addVertex(p3[0], p3[1], p3[2]);
        t.draw();
    }

    public static int mix(int a, int b, double f) {
        f = Math.max(0, Math.min(1, f));
        int r = (int) ((a >> 16 & 0xFF) * (1 - f) + (b >> 16 & 0xFF) * f);
        int g = (int) ((a >> 8 & 0xFF) * (1 - f) + (b >> 8 & 0xFF) * f);
        int bl = (int) ((a & 0xFF) * (1 - f) + (b & 0xFF) * f);
        return r << 16 | g << 8 | bl;
    }
}
