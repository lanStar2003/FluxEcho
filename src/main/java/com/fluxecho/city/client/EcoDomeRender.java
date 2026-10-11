package com.fluxecho.city.client;

import static com.fluxecho.client.FluxDraw.WHITE;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;
import net.minecraftforge.common.IPlantable;

import org.lwjgl.opengl.GL11;

import com.fluxecho.FluxEcho;
import com.fluxecho.city.TileEcoDome;
import com.fluxecho.client.Motes;
import com.fluxecho.client.Motifs;
import com.fluxecho.logic.EcoDomeShape;
import com.fluxecho.logic.ModuleLook;
import com.fluxecho.logic.ShapeKit;
import com.fluxecho.logic.UnitLayout;
import com.fluxecho.render.Shapes;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The Eco Dome's own visuals (0.11.0, CITY.md §4.1 Visuals), on top of what every city module shows
 * ({@link CityRender}).
 * <ul>
 * <li>Formed: the world tree's trunk and seat have given way to a tree of light: three strands winding up round a soft
 * core from the pond to the seat, a pulse climbing them, six branches spreading under the crown, a canopy of glowing
 * leaves under the crown lantern swaying slowly, leaves of light drifting down, the lantern and the canopy glowing.
 * Idle (undocked or without power) it burns low. Seen from beyond {@link CityRender#NEAR} blocks it is a core of light
 * and a cloud of glow where the canopy is.</li>
 * <li>Docked: the dome's lit ribs (the eight meridians from the drum up to the lantern, and the latitude rings at y12
 * and y16) breathe in the dome's green, a wave of light rising them; on the outside of the glass seen from outside, on
 * the inside seen from within. The planters show their samples as small holograms over their sockets on a cone of
 * light, turning: a crop cycles through its growth stages, a bee circles its socket.</li>
 * <li>Powered: pollen rises from the planters and the garden, butterflies (near only) drift between the ground, the
 * islands and the gallery, and rings ripple across the pond.</li>
 * </ul>
 * Budgets (CITY.md §1.3, quads a frame for the Eco Dome on top of {@link CityRender}'s): near, the tree about 260
 * (72 strands, 4 core, 24 branches, {@link #LEAVES} leaves, {@link #FALLING} falling, 3 glows), the ribs about 350
 * (8 meridians of up to 19 pieces with a halo, two rings of {@link #RING_SEGMENTS}), the holograms at most 6 a socket
 * (216), pollen {@link #POLLEN}, butterflies 3 each ({@link #BUTTERFLIES}), ripples {@link #RIPPLES} rings of
 * {@link #RIPPLE_SEGMENTS}; beyond {@link CityRender#NEAR}, the tree 15 and the ribs about 120 (every other piece, no
 * halo, rings of 16); beyond the effect range (the far tier, {@link ModuleLook#tier}), the tree 15 and the meridians
 * alone in four pieces each (32). With the plinth line, the plate and the socket items that keeps the dome under 2,000
 * near, 300 mid and 64 far.
 * <p>
 * Shader-safe as {@link CityRender}: everything in its frame ({@link CityFrame}) relative to the camera, quads only, a
 * normal towards the viewer, no depth writes; the holograms and the leaves add their light. Animation follows the world
 * time alone; its tables (the ribs hugging the glass, the planters, the garden) are worked out once from
 * {@link EcoDomeShape}. Each part fails on its own.
 */
@SideOnly(Side.CLIENT)
final class EcoDomeRender {

    /** The trunk's axis and the dome's middle, in the shape (fractional). */
    private static final double TX = EcoDomeShape.TRUNK_X + 0.5, TZ = EcoDomeShape.TRUNK_Z + 0.5,
        DOME_MID_Y = EcoDomeShape.DOME_Y + 0.5, R = EcoDomeShape.DOME_R;
    /** The trunk runs from the pond to the seat; the canopy under the lantern. */
    private static final double TRUNK_LOW = 1.0, TRUNK_HIGH = 17.6, CANOPY_Y = 16.4, CANOPY_RH = 4.4, CANOPY_RV = 2.0,
        LANTERN_Y = 19.4;
    /** How far the crown island and the floating island reach from the trunk (where falling leaves land). */
    private static final double CROWN_R = 3.6, ISLAND_R = 4.8;
    /**
     * The ripple rings' height: a little over the pond's still surface (its water at y1, a source's top one ninth below
     * the block's), so the water a shader pack draws with depth (and waves) never covers them; the largest ring.
     */
    private static final double POND_Y = 1 + ModuleLook.WATER_TOP + 0.035, RIPPLE_MAX = 0.8;
    private static final int STRANDS = 3, STRAND_PIECES = 24, BRANCHES = 6, BRANCH_PIECES = 4, LEAVES = 140,
        FALLING = 16, LOD_GLOWS = 10, RIBS = 8, RING_SEGMENTS = 32, POLLEN = 40, BUTTERFLIES = 8, RIPPLES = 6,
        RIPPLE_SEGMENTS = 16;
    /** The layers the ribs and the rings are known at. */
    private static final int LOW = 1, HIGH = EcoDomeShape.HEIGHT - 1;
    private static final int[] RING_Y = { 12, 16 };
    private static final int LEAF_LIGHT = 0xB8F5C0, LEAF_DARK = 0x3FA860, POLLEN_RGB = 0xFFF3A8, BEE = 0xFFD24A;
    private static final int[] WINGS = { 0xFFB45A, 0x7EC8FF, 0xF4F0E8, 0x9CF0A6, 0xC79BFF, 0xFF8CB4 };

    /** The ribs' and rings' distance from the dome's axis, outside and inside the glass, by layer; NaN for none. */
    private static final double[][] RIB_OUT, RIB_IN, RING_OUT, RING_IN;
    /** The planters' sockets {x, y, z} (cells), their tops, and the waypoints of the butterflies. */
    private static final double[][] GROUND_TOPS, UPPER_TOPS, LOW_RING, HIGH_RING, POLLEN_AT;
    /** The pond's water cells {x, z, room}: where a ripple may start and how wide it may grow and stay on water. */
    private static final double[][] WATER = ModuleLook.water(EcoDomeShape.BUILD, 1, RIPPLE_MAX)
        .toArray(new double[0][]);

    /** The parts that can fail on their own. */
    private static final int TREE = 0, RIBS_PART = 1, HOLOS = 2, POLLEN_PART = 3, FLIES = 4, POND = 5;
    private static final String[] NAMES = { "tree of light", "breathing ribs", "planter holograms", "pollen",
        "butterflies", "pond ripples" };
    private static final boolean[] OFF = new boolean[NAMES.length];

    static {
        RIB_OUT = new double[RIBS][HIGH + 1];
        RIB_IN = new double[RIBS][HIGH + 1];
        for (int k = 0; k < RIBS; k++) for (int y = 0; y <= HIGH; y++) {
            double[] e = extent(k * Math.PI / 4, y);
            RIB_OUT[k][y] = e[0];
            RIB_IN[k][y] = e[1];
        }
        RING_OUT = new double[RING_Y.length][RING_SEGMENTS];
        RING_IN = new double[RING_Y.length][RING_SEGMENTS];
        for (int r = 0; r < RING_Y.length; r++) for (int i = 0; i < RING_SEGMENTS; i++) {
            double[] e = extent(i * 2 * Math.PI / RING_SEGMENTS, RING_Y[r]);
            RING_OUT[r][i] = e[0];
            RING_IN[r][i] = e[1];
        }
        List<double[]> ground = new ArrayList<>(), upper = new ArrayList<>(), pollen = new ArrayList<>();
        for (UnitLayout.Unit u : EcoDomeShape.UNITS.units()) {
            double sx = 0, sz = 0;
            for (int[] s : u.sockets) {
                sx += s[0] + 0.5;
                sz += s[2] + 0.5;
                pollen.add(new double[] { s[0] + 0.5, u.floor + 1.0, s[2] + 0.5 });
            }
            int n = Math.max(1, u.sockets.size());
            double[] top = { sx / n, u.floor + 1.4, sz / n };
            (u.floor <= 1 ? ground : upper).add(top);
        }
        // the open garden: soil round the trunk off the axes (where the player's own hives and crops go)
        for (int i = 0; pollen.size() < POLLEN; i++) {
            double a = Motifs.hash(i * 7 + 1) * 2 * Math.PI, r = 5.2 + 4.4 * Motifs.hash(i * 11 + 3);
            pollen.add(new double[] { TX + Math.cos(a) * r, 1.0, TZ + Math.sin(a) * r });
        }
        GROUND_TOPS = ground.toArray(new double[0][]);
        UPPER_TOPS = upper.toArray(new double[0][]);
        POLLEN_AT = pollen.subList(0, POLLEN)
            .toArray(new double[0][]);
        // the air round the island and under the gallery, low and high, where the butterflies change level
        LOW_RING = new double[6][];
        HIGH_RING = new double[6][];
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3 + 0.3;
            LOW_RING[i] = new double[] { TX + Math.cos(a) * 7, 3.6, TZ + Math.sin(a) * 7 };
            HIGH_RING[i] = new double[] { TX + Math.cos(a + 0.5) * 7, 9.2, TZ + Math.sin(a + 0.5) * 7 };
        }
    }

    private EcoDomeRender() {}

    /**
     * How far from the dome's axis the shell reaches along a direction at a layer, {outside, inside}: the outermost
     * and innermost faces of the drum's or the dome's cells near the ray (the portal block not counted); NaN for
     * none. The ribs hug these, so they lie on the glass whatever its steps.
     */
    private static double[] extent(double ang, int y) {
        double cx = Math.cos(ang), cz = Math.sin(ang), half = 0.5 * (Math.abs(cx) + Math.abs(cz));
        double out = Double.NaN, in = Double.NaN;
        for (int x = 0; x < EcoDomeShape.WIDTH; x++) for (int z = 0; z < EcoDomeShape.DEPTH; z++) {
            if (!shell(x, y, z)) continue;
            double dx = x + 0.5 - TX, dz = z + 0.5 - TZ;
            double along = dx * cx + dz * cz, across = Math.abs(-dx * cz + dz * cx);
            if (along <= 0 || across > 0.75) continue;
            if (Double.isNaN(out) || along + half > out) out = along + half;
            if (Double.isNaN(in) || along - half < in) in = along - half;
        }
        return new double[] { out, in };
    }

    /**
     * Whether a cell is part of the drum or the dome (not the portal block): their walls, glass and ribs, the gate
     * niche's soft ones too (the same blocks).
     */
    private static boolean shell(int x, int y, int z) {
        if (EcoDomeShape.blockWall(x, z)) return false;
        char ch = ShapeKit.checkedTwin(EcoDomeShape.cell(x, y, z));
        if (y <= 7) return EcoDomeShape.onEdge(x, z) && "wWLGnA".indexOf(ch) >= 0;
        return ch == 'L' || ch == 'G';
    }

    /**
     * Draws the Eco Dome's own visuals; {@code p0} is its distance fade times how far it has formed, {@code cam} the
     * camera in its frame, {@code tier} how much to draw for it ({@link ModuleLook#tier}).
     */
    static void draw(TileEcoDome d, CityFrame f, double t, float p0, double[] cam, int tier) {
        if (p0 <= 0.01f) return;
        boolean nearby = tier == ModuleLook.NEAR;
        boolean docked = CityRender.docked(d), on = CityRender.on(d);
        float life = p0 * (on ? 1f : docked ? 0.6f : 0.3f);
        int accent = d.moduleColor();
        if (!OFF[TREE]) try {
            if (nearby) tree(f, t, life, accent);
            else treeFar(f, t, life, accent);
        } catch (Throwable x) {
            CityRender.recover();
            fail(TREE, x);
        } finally {
            CityRender.close();
        }
        if (docked && !OFF[RIBS_PART]) try {
            ribs(f, t, p0, accent, tier, insideDome(cam));
        } catch (Throwable x) {
            CityRender.recover();
            fail(RIBS_PART, x);
        } finally {
            CityRender.close();
        }
        if (!nearby) return;
        if (docked && !OFF[HOLOS]) try {
            holograms(d, f, t, p0, accent);
        } catch (Throwable x) {
            CityRender.recover();
            fail(HOLOS, x);
        } finally {
            CityRender.close();
        }
        if (!on) return;
        if (!OFF[POLLEN_PART]) try {
            pollen(f, t, p0);
        } catch (Throwable x) {
            CityRender.recover();
            fail(POLLEN_PART, x);
        } finally {
            CityRender.close();
        }
        if (!OFF[FLIES]) try {
            butterflies(f, t, p0);
        } catch (Throwable x) {
            CityRender.recover();
            fail(FLIES, x);
        } finally {
            CityRender.close();
        }
        if (!OFF[POND]) try {
            ripples(f, t, p0);
        } catch (Throwable x) {
            CityRender.recover();
            fail(POND, x);
        } finally {
            CityRender.close();
        }
    }

    /**
     * Whether the camera is under the glass (in the drum or the dome) or in the portal block, which opens on it: the
     * ribs are then seen from inside.
     */
    private static boolean insideDome(double[] cam) {
        double r = Math.hypot(cam[0] - TX, cam[2] - TZ), dy = cam[1] - DOME_MID_Y;
        boolean dome = r < R + 0.4 && cam[1] > -1 && (dy <= 0 || Math.sqrt(r * r + dy * dy) < R + 0.4);
        boolean block = cam[0] >= 2 && cam[0] <= 25 && cam[2] >= 0 && cam[2] <= 12 && cam[1] > -1 && cam[1] < 12;
        return dome || block;
    }

    // ---- the tree of light

    /** The whole tree, near: strands, core, branches, canopy, falling leaves and the glows. */
    private static void tree(CityFrame f, double t, float p, int accent) {
        if (p <= 0.01f) return;
        int bright = Shapes.mix(accent, WHITE, 0.35);
        Shapes.begin(true);
        double bx = f.x(TX, TZ), bz = f.z(TX, TZ);
        Shapes.beam(bx, f.y(TRUNK_LOW), bz, f.y(TRUNK_HIGH), 0.45, accent, 0.28f * p, 0.14f * p);
        Tessellator tes = CityFrame.start();
        // the strands, a pulse climbing them
        double climb = (t * 0.06) % (TRUNK_HIGH - TRUNK_LOW + 6) + TRUNK_LOW - 3;
        for (int s = 0; s < STRANDS; s++) {
            double[] prev = null;
            for (int i = 0; i <= STRAND_PIECES; i++) {
                double u = i / (double) STRAND_PIECES, y = TRUNK_LOW + u * (TRUNK_HIGH - TRUNK_LOW);
                double r = (y < 2.2 ? 0.55 - 0.15 * (y - TRUNK_LOW) / 1.2 : 0.4) - 0.2 * u;
                double a = s * 2 * Math.PI / STRANDS + y * 0.75 + t * 0.008;
                double[] pt = { TX + Math.cos(a) * r, y, TZ + Math.sin(a) * r };
                if (prev != null) {
                    double dy = y - climb;
                    float k = p * (0.45f + 0.2f * (float) Math.sin(t * 0.05 + i * 0.4 + s)
                        + 0.6f * (float) Math.exp(-dy * dy / 2));
                    tes.setColorRGBA_I(bright, CityFrame.alpha255(k));
                    f.line(tes, prev[0], prev[1], prev[2], pt[0], pt[1], pt[2], 0.07);
                }
                prev = pt;
            }
        }
        // the branches, spreading and rising under the crown
        for (int j = 0; j < BRANCHES; j++) {
            double y0 = 12.8 + j * 0.45, a0 = j * 2 * Math.PI / BRANCHES + 0.3 + Math.sin(t * 0.01 + j) * 0.04;
            double[] prev = { TX, y0, TZ };
            for (int i = 1; i <= BRANCH_PIECES; i++) {
                double u = i / (double) BRANCH_PIECES, r = 0.3 + u * 3.4, a = a0 + u * 0.35;
                double[] pt = { TX + Math.cos(a) * r, y0 + 2.0 * Math.sin(u * Math.PI / 2), TZ + Math.sin(a) * r };
                tes.setColorRGBA_I(accent, CityFrame.alpha255(p * (0.5f - 0.25f * (float) u)));
                f.line(tes, prev[0], prev[1], prev[2], pt[0], pt[1], pt[2], 0.06 - 0.03 * u);
                prev = pt;
            }
        }
        // the canopy: leaves on a cap under the lantern, each tilted its own way, swaying
        for (int i = 0; i < LEAVES; i++) leaf(tes, f, i, t, p, accent);
        tes.draw();
        Shapes.end();

        Motes.begin();
        Motes.add(f.x(TX, TZ), f.y(LANTERN_Y), f.z(TX, TZ), 1.4, accent, 0.25f * p);
        Motes.add(f.x(TX, TZ), f.y(CANOPY_Y - 0.4), f.z(TX, TZ), 2.6, accent, 0.12f * p);
        Motes.add(f.x(TX, TZ), f.y(TRUNK_LOW + 0.3), f.z(TX, TZ), 0.8, bright, 0.3f * p);
        // leaves of light drifting down from the canopy onto the crown island, the island or the garden
        for (int i = 0; i < FALLING; i++) {
            double h = Motifs.hash(i * 17 + 5), g = Motifs.hash(i * 31 + 9);
            double fr = (t / 220.0 + h) % 1;
            double r0 = 2.5 + 3.5 * Motifs.hash(i * 23 + 2), a = g * 2 * Math.PI + fr * 1.5,
                r = r0 + 0.4 * Math.sin(t * 0.03 + i);
            double floor = r0 < CROWN_R ? EcoDomeShape.CROWN_Y + 1.05
                : r0 < ISLAND_R ? EcoDomeShape.DECK_Y + 1.05 : 1.05;
            double y = CANOPY_Y - 0.5 - fr * (CANOPY_Y - 0.5 - floor);
            double lx = TX + Math.cos(a) * r, lz = TZ + Math.sin(a) * r;
            Motes.add(
                f.x(lx, lz),
                f.y(y),
                f.z(lx, lz),
                0.06,
                i % 4 == 0 ? POLLEN_RGB : LEAF_LIGHT,
                (float) Math.sin(fr * Math.PI) * 0.6f * p);
        }
        Motes.end();
    }

    /**
     * One leaf of the canopy: a narrow diamond on a cap of the canopy's ellipsoid (a golden spiral, some leaves
     * further in), tilted its own way and swaying round the upright.
     */
    private static void leaf(Tessellator tes, CityFrame f, int i, double t, float p, int accent) {
        double h1 = Motifs.hash(i * 13 + 1), h2 = Motifs.hash(i * 29 + 7), h3 = Motifs.hash(i * 43 + 11);
        double u = (i + 0.5) / LEAVES, pol = Math.acos(1 - u * 1.25), az = i * 2.39996;
        double depth = 0.65 + 0.35 * h1;
        double sx = Math.sin(pol) * Math.cos(az), sz = Math.sin(pol) * Math.sin(az), sy = Math.cos(pol);
        double cx = TX + sx * CANOPY_RH * depth, cy = CANOPY_Y + sy * CANOPY_RV * depth - 0.6,
            cz = TZ + sz * CANOPY_RH * depth;
        double sway = 0.25 * Math.sin(t * 0.035 + i * 0.7), yaw = az + Math.PI / 2 + (h2 - 0.5) + sway;
        double tilt = 0.5 + 0.6 * h3, len = 0.22 + 0.12 * h2, wid = 0.09 + 0.05 * h1;
        // the leaf's long axis (level, turned by yaw, tipped down by tilt) and its width (level, across it)
        double ax = Math.cos(yaw) * Math.cos(tilt), ay = -Math.sin(tilt), az2 = Math.sin(yaw) * Math.cos(tilt);
        double bx = -Math.sin(yaw), bz = Math.cos(yaw);
        int rgb = h3 < 0.33 ? LEAF_DARK : h3 < 0.75 ? accent : LEAF_LIGHT;
        float a = p * (0.32f + 0.25f * (float) h2) * (0.8f + 0.2f * (float) Math.sin(t * 0.04 + i));
        tes.setColorRGBA_I(rgb, CityFrame.alpha255(a));
        f.vertex(tes, cx + ax * len, cy + ay * len, cz + az2 * len);
        f.vertex(tes, cx + bx * wid, cy, cz + bz * wid);
        f.vertex(tes, cx - ax * len, cy - ay * len, cz - az2 * len);
        f.vertex(tes, cx - bx * wid, cy, cz - bz * wid);
    }

    /** The tree from afar: its core of light and a cloud of glow where the canopy is. */
    private static void treeFar(CityFrame f, double t, float p, int accent) {
        if (p <= 0.01f) return;
        Shapes.begin(true);
        Shapes.beam(f.x(TX, TZ), f.y(TRUNK_LOW), f.z(TX, TZ), f.y(TRUNK_HIGH), 0.5, accent, 0.3f * p, 0.18f * p);
        Shapes.end();
        Motes.begin();
        for (int i = 0; i < LOD_GLOWS; i++) {
            double a = i * 2 * Math.PI / LOD_GLOWS + 0.4 * Motifs.hash(i), r = 1.5 + 2.2 * Motifs.hash(i * 5 + 2);
            double y = CANOPY_Y - 0.4 + 1.2 * Motifs.hash(i * 3 + 1) + 0.1 * Math.sin(t * 0.03 + i);
            double lx = TX + Math.cos(a) * r, lz = TZ + Math.sin(a) * r;
            Motes.add(f.x(lx, lz), f.y(y), f.z(lx, lz), 1.3, i % 3 == 0 ? LEAF_LIGHT : accent, 0.22f * p);
        }
        Motes.add(f.x(TX, TZ), f.y(LANTERN_Y), f.z(TX, TZ), 1.6, accent, 0.25f * p);
        Motes.end();
    }

    // ---- the breathing ribs

    /**
     * The dome's lit ribs breathing in its green: each meridian a line along the shell from the drum's foot to the
     * lantern, a wave of light rising it, and the two latitude rings; outside the glass for a camera outside, inside
     * for one within. Near, a soft halo along each; mid, every other piece and no halo; far, the meridians alone in
     * pieces four layers long.
     */
    private static void ribs(CityFrame f, double t, float p, int accent, int tier, boolean inside) {
        if (p <= 0.01f) return;
        double[][] table = inside ? RIB_IN : RIB_OUT, rings = inside ? RING_IN : RING_OUT;
        double off = inside ? -0.06 : 0.06;
        boolean nearby = tier == ModuleLook.NEAR;
        int step = nearby ? 1 : tier == ModuleLook.MID ? 2 : 4;
        Shapes.begin(true);
        Tessellator tes = CityFrame.start();
        for (int k = 0; k < RIBS; k++) {
            double ang = k * Math.PI / 4, cx = Math.cos(ang), cz = Math.sin(ang);
            double[] prev = null;
            float prevK = 0;
            for (int y = LOW + 1; y <= HIGH; y += step) {
                double r = table[k][y];
                if (Double.isNaN(r)) {
                    prev = null;
                    continue;
                }
                double ly = y + 0.5;
                double[] pt = { TX + cx * (r + off), ly, TZ + cz * (r + off) };
                float b = breath(t, ly, k);
                if (prev != null) {
                    float a = p * (0.25f + 0.55f * (b + prevK) / 2);
                    int rgb = Shapes.mix(accent, WHITE, 0.3 * (b + prevK) / 2);
                    tes.setColorRGBA_I(rgb, CityFrame.alpha255(a));
                    f.line(tes, prev[0], prev[1], prev[2], pt[0], pt[1], pt[2], 0.08);
                    if (nearby) {
                        tes.setColorRGBA_I(accent, CityFrame.alpha255(0.22f * a));
                        f.line(tes, prev[0], prev[1], prev[2], pt[0], pt[1], pt[2], 0.4);
                    }
                }
                prev = pt;
                prevK = b;
            }
        }
        for (int r = 0; r < (tier == ModuleLook.FAR ? 0 : RING_Y.length); r++) {
            double ly = RING_Y[r] + 0.5;
            float b = breath(t, ly, r * 3);
            float a = p * (0.2f + 0.45f * b);
            tes.setColorRGBA_I(Shapes.mix(accent, WHITE, 0.25 * b), CityFrame.alpha255(a));
            for (int i = 0; i < RING_SEGMENTS; i += step) {
                int j = (i + step) % RING_SEGMENTS;
                double r0 = rings[r][i], r1 = rings[r][j];
                if (Double.isNaN(r0) || Double.isNaN(r1)) continue;
                double a0 = i * 2 * Math.PI / RING_SEGMENTS, a1 = j * 2 * Math.PI / RING_SEGMENTS;
                f.line(
                    tes,
                    TX + Math.cos(a0) * (r0 + off),
                    ly,
                    TZ + Math.sin(a0) * (r0 + off),
                    TX + Math.cos(a1) * (r1 + off),
                    ly,
                    TZ + Math.sin(a1) * (r1 + off),
                    0.08);
            }
        }
        tes.draw();
        Shapes.end();
    }

    /** The breath at a height of a rib: 0 to 1, a slow wave rising the dome (each rib a little out of step). */
    private static float breath(double t, double y, int k) {
        return (float) (0.5 + 0.5 * Math.sin(t * 0.06 - y * 0.45 + k * 0.8));
    }

    // ---- the planter holograms

    /**
     * Over every planter socket that keeps a sample: a cone of light up from the socket's top to a small hologram of
     * the sample, turning slowly. A seed's hologram is its crop growing through its stages; a bee's has a bee circling
     * the socket. Blocks' pictures first and then items', each render pass in its colour, the light added.
     */
    private static void holograms(TileEcoDome d, CityFrame f, double t, float p, int accent) {
        if (p <= 0.02f) return;
        World w = d.getWorldObj();
        int slots = d.samples()
            .getSlots();
        double camX = RenderManager.renderPosX, camY = RenderManager.renderPosY, camZ = RenderManager.renderPosZ;
        double reach = CityRender.NEAR * CityRender.NEAR;
        List<int[]> shown = new ArrayList<>();
        for (int slot = 0; slot < slots; slot++) {
            ItemStack s = d.sample(slot);
            int[] c = s == null ? null : d.slotCell(slot);
            if (c == null) continue;
            double dx = c[0] + 0.5 - camX, dy = c[1] + 1 - camY, dz = c[2] + 0.5 - camZ;
            if (dx * dx + dy * dy + dz * dz <= reach) shown.add(new int[] { slot, c[0], c[1], c[2] });
        }
        if (shown.isEmpty()) return;
        Shapes.begin(true);
        Tessellator tes = CityFrame.start();
        double yaw = Math.toRadians(RenderManager.instance.playerViewY);
        double rx = -Math.cos(yaw) * 0.18, rz = -Math.sin(yaw) * 0.18;
        for (int[] s : shown) {
            double x = s[1] + 0.5 - camX, y = s[2] + 1 - camY, z = s[3] + 0.5 - camZ;
            float k = p * (0.5f + 0.15f * (float) Math.sin(t * 0.07 + s[0]));
            // the cone: two crossed planes turned to the camera, from a point of light to the picture's width
            int i0 = CityFrame.alpha255(0.35f * k);
            tes.setColorRGBA_I(accent, i0);
            tes.addVertex(x - rx * 0.15, y + 0.02, z - rz * 0.15);
            tes.addVertex(x + rx * 0.15, y + 0.02, z + rz * 0.15);
            tes.setColorRGBA_I(accent, 0);
            tes.addVertex(x + rx * 1.2, y + 0.42, z + rz * 1.2);
            tes.addVertex(x - rx * 1.2, y + 0.42, z - rz * 1.2);
        }
        tes.draw();
        Shapes.end();

        Motes.begin();
        for (int[] s : shown) {
            double x = s[1] + 0.5 - camX, y = s[2] + 1 - camY, z = s[3] + 0.5 - camZ;
            Motes.add(x, y + 0.03, z, 0.22, accent, 0.3f * p);
            ItemStack st = d.sample(s[0]);
            if (st != null && bee(st)) {
                double a = t * 0.15 + s[0];
                double bx = x + Math.cos(a) * 0.38, by = y + 0.3 + 0.06 * Math.sin(t * 0.4 + s[0]),
                    bz = z + Math.sin(a) * 0.38;
                Motes.add(bx, by, bz, 0.07, BEE, 0.9f * p);
                Motes.add(bx, by + 0.03, bz, 0.05, WHITE, 0.45f * p * (float) Math.abs(Math.sin(t * 1.3)));
            }
        }
        Motes.end();

        CityRender.worldState();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.05f);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        Minecraft mc = Minecraft.getMinecraft();
        for (int sheet = 0; sheet <= 1; sheet++) {
            mc.getTextureManager()
                .bindTexture(sheet == 0 ? TextureMap.locationBlocksTexture : TextureMap.locationItemsTexture);
            tes.startDrawingQuads();
            CityFrame.normal(tes);
            for (int[] s : shown) {
                ItemStack st = d.sample(s[0]);
                if (st == null || st.getItem() == null) continue;
                double x = s[1] + 0.5 - camX, y = s[2] + 1.62 + 0.04 * Math.sin(t * 0.05 + s[0]) - camY,
                    z = s[3] + 0.5 - camZ;
                double turn = t * 0.03 + s[0] * 0.9, hx = Math.cos(turn), hz = Math.sin(turn);
                try {
                    IIcon crop = cropStage(st, w, s, t);
                    if (crop != null) {
                        if (sheet != 0) continue;
                        tes.setColorRGBA_I(0xFFFFFF, CityFrame.alpha255(0.85f * p));
                        CityRender.quad(tes, crop, x, y, z, hx * 0.2, hz * 0.2, 0.2);
                        continue;
                    }
                    if (st.getItem()
                        .getSpriteNumber() != sheet) continue;
                    CityRender.picture(tes, st, x, y, z, hx, hz, 0.2, 0.8f * p);
                } catch (RuntimeException e) {
                    // a modded item whose picture cannot be had here shows none
                }
            }
            tes.draw();
        }
    }

    /**
     * The icon of the crop a seed grows into, at the growth stage its hologram shows now (one stage a second, the
     * sockets out of step), or null when the sample is no seed of a crop. Asked of the client's world at the socket.
     */
    private static IIcon cropStage(ItemStack st, World w, int[] s, double t) {
        Item item = st.getItem();
        if (!(item instanceof IPlantable plantable) || w == null) return null;
        Block plant = plantable.getPlant(w, s[1], s[2] + 1, s[3]);
        // crops are drawn as crops (render type 6), each stage its own icon
        if (plant == null || plant.getRenderType() != 6) return null;
        int stage = (int) Math.floorMod((long) Math.floor(t / 20 + s[0] * 3), 8L);
        return plant.getIcon(0, stage);
    }

    /** Whether a sample is a bee (by its registry name, so no bee mod's classes are named here: {@link ModuleLook}). */
    private static boolean bee(ItemStack st) {
        Object name = Item.itemRegistry.getNameForObject(st.getItem());
        return name != null && ModuleLook.beeName(name.toString());
    }

    // ---- active: pollen, butterflies, ripples

    /** Pollen rising from the planters' sockets and the garden's soil, drifting and fading. */
    private static void pollen(CityFrame f, double t, float p) {
        Motes.begin();
        for (int i = 0; i < POLLEN_AT.length; i++) {
            double[] at = POLLEN_AT[i];
            double h = Motifs.hash(i * 19 + 4), fr = (t / 170.0 + h) % 1;
            double lx = at[0] + 0.4 * Math.sin(t * 0.02 + i) * fr + (h - 0.5) * 0.6,
                lz = at[2] + 0.4 * Math.cos(t * 0.017 + i * 1.3) * fr;
            double y = at[1] + 0.2 + fr * 4.2;
            Motes.add(
                f.x(lx, lz),
                f.y(y),
                f.z(lx, lz),
                0.04 + 0.02 * h,
                i % 3 == 0 ? WHITE : POLLEN_RGB,
                (float) Math.sin(fr * Math.PI) * 0.55f * p);
        }
        Motes.end();
    }

    /**
     * Butterflies drifting between the ground's planters, the air round the island and the upper planters (the island,
     * the gallery, the crown): each flies legs from waypoint to waypoint, low ring and high ring between the levels
     * so its path keeps clear of the decks, wobbling, its two wings flapping about its heading.
     */
    private static void butterflies(CityFrame f, double t, float p) {
        Shapes.begin(false);
        Tessellator tes = CityFrame.start();
        double[][] bodies = new double[BUTTERFLIES][];
        for (int b = 0; b < BUTTERFLIES; b++) {
            double leg = 190 + 40 * Motifs.hash(b * 7 + 3), clock = t + b * 61;
            long n = (long) Math.floor(clock / leg);
            double fr = clock / leg - n, s = fr * fr * (3 - 2 * fr);
            double[] a = waypoint(b, n), c = waypoint(b, n + 1);
            double x = a[0] + (c[0] - a[0]) * s + 0.35 * Math.sin(t * 0.07 + b),
                y = a[1] + (c[1] - a[1]) * s + 0.25 * Math.sin(t * 0.11 + b * 2),
                z = a[2] + (c[2] - a[2]) * s + 0.35 * Math.cos(t * 0.06 + b * 3);
            double hx = c[0] - a[0], hz = c[2] - a[2], hl = Math.sqrt(hx * hx + hz * hz);
            if (hl < 1e-3) {
                hx = 1;
                hz = 0;
            } else {
                hx /= hl;
                hz /= hl;
            }
            double flap = 0.25 + 1.05 * Math.abs(Math.sin(t * 0.9 + b * 1.7));
            int rgb = WINGS[b % WINGS.length];
            wing(tes, f, x, y, z, hx, hz, flap, 1, rgb, p);
            wing(tes, f, x, y, z, hx, hz, flap, -1, rgb, p);
            bodies[b] = new double[] { x, y, z };
        }
        tes.draw();
        Shapes.end();
        Motes.begin();
        for (double[] b : bodies) Motes.add(f.x(b[0], b[2]), f.y(b[1]), f.z(b[0], b[2]), 0.05, WHITE, 0.5f * p);
        Motes.end();
    }

    /** One wing: hinged along the body's heading, raised by {@code flap} on its side ({@code dir} 1 or -1). */
    private static void wing(Tessellator tes, CityFrame f, double x, double y, double z, double hx, double hz,
        double flap, int dir, int rgb, float p) {
        double sx = -hz * dir, sz = hx * dir, span = 0.17, chord = 0.07;
        double ox = sx * Math.cos(flap) * span, oy = Math.sin(flap) * span, oz = sz * Math.cos(flap) * span;
        tes.setColorRGBA_I(rgb, CityFrame.alpha255(0.85f * p));
        f.vertex(tes, x + hx * chord, y, z + hz * chord);
        f.vertex(tes, x - hx * chord, y, z - hz * chord);
        f.vertex(tes, x - hx * chord * 0.6 + ox, y + oy, z - hz * chord * 0.6 + oz);
        f.vertex(tes, x + hx * chord * 1.4 + ox, y + oy, z + hz * chord * 1.4 + oz);
    }

    /**
     * Butterfly {@code b}'s waypoint {@code n}: the legs cycle ground planter, low ring, high ring, upper planter, high
     * ring, low ring, each pick hashed from the butterfly and the cycle.
     */
    private static double[] waypoint(int b, long n) {
        long cycle = Math.floorDiv(n, 6L);
        int step = (int) Math.floorMod(n, 6L), seed = (int) (cycle * 31 + b * 97);
        switch (step) {
            case 0:
                return pick(GROUND_TOPS, seed);
            case 1:
            case 5:
                return pick(LOW_RING, seed + step);
            case 2:
            case 4:
                return pick(HIGH_RING, seed + step);
            default:
                return pick(UPPER_TOPS, seed);
        }
    }

    private static double[] pick(double[][] from, int seed) {
        return from[(int) (Motifs.hash(seed) * from.length) % from.length];
    }

    /**
     * Rings rippling across the pond: each spreads from the middle of a cell of water and fades, then starts
     * elsewhere; no wider than that cell's room ({@link ModuleLook#water}), so it never runs onto the rim or the
     * trunk's moat, and a little over the water's surface.
     */
    private static void ripples(CityFrame f, double t, float p) {
        if (WATER.length == 0) return;
        Shapes.begin(true);
        Tessellator tes = CityFrame.start();
        for (int k = 0; k < RIPPLES; k++) {
            double period = 70, clock = t + k * 13.7;
            long n = (long) Math.floor(clock / period);
            double fr = clock / period - n;
            int seed = (int) (n * 7 + k * 131);
            double[] at = WATER[(int) (Motifs.hash(seed) * WATER.length) % WATER.length];
            double cx = at[0], cz = at[1], rad = 0.05 + Math.max(0, at[2] - 0.09) * fr;
            float alpha = (float) (1 - fr) * 0.5f * p;
            if (alpha <= 0.01f) continue;
            tes.setColorRGBA_I(Shapes.mix(0x9AE8FF, WHITE, 0.3), CityFrame.alpha255(alpha));
            for (int i = 0; i < RIPPLE_SEGMENTS; i++) {
                double a0 = i * 2 * Math.PI / RIPPLE_SEGMENTS, a1 = (i + 1) * 2 * Math.PI / RIPPLE_SEGMENTS;
                double in = Math.max(0, rad - 0.035), out = rad + 0.035;
                f.vertex(tes, cx + Math.cos(a0) * in, POND_Y, cz + Math.sin(a0) * in);
                f.vertex(tes, cx + Math.cos(a1) * in, POND_Y, cz + Math.sin(a1) * in);
                f.vertex(tes, cx + Math.cos(a1) * out, POND_Y, cz + Math.sin(a1) * out);
                f.vertex(tes, cx + Math.cos(a0) * out, POND_Y, cz + Math.sin(a0) * out);
            }
        }
        tes.draw();
        Shapes.end();
    }

    private static void fail(int part, Throwable x) {
        if (OFF[part]) return;
        OFF[part] = true;
        FluxEcho.LOG.warn("The Eco Dome's {} failed and is off until the game restarts", NAMES[part], x);
    }
}
