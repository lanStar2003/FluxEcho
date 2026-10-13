package com.fluxecho.city.client;

import static com.fluxecho.client.FluxDraw.CYAN;
import static com.fluxecho.client.FluxDraw.WHITE;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.EntityList;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.fluxecho.FluxEcho;
import com.fluxecho.city.TileHuntingGround;
import com.fluxecho.client.Motes;
import com.fluxecho.logic.HuntingLook;
import com.fluxecho.logic.ModuleLook;
import com.fluxecho.render.Shapes;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The Echo Hunting Ground's own visuals (0.13.0, CITY.md §4.5 Visuals), on top of what every city module shows
 * ({@link CityRender}); where everything goes and how it moves is {@link HuntingLook}'s.
 * <ul>
 * <li>Formed: the velarium's ring segments have given way to a translucent canopy of hexagons closing over the bowl,
 * amber at its rim and cyan over the arena, a swell of light rolling in over it; the ring itself stays as lines of
 * light. Undocked it burns low.</li>
 * <li>Docked: every six seconds a line of light sweeps across the arena's holo grid, each sweep a quarter turn on from
 * the last, its lit tiles glowing behind it; near, every imprint console keeping a sample shows a small hologram over
 * its socket, a flat silhouette turned to the camera in the colours of the spawn egg of the mob the sample names (a
 * prey
 * imprint or a spawn egg), in neutral colours when it names none. Silhouettes, never entity models (CITY.md §4.5).</li>
 * <li>Powered: brighter; nothing more until the function (0.19.0 on) lets the imprinted mobs perform.</li>
 * </ul>
 * Budgets (CITY.md §1.3, quads a frame on top of {@link CityRender}'s, {@link HuntingLook#quads}): far, the canopy as
 * eight quads and its rim; mid, the canopy's large hexagons, its rim and the scan line; near, the small hexagons with
 * their edges, the ring's two octagons and their ties, the scan line with its glow and trail, and the holograms. With
 * the plinth line, the plate and the sockets' samples ({@link ModuleLook#baseQuads}) that keeps the ground under 2,000
 * near, 300 mid and 64 far ({@code HuntingLookTest}).
 * <p>
 * Shader-safe as {@link CityRender}: everything in its frame ({@link CityFrame}) relative to the camera, quads only, a
 * normal towards the viewer on every vertex, no depth writes; the canopy, the scan and the glows are added, the
 * silhouettes blended (a dark egg colour cannot be added). Animation by the world time alone, nothing counted, spawned
 * or sent, so a frame drawn again behind a light gate is the same; the spawn eggs' colours are looked up once a mob.
 * Each part fails on its own.
 */
@SideOnly(Side.CLIENT)
final class HuntingGroundRender {

    private static final double AXIS = HuntingLook.AXIS;
    /** The scan line's core and glow half-widths, and the hexagons' edges' and the ring's widths. */
    private static final double SCAN_CORE = 0.035, SCAN_GLOW = 0.3, EDGE_W = 0.03, RING_W = 0.07;

    private static final int CANOPY = 0, SCAN = 1, HOLOGRAMS = 2;
    private static final String[] NAMES = { "velarium canopy", "arena scan", "imprint holograms" };
    private static final boolean[] OFF = new boolean[NAMES.length];

    /** Each mob's spawn egg colours by the name it is registered under, {main, spots}, looked up once; or none. */
    private static final Map<String, int[]> EGGS = new ConcurrentHashMap<>();
    private static final int[] NO_EGG = {};

    private HuntingGroundRender() {}

    /**
     * Draws the Echo Hunting Ground's own visuals; {@code p0} is its distance fade times how far it has formed,
     * {@code tier} how much to draw ({@link ModuleLook#tier}).
     */
    static void draw(TileHuntingGround g, CityFrame f, double t, float p0, int tier) {
        if (p0 <= 0.01f) return;
        boolean docked = CityRender.docked(g), on = CityRender.on(g), nearby = tier == ModuleLook.NEAR;
        float life = p0 * (on ? 1f : docked ? 0.6f : 0.3f);
        int accent = g.moduleColor();
        if (!OFF[CANOPY]) try {
            canopy(f, t, life, tier, accent);
        } catch (Throwable x) {
            CityRender.recover();
            fail(CANOPY, x);
        } finally {
            CityRender.close();
        }
        if (!docked) return;
        if (tier != ModuleLook.FAR && !OFF[SCAN]) try {
            scan(f, t, life, nearby, accent);
        } catch (Throwable x) {
            CityRender.recover();
            fail(SCAN, x);
        } finally {
            CityRender.close();
        }
        if (nearby && !OFF[HOLOGRAMS]) try {
            holograms(g, f, t, life, accent);
        } catch (Throwable x) {
            CityRender.recover();
            fail(HOLOGRAMS, x);
        } finally {
            CityRender.close();
        }
    }

    // ---- the canopy

    /**
     * The velarium's canopy: near its small hexagons, each two quads shading from amber at the rim to cyan over the
     * arena, with their edges and the ring's two octagons tied at their corners; mid its large hexagons and its rim;
     * far an octagon of eight quads and its rim.
     */
    private static void canopy(CityFrame f, double t, float life, int tier, int accent) {
        Shapes.begin(true);
        Tessellator tes = CityFrame.start();
        if (tier == ModuleLook.FAR) {
            double[][] rim = HuntingLook.fan();
            double top = HuntingLook.EDGE_Y + HuntingLook.RISE;
            for (int i = 0; i < rim.length; i++) {
                double[] a = rim[i], b = rim[(i + 1) % rim.length];
                double mx = (a[0] + b[0]) / 2, mz = (a[1] + b[1]) / 2;
                tes.setColorRGBA_I(CYAN, CityFrame.alpha255(0.12f * life));
                f.vertex(tes, AXIS, top, AXIS);
                tes.setColorRGBA_I(accent, CityFrame.alpha255(0.12f * life));
                f.vertex(tes, a[0], HuntingLook.EDGE_Y, a[1]);
                f.vertex(tes, mx, HuntingLook.canopyY(mx, mz), mz);
                f.vertex(tes, b[0], HuntingLook.EDGE_Y, b[1]);
            }
        } else for (double[][] h : HuntingLook.hexes(tier)) {
            double[] mid = h[0];
            float a = 0.13f * life * (float) HuntingLook.hexLight(mid, t);
            tes.setColorRGBA_I(Shapes.mix(CYAN, accent, mid[2]), CityFrame.alpha255(a));
            for (int k : new int[] { 1, 2, 3, 4, 4, 5, 6, 1 }) f.vertex(tes, h[k][0], h[k][1], h[k][2]);
        }
        if (tier == ModuleLook.NEAR) {
            tes.setColorRGBA_I(Shapes.mix(CYAN, WHITE, 0.25), CityFrame.alpha255(0.3f * life));
            for (double[] e : HuntingLook.hexEdges()) f.line(tes, e[0], e[1], e[2], e[3], e[4], e[5], EDGE_W);
            tes.setColorRGBA_I(accent, CityFrame.alpha255(0.55f * life));
            double[][] in = HuntingLook.ring(false), out = HuntingLook.ring(true);
            octagon(tes, f, in);
            octagon(tes, f, out);
            for (int i = 0; i < HuntingLook.TIES; i++)
                f.line(tes, in[i][0], HuntingLook.EDGE_Y, in[i][1], out[i][0], HuntingLook.EDGE_Y, out[i][1], RING_W);
        } else {
            tes.setColorRGBA_I(accent, CityFrame.alpha255(0.5f * life));
            octagon(tes, f, HuntingLook.fan());
        }
        tes.draw();
        Shapes.end();
    }

    /** An octagon at the canopy's rim height as lines into the open batch. */
    private static void octagon(Tessellator tes, CityFrame f, double[][] o) {
        double y = HuntingLook.EDGE_Y;
        for (int i = 0; i < o.length; i++) {
            double[] a = o[i], b = o[(i + 1) % o.length];
            f.line(tes, a[0], y, a[1], b[0], y, b[1], RING_W);
        }
    }

    // ---- the scan

    /**
     * The scan line lying on the arena's grid, cut where the emitter and its pedestals stand: a bright core and, near,
     * a soft glow either side of it, and the grid's lit tiles glowing behind it.
     */
    private static void scan(CityFrame f, double t, float life, boolean nearby, int accent) {
        List<double[]> pieces = HuntingLook.scanPieces(t);
        if (pieces.isEmpty() && !nearby) return;
        int light = Shapes.mix(CYAN, WHITE, 0.4);
        Shapes.begin(true);
        Tessellator tes = CityFrame.start();
        double y = HuntingLook.SCAN_Y;
        for (double[] p : pieces) {
            tes.setColorRGBA_I(light, CityFrame.alpha255(0.85f * life));
            flat(tes, f, p[0], p[1], p[2], p[3], y + 0.002, SCAN_CORE);
            if (!nearby) continue;
            tes.setColorRGBA_I(CYAN, CityFrame.alpha255(0.2f * life));
            flat(tes, f, p[0], p[1], p[2], p[3], y, SCAN_GLOW);
        }
        if (nearby) {
            int glow = Shapes.mix(CYAN, accent, 0.35);
            for (int[] c : HuntingLook.lit()) {
                double k = HuntingLook.trail(c[0], c[1], t);
                if (k <= 0) continue;
                tes.setColorRGBA_I(glow, CityFrame.alpha255(0.45f * life * (float) k));
                f.vertex(tes, c[0], y - 0.004, c[1]);
                f.vertex(tes, c[0] + 1, y - 0.004, c[1]);
                f.vertex(tes, c[0] + 1, y - 0.004, c[1] + 1);
                f.vertex(tes, c[0], y - 0.004, c[1] + 1);
            }
        }
        tes.draw();
        Shapes.end();
    }

    /** A level band between two points of the frame at a height: a line lying on the floor. */
    private static void flat(Tessellator tes, CityFrame f, double x0, double z0, double x1, double z1, double y,
        double half) {
        double dx = x1 - x0, dz = z1 - z0, l = Math.sqrt(dx * dx + dz * dz);
        if (l < 1e-9) return;
        double sx = -dz / l * half, sz = dx / l * half;
        f.vertex(tes, x0 - sx, y, z0 - sz);
        f.vertex(tes, x0 + sx, y, z0 + sz);
        f.vertex(tes, x1 + sx, y, z1 + sz);
        f.vertex(tes, x1 - sx, y, z1 - sz);
    }

    // ---- the holograms

    /**
     * Over every imprint console keeping a sample, a hologram: its silhouette turned to the camera (about the upright,
     * so it stands as a billboard) in the egg's colours, blended; then a stripe of light running up it and the
     * projector's glow on the socket, added. An empty console shows its projector's glow, dim.
     */
    private static void holograms(TileHuntingGround g, CityFrame f, double t, float life, int accent) {
        int n = HuntingLook.consoles()
            .size();
        double[][] foot = new double[n][];
        int[] kind = new int[n];
        int[][] colours = new int[n][];
        for (int i = 0; i < n; i++) {
            double[] p = HuntingLook.silhouetteFoot(i, t);
            foot[i] = new double[] { f.x(p[0], p[2]), f.y(p[1]), f.z(p[0], p[2]) };
            ItemStack s = g.sample(HuntingLook.consoleSlot(i));
            if (s == null || s.getItem() == null) continue;
            String mob = mobOf(s);
            kind[i] = HuntingLook.kindOf(mob);
            int[] egg = eggColours(mob);
            colours[i] = egg.length == 2 ? egg : new int[] { HuntingLook.NEUTRAL, HuntingLook.NEUTRAL_SPOTS };
        }
        double scale = HuntingLook.SIL_SCALE;
        Shapes.begin(false);
        Tessellator tes = CityFrame.start();
        for (int i = 0; i < n; i++) {
            if (colours[i] == null) continue;
            double[] p = foot[i];
            double hl = Math.sqrt(p[0] * p[0] + p[2] * p[2]);
            if (hl < 1e-6) continue;
            // the camera's right, level: the silhouette faces the camera about the upright
            double rx = -p[2] / hl * scale, rz = p[0] / hl * scale;
            for (double[] r : HuntingLook.silhouette(kind[i])) {
                tes.setColorRGBA_I(colours[i][(int) r[4]], CityFrame.alpha255(0.8f * life));
                tes.addVertex(p[0] + rx * r[0], p[1] + r[1] * scale, p[2] + rz * r[0]);
                tes.addVertex(p[0] + rx * r[2], p[1] + r[1] * scale, p[2] + rz * r[2]);
                tes.addVertex(p[0] + rx * r[2], p[1] + r[3] * scale, p[2] + rz * r[2]);
                tes.addVertex(p[0] + rx * r[0], p[1] + r[3] * scale, p[2] + rz * r[0]);
            }
        }
        tes.draw();
        Shapes.end();
        Shapes.begin(true);
        tes = CityFrame.start();
        tes.setColorRGBA_I(Shapes.mix(CYAN, WHITE, 0.3), CityFrame.alpha255(0.5f * life));
        for (int i = 0; i < n; i++) {
            if (colours[i] == null) continue;
            double[] p = foot[i];
            double hl = Math.sqrt(p[0] * p[0] + p[2] * p[2]);
            if (hl < 1e-6) continue;
            double rx = -p[2] / hl * scale * 0.5, rz = p[0] / hl * scale * 0.5;
            double v = p[1] + HuntingLook.stripe(i, t) * HuntingLook.height(kind[i]) * scale;
            tes.addVertex(p[0] - rx, v - 0.012, p[2] - rz);
            tes.addVertex(p[0] + rx, v - 0.012, p[2] + rz);
            tes.addVertex(p[0] + rx, v + 0.012, p[2] + rz);
            tes.addVertex(p[0] - rx, v + 0.012, p[2] - rz);
        }
        tes.draw();
        Shapes.end();
        Motes.begin();
        for (int i = 0; i < n; i++) {
            double[] p = foot[i];
            Motes.add(p[0], p[1], p[2], 0.35, accent, (colours[i] == null ? 0.12f : 0.35f) * life);
        }
        Motes.end();
    }

    /** The mob a sample names: a spawn egg's, by its damage; a prey imprint's, by its tag; else none. */
    private static String mobOf(ItemStack s) {
        Item item = s.getItem();
        if (item == Items.spawn_egg) return EntityList.getStringFromID(s.getItemDamage());
        Object name = Item.itemRegistry.getNameForObject(item);
        NBTTagCompound tag = s.getTagCompound();
        String mob = tag != null && tag.hasKey(HuntingLook.IMPRINT_TAG, 8) ? tag.getString(HuntingLook.IMPRINT_TAG)
            : null;
        return HuntingLook.imprintMob(name == null ? null : name.toString(), mob);
    }

    /**
     * The colours of the spawn egg of the mob registered under {@code mob}, {main, spots} (the egg whose entity is that
     * mob's class); none for no mob, or a mob without an egg (most mods' mobs).
     */
    private static int[] eggColours(String mob) {
        if (mob == null) return NO_EGG;
        return EGGS.computeIfAbsent(mob, HuntingGroundRender::lookUpEgg);
    }

    private static int[] lookUpEgg(String mob) {
        Class<?> c = EntityList.stringToClassMapping.get(mob);
        if (c == null) return NO_EGG;
        for (Map.Entry<Integer, EntityList.EntityEggInfo> e : EntityList.entityEggs.entrySet()) {
            if (EntityList.IDtoClassMapping.get(e.getKey()) == c)
                return new int[] { e.getValue().primaryColor, e.getValue().secondaryColor };
        }
        return NO_EGG;
    }

    private static void fail(int part, Throwable x) {
        if (OFF[part]) return;
        OFF[part] = true;
        FluxEcho.LOG.warn("The Echo Hunting Ground's {} failed and is off until the game restarts", NAMES[part], x);
    }
}
