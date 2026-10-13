package com.fluxecho.city.client;

import static com.fluxecho.client.FluxDraw.WHITE;

import java.util.List;

import net.minecraft.client.renderer.Tessellator;

import com.fluxecho.FluxEcho;
import com.fluxecho.city.TileBloodCovenant;
import com.fluxecho.client.Motes;
import com.fluxecho.logic.BloodLook;
import com.fluxecho.logic.ModuleLook;
import com.fluxecho.render.Shapes;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The Blood Covenant Altar's own visuals (0.13.0, CITY.md §4.4 Visuals), on top of what every city module shows
 * ({@link CityRender}); where everything goes and how it moves is {@link BloodLook}'s.
 * <ul>
 * <li>Formed: the blood orb floats over the altar under the oculus, a red sphere breathing slowly (undocked it burns
 * low), strings of light hanging from the oculus's rim down to it with drops of light running down them, short drips
 * hanging under it.</li>
 * <li>Docked: red light seeps along the three tiers' cornices, swells running slowly round them; inside (near), the
 * ritual circle's lit tiles glow red and every cradle shows a small orb turning over its socket, a ring turning round
 * it (brighter for a cradle that keeps an orb).</li>
 * <li>Powered: the orb beats like a heart; near, a pulse runs out from the dais along the spokes and over the circle at
 * every beat, and droplets fall from the orb onto the altar.</li>
 * </ul>
 * Budgets (CITY.md §1.3, quads a frame on top of {@link CityRender}'s, {@link BloodLook#quads}): far, the cornices'
 * octagons and the orb's glow; mid, the orb's sphere of 5 x 8, its strings and the octagons; near, the sphere of 8 x
 * 12, the strings and drips, a line and a halo on every outer face of the three cornices, the circle's tiles, the
 * cradles' orbs and, powered, the pulse and the droplets. With the plinth line, the plate and the sockets' samples
 * ({@link ModuleLook#baseQuads}) that keeps the altar under 2,000 near, 300 mid and 64 far ({@code BloodLookTest}).
 * <p>
 * Shader-safe as {@link CityRender}: everything in its frame ({@link CityFrame}) relative to the camera, quads only, a
 * normal towards the viewer on every vertex, no depth writes (the spheres' faces turned away are left out); the orb's
 * body is blended, its glow, the strings and the seeping light are added. Animation by the world time alone, nothing
 * counted, spawned or sent. Each part fails on its own.
 */
@SideOnly(Side.CLIENT)
final class BloodCovenantRender {

    /** The orb's dark rim and the blood's light. */
    private static final int DARK = 0x3A0008, BRIGHT = 0xFF8A96;
    private static final double AXIS = BloodLook.AXIS;
    /** How far out of its face the cornice's line lies, its core's and its halo's half-heights. */
    private static final double FACE_OFF = 0.04, CORE = 0.035, HALO = 0.2;

    private static final int ORB = 0, STRINGS = 1, CORNICES = 2, RITUAL = 3, CRADLES = 4, DROPLETS = 5;
    private static final String[] NAMES = { "blood orb", "light strings", "seeping cornices", "ritual circle",
        "cradle orbs", "droplets" };
    private static final boolean[] OFF = new boolean[NAMES.length];

    /** Each cornice's outer faces and its octagon, worked out once. */
    private static final List<int[]>[] FACES;
    private static final double[][][] HULLS;

    static {
        @SuppressWarnings("unchecked")
        List<int[]>[] faces = new List[BloodLook.CORNICES.length];
        HULLS = new double[BloodLook.CORNICES.length][][];
        for (int i = 0; i < faces.length; i++) {
            faces[i] = BloodLook.cornice(BloodLook.CORNICES[i]);
            HULLS[i] = BloodLook.hull(BloodLook.CORNICES[i]);
        }
        FACES = faces;
    }

    private BloodCovenantRender() {}

    /**
     * Draws the Blood Covenant Altar's own visuals; {@code p0} is its distance fade times how far it has formed,
     * {@code cam} the camera in its frame, {@code tier} how much to draw ({@link ModuleLook#tier}).
     */
    static void draw(TileBloodCovenant b, CityFrame f, double t, float p0, double[] cam, int tier) {
        if (p0 <= 0.01f) return;
        boolean docked = CityRender.docked(b), on = CityRender.on(b), nearby = tier == ModuleLook.NEAR;
        float life = p0 * (on ? 1f : docked ? 0.6f : 0.3f);
        int accent = b.moduleColor();
        double beat = BloodLook.heartbeat(t, on), r = BloodLook.orbRadius(t, on);
        if (!OFF[ORB]) try {
            orb(f, cam, r, beat, life, tier, accent);
        } catch (Throwable x) {
            CityRender.recover();
            fail(ORB, x);
        } finally {
            CityRender.close();
        }
        if (tier != ModuleLook.FAR && !OFF[STRINGS]) try {
            strings(f, t, r, life, nearby, accent);
        } catch (Throwable x) {
            CityRender.recover();
            fail(STRINGS, x);
        } finally {
            CityRender.close();
        }
        if (!docked) return;
        if (!OFF[CORNICES]) try {
            cornices(f, t, life, nearby, accent);
        } catch (Throwable x) {
            CityRender.recover();
            fail(CORNICES, x);
        } finally {
            CityRender.close();
        }
        if (!nearby) return;
        if (!OFF[RITUAL]) try {
            ritual(f, t, p0, life, on, accent);
        } catch (Throwable x) {
            CityRender.recover();
            fail(RITUAL, x);
        } finally {
            CityRender.close();
        }
        if (!OFF[CRADLES]) try {
            cradles(b, f, t, cam, life, accent);
        } catch (Throwable x) {
            CityRender.recover();
            fail(CRADLES, x);
        } finally {
            CityRender.close();
        }
        if (on && !OFF[DROPLETS]) try {
            droplets(f, t, p0, accent);
        } catch (Throwable x) {
            CityRender.recover();
            fail(DROPLETS, x);
        } finally {
            CityRender.close();
        }
    }

    // ---- the orb

    /**
     * The blood orb: near and mid a sphere of the tier's latitudes and meridians, only the faces turned to the camera,
     * dark at its rim and bright where it faces the viewer, brighter as it beats; then its glow and a highlight added
     * over it. Far, the glows alone.
     */
    private static void orb(CityFrame f, double[] cam, double r, double beat, float life, int tier, int accent) {
        int lat = BloodLook.ORB_LAT[tier], lon = BloodLook.ORB_LON[tier];
        double[] c = { AXIS, BloodLook.ORB_Y, AXIS };
        if (lat > 0) {
            int light = Shapes.mix(accent, BRIGHT, 0.5 * beat);
            Shapes.begin(false);
            Tessellator tes = CityFrame.start();
            sphere(tes, f, c, r, lat, lon, cam, DARK, light, 0.85f * life);
            tes.draw();
            Shapes.end();
        }
        Motes.begin();
        Motes.add(f.x(c[0], c[2]), f.y(c[1]), f.z(c[0], c[2]), r * 2.2, accent, life * (0.25f + 0.25f * (float) beat));
        Motes.add(
            f.x(c[0], c[2]),
            f.y(c[1] + r * 0.45),
            f.z(c[0], c[2]),
            r * 0.5,
            Shapes.mix(BRIGHT, WHITE, 0.5),
            life * (0.2f + 0.3f * (float) beat));
        Motes.end();
    }

    /**
     * A sphere into the open batch, its faces turned to the camera only: each vertex shading from {@code rim} where the
     * surface turns away to {@code face} where it looks at the viewer.
     */
    private static void sphere(Tessellator tes, CityFrame f, double[] c, double r, int lat, int lon, double[] cam,
        int rim, int face, float a) {
        for (int i = 0; i < lat; i++) for (int j = 0; j < lon; j++) {
            double[] p00 = point(c, r, i, lat, j, lon), p01 = point(c, r, i, lat, j + 1, lon),
                p11 = point(c, r, i + 1, lat, j + 1, lon), p10 = point(c, r, i + 1, lat, j, lon);
            double mx = (p00[0] + p01[0] + p11[0] + p10[0]) / 4, my = (p00[1] + p01[1] + p11[1] + p10[1]) / 4,
                mz = (p00[2] + p01[2] + p11[2] + p10[2]) / 4;
            if ((mx - c[0]) * (cam[0] - mx) + (my - c[1]) * (cam[1] - my) + (mz - c[2]) * (cam[2] - mz) <= 0) continue;
            shade(tes, f, c, r, p00, cam, rim, face, a);
            shade(tes, f, c, r, p01, cam, rim, face, a);
            shade(tes, f, c, r, p11, cam, rim, face, a);
            shade(tes, f, c, r, p10, cam, rim, face, a);
        }
    }

    /** A point of a sphere round {@code c} ({@link BloodLook#spherePoint}). */
    private static double[] point(double[] c, double r, int i, int lat, int j, int lon) {
        return BloodLook.spherePoint(c[0], c[1], c[2], r, i, lat, j, lon);
    }

    /** One vertex of a sphere, coloured by how squarely it faces the camera. */
    private static void shade(Tessellator tes, CityFrame f, double[] c, double r, double[] p, double[] cam, int rim,
        int face, float a) {
        double vx = cam[0] - p[0], vy = cam[1] - p[1], vz = cam[2] - p[2];
        double vl = Math.sqrt(vx * vx + vy * vy + vz * vz);
        double facing = vl < 1e-9 ? 1
            : Math.max(0, ((p[0] - c[0]) * vx + (p[1] - c[1]) * vy + (p[2] - c[2]) * vz) / (r * vl));
        tes.setColorRGBA_I(
            Shapes.mix(rim, face, 0.2 + 0.8 * facing),
            CityFrame.alpha255(a * (0.6f + 0.4f * (float) facing)));
        f.vertex(tes, p[0], p[1], p[2]);
    }

    // ---- the strings

    /**
     * The strings of light from the oculus's rim down to the orb (near in pieces, sagging, a drop of light running down
     * each; mid a straight line each), and near the drips hanging under the orb, a drop at each tip.
     */
    private static void strings(CityFrame f, double t, double r, float life, boolean nearby, int accent) {
        int pieces = BloodLook.STRING_PIECES[nearby ? ModuleLook.NEAR : ModuleLook.MID];
        int light = Shapes.mix(accent, WHITE, 0.25);
        Shapes.begin(true);
        Tessellator tes = CityFrame.start();
        tes.setColorRGBA_I(light, CityFrame.alpha255(0.45f * life));
        for (int k = 0; k < BloodLook.STRINGS; k++) {
            double[] a = BloodLook.stringPoint(k, 0, r);
            for (int j = 1; j <= pieces; j++) {
                double[] p = BloodLook.stringPoint(k, j / (double) pieces, r);
                f.line(tes, a[0], a[1], a[2], p[0], p[1], p[2], 0.04);
                a = p;
            }
        }
        if (nearby) for (int j = 0; j < BloodLook.DRIPS; j++) {
            double[] top = BloodLook.dripTop(j, r);
            tes.setColorRGBA_I(accent, CityFrame.alpha255(0.5f * life));
            f.line(tes, top[0], top[1], top[2], top[0], top[1] - BloodLook.dripLength(j, t), top[2], 0.035);
        }
        tes.draw();
        Shapes.end();
        if (!nearby) return;
        Motes.begin();
        for (int k = 0; k < BloodLook.STRINGS; k++) {
            double[] p = BloodLook.stringPoint(k, BloodLook.drop(k, t), r);
            Motes.add(f.x(p[0], p[2]), f.y(p[1]), f.z(p[0], p[2]), 0.08, light, 0.8f * life);
        }
        for (int j = 0; j < BloodLook.DRIPS; j++) {
            double[] top = BloodLook.dripTop(j, r);
            double y = top[1] - BloodLook.dripLength(j, t);
            Motes.add(f.x(top[0], top[2]), f.y(y), f.z(top[0], top[2]), 0.07, accent, 0.7f * life);
        }
        Motes.end();
    }

    // ---- the cornices

    /**
     * The red light seeping along the three cornices, swells running slowly round them: near, a line on every outer
     * face of each cornice with a soft halo; further off, each cornice's octagon as eight lines.
     */
    private static void cornices(CityFrame f, double t, float life, boolean nearby, int accent) {
        Shapes.begin(true);
        Tessellator tes = CityFrame.start();
        for (int c = 0; c < BloodLook.CORNICES.length; c++) {
            double y = BloodLook.CORNICES[c] + BloodLook.CORNICE_LIGHT;
            if (!nearby) {
                tes.setColorRGBA_I(accent, CityFrame.alpha255(0.5f * life));
                double[][] h = HULLS[c];
                for (int i = 0; i < h.length; i++) {
                    double[] a = h[i], b2 = h[(i + 1) % h.length];
                    f.line(tes, a[0], y, a[1], b2[0], y, b2[1], 0.08);
                }
                continue;
            }
            List<int[]> faces = FACES[c];
            int n = faces.size();
            for (int[] face : faces) {
                float k = life * (0.25f + 0.5f * (float) BloodLook.seep(face[4], n, t, c));
                double ax, az, bx, bz;
                if (face[2] != 0) {
                    ax = bx = (face[2] < 0 ? face[0] : face[0] + 1) + face[2] * FACE_OFF;
                    az = face[1];
                    bz = face[1] + 1;
                } else {
                    az = bz = (face[3] < 0 ? face[1] : face[1] + 1) + face[3] * FACE_OFF;
                    ax = face[0];
                    bx = face[0] + 1;
                }
                tes.setColorRGBA_I(accent, CityFrame.alpha255(k));
                f.vertex(tes, ax, y - CORE, az);
                f.vertex(tes, bx, y - CORE, bz);
                f.vertex(tes, bx, y + CORE, bz);
                f.vertex(tes, ax, y + CORE, az);
                f.softBand(tes, ax, az, bx, bz, y, HALO, accent, 0.4f * k, 0.4f * k);
            }
        }
        tes.draw();
        Shapes.end();
    }

    // ---- inside

    /**
     * The ritual circle's lit tiles glowing red a little over the floor, breathing; powered, the heartbeat's pulse
     * running out over them from the dais with each beat, a glow at its front on each spoke.
     */
    private static void ritual(CityFrame f, double t, float p0, float life, boolean on, int accent) {
        Shapes.begin(true);
        Tessellator tes = CityFrame.start();
        double y = BloodLook.RITUAL_Y;
        for (int[] c : BloodLook.ritual()) {
            double dx = c[0] + 0.5 - AXIS, dz = c[1] + 0.5 - AXIS, rr = Math.sqrt(dx * dx + dz * dz);
            float a = life * (0.16f + 0.06f * (float) Math.sin(t * 0.05 + rr * 0.6));
            if (on) a += 0.5f * p0 * (float) BloodLook.pulse(rr, t);
            tes.setColorRGBA_I(accent, CityFrame.alpha255(a));
            f.vertex(tes, c[0], y, c[1]);
            f.vertex(tes, c[0] + 1, y, c[1]);
            f.vertex(tes, c[0] + 1, y, c[1] + 1);
            f.vertex(tes, c[0], y, c[1] + 1);
        }
        tes.draw();
        Shapes.end();
        if (!on) return;
        Motes.begin();
        for (int k = 0; k < BloodLook.SPOKES; k++) {
            double[] m = BloodLook.pulseMote(k, t);
            Motes.add(f.x(m[0], m[2]), f.y(m[1]), f.z(m[0], m[2]), 0.3, accent, 0.7f * p0 * (float) m[3]);
        }
        Motes.end();
    }

    /**
     * Over every cradle's socket a small orb turning (only its faces turned to the camera), a ring turning round it
     * and a glow; brighter over a cradle that keeps an orb.
     */
    private static void cradles(TileBloodCovenant b, CityFrame f, double t, double[] cam, float life, int accent) {
        int n = BloodLook.cradles()
            .size();
        double[][] at = new double[n][];
        float[] k = new float[n];
        for (int i = 0; i < n; i++) {
            at[i] = BloodLook.cradleOrb(i, t);
            k[i] = life * (b.sample(BloodLook.cradleSlot(i)) != null ? 1f : 0.45f);
        }
        Shapes.begin(false);
        Tessellator tes = CityFrame.start();
        for (int i = 0; i < n; i++) sphere(
            tes,
            f,
            at[i],
            BloodLook.CRADLE_ORB,
            BloodLook.CRADLE_LAT,
            BloodLook.CRADLE_LON,
            cam,
            DARK,
            accent,
            0.85f * k[i]);
        tes.draw();
        Shapes.end();
        Shapes.begin(true);
        tes = CityFrame.start();
        double ring = BloodLook.CRADLE_ORB + 0.14;
        for (int i = 0; i < n; i++) {
            double turn = BloodLook.cradleTurn(i, t), tilt = 0.45;
            tes.setColorRGBA_I(Shapes.mix(accent, WHITE, 0.3), CityFrame.alpha255(0.6f * k[i]));
            int m = BloodLook.CRADLE_RING;
            double[] prev = null;
            for (int j = 0; j <= m; j++) {
                double a = turn + 2 * Math.PI * j / m;
                // a ring tilted about the line at the orb's own turn, so it wobbles as it goes round
                double[] p = { at[i][0] + ring * Math.cos(a), at[i][1] + ring * Math.sin(a) * Math.sin(tilt),
                    at[i][2] + ring * Math.sin(a) * Math.cos(tilt) };
                if (prev != null) f.line(tes, prev[0], prev[1], prev[2], p[0], p[1], p[2], 0.02);
                prev = p;
            }
        }
        tes.draw();
        Shapes.end();
        Motes.begin();
        for (int i = 0; i < n; i++)
            Motes.add(f.x(at[i][0], at[i][2]), f.y(at[i][1]), f.z(at[i][0], at[i][2]), 0.45, accent, 0.3f * k[i]);
        Motes.end();
    }

    /** The droplets falling from the orb onto the altar: a streak above each, a glow on it. */
    private static void droplets(CityFrame f, double t, float p0, int accent) {
        double[][] d = new double[BloodLook.DROPLETS][];
        for (int k = 0; k < d.length; k++) d[k] = BloodLook.droplet(k, t);
        Shapes.begin(true);
        Tessellator tes = CityFrame.start();
        // a droplet behind the name plate (its alpha 0, BloodLook.PLATE_Y) is left out
        for (double[] drop : d) {
            if (drop[1] <= 0) continue;
            tes.setColorRGBA_I(accent, CityFrame.alpha255(0.6f * p0 * (float) drop[1]));
            f.line(tes, AXIS, drop[0], AXIS, AXIS, drop[0] + drop[2], AXIS, 0.05);
        }
        tes.draw();
        Shapes.end();
        Motes.begin();
        for (double[] drop : d) if (drop[1] > 0) Motes.add(
            f.x(AXIS, AXIS),
            f.y(drop[0]),
            f.z(AXIS, AXIS),
            BloodLook.DROP_GLOW,
            Shapes.mix(accent, BRIGHT, 0.5),
            p0 * (float) drop[1]);
        Motes.end();
    }

    private static void fail(int part, Throwable x) {
        if (OFF[part]) return;
        OFF[part] = true;
        FluxEcho.LOG.warn("The Blood Covenant Altar's {} failed and is off until the game restarts", NAMES[part], x);
    }
}
