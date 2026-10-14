package com.fluxecho.campus.client;

import static com.fluxecho.client.FluxDraw.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
import com.fluxecho.campus.ModuleSpec;
import com.fluxecho.campus.ModuleSpecs;
import com.fluxecho.client.FarDraw;
import com.fluxecho.client.FluxDraw;
import com.fluxecho.client.ShaderCompat;
import com.fluxecho.core.EchoText;
import com.fluxecho.logic.ArchiveShape;
import com.fluxecho.logic.Blueprint;
import com.fluxecho.logic.BuildPlan;
import com.fluxecho.logic.BuildState;
import com.fluxecho.logic.CampusBand;
import com.fluxecho.logic.CampusPlan;
import com.fluxecho.logic.CampusSplit;
import com.fluxecho.logic.DomeGeneratorShape;
import com.fluxecho.logic.LiftRule;
import com.fluxecho.logic.NexusShape;
import com.fluxecho.logic.OuterLook;
import com.fluxecho.logic.OuterRing;
import com.fluxecho.logic.PodView;
import com.fluxecho.logic.RingSlots;
import com.fluxecho.logic.RootShape;
import com.fluxecho.logic.SiteFamily;
import com.fluxecho.logic.SkyRing;
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
 * the promenade ring, the gate (site 0) with the gatehouse's two wings over it (0.14.0, dashed while empty, its sign on
 * the road in front of it), each hall site's envelope (the Echo Archive's footprint, its door marked on the side facing
 * the nexus) and the diagonal sites' pavilion octagons (dashed while empty), with each site's number on a sign turned
 * to the camera. A site with a module on it is brighter, in the module's colour, and named by it; the
 * site of the current job is cyan; an empty hall site (0.13.0: a diagonal too) names the module the campus plans there
 * and where it stands (0.10.1: 「回响书库（需研究）」), so a player sees what comes next and how to unlock it.
 * <p>
 * The two sites on the nexus's axis (0.12.0): the zenith ({@link SiteFamily#ZENITH_SITE}) as the pod's octagon dashed
 * on the forum, drawn again up at the pod's floor (Y0 + 40, or the lower sky datum over high ground since 0.14.1,
 * {@link DomeGeneratorShape#datum}), and the squares of its six legs' feet while it stands on them (0.16.0: not once
 * it floats), its sign under the pod; the root
 * ({@link SiteFamily#ROOT_SITE}) as the well's octagon dashed on the forum over it, its sign behind the nexus. Their
 * signs name them (天顶, 根位) and what stands, is built or is planned there, as a hall site's do.
 * <p>
 * The outer ring (0.15.0, CITY.md §2.3), once the nexus has grown to phase III, the outer establish is the job or its
 * ground is laid ({@link OuterLook}): the outer disc's edge, the four 井 streets, the outer promenade, the four avenues
 * and the outer gate with its pylons, dashed until the ground is laid; the outer diagonals' octagons (sites 11, 13,
 * 15, 17, dashed while empty, coloured and signed as the inner sites) and the reserved outer flankers' envelopes
 * (20 to 27, dim dashes, signed 预留); and the split with every neighbouring campus near enough to share the ring (the
 * ones the server judges the outer sites against, sent with the campus, the job's own, and any other campus the client
 * has within {@link CampusSplit#REACH}), an amber dashed line where it crosses the outer disc. An outer diagonal a
 * neighbour holds part of for good (its inner square, or an outer diagonal it claimed) is signed as left to the
 * neighbours.
 * <p>
 * The sky ring (0.16.0, CITY.md §2.5), once the nexus has grown to phase IV, a sky module is the job or one is docked:
 * up at the pod's floor (the sky datum the pod has), the four module platforms' octagons ({@link SkyRing#centre},
 * S0, S2, S4 and S6) and the two edges of each catwalk from the pod out to its platform, dashed until the platform's
 * module stands, coloured and signed as the inner sites (the job's breathing cyan); each sign hangs under its
 * platform's front, seen from the forum.
 * <p>
 * The preview, while holding the nexus core and aiming at a block (not sneaking): what a lifted placement there would
 * raise, worked out with the item's own rules ({@link ItemBlockNexusCore#ground}, {@link ItemBlockNexusCore#lift},
 * {@link ItemBlockNexusCore#facingFor}): the dais, the forum, the promenade ring, the gate and the graded disc's edge
 * round the nexus centre the core would get, its front the way the core would face. It turns red, with the reason
 * over the core, when the client can already tell the server will refuse: the base outside the campus band (the nexus
 * would reach past the top of the world, or since 0.14.1 no well fits over the bedrock; {@link CampusBand}, never for
 * a core put back where it stood), no sky over the core ({@code Config.buildRequireSky}), the dais on a module, or
 * another nexus it knows of closer than {@code Config.buildMinSpacing}. Nothing when the campus is off.
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
    /**
     * The gatehouse's two wings either side of its open passage (0.14.0, {@link CampusPlan#inGatehouse}): R -15..-7
     * and 7..15 from A 23 to 39, dashed while no gatehouse stands.
     */
    private static final double[][] WING_LEFT = wing(-1), WING_RIGHT = wing(1);
    /**
     * The chamfer of a diagonal site's envelope (0.11.0: the pavilion's {@code oct(12, 16)}): its corners cut where
     * {@code |du| + |dv|} passes this.
     */
    private static final int DIAGONAL_CHAMFER = CampusPlan.DIAGONAL_K;

    /** The sites the masterplan shows: the inner ring's and the two on the nexus's axis (0.12.0). */
    private static final int SITES = SiteFamily.ROOT_SITE + 1;
    /** The zenith's pod and the root's well seen from above, and the zenith's legs' feet (0.12.0). */
    private static final double[][] POD = octagon(DomeGeneratorShape.POD_R, DomeGeneratorShape.POD_K),
        WELL = octagon(RootShape.WELL_R, RootShape.WELL_K), FOOT = octagon(0, 1);
    private static final List<int[]> FEET = DomeGeneratorShape.feet();

    /** The outer ring's sites (0.15.0): the outer diagonals, then the reserved outer flankers. */
    private static final int[] OUTER_SITES = { 11, 13, 15, 17, 20, 21, 22, 23, 24, 25, 26, 27 };
    /** The outer ring's lines, campus frame (0.15.0, {@link OuterLook}). */
    private static final List<OuterLook.Path> OUTER_DISC = OuterLook.disc(), OUTER_PROM = OuterLook.promenade(),
        STREETS = OuterLook.streets(), AVENUES = OuterLook.avenues(), OUTER_PYLONS = OuterLook.pylons();
    /**
     * Which outer diagonals are left to a campus's neighbours, by the campus's centre, front, claims and neighbours: a
     * cache (the answer never changes for the same question), cleared when it grows large.
     */
    private static final Map<String, boolean[]> TRIMMED = new ConcurrentHashMap<>();

    /** The parts that fail on their own. */
    private static final int PLAN = 0, SIGNS = 1, PREVIEW = 2, OUTER = 3, SKY = 4;
    private static final String[] NAMES = { "masterplan", "masterplan signs", "placement preview",
        "masterplan's outer ring", "masterplan's sky ring" };

    /** The sky ring's module sites (0.16.0): S0, S2, S4, S6; the berths between them come with 0.17.0. */
    private static final int[] SKY_SITES = { SkyRing.SITE0, SkyRing.SITE0 + 2, SkyRing.SITE0 + 4, SkyRing.SITE0 + 6 };
    /** A sky platform seen from above round its centre: {@code oct(10, 14)}. */
    private static final double[][] PLATFORM = octagon(SkyRing.HALF, SkyRing.K);
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
                // the pod's floor at the campus's sky datum (0.14.1: lower over high ground; 0.16.0: where a pod
                // standing there has it), and whether it stands on its legs
                int[] pod = pod(n, v, tiles, c, n.front().offsetX, n.front().offsetZ);
                int datum = pod[0];
                boolean moored = pod[1] != 0;
                Frame up = new Frame(ox, oy + datum, oz, f.fx, f.fz);
                Site[] sites = sites(n, v, tiles, c, f.fx, f.fz);
                float breath = 0.85f + 0.15f * (float) Math.sin(t * 0.06);
                // the outer ring (0.15.0), its own part: an error there leaves the inner city's plan drawn
                Outer outer = null;
                if (!OFF[OUTER]) try {
                    outer = outer(n, v, tiles, c, f.fx, f.fz);
                } catch (Throwable x) {
                    fail(OUTER, x);
                }
                // the sky ring (0.16.0), its own part likewise
                Site[] sky = null;
                if (!OFF[SKY]) try {
                    sky = sky(n, v, tiles, c, f.fx, f.fz, datum);
                } catch (Throwable x) {
                    fail(SKY, x);
                }
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
                    Site gate = sites[0];
                    float ga = gate.alpha * k * (gate.job ? breath : 1f);
                    outline(tes, f, GATE, 0, 0, false, 0.12, gate.rgb, ga, false);
                    outline(tes, f, WING_LEFT, 0, 0, true, 0.12, gate.rgb, ga, !gate.occupied);
                    outline(tes, f, WING_RIGHT, 0, 0, true, 0.12, gate.rgb, ga, !gate.occupied);
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
                    // the axis: the zenith's pod on the forum and up at its floor, its legs' feet; the root's well
                    Site zen = sites[SiteFamily.ZENITH_SITE], root = sites[SiteFamily.ROOT_SITE];
                    float za = zen.alpha * k * (zen.job ? breath : 1f), ra = root.alpha * k * (root.job ? breath : 1f);
                    outline(tes, f, POD, 0, 0, true, 0.12, zen.rgb, za, true);
                    outline(tes, up, POD, 0, 0, true, 0.14, zen.rgb, Math.min(0.35f, za * 1.3f), false);
                    // the legs' feet only while the pod stands (or is to stand) on them: not once it floats (0.16.0)
                    if (moored)
                        for (int[] foot : FEET) outline(tes, f, FOOT, foot[0], foot[1], true, 0.1, zen.rgb, za, false);
                    outline(tes, f, WELL, 0, 0, true, 0.12, root.rgb, ra, true);
                    if (outer != null && !OFF[OUTER]) try {
                        outerLines(tes, f, outer, k, breath);
                    } catch (Throwable x) {
                        // the batch stays open for the inner plan's draw below
                        fail(OUTER, x);
                    }
                    if (sky != null && !OFF[SKY]) try {
                        skyLines(tes, up, sky, k, breath);
                    } catch (Throwable x) {
                        fail(SKY, x);
                    }
                    drawing = false;
                    tes.draw();
                    if (through) GL11.glEnable(GL11.GL_DEPTH_TEST);
                }
                for (int s = 0; s < SITES; s++) {
                    double[] at = signAt(s, sites[s].ring, datum);
                    double[] r = f.at(at[0], at[1]);
                    signs.add(new Sign(r[0], oy + at[2], r[1], sites[s].title, sites[s].caption, sites[s].rgb, 2f));
                }
                if (outer != null && !OFF[OUTER]) for (int i = 0; i < OUTER_SITES.length; i++) {
                    Site st = outer.sites[i];
                    double[] at = OuterLook.signAt(OUTER_SITES[i]);
                    double[] r = f.at(at[0], at[1]);
                    signs.add(new Sign(r[0], oy + at[2], r[1], st.title, st.caption, st.rgb, 2f));
                }
                if (sky != null && !OFF[SKY]) for (int i = 0; i < SKY_SITES.length; i++) {
                    Site st = sky[i];
                    double[] at = skySignAt(SKY_SITES[i], datum);
                    double[] r = f.at(at[0], at[1]);
                    signs.add(new Sign(r[0], oy + at[2], r[1], st.title, st.caption, st.rgb, 2f));
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
        /**
         * Whether a module that is no pavilion stands on a diagonal (a 0.9.2 module on its old inner-ring slot, docked
         * there or turned away by the nexus), so the sign keeps off the allee that module stands over.
         */
        boolean ring;
        int rgb = DIM;
        float alpha = 0.2f;
        String title = "", caption = "";
    }

    /**
     * The state of each site of the campus: whether a module stands on it (docked to the nexus under the site's
     * number, or a loaded module standing in its envelope), whether the current job builds there, and its sign.
     */
    private static Site[] sites(TileNexus n, Campus.View v, List<TileMultiblock> tiles, int[] c, int fx, int fz) {
        Site[] out = new Site[SITES];
        int jobSite = -1;
        String jobModule = null;
        if (v.hasJob() && !BuildState.ended(v.stateEnum())) {
            jobModule = ModuleSpecs.moduleOf(v.planKey);
            jobSite = jobModule == null ? -1 : v.site;
        }
        String[] names = new String[SITES];
        int[] colours = new int[SITES];
        boolean[] standing = new boolean[SITES], pavilion = new boolean[SITES], ringStanding = new boolean[SITES];
        boolean gatehouse = false;
        for (TileMultiblock m : tiles) {
            if (!(m instanceof TileModule mod) || m.isInvalid() || m.getWorldObj() != n.getWorldObj()) continue;
            ModuleSpec spec = ModuleSpecs.get(mod.moduleKey());
            boolean isPavilion = spec != null && spec.family == SiteFamily.PAVILION;
            boolean isGate = spec != null && spec.family == SiteFamily.GATE;
            // A formed module that is no pavilion on a diagonal's old inner-ring slot (a 0.9.2 module, docked or
            // turned away) keeps that diagonal's sign in the octagon's middle, clear of its walls, as in 0.12.0. It
            // is kept apart from standing, so the site's caption and colour stay as they were.
            int[] mc = mod.centre();
            int k = RingSlots.slotAt(mc[0] - c[0], mc[1] - c[1], mc[2] - c[2], Config.innerRadius, fx, fz);
            if (k % 2 == 1 && mod.formed() && !isPavilion) ringStanding[k] = true;
            int s = axisSiteOf(mod, c, fx, fz);
            if (s < 0) {
                int[] l = CampusPlan.toLocal(mc[0] - c[0], mc[2] - c[2], fx, fz);
                if (Math.abs(mc[1] - c[1]) > 8) continue;
                s = siteOf(l[0], l[1]);
            }
            if (s < 0) {
                if (!mod.clientDocked || mod.clientSlot < 0 || !Arrays.equals(mod.clientNexus, c)) continue;
                s = mod.clientSlot;
            }
            if (s >= SITES) continue;
            // the gate's site counts a gatehouse alone (0.14.0): a 0.9.2 module on the old ring's slot 0 stands on the
            // gate road, which keeps its sign
            if (s == 0 && !isGate) continue;
            gatehouse |= isGate;
            standing[s] = true;
            pavilion[s] |= isPavilion;
            if (names[s] == null) {
                names[s] = mod.moduleKey();
                colours[s] = mod.moduleColor();
            }
        }
        for (int s = 0; s < SITES; s++) {
            Site st = new Site();
            SiteFamily family = SiteFamily.of(s);
            boolean axis = family != null && family.axis();
            boolean docked = !axis && (n.clientDockedMask & 1 << s) != 0;
            // the gate is occupied by a gatehouse alone (0.14.0): one standing, or docked there by the campus's record
            st.occupied = s != 0 ? docked || standing[s] : gatehouse || docked && v.gateBuilt(s);
            st.job = s == jobSite;
            st.ring = s % 2 == 1 && !axis
                && !st.job
                && !pavilion[s]
                && !v.pavilionBuilt(s)
                && (st.occupied || ringStanding[s]);
            st.title = EchoText.t(SiteFamily.labelKey(s), s);
            if (s == 0 && !st.job && !st.occupied && planned(v, s) == null) {
                // the gate road, nothing planned over it
                st.rgb = CYAN;
                st.alpha = 0.22f;
                st.caption = EchoText.t("build.masterplan.reserved");
            } else if (st.job) {
                st.rgb = CYAN;
                st.alpha = 0.3f;
                st.caption = EchoText.t("build.masterplan.building", EchoText.t("module." + jobModule));
            } else if (st.occupied) {
                int col = docked ? n.clientModuleColor[s] : axis || s == 0 ? colours[s] : 0;
                st.rgb = col == 0 ? WHITE : col;
                st.alpha = 0.28f;
                st.caption = names[s] != null ? EchoText.t("module." + names[s]) : EchoText.t("build.masterplan.taken");
            } else {
                // every site names what the campus plans there (0.13.0: the diagonals too, for the pavilions); a
                // diagonal nothing is planned on is reserved still
                st.rgb = DIM;
                st.alpha = s % 2 == 0 ? 0.2f : 0.15f;
                st.caption = EchoText.t(s % 2 == 0 && !axis ? "build.masterplan.hall" : "build.masterplan.reserved");
                Campus.ModuleState planned = planned(v, s);
                if (planned != null) {
                    // the module planned here and where it stands: 「回响书库（需研究）」
                    st.caption = EchoText.t(
                        "build.masterplan.planned",
                        EchoText.t("module." + planned.key),
                        EchoText.t(
                            "build.masterplan.status." + planned.status.name()
                                .toLowerCase(Locale.ROOT)));
                    st.rgb = planned.status == Campus.ModuleStatus.NEEDS_RESEARCH ? AMBER
                        : planned.status == Campus.ModuleStatus.QUEUED ? CYAN : DIM;
                    st.alpha = 0.24f;
                }
            }
            out[s] = st;
        }
        return out;
    }

    /**
     * The first module the campus plans on a site and has not built there yet ({@link Campus.View#modules}), or null.
     */
    private static Campus.ModuleState planned(Campus.View v, int site) {
        for (Campus.ModuleState m : v.modules) if (m.site == site && m.status != Campus.ModuleStatus.BUILT) return m;
        return null;
    }

    /**
     * The site whose envelope holds the campus cell (a, r): a hall site's Archive footprint, a diagonal's, or the
     * gatehouse's on the gate (0.14.0).
     */
    private static int siteOf(int a, int r) {
        if (CampusPlan.inGatehouse(a, r)) return 0;
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

    /**
     * The zenith's pod as the masterplan draws it (0.16.0): {its sky datum, 1 while it stands or is to stand on its
     * legs, 0 once it floats} ({@link PodView#moored}). An Echo Dome Generator standing at the zenith's controller
     * column at a sky datum says it, docked or not (a docked one first), by the shape it last stood as and its datum
     * where its controller stands; else the campus's jobs: the current release job, on its legs at the datum it
     * releases, or a dome job current or queued that builds the pod floating, at its datum; else the pod the campus's
     * base layer gives, on its legs.
     */
    private static int[] pod(TileNexus n, Campus.View v, List<TileMultiblock> tiles, int[] c, int fx, int fz) {
        int[] ctrl = DomeGeneratorShape.controller(SiteFamily.ZENITH_SITE, c[0], c[1], c[2], fx, fz);
        TileModule seen = null;
        for (TileMultiblock m : tiles) {
            if (!(m instanceof TileModule mod) || m.isInvalid() || m.getWorldObj() != n.getWorldObj()) continue;
            if (!DomeGeneratorShape.KEY.equals(mod.moduleKey()) || mod.xCoord != ctrl[0] || mod.zCoord != ctrl[2])
                continue;
            if (!DomeGeneratorShape.isDatum(DomeGeneratorShape.podDatum(mod.yCoord, c[1]))) continue;
            boolean docked = mod.clientDocked && mod.clientSlot == SiteFamily.ZENITH_SITE
                && Arrays.equals(mod.clientNexus, c);
            if (seen == null || docked) seen = mod;
            if (docked) break;
        }
        boolean releasing = v.hasJob() && !BuildState.ended(v.stateEnum())
            && BuildPlan.RELEASE.equals(v.planKey)
            && v.form.floats();
        boolean floatingJob = v.floatingJob > 0;
        boolean moored = PodView.moored(seen == null ? null : seen.shapeName(), floatingJob, releasing);
        int datum = seen != null ? DomeGeneratorShape.podDatum(seen.yCoord, c[1])
            : releasing ? v.form.pod : floatingJob ? v.floatingJob : DomeGeneratorShape.datum(c[1]);
        return new int[] { datum, moored ? 1 : 0 };
    }

    /**
     * The site of the nexus's axis a module stands on (0.12.0): its controller is where the zenith's or the root's
     * stands for this nexus, facing its front; -1 for any other.
     */
    private static int axisSiteOf(TileModule m, int[] c, int fx, int fz) {
        int[][] at = { DomeGeneratorShape.controller(SiteFamily.ZENITH_SITE, c[0], c[1], c[2], fx, fz),
            RootShape.controller(SiteFamily.ROOT_SITE, c[0], c[1], c[2], fx, fz) };
        int[] sites = { SiteFamily.ZENITH_SITE, SiteFamily.ROOT_SITE };
        for (int i = 0; i < at.length; i++) {
            if (at[i][0] == m.xCoord && at[i][1] == m.yCoord && at[i][2] == m.zCoord) return sites[i];
        }
        return -1;
    }

    /**
     * Where a site's sign stands: {A, R, height over the floor}. A diagonal's stands on its allee in front of the
     * pavilion's inner portal ({@link CampusPlan#pavilionSign}, 0.13.0), at eye height as a hall's before its door, so
     * the pavilion planned, rising or standing there never shuts it in; only a diagonal a 0.9.2 module stands on by
     * its old inner-ring slot ({@code ring}, that module over the allee) keeps it in the octagon's middle. The
     * zenith's hangs under the pod at the campus's sky datum ({@code datum}, 0.14.1).
     */
    private static double[] signAt(int site, boolean ring, int datum) {
        // the gate's on the road in front of the gatehouse (0.14.0), as a hall's before its door, never under the
        // bridge
        if (site == 0) return new double[] { CampusPlan.GATE_SIGN, 0, 3.2 };
        // the zenith's under the pod (off the lift's shaft, below its own plate), seen from the forum; the root's
        // behind the nexus
        if (site == SiteFamily.ZENITH_SITE) return new double[] { 3, 0, datum - 7.5 };
        if (site == SiteFamily.ROOT_SITE) return new double[] { -8.5, 0, 2.2 };
        int[] ax = CampusPlan.siteAxis(site);
        if (site % 2 == 0) {
            // in front of the hall's door, at eye height for someone on the forecourt
            double d = CampusPlan.HALL_FRONT - 3.5;
            return new double[] { ax[0] * d, ax[1] * d, 3.2 };
        }
        if (ring) {
            int[] dc = CampusPlan.diagonalCentre(site);
            return new double[] { dc[0], dc[1], 3.0 };
        }
        int[] at = CampusPlan.pavilionSign(site);
        return new double[] { at[0], at[1], 3.2 };
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

    /** One wing of the gatehouse ({@code side} -1 left, 1 right of the road): its envelope's outer cells' edges. */
    private static double[][] wing(int side) {
        double a0 = CampusPlan.GATEHOUSE_FROM - 0.5, a1 = CampusPlan.GATEHOUSE_TO + 0.5;
        double r0 = side * (CampusPlan.GATE_PASSAGE + 0.5), r1 = side * (CampusPlan.GATEHOUSE_HALF + 0.5);
        return new double[][] { { a0, r0 }, { a1, r0 }, { a1, r1 }, { a0, r1 } };
    }

    /** The gate strip's outline from {@code from} out, open on the inner side. */
    private static double[][] gate(double from) {
        return new double[][] { { from, -3.5 }, { 24.5, -3.5 }, { 24.5, -5.5 }, { 29.5, -5.5 }, { 29.5, -3.5 },
            { 33.5, -3.5 }, { 33.5, -5.5 }, { 36.5, -5.5 }, { 36.5, 5.5 }, { 33.5, 5.5 }, { 33.5, 3.5 }, { 29.5, 3.5 },
            { 29.5, 5.5 }, { 24.5, 5.5 }, { 24.5, 3.5 }, { from, 3.5 } };
    }

    // ---- the sky ring (0.16.0)

    /**
     * The sky ring's module sites as the masterplan shows them ({@link #SKY_SITES}), or null while it shows none:
     * before the nexus has grown to the phase that opens the sky ring ({@link SiteFamily#SKY}'s), unless a sky module
     * is the job or one is docked. A site is occupied by a module docked there ({@link TileNexus#clientSkyDocked}) or a
     * loaded one standing on its platform at about the pod's datum {@code datum}.
     */
    private static Site[] sky(TileNexus n, Campus.View v, List<TileMultiblock> tiles, int[] c, int fx, int fz,
        int datum) {
        int jobSite = -1;
        String jobModule = null;
        if (v.hasJob() && !BuildState.ended(v.stateEnum())) {
            jobModule = ModuleSpecs.moduleOf(v.planKey);
            jobSite = jobModule == null || !SkyRing.skySite(v.site) ? -1 : v.site;
        }
        if (n.phase() < SiteFamily.SKY.opens && jobSite < 0 && n.clientSkyDocked == 0) return null;
        // the sky modules that stand, by the platform their foundation's centre stands on
        String[] names = new String[SKY_SITES.length];
        int[] colours = new int[names.length];
        for (TileMultiblock m : tiles) {
            if (!(m instanceof TileModule mod) || m.isInvalid() || m.getWorldObj() != n.getWorldObj()) continue;
            ModuleSpec spec = ModuleSpecs.get(mod.moduleKey());
            if (spec == null || spec.family != SiteFamily.SKY || !mod.formed()) continue;
            int[] mc = mod.centre();
            if (Math.abs(mc[1] - (c[1] + datum)) > 16) continue;
            int[] l = CampusPlan.toLocal(mc[0] - c[0], mc[2] - c[2], fx, fz);
            for (int i = 0; i < names.length; i++)
                if (names[i] == null && SkyRing.inPlatform(SKY_SITES[i], l[0], l[1])) {
                    names[i] = mod.moduleKey();
                    colours[i] = mod.moduleColor();
                }
        }
        Site[] out = new Site[SKY_SITES.length];
        for (int i = 0; i < SKY_SITES.length; i++) {
            int s = SKY_SITES[i], slot = SkyRing.slot(s);
            Site st = new Site();
            st.title = EchoText.t(SiteFamily.labelKey(s), s);
            out[i] = st;
            boolean docked = (n.clientSkyDocked & SkyRing.bit(s)) != 0;
            st.occupied = docked || names[i] != null;
            st.job = s == jobSite;
            if (st.job) {
                st.rgb = CYAN;
                st.alpha = 0.3f;
                st.caption = EchoText.t("build.masterplan.building", EchoText.t("module." + jobModule));
            } else if (st.occupied) {
                int col = docked && n.clientSkyColor[slot] != 0 ? n.clientSkyColor[slot] : colours[i];
                st.rgb = col == 0 ? WHITE : col;
                st.alpha = 0.28f;
                st.caption = names[i] != null ? EchoText.t("module." + names[i]) : EchoText.t("build.masterplan.taken");
            } else {
                st.rgb = DIM;
                st.alpha = 0.15f;
                st.caption = EchoText.t("build.masterplan.reserved");
                Campus.ModuleState planned = planned(v, s);
                if (planned != null) {
                    st.caption = EchoText.t(
                        "build.masterplan.planned",
                        EchoText.t("module." + planned.key),
                        EchoText.t(
                            "build.masterplan.status." + planned.status.name()
                                .toLowerCase(Locale.ROOT)));
                    st.rgb = planned.status == Campus.ModuleStatus.NEEDS_RESEARCH ? AMBER
                        : planned.status == Campus.ModuleStatus.QUEUED ? CYAN : DIM;
                    st.alpha = 0.24f;
                }
            }
        }
        return out;
    }

    /**
     * The sky ring's lines up at the pod's floor ({@code up}): each module platform's octagon and its catwalk's two
     * edges from the pod's rim out to the platform's front, dashed until the platform's module stands.
     */
    private static void skyLines(Tessellator tes, Frame up, Site[] sky, float k, float breath) {
        double t0 = SkyRing.CATWALK_FROM - 0.5, t1 = SkyRing.CATWALK_TO + 0.5, side = SkyRing.CATWALK_HALF + 0.5;
        for (int i = 0; i < SKY_SITES.length; i++) {
            Site st = sky[i];
            int s = SKY_SITES[i];
            float a = Math.min(0.35f, st.alpha * 1.2f) * k * (st.job ? breath : 1f);
            int[] pc = SkyRing.centre(s), ax = SkyRing.axis(s);
            outline(tes, up, PLATFORM, pc[0], pc[1], true, 0.14, st.rgb, a, !st.occupied);
            for (double e : new double[] { -side, side }) outline(
                tes,
                up,
                new double[][] { local(ax, t0, e), local(ax, t1, e) },
                0,
                0,
                false,
                0.1,
                st.rgb,
                a,
                !st.occupied);
        }
    }

    /**
     * Where a sky site's sign hangs: {A, R, height over the floor}, under its platform's front edge (radial 24, clear
     * of the light pillar under its middle), a few blocks under the deck, so it reads from the forum.
     */
    private static double[] skySignAt(int site, int datum) {
        int[] ax = SkyRing.axis(site);
        double d = SkyRing.FRONT + 2;
        return new double[] { ax[0] * d, ax[1] * d, datum - 3.5 };
    }

    // ---- the outer ring (0.15.0)

    /**
     * What the masterplan shows of a campus's outer ring: whether its ground is laid, the neighbours it shares the
     * ring with ({x, z} centres) and the state of each outer site ({@link #OUTER_SITES}).
     */
    private static final class Outer {

        final boolean laid;
        final List<int[]> neighbours;
        final int cx, cz;
        final Site[] sites;

        Outer(boolean laid, List<int[]> neighbours, int cx, int cz, Site[] sites) {
            this.laid = laid;
            this.neighbours = neighbours;
            this.cx = cx;
            this.cz = cz;
            this.sites = sites;
        }
    }

    /**
     * The campus's outer ring as the masterplan shows it, or null while it shows none: before the nexus has grown to
     * the phase that opens the outer ring ({@link SiteFamily#OUTER_DIAG}'s), unless the outer establish is the job or
     * has laid the ground already.
     */
    private static Outer outer(TileNexus n, Campus.View v, List<TileMultiblock> tiles, int[] c, int fx, int fz) {
        boolean job = v.hasJob() && BuildPlan.OUTER.equals(v.planKey) && !BuildState.ended(v.stateEnum());
        if (n.phase() < SiteFamily.OUTER_DIAG.opens && !v.outerLaid && !job) return null;
        List<int[]> near = neighbours(n, v, tiles, c);
        boolean[] trimmed = trimmed(c, fx, fz, v.claims, near);
        int jobSite = -1;
        String jobModule = null;
        if (v.hasJob() && !BuildState.ended(v.stateEnum())) {
            jobModule = ModuleSpecs.moduleOf(v.planKey);
            jobSite = jobModule == null ? -1 : v.site;
        }
        // the outer diagonals' buildings that stand, by the octagon their foundation's centre stands on
        String[] names = new String[OuterRing.DIAG_SITES.length];
        int[] colours = new int[names.length];
        for (TileMultiblock m : tiles) {
            if (!(m instanceof TileModule mod) || m.isInvalid() || m.getWorldObj() != n.getWorldObj()) continue;
            ModuleSpec spec = ModuleSpecs.get(mod.moduleKey());
            if (spec == null || spec.family != SiteFamily.OUTER_DIAG) continue;
            int[] mc = mod.centre();
            if (Math.abs(mc[1] - c[1]) > 8) continue;
            int[] l = CampusPlan.toLocal(mc[0] - c[0], mc[2] - c[2], fx, fz);
            int site = OuterRing.diagSiteAt(l[0], l[1]);
            for (int i = 0; i < names.length; i++) if (OuterRing.DIAG_SITES[i] == site && names[i] == null) {
                names[i] = mod.moduleKey();
                colours[i] = mod.moduleColor();
            }
        }
        Site[] out = new Site[OUTER_SITES.length];
        for (int i = 0; i < OUTER_SITES.length; i++) {
            int s = OUTER_SITES[i];
            Site st = new Site();
            st.title = EchoText.t(SiteFamily.labelKey(s), s);
            out[i] = st;
            if (!OuterRing.diagSite(s)) {
                // a reserved outer flanker (opened from phase IV)
                st.rgb = DIM;
                st.alpha = 0.1f;
                st.caption = EchoText.t("build.masterplan.reserved");
                continue;
            }
            boolean docked = (n.clientOuterDocked & 1 << i) != 0;
            st.occupied = docked || names[i] != null;
            st.job = s == jobSite;
            if (st.job) {
                st.rgb = CYAN;
                st.alpha = 0.3f;
                st.caption = EchoText.t("build.masterplan.building", EchoText.t("module." + jobModule));
            } else if (st.occupied) {
                int col = docked && n.clientOuterColor[i] != 0 ? n.clientOuterColor[i] : colours[i];
                st.rgb = col == 0 ? WHITE : col;
                st.alpha = 0.28f;
                st.caption = names[i] != null ? EchoText.t("module." + names[i]) : EchoText.t("build.masterplan.taken");
            } else if (trimmed[i]) {
                // it reaches over the split: the campus offers no building there (why.trimmed)
                st.rgb = DIM;
                st.alpha = 0.12f;
                st.caption = EchoText.t("build.masterplan.trimmed");
            } else {
                st.rgb = DIM;
                st.alpha = 0.15f;
                st.caption = EchoText.t("build.masterplan.reserved");
                Campus.ModuleState planned = planned(v, s);
                if (planned != null) {
                    st.caption = EchoText.t(
                        "build.masterplan.planned",
                        EchoText.t("module." + planned.key),
                        EchoText.t(
                            "build.masterplan.status." + planned.status.name()
                                .toLowerCase(Locale.ROOT)));
                    st.rgb = planned.status == Campus.ModuleStatus.NEEDS_RESEARCH ? AMBER
                        : planned.status == Campus.ModuleStatus.QUEUED ? CYAN : DIM;
                    st.alpha = 0.24f;
                }
            }
        }
        return new Outer(v.outerLaid, near, c[0], c[2], out);
    }

    /**
     * The neighbouring campuses a campus shares its outer ring with, {x, z} or {x, z, claims} each
     * ({@link OuterLook#neighbours}): those the server judges its outer sites against ({@link Campus.View#split},
     * sent with the campus, 0.15.0), those its job was planned with ({@link Campus.View#neighbours}), and any other
     * active campus the client has loaded whose centre lies within {@link CampusSplit#REACH}. Sorted, each once.
     */
    private static List<int[]> neighbours(TileNexus n, Campus.View v, List<TileMultiblock> tiles, int[] c) {
        List<int[]> loaded = new ArrayList<>();
        for (TileMultiblock m : tiles) {
            if (!(m instanceof TileNexus x) || x == n || m.isInvalid() || m.getWorldObj() != n.getWorldObj()) continue;
            Campus.View xv = x.clientCampus;
            if (xv == null || !xv.active) continue;
            int[] o = x.centre();
            loaded.add(new int[] { o[0], o[2] });
        }
        return OuterLook.neighbours(v.split, v.neighbours, loaded, c[0], c[2]);
    }

    /**
     * Which outer diagonals the neighbours hold part of for good ({@link OuterLook#siteTrimmed}, with the campus's own
     * claims), cached.
     */
    private static boolean[] trimmed(int[] c, int fx, int fz, int claims, List<int[]> near) {
        if (near.isEmpty()) return new boolean[OuterRing.DIAG_SITES.length];
        StringBuilder key = new StringBuilder().append(c[0])
            .append(',')
            .append(c[2])
            .append(',')
            .append(fx)
            .append(',')
            .append(fz)
            .append(',')
            .append(claims);
        for (int[] o : near) key.append('|')
            .append(java.util.Arrays.toString(o));
        String k = key.toString();
        boolean[] got = TRIMMED.get(k);
        if (got != null) return got;
        boolean[] out = new boolean[OuterRing.DIAG_SITES.length];
        for (int i = 0; i < out.length; i++)
            out[i] = OuterLook.siteTrimmed(OuterRing.DIAG_SITES[i], c[0], c[2], fx, fz, claims, near);
        if (TRIMMED.size() > 64) TRIMMED.clear();
        TRIMMED.put(k, out);
        return out;
    }

    /**
     * The outer ring's lines into the open batch ({@code k} the pass's share of alpha): the disc's edge, the promenade,
     * the streets, the avenues and the outer gate with its pylons, dashed until the ground is laid; the outer
     * diagonals' octagons as their sites' state has them (dashed while empty), the reserved flankers in dim dashes;
     * the split with each neighbour, amber dashes.
     */
    private static void outerLines(Tessellator tes, Frame f, Outer o, float k, float breath) {
        boolean dashed = !o.laid;
        float a = (o.laid ? 0.18f : 0.12f) * k;
        for (OuterLook.Path p : OUTER_DISC) outline(tes, f, p.pts, 0, 0, p.closed, 0.2, DISC, 0.16f * k, dashed);
        for (OuterLook.Path p : OUTER_PROM) outline(tes, f, p.pts, 0, 0, p.closed, 0.12, CYAN, a, dashed);
        for (OuterLook.Path p : STREETS) outline(tes, f, p.pts, 0, 0, p.closed, 0.12, CYAN, a, dashed);
        for (OuterLook.Path p : AVENUES) outline(tes, f, p.pts, 0, 0, p.closed, 0.12, CYAN, a, dashed);
        for (OuterLook.Path p : OUTER_PYLONS) outline(tes, f, p.pts, 0, 0, p.closed, 0.1, CYAN, 1.4f * a, false);
        for (int i = 0; i < OUTER_SITES.length; i++) {
            int s = OUTER_SITES[i];
            Site st = o.sites[i];
            float sa = st.alpha * k * (st.job ? breath : 1f);
            if (OuterRing.diagSite(s))
                outline(tes, f, OuterLook.diagOutline(s), 0, 0, true, 0.12, st.rgb, sa, !st.occupied);
            else outline(tes, f, OuterLook.flankOutline(s), 0, 0, true, 0.1, st.rgb, sa, true);
        }
        float sa = 0.32f * k;
        tes.setColorRGBA_I(AMBER & 0xFFFFFF, (int) (Math.max(0, Math.min(1, sa)) * 255));
        for (int[] nb : o.neighbours) {
            double[] l = OuterLook.splitLine(o.cx, o.cz, nb[0], nb[1]);
            if (l == null) continue;
            dashes(
                tes,
                l[0] - RenderManager.renderPosX,
                l[1] - RenderManager.renderPosZ,
                l[2] - RenderManager.renderPosX,
                l[3] - RenderManager.renderPosZ,
                f.oy,
                0.2);
        }
    }

    /** A dashed line along the floor between two camera-relative points {@code (x, z)}, as {@link #outline} dashes. */
    private static void dashes(Tessellator t, double x0, double z0, double x1, double z1, double y, double w) {
        double len = Math.hypot(x1 - x0, z1 - z0);
        if (len < 1e-6) return;
        for (double s = 0; s < len; s += 2.0) {
            double e = Math.min(len, s + 1.2);
            double f0 = s / len, f1 = e / len;
            band(t, x0 + (x1 - x0) * f0, x0 + (x1 - x0) * f1, z0 + (z1 - z0) * f0, z0 + (z1 - z0) * f1, y, w);
        }
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
        String why = refusal(w, b, c, x, cy, z, fd, ItemBlockNexusCore.putsBack(p.getHeldItem(), w, x, cy, z));
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
     * can tell (the same checks in the same order as the item's; the campus band never refuses a core put back where
     * it stood, {@code putBack}), translated; null when it sees nothing wrong.
     */
    private static String refusal(World w, Blueprint b, int[] c, int x, int cy, int z, ForgeDirection f,
        boolean putBack) {
        String band = CampusBand.refusal(c[1], putBack);
        if (band != null) return EchoText.t(band);
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
        // a core put back where it stood keeps only the inner discs' spacing (0.15.0), as the item's check does
        int spacing = putBack ? com.fluxecho.campus.Campus.innerSpacing() : Config.buildMinSpacing;
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
