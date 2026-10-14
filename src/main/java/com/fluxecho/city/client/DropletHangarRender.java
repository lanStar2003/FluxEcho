package com.fluxecho.city.client;

import static com.fluxecho.client.FluxDraw.WHITE;

import java.util.Arrays;

import net.minecraft.client.renderer.Tessellator;

import com.fluxecho.FluxEcho;
import com.fluxecho.city.TileDropletHangar;
import com.fluxecho.client.Motes;
import com.fluxecho.logic.DropletHangarShape;
import com.fluxecho.logic.DropletLook;
import com.fluxecho.logic.ModuleLook;
import com.fluxecho.render.Shapes;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The Droplet Hangar's own visuals (0.16.0, CITY.md §4.12 Visuals), on top of what every city module and every sky
 * module shows ({@link CityRender}, {@link SkyRender}); where they go is {@link DropletLook}'s.
 * <ul>
 * <li>Over each cradle a mirror droplet: a teardrop lying level, nose to the front, every face of its skin turned to
 * the
 * camera coloured with the sky or the ground it reflects there (the chrome gradient: a pale sky darkening upwards, a
 * bright horizon line, a dark ground), a white glint on it. Docked it hovers and bobs, each in its own rhythm, swaying
 * a
 * little; undocked it rests on its socket, still. A soft glow lies on its cradle under it.</li>
 * <li>The canopy ring over the kiosk (its ring segments dissolve) is a ring of light breathing in the hangar's
 * colour.</li>
 * </ul>
 * Budgets ({@link DropletLook#quads}): about 700 near, 150 mid, 16 far, on top of the shared drawing.
 * <p>
 * Shader-safe as {@link CityRender}: in its frame relative to the camera, quads only, a normal towards the viewer, no
 * depth writes (a droplet is convex and only its faces turned to the camera are drawn, the farthest droplet first, so
 * the mirrors need no depth; the light is added), animation by the world time alone. Each part fails on its own.
 */
@SideOnly(Side.CLIENT)
final class DropletHangarRender {

    private static final int DROPLETS = 0, CANOPY = 1;
    private static final String[] NAMES = { "droplets", "canopy ring" };
    private static final boolean[] OFF = new boolean[NAMES.length];

    private DropletHangarRender() {}

    /**
     * Draws the hangar's own visuals; {@code p} its distance fade times how far it has formed, {@code cam} the camera
     * in
     * its frame.
     */
    static void draw(TileDropletHangar h, CityFrame f, double t, float p, double[] cam, int tier) {
        if (p <= 0.01f) return;
        float[] life = new float[1];
        int glow = SkyRender.glow(h, t, life);
        boolean docked = CityRender.docked(h);
        if (!OFF[DROPLETS]) try {
            droplets(f, t, p, life[0], glow, docked, cam, tier);
        } catch (Throwable x) {
            CityRender.recover();
            fail(DROPLETS, x);
        } finally {
            CityRender.close();
        }
        if (!OFF[CANOPY]) try {
            canopy(f, t, p * life[0], glow, tier);
        } catch (Throwable x) {
            CityRender.recover();
            fail(CANOPY, x);
        } finally {
            CityRender.close();
        }
    }

    // ---- the droplets

    /**
     * The one grid a droplet's skin is filled into, as large as the near tier's (the render thread's alone; every draw
     * fills what it reads first), so drawing the droplets makes no arrays a frame.
     */
    private static final double[][][] GRID = new double[DropletLook.RINGS[ModuleLook.NEAR]
        + 1][DropletLook.SIDES[ModuleLook.NEAR] + 1][6];

    /**
     * The eight droplets, the farthest first: near and mid their skins (the faces turned to the camera, each in its
     * chrome colour), a glint and a glow on the cradle under each; far only their glints.
     */
    private static void droplets(CityFrame f, double t, float p, float life, int glow, boolean docked, double[] cam,
        int tier) {
        int n = DropletHangarShape.CRADLES.length, rings = DropletLook.RINGS[tier], sides = DropletLook.SIDES[tier];
        double[][] at = new double[n][];
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            at[i] = DropletLook.centre(i, t, docked);
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> Double.compare(dist(at[b], cam), dist(at[a], cam)));
        if (rings > 0) {
            Shapes.begin(false);
            Tessellator tes = CityFrame.start();
            double[][][] g = GRID;
            for (int i : order) {
                double[] c = at[i];
                // the skin from the tier's table, turned by the droplet's sway, into the one grid (0.16.0)
                double cs = StrictMath.cos(c[3]), sn = StrictMath.sin(c[3]);
                for (int k = 0; k <= rings; k++)
                    for (int j = 0; j <= sides; j++) DropletLook.skinInto(c, cs, sn, tier, k, j, g[k][j]);
                for (int k = 0; k < rings; k++) for (int j = 0; j < sides; j++) {
                    double[] a = g[k][j], b = g[k][j + 1], d = g[k + 1][j + 1], e = g[k + 1][j];
                    double mx = (a[0] + b[0] + d[0] + e[0]) / 4, my = (a[1] + b[1] + d[1] + e[1]) / 4,
                        mz = (a[2] + b[2] + d[2] + e[2]) / 4;
                    double nx = a[3] + b[3] + d[3] + e[3], ny = a[4] + b[4] + d[4] + e[4],
                        nz = a[5] + b[5] + d[5] + e[5];
                    // the camera's side of the face only: a droplet is convex, so it needs no depth
                    if ((cam[0] - mx) * nx + (cam[1] - my) * ny + (cam[2] - mz) * nz <= 0) continue;
                    corner(tes, f, a, cam, p);
                    corner(tes, f, b, cam, p);
                    corner(tes, f, d, cam, p);
                    corner(tes, f, e, cam, p);
                }
            }
            tes.draw();
            Shapes.end();
        }
        Motes.begin();
        for (int i : order) {
            double[] c = at[i];
            // the glint high on the nose's side, and near and mid the glow on the cradle under it
            Motes.add(f.x(c[0], c[2] - 0.3), f.y(c[1] + 0.25), f.z(c[0], c[2] - 0.3), 0.18, WHITE, 0.8f * p);
            if (rings == 0) continue;
            double up = c[1] - DropletLook.REST + 0.3;
            Motes.add(
                f.x(c[0], c[2]),
                f.y(2.05),
                f.z(c[0], c[2]),
                0.55 + 0.25 * up,
                glow,
                (0.25f + 0.25f * life) * p / (float) (1 + up));
        }
        Motes.end();
    }

    /** A corner of a droplet's face in its chrome colour: the sky or the ground the camera sees reflected there. */
    private static void corner(Tessellator tes, CityFrame f, double[] q, double[] cam, float p) {
        // the normal and the view along the world's axes: the frame turned, and up the same
        double nx = f.dirX(q[3], q[5]), nz = f.dirZ(q[3], q[5]);
        double vx = f.dirX(q[0] - cam[0], q[2] - cam[2]), vz = f.dirZ(q[0] - cam[0], q[2] - cam[2]);
        int rgb = DropletLook.chrome(nx, q[4], nz, vx, q[1] - cam[1], vz);
        tes.setColorRGBA_I(rgb, CityFrame.alpha255(p));
        f.vertex(tes, q[0], q[1], q[2]);
    }

    private static double dist(double[] c, double[] cam) {
        double dx = c[0] - cam[0], dy = c[1] - cam[1], dz = c[2] - cam[2];
        return dx * dx + dy * dy + dz * dz;
    }

    // ---- the canopy ring

    /** The canopy ring over the kiosk: a ring of light, breathing, near with a halo. */
    private static void canopy(CityFrame f, double t, float a, int glow, int tier) {
        if (a <= 0.01f) return;
        int n = DropletLook.CANOPY_PIECES[tier];
        float k = a * (0.6f + 0.2f * (float) Math.sin(t * 0.06));
        Shapes.begin(true);
        Tessellator tes = CityFrame.start();
        for (int pass = 0; pass < (tier == ModuleLook.NEAR ? 2 : 1); pass++) {
            tes.setColorRGBA_I(
                pass == 0 ? Shapes.mix(glow, WHITE, 0.35) : glow,
                CityFrame.alpha255(pass == 0 ? 0.8f * k : 0.2f * k));
            for (int i = 0; i < n; i++) {
                double[] u = DropletLook.canopy(2 * Math.PI * i / n), v = DropletLook.canopy(2 * Math.PI * (i + 1) / n);
                f.line(tes, u[0], u[1], u[2], v[0], v[1], v[2], pass == 0 ? 0.07 : 0.4);
            }
        }
        tes.draw();
        Shapes.end();
    }

    private static void fail(int part, Throwable x) {
        if (OFF[part]) return;
        OFF[part] = true;
        FluxEcho.LOG.warn("The Droplet Hangar's {} failed and is off until the game restarts", NAMES[part], x);
    }
}
