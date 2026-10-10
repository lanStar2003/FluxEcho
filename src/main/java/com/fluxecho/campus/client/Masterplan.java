package com.fluxecho.campus.client;

import static com.fluxecho.client.FluxDraw.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.ForgeDirection;

import org.lwjgl.opengl.GL11;

import com.fluxecho.Config;
import com.fluxecho.FluxEcho;
import com.fluxecho.campus.Campus;
import com.fluxecho.campus.ModuleSpecs;
import com.fluxecho.client.FarDraw;
import com.fluxecho.client.FluxDraw;
import com.fluxecho.client.ShaderCompat;
import com.fluxecho.core.EchoText;
import com.fluxecho.logic.ArchiveShape;
import com.fluxecho.logic.Blueprint;
import com.fluxecho.logic.BuildPlan;
import com.fluxecho.logic.BuildState;
import com.fluxecho.logic.CampusPlan;
import com.fluxecho.logic.LiftRule;
import com.fluxecho.logic.NexusShape;
import com.fluxecho.logic.RingSlots;
import com.fluxecho.nexus.ClientTiles;
import com.fluxecho.nexus.ItemBlockNexusCore;
import com.fluxecho.nexus.TileModule;
import com.fluxecho.nexus.TileMultiblock;
import com.fluxecho.nexus.TileNexus;
import com.fluxecho.nexus.client.NexusRender;
import com.fluxecho.render.Shapes;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The campus masterplan and the core's placement preview (0.10.0 「营造」), shown while the player holds something
 * that places or links a multiblock (the nexus core, a module's core, a flux terminal: {@link NexusRender#holdsHint}).
 * <p>
 * The masterplan: round every active campus within 256 blocks, faint outlines lying on its floor of what the campus
 * will hold, so a player sees where things go before anything is built there: the graded disc's edge, the forum and
 * the promenade ring, the gate (site 0), each hall site's envelope (the Echo Archive's footprint, its door marked on
 * the side facing the nexus) and the reserved diagonal sites (dashed), with each site's number on a sign turned to the
 * camera. A site with a module on it is brighter, in the module's colour; the site of the current job is cyan.
 * <p>
 * The preview, while holding the nexus core and aiming at a block (not sneaking): what a lifted placement there would
 * raise, worked out with the item's own rules ({@link ItemBlockNexusCore#ground}, {@link ItemBlockNexusCore#lift},
 * {@link ItemBlockNexusCore#facingFor}): the dais, the forum, the promenade ring, the gate and the graded disc's edge
 * round the nexus centre the core would get, its front the way the core would face. It turns red, with the reason
 * over the core, when the client can already tell the server will refuse: the nexus would reach past the top of the
 * world, no sky over the core ({@code Config.buildRequireSky}), the dais on a module, or another nexus it knows of
 * closer than {@code Config.buildMinSpacing}. Nothing when the campus is off.
 * <p>
 * Shader-safe like {@link Shapes} (drawn after the world, skipped in the shadow pass, relative to the camera, quads
 * with a normal towards the viewer, additive, no depth writes); the outlines are drawn twice, once faintly through
 * the terrain and once with the depth test, so an outline running into a hillside still reads. Each outline is a
 * band along the floor turned to the camera and at least about a pixel wide wherever it is: a band lying flat would
 * thin to nothing seen across the campus from standing height. The masterplan, the
 * signs and the preview fail on their own: an error is logged once and turns that part off for the session.
 */
@SideOnly(Side.CLIENT)
public final class Masterplan {

    /** How far (blocks) the masterplan of a campus is drawn, and its signs. */
    private static final double RANGE = 256, SIGN_RANGE = 96;
    private static final int DANGER = 0xFF4A4A, DISC = 0x7FB8CC;
    /** The share of an outline's alpha drawn through the terrain. */
    private static final float THROUGH = 0.35f;
    /** The least width of an outline per block from the camera (about a pixel and a half), and its longest piece. */
    private static final double PIXEL = 0.0022, PIECE = 8;

    // the outlines in the campus frame: (A, R) from the middle of the centre cell, cell edges at half blocks
    private static final double[][] DISC_EDGE = octagon(CampusPlan.GRADE_R, CampusPlan.GRADE_K);
    private static final double[][] FORUM = octagon(CampusPlan.FORUM_R, CampusPlan.FORUM_K);
    private static final double[][] RING = octagon(CampusPlan.RING_R, CampusPlan.RING_K);
    private static final double[][] DAIS = octagon(NexusShape.RADIUS, 7);
    /** The gate strip ({@link CampusPlan#inGate}) from the promenade ring's outer edge, open at the ring. */
    private static final double[][] GATE = gate(CampusPlan.RING_R + 0.5);
    /** The chamfer of a diagonal site's reserved envelope: its corners cut where {@code |du| + |dv|} passes this. */
    private static final int DIAGONAL_CHAMFER = 15;

    /** The parts that fail on their own. */
    private static final int PLAN = 0, SIGNS = 1, PREVIEW = 2;
    private static final String[] NAMES = { "masterplan", "masterplan signs", "placement preview" };
    private static final boolean[] OFF = new boolean[NAMES.length];

    private float nx, ny = 1, nz;

    private Masterplan() {}

    /** Registers the render handler (the client proxy calls it through CampusClient). */
    public static void register() {
        Masterplan h = new Masterplan();
        MinecraftForge.EVENT_BUS.register(h);
        FarDraw.add(h::onRenderLast);
    }

    @SubscribeEvent
    public void onRenderLast(RenderWorldLastEvent e) {
        if (ShaderCompat.shadowPass()) return;
        Minecraft mc = Minecraft.getMinecraft();
        World w = mc.theWorld;
        EntityPlayer p = mc.thePlayer;
        if (w == null || p == null) return;
        ItemStack held = p.getHeldItem();
        boolean core = held != null && held.getItem() instanceof ItemBlockNexusCore;
        boolean hint;
        try {
            hint = core || NexusRender.holdsHint(p);
        } catch (Throwable t) {
            fail(PLAN, t);
            return;
        }
        if (!hint) return;
        double t = w.getTotalWorldTime() + e.partialTicks;
        double yaw = Math.toRadians(RenderManager.instance.playerViewY),
            pitch = Math.toRadians(RenderManager.instance.playerViewX);
        nx = (float) (Math.sin(yaw) * Math.cos(pitch));
        ny = (float) Math.sin(pitch);
        nz = (float) (-Math.cos(yaw) * Math.cos(pitch));
        List<Sign> signs = new ArrayList<>();
        if (!OFF[PLAN]) {
            try {
                plans(w, t, signs);
            } catch (Throwable x) {
                fail(PLAN, x);
            }
        }
        if (core && !OFF[PREVIEW] && Config.nexusEnabled && Config.campusEnabled) {
            try {
                preview(mc, w, p, t, signs);
            } catch (Throwable x) {
                fail(PREVIEW, x);
            }
        }
        if (!signs.isEmpty() && !OFF[SIGNS]) signs(signs);
    }

    // ---- the masterplan

    /** A sign to draw: where (camera-relative), its lines, its colour, the size of its title. */
    private static final class Sign {

        final double x, y, z;
        final String title, caption;
        final int rgb;
        final float scale;

        Sign(double x, double y, double z, String title, String caption, int rgb, float scale) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.title = title;
            this.caption = caption;
            this.rgb = rgb;
            this.scale = scale;
        }
    }

    /** The masterplan of every active campus in range. */
    private void plans(World w, double t, List<Sign> signs) {
        List<TileMultiblock> tiles = ClientTiles.all();
        boolean begun = false;
        Tessellator tes = Tessellator.instance;
        boolean drawing = false;
        try {
            for (TileMultiblock m : tiles) {
                if (!(m instanceof TileNexus n) || m.getWorldObj() != w || m.isInvalid()) continue;
                Campus.View v = n.clientCampus;
                if (v == null || !v.active) continue;
                int[] c = n.centre();
                double ox = c[0] + 0.5 - RenderManager.renderPosX, oy = c[1] + 1.05 - RenderManager.renderPosY,
                    oz = c[2] + 0.5 - RenderManager.renderPosZ;
                if (ox * ox + oy * oy + oz * oz > RANGE * RANGE) continue;
                if (!begun) {
                    Shapes.begin(true);
                    begun = true;
                }
                Frame f = new Frame(ox, oy, oz, n.front().offsetX, n.front().offsetZ);
                Site[] sites = sites(n, v, tiles, c, f.fx, f.fz);
                float breath = 0.85f + 0.15f * (float) Math.sin(t * 0.06);
                for (int pass = 0; pass < 2; pass++) {
                    boolean through = pass == 0;
                    if (through) GL11.glDisable(GL11.GL_DEPTH_TEST);
                    float k = through ? THROUGH : 1f;
                    tes.startDrawingQuads();
                    drawing = true;
                    tes.setNormal(nx, ny, nz);
                    outline(tes, f, DISC_EDGE, 0, 0, true, 0.2, DISC, 0.16f * k, false);
                    outline(tes, f, FORUM, 0, 0, true, 0.12, CYAN, 0.18f * k, false);
                    outline(tes, f, RING, 0, 0, true, 0.12, CYAN, 0.18f * k, false);
                    outline(tes, f, GATE, 0, 0, false, 0.12, sites[0].rgb, sites[0].alpha * k, false);
                    for (int s = 1; s < RingSlots.SLOTS; s++) {
                        Site st = sites[s];
                        float a = st.alpha * k * (st.job ? breath : 1f);
                        if (s % 2 == 0) {
                            outline(tes, f, hall(s), 0, 0, true, 0.12, st.rgb, a, false);
                            outline(tes, f, door(s), 0, 0, false, 0.22, st.rgb, Math.min(0.3f, a * 1.4f), false);
                        } else {
                            int[] dc = CampusPlan.diagonalCentre(s);
                            outline(tes, f, diagonal(), dc[0], dc[1], true, 0.12, st.rgb, a, !st.occupied);
                        }
                    }
                    drawing = false;
                    tes.draw();
                    if (through) GL11.glEnable(GL11.GL_DEPTH_TEST);
                }
                for (int s = 0; s < RingSlots.SLOTS; s++) {
                    double[] at = signAt(s);
                    double[] r = f.at(at[0], at[1]);
                    signs.add(new Sign(r[0], oy + at[2], r[1], sites[s].title, sites[s].caption, sites[s].rgb, 2f));
                }
            }
        } catch (Throwable x) {
            if (drawing) abandon(tes);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            throw x;
        } finally {
            if (begun) Shapes.end();
        }
    }

    /** What the masterplan shows of one site: its colour and alpha, its sign. */
    private static final class Site {

        boolean occupied, job;
        int rgb = DIM;
        float alpha = 0.2f;
        String title = "", caption = "";
    }

    /**
     * The state of each site of the campus: whether a module stands on it (docked to the nexus under the site's
     * number, or a loaded module standing in its envelope), whether the current job builds there, and its sign.
     */
    private static Site[] sites(TileNexus n, Campus.View v, List<TileMultiblock> tiles, int[] c, int fx, int fz) {
        Site[] out = new Site[RingSlots.SLOTS];
        int jobSite = -1;
        String jobModule = null;
        if (v.hasJob() && !BuildState.ended(v.stateEnum())) {
            jobModule = ModuleSpecs.moduleOf(v.planKey);
            jobSite = jobModule == null ? -1 : v.site;
        }
        String[] names = new String[RingSlots.SLOTS];
        boolean[] standing = new boolean[RingSlots.SLOTS];
        for (TileMultiblock m : tiles) {
            if (!(m instanceof TileModule mod) || m.isInvalid() || m.getWorldObj() != n.getWorldObj()) continue;
            int[] mc = mod.centre();
            int[] l = CampusPlan.toLocal(mc[0] - c[0], mc[2] - c[2], fx, fz);
            if (Math.abs(mc[1] - c[1]) > 8) continue;
            int s = siteOf(l[0], l[1]);
            if (s < 0) {
                if (!mod.clientDocked || mod.clientSlot < 0 || !Arrays.equals(mod.clientNexus, c)) continue;
                s = mod.clientSlot;
            }
            if (s >= RingSlots.SLOTS) continue;
            standing[s] = true;
            if (names[s] == null) names[s] = mod.moduleKey();
        }
        for (int s = 0; s < RingSlots.SLOTS; s++) {
            Site st = new Site();
            boolean docked = (n.clientDockedMask & 1 << s) != 0;
            st.occupied = s != 0 && (docked || standing[s]);
            st.job = s == jobSite;
            st.title = EchoText.t("build.gui.site", s);
            if (s == 0) {
                st.rgb = CYAN;
                st.alpha = 0.22f;
                st.caption = EchoText.t("build.masterplan.gate");
            } else if (st.job) {
                st.rgb = CYAN;
                st.alpha = 0.3f;
                st.caption = EchoText.t("build.masterplan.building", EchoText.t("module." + jobModule));
            } else if (st.occupied) {
                int col = docked ? n.clientModuleColor[s] : 0;
                st.rgb = col == 0 ? WHITE : col;
                st.alpha = 0.28f;
                st.caption = names[s] != null ? EchoText.t("module." + names[s]) : EchoText.t("build.masterplan.taken");
            } else {
                st.rgb = DIM;
                st.alpha = s % 2 == 0 ? 0.2f : 0.15f;
                st.caption = EchoText.t(s % 2 == 0 ? "build.masterplan.hall" : "build.masterplan.reserved");
            }
            out[s] = st;
        }
        return out;
    }

    /** The site whose envelope holds the campus cell (a, r): a hall site's Archive footprint or a diagonal's. */
    private static int siteOf(int a, int r) {
        for (int s : CampusPlan.HALL_SITES) {
            if (CampusPlan.inHall(s, ArchiveShape.WIDTH, ArchiveShape.DEPTH, a, r)) return s;
        }
        for (int s = 1; s < RingSlots.SLOTS; s += 2) {
            int[] dc = CampusPlan.diagonalCentre(s);
            int du = Math.abs(a - dc[0]), dv = Math.abs(r - dc[1]), half = CampusPlan.DIAGONAL_SIZE / 2;
            if (du <= half && dv <= half && du + dv <= DIAGONAL_CHAMFER) return s;
        }
        return -1;
    }

    /** Where a site's sign stands: {A, R, height over the floor}. */
    private static double[] signAt(int site) {
        if (site == 0) return new double[] { 31, 0, 4.2 };
        int[] ax = CampusPlan.siteAxis(site);
        if (site % 2 == 0) {
            // in front of the hall's door, at eye height for someone on the forecourt
            double d = CampusPlan.HALL_FRONT - 3.5;
            return new double[] { ax[0] * d, ax[1] * d, 3.2 };
        }
        int[] dc = CampusPlan.diagonalCentre(site);
        return new double[] { dc[0], dc[1], 3.0 };
    }

    /** A hall site's envelope: the Archive's footprint, front row at {@link CampusPlan#HALL_FRONT}. */
    private static double[][] hall(int site) {
        double t0 = CampusPlan.HALL_FRONT - 0.5, t1 = CampusPlan.HALL_FRONT + ArchiveShape.DEPTH - 0.5;
        double s0 = -(ArchiveShape.WIDTH / 2) - 0.5, s1 = (ArchiveShape.WIDTH - 1) / 2 + 0.5;
        return siteRect(site, t0, t1, s0, s1);
    }

    /** The door's side of a hall envelope: its middle seven cells, as wide as the lit threshold. */
    private static double[][] door(int site) {
        int[] ax = CampusPlan.siteAxis(site);
        double t = CampusPlan.HALL_FRONT - 0.5;
        return new double[][] { local(ax, t, -3.5), local(ax, t, 3.5) };
    }

    private static double[][] siteRect(int site, double t0, double t1, double s0, double s1) {
        int[] ax = CampusPlan.siteAxis(site);
        return new double[][] { local(ax, t0, s0), local(ax, t1, s0), local(ax, t1, s1), local(ax, t0, s1) };
    }

    /** The campus point {A, R} that is {@code t} along a site's axis and {@code s} across it (as inHall counts). */
    private static double[] local(int[] ax, double t, double s) {
        return new double[] { t * ax[0] - s * ax[1], t * ax[1] + s * ax[0] };
    }

    /** A diagonal site's reserved envelope round its centre: a chamfered square. */
    private static double[][] diagonal() {
        int half = CampusPlan.DIAGONAL_SIZE / 2;
        return octagon(half, DIAGONAL_CHAMFER);
    }

    /**
     * The outline of the cells of {@code oct(rmax, k)} in the campus frame: {@code |A|, |R| <= rmax + 1/2} and
     * {@code |A| + |R| <= k + 1}, the line through the outer corners of the octagon's stepped edge.
     */
    private static double[][] octagon(int rmax, int k) {
        double h = rmax + 0.5, s = k + 1, c = Math.min(h, s - h);
        return new double[][] { { h, -c }, { h, c }, { c, h }, { -c, h }, { -h, c }, { -h, -c }, { -c, -h },
            { c, -h } };
    }

    /** The gate strip's outline from {@code from} out, open on the inner side. */
    private static double[][] gate(double from) {
        return new double[][] { { from, -3.5 }, { 24.5, -3.5 }, { 24.5, -5.5 }, { 29.5, -5.5 }, { 29.5, -3.5 },
            { 33.5, -3.5 }, { 33.5, -5.5 }, { 36.5, -5.5 }, { 36.5, 5.5 }, { 33.5, 5.5 }, { 33.5, 3.5 }, { 29.5, 3.5 },
            { 29.5, 5.5 }, { 24.5, 5.5 }, { 24.5, 3.5 }, { from, 3.5 } };
    }

    // ---- the placement preview

    /** The placement preview of the core in hand, when it would be a lifted placement. */
    private void preview(Minecraft mc, World w, EntityPlayer p, double t, List<Sign> signs) {
        MovingObjectPosition hit = mc.objectMouseOver;
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK || p.isSneaking()) return;
        int[] g = ItemBlockNexusCore.ground(w, hit.blockX, hit.blockY, hit.blockZ, hit.sideHit);
        if (g == null || ItemBlockNexusCore.lift(w, p, g) == 0) return;
        int x = g[0], cy = g[1] + LiftRule.LIFT, z = g[2];
        ForgeDirection fd = ForgeDirection.getOrientation(ItemBlockNexusCore.facingFor(p));
        Blueprint b = NexusShape.PHASE_1;
        int[] c = b.world(b.ctrlA, b.height() - 1, b.ctrlC + b.centreBack(), x, cy, z, fd.offsetX, fd.offsetZ);
        String why = refusal(w, b, c, x, cy, z, fd);
        int rgb = why == null ? CYAN : DANGER;
        float pulse = 0.8f + 0.2f * (float) Math.sin(t * 0.12);
        double ox = c[0] + 0.5 - RenderManager.renderPosX, oy = c[1] + 1.05 - RenderManager.renderPosY,
            oz = c[2] + 0.5 - RenderManager.renderPosZ;
        Frame f = new Frame(ox, oy, oz, fd.offsetX, fd.offsetZ);
        Tessellator tes = Tessellator.instance;
        boolean drawing = false;
        try {
            Shapes.begin(true);
            for (int pass = 0; pass < 2; pass++) {
                boolean through = pass == 0;
                if (through) GL11.glDisable(GL11.GL_DEPTH_TEST);
                float k = (through ? THROUGH : 1f) * pulse;
                tes.startDrawingQuads();
                drawing = true;
                tes.setNormal(nx, ny, nz);
                outline(tes, f, DAIS, 0, 0, true, 0.14, rgb, 0.6f * k, false);
                outline(tes, f, FORUM, 0, 0, true, 0.12, rgb, 0.42f * k, false);
                outline(tes, f, RING, 0, 0, true, 0.12, rgb, 0.36f * k, false);
                outline(tes, f, GATE, 0, 0, false, 0.12, rgb, 0.45f * k, false);
                outline(tes, f, DISC_EDGE, 0, 0, true, 0.2, rgb, 0.3f * k, false);
                // a chevron at the dais's front: the way the nexus will face
                outline(
                    tes,
                    f,
                    new double[][] { { 6.5, -2 }, { 8, 0 }, { 6.5, 2 } },
                    0,
                    0,
                    false,
                    0.14,
                    rgb,
                    0.6f * k,
                    false);
                drawing = false;
                tes.draw();
                if (through) GL11.glEnable(GL11.GL_DEPTH_TEST);
            }
            // where the core goes: a thin column of light from the floor to it
            double cx = x + 0.5 - RenderManager.renderPosX, cz = z + 0.5 - RenderManager.renderPosZ;
            Shapes.beam(cx, oy - 0.05, cz, cy + 1 - RenderManager.renderPosY, 0.3, rgb, 0.6f * pulse, 0.1f);
        } catch (Throwable x2) {
            if (drawing) abandon(tes);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            throw x2;
        } finally {
            Shapes.end();
        }
        if (why != null) signs.add(
            new Sign(
                x + 0.5 - RenderManager.renderPosX,
                cy + 1.8 - RenderManager.renderPosY,
                z + 0.5 - RenderManager.renderPosZ,
                why,
                "",
                DANGER,
                1f));
    }

    /**
     * Why the server would refuse a lifted core at (x, cy, z) whose nexus centre is {@code c}, as far as the client
     * can tell (the same checks in the same order as the item's), translated; null when it sees nothing wrong.
     */
    private static String refusal(World w, Blueprint b, int[] c, int x, int cy, int z, ForgeDirection f) {
        if (c[1] + ItemBlockNexusCore.HEADROOM > 255) return EchoText.t("build.too_high");
        if (Config.buildRequireSky && !w.canBlockSeeTheSky(x, cy, z)) return EchoText.t("build.no_sky");
        int[] p0 = b.world(0, 0, 0, x, cy, z, f.offsetX, f.offsetZ);
        int[] p1 = b.world(b.width() - 1, b.height() - 1, b.depth() - 1, x, cy, z, f.offsetX, f.offsetZ);
        int[] box = { Math.min(p0[0], p1[0]), Math.min(p0[1], p1[1]), Math.min(p0[2], p1[2]), Math.max(p0[0], p1[0]),
            Math.max(p0[1], p1[1]), Math.max(p0[2], p1[2]) };
        List<TileMultiblock> tiles = ClientTiles.all();
        for (TileMultiblock m : tiles) {
            if (m instanceof TileModule mod && !m.isInvalid() && m.getWorldObj() == w && overlaps(box, moduleBox(mod)))
                return EchoText.t("build.on_module");
        }
        int spacing = Config.buildMinSpacing;
        if (spacing <= 0) return null;
        for (TileMultiblock m : tiles) {
            if (!(m instanceof TileNexus n) || m.isInvalid() || m.getWorldObj() != w) continue;
            int[] o = n.centre();
            if (Math.max(Math.abs(o[0] - c[0]), Math.abs(o[2] - c[2])) < spacing)
                return EchoText.t("build.too_close", o[0] + ", " + o[1] + ", " + o[2]);
        }
        return null;
    }

    /** A module's block box: its checked range, or its foundation round its centre when it has none yet. */
    private static int[] moduleBox(TileModule m) {
        int[] mb = m.bounds();
        if (mb != null) return mb;
        int[] mc = m.centre();
        int[] k = BuildPlan.moduleKeepOut(mc);
        return new int[] { Math.min(k[0], k[2]), mc[1] - 1, Math.min(k[1], k[3]), Math.max(k[0], k[2]), mc[1] + 16,
            Math.max(k[1], k[3]) };
    }

    private static boolean overlaps(int[] a, int[] b) {
        return a[0] <= b[3] && b[0] <= a[3] && a[1] <= b[4] && b[1] <= a[4] && a[2] <= b[5] && b[2] <= a[5];
    }

    // ---- drawing

    /** A campus frame placed in the world: the camera-relative middle of the centre cell's top and the front. */
    private static final class Frame {

        final double ox, oy, oz;
        final int fx, fz;

        Frame(double ox, double oy, double oz, int fx, int fz) {
            this.ox = ox;
            this.oy = oy;
            this.oz = oz;
            this.fx = fx;
            this.fz = fz;
        }

        /** The camera-relative {x, z} of the campus point (a, r). */
        double[] at(double a, double r) {
            return new double[] { ox + a * fx - r * fz, oz + a * fz + r * fx };
        }
    }

    /**
     * A polygon of campus points (offset by {@code (da, dr)}) as bands of width {@code w} along the floor
     * ({@link #band}),
     * into the open quad batch; closed back to its first point when {@code closed}; in dashes when {@code dashed}.
     */
    private static void outline(Tessellator t, Frame f, double[][] pts, double da, double dr, boolean closed, double w,
        int rgb, float a, boolean dashed) {
        if (a <= 0.005f) return;
        t.setColorRGBA_I(rgb & 0xFFFFFF, (int) (Math.max(0, Math.min(1, a)) * 255));
        int n = pts.length, edges = closed ? n : n - 1;
        for (int i = 0; i < edges; i++) {
            double[] from = pts[i], to = pts[(i + 1) % n];
            double[] p = f.at(from[0] + da, from[1] + dr), q = f.at(to[0] + da, to[1] + dr);
            if (!dashed) {
                band(t, p[0], q[0], p[1], q[1], f.oy, w);
                continue;
            }
            double len = Math.hypot(q[0] - p[0], q[1] - p[1]);
            for (double s = 0; s < len; s += 2.0) {
                double e = Math.min(len, s + 1.2);
                double f0 = s / len, f1 = e / len;
                band(
                    t,
                    p[0] + (q[0] - p[0]) * f0,
                    p[0] + (q[0] - p[0]) * f1,
                    p[1] + (q[1] - p[1]) * f0,
                    p[1] + (q[1] - p[1]) * f1,
                    f.oy,
                    w);
            }
        }
    }

    /**
     * A line along the floor from (x0, z0) to (x1, z1) at height {@code y} (camera-relative) as a band turned to the
     * camera (across the line and across the line of sight, as {@code Shapes.string} turns its strings), {@code w}
     * wide or as wide as about a pixel and a half where it is, whichever is wider: a level band seen side-on from
     * standing height is thinner than a pixel past some fifteen blocks and breaks up into crawling dashes. It is cut
     * into pieces of at most {@link #PIECE} blocks so each is as wide as its own distance needs, lifted by half its
     * width so it does not sink into the floor, and carried on past its ends by half its width to close corners.
     */
    private static void band(Tessellator t, double x0, double x1, double z0, double z1, double y, double w) {
        double dx = x1 - x0, dz = z1 - z0, len = Math.hypot(dx, dz);
        if (len < 1e-6) return;
        double ux = dx / len, uz = dz / len;
        int pieces = Math.max(1, (int) Math.ceil(len / PIECE));
        for (int k = 0; k < pieces; k++) {
            double a = len * k / pieces, b = len * (k + 1) / pieces;
            double mx = x0 + ux * (a + b) / 2, mz = z0 + uz * (a + b) / 2;
            double width = Math.max(w, Math.sqrt(mx * mx + y * y + mz * mz) * PIXEL), half = width / 2;
            if (k == 0) a -= half;
            if (k == pieces - 1) b += half;
            double my = y + half;
            // sideways: the line's way crossed with the way from the camera to the piece's middle
            double sx = -uz * my, sy = uz * mx - ux * mz, sz = ux * my;
            double sl = Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (sl < 1e-9) {
                // the camera looks right along the line: lay the piece flat
                sx = -uz;
                sy = 0;
                sz = ux;
                sl = 1;
            }
            sx *= half / sl;
            sy *= half / sl;
            sz *= half / sl;
            double ax = x0 + ux * a, az = z0 + uz * a, bx = x0 + ux * b, bz = z0 + uz * b;
            t.addVertex(ax - sx, my - sy, az - sz);
            t.addVertex(ax + sx, my + sy, az + sz);
            t.addVertex(bx + sx, my + sy, bz + sz);
            t.addVertex(bx - sx, my - sy, bz - sz);
        }
    }

    /** The signs: a site's number and what it holds, on a faint pane turned to the camera. */
    private void signs(List<Sign> signs) {
        boolean begun = false;
        try {
            for (Sign s : signs) {
                double d = Math.sqrt(s.x * s.x + s.y * s.y + s.z * s.z);
                float a = (float) Math.min(1, (SIGN_RANGE - d) / 16) * 0.9f;
                if (a <= 0.05f) continue;
                if (!begun) {
                    FluxDraw.worldBegin();
                    begun = true;
                }
                // farther signs grow a little, so the number stays readable across the campus
                float px = (float) (1 / 48.0 * Math.max(1, d / 24));
                int tw = (int) Math.ceil(font().getStringWidth(s.title) * s.scale),
                    cw = s.caption.isEmpty() ? 0 : font().getStringWidth(s.caption);
                int th = (int) Math.ceil(9 * s.scale), wdt = Math.max(tw, cw) + 12,
                    hgt = s.caption.isEmpty() ? th + 4 : th + 15;
                GL11.glPushMatrix();
                // the matrix is popped on every path, so a failing sign cannot leave the stack one deeper
                try {
                    GL11.glTranslated(s.x, s.y, s.z);
                    GL11.glRotatef(-RenderManager.instance.playerViewY, 0f, 1f, 0f);
                    GL11.glRotatef(RenderManager.instance.playerViewX, 1f, 0f, 0f);
                    GL11.glScalef(-px, -px, px);
                    GL11.glTranslatef(-wdt / 2f, -hgt / 2f, 0);
                    FluxDraw.begin();
                    pane(0, 0, wdt, hgt, 0.45f * a);
                    rect(1, hgt - 2, wdt - 1, hgt - 1, s.rgb, 0.7f * a);
                    FluxDraw.end();
                    smallCentered(s.title, wdt / 2.0, 3, s.scale, s.rgb, a);
                    if (!s.caption.isEmpty()) smallCentered(s.caption, wdt / 2.0, th + 4, 1f, WHITE, a);
                } finally {
                    GL11.glPopMatrix();
                }
            }
        } catch (Throwable x) {
            abandon(Tessellator.instance);
            fail(SIGNS, x);
        } finally {
            if (begun) FluxDraw.worldEnd();
        }
    }

    /** Ends a batch an error broke off, so the tessellator is not left drawing. */
    private static void abandon(Tessellator t) {
        try {
            t.draw();
        } catch (Throwable ignored) {
            // it was not drawing after all
        }
    }

    private static void fail(int part, Throwable t) {
        if (OFF[part]) return;
        OFF[part] = true;
        FluxEcho.LOG.warn("The campus {} failed and is off until the game restarts", NAMES[part], t);
    }
}
