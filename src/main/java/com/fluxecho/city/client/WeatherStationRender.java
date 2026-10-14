package com.fluxecho.city.client;

import static com.fluxecho.client.FluxDraw.WHITE;

import java.util.Arrays;
import java.util.List;

import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.world.World;

import com.fluxecho.FluxEcho;
import com.fluxecho.city.TileWeatherStation;
import com.fluxecho.client.Motes;
import com.fluxecho.logic.Mix;
import com.fluxecho.logic.ModuleLook;
import com.fluxecho.logic.SkyLook;
import com.fluxecho.logic.WeatherLook;
import com.fluxecho.render.Shapes;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The Weather Station's own visuals (0.16.0, CITY.md §4.12 Visuals), on top of what every city module and every sky
 * module shows ({@link CityRender}, {@link SkyRender}); where they go is {@link WeatherLook}'s.
 * <ul>
 * <li>Formed, the dissolved weather mast is a slim steel mast with a line of light up it; at its top an anemometer of
 * three cups turns and under it a wind vane swings with the wind; over each vane pylon's ring a small rotor
 * turns. They turn faster as the weather worsens.</li>
 * <li>Over the mast a cloud churns slowly: soft puffs of grey-white, a wisp in fair weather, thickening and darkening
 * as the rain the server has coming nears (the forecast the station is told, {@link TileWeatherStation#clientRainAt}),
 * full while it rains, darkest in a thunderstorm; the puffs are drawn from the farthest to the nearest, so they lie
 * over one another as a cloud does.</li>
 * <li>Now and then a mini lightning flickers: a jagged bolt from inside the cloud down onto the mast's top (or across
 * in
 * the cloud) for a few stuttering ticks, lighting the cloud; rarely in fair weather, more often as rain gathers and in
 * a storm.</li>
 * <li>A landmark ({@link #landmark}): the cloud as it is drawn far, and its flashes.</li>
 * </ul>
 * Without power the mast and the rotors are lit dim amber and the cloud still churns; undocked it all burns low.
 * Budgets ({@link WeatherLook#quads}): about 190 near, 90 mid, 32 far, on top of the shared drawing.
 * <p>
 * Shader-safe as {@link CityRender}: in its frame relative to the camera, quads only, a normal towards the viewer, no
 * depth writes (the cloud's puffs blend over one another, the light adds up), animation by the world time, the
 * client's weather and the forecast alone (the flashes from a hash of the time). Each part fails on its own.
 */
@SideOnly(Side.CLIENT)
final class WeatherStationRender {

    private static final int STEEL = 0x2C5470, CLOUD_LIGHT = 0xF2F6FA, CLOUD_DARK = 0x58626F, BOLT = 0xEAF4FF;

    private static final int MAST = 0, CLOUD = 1, FLASH = 2;
    private static final String[] NAMES = { "mast and vanes", "cloud", "lightning" };
    private static final boolean[] OFF = new boolean[NAMES.length];

    private WeatherStationRender() {}

    /**
     * Draws the station's own visuals; {@code fade} is its effects' distance fade, {@code appear} how far it has
     * formed.
     * The cloud and its flashes fade by their own distance as the landmark does, so the two meet without a seam.
     */
    static void draw(TileWeatherStation s, CityFrame f, double t, float fade, float appear, int tier) {
        World w = s.getWorldObj();
        if (w == null) return;
        double d = density(s, w, t);
        float[] life = new float[1];
        int glow = SkyRender.glow(s, t, life);
        float p = fade * appear;
        if (p > 0.01f && tier != ModuleLook.FAR && !OFF[MAST]) try {
            mast(f, t, d, p, life[0], glow, tier == ModuleLook.NEAR);
        } catch (Throwable x) {
            CityRender.recover();
            fail(MAST, x);
        } finally {
            CityRender.close();
        }
        float sky = appear * cloudFade(f);
        if (sky > 0.01f) sky(s, f, t, d, w, sky, tier);
    }

    /** The cloud and its flashes alone, as the landmark past the effects' range. */
    static void landmark(TileWeatherStation s, double t) {
        World w = s.getWorldObj();
        if (w == null || !s.formed()) return;
        CityFrame f = new CityFrame(s);
        float fade = cloudFade(f);
        if (fade <= 0.01f) return;
        sky(s, f, t, density(s, w, t), w, fade, ModuleLook.FAR);
    }

    /** How much of the cloud shows at its distance from the camera, as a landmark fades. */
    private static float cloudFade(CityFrame f) {
        double x = f.x(WeatherLook.MAST_X, WeatherLook.MAST_Z), y = f.y(WeatherLook.CLOUD_Y),
            z = f.z(WeatherLook.MAST_X, WeatherLook.MAST_Z);
        return SkyLook.landmarkFade(Math.sqrt(x * x + y * y + z * z));
    }

    /** The cloud and its flashes, each its own part. */
    private static void sky(TileWeatherStation s, CityFrame f, double t, double d, World w, float p, int tier) {
        if (!OFF[CLOUD]) try {
            cloud(f, t, d, p, tier);
        } catch (Throwable x) {
            CityRender.recover();
            fail(CLOUD, x);
        } finally {
            CityRender.close();
        }
        if (!OFF[FLASH]) try {
            flash(s, f, t, d, thunder(w, t), p, tier);
        } catch (Throwable x) {
            CityRender.recover();
            fail(FLASH, x);
        } finally {
            CityRender.close();
        }
    }

    /** How thick the cloud is now: the client's rain and thunder, and the forecast the station was told. */
    private static double density(TileWeatherStation s, World w, double t) {
        float pt = (float) (t - Math.floor(t));
        double rainIn = s.clientRainAt < 0 ? -1 : s.clientRainAt - t;
        return WeatherLook.density(w.getRainStrength(pt), thunder(w, t), rainIn);
    }

    private static double thunder(World w, double t) {
        return w.getWeightedThunderStrength((float) (t - Math.floor(t)));
    }

    // ---- the mast

    /**
     * The mast as a slim steel strut with a line of light up it, the anemometer's arms (near with their cups and a
     * glow on its hub), the wind vane, and each pylon's rotor (near with a glow on its hub).
     */
    private static void mast(CityFrame f, double t, double d, float p, float life, int glow, boolean nearby) {
        double mx = f.x(WeatherLook.MAST_X, WeatherLook.MAST_Z), mz = f.z(WeatherLook.MAST_X, WeatherLook.MAST_Z);
        Shapes.begin(false);
        Shapes.strut(
            mx,
            f.y(WeatherLook.MAST_FOOT),
            mz,
            f.y(WeatherLook.MAST_TOP),
            WeatherLook.MAST_W,
            0.4,
            STEEL,
            0.95f * p);
        Shapes.additive(true);
        Tessellator tes = CityFrame.start();
        float a = p * life;
        tes.setColorRGBA_I(glow, CityFrame.alpha255(0.55f * a));
        f.line(
            tes,
            WeatherLook.MAST_X,
            WeatherLook.MAST_FOOT,
            WeatherLook.MAST_Z - WeatherLook.MAST_W - 0.02,
            WeatherLook.MAST_X,
            WeatherLook.MAST_TOP,
            WeatherLook.MAST_Z - WeatherLook.MAST_W - 0.02,
            0.04);
        // the anemometer: the arms out to the cups, near the cups as small bright marks across them
        int light = Shapes.mix(glow, WHITE, 0.4);
        List<double[]> cups = WeatherLook.cups(WeatherLook.rotor(t, d));
        for (double[] c : cups) {
            tes.setColorRGBA_I(light, CityFrame.alpha255(0.7f * a + 0.15f * p));
            f.line(
                tes,
                WeatherLook.MAST_X,
                WeatherLook.ROTOR_Y,
                WeatherLook.MAST_Z,
                c[0],
                WeatherLook.ROTOR_Y,
                c[1],
                0.05);
            if (!nearby) continue;
            double ux = c[0] - WeatherLook.MAST_X, uz = c[1] - WeatherLook.MAST_Z, l = Math.hypot(ux, uz);
            double sx = -uz / l * WeatherLook.CUP, sz = ux / l * WeatherLook.CUP;
            tes.setColorRGBA_I(glow, CityFrame.alpha255(0.8f * a + 0.15f * p));
            f.line(
                tes,
                c[0] - sx,
                WeatherLook.ROTOR_Y,
                c[1] - sz,
                c[0] + sx,
                WeatherLook.ROTOR_Y,
                c[1] + sz,
                WeatherLook.CUP * 1.4);
        }
        // the wind vane: its shaft and its tail fin, wider at the tail
        double yaw = WeatherLook.vaneYaw(t, d), hx = Math.cos(yaw) * WeatherLook.VANE_LEN / 2,
            hz = Math.sin(yaw) * WeatherLook.VANE_LEN / 2;
        tes.setColorRGBA_I(light, CityFrame.alpha255(0.6f * a + 0.15f * p));
        f.line(
            tes,
            WeatherLook.MAST_X - hx,
            WeatherLook.VANE_Y,
            WeatherLook.MAST_Z - hz,
            WeatherLook.MAST_X + hx,
            WeatherLook.VANE_Y,
            WeatherLook.MAST_Z + hz,
            0.05);
        tes.setColorRGBA_I(glow, CityFrame.alpha255(0.5f * a + 0.1f * p));
        f.taper(
            tes,
            WeatherLook.MAST_X - hx * 0.3,
            WeatherLook.VANE_Y,
            WeatherLook.MAST_Z - hz * 0.3,
            WeatherLook.MAST_X - hx,
            WeatherLook.VANE_Y,
            WeatherLook.MAST_Z - hz,
            0.05,
            0.5);
        // the pylons' rotors
        for (int i = 0; i < WeatherLook.PYLON_X.length; i++) {
            tes.setColorRGBA_I(light, CityFrame.alpha255(0.6f * a + 0.15f * p));
            for (double[] b : WeatherLook.blades(i, WeatherLook.pylonRotor(t, d, i))) f.line(
                tes,
                WeatherLook.PYLON_X[i],
                WeatherLook.PYLON_ROTOR_Y,
                WeatherLook.PYLON_Z,
                b[0],
                WeatherLook.PYLON_ROTOR_Y,
                b[1],
                0.07);
        }
        tes.draw();
        Shapes.end();
        if (!nearby) return;
        Motes.begin();
        Motes.add(mx, f.y(WeatherLook.ROTOR_Y), mz, 0.45, glow, 0.6f * a);
        for (int i = 0; i < WeatherLook.PYLON_X.length; i++) Motes.add(
            f.x(WeatherLook.PYLON_X[i], WeatherLook.PYLON_Z),
            f.y(WeatherLook.PYLON_ROTOR_Y),
            f.z(WeatherLook.PYLON_X[i], WeatherLook.PYLON_Z),
            0.35,
            glow,
            0.5f * a);
        Motes.end();
    }

    // ---- the cloud

    /**
     * The cloud's puffs as soft fans turned to the camera, light grey to storm grey, the farthest first (blended over
     * one another, not added), each fading out to its rim.
     */
    private static void cloud(CityFrame f, double t, double d, float p, int tier) {
        int n = WeatherLook.puffs(tier), sides = WeatherLook.PUFF_SIDES[tier];
        double[][] puffs = new double[n][];
        for (int i = 0; i < n; i++) {
            double[] q = WeatherLook.puff(i, t, d);
            double x = f.x(q[0], q[2]), y = f.y(q[1]), z = f.z(q[0], q[2]);
            puffs[i] = new double[] { x, y, z, q[3], q[4], q[5], x * x + y * y + z * z };
        }
        Arrays.sort(puffs, (a, b) -> Double.compare(b[6], a[6]));
        double yaw = Math.toRadians(RenderManager.instance.playerViewY),
            pitch = Math.toRadians(RenderManager.instance.playerViewX);
        double rx = Math.cos(yaw), rz = Math.sin(yaw), ux = -Math.sin(pitch) * Math.sin(yaw), uy = Math.cos(pitch),
            uz = Math.sin(pitch) * Math.cos(yaw);
        Shapes.begin(false);
        Tessellator tes = CityFrame.start();
        for (double[] q : puffs) {
            float a = (float) q[4] * p;
            if (a <= 0.005f) continue;
            int rgb = Shapes.mix(CLOUD_LIGHT, CLOUD_DARK, q[5]);
            double s = q[3];
            for (int k = 0; k < sides; k++) {
                double a0 = 2 * Math.PI * k / sides, a1 = 2 * Math.PI * (k + 1) / sides;
                double c0 = Math.cos(a0) * s, s0 = Math.sin(a0) * s, c1 = Math.cos(a1) * s, s1 = Math.sin(a1) * s;
                tes.setColorRGBA_I(rgb, CityFrame.alpha255(a));
                tes.addVertex(q[0], q[1], q[2]);
                tes.addVertex(q[0], q[1], q[2]);
                tes.setColorRGBA_I(rgb, 0);
                tes.addVertex(q[0] + c1 * rx + s1 * ux, q[1] + s1 * uy, q[2] + c1 * rz + s1 * uz);
                tes.addVertex(q[0] + c0 * rx + s0 * ux, q[1] + s0 * uy, q[2] + c0 * rz + s0 * uz);
            }
        }
        tes.draw();
        Shapes.end();
    }

    // ---- the lightning

    /**
     * The mini lightning while a flash is lit: its jagged bolt (a bright core and, near and mid, a halo) and a glow
     * lighting the cloud from inside.
     */
    private static void flash(TileWeatherStation s, CityFrame f, double t, double d, double thunder, float p,
        int tier) {
        long seed = Mix.seed(s.xCoord, s.yCoord, s.zCoord);
        long w = WeatherLook.flash(seed, t, d, thunder);
        if (w < 0) return;
        double[][] bolt = WeatherLook.bolt(seed, w);
        int halo = s.moduleColor();
        Shapes.begin(true);
        Tessellator tes = CityFrame.start();
        for (int k = 0; k + 1 < bolt.length; k++) {
            double[] a = bolt[k], b = bolt[k + 1];
            tes.setColorRGBA_I(BOLT, CityFrame.alpha255(0.95f * p));
            f.line(tes, a[0], a[1], a[2], b[0], b[1], b[2], 0.07);
            if (tier == ModuleLook.FAR) continue;
            tes.setColorRGBA_I(halo, CityFrame.alpha255(0.3f * p));
            f.line(tes, a[0], a[1], a[2], b[0], b[1], b[2], 0.45);
        }
        tes.draw();
        Shapes.end();
        Motes.begin();
        double[] c = bolt[0];
        Motes.add(f.x(c[0], c[2]), f.y(c[1]), f.z(c[0], c[2]), 4.5, Shapes.mix(halo, WHITE, 0.5), 0.55f * p);
        Motes.end();
    }

    private static void fail(int part, Throwable x) {
        if (OFF[part]) return;
        OFF[part] = true;
        FluxEcho.LOG.warn("The Weather Station's {} failed and is off until the game restarts", NAMES[part], x);
    }
}
