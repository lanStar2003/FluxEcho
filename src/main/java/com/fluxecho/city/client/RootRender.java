package com.fluxecho.city.client;

import static com.fluxecho.client.FluxDraw.AMBER;
import static com.fluxecho.client.FluxDraw.VIOLET;
import static com.fluxecho.client.FluxDraw.WHITE;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.world.World;

import com.fluxecho.FluxEcho;
import com.fluxecho.campus.PartBlocks;
import com.fluxecho.city.TileRoot;
import com.fluxecho.client.Motes;
import com.fluxecho.logic.ModuleLook;
import com.fluxecho.logic.Parts;
import com.fluxecho.logic.RootLook;
import com.fluxecho.logic.RootShape;
import com.fluxecho.render.Shapes;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The Tidal Well's own visuals (0.12.0, CITY.md §4.9 Visuals), on top of what every city module shows
 * ({@link CityRender}); where they go is {@link RootLook}'s, the tide is {@code Tide}'s curve of the world's day time
 * (drawn already, though tides have no effect before 0.19.0).
 * <ul>
 * <li>Formed: the three tidal rings have given way to drawn rings of steel that rise and fall with the tide (the upper
 * ones a little behind) and turn, the middle one the other way; a seam of light runs round each, lights riding it.</li>
 * <li>Docked: the eight lit ribs pulse upward (a wave of light climbing the shaft), the three flux-layer bands glow in
 * their own tints (表层 the well's colour, 记忆层 towards violet, 深层 towards the deep root's green); the pool's
 * surface glows, brighter as the tide rises. From the surface the forum's grate band over the well glows with the tide
 * and, at the top of a high tide, the rings' crests show through it as blades of light round the dais.</li>
 * <li>Powered: caustics run over the pool, two families of wavering lines crossing on the water.</li>
 * </ul>
 * Budgets (CITY.md §1.3, quads a frame on top of {@link CityRender}'s): near, the rings at most 4 faces of
 * {@value #ARCS} x {@value #NEAR_PIECES} pieces each (only the faces turned to the camera, about half) and their seams
 * and motes (about 290 for three), the ribs a line and a halo a block (about 300), the bands a line a face (about
 * 230), the pool a glow and two lines a cell of water (about 330), the surface a glow a grate cell and 32 blades;
 * mid, a piece an arc and no seams, a line a rib's stretch, no bands, the pool's glow alone (about 200); far, the
 * rings as lines (48). With the plate and the tap sockets the well stays under 2,000 near, 300 mid and 64 far.
 * <p>
 * Shader-safe as {@link CityRender}: in its frame ({@link CityFrame}) relative to the camera, quads only, a normal
 * towards the viewer, no depth writes (the rings' faces turned away are left out), the caustics a little over the
 * water's still surface so a shader pack's water does not cover them; animation by the world time alone. Each part
 * fails on its own.
 * <p>
 * Over low ground the well is shallower (0.14.1): the rings, ribs, bands and the forum over it are those of the well
 * the tile stands as ({@link TileRoot#well}), worked out once for each depth.
 */
@SideOnly(Side.CLIENT)
final class RootRender {

    private static final int STEEL = 0x1C3446, STEEL_LIGHT = 0x2C5470, STEEL_DARK = 0x0E1C28, IDLE = 0x4A6A80,
        DEEP = 0x5FD3A8, WATER_LIGHT = 0x9AE8FF;
    private static final int ARCS = 8, NEAR_PIECES = 3, BLADES = 16;
    /** A ring's thickness, half of it each way from its middle. */
    private static final double RING_HALF = 0.2;
    /** The middle of the shaft, and the pool's caustics a little over the water's still surface. */
    private static final double C = RootShape.MID + 0.5, POOL_Y = RootShape.FLOOR_Y + ModuleLook.WATER_TOP + 0.035;

    private static final double[][] RIBS = RootLook.ribs();
    private static final List<int[]> GRATE = RootLook.grate();
    /** What is drawn of a well of each depth, by depth. */
    private static final Map<Integer, Look> LOOKS = new ConcurrentHashMap<>();

    private static final int RINGS = 0, RIB_PART = 1, BAND_PART = 2, POOL = 3, SURFACE = 4;
    private static final String[] NAMES = { "tide rings", "rising ribs", "flux-layer bands", "pool", "grate glow" };
    private static final boolean[] OFF = new boolean[NAMES.length];

    private RootRender() {}

    /**
     * Where a well of one depth is drawn: its lit ribs' stretches, its bands and their faces, its rings, its pool's
     * water and the forum's layer over its lid, in the well's own frame.
     */
    private static final class Look {

        final List<List<int[]>> runs = new ArrayList<>(), bandFaces = new ArrayList<>();
        final int[] bands;
        final int rings, lid, surface;
        final double[][] water;

        Look(RootShape.Well w) {
            for (int i = 0; i < RIBS.length; i++) runs.add(RootLook.ribRuns(i, w));
            bands = w.bands();
            for (int y : bands) bandFaces.add(RootLook.bandFaces(y, w));
            rings = w.ringY().length;
            lid = w.lidY;
            surface = RootLook.surfaceY(w);
            water = ModuleLook.water(w.build, RootShape.FLOOR_Y, 0.5)
                .toArray(new double[0][]);
        }
    }

    private static Look look(TileRoot r) {
        RootShape.Well w = r.well();
        return LOOKS.computeIfAbsent(w.depth, d -> new Look(w));
    }

    /**
     * Draws the Tidal Well's own visuals; {@code p0} is its distance fade times how far it has formed, {@code cam} the
     * camera in its frame, {@code tier} how much to draw ({@link ModuleLook#tier}).
     */
    static void draw(TileRoot r, CityFrame f, double t, float p0, double[] cam, int tier) {
        if (p0 <= 0.01f) return;
        World w = r.getWorldObj();
        if (w == null) return;
        double day = w.getWorldTime() + (t - Math.floor(t)), tide = RootLook.tide(day);
        boolean docked = CityRender.docked(r), on = CityRender.on(r);
        int accent = r.moduleColor();
        Look look = look(r);
        if (!OFF[RINGS]) try {
            rings(f, look.rings, t, day, p0, tier, on, docked, accent, cam);
        } catch (Throwable x) {
            CityRender.recover();
            fail(RINGS, x);
        } finally {
            CityRender.close();
        }
        if (!docked || tier == ModuleLook.FAR) return;
        boolean nearby = tier == ModuleLook.NEAR;
        if (!OFF[RIB_PART]) try {
            ribs(f, look, t, p0, nearby, on, accent);
        } catch (Throwable x) {
            CityRender.recover();
            fail(RIB_PART, x);
        } finally {
            CityRender.close();
        }
        if (nearby && !OFF[BAND_PART]) try {
            bands(f, look, t, p0, on, accent);
        } catch (Throwable x) {
            CityRender.recover();
            fail(BAND_PART, x);
        } finally {
            CityRender.close();
        }
        if (!OFF[POOL]) try {
            pool(f, look, t, p0, tide, nearby && on, accent);
        } catch (Throwable x) {
            CityRender.recover();
            fail(POOL, x);
        } finally {
            CityRender.close();
        }
        if (!OFF[SURFACE]) try {
            surface(r, f, look, t, day, p0, tide, on, accent);
        } catch (Throwable x) {
            CityRender.recover();
            fail(SURFACE, x);
        } finally {
            CityRender.close();
        }
    }

    // ---- the rings

    /**
     * The three tide rings (the {@code n} lowest, those a shallower well has): eight arcs of steel a ring with a gap
     * between each, at the height and turn the tide gives them (only the faces turned to the camera: no depth needed),
     * then a seam of light round each and lights riding it. Far, three lines.
     */
    private static void rings(CityFrame f, int n, double t, double day, float p, int tier, boolean on, boolean docked,
        int accent, double[] cam) {
        if (n == 0) return;
        int glow = on ? accent : docked ? AMBER : IDLE;
        float life = p * (on ? 1f : docked ? 0.6f : 0.3f);
        if (tier == ModuleLook.FAR) {
            Shapes.begin(true);
            Tessellator tes = CityFrame.start();
            tes.setColorRGBA_I(glow, CityFrame.alpha255(0.5f * life));
            for (int i = 0; i < n; i++) {
                double y = ringY(i, day), r = RootShape.RING_R[i] + 0.5;
                for (int k = 0; k < 16; k++) {
                    double a0 = k * Math.PI / 8, a1 = (k + 1) * Math.PI / 8;
                    f.line(
                        tes,
                        C + Math.cos(a0) * r,
                        y,
                        C + Math.sin(a0) * r,
                        C + Math.cos(a1) * r,
                        y,
                        C + Math.sin(a1) * r,
                        0.5);
                }
            }
            tes.draw();
            Shapes.end();
            return;
        }
        boolean nearby = tier == ModuleLook.NEAR;
        int pieces = nearby ? NEAR_PIECES : 1;
        Shapes.begin(false);
        Tessellator tes = CityFrame.start();
        for (int i = 0; i < n; i++) {
            double y = ringY(i, day), turn = RootLook.ringTurn(i, t, day);
            double r0 = RootShape.RING_R[i], r1 = r0 + 1, top = y + RING_HALF, bottom = y - RING_HALF;
            for (int k = 0; k < ARCS; k++) {
                double from = turn + k * 2 * Math.PI / ARCS + 0.06, to = turn + (k + 1) * 2 * Math.PI / ARCS - 0.06;
                for (int j = 0; j < pieces; j++) {
                    double a0 = from + (to - from) * j / pieces, a1 = from + (to - from) * (j + 1) / pieces;
                    double c0 = Math.cos(a0), s0 = Math.sin(a0), c1 = Math.cos(a1), s1 = Math.sin(a1);
                    double am = (a0 + a1) / 2, rx = Math.cos(am), rz = Math.sin(am);
                    if (cam[1] > top) quad(tes, f, c0, s0, c1, s1, r0, r1, top, top, STEEL_LIGHT, 0.95f * p);
                    else if (cam[1] < bottom)
                        quad(tes, f, c0, s0, c1, s1, r0, r1, bottom, bottom, STEEL_DARK, 0.95f * p);
                    // the outer band from outside it, the inner band from inside it
                    double ox = cam[0] - (C + rx * r1), oz = cam[2] - (C + rz * r1);
                    if (ox * rx + oz * rz > 0) band(tes, f, c0, s0, c1, s1, r1, bottom, top, STEEL, 0.95f * p);
                    double ix = cam[0] - (C + rx * r0), iz = cam[2] - (C + rz * r0);
                    if (ix * rx + iz * rz < 0) band(tes, f, c0, s0, c1, s1, r0, bottom, top, STEEL_DARK, 0.95f * p);
                }
            }
        }
        tes.draw();
        Shapes.end();
        if (!nearby) return;
        Shapes.begin(true);
        tes = CityFrame.start();
        for (int i = 0; i < n; i++) {
            double y = ringY(i, day) + RING_HALF + 0.01, turn = RootLook.ringTurn(i, t, day),
                r = RootShape.RING_R[i] + 0.5;
            float breath = 0.6f + 0.4f * (float) Math.sin(t * 0.05 + i * 2.1);
            tes.setColorRGBA_I(glow, CityFrame.alpha255(0.8f * life * breath));
            for (int k = 0; k < ARCS; k++) {
                double from = turn + k * 2 * Math.PI / ARCS + 0.1, to = turn + (k + 1) * 2 * Math.PI / ARCS - 0.1;
                for (int j = 0; j < NEAR_PIECES; j++) {
                    double a0 = from + (to - from) * j / NEAR_PIECES, a1 = from + (to - from) * (j + 1) / NEAR_PIECES;
                    f.line(
                        tes,
                        C + Math.cos(a0) * r,
                        y,
                        C + Math.sin(a0) * r,
                        C + Math.cos(a1) * r,
                        y,
                        C + Math.sin(a1) * r,
                        0.06);
                }
            }
        }
        tes.draw();
        Shapes.end();
        Motes.begin();
        for (int i = 0; i < n; i++) {
            double y = ringY(i, day) + RING_HALF + 0.05, r = RootShape.RING_R[i] + 0.5;
            for (int k = 0; k < 3; k++) {
                double a = RootLook.ringTurn(i, t, day) * 3 + k * 2 * Math.PI / 3;
                Motes.add(
                    f.x(C + Math.cos(a) * r, C + Math.sin(a) * r),
                    f.y(y),
                    f.z(C + Math.cos(a) * r, C + Math.sin(a) * r),
                    0.22,
                    WHITE,
                    0.6f * life);
            }
        }
        Motes.end();
    }

    /** A ring's middle height now: its layer's middle and the tide's rise. */
    private static double ringY(int i, double day) {
        return RootShape.RING_Y[i] + 0.5 + RootLook.ringRise(i, day);
    }

    /** A level face of a ring between radii r0 and r1 over the angles of (c0, s0) .. (c1, s1). */
    private static void quad(Tessellator tes, CityFrame f, double c0, double s0, double c1, double s1, double r0,
        double r1, double y0, double y1, int rgb, float a) {
        tes.setColorRGBA_I(rgb, CityFrame.alpha255(a));
        f.vertex(tes, C + c0 * r0, y0, C + s0 * r0);
        f.vertex(tes, C + c1 * r0, y0, C + s1 * r0);
        f.vertex(tes, C + c1 * r1, y1, C + s1 * r1);
        f.vertex(tes, C + c0 * r1, y1, C + s0 * r1);
    }

    /** An upright face of a ring at radius r over the angles of (c0, s0) .. (c1, s1). */
    private static void band(Tessellator tes, CityFrame f, double c0, double s0, double c1, double s1, double r,
        double y0, double y1, int rgb, float a) {
        tes.setColorRGBA_I(rgb, CityFrame.alpha255(a));
        f.vertex(tes, C + c0 * r, y0, C + s0 * r);
        f.vertex(tes, C + c1 * r, y0, C + s1 * r);
        f.vertex(tes, C + c1 * r, y1, C + s1 * r);
        f.vertex(tes, C + c0 * r, y1, C + s0 * r);
    }

    // ---- the ribs and the bands

    /**
     * The eight lit ribs, a line up each stretch between the bands on the wall's inner face, a wave of light climbing
     * them (each rib a little behind the one before); near, a soft halo too; mid, a line a stretch.
     */
    private static void ribs(CityFrame f, Look look, double t, float p, boolean nearby, boolean on, int accent) {
        float life = p * (on ? 1f : 0.55f);
        int glow = on ? accent : AMBER;
        Shapes.begin(true);
        Tessellator tes = CityFrame.start();
        for (int i = 0; i < RIBS.length; i++) {
            double[] r = RIBS[i];
            double x = r[0] - r[2] * 0.04, z = r[1] - r[3] * 0.04;
            double climb = (t * 0.12 + i * 3.1) % (look.lid + 8) - 4;
            for (int[] run : look.runs.get(i)) {
                if (!nearby) {
                    double mid = (run[0] + run[1]) / 2.0, d = mid - climb;
                    tes.setColorRGBA_I(glow, CityFrame.alpha255(life * (0.3f + 0.4f * (float) Math.exp(-d * d / 20))));
                    f.line(tes, x, run[0], z, x, run[1], z, 0.08);
                    continue;
                }
                for (int y = run[0]; y < run[1]; y++) {
                    double d = y + 0.5 - climb;
                    float k = (float) Math.exp(-d * d / 6);
                    float a = life * (0.25f + 0.6f * k);
                    tes.setColorRGBA_I(Shapes.mix(glow, WHITE, 0.35 * k), CityFrame.alpha255(a));
                    f.line(tes, x, y, z, x, y + 1, z, 0.07);
                    tes.setColorRGBA_I(glow, CityFrame.alpha255(0.2f * a));
                    f.line(tes, x, y, z, x, y + 1, z, 0.4);
                }
            }
        }
        tes.draw();
        Shapes.end();
    }

    /**
     * The three flux-layer bands (those a shallower well has): a line along the inner faces of each dark course,
     * breathing slowly, in its layer's tint (表层 the well's colour, 记忆层 towards violet, 深层 towards the deep
     * root's green).
     */
    private static void bands(CityFrame f, Look look, double t, float p, boolean on, int accent) {
        if (look.bands.length == 0) return;
        float life = p * (on ? 1f : 0.55f);
        Shapes.begin(true);
        Tessellator tes = CityFrame.start();
        for (int b = 0; b < look.bands.length; b++) {
            int y = look.bands[b];
            // the lowest band is the deepest layer
            int tint = b == 0 ? Shapes.mix(accent, DEEP, 0.6) : b == 1 ? Shapes.mix(accent, VIOLET, 0.55) : accent;
            if (!on) tint = AMBER;
            float a = life * (0.4f + 0.15f * (float) Math.sin(t * 0.04 + b * 2.3));
            double ly = y + 0.5;
            tes.setColorRGBA_I(tint, CityFrame.alpha255(a));
            for (int[] face : look.bandFaces.get(b)) {
                double ax, az, bx, bz;
                if (face[2] != 0) {
                    ax = bx = (face[2] > 0 ? face[0] + 1 : face[0]) + face[2] * 0.04;
                    az = face[1];
                    bz = face[1] + 1;
                } else {
                    az = bz = (face[3] > 0 ? face[1] + 1 : face[1]) + face[3] * 0.04;
                    ax = face[0];
                    bx = face[0] + 1;
                }
                f.vertex(tes, ax, ly - 0.035, az);
                f.vertex(tes, bx, ly - 0.035, bz);
                f.vertex(tes, bx, ly + 0.035, bz);
                f.vertex(tes, ax, ly + 0.035, az);
            }
        }
        tes.draw();
        Shapes.end();
    }

    // ---- the pool

    /**
     * The pool's surface: a glow on every cell of water, brighter as the tide rises; powered and near, caustics: two
     * families of wavering lines, one along each axis, crossing on the water and drifting.
     */
    private static void pool(CityFrame f, Look look, double t, float p, double tide, boolean caustics, int accent) {
        double[][] water = look.water;
        if (water.length == 0) return;
        Shapes.begin(true);
        Tessellator tes = CityFrame.start();
        tes.setColorRGBA_I(accent, CityFrame.alpha255(p * (0.05f + 0.05f * (float) (tide + 1) / 2)));
        for (double[] c : water) {
            double x0 = c[0] - 0.5, z0 = c[1] - 0.5;
            f.vertex(tes, x0, POOL_Y, z0);
            f.vertex(tes, x0 + 1, POOL_Y, z0);
            f.vertex(tes, x0 + 1, POOL_Y, z0 + 1);
            f.vertex(tes, x0, POOL_Y, z0 + 1);
        }
        if (caustics) {
            int rgb = Shapes.mix(WATER_LIGHT, WHITE, 0.4);
            for (double[] c : water) {
                double x0 = c[0] - 0.5, z0 = c[1] - 0.5;
                float a = p * (0.16f + 0.1f * (float) Math.sin(t * 0.07 + x0 * 0.9 + z0 * 1.3));
                tes.setColorRGBA_I(rgb, CityFrame.alpha255(a));
                double xa = x0 + 0.5 + 0.3 * Math.sin(z0 * 1.7 + t * 0.05 + x0 * 2.1),
                    xb = x0 + 0.5 + 0.3 * Math.sin((z0 + 1) * 1.7 + t * 0.05 + x0 * 2.1);
                f.line(tes, xa, POOL_Y, z0, xb, POOL_Y, z0 + 1, 0.04);
                double za = z0 + 0.5 + 0.3 * Math.sin(x0 * 1.9 + t * 0.043 + z0 * 1.3),
                    zb = z0 + 0.5 + 0.3 * Math.sin((x0 + 1) * 1.9 + t * 0.043 + z0 * 1.3);
                f.line(tes, x0, POOL_Y, za, x0 + 1, POOL_Y, zb, 0.04);
            }
        }
        tes.draw();
        Shapes.end();
    }

    // ---- from the surface

    /**
     * The forum's grate band over the well (where a grate tile lies), glowing with the tide; at the top of a high tide
     * the rings' crests show through it, blades of light standing round the dais (in a well that has rings).
     */
    private static void surface(TileRoot r, CityFrame f, Look look, double t, double day, float p, double tide,
        boolean on, int accent) {
        World w = r.getWorldObj();
        int code = Parts.deck(Parts.D_GRATE);
        Block grate = PartBlocks.block(code);
        int meta = PartBlocks.meta(code);
        if (grate == null) return;
        float life = p * (on ? 1f : 0.5f);
        float a = life * (0.06f + 0.22f * (float) Math.max(0, tide)) * (0.85f + 0.15f * (float) Math.sin(t * 0.05));
        double top = look.surface + 1.02;
        Shapes.begin(true);
        Tessellator tes = CityFrame.start();
        tes.setColorRGBA_I(on ? accent : AMBER, CityFrame.alpha255(a));
        int shown = 0;
        for (int[] c : GRATE) {
            int[] at = r.worldOf(c[0], look.surface, c[1]);
            if (w.getBlock(at[0], at[1], at[2]) != grate || w.getBlockMetadata(at[0], at[1], at[2]) != meta) continue;
            f.vertex(tes, c[0], top, c[1]);
            f.vertex(tes, c[0] + 1, top, c[1]);
            f.vertex(tes, c[0] + 1, top, c[1] + 1);
            f.vertex(tes, c[0], top, c[1] + 1);
            shown++;
        }
        double crest = RootLook.crest(tide);
        if (crest > 0 && shown > 0 && look.rings > 0) {
            double turn = RootLook.ringTurn(look.rings - 1, t, day);
            for (int i = 0; i < BLADES; i++) {
                double ang = turn + i * 2 * Math.PI / BLADES, rad = 6.0;
                double x = C + Math.cos(ang) * rad, z = C + Math.sin(ang) * rad;
                double h = crest * (0.7 + 0.3 * Math.sin(t * 0.08 + i * 1.3));
                tes.setColorRGBA_I(Shapes.mix(accent, WHITE, 0.4), CityFrame.alpha255(0.7f * life));
                f.line(tes, x, top, z, x, top + h, z, 0.08);
                tes.setColorRGBA_I(accent, CityFrame.alpha255(0.2f * life));
                f.line(tes, x, top, z, x, top + h * 0.7, z, 0.4);
            }
        }
        tes.draw();
        Shapes.end();
    }

    private static void fail(int part, Throwable x) {
        if (OFF[part]) return;
        OFF[part] = true;
        FluxEcho.LOG.warn("The Tidal Well's {} failed and is off until the game restarts", NAMES[part], x);
    }
}
