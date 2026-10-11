package com.fluxecho.city.client;

import static com.fluxecho.client.FluxDraw.AMBER;
import static com.fluxecho.client.FluxDraw.WHITE;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.renderer.Tessellator;

import com.fluxecho.Config;
import com.fluxecho.FluxEcho;
import com.fluxecho.city.TileDomeGenerator;
import com.fluxecho.client.Motes;
import com.fluxecho.client.Motifs;
import com.fluxecho.logic.DomeGeneratorShape;
import com.fluxecho.logic.DomeLook;
import com.fluxecho.logic.ModuleLook;
import com.fluxecho.nexus.ClientTiles;
import com.fluxecho.nexus.TileMultiblock;
import com.fluxecho.nexus.TileNexus;
import com.fluxecho.render.Shapes;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The Echo Dome Generator's own visuals (0.12.0, CITY.md §4.8 Visuals), on top of what every city module shows
 * ({@link CityRender}); where they go is {@link DomeLook}'s.
 * <ul>
 * <li>Forming: a guard-blue collar runs up each leg as its pillars give way, and a plane of light rises through the
 * pod.</li>
 * <li>Formed on its legs: each leg is a tapered rib of steel following the curve its cells were rounded from, cut into
 * segments whose joints pulse (a wave of light climbing the six legs into the pod while it runs), a line of light up
 * its inner face. The emitter nodes on the roof are small crystals; the emitter crystal floats over the roof, threads
 * of light running up to it from the nodes.</li>
 * <li>Docked (idle, CITY.md §4.8): the dome, a hemisphere of radius {@link DomeLook#shellRadius} over the forum,
 * nearly invisible: only a soft ring where it meets the ground and a few hexagonal cells shimmering near the apex. Six
 * arcs crackle from the nodes out to the dome. Without power the dome and the arcs flicker faintly in amber; powered,
 * the arcs, the crystal and the joints are at full brightness.</li>
 * <li>A landmark ({@link #landmark}, past the range the rest is drawn in): the dome while docked, drawn to
 * {@link DomeLook#LANDMARK} blocks, and the moored legs' far lines in any dock state (their pillars are hidden at every
 * distance, so the pod never seems to float before phase IV), both fading out over the same last
 * {@link DomeLook#LANDMARK_FADE} blocks.</li>
 * </ul>
 * Budgets (CITY.md §1.3, quads a frame on top of {@link CityRender}'s): the legs near (their own box within
 * {@link CityRender#NEAR} blocks) {@value #LEGS} legs of {@link DomeLook#LEG_SEGMENTS} segments, at most four faces
 * and a joint collar of four a segment and a line (about 700), mid {@value #MID_SEGMENTS} segments without collars
 * (about 120), far {@link DomeLook#FAR_LEG_LINES} lines a leg in their pieces (36, {@link DomeLook#farLegQuads}); the
 * nodes and the crystal 64 and 8 motes; the arcs {@value #ARC_PIECES} pieces with a halo near (168), half the pieces
 * mid, none far; the dome at most 280 at phase II and 352 at its widest ({@link DomeLook#shellQuads}). With the
 * plinth line, the plate and the ward sockets the generator stays under 2,000 near and 300 mid, and the landmark (the
 * dome, the far legs and the crystal's glow) under {@link DomeLook#BUDGET} at any distance and any radius the dome
 * grows to ({@link DomeLook#landmarkQuads}).
 * <p>
 * Shader-safe as {@link CityRender}: in its frame ({@link CityFrame}) relative to the camera, quads only, a normal
 * towards the viewer, no depth writes (the ribs' faces turned away are left out, so the solid ribs need no depth),
 * animation by the world time alone. Each part fails on its own.
 */
@SideOnly(Side.CLIENT)
final class DomeGeneratorRender {

    private static final int STEEL = 0x1C3446, STEEL_LIGHT = 0x2C5470, STEEL_DARK = 0x0E1C28, ARC = 0xDCEBFF,
        IDLE = 0x4A6A80, CRYSTAL = 0xE8F4FF;
    private static final int LEGS = DomeGeneratorShape.LEGS, MID_SEGMENTS = 4, ARC_PIECES = 14;
    /** The gap between two segments of a rib, as a share of a segment. */
    private static final double GAP = 0.04;
    /** The legs' box in the pod's frame (the blueprint's: the feet on the forum to the pod's floor). */
    private static final double LEG_X0 = -DomeGeneratorShape.OFFSET[0], LEG_X1 = LEG_X0 + DomeGeneratorShape.BP_WIDTH,
        LEG_Z0 = -DomeGeneratorShape.OFFSET[2], LEG_Z1 = LEG_Z0 + DomeGeneratorShape.BP_DEPTH, LEG_Y0 = DomeLook.CY;

    /** The legs' axes at the segment ends, near, mid and far: {@code [leg][point]{x, y, z, s}}. */
    private static final double[][][] NEAR_AXIS = axes(DomeLook.LEG_SEGMENTS), MID_AXIS = axes(MID_SEGMENTS),
        FAR_AXIS = axes(DomeLook.FAR_LEG_LINES);
    /** The emitter nodes' middles. */
    private static final double[][] NODES;
    /** The apex cells by dome radius and rings. */
    private static final Map<Long, List<double[][]>> APEX = new ConcurrentHashMap<>();

    private static final int LEG_PART = 0, EMITTER = 1, ARCS = 2, SHELL = 3, SWEEP = 4;
    private static final String[] NAMES = { "leg ribs", "emitter", "arcs", "dome shell", "forming sweep" };
    private static final boolean[] OFF = new boolean[NAMES.length];

    static {
        List<int[]> n = DomeGeneratorShape.nodes();
        NODES = new double[n.size()][];
        for (int i = 0; i < NODES.length; i++)
            NODES[i] = new double[] { n.get(i)[0] + 0.5, n.get(i)[1] + 0.5, n.get(i)[2] + 0.5 };
    }

    private DomeGeneratorRender() {}

    private static double[][][] axes(int segments) {
        double[][][] out = new double[LEGS][segments + 1][];
        for (int leg = 0; leg < LEGS; leg++) for (int k = 0; k <= segments; k++) {
            double s = k / (double) segments;
            double[] p = DomeLook.legPoint(leg, s);
            out[leg][k] = new double[] { p[0], p[1], p[2], s };
        }
        return out;
    }

    /**
     * Draws the Dome Generator's own visuals; {@code p0} is its distance fade times how far it has formed, {@code cam}
     * the camera in its frame, {@code tier} how much to draw for the pod ({@link ModuleLook#tier}); the legs and the
     * dome measure their own distance.
     */
    static void draw(TileDomeGenerator d, CityFrame f, double t, float p0, double[] cam, int tier, float appear) {
        boolean docked = CityRender.docked(d), on = CityRender.on(d);
        int accent = d.moduleColor();
        double sweep = d.sweepLevel();
        boolean moored = DomeGeneratorShape.MOORED.equals(d.shapeName());
        if (moored && !OFF[LEG_PART]) try {
            int legTier = ModuleLook.tier(false, legDistance(cam), CityRender.NEAR, Config.nexusEffectRange);
            double below = sweep == Double.MAX_VALUE ? Double.MAX_VALUE : f.local(sweep);
            // far, faded as the landmark is rather than by the effects' range, so the legs carry on unbroken into
            // the landmark's drawing past that range
            float p = legTier == ModuleLook.FAR ? appear * DomeLook.landmarkFade(domeDistance(cam)) : p0;
            legs(f, t, p, legTier, on, docked, accent, below);
        } catch (Throwable x) {
            CityRender.recover();
            fail(LEG_PART, x);
        } finally {
            CityRender.close();
        }
        if (!OFF[EMITTER]) try {
            emitter(f, t, p0, tier, on, docked, accent);
        } catch (Throwable x) {
            CityRender.recover();
            fail(EMITTER, x);
        } finally {
            CityRender.close();
        }
        if (docked && !OFF[SHELL]) try {
            shell(d, f, t, appear, cam, on);
        } catch (Throwable x) {
            CityRender.recover();
            fail(SHELL, x);
        } finally {
            CityRender.close();
        }
        // an idle look (CITY.md §4.8): drawn while docked, dim and amber until powered
        if (docked && tier != ModuleLook.FAR && !OFF[ARCS]) try {
            arcs(d, f, t, p0, tier, accent, on);
        } catch (Throwable x) {
            CityRender.recover();
            fail(ARCS, x);
        } finally {
            CityRender.close();
        }
    }

    /**
     * The landmark alone, for a camera past the range the rest is drawn in ({@code NexusRender} calls this or
     * {@link #draw}, never both): the moored legs' far lines in any dock state, then, while docked, the dome and a glow
     * where the crystal floats. At most {@link DomeLook#landmarkQuads} quads.
     */
    static void landmark(TileDomeGenerator d, double t) {
        if (!d.formed()) return;
        boolean docked = CityRender.docked(d), on = CityRender.on(d);
        // the legs' pillars are hidden at every distance, so their drawing must carry on out here too
        if (DomeGeneratorShape.MOORED.equals(d.shapeName()) && !OFF[LEG_PART]) try {
            CityFrame f = new CityFrame(d);
            double sweep = d.sweepLevel();
            double below = sweep == Double.MAX_VALUE ? Double.MAX_VALUE : f.local(sweep);
            float p = DomeLook.landmarkFade(domeDistance(CityFrame.camera(d)));
            legs(f, t, p, ModuleLook.FAR, on, docked, d.moduleColor(), below);
        } catch (Throwable x) {
            CityRender.recover();
            fail(LEG_PART, x);
        } finally {
            CityRender.close();
        }
        if (!docked || OFF[SHELL]) return;
        try {
            CityFrame f = new CityFrame(d);
            shell(d, f, t, 1f, CityFrame.camera(d), on);
            double[] c = { DomeLook.CX, DomeLook.EMITTER_Y, DomeLook.CZ };
            double x = f.x(c[0], c[2]), y = f.y(c[1]), z = f.z(c[0], c[2]);
            double dist = Math.sqrt(x * x + y * y + z * z);
            float a = DomeLook.landmarkFade(dist) * (on ? 0.5f : 0.2f);
            if (a > 0.01f) {
                Motes.begin();
                Motes.add(x, y, z, 2.5 + dist * 0.004, d.moduleColor(), a);
                Motes.end();
            }
        } catch (Throwable x) {
            CityRender.recover();
            fail(SHELL, x);
        } finally {
            CityRender.close();
        }
    }

    /** How far the camera ({@code p}, in the pod's frame) is from the dome's middle, in blocks. */
    private static double domeDistance(double[] p) {
        double dx = p[0] - DomeLook.CX, dy = p[1] - DomeLook.CY, dz = p[2] - DomeLook.CZ;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** How far the camera is from the legs' box (the blueprint's, under the pod), in blocks. */
    private static double legDistance(double[] p) {
        double dx = Math.max(0, Math.max(LEG_X0 - p[0], p[0] - LEG_X1));
        double dy = Math.max(0, Math.max(LEG_Y0 - p[1], p[1]));
        double dz = Math.max(0, Math.max(LEG_Z0 - p[2], p[2] - LEG_Z1));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    // ---- the legs

    /**
     * The six legs as ribs: steel segments (the faces turned to the camera; lighter outside, dark inside), a collar of
     * light at each joint pulsing as a wave climbs the legs, a line up the inner face. Only below {@code below} (the
     * forming sweep's height in the pod's frame; the pillars above it still stand). Mid, fewer segments and no collars;
     * far (and in the landmark), {@link DomeLook#FAR_LEG_LINES} lines a leg.
     */
    private static void legs(CityFrame f, double t, float p, int tier, boolean on, boolean docked, int accent,
        double below) {
        if (p <= 0.01f) return;
        int glow = on ? accent : docked ? AMBER : IDLE;
        float life = on ? 1f : docked ? 0.55f : 0.3f;
        if (tier == ModuleLook.FAR) {
            Shapes.begin(true);
            Tessellator tes = CityFrame.start();
            tes.setColorRGBA_I(Shapes.mix(STEEL_LIGHT, glow, 0.5), CityFrame.alpha255(0.6f * p));
            for (double[][] leg : FAR_AXIS) for (int k = 0; k + 1 < leg.length; k++) {
                double[] a = leg[k], b = leg[k + 1];
                if (b[1] > below) continue;
                f.line(tes, a[0], a[1], a[2], b[0], b[1], b[2], 0.6);
            }
            tes.draw();
            Shapes.end();
            return;
        }
        boolean nearby = tier == ModuleLook.NEAR;
        double[][][] axes = nearby ? NEAR_AXIS : MID_AXIS;
        // the solid ribs first
        Shapes.begin(false);
        Tessellator tes = CityFrame.start();
        for (double[][] leg : axes) {
            for (int k = 0; k + 1 < leg.length; k++) {
                double[] a = lerp(leg[k], leg[k + 1], GAP), b = lerp(leg[k], leg[k + 1], 1 - GAP);
                if (a[1] >= below) continue;
                if (b[1] > below) b = cut(a, b, below);
                segment(tes, f, a, b, DomeLook.legHalf(a[3]), DomeLook.legHalf(b[3]), 0.95f * p);
            }
        }
        tes.draw();
        Shapes.end();
        // the light: collars at the joints and a line up the inner face
        Shapes.begin(true);
        tes = CityFrame.start();
        for (int l = 0; l < axes.length; l++) {
            double[][] leg = axes[l];
            double wave = (t * 0.004 + l * 0.11) % 1.3 - 0.15;
            for (int k = 0; k < leg.length; k++) {
                double[] j = leg[k];
                if (j[1] > below) break;
                double dw = j[3] - wave;
                float k0 = life * p * (0.25f + (on ? 0.75f : 0.3f) * (float) Math.exp(-dw * dw / 0.004));
                int rgb = Shapes.mix(glow, WHITE, 0.4 * Math.exp(-dw * dw / 0.004));
                if (nearby) collar(tes, f, leg, k, rgb, k0);
                if (k + 1 < leg.length) {
                    double[] a = j, b = leg[k + 1];
                    if (b[1] > below) b = cut(a, b, below);
                    double[] ia = inner(a, DomeLook.legHalf(a[3])), ib = inner(b, DomeLook.legHalf(b[3]));
                    tes.setColorRGBA_I(glow, CityFrame.alpha255(0.45f * life * p));
                    f.line(tes, ia[0], ia[1], ia[2], ib[0], ib[1], ib[2], 0.05);
                }
            }
        }
        tes.draw();
        Shapes.end();
        if (!nearby) return;
        // a glow on each joint the wave passes
        Motes.begin();
        for (int l = 0; l < axes.length; l++) {
            double wave = (t * 0.004 + l * 0.11) % 1.3 - 0.15;
            for (double[] j : axes[l]) {
                if (j[1] > below) break;
                double dw = j[3] - wave;
                float a = (float) Math.exp(-dw * dw / 0.004) * (on ? 0.6f : 0.2f) * life * p;
                if (a > 0.02f) Motes.add(f.x(j[0], j[2]), f.y(j[1]), f.z(j[0], j[2]), 0.55, glow, a);
            }
        }
        Motes.end();
    }

    /** The point {@code k} of the way from {@code a} to {@code b} (axis points {x, y, z, s}). */
    private static double[] lerp(double[] a, double[] b, double k) {
        return new double[] { a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k, a[2] + (b[2] - a[2]) * k,
            a[3] + (b[3] - a[3]) * k };
    }

    /** The point of {@code a .. b} at the height {@code y}. */
    private static double[] cut(double[] a, double[] b, double y) {
        double k = (y - a[1]) / (b[1] - a[1]);
        return lerp(a, b, Math.max(0, Math.min(1, k)));
    }

    /** A point of the leg's axis moved to its inner face (towards the pod's axis), a hair out of the steel. */
    private static double[] inner(double[] a, double half) {
        double dx = DomeLook.CX - a[0], dz = DomeLook.CZ - a[2], l = Math.hypot(dx, dz);
        double k = (half * 0.8 + 0.03) / Math.max(l, 1e-6);
        return new double[] { a[0] + dx * k, a[1], a[2] + dz * k };
    }

    /**
     * A section's four corners round an axis point: across the leg (level, square to the way out from the pod's
     * axis) {@code half} each way, and along the leg's bend (square to it and to the way along) {@code 0.8 half}.
     */
    private static double[][] section(double[] a, double[] along, double half) {
        double rx = a[0] - DomeLook.CX, rz = a[2] - DomeLook.CZ, rl = Math.hypot(rx, rz);
        double sx = -rz / rl, sz = rx / rl;
        // out = along x side, normalised
        double ox = along[1] * sz, oy = along[2] * sx - along[0] * sz, oz = -along[1] * sx;
        double ol = Math.sqrt(ox * ox + oy * oy + oz * oz);
        ox /= ol;
        oy /= ol;
        oz /= ol;
        double d = 0.8 * half;
        double[][] c = new double[4][];
        int[][] signs = { { -1, -1 }, { 1, -1 }, { 1, 1 }, { -1, 1 } };
        for (int i = 0; i < 4; i++) {
            double w = signs[i][0] * half, o = signs[i][1] * d;
            c[i] = new double[] { a[0] + sx * w + ox * o, a[1] + oy * o, a[2] + sz * w + oz * o };
        }
        return c;
    }

    /**
     * One segment of a rib from {@code a} to {@code b}: its side faces and its two ends, each only when turned to the
     * camera (so a solid rib needs no depth), shaded as the nexus's ribs are.
     */
    private static void segment(Tessellator tes, CityFrame f, double[] a, double[] b, double ha, double hb,
        float alpha) {
        double[] along = { b[0] - a[0], b[1] - a[1], b[2] - a[2] };
        double l = Math.sqrt(along[0] * along[0] + along[1] * along[1] + along[2] * along[2]);
        if (l < 1e-6) return;
        for (int i = 0; i < 3; i++) along[i] /= l;
        double[][] lo = section(a, along, ha), hi = section(b, along, hb);
        double[][] wl = new double[4][], wh = new double[4][];
        for (int i = 0; i < 4; i++) {
            wl[i] = world(f, lo[i]);
            wh[i] = world(f, hi[i]);
        }
        double[] ca = world(f, a), cb = world(f, b);
        int[] shade = { STEEL_DARK, STEEL, STEEL_LIGHT, STEEL };
        for (int i = 0; i < 4; i++) {
            int n = (i + 1) % 4;
            face(tes, wl[i], wl[n], wh[n], wh[i], ca, cb, shade[i], alpha);
        }
        face(tes, wl[3], wl[2], wl[1], wl[0], cb, ca, STEEL_DARK, alpha);
        face(tes, wh[0], wh[1], wh[2], wh[3], ca, cb, STEEL_LIGHT, alpha);
    }

    /**
     * A face of a segment, drawn only when the camera (at the origin) is on its outer side: its middle less the
     * segment's axis middle points out of the segment.
     */
    private static void face(Tessellator tes, double[] p0, double[] p1, double[] p2, double[] p3, double[] ca,
        double[] cb, int rgb, float alpha) {
        double mx = (p0[0] + p1[0] + p2[0] + p3[0]) / 4, my = (p0[1] + p1[1] + p2[1] + p3[1]) / 4,
            mz = (p0[2] + p1[2] + p2[2] + p3[2]) / 4;
        double ox = mx - (ca[0] + cb[0]) / 2, oy = my - (ca[1] + cb[1]) / 2, oz = mz - (ca[2] + cb[2]) / 2;
        // the camera sits at the origin: seen from outside when the way from the face to it agrees with the outward
        if (-mx * ox - my * oy - mz * oz <= 0) return;
        tes.setColorRGBA_I(rgb, CityFrame.alpha255(alpha));
        tes.addVertex(p0[0], p0[1], p0[2]);
        tes.addVertex(p1[0], p1[1], p1[2]);
        tes.addVertex(p2[0], p2[1], p2[2]);
        tes.addVertex(p3[0], p3[1], p3[2]);
    }

    private static double[] world(CityFrame f, double[] p) {
        return new double[] { f.x(p[0], p[2]), f.y(p[1]), f.z(p[0], p[2]) };
    }

    /** A collar of light round joint {@code k} of a leg: a square band a little wider than the steel. */
    private static void collar(Tessellator tes, CityFrame f, double[][] leg, int k, int rgb, float a) {
        double[] j = leg[k], next = leg[Math.min(k + 1, leg.length - 1)], prev = leg[Math.max(k - 1, 0)];
        double[] along = { next[0] - prev[0], next[1] - prev[1], next[2] - prev[2] };
        double l = Math.sqrt(along[0] * along[0] + along[1] * along[1] + along[2] * along[2]);
        if (l < 1e-6) return;
        for (int i = 0; i < 3; i++) along[i] /= l;
        double half = DomeLook.legHalf(j[3]) * 1.12;
        double[][] c = section(j, along, half);
        tes.setColorRGBA_I(rgb, CityFrame.alpha255(a));
        for (int i = 0; i < 4; i++) {
            double[] u = c[i], v = c[(i + 1) % 4];
            f.line(tes, u[0], u[1], u[2], v[0], v[1], v[2], 0.07);
        }
    }

    // ---- the emitter

    /**
     * The six emitter nodes as small crystals turning on the roof, the emitter crystal floating over it with a
     * smaller one turning the other way inside, threads of light from the nodes up to it (a pulse climbing them).
     */
    private static void emitter(CityFrame f, double t, float p, int tier, boolean on, boolean docked, int accent) {
        if (p <= 0.01f) return;
        float life = p * (on ? 1f : docked ? 0.5f : 0.25f);
        double bob = 0.15 * Math.sin(t * 0.03);
        double cx = f.x(DomeLook.CX, DomeLook.CZ), cy = f.y(DomeLook.EMITTER_Y + bob),
            cz = f.z(DomeLook.CX, DomeLook.CZ);
        Shapes.begin(false);
        Shapes.crystal(cx, cy, cz, 0.7, 1.25, t * 0.02, CRYSTAL, accent, 0.85f * life + 0.1f * p);
        if (tier != ModuleLook.FAR) for (int i = 0; i < NODES.length; i++) {
            double[] n = NODES[i];
            Shapes.crystal(
                f.x(n[0], n[2]),
                f.y(n[1] + 0.05 * Math.sin(t * 0.05 + i)),
                f.z(n[0], n[2]),
                0.26,
                0.42,
                -t * 0.03 + i,
                CRYSTAL,
                accent,
                0.8f * life + 0.1f * p);
        }
        Shapes.additive(true);
        Shapes.crystal(cx, cy, cz, 0.32, 0.6, -t * 0.045, WHITE, accent, 0.6f * life);
        if (tier != ModuleLook.FAR) {
            Tessellator tes = CityFrame.start();
            for (int i = 0; i < NODES.length; i++) {
                double[] n = NODES[i];
                double climb = (t * 0.02 + i / 6.0) % 1;
                for (int k = 0; k < 2; k++) {
                    double u0 = k / 2.0, u1 = (k + 1) / 2.0, um = (u0 + u1) / 2;
                    float a = life * (0.18f + 0.5f * (float) Math.exp(-(um - climb) * (um - climb) / 0.02));
                    tes.setColorRGBA_I(accent, CityFrame.alpha255(a));
                    f.line(
                        tes,
                        n[0] + (DomeLook.CX - n[0]) * u0,
                        n[1] + (DomeLook.EMITTER_Y - n[1]) * u0,
                        n[2] + (DomeLook.CZ - n[2]) * u0,
                        n[0] + (DomeLook.CX - n[0]) * u1,
                        n[1] + (DomeLook.EMITTER_Y - n[1]) * u1,
                        n[2] + (DomeLook.CZ - n[2]) * u1,
                        0.05);
                }
            }
            tes.draw();
        }
        Shapes.end();
        Motes.begin();
        Motes.add(cx, cy, cz, 1.8, accent, 0.35f * life + 0.05f * p);
        if (tier != ModuleLook.FAR)
            for (double[] n : NODES) Motes.add(f.x(n[0], n[2]), f.y(n[1]), f.z(n[0], n[2]), 0.5, accent, 0.4f * life);
        Motes.end();
    }

    // ---- the arcs

    /**
     * Six arcs from the nodes out to the dome, bowing up a little, crackling: every piece shifted at random, the
     * shifts drawn again every other tick; a bright core and, near, a soft halo; a glow where each meets the dome.
     * Without power ({@code on} false, still docked) the same pieces, faint, amber and flickering as the dome does, the
     * crackle slower.
     */
    private static void arcs(TileDomeGenerator d, CityFrame f, double t, float p0, int tier, int accent, boolean on) {
        float p = p0 * (on ? 1f : 0.35f + 0.15f * (float) Math.sin(t * 0.21));
        if (p <= 0.01f) return;
        int core = on ? ARC : Shapes.mix(ARC, AMBER, 0.6), halo = on ? accent : AMBER;
        double radius = DomeLook.shellRadius(phase(d));
        boolean nearby = tier == ModuleLook.NEAR;
        int pieces = nearby ? ARC_PIECES : ARC_PIECES / 2;
        long flick = (long) Math.floor(t / (on ? 2 : 6));
        Shapes.begin(true);
        Tessellator tes = CityFrame.start();
        double[][] ends = new double[NODES.length][];
        for (int i = 0; i < NODES.length; i++) {
            double[] a = NODES[i], b = DomeLook.arcEnd(a[0], a[1], a[2], radius);
            ends[i] = b;
            double len = Math.sqrt(sq(b[0] - a[0]) + sq(b[1] - a[1]) + sq(b[2] - a[2]));
            double amp = Math.min(1.6, 0.05 * len);
            double[] prev = null;
            float flare = 0.7f + 0.3f * (float) Math.sin(t * 0.3 + i * 1.9);
            for (int k = 0; k <= pieces; k++) {
                double u = k / (double) pieces;
                double[] q = DomeLook.arcPoint(a, b, u);
                double jitter = Math.sin(Math.PI * u) * amp;
                int seed = (int) (flick * 7919 + i * 977 + k * 131);
                q[0] += (Motifs.hash(seed) - 0.5) * jitter;
                q[1] += (Motifs.hash(seed + 1) - 0.5) * jitter;
                q[2] += (Motifs.hash(seed + 2) - 0.5) * jitter;
                if (prev != null) {
                    float fade = (float) (1 - 0.6 * u);
                    tes.setColorRGBA_I(core, CityFrame.alpha255(0.8f * p * flare * fade));
                    f.line(tes, prev[0], prev[1], prev[2], q[0], q[1], q[2], 0.09);
                    if (nearby) {
                        tes.setColorRGBA_I(halo, CityFrame.alpha255(0.18f * p * flare * fade));
                        f.line(tes, prev[0], prev[1], prev[2], q[0], q[1], q[2], 0.6);
                    }
                }
                prev = q;
            }
        }
        tes.draw();
        Shapes.end();
        Motes.begin();
        for (double[] b : ends) Motes.add(f.x(b[0], b[2]), f.y(b[1]), f.z(b[0], b[2]), 1.4, halo, 0.35f * p);
        Motes.end();
    }

    // ---- the dome

    /**
     * The dome, nearly invisible as CITY.md §4.8 asks: only a soft ring where it meets the ground (a band rising from
     * the forum and a line along it) and the apex cells as they shimmer, the same from inside and out. Fades out over
     * the last {@link DomeLook#LANDMARK_FADE} of its {@link DomeLook#LANDMARK} blocks; without power, fainter and
     * amber.
     * At most {@link DomeLook#shellQuads} quads, counted from the same points.
     */
    private static void shell(TileDomeGenerator d, CityFrame f, double t, float appear, double[] cam, boolean on) {
        double radius = DomeLook.shellRadius(phase(d));
        double dist = domeDistance(cam);
        if (dist > DomeLook.LANDMARK) return;
        float a = DomeLook.landmarkFade(dist) * appear * (on ? 1f : 0.35f + 0.15f * (float) Math.sin(t * 0.21));
        if (a <= 0.01f) return;
        int rgb = on ? d.moduleColor() : AMBER, light = Shapes.mix(rgb, WHITE, 0.45);
        Shapes.begin(true);
        Tessellator tes = CityFrame.start();
        // the ground ring: a soft band rising from where the dome meets the forum, and a line along it
        int n = DomeLook.groundSegments(dist);
        double gy = DomeLook.CY + 0.06;
        for (int i = 0; i < n; i++) {
            double[] q0 = DomeLook.groundPoint(i, n, radius), q1 = DomeLook.groundPoint(i + 1, n, radius);
            double x0 = q0[0], z0 = q0[1], x1 = q1[0], z1 = q1[1];
            tes.setColorRGBA_I(rgb, CityFrame.alpha255(0.2f * a));
            f.vertex(tes, x0, gy, z0);
            f.vertex(tes, x1, gy, z1);
            tes.setColorRGBA_I(rgb, 0);
            f.vertex(tes, x1, gy + 2.5, z1);
            f.vertex(tes, x0, gy + 2.5, z0);
            tes.setColorRGBA_I(light, CityFrame.alpha255(0.35f * a));
            f.line(tes, x0, gy, z0, x1, gy, z1, 0.08);
        }
        // the apex cells as they shimmer: a faint fill and an outline
        int rings = DomeLook.hexRings(dist, radius);
        List<double[][]> cells = APEX
            .computeIfAbsent(Double.doubleToLongBits(radius) * 31 + rings, k -> DomeLook.apexCells(radius, rings));
        for (int i = 0; i < cells.size(); i++) {
            double s = DomeLook.shimmer(i, t) * a;
            if (s < 0.02) continue;
            double[][] c = cells.get(i);
            tes.setColorRGBA_I(rgb, CityFrame.alpha255(0.12f * (float) s));
            hexFill(tes, f, c, 1, 2, 3, 4);
            hexFill(tes, f, c, 4, 5, 6, 1);
            tes.setColorRGBA_I(light, CityFrame.alpha255(0.4f * (float) s));
            for (int k = 1; k <= 6; k++) {
                double[] u = c[k], v = c[k % 6 + 1];
                f.line(
                    tes,
                    DomeLook.CX + u[0],
                    DomeLook.CY + u[1],
                    DomeLook.CZ + u[2],
                    DomeLook.CX + v[0],
                    DomeLook.CY + v[1],
                    DomeLook.CZ + v[2],
                    0.1);
            }
        }
        tes.draw();
        Shapes.end();
    }

    /** Half a hexagon: four of its points (relative to the dome's middle) as a quad. */
    private static void hexFill(Tessellator tes, CityFrame f, double[][] c, int a, int b, int e, int g) {
        for (int k : new int[] { a, b, e, g })
            f.vertex(tes, DomeLook.CX + c[k][0], DomeLook.CY + c[k][1], DomeLook.CZ + c[k][2]);
    }

    // ---- the forming sweep

    /**
     * While it forms: a collar of light on each leg at the sweep's height (the pillars give way under it), and a plane
     * of its colour with a bright rim rising through the pod once the sweep reaches it.
     */
    static void sweep(TileDomeGenerator d, double level, float k) {
        if (k <= 0 || OFF[SWEEP]) return;
        try {
            CityFrame f = new CityFrame(d);
            double y = f.local(level);
            int accent = d.moduleColor();
            Shapes.begin(true);
            Tessellator tes = CityFrame.start();
            if (DomeGeneratorShape.MOORED.equals(d.shapeName()) && y < 0) {
                for (double[][] leg : MID_AXIS) for (int i = 0; i + 1 < leg.length; i++) {
                    if (leg[i][1] > y || leg[i + 1][1] < y) continue;
                    double[] j = cut(leg[i], leg[i + 1], y);
                    double[] along = { leg[i + 1][0] - leg[i][0], leg[i + 1][1] - leg[i][1],
                        leg[i + 1][2] - leg[i][2] };
                    double l = Math.sqrt(along[0] * along[0] + along[1] * along[1] + along[2] * along[2]);
                    for (int q = 0; q < 3; q++) along[q] /= l;
                    double[][] c = section(j, along, DomeLook.legHalf(j[3]) * 1.3);
                    tes.setColorRGBA_I(WHITE, CityFrame.alpha255(0.9f * k));
                    for (int q = 0; q < 4; q++) {
                        double[] u = c[q], v = c[(q + 1) % 4];
                        f.line(tes, u[0], u[1], u[2], v[0], v[1], v[2], 0.12);
                    }
                }
            }
            if (y >= 0 && y <= DomeGeneratorShape.HEIGHT) {
                int s = DomeGeneratorShape.SIZE;
                tes.setColorRGBA_I(accent, CityFrame.alpha255(0.3f * k));
                f.vertex(tes, 0, y, 0);
                f.vertex(tes, s, y, 0);
                f.vertex(tes, s, y, s);
                f.vertex(tes, 0, y, s);
                tes.setColorRGBA_I(WHITE, CityFrame.alpha255(0.8f * k));
                f.line(tes, 0, y, 0, s, y, 0, 0.15);
                f.line(tes, s, y, 0, s, y, s, 0.15);
                f.line(tes, s, y, s, 0, y, s, 0.15);
                f.line(tes, 0, y, s, 0, y, 0, 0.15);
            }
            tes.draw();
            Shapes.end();
        } catch (Throwable x) {
            CityRender.recover();
            fail(SWEEP, x);
        } finally {
            CityRender.close();
        }
    }

    // ---- the nexus under it

    /**
     * The phase of the nexus the generator is docked to (its dome grows with the city), as the client heard it; phase
     * II when that nexus is not loaded here.
     */
    static int phase(TileDomeGenerator d) {
        int[] c = d.clientNexus;
        if (c == null) return 2;
        for (TileMultiblock m : ClientTiles.all()) {
            if (m instanceof TileNexus n && !m.isInvalid()
                && m.getWorldObj() == d.getWorldObj()
                && Arrays.equals(n.centre(), c)) return Math.max(2, n.phase());
        }
        return 2;
    }

    private static double sq(double v) {
        return v * v;
    }

    private static void fail(int part, Throwable x) {
        if (OFF[part]) return;
        OFF[part] = true;
        FluxEcho.LOG.warn("The Dome Generator's {} failed and is off until the game restarts", NAMES[part], x);
    }
}
