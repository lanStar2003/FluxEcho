package com.fluxecho.city.client;

import static com.fluxecho.client.FluxDraw.AMBER;
import static com.fluxecho.client.FluxDraw.WHITE;
import static com.fluxecho.client.FluxDraw.font;
import static com.fluxecho.client.FluxDraw.pane;
import static com.fluxecho.client.FluxDraw.rect;
import static com.fluxecho.client.FluxDraw.smallCentered;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

import org.lwjgl.opengl.GL11;

import com.fluxecho.Config;
import com.fluxecho.FluxEcho;
import com.fluxecho.campus.BlockFitting;
import com.fluxecho.campus.PartBlocks;
import com.fluxecho.city.TileArcaneCourt;
import com.fluxecho.city.TileBloodCovenant;
import com.fluxecho.city.TileCityModule;
import com.fluxecho.city.TileDomeGenerator;
import com.fluxecho.city.TileDualFoilVault;
import com.fluxecho.city.TileEcoDome;
import com.fluxecho.city.TileHoloTable;
import com.fluxecho.city.TileHuntingGround;
import com.fluxecho.city.TileManaFountain;
import com.fluxecho.city.TileResonanceGate;
import com.fluxecho.city.TileRoot;
import com.fluxecho.city.TileSophonArray;
import com.fluxecho.client.FluxDraw;
import com.fluxecho.client.Motes;
import com.fluxecho.core.EchoText;
import com.fluxecho.logic.ModuleLook;
import com.fluxecho.logic.Parts;
import com.fluxecho.logic.ShapeKit;
import com.fluxecho.nexus.TileMultiblock;
import com.fluxecho.render.Shapes;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * What every city module shows of itself (0.11.0, CITY.md §1.1 and §1.6 item 8), one renderer for every
 * {@link TileCityModule}; a module kind's own visuals come on top ({@link EcoDomeRender}; 0.12.0
 * {@link DomeGeneratorRender}, whose dome is a landmark drawn past the effects' range too, {@link #landmark}, and
 * {@link RootRender}; 0.13.0 the pavilions' {@link ArcaneCourtRender}, {@link BloodCovenantRender},
 * {@link HuntingGroundRender} and {@link ManaFountainRender}; 0.14.0 the Dual-foil Vault's {@link VaultRender} and
 * the Resonance Gate's {@link ResonanceGateRender}, whose floating spire is a landmark too; 0.15.0 the Sophon
 * Array's {@link SophonRender}, whose frame, light point and circuit canopy are a landmark, and the Holo Sand
 * Table's {@link HoloTableRender}).
 * <ul>
 * <li>A line of light along the plinth course of its walls ({@link ModuleLook#course}), in its dock state: the module's
 * own colour while docked and powered (a soft pulse running round it), amber breathing while docked without power, a
 * dim grey-blue otherwise. Not formed (a panel mined, the last cells not yet built) the line stays, dimmer, along the
 * stretches of wall that stand, and nothing else is drawn.</li>
 * <li>While it forms, a plane of its colour sweeps up through its box (the Echo Dome Generator's up its legs and
 * through its pod).</li>
 * <li>Its name plate over the portal's canopy ({@link ModuleLook#plates}; over the controller inside a module the
 * light lift enters, and under the floor of one hanging in the air, 0.12.0), turned to the camera within
 * {@link #PLATE_RANGE} blocks: its name in its colour and, under it, its state.</li>
 * <li>Near it (the camera inside or within {@link #NEAR} blocks of its box): the sample each socket keeps, floating in
 * the socket's lit recess on every side that is open and turned to the camera (0.15.0: the column hides the recesses
 * of the far sides), with a soft glow behind it; the nearest {@link #ITEMS} within
 * {@link #NEAR} blocks of the camera. And (0.14.0) the item each unit made of canvas holds, painted large on its
 * canvas, or a faint grid on an empty one ({@link CanvasRender}), under the same rule.</li>
 * </ul>
 * Budgets (CITY.md §1.3, quads a frame for one module): the course line one core quad a face beyond {@link #NEAR}
 * (125 faces for the Eco Dome) and a halo of two more near; the plate about 15 and one a letter; socket items one
 * picture a pass and a glow a face (at most {@link #ITEMS} items of four faces). Beyond the effect range (the far tier
 * of {@link ModuleLook#tier}, 64 quads) only the sweep and a module kind's far look are drawn: the course line would
 * be thinner than a pixel there anyway.
 * <p>
 * Shader-safe as {@link Shapes}: drawn after the world (called from {@code NexusRender}, which skips the shadow pass
 * and is registered with {@code FarDraw}), relative to {@code RenderManager.renderPos*}, quads only, a normal towards
 * the viewer on every vertex, no depth writes, texturing switched on by hand after the state is popped. Animation
 * follows the world time alone and nothing here counts, spawns or sends, so a frame drawn again behind a light gate
 * looks the same. Each part fails on its own: an error is logged once and turns that part off until the game restarts,
 * and the GL state is restored whatever happens.
 */
@SideOnly(Side.CLIENT)
public final class CityRender {

    /** How near (blocks from its box) the camera must be for the inside to be drawn when it is not in it. */
    static final double NEAR = 24;
    /** How far the name plate is seen; the most socket items drawn for one module. */
    static final double PLATE_RANGE = 48;
    static final int ITEMS = 256;
    private static final int IDLE = 0x4A6A80;
    /** The plinth course's light: its height in the shape, how far off the wall, its core and halo half-heights. */
    private static final double COURSE_Y = ModuleLook.COURSE_Y + 0.5, COURSE_OFF = 0.04, COURSE_CORE = 0.035,
        COURSE_HALO = 0.16;
    /**
     * The socket's recess: how far in its picture floats from the block's edge, its half-size, its height (high on
     * the column, so the sample shows over a planter's basin rim: {@link ModuleLook#PICTURE_MID}).
     */
    private static final double RECESS_IN = ModuleLook.RECESS_IN, PICTURE = ModuleLook.PICTURE_HALF,
        RECESS_MID = ModuleLook.PICTURE_MID;

    /** The parts that can fail on their own; {@link #OFF} says which have. */
    static final int COURSE = 0, SWEEP = 1, PLATE = 2, SOCKETS = 3, OWN = 4, CANVAS = 5, ALL = 6;
    private static final String[] NAMES = { "plinth light", "forming sweep", "name plate", "socket items",
        "module visuals", "canvas paintings", "drawing" };
    private static final boolean[] OFF = new boolean[NAMES.length];

    /** Whether a part opened the world state itself (and it must be ended). */
    private static boolean begun;

    /** Each module kind's plinth course, worked out once from its shape. */
    private static final Map<String, int[][]> COURSES = new ConcurrentHashMap<>();
    /** Each module kind's name plates ({@link ModuleLook#plates}), worked out once from its shape. */
    private static final Map<String, List<double[]>> PLATES = new ConcurrentHashMap<>();
    /** How near (blocks across, and up or down) a plate may come to the camera before it is left out. */
    private static final double PLATE_CLEAR = 1.5, PLATE_CLEAR_Y = 4;
    /** Scratch of one draw: the sockets to draw ({slot, x, y, z} and the distance squared). */
    private static int[][] near = new int[64][];
    private static double[] nearD = new double[64];

    private CityRender() {}

    /**
     * Draws a city module; {@code t} is the world time in ticks, {@code fade} the nexus effects' distance fade. One
     * that is not formed shows only its plinth light, dim, along the stretches of its walls that stand.
     */
    public static void draw(TileCityModule m, double t, float fade) {
        if (OFF[ALL]) return;
        try {
            CityFrame f = new CityFrame(m);
            double[] cam = CityFrame.camera(m);
            int tier = ModuleLook.tier(f.inside(cam), f.boxDistance(cam), NEAR, Config.nexusEffectRange);
            boolean nearby = tier == ModuleLook.NEAR;
            if (!m.formed()) {
                if (tier != ModuleLook.FAR && !OFF[COURSE]) try {
                    course(f, m, t, fade, false);
                } catch (Throwable x) {
                    recover();
                    fail(COURSE, x);
                } finally {
                    close();
                }
                return;
            }
            double sweep = m.sweepLevel();
            float appear = sweep == Double.MAX_VALUE ? 1f
                : (float) Math
                    .max(0, Math.min(1, (System.currentTimeMillis() - m.formedAt) / (double) TileMultiblock.SWEEP_MS));

            if (tier != ModuleLook.FAR && !OFF[COURSE]) try {
                course(f, m, t, fade * (0.3f + 0.7f * appear), nearby);
            } catch (Throwable x) {
                recover();
                fail(COURSE, x);
            } finally {
                close();
            }
            if (sweep != Double.MAX_VALUE && !OFF[SWEEP]) try {
                // the Dome Generator's sweep runs up its legs and through its pod rather than through its whole box
                if (m instanceof TileDomeGenerator g) DomeGeneratorRender.sweep(g, sweep, (1 - appear) * fade);
                else sweep(m, sweep, (1 - appear) * fade);
                // the Dual-foil Vault's facade is washed parchment-white as the sweep rises past it (0.14.0)
                if (m instanceof TileDualFoilVault v) VaultRender.sweep(v, sweep, (1 - appear) * fade);
            } catch (Throwable x) {
                recover();
                fail(SWEEP, x);
            } finally {
                close();
            }
            if (!OFF[PLATE]) try {
                for (double[] at : PLATES
                    .computeIfAbsent(m.moduleKey() + "@" + m.shapeName(), k -> ModuleLook.plates(m.build())))
                    plate(f, m, at, fade * appear);
            } catch (Throwable x) {
                recover();
                fail(PLATE, x);
            } finally {
                close();
            }
            if (!OFF[OWN]) try {
                if (m instanceof TileEcoDome d) EcoDomeRender.draw(d, f, t, fade * appear, cam, tier);
                else if (m instanceof TileDomeGenerator g)
                    DomeGeneratorRender.draw(g, f, t, fade * appear, cam, tier, appear);
                else if (m instanceof TileRoot r) RootRender.draw(r, f, t, fade * appear, cam, tier);
                else if (m instanceof TileArcaneCourt ac) ArcaneCourtRender.draw(ac, f, t, fade * appear, tier);
                else if (m instanceof TileBloodCovenant bc)
                    BloodCovenantRender.draw(bc, f, t, fade * appear, cam, tier);
                else if (m instanceof TileHuntingGround hg) HuntingGroundRender.draw(hg, f, t, fade * appear, tier);
                else if (m instanceof TileManaFountain mf) ManaFountainRender.draw(mf, f, t, fade * appear, tier);
                else if (m instanceof TileDualFoilVault v) VaultRender.draw(v, f, t, fade * appear, tier);
                else if (m instanceof TileResonanceGate rg)
                    ResonanceGateRender.draw(rg, f, t, fade * appear, cam, tier, appear);
                else if (m instanceof TileSophonArray sa) SophonRender.draw(sa, f, t, fade * appear, cam, tier, appear);
                else if (m instanceof TileHoloTable ht) HoloTableRender.draw(ht, f, t, fade * appear, tier);
            } catch (Throwable x) {
                recover();
                fail(OWN, x);
            } finally {
                close();
            }
            if (nearby && !OFF[SOCKETS]) try {
                sockets(m, t, fade * appear);
            } catch (Throwable x) {
                recover();
                fail(SOCKETS, x);
            } finally {
                close();
            }
            if (nearby && !OFF[CANVAS]) try {
                CanvasRender.draw(m, f, fade * appear, cam);
            } catch (Throwable x) {
                recover();
                fail(CANVAS, x);
            } finally {
                close();
            }
        } catch (Throwable x) {
            recover();
            close();
            fail(ALL, x);
        }
    }

    /**
     * What a city module shows past the range the rest of it is drawn in (0.12.0): its landmark, if it has one (the
     * Dome Generator's dome, CITY.md §1.3 "landmarks drawn to 512"; 0.14.0 the Resonance Gate's floating spire; 0.15.0
     * the Sophon Array's frame, light point and circuit canopy); nothing for the others. Called by the nexus's
     * renderer for a module beyond its effects' range.
     */
    public static void landmark(TileCityModule m, double t) {
        if (OFF[ALL] || OFF[OWN]) return;
        try {
            if (m instanceof TileDomeGenerator g) DomeGeneratorRender.landmark(g, t);
            else if (m instanceof TileResonanceGate rg) ResonanceGateRender.landmark(rg, t);
            else if (m instanceof TileSophonArray sa) SophonRender.landmark(sa, t);
        } catch (Throwable x) {
            recover();
            fail(OWN, x);
        } finally {
            close();
        }
    }

    /** Whether the module is docked and powered, as the client heard it. */
    static boolean on(TileCityModule m) {
        return "docked".equals(m.clientDock) && m.clientPowered;
    }

    /** Whether the module is docked, as the client heard it. */
    static boolean docked(TileCityModule m) {
        return "docked".equals(m.clientDock);
    }

    // ---- the plinth course

    /**
     * The light along the plinth course, in the dock state: the module's colour docked and powered (a soft pulse
     * running round the building), amber docked without power, dim when not docked, dimmer still when not formed. A
     * bright core and, near, a soft halo. An unformed module lights only the faces whose course block stands (a module
     * core alone, or a module half built, shows no line in the air), each looked up in the world without an array.
     */
    private static void course(CityFrame f, TileCityModule m, double t, float fade, boolean nearby) {
        int[][] cells = COURSES.computeIfAbsent(m.moduleKey() + "@" + m.shapeName(), k -> courseOf(m));
        if (cells.length == 0) return;
        boolean formed = m.formed(), docked = formed && docked(m), on = docked && m.clientPowered;
        World w = m.getWorldObj();
        int code = ShapeKit.partOf(ModuleLook.COURSE);
        Block wall = formed ? null : PartBlocks.block(code);
        int wallMeta = PartBlocks.meta(code);
        if (!formed && (w == null || wall == null)) return;
        int rgb;
        float a;
        if (!formed) {
            rgb = IDLE;
            a = 0.25f;
        } else if (on) {
            rgb = m.moduleColor();
            a = 0.55f;
        } else if (docked) {
            rgb = AMBER;
            a = 0.3f + 0.25f * (float) (0.5 + 0.5 * Math.sin(t * 0.1));
        } else {
            rgb = IDLE;
            a = 0.35f;
        }
        a *= fade;
        if (a <= 0.01f) return;
        int n = cells.length;
        double pulse = t * 0.4 % n;
        Shapes.begin(true);
        Tessellator tes = CityFrame.start();
        for (int[] c : cells) {
            if (!formed) {
                int[] p = m.worldOf(c[0], ModuleLook.COURSE_Y, c[1]);
                if (w.getBlock(p[0], p[1], p[2]) != wall || w.getBlockMetadata(p[0], p[1], p[2]) != wallMeta) continue;
            }
            float k = a;
            if (on) {
                double d = Math.abs(c[4] + 0.5 - pulse);
                d = Math.min(d, n - d);
                k += 0.45f * fade * (float) Math.exp(-d * d / 18);
            }
            double ax, az, bx, bz;
            if (c[2] != 0) {
                ax = bx = (c[2] < 0 ? c[0] : c[0] + 1) + c[2] * COURSE_OFF;
                az = c[1];
                bz = c[1] + 1;
            } else {
                az = bz = (c[3] < 0 ? c[1] : c[1] + 1) + c[3] * COURSE_OFF;
                ax = c[0];
                bx = c[0] + 1;
            }
            tes.setColorRGBA_I(rgb, CityFrame.alpha255(k));
            f.vertex(tes, ax, COURSE_Y - COURSE_CORE, az);
            f.vertex(tes, bx, COURSE_Y - COURSE_CORE, bz);
            f.vertex(tes, bx, COURSE_Y + COURSE_CORE, bz);
            f.vertex(tes, ax, COURSE_Y + COURSE_CORE, az);
            if (nearby) f.softBand(tes, ax, az, bx, bz, COURSE_Y, COURSE_HALO, rgb, 0.45f * k, 0.45f * k);
        }
        tes.draw();
        Shapes.end();
    }

    private static int[][] courseOf(TileCityModule m) {
        List<int[]> l = ModuleLook.course(m.build());
        return l.toArray(new int[0][]);
    }

    /** The forming sweep: a plane of the module's colour rising through its box, its rim bright. */
    private static void sweep(TileCityModule m, double level, float k) {
        if (k <= 0) return;
        int[] b = m.bounds();
        if (b == null) return;
        double camX = RenderManager.renderPosX, camZ = RenderManager.renderPosZ;
        double x0 = b[0] - camX, x1 = b[3] + 1 - camX, z0 = b[2] - camZ, z1 = b[5] + 1 - camZ,
            y = level - RenderManager.renderPosY;
        Shapes.begin(true);
        Shapes.plane(x0, y, z0, x1, z1, m.moduleColor(), 0.3f * k);
        Tessellator tes = CityFrame.start();
        tes.setColorRGBA_I(WHITE, CityFrame.alpha255(0.8f * k));
        CityFrame.lineRel(tes, x0, y, z0, x1, y, z0, 0.15);
        CityFrame.lineRel(tes, x1, y, z0, x1, y, z1, 0.15);
        CityFrame.lineRel(tes, x1, y, z1, x0, y, z1, 0.15);
        CityFrame.lineRel(tes, x0, y, z1, x0, y, z0, 0.15);
        tes.draw();
        Shapes.end();
    }

    // ---- the name plate

    /**
     * A name plate ({@link ModuleLook#plates}: over the portal, or over the controller of a module the lift enters): a
     * faint pane turned to the camera with a line of the module's colour, its name in that colour and its state under
     * it. Fades out over the last 16 of its {@link #PLATE_RANGE} blocks; left out when the camera is right at it (a
     * rider rising through the pod's floor passes its plate under the floor).
     */
    private static void plate(CityFrame f, TileCityModule m, double[] p, float fade) {
        double x = f.x(p[0], p[2]), y = f.y(p[1]), z = f.z(p[0], p[2]);
        if (Math.abs(x) < PLATE_CLEAR && Math.abs(z) < PLATE_CLEAR && Math.abs(y) < PLATE_CLEAR_Y) return;
        double d = Math.sqrt(x * x + y * y + z * z);
        float a = (float) Math.min(1, (PLATE_RANGE - d) / 16) * 0.9f * fade;
        if (a <= 0.05f) return;
        int accent = m.moduleColor();
        String title = EchoText.t("module." + m.moduleKey()), caption = plateCaption(m);
        float scale = 1.5f, px = 1 / 64f;
        int tw = (int) Math.ceil(font().getStringWidth(title) * scale), cw = font().getStringWidth(caption);
        int th = (int) Math.ceil(9 * scale), wdt = Math.max(tw, cw) + 14, hgt = th + 15;
        worldState();
        GL11.glPushMatrix();
        // the matrix is popped on every path, so a failing plate cannot leave the stack one deeper
        try {
            GL11.glTranslated(x, y, z);
            GL11.glRotatef(-RenderManager.instance.playerViewY, 0f, 1f, 0f);
            GL11.glRotatef(RenderManager.instance.playerViewX, 1f, 0f, 0f);
            GL11.glScalef(-px, -px, px);
            GL11.glTranslatef(-wdt / 2f, -hgt / 2f, 0);
            FluxDraw.begin();
            pane(0, 0, wdt, hgt, 0.5f * a);
            rect(1, hgt - 2, wdt - 1, hgt - 1, accent, 0.8f * a);
            rect(3, th + 3, wdt - 3, th + 4, accent, 0.3f * a);
            FluxDraw.end();
            smallCentered(title, wdt / 2.0, 3, scale, accent, a);
            smallCentered(caption, wdt / 2.0, th + 5.5, 1f, on(m) ? WHITE : AMBER, a);
        } finally {
            GL11.glPopMatrix();
        }
    }

    /** The plate's second line: running with its samples, or why it does not run. */
    private static String plateCaption(TileCityModule m) {
        if (on(m)) return EchoText.t(
            "city.plate.on",
            m.sampleCount(),
            m.samples()
                .getSlots());
        String dock = m.clientDock == null || m.clientDock.isEmpty() ? "unformed" : m.clientDock;
        return EchoText.t("dock." + ("docked".equals(dock) ? "no_power" : dock));
    }

    // ---- the socket items

    /**
     * The samples in the sockets: each one floating in the lit recess of every side of its socket that is open (not
     * against a whole cube or another socket) and turned to the camera, bobbing a little, its picture facing out of
     * the recess with a soft glow of the module's colour behind it. The recesses on the far sides are left out
     * (0.15.0): the socket's column stands between them and the camera, so at most two sides of a socket are drawn
     * (the Sophon Array's 144 sockets would else take 1,728 quads). The nearest {@link #ITEMS} sockets within
     * {@link #NEAR} blocks of the camera; blocks' pictures first and then items', each render pass in its colour. A
     * picture that cannot be had here is left out.
     */
    private static void sockets(TileCityModule m, double t, float p) {
        if (p <= 0.02f) return;
        World w = m.getWorldObj();
        if (w == null) return;
        int n = pickSockets(m);
        if (n == 0) return;
        double camX = RenderManager.renderPosX, camY = RenderManager.renderPosY, camZ = RenderManager.renderPosZ;
        int accent = m.moduleColor();
        // the glows first, under the pictures
        Motes.begin();
        for (int i = 0; i < n; i++) {
            int[] s = near[i];
            for (int side = 2; side < 6; side++) {
                if (!facing(s, side, camX, camZ) || !open(w, s[1], s[2], s[3], side)) continue;
                double[] c = recess(s, side, t);
                Motes.add(c[0] - camX, c[1] - camY, c[2] - camZ, 0.2, accent, 0.35f * p);
            }
        }
        Motes.end();

        worldState();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.05f);
        Minecraft mc = Minecraft.getMinecraft();
        Tessellator tes = Tessellator.instance;
        for (int sheet = 0; sheet <= 1; sheet++) {
            mc.getTextureManager()
                .bindTexture(sheet == 0 ? TextureMap.locationBlocksTexture : TextureMap.locationItemsTexture);
            tes.startDrawingQuads();
            CityFrame.normal(tes);
            for (int i = 0; i < n; i++) {
                int[] s = near[i];
                ItemStack st = m.sample(s[0]);
                if (st == null || st.getItem() == null) continue;
                try {
                    if (st.getItem()
                        .getSpriteNumber() != sheet) continue;
                    for (int side = 2; side < 6; side++) {
                        if (!facing(s, side, camX, camZ) || !open(w, s[1], s[2], s[3], side)) continue;
                        double[] c = recess(s, side, t);
                        int[] o = OFFSETS[side];
                        // the picture's right, for someone looking at this side of the socket
                        double rx = o[2], rz = -o[0];
                        picture(tes, st, c[0] - camX, c[1] - camY, c[2] - camZ, rx, rz, PICTURE, 0.95f * p);
                    }
                } catch (RuntimeException e) {
                    // a modded item whose picture cannot be had here shows none
                }
            }
            tes.draw();
        }
    }

    /** The neighbour offsets of the four sides, in vanilla's side order (only 2 to 5 are used). */
    private static final int[][] OFFSETS = { { 0, -1, 0 }, { 0, 1, 0 }, { 0, 0, -1 }, { 0, 0, 1 }, { -1, 0, 0 },
        { 1, 0, 0 } };

    /**
     * Gathers the sockets holding a sample within {@link #NEAR} blocks of the camera into {@link #near}, the nearest
     * {@link #ITEMS} of them first; returns how many.
     */
    private static int pickSockets(TileCityModule m) {
        int slots = m.samples()
            .getSlots(), n = 0;
        double camX = RenderManager.renderPosX, camY = RenderManager.renderPosY, camZ = RenderManager.renderPosZ;
        double reach = NEAR * NEAR;
        for (int slot = 0; slot < slots; slot++) {
            if (m.sample(slot) == null) continue;
            int[] c = m.slotCell(slot);
            if (c == null) continue;
            double dx = c[0] + 0.5 - camX, dy = c[1] + 0.5 - camY, dz = c[2] + 0.5 - camZ;
            double d = dx * dx + dy * dy + dz * dz;
            if (d > reach) continue;
            if (n == near.length) {
                near = Arrays.copyOf(near, n * 2);
                nearD = Arrays.copyOf(nearD, n * 2);
            }
            near[n] = new int[] { slot, c[0], c[1], c[2] };
            nearD[n] = d;
            n++;
        }
        if (n <= ITEMS) return n;
        // the nearest first (a simple insertion sort: the list is short and nearly sorted frame to frame)
        for (int i = 1; i < n; i++) {
            int[] s = near[i];
            double d = nearD[i];
            int j = i - 1;
            while (j >= 0 && nearD[j] > d) {
                near[j + 1] = near[j];
                nearD[j + 1] = nearD[j];
                j--;
            }
            near[j + 1] = s;
            nearD[j + 1] = d;
        }
        return ITEMS;
    }

    /**
     * Whether a socket's side is open to view, so its recess shows a sample: not against a whole opaque cube or
     * another socket (its column stands a sixteenth away and hides the recess).
     */
    private static boolean open(World w, int x, int y, int z, int side) {
        int[] o = OFFSETS[side];
        Block b = w.getBlock(x + o[0], y, z + o[2]);
        if (b.isOpaqueCube()) return false;
        return !(b instanceof BlockFitting && w.getBlockMetadata(x + o[0], y, z + o[2]) == Parts.F_SOCKET);
    }

    /**
     * Whether a socket's side is turned to the camera (0.15.0): the camera lies out past the plane its recess's
     * picture floats in ({@link ModuleLook#socketSideFacing}), so the column does not hide it.
     */
    private static boolean facing(int[] s, int side, double camX, double camZ) {
        int[] o = OFFSETS[side];
        return ModuleLook.socketSideFacing(o[0], o[2], camX - (s[1] + 0.5), camZ - (s[3] + 0.5));
    }

    /** The middle of a socket's recess on a side, where its picture floats this frame: {x, y, z} in the world. */
    private static double[] recess(int[] s, int side, double t) {
        int[] o = OFFSETS[side];
        double bob = 0.025 * Math.sin(t * 0.06 + s[0] * 1.7 + side);
        double out = 0.5 - RECESS_IN;
        return new double[] { s[1] + 0.5 + o[0] * out, s[2] + RECESS_MID + bob, s[3] + 0.5 + o[2] * out };
    }

    /**
     * An item's picture, upright, its right along {@code (rx, rz)}: each render pass in its colour (as the Echo
     * Archive draws its pedestals' books).
     */
    static void picture(Tessellator tes, ItemStack s, double x, double y, double z, double rx, double rz, double h,
        float alpha) {
        Item item = s.getItem();
        int passes = item.requiresMultipleRenderPasses() ? item.getRenderPasses(s.getItemDamage()) : 1;
        double dx = rx * h, dz = rz * h;
        int ai = CityFrame.alpha255(alpha);
        for (int pass = 0; pass < passes; pass++) {
            IIcon ic = item.getIcon(s, pass);
            if (ic == null) continue;
            int rgb = item.getColorFromItemStack(s, pass);
            tes.setColorRGBA_I(rgb & 0xFFFFFF, ai);
            quad(tes, ic, x, y, z, dx, dz, h);
        }
    }

    /** One upright textured quad: top left, bottom left, bottom right, top right as the viewer sees it. */
    static void quad(Tessellator tes, IIcon ic, double x, double y, double z, double dx, double dz, double h) {
        tes.addVertexWithUV(x - dx, y + h, z - dz, ic.getMinU(), ic.getMinV());
        tes.addVertexWithUV(x - dx, y - h, z - dz, ic.getMinU(), ic.getMaxV());
        tes.addVertexWithUV(x + dx, y - h, z + dz, ic.getMaxU(), ic.getMaxV());
        tes.addVertexWithUV(x + dx, y + h, z + dz, ic.getMaxU(), ic.getMinV());
    }

    // ---- state

    /** Opens the world state for a part that draws with its own texture or text ({@link #close} ends it). */
    static void worldState() {
        if (begun) return;
        FluxDraw.worldBegin();
        begun = true;
    }

    /**
     * After an error: ends the motes' batch if it was open, then a quad batch the error broke off (the tessellator
     * refuses to start another while one is open, which would take every later drawing with it).
     */
    static void recover() {
        try {
            Motes.end();
        } catch (Throwable ignored) {
            // the motes' state is popped whatever happens
        }
        try {
            Tessellator.instance.draw();
        } catch (Throwable ignored) {
            // nothing was open
        }
    }

    /** Restores the GL state of whichever part ran, on every path. */
    static void close() {
        try {
            Motes.end();
        } catch (Throwable ignored) {
            // the motes' state is popped whatever happens
        }
        Shapes.end();
        if (begun) {
            begun = false;
            FluxDraw.worldEnd();
        }
    }

    private static void fail(int part, Throwable x) {
        if (OFF[part]) return;
        OFF[part] = true;
        FluxEcho.LOG.warn("The city modules' {} failed and is off until the game restarts", NAMES[part], x);
    }
}
