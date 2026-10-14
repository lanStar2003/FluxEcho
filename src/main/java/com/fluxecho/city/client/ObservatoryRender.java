package com.fluxecho.city.client;

import static com.fluxecho.client.FluxDraw.WHITE;

import net.minecraft.client.renderer.Tessellator;

import com.fluxecho.FluxEcho;
import com.fluxecho.city.TileObservatory;
import com.fluxecho.client.Motes;
import com.fluxecho.logic.ModuleLook;
import com.fluxecho.logic.ObservatoryLook;
import com.fluxecho.logic.SkyLook;
import com.fluxecho.render.Shapes;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The Observatory's own visuals (0.16.0, CITY.md §4.12), on top of what every city module and every sky module shows
 * ({@link CityRender}, {@link SkyRender}); where they go is {@link ObservatoryLook}'s.
 * <ul>
 * <li>Formed, the whole pier (its console, pillars and seat) gives way to the telescope: a steel column from the deck
 * up to a fork, and on it a tube of steel sweeping slowly along the dome's meridian slit, lit rings at both its ends
 * and a glassy objective glowing at its far end.</li>
 * <li>Over the dome the big ring lens floats, turning, rocking a little and bobbing: a ring of steel with a seam of
 * light, a faint glassy fill inside it, a glint running round it. Powered, a thin beam runs from the lens's middle
 * down to the objective.</li>
 * <li>A landmark ({@link #landmark}): the ring lens as a ring of light and its middle's glow.</li>
 * </ul>
 * Budgets ({@link ObservatoryLook#quads}): about 210 near, 45 mid, 16 far, on top of the shared drawing.
 * <p>
 * Shader-safe as {@link CityRender}: in its frame relative to the camera, quads only, a normal towards the viewer, no
 * depth writes (the steel's faces turned away are left out, so it needs no depth), animation by the world time alone.
 * Each part fails on its own.
 */
@SideOnly(Side.CLIENT)
final class ObservatoryRender {

    private static final int STEEL = 0x1C3446, STEEL_LIGHT = 0x2C5470, STEEL_DARK = 0x0E1C28, GLASS = 0xDCE6FF;

    private static final int SCOPE = 0, LENS = 1;
    private static final String[] NAMES = { "telescope", "ring lens" };
    private static final boolean[] OFF = new boolean[NAMES.length];

    private ObservatoryRender() {}

    /**
     * Draws the observatory's own visuals; {@code fade} its effects' distance fade, {@code appear} how far it has
     * formed, {@code cam} the camera in its frame. The ring lens fades by its own distance as the landmark does.
     */
    static void draw(TileObservatory o, CityFrame f, double t, float fade, float appear, double[] cam, int tier) {
        float[] life = new float[1];
        int glow = SkyRender.glow(o, t, life);
        boolean on = CityRender.on(o);
        if (tier != ModuleLook.FAR && fade * appear > 0.01f && !OFF[SCOPE]) try {
            scope(f, t, fade * appear, life[0], glow, cam, tier == ModuleLook.NEAR);
        } catch (Throwable x) {
            CityRender.recover();
            fail(SCOPE, x);
        } finally {
            CityRender.close();
        }
        if (!OFF[LENS]) try {
            lens(f, t, appear * lensFade(f), life[0], glow, cam, tier, on);
        } catch (Throwable x) {
            CityRender.recover();
            fail(LENS, x);
        } finally {
            CityRender.close();
        }
    }

    /** The ring lens as drawn far, as the landmark past the effects' range. */
    static void landmark(TileObservatory o, double t) {
        if (!o.formed() || OFF[LENS]) return;
        try {
            CityFrame f = new CityFrame(o);
            float[] life = new float[1];
            int glow = SkyRender.glow(o, t, life);
            lens(f, t, lensFade(f), life[0], glow, CityFrame.camera(o), ModuleLook.FAR, false);
        } catch (Throwable x) {
            CityRender.recover();
            fail(LENS, x);
        } finally {
            CityRender.close();
        }
    }

    private static float lensFade(CityFrame f) {
        double x = f.x(ObservatoryLook.PIER_X, ObservatoryLook.PIER_Z), y = f.y(ObservatoryLook.LENS_Y),
            z = f.z(ObservatoryLook.PIER_X, ObservatoryLook.PIER_Z);
        return SkyLook.landmarkFade(Math.sqrt(x * x + y * y + z * z));
    }

    // ---- the telescope

    /**
     * The telescope: the column and the fork, the tube's sides turned to the camera (shaded by how they face up), the
     * lit rings at its ends, the objective's glassy disc and glow. Mid, the column, the tube as a band and the glow.
     */
    private static void scope(CityFrame f, double t, float p, float life, int glow, double[] cam, boolean nearby) {
        double e = ObservatoryLook.elevation(t);
        double[] ax = ObservatoryLook.tubeAxis(e);
        double px = ObservatoryLook.PIER_X, py = ObservatoryLook.PIVOT_Y, pz = ObservatoryLook.PIER_Z;
        double[] back = { px - ax[0] * ObservatoryLook.TUBE_BACK, py - ax[1] * ObservatoryLook.TUBE_BACK,
            pz - ax[2] * ObservatoryLook.TUBE_BACK };
        double[] front = ObservatoryLook.objective(e);
        double forkY = py - ObservatoryLook.FORK_DROP;
        Shapes.begin(false);
        Shapes.strut(
            f.x(px, pz),
            f.y(ObservatoryLook.PIER_FOOT),
            f.z(px, pz),
            f.y(forkY),
            ObservatoryLook.PIER_W,
            Math.PI / 4,
            STEEL_LIGHT,
            0.95f * p);
        Tessellator tes = CityFrame.start();
        if (!nearby) {
            tes.setColorRGBA_I(STEEL_LIGHT, CityFrame.alpha255(0.95f * p));
            f.line(tes, back[0], back[1], back[2], front[0], front[1], front[2], 2 * ObservatoryLook.TUBE_R);
            tes.draw();
        } else {
            // the fork's arms either side, from the column's head to the pivot
            tes.setColorRGBA_I(STEEL, CityFrame.alpha255(0.95f * p));
            for (int s = -1; s <= 1; s += 2)
                f.line(tes, px + s * ObservatoryLook.FORK, forkY, pz, px + s * ObservatoryLook.FORK, py, pz, 0.14);
            // the tube's sides, those turned to the camera
            double[] e1 = { 1, 0, 0 }, e2 = { 0, -ax[2], ax[1] };
            int n = ObservatoryLook.TUBE_SIDES;
            double r = ObservatoryLook.TUBE_R;
            for (int k = 0; k < n; k++) {
                double a0 = 2 * Math.PI * k / n, a1 = 2 * Math.PI * (k + 1) / n, am = (a0 + a1) / 2;
                double[] out = { Math.cos(am) * e1[0] + Math.sin(am) * e2[0],
                    Math.cos(am) * e1[1] + Math.sin(am) * e2[1], Math.cos(am) * e1[2] + Math.sin(am) * e2[2] };
                double mx = (back[0] + front[0]) / 2 + out[0] * r, my = (back[1] + front[1]) / 2 + out[1] * r,
                    mz = (back[2] + front[2]) / 2 + out[2] * r;
                if ((cam[0] - mx) * out[0] + (cam[1] - my) * out[1] + (cam[2] - mz) * out[2] <= 0) continue;
                int shade = out[1] > 0.3 ? STEEL_LIGHT : out[1] < -0.3 ? STEEL_DARK : STEEL;
                tes.setColorRGBA_I(shade, CityFrame.alpha255(0.97f * p));
                double[] b0 = ring(back, e1, e2, r, a0), b1 = ring(back, e1, e2, r, a1),
                    f1 = ring(front, e1, e2, r, a1), f0 = ring(front, e1, e2, r, a0);
                f.vertex(tes, b0[0], b0[1], b0[2]);
                f.vertex(tes, b1[0], b1[1], b1[2]);
                f.vertex(tes, f1[0], f1[1], f1[2]);
                f.vertex(tes, f0[0], f0[1], f0[2]);
            }
            tes.draw();
            Shapes.additive(true);
            tes = CityFrame.start();
            float a = p * life;
            tes.setColorRGBA_I(glow, CityFrame.alpha255(0.7f * a + 0.1f * p));
            for (double[] c : new double[][] { back, front }) for (int k = 0; k < n; k++) {
                double[] u = ring(c, e1, e2, r + 0.03, 2 * Math.PI * k / n),
                    v = ring(c, e1, e2, r + 0.03, 2 * Math.PI * (k + 1) / n);
                f.line(tes, u[0], u[1], u[2], v[0], v[1], v[2], 0.05);
            }
            // the objective: a glassy disc a hair over the tube's end
            double[] o = { front[0] + ax[0] * 0.02, front[1] + ax[1] * 0.02, front[2] + ax[2] * 0.02 };
            for (int k = 0; k < n; k++) {
                double[] u = ring(o, e1, e2, r * 0.9, 2 * Math.PI * k / n),
                    v = ring(o, e1, e2, r * 0.9, 2 * Math.PI * (k + 1) / n);
                tes.setColorRGBA_I(GLASS, CityFrame.alpha255(0.35f * p));
                f.vertex(tes, o[0], o[1], o[2]);
                f.vertex(tes, o[0], o[1], o[2]);
                tes.setColorRGBA_I(glow, CityFrame.alpha255(0.2f * p));
                f.vertex(tes, v[0], v[1], v[2]);
                f.vertex(tes, u[0], u[1], u[2]);
            }
            tes.draw();
        }
        Shapes.end();
        Motes.begin();
        Motes.add(
            f.x(front[0], front[2]),
            f.y(front[1]),
            f.z(front[0], front[2]),
            0.5,
            glow,
            0.5f * p * life + 0.1f * p);
        Motes.end();
    }

    /** A point of the circle of radius {@code r} round {@code c} in the plane of {@code e1}, {@code e2}. */
    private static double[] ring(double[] c, double[] e1, double[] e2, double r, double a) {
        double ca = Math.cos(a) * r, sa = Math.sin(a) * r;
        return new double[] { c[0] + ca * e1[0] + sa * e2[0], c[1] + ca * e1[1] + sa * e2[1],
            c[2] + ca * e1[2] + sa * e2[2] };
    }

    // ---- the ring lens

    /**
     * The ring lens over the dome: near each segment's faces turned to the camera (steel), a seam of light along its
     * top, the glassy fill inside it and two glows; mid its top or bottom and its seam; far a ring of light. Powered,
     * near and mid, a beam from its middle down to the telescope's objective.
     */
    private static void lens(CityFrame f, double t, float p, float life, int glow, double[] cam, int tier, boolean on) {
        if (p <= 0.01f) return;
        int seg = ObservatoryLook.LENS_SEGMENTS[tier];
        double turn = ObservatoryLook.lensTurn(t), tilt = ObservatoryLook.lensTilt(t), bob = ObservatoryLook.lensBob(t);
        double r = ObservatoryLook.LENS_R, w = ObservatoryLook.LENS_W / 2, h = ObservatoryLook.LENS_H / 2;
        float a = p * life;
        int light = Shapes.mix(glow, WHITE, 0.35);
        if (tier == ModuleLook.FAR) {
            Shapes.begin(true);
            Tessellator tes = CityFrame.start();
            tes.setColorRGBA_I(light, CityFrame.alpha255(0.6f * a + 0.15f * p));
            for (int k = 0; k < seg; k++) {
                double[] u = ObservatoryLook.lensPoint(turn + 2 * Math.PI * k / seg, r, 0, tilt, bob),
                    v = ObservatoryLook.lensPoint(turn + 2 * Math.PI * (k + 1) / seg, r, 0, tilt, bob);
                f.line(tes, u[0], u[1], u[2], v[0], v[1], v[2], 0.5);
            }
            tes.draw();
            Shapes.end();
            return;
        }
        boolean nearby = tier == ModuleLook.NEAR;
        // which way the lens's top faces: its normal, rocked about the across axis
        double ny = Math.cos(tilt), nz = -Math.sin(tilt);
        double[] mid = ObservatoryLook.lensPoint(0, 0, 0, tilt, bob);
        boolean above = (cam[1] - mid[1]) * ny + (cam[2] - mid[2]) * nz > 0;
        Shapes.begin(false);
        Tessellator tes = CityFrame.start();
        for (int k = 0; k < seg; k++) {
            double a0 = turn + 2 * Math.PI * k / seg, a1 = turn + 2 * Math.PI * (k + 1) / seg;
            double dy = above ? h : -h;
            quad(
                tes,
                f,
                ObservatoryLook.lensPoint(a0, r - w, dy, tilt, bob),
                ObservatoryLook.lensPoint(a1, r - w, dy, tilt, bob),
                ObservatoryLook.lensPoint(a1, r + w, dy, tilt, bob),
                ObservatoryLook.lensPoint(a0, r + w, dy, tilt, bob),
                above ? STEEL_LIGHT : STEEL_DARK,
                0.95f * p);
            if (!nearby) continue;
            for (int band = 0; band < 2; band++) {
                double rr = band == 0 ? r + w : r - w;
                double[] m0 = ObservatoryLook.lensPoint((a0 + a1) / 2, rr, 0, tilt, bob);
                double ox = m0[0] - mid[0], oy = m0[1] - mid[1], oz = m0[2] - mid[2];
                double toward = (cam[0] - m0[0]) * ox + (cam[1] - m0[1]) * oy + (cam[2] - m0[2]) * oz;
                // the outer band from outside the ring, the inner band from inside it
                if (band == 0 ? toward <= 0 : toward >= 0) continue;
                quad(
                    tes,
                    f,
                    ObservatoryLook.lensPoint(a0, rr, -h, tilt, bob),
                    ObservatoryLook.lensPoint(a1, rr, -h, tilt, bob),
                    ObservatoryLook.lensPoint(a1, rr, h, tilt, bob),
                    ObservatoryLook.lensPoint(a0, rr, h, tilt, bob),
                    band == 0 ? STEEL : STEEL_DARK,
                    0.95f * p);
            }
        }
        tes.draw();
        Shapes.additive(true);
        tes = CityFrame.start();
        tes.setColorRGBA_I(light, CityFrame.alpha255(0.75f * a + 0.1f * p));
        for (int k = 0; k < seg; k++) {
            double[] u = ObservatoryLook.lensPoint(turn + 2 * Math.PI * k / seg, r, h + 0.02, tilt, bob),
                v = ObservatoryLook.lensPoint(turn + 2 * Math.PI * (k + 1) / seg, r, h + 0.02, tilt, bob);
            f.line(tes, u[0], u[1], u[2], v[0], v[1], v[2], 0.06);
        }
        if (nearby) {
            // the glassy fill: brighter at its middle, clear at the ring
            int fill = seg / 2;
            for (int k = 0; k < fill; k++) {
                double[] u = ObservatoryLook.lensPoint(turn + 2 * Math.PI * k / fill, r - w, 0, tilt, bob),
                    v = ObservatoryLook.lensPoint(turn + 2 * Math.PI * (k + 1) / fill, r - w, 0, tilt, bob);
                tes.setColorRGBA_I(GLASS, CityFrame.alpha255(0.1f * p));
                f.vertex(tes, mid[0], mid[1], mid[2]);
                f.vertex(tes, mid[0], mid[1], mid[2]);
                tes.setColorRGBA_I(glow, CityFrame.alpha255(0.03f * p));
                f.vertex(tes, v[0], v[1], v[2]);
                f.vertex(tes, u[0], u[1], u[2]);
            }
        }
        if (on) {
            double[] o = ObservatoryLook.objective(ObservatoryLook.elevation(t));
            float k = 0.35f * a * (0.8f + 0.2f * (float) Math.sin(t * 0.2));
            tes.setColorRGBA_I(light, CityFrame.alpha255(k));
            f.line(tes, mid[0], mid[1], mid[2], o[0], o[1], o[2], 0.05);
            if (nearby) {
                tes.setColorRGBA_I(glow, CityFrame.alpha255(0.3f * k));
                f.line(tes, mid[0], mid[1], mid[2], o[0], o[1], o[2], 0.4);
            }
        }
        tes.draw();
        Shapes.end();
        if (!nearby) return;
        Motes.begin();
        Motes.add(f.x(mid[0], mid[2]), f.y(mid[1]), f.z(mid[0], mid[2]), 1.6, glow, 0.25f * a + 0.05f * p);
        double[] g = ObservatoryLook.lensPoint(turn * 3, r, h + 0.05, tilt, bob);
        Motes.add(f.x(g[0], g[2]), f.y(g[1]), f.z(g[0], g[2]), 0.45, WHITE, 0.6f * a + 0.1f * p);
        Motes.end();
    }

    private static void quad(Tessellator tes, CityFrame f, double[] p0, double[] p1, double[] p2, double[] p3, int rgb,
        float a) {
        tes.setColorRGBA_I(rgb, CityFrame.alpha255(a));
        f.vertex(tes, p0[0], p0[1], p0[2]);
        f.vertex(tes, p1[0], p1[1], p1[2]);
        f.vertex(tes, p2[0], p2[1], p2[2]);
        f.vertex(tes, p3[0], p3[1], p3[2]);
    }

    private static void fail(int part, Throwable x) {
        if (OFF[part]) return;
        OFF[part] = true;
        FluxEcho.LOG.warn("The Observatory's {} failed and is off until the game restarts", NAMES[part], x);
    }
}
