package com.fluxecho.city.client;

import static com.fluxecho.client.FluxDraw.CYAN;
import static com.fluxecho.client.FluxDraw.WHITE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.client.renderer.Tessellator;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.fluxecho.FluxEcho;
import com.fluxecho.campus.Campus;
import com.fluxecho.campus.ModuleSpec;
import com.fluxecho.campus.ModuleSpecs;
import com.fluxecho.city.TileDomeGenerator;
import com.fluxecho.city.TileHoloTable;
import com.fluxecho.city.TileRoot;
import com.fluxecho.logic.CampusPlan;
import com.fluxecho.logic.DomeGeneratorShape;
import com.fluxecho.logic.HoloLook;
import com.fluxecho.logic.ModuleLook;
import com.fluxecho.logic.OuterRing;
import com.fluxecho.logic.RootShape;
import com.fluxecho.logic.SkyRing;
import com.fluxecho.nexus.ClientTiles;
import com.fluxecho.nexus.TileModule;
import com.fluxecho.nexus.TileMultiblock;
import com.fluxecho.nexus.TileNexus;
import com.fluxecho.render.Shapes;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The Holo Sand Table's own visuals (0.15.0, CITY.md §4.11 Visuals), on top of what every city module shows
 * ({@link CityRender}); where everything goes and how it turns is {@link HoloLook}'s.
 * <ul>
 * <li>Formed: the ring light's segments give way to a rendered ring of light hanging over the table, in the table's
 * colour (a highlight running round it while powered); undocked it burns low.</li>
 * <li>Docked (idle): over the glass a miniature of the campus the table is docked to, made each time it is drawn from
 * what the client knows of that campus: the nexus's column at its phase, the ground and its rings (the outer ring's
 * once it is laid), every formed module of the campus the client has as a box of its own colour, and the zenith's pod
 * with its legs and the root's well in section; 0.16.0 the sky ring's modules as boxes up at the pod's deck with their
 * catwalks to the pod as lines, and the deep root's chamber in section under the well's. It turns slowly, carried up
 * from the glass by a soft column of light.
 * Powered it is brighter (its layers come with the units' functions).</li>
 * </ul>
 * Budgets (CITY.md §1.3, quads a frame on top of {@link CityRender}'s, {@link HoloLook#quads}): the ring light 96
 * near, 24 mid, 16 far; the miniature near at most its ground, rings, {@link HoloLook#MINI_BOXES} boxes and the
 * nexus with their rims, the sections (three under a deep root's chamber), the legs, the catwalks and the column, mid
 * their tops alone, far nothing. With the plinth
 * line and the plate the table stays under 2,000 near, 300 mid and 64 far ({@code HoloLookTest}).
 * <p>
 * Shader-safe as {@link CityRender}: in its frame ({@link CityFrame}) relative to the camera, quads only with the
 * viewer's normal, added onto the world without depth writes (a hologram); the miniature is laid along the world's
 * axes from the table's middle, so it is never mirrored. It reads only the client's tile list and the nexus's synced
 * campus view, never the world; animation by the world time alone, nothing counted, spawned or sent, so a frame drawn
 * again behind a light gate is the same. Each part fails on its own.
 */
@SideOnly(Side.CLIENT)
final class HoloTableRender {

    /** Line widths: the ring light's core and halo, the miniature's lines. */
    private static final double RING_CORE = 0.09, RING_HALO = 0.7, MINI_W = 0.02;

    private static final int RING = 0, MINI = 1;
    private static final String[] NAMES = { "ring light", "campus miniature" };
    private static final boolean[] OFF = new boolean[NAMES.length];

    private HoloTableRender() {}

    /**
     * Draws the Holo Sand Table's own visuals; {@code p} is its distance fade times how far it has formed, {@code tier}
     * how much to draw ({@link ModuleLook#tier}).
     */
    static void draw(TileHoloTable h, CityFrame f, double t, float p, int tier) {
        boolean docked = CityRender.docked(h), on = CityRender.on(h);
        float life = on ? 1f : docked ? 0.6f : 0.3f;
        if (!OFF[RING]) try {
            ring(h, f, t, p, life, tier, on);
        } catch (Throwable x) {
            CityRender.recover();
            fail(RING, x);
        } finally {
            CityRender.close();
        }
        if (docked && tier != ModuleLook.FAR && !OFF[MINI]) try {
            HoloLook.Model m = model(h);
            if (m != null) mini(h, f, t, p * (on ? 1f : 0.75f), m, tier);
        } catch (Throwable x) {
            CityRender.recover();
            fail(MINI, x);
        } finally {
            CityRender.close();
        }
    }

    // ---- the ring light

    /** The ring of light over the table: its core in pieces round it, near with a soft halo. */
    private static void ring(TileHoloTable h, CityFrame f, double t, float p, float life, int tier, boolean on) {
        float a = (0.55f * life + 0.1f) * p;
        if (a <= 0.01f) return;
        int accent = h.moduleColor(), core = Shapes.mix(accent, WHITE, 0.4);
        int n = HoloLook.RING_PIECES[tier];
        boolean nearby = tier == ModuleLook.NEAR;
        double y = HoloLook.RING_Y;
        Shapes.begin(true);
        Tessellator tes = CityFrame.start();
        for (int i = 0; i < n; i++) {
            double a0 = 2 * Math.PI * i / n, a1 = 2 * Math.PI * (i + 1) / n;
            double[] u = HoloLook.onRing(a0), v = HoloLook.onRing(a1);
            float k = a * HoloLook.ringLight((a0 + a1) / 2, t, on);
            tes.setColorRGBA_I(core, CityFrame.alpha255(k));
            f.line(tes, u[0], y, u[1], v[0], y, v[1], RING_CORE);
            if (nearby) {
                tes.setColorRGBA_I(accent, CityFrame.alpha255(0.2f * k));
                f.line(tes, u[0], y, u[1], v[0], y, v[1], RING_HALO);
            }
        }
        tes.draw();
        Shapes.end();
    }

    // ---- the miniature

    /**
     * The miniature of the campus the table is docked to, from the nexus's campus view and the client's tile list; null
     * when the client does not have that nexus. Every module the view lists as built on a ground site is a box of its
     * footprint and height ({@link HoloLook#siteBox}), whether or not the client has its chunks loaded (0.15.0: from
     * the table on an outer diagonal the far side of the campus is 120 to 175 blocks off), refined to its structure's
     * bounds when it has; the zenith and the root, and a formed module the view does not list, come from the tiles.
     * A sky module (0.16.0) stands up at the pod's sky datum (the loaded pod's, else the one the base layer gives) over
     * its platform ({@link HoloLook#skyBox}), its catwalk to the pod drawn as two lines ({@link HoloLook#walks}); the
     * deep root shows its chamber in section under the well's ({@link HoloLook#rootSections}).
     */
    private static HoloLook.Model model(TileHoloTable h) {
        int[] at = h.clientNexus;
        World w = h.getWorldObj();
        if (at == null || w == null) return null;
        List<TileMultiblock> tiles = ClientTiles.all();
        TileNexus n = null;
        for (TileMultiblock m : tiles) {
            if (m instanceof TileNexus x && m.getWorldObj() == w && !m.isInvalid() && Arrays.equals(x.centre(), at)) {
                n = x;
                break;
            }
        }
        if (n == null) return null;
        int[] c = n.centre();
        Campus.View v = n.clientCampus;
        boolean outer = v != null && v.outerLaid;
        ForgeDirection fd = n.front();
        int reach = (outer ? OuterRing.OUTER_R : CampusPlan.GRADE_R) + 24;
        // the campus's middle at the ground: the middle of the centre cell, the forum's floor (Y0 + 1)
        double ox = c[0] + 0.5, oy = c[1] + 1, oz = c[2] + 0.5;
        List<HoloLook.Box> boxes = new ArrayList<>();
        HoloLook.Box pod = null, root = null, chamber = null;
        List<double[]> legs = new ArrayList<>(), walks = new ArrayList<>();
        int legRgb = CYAN;
        int deck = skyDatum(tiles, w, c);
        // the modules the campus view lists as built on its ground sites, loaded or not
        Set<TileModule> shown = new HashSet<>();
        if (v != null) for (Campus.ModuleState ms : v.modules) {
            if (ms.status != Campus.ModuleStatus.BUILT || ms.site < 0) continue;
            ModuleSpec spec = ModuleSpecs.get(ms.key);
            if (spec == null || spec.axis()) continue;
            if (SkyRing.skySite(ms.site)) {
                // a sky module: up on its platform at the pod's deck, its catwalk to the pod
                int[] fp = SkyRing.footprint(ms.site, c[0], c[2], fd.offsetX, fd.offsetZ, 0);
                TileModule loaded = loadedOn(tiles, w, c, ms.key, fp);
                if (loaded != null) shown.add(loaded);
                int[] b = loaded == null ? null : loaded.bounds();
                boxes.add(
                    b != null ? HoloLook.siteBox(fp, b, c[0], c[1], c[2], spec.height, loaded.moduleColor())
                        : HoloLook.skyBox(ms.site, fd.offsetX, fd.offsetZ, deck, spec.height, spec.colour));
                if (SkyRing.platformSite(ms.site)) walks.addAll(HoloLook.walks(ms.site, fd.offsetX, fd.offsetZ, deck));
                continue;
            }
            int[] fp;
            try {
                fp = spec.footprint(ms.site, c[0], c[2], fd.offsetX, fd.offsetZ, 0);
            } catch (UnsupportedOperationException e) {
                continue;
            }
            TileModule loaded = loadedOn(tiles, w, c, ms.key, fp);
            if (loaded != null) shown.add(loaded);
            boxes.add(
                HoloLook.siteBox(
                    fp,
                    loaded == null ? null : loaded.bounds(),
                    c[0],
                    c[1],
                    c[2],
                    spec.height,
                    loaded == null ? spec.colour : loaded.moduleColor()));
        }
        for (TileMultiblock m : tiles) {
            if (!(m instanceof TileModule mod) || m.getWorldObj() != w || m.isInvalid() || !mod.formed()) continue;
            if (shown.contains(mod)) continue;
            // a module docked to another nexus belongs to that campus
            if (mod.clientNexus != null && !Arrays.equals(mod.clientNexus, c)) continue;
            int[] mc = mod.centre();
            if (Math.max(Math.abs(mc[0] - c[0]), Math.abs(mc[2] - c[2])) > reach || Math.abs(mc[1] - c[1]) > 96)
                continue;
            boolean axis = Math.abs(mc[0] - c[0]) <= 2 && Math.abs(mc[2] - c[2]) <= 2;
            if (axis && mod instanceof TileDomeGenerator g) {
                // the zenith's pod in section, and its legs while it stands on them
                int datum = g.datum();
                double half = DomeGeneratorShape.POD_R + 0.5;
                pod = new HoloLook.Box(
                    -half,
                    datum - 1,
                    -half,
                    half,
                    datum - 1 + DomeGeneratorShape.HEIGHT,
                    half,
                    g.moduleColor());
                legRgb = g.moduleColor();
                if (DomeGeneratorShape.moored(g.shapeName()) != null) for (int[] foot : DomeGeneratorShape.feet()) {
                    double len = Math.hypot(foot[0], foot[1]), ha = foot[0] / len * DomeGeneratorShape.LEG_HEAD,
                        hr = foot[1] / len * DomeGeneratorShape.LEG_HEAD;
                    legs.add(
                        new double[] { foot[0] * fd.offsetX - foot[1] * fd.offsetZ, 0,
                            foot[0] * fd.offsetZ + foot[1] * fd.offsetX, ha * fd.offsetX - hr * fd.offsetZ, datum - 1,
                            ha * fd.offsetZ + hr * fd.offsetX });
                }
                continue;
            }
            int[] b = mod.bounds();
            if (b == null) continue;
            if (axis && mod instanceof TileRoot r) {
                // the root's well in section, from its lid down to its floor, and a deep root's chamber under it
                RootShape.Deep d = r.deep();
                HoloLook.Box[] sec = HoloLook
                    .rootSections(b[1] - oy, b[4] + 1 - oy, d == null ? 0 : d.h, mod.moduleColor());
                root = sec[0];
                chamber = sec[1];
                continue;
            }
            boxes.add(
                new HoloLook.Box(
                    b[0] - ox,
                    b[1] - oy,
                    b[2] - oz,
                    b[3] + 1 - ox,
                    b[4] + 1 - oy,
                    b[5] + 1 - oz,
                    mod.moduleColor()));
        }
        return HoloLook.model(n.phase(), outer, CYAN, boxes, pod, legs, legRgb, root, chamber, walks);
    }

    /**
     * The sky datum of the campus centred at {@code c} (0.16.0): its loaded pod's, else the one its base layer gives
     * ({@link DomeGeneratorShape#datum}).
     */
    private static int skyDatum(List<TileMultiblock> tiles, World w, int[] c) {
        for (TileMultiblock m : tiles) {
            if (!(m instanceof TileDomeGenerator g) || m.getWorldObj() != w || m.isInvalid() || !g.formed()) continue;
            int[] mc = g.centre();
            if (Math.abs(mc[0] - c[0]) <= 2 && Math.abs(mc[2] - c[2]) <= 2 && Math.abs(mc[1] - c[1]) <= 96)
                return g.datum();
        }
        return DomeGeneratorShape.datum(c[1]);
    }

    /**
     * The formed module of a key the client has loaded with the centre of its foundation in a footprint
     * {x0, z0, x1, z1}, docked to the nexus centred at {@code c} or to none; null when there is none.
     */
    private static TileModule loadedOn(List<TileMultiblock> tiles, World w, int[] c, String key, int[] fp) {
        for (TileMultiblock m : tiles) {
            if (!(m instanceof TileModule mod) || m.getWorldObj() != w || m.isInvalid() || !mod.formed()) continue;
            if (!key.equals(mod.moduleKey())) continue;
            if (mod.clientNexus != null && !Arrays.equals(mod.clientNexus, c)) continue;
            int[] mc = mod.centre();
            if (mc[0] >= fp[0] && mc[0] <= fp[2] && mc[2] >= fp[1] && mc[2] <= fp[3]) return mod;
        }
        return null;
    }

    /**
     * The miniature, turned as the world time has it: near the column of light up from the glass, the ground's fill and
     * its rings, the nexus and every box with its top's rim, the sections, the legs and the catwalks; mid the ground's
     * fill and its edge, the tops of the nexus and the boxes and the sections' planes.
     */
    private static void mini(TileHoloTable h, CityFrame f, double t, float p, HoloLook.Model m, int tier) {
        if (p <= 0.01f) return;
        boolean nearby = tier == ModuleLook.NEAR;
        int accent = h.moduleColor(), line = Shapes.mix(accent, WHITE, 0.3);
        double turn = HoloLook.turn(t), ox = f.x(HoloLook.CX, HoloLook.CZ), oz = f.z(HoloLook.CX, HoloLook.CZ);
        Mini mini = new Mini(m, f, turn, ox, oz);
        Shapes.begin(true);
        if (nearby) Shapes
            .beam(ox, f.y(HoloLook.TABLE_TOP), oz, f.y(m.ground), HoloLook.MINI_R * 0.9, accent, 0.14f * p, 0.03f * p);
        Tessellator tes = CityFrame.start();
        // the ground: its edge's octagon filled faintly, as three quads
        double[][] edge = m.rings.get(m.rings.size() - 1);
        tes.setColorRGBA_I(accent, CityFrame.alpha255(0.07f * p));
        for (int[] q : new int[][] { { 0, 1, 2, 3 }, { 3, 4, 7, 0 }, { 4, 5, 6, 7 } }) {
            for (int i : q) mini.vertex(tes, edge[i][0], 0, edge[i][1]);
        }
        // the rings on the ground (mid: its edge alone)
        tes.setColorRGBA_I(line, CityFrame.alpha255(0.35f * p));
        for (int r = nearby ? 0 : m.rings.size() - 1; r < m.rings.size(); r++) {
            double[][] o = m.rings.get(r);
            for (int i = 0; i < o.length; i++) {
                double[] a = o[i], b = o[(i + 1) % o.length];
                mini.line(tes, a[0], 0, a[1], b[0], 0, b[1]);
            }
        }
        // the nexus and the modules
        box(tes, mini, m.nexus, p, nearby);
        for (HoloLook.Box b : m.boxes) box(tes, mini, b, p, nearby);
        // the zenith's pod and the root's well in section: two planes crossed on the axis
        for (HoloLook.Box b : m.sections) {
            tes.setColorRGBA_I(b.rgb, CityFrame.alpha255(0.16f * p));
            mini.vertex(tes, b.x0, b.y0, 0);
            mini.vertex(tes, b.x1, b.y0, 0);
            mini.vertex(tes, b.x1, b.y1, 0);
            mini.vertex(tes, b.x0, b.y1, 0);
            mini.vertex(tes, 0, b.y0, b.z0);
            mini.vertex(tes, 0, b.y0, b.z1);
            mini.vertex(tes, 0, b.y1, b.z1);
            mini.vertex(tes, 0, b.y1, b.z0);
            if (!nearby) continue;
            tes.setColorRGBA_I(Shapes.mix(b.rgb, WHITE, 0.3), CityFrame.alpha255(0.6f * p));
            mini.line(tes, b.x0, b.y0, 0, b.x1, b.y0, 0);
            mini.line(tes, b.x0, b.y1, 0, b.x1, b.y1, 0);
            mini.line(tes, b.x0, b.y0, 0, b.x0, b.y1, 0);
            mini.line(tes, b.x1, b.y0, 0, b.x1, b.y1, 0);
            mini.line(tes, 0, b.y0, b.z0, 0, b.y0, b.z1);
            mini.line(tes, 0, b.y1, b.z0, 0, b.y1, b.z1);
            mini.line(tes, 0, b.y0, b.z0, 0, b.y1, b.z0);
            mini.line(tes, 0, b.y0, b.z1, 0, b.y1, b.z1);
        }
        if (nearby) {
            tes.setColorRGBA_I(Shapes.mix(m.legRgb, WHITE, 0.3), CityFrame.alpha255(0.5f * p));
            for (double[] l : m.legs) mini.line(tes, l[0], l[1], l[2], l[3], l[4], l[5]);
            // the sky ring's catwalks from the pod out to their platforms
            tes.setColorRGBA_I(line, CityFrame.alpha255(0.5f * p));
            for (double[] l : m.walks) mini.line(tes, l[0], l[1], l[2], l[3], l[4], l[5]);
        }
        tes.draw();
        Shapes.end();
    }

    /** A box of the miniature: near its top and four sides faintly and its top's rim bright, mid its top alone. */
    private static void box(Tessellator tes, Mini mini, HoloLook.Box b, float p, boolean nearby) {
        int rgb = Shapes.mix(b.rgb, WHITE, 0.15);
        tes.setColorRGBA_I(rgb, CityFrame.alpha255(0.3f * p));
        mini.vertex(tes, b.x0, b.y1, b.z0);
        mini.vertex(tes, b.x1, b.y1, b.z0);
        mini.vertex(tes, b.x1, b.y1, b.z1);
        mini.vertex(tes, b.x0, b.y1, b.z1);
        if (!nearby) return;
        tes.setColorRGBA_I(rgb, CityFrame.alpha255(0.16f * p));
        double[][] c = { { b.x0, b.z0 }, { b.x1, b.z0 }, { b.x1, b.z1 }, { b.x0, b.z1 } };
        for (int i = 0; i < 4; i++) {
            double[] u = c[i], v = c[(i + 1) % 4];
            mini.vertex(tes, u[0], b.y0, u[1]);
            mini.vertex(tes, v[0], b.y0, v[1]);
            mini.vertex(tes, v[0], b.y1, v[1]);
            mini.vertex(tes, u[0], b.y1, u[1]);
        }
        tes.setColorRGBA_I(Shapes.mix(b.rgb, WHITE, 0.4), CityFrame.alpha255(0.7f * p));
        for (int i = 0; i < 4; i++) {
            double[] u = c[i], v = c[(i + 1) % 4];
            mini.line(tes, u[0], b.y1, u[1], v[0], b.y1, v[1]);
        }
    }

    /** The miniature placed this frame: campus points to camera-relative ones, and its lines. */
    private static final class Mini {

        final HoloLook.Model m;
        final CityFrame f;
        final double turn, ox, oz;

        Mini(HoloLook.Model m, CityFrame f, double turn, double ox, double oz) {
            this.m = m;
            this.f = f;
            this.turn = turn;
            this.ox = ox;
            this.oz = oz;
        }

        void vertex(Tessellator t, double x, double y, double z) {
            double[] q = HoloLook.at(m, x, y, z, turn);
            t.addVertex(ox + q[0], f.y(q[1]), oz + q[2]);
        }

        void line(Tessellator t, double x0, double y0, double z0, double x1, double y1, double z1) {
            double[] a = HoloLook.at(m, x0, y0, z0, turn), b = HoloLook.at(m, x1, y1, z1, turn);
            CityFrame.lineRel(t, ox + a[0], f.y(a[1]), oz + a[2], ox + b[0], f.y(b[1]), oz + b[2], MINI_W);
        }
    }

    private static void fail(int part, Throwable x) {
        if (OFF[part]) return;
        OFF[part] = true;
        FluxEcho.LOG.warn("The Holo Sand Table's {} failed and is off until the game restarts", NAMES[part], x);
    }
}
