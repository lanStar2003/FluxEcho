package com.fluxecho.city.client;

import static com.fluxecho.client.FluxDraw.WHITE;

import net.minecraft.client.renderer.Tessellator;
import net.minecraft.world.World;

import com.fluxecho.FluxEcho;
import com.fluxecho.city.TileSkySails;
import com.fluxecho.client.Motes;
import com.fluxecho.logic.ModuleLook;
import com.fluxecho.logic.SailLook;
import com.fluxecho.logic.SkyLook;
import com.fluxecho.logic.SkySailsShape;
import com.fluxecho.render.Shapes;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The Sky Sail Array's own visuals (0.16.0, CITY.md §4.12 Visuals), on top of what every city module and every sky
 * module shows ({@link CityRender}, {@link SkyRender}); where they go is {@link SailLook}'s.
 * <ul>
 * <li>Three huge sails of gold light, one between each pair of masts, hanging from a yard between the masts' tops as
 * they stand in the world (lower over high ground, where the world's top clips the masts), turned about the yard
 * towards the sun (by night towards the moon, fainter) as far as each has room among the masts ({@link SailLook#room}),
 * bellying away from it and fluttering; brighter towards the yard and where they face the light, their edges burning
 * brighter still.</li>
 * <li>Near, gold light streams in from the sky along the light's way onto each sail, and each mast's top glows.</li>
 * <li>A landmark ({@link #landmark}): the sails as they are drawn far, a sheet and its edges each, so the array's gold
 * shows from across the land.</li>
 * </ul>
 * In gold while docked and powered, amber without power, a dim grey-blue undocked. Budgets ({@link SailLook#quads}):
 * about 340 near, 80 mid, 27 far, on top of the shared drawing.
 * <p>
 * Shader-safe as {@link CityRender}: in its frame relative to the camera, quads only, a normal towards the viewer,
 * light added without depth writes, animation by the world time and the sky's angle alone. Each part fails on its own.
 */
@SideOnly(Side.CLIENT)
final class SkySailsRender {

    private static final int SAIL = 0;
    private static final String[] NAMES = { "light sails" };
    private static final boolean[] OFF = new boolean[NAMES.length];

    private SkySailsRender() {}

    /**
     * Draws the array's own visuals; {@code appear} is how far it has formed: the sails fade by their own distance as
     * the landmark does, so the two meet without a seam.
     */
    static void draw(TileSkySails s, CityFrame f, double t, float appear, int tier) {
        if (OFF[SAIL]) return;
        try {
            sails(s, f, t, appear * fade(f), tier);
        } catch (Throwable x) {
            CityRender.recover();
            fail(SAIL, x);
        } finally {
            CityRender.close();
        }
    }

    /** The sails as drawn far, as the landmark past the effects' range. */
    static void landmark(TileSkySails s, double t) {
        if (!s.formed() || OFF[SAIL]) return;
        try {
            CityFrame f = new CityFrame(s);
            sails(s, f, t, fade(f), ModuleLook.FAR);
        } catch (Throwable x) {
            CityRender.recover();
            fail(SAIL, x);
        } finally {
            CityRender.close();
        }
    }

    /** How much of the sails shows at the distance of their middle, as a landmark fades. */
    private static float fade(CityFrame f) {
        double x = f.x(SkySailsShape.CTRL_X + 0.5, 15), y = f.y(9), z = f.z(SkySailsShape.CTRL_X + 0.5, 15);
        return SkyLook.landmarkFade(Math.sqrt(x * x + y * y + z * z));
    }

    /**
     * The three sails: each a grid of light turned to the sky's light, its edges, near the rays streaming onto it; near
     * a glow on each mast's top.
     */
    private static void sails(TileSkySails s, CityFrame f, double t, float p, int tier) {
        World w = s.getWorldObj();
        if (w == null || p <= 0.01f) return;
        float[] life = new float[1];
        int rgb = SkyRender.glow(s, t, life);
        double[] sky = SailLook.light(w.getCelestialAngle((float) (t - Math.floor(t))));
        // the light's way in the module's frame (CityFrame's turn is its own inverse)
        double lx = sky[0] * f.fz - sky[2] * f.fx, ly = sky[1], lz = -sky[0] * f.fx - sky[2] * f.fz;
        if (sky[3] * life[0] * p <= 0.005f) return;
        double yard = SailLook.yard(s.yCoord - SkySailsShape.CTRL_Y), drop = SailLook.height(yard);
        int nx = SailLook.GRID[tier][0], ny = SailLook.GRID[tier][1];
        boolean nearby = tier == ModuleLook.NEAR, far = tier == ModuleLook.FAR;
        int edge = Shapes.mix(rgb, WHITE, 0.45);
        Shapes.begin(true);
        Tessellator tes = CityFrame.start();
        float brightest = 0;
        for (int sail = 0; sail < SailLook.SAILS; sail++) {
            // each sail turns to the light as far as it has room to (SailLook#room)
            double tilt = SailLook.tilt(sail, ly, lz);
            int side = SailLook.lit(tilt, ly, lz);
            float bright = (float) SailLook.glow(tilt, lx, ly, lz, sky[3]) * life[0] * p;
            brightest = Math.max(brightest, bright);
            if (bright <= 0.005f) continue;
            double[][][] g = new double[nx + 1][ny + 1][];
            for (int i = 0; i <= nx; i++) for (int j = 0; j <= ny; j++)
                g[i][j] = SailLook.point(sail, i / (double) nx, j / (double) ny, yard, drop, tilt, side, t);
            // the sheet, brighter towards the yard, shimmering a little
            for (int i = 0; i < nx; i++) for (int j = 0; j < ny; j++) {
                double v = (j + 0.5) / ny;
                float a = bright * (0.12f + 0.12f * (float) (1 - v))
                    * (0.85f + 0.15f * (float) Math.sin(t * 0.05 + i * 1.3 + j * 0.9 + sail));
                tes.setColorRGBA_I(rgb, CityFrame.alpha255(a));
                vertex(tes, f, g[i][j]);
                vertex(tes, f, g[i + 1][j]);
                vertex(tes, f, g[i + 1][j + 1]);
                vertex(tes, f, g[i][j + 1]);
            }
            // its edges: the yard, the foot and the two sides, near with a halo
            for (int pass = 0; pass < (nearby ? 2 : 1); pass++) {
                boolean halo = pass == 1;
                tes.setColorRGBA_I(halo ? rgb : edge, CityFrame.alpha255((halo ? 0.18f : 0.6f) * bright));
                double wd = halo ? 0.35 : 0.06;
                for (int i = 0; i < nx; i++) {
                    seg(tes, f, g[i][0], g[i + 1][0], wd);
                    seg(tes, f, g[i][ny], g[i + 1][ny], wd);
                }
                for (int j = 0; j < ny; j++) {
                    seg(tes, f, g[0][j], g[0][j + 1], wd);
                    seg(tes, f, g[nx][j], g[nx][j + 1], wd);
                }
            }
            if (!nearby) continue;
            // the light streaming in from the sky onto it, fading away from the sail
            for (int r = 0; r < SailLook.RAYS; r++) {
                double[] q = SailLook.point(sail, (r + 0.5) / SailLook.RAYS, 0.45, yard, drop, tilt, side, t);
                double l = Math.sqrt(lx * lx + ly * ly + lz * lz);
                double[] mid = { q[0] + lx / l * SailLook.RAY_LEN * 0.4, q[1] + ly / l * SailLook.RAY_LEN * 0.4,
                    q[2] + lz / l * SailLook.RAY_LEN * 0.4 };
                double[] end = { q[0] + lx / l * SailLook.RAY_LEN, q[1] + ly / l * SailLook.RAY_LEN,
                    q[2] + lz / l * SailLook.RAY_LEN };
                float k = bright * (0.1f + 0.06f * (float) Math.sin(t * 0.03 + r * 2.1 + sail * 1.3));
                tes.setColorRGBA_I(rgb, CityFrame.alpha255(k));
                f.taper(tes, q[0], q[1], q[2], mid[0], mid[1], mid[2], 0.5, 0.9);
                tes.setColorRGBA_I(rgb, CityFrame.alpha255(0.4f * k));
                f.taper(tes, mid[0], mid[1], mid[2], end[0], end[1], end[2], 0.9, 1.4);
            }
        }
        tes.draw();
        Shapes.end();
        if (!nearby) return;
        Motes.begin();
        for (int[] m : SkySailsShape.MASTS) {
            double x = com.fluxecho.logic.SkyRing.MID + m[0] + 0.5, z = com.fluxecho.logic.SkyRing.MID + m[1] + 0.5;
            Motes.add(f.x(x, z), f.y(yard), f.z(x, z), 0.4, edge, 0.6f * brightest + 0.1f * p);
        }
        Motes.end();
    }

    private static void vertex(Tessellator tes, CityFrame f, double[] q) {
        f.vertex(tes, q[0], q[1], q[2]);
    }

    private static void seg(Tessellator tes, CityFrame f, double[] a, double[] b, double w) {
        f.line(tes, a[0], a[1], a[2], b[0], b[1], b[2], w);
    }

    private static void fail(int part, Throwable x) {
        if (OFF[part]) return;
        OFF[part] = true;
        FluxEcho.LOG.warn("The Sky Sail Array's {} failed and is off until the game restarts", NAMES[part], x);
    }
}
