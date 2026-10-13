package com.fluxecho.city.client;

import static com.fluxecho.client.FluxDraw.WHITE;

import java.util.List;

import net.minecraft.client.renderer.Tessellator;

import com.fluxecho.FluxEcho;
import com.fluxecho.city.TileSophonArray;
import com.fluxecho.client.Motes;
import com.fluxecho.logic.ModuleLook;
import com.fluxecho.logic.SophonLook;
import com.fluxecho.render.Shapes;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The Sophon Array's own visuals (0.15.0, CITY.md §4.10 Visuals), on top of what every city module shows
 * ({@link CityRender}); where everything goes and how it moves is {@link SophonLook}'s.
 * <ul>
 * <li>Formed: its struts and lattice give way to a geodesic frame of thin bright struts on the sphere, carried by four
 * bright struts from the podium's roof, round a light point in its middle; the frame shimmers slowly from bottom to
 * top. Undocked it burns low.</li>
 * <li>Docked (idle): the frame and the point in the array's colour, and a soft column of light from the central
 * console up through the oculus to the point (near and mid; near with motes rising in it).</li>
 * <li>Powered (active): the point unfolds into a turning icosahedron, and then a flat circuit plane spreads over the
 * sky {@link SophonLook#CANOPY_Y} over the base: faint tiles, a die, sixteen traces with their vias, a ring bus and a
 * pulse of light flowing out along every trace, fading towards its rim and with distance. Losing power folds it back
 * into the point.</li>
 * <li>A landmark ({@link #landmark}, past the range the rest is drawn in): the frame, its struts, the point or the
 * icosahedron and the canopy, drawn to {@link SophonLook#LANDMARK} blocks and fading out over the last
 * {@link SophonLook#LANDMARK_FADE}; within the range they are the same drawing faded by the same distance, so the two
 * meet without a seam.</li>
 * </ul>
 * Budgets (CITY.md §1.3): the landmark at most {@link SophonLook#landmarkQuads(double, double)} at any distance and
 * unfolding, the fold-back after the power drops included, under {@link SophonLook#BUDGET} (the frame 120, past
 * {@link SophonLook#FRAME_FAR} 60; the struts 4; the point 2; while unfolded the icosahedron 50 and the canopy
 * {@link SophonLook#canopyQuads}); the column {@link SophonLook#quads} on top of the
 * shared drawing, which with 144 sockets stays under 2,000 near, 300 mid and 64 far ({@code SophonLookTest}).
 * <p>
 * Shader-safe as {@link CityRender}: in its frame ({@link CityFrame}) relative to the camera, quads only with the
 * viewer's normal (the icosahedron's triangles as quads with a corner doubled), no depth writes. Animation by the world
 * time alone, and the unfolding by the time since the client heard the power change
 * ({@link TileSophonArray#powerFlip}); nothing counted, spawned or sent, so a frame drawn again behind a light gate is
 * the same. Each part fails on its own.
 */
@SideOnly(Side.CLIENT)
final class SophonRender {

    private static final int CRYSTAL = 0xFFF2FB, STEEL = 0x1C3446;
    /** Line widths: the frame's struts, the strut columns, the icosahedron's edges, a trace, a pulse. */
    private static final double FRAME_W = 0.06, STRUT_W = 0.12, ICO_W = 0.05, TRACE_W = 0.14, PULSE_W = 0.24;

    private static final int FRAME = 0, CORE = 1, CANOPY = 2, BEAM = 3;
    private static final String[] NAMES = { "geodesic frame", "light point", "circuit canopy", "oculus column" };
    private static final boolean[] OFF = new boolean[NAMES.length];

    private SophonRender() {}

    /**
     * Draws the Sophon Array's own visuals; {@code p0} is its distance fade times how far it has formed, {@code cam}
     * the camera in its frame, {@code tier} how much to draw ({@link ModuleLook#tier}), {@code appear} how far it has
     * formed.
     */
    static void draw(TileSophonArray s, CityFrame f, double t, float p0, double[] cam, int tier, float appear) {
        boolean docked = CityRender.docked(s), on = CityRender.on(s);
        double d = sphereDistance(cam);
        // the landmark's parts fade by their own distance, so they carry on unbroken into the landmark's drawing
        landmarkParts(s, f, t, appear * SophonLook.landmarkFade(d), d, docked, on);
        if (docked && tier != ModuleLook.FAR && !OFF[BEAM]) try {
            beam(s, f, t, p0, tier == ModuleLook.NEAR, on);
        } catch (Throwable x) {
            CityRender.recover();
            fail(BEAM, x);
        } finally {
            CityRender.close();
        }
    }

    /**
     * The landmark alone, for a camera past the range the rest is drawn in ({@code NexusRender} calls this or
     * {@link #draw}, never both): the frame, its struts, the point or the icosahedron and, as far as it has unfolded,
     * the canopy. At most {@link SophonLook#landmarkQuads(double, double)} quads at its unfolding.
     */
    static void landmark(TileSophonArray s, double t) {
        if (!s.formed()) return;
        CityFrame f = new CityFrame(s);
        double d = sphereDistance(CityFrame.camera(s));
        float p = SophonLook.landmarkFade(d);
        if (p <= 0.01f) return;
        landmarkParts(s, f, t, p, d, CityRender.docked(s), CityRender.on(s));
    }

    /** The parts the landmark is made of, each failing on its own. */
    private static void landmarkParts(TileSophonArray s, CityFrame f, double t, float p, double d, boolean docked,
        boolean on) {
        if (p <= 0.01f) return;
        float life = on ? 1f : docked ? 0.6f : 0.3f;
        double unfold = SophonLook.unfold(on, since(s));
        if (!OFF[FRAME]) try {
            frame(s, f, t, p, life, SophonLook.frameFreq(d));
        } catch (Throwable x) {
            CityRender.recover();
            fail(FRAME, x);
        } finally {
            CityRender.close();
        }
        if (!OFF[CORE]) try {
            core(s, f, t, p, life, d, SophonLook.icoScale(unfold));
        } catch (Throwable x) {
            CityRender.recover();
            fail(CORE, x);
        } finally {
            CityRender.close();
        }
        double spread = SophonLook.spread(unfold);
        if (spread > 0 && !OFF[CANOPY]) try {
            canopy(s, f, t, p, spread);
        } catch (Throwable x) {
            CityRender.recover();
            fail(CANOPY, x);
        } finally {
            CityRender.close();
        }
    }

    /** The milliseconds since the client heard the array turn on or off; long ago when it never heard it change. */
    private static long since(TileSophonArray s) {
        return s.powerFlip == 0 ? Long.MAX_VALUE : System.currentTimeMillis() - s.powerFlip;
    }

    /** How far the camera ({@code p}, in the array's frame) is from the sphere's middle, in blocks. */
    private static double sphereDistance(double[] p) {
        double dx = p[0] - SophonLook.CX, dy = p[1] - SophonLook.CY, dz = p[2] - SophonLook.CZ;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    // ---- the frame

    /**
     * The geodesic frame (its edges, a slow shimmer climbing them) and the four struts that carry it, thin bright
     * lines in the array's colour paled towards white.
     */
    private static void frame(TileSophonArray s, CityFrame f, double t, float p, float life, int freq) {
        int accent = s.moduleColor(), line = Shapes.mix(accent, WHITE, 0.55);
        float a = (0.45f * life + 0.1f) * p;
        if (a <= 0.01f) return;
        Shapes.begin(true);
        Tessellator tes = CityFrame.start();
        for (double[] e : SophonLook.frame(freq)) {
            double mid = (e[1] + e[4]) / 2;
            float k = a * (0.75f + 0.25f * (float) Math.sin(t * 0.05 - mid * 0.35));
            tes.setColorRGBA_I(line, CityFrame.alpha255(k));
            f.line(tes, e[0], e[1], e[2], e[3], e[4], e[5], FRAME_W);
        }
        tes.setColorRGBA_I(Shapes.mix(accent, WHITE, 0.3), CityFrame.alpha255(a));
        for (double[] e : SophonLook.struts()) f.line(tes, e[0], e[1], e[2], e[3], e[4], e[5], STRUT_W);
        tes.draw();
        Shapes.end();
    }

    // ---- the point and the icosahedron

    /**
     * The middle: a glow and its white core (the light point), and once it unfolds ({@code scale} of its full size) the
     * icosahedron turning round it, its faces shaded from pale to the array's colour and its edges bright.
     */
    private static void core(TileSophonArray s, CityFrame f, double t, float p, float life, double d, double scale) {
        int accent = s.moduleColor();
        double cx = f.x(SophonLook.CX, SophonLook.CZ), cy = f.y(SophonLook.CY), cz = f.z(SophonLook.CX, SophonLook.CZ);
        if (scale > 0.001) {
            double[][] v = SophonLook.ico(t, scale);
            int light = Shapes.mix(CRYSTAL, accent, 0.25), dark = Shapes.mix(accent, STEEL, 0.4);
            float a = (0.35f + 0.25f * life) * p;
            Shapes.begin(false);
            Tessellator tes = CityFrame.start();
            for (int i = 0; i < SophonLook.ICO_FACES.length; i++) {
                int[] q = SophonLook.ICO_FACES[i];
                // a triangle as a quad with its first corner doubled; the faces shade by their height
                double h = (v[q[0]][1] + v[q[1]][1] + v[q[2]][1]) / 3 - SophonLook.CY;
                int rgb = Shapes.mix(dark, light, 0.5 + h / (2 * SophonLook.ICO_R));
                tes.setColorRGBA_I(rgb, CityFrame.alpha255(a * (i % 2 == 0 ? 1f : 0.8f)));
                f.vertex(tes, v[q[0]][0], v[q[0]][1], v[q[0]][2]);
                f.vertex(tes, v[q[0]][0], v[q[0]][1], v[q[0]][2]);
                f.vertex(tes, v[q[1]][0], v[q[1]][1], v[q[1]][2]);
                f.vertex(tes, v[q[2]][0], v[q[2]][1], v[q[2]][2]);
            }
            tes.draw();
            Shapes.additive(true);
            tes = CityFrame.start();
            tes.setColorRGBA_I(Shapes.mix(accent, WHITE, 0.6), CityFrame.alpha255(0.8f * p));
            for (int[] e : SophonLook.ICO_EDGES)
                f.line(tes, v[e[0]][0], v[e[0]][1], v[e[0]][2], v[e[1]][0], v[e[1]][1], v[e[1]][2], ICO_W);
            tes.draw();
            Shapes.end();
        }
        Motes.begin();
        Motes.add(cx, cy, cz, 2.2 + d * 0.004, accent, (0.3f * life + 0.08f) * p);
        Motes.add(cx, cy, cz, 0.45 + d * 0.0015, WHITE, (0.5f * life + 0.3f) * p);
        Motes.end();
    }

    // ---- the canopy

    /**
     * The circuit canopy, spread {@code spread} of the way: its tiles lit as far out as it has spread, the die, each
     * trace drawn out from the die as far as it has spread with its via once it reaches it, the ring bus once the
     * spread passes it, and a pulse flowing out along every trace; all fading towards the rim.
     */
    private static void canopy(TileSophonArray s, CityFrame f, double t, float p, double spread) {
        int accent = s.moduleColor(), trace = Shapes.mix(accent, WHITE, 0.35);
        double y = SophonLook.CANOPY_Y, edge = spread * SophonLook.CANOPY_R;
        Shapes.begin(true);
        Tessellator tes = CityFrame.start();
        // the tiles, lit as the spread passes them
        for (double[] q : SophonLook.tiles()) {
            float k = (float) Math.max(0, Math.min(1, (edge - q[4]) / SophonLook.TILE + 1));
            if (k <= 0) continue;
            float a = 0.07f * p * k * SophonLook.radialFade(q[4]);
            tes.setColorRGBA_I(accent, CityFrame.alpha255(a));
            f.vertex(tes, q[0], y, q[1]);
            f.vertex(tes, q[2], y, q[1]);
            f.vertex(tes, q[2], y, q[3]);
            f.vertex(tes, q[0], y, q[3]);
        }
        // the die in the middle and its rim
        double die = SophonLook.DIE, x0 = SophonLook.CX - die, x1 = SophonLook.CX + die, z0 = SophonLook.CZ - die,
            z1 = SophonLook.CZ + die;
        tes.setColorRGBA_I(accent, CityFrame.alpha255(0.22f * p));
        f.vertex(tes, x0, y, z0);
        f.vertex(tes, x1, y, z0);
        f.vertex(tes, x1, y, z1);
        f.vertex(tes, x0, y, z1);
        tes.setColorRGBA_I(trace, CityFrame.alpha255(0.7f * p));
        f.line(tes, x0, y, z0, x1, y, z0, TRACE_W);
        f.line(tes, x1, y, z0, x1, y, z1, TRACE_W);
        f.line(tes, x1, y, z1, x0, y, z1, TRACE_W);
        f.line(tes, x0, y, z1, x0, y, z0, TRACE_W);
        // the traces, as far out as the canopy has spread, each with its via once it is whole
        List<double[][]> traces = SophonLook.traces();
        for (double[][] tr : traces) {
            double reach = SophonLook.length(tr) * spread, at = 0;
            for (int i = 1; i < tr.length && at < reach; i++) {
                double[] a = tr[i - 1], b = tr[i];
                double seg = Math.hypot(b[0] - a[0], b[1] - a[1]), k = Math.min(1, (reach - at) / seg);
                double ex = a[0] + (b[0] - a[0]) * k, ez = a[1] + (b[1] - a[1]) * k;
                double r = Math.hypot((a[0] + ex) / 2 - SophonLook.CX, (a[1] + ez) / 2 - SophonLook.CZ);
                tes.setColorRGBA_I(trace, CityFrame.alpha255(0.5f * p * SophonLook.radialFade(r)));
                f.line(tes, a[0], y, a[1], ex, y, ez, TRACE_W);
                at += seg;
            }
            if (spread >= 1) {
                double[] v = tr[tr.length - 1];
                double h = SophonLook.VIA, r = Math.hypot(v[0] - SophonLook.CX, v[1] - SophonLook.CZ);
                tes.setColorRGBA_I(trace, CityFrame.alpha255(0.6f * p * SophonLook.radialFade(r)));
                f.vertex(tes, v[0] - h, y, v[1] - h);
                f.vertex(tes, v[0] + h, y, v[1] - h);
                f.vertex(tes, v[0] + h, y, v[1] + h);
                f.vertex(tes, v[0] - h, y, v[1] + h);
            }
        }
        // the ring bus, once the spread has passed it
        if (edge > SophonLook.BUS_R) {
            double[][] bus = SophonLook.bus();
            float k = (float) Math.min(1, (edge - SophonLook.BUS_R) / 4);
            tes.setColorRGBA_I(trace, CityFrame.alpha255(0.4f * p * k * SophonLook.radialFade(SophonLook.BUS_R)));
            for (int i = 0; i < bus.length; i++) {
                double[] a = bus[i], b = bus[(i + 1) % bus.length];
                f.line(tes, a[0], y, a[1], b[0], y, b[1], TRACE_W);
            }
        }
        // the pulses flowing out along the traces
        for (int k = 0; k < traces.size(); k++) {
            double[] q = SophonLook.pulse(k, t, spread);
            if (q == null) continue;
            double r = Math.hypot(q[2] - SophonLook.CX, q[3] - SophonLook.CZ);
            tes.setColorRGBA_I(Shapes.mix(trace, WHITE, 0.6), CityFrame.alpha255(0.95f * p * SophonLook.radialFade(r)));
            f.line(tes, q[0], y, q[1], q[2], y, q[3], PULSE_W);
        }
        tes.draw();
        Shapes.end();
    }

    // ---- the column

    /**
     * The column of light from the central console up through the oculus to the point (or to the icosahedron's
     * underside); near, motes rising in it and a glow where it leaves the console.
     */
    private static void beam(TileSophonArray s, CityFrame f, double t, float p, boolean nearby, boolean on) {
        if (p <= 0.01f) return;
        int accent = s.moduleColor();
        double top = SophonLook.CY - 1;
        double x = f.x(SophonLook.CX, SophonLook.CZ), z = f.z(SophonLook.CX, SophonLook.CZ);
        float a = on ? 0.45f : 0.25f;
        Shapes.begin(true);
        Shapes.beam(
            x,
            f.y(SophonLook.BEAM_FROM),
            z,
            f.y(top),
            SophonLook.BEAM_R,
            Shapes.mix(accent, WHITE, 0.3),
            a * p,
            0.6f * a * p);
        Shapes.end();
        if (!nearby) return;
        Motes.begin();
        Motes.add(x, f.y(SophonLook.BEAM_FROM + 0.3), z, 0.7, accent, 0.3f * p);
        for (int k = 0; k < SophonLook.BEAM_MOTES; k++) {
            double[] m = SophonLook.beamMote(k, t, top);
            Motes.add(x, f.y(m[0]), z, 0.12, WHITE, 0.8f * p * (float) m[1]);
        }
        Motes.end();
    }

    private static void fail(int part, Throwable x) {
        if (OFF[part]) return;
        OFF[part] = true;
        FluxEcho.LOG.warn("The Sophon Array's {} failed and is off until the game restarts", NAMES[part], x);
    }
}
