package com.fluxecho.campus.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityDiggingFX;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

import org.lwjgl.opengl.GL11;

import com.fluxecho.Config;
import com.fluxecho.FluxEcho;
import com.fluxecho.campus.Campus;
import com.fluxecho.campus.PartBlocks;
import com.fluxecho.client.FarDraw;
import com.fluxecho.client.Motes;
import com.fluxecho.client.ShaderCompat;
import com.fluxecho.logic.LaunchPhase;
import com.fluxecho.nexus.ClientTiles;
import com.fluxecho.nexus.TileMultiblock;
import com.fluxecho.nexus.TileNexus;
import com.fluxecho.render.Shapes;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The campus builder's launches made visible (0.10.0 「营造」, the user's 「自动放置然后走特效生成」): every block the
 * builder places leaves the nexus core as a small glowing cube of its own texture, rides an arc of light to its cell
 * with a bright pulse running ahead of it, prints in bottom-up over the last ticks before it lands (the cell's box
 * filling with the block's texture, a scan line at the fill height), and flashes its outline when it lands. Terrain
 * the builder clears breaks with vanilla's break particles and sound, and its cell's outline lingers a moment.
 * <p>
 * It draws only from what {@link BuildClient} keeps (the launches and clears of {@code BUILD_FX}, with absolute world
 * ticks) and the timing of {@link LaunchPhase}, so a frame drawn twice (the light gates draw the world again,
 * {@link FarDraw}) looks the same both times: the render handler keeps no counters and spawns nothing. The client tick
 * notes the tick it first sees each launch and draws it on a timeline of its own from then
 * ({@link LaunchPhase#shownStart}): launches reach the client a few ticks late, so the cargo starts from the core when
 * it is first seen and still lands the tick its block appears. The particles and sounds come from the client
 * tick too, rate-limited: up to 32 clears a tick get a few break particles each, one break sound and one soft placing
 * sound (when a launch is shown landing) at most every five ticks.
 * <p>
 * Shader-safe like {@link Shapes}: drawn after the world, skipped in the shadow pass, relative to the camera
 * ({@code RenderManager.renderPos*}), quads only, every vertex with a normal towards the viewer, additive, no depth
 * writes, the block atlas bound for the textured parts as {@code LibraryRender} does. Each part (arcs, cargo, prints,
 * flashes, clear outlines, particles, sounds) fails on its own: an error is logged once and turns that part off for
 * the session, and the GL state is restored whatever happens. Off with {@code Config.buildEffects}.
 */
@SideOnly(Side.CLIENT)
public final class BuildFx {

    private static final int CYAN = 0x4FE3FF, WHITE = 0xE8F8FF, PULSE = 0xC8F8FF, DUST = 0xFFD9A0;
    /** How much further than the nexus effects the build effects are drawn, in blocks. */
    private static final int EXTRA_RANGE = 64;
    /** The most cells printing or flashing drawn per nexus, and the most clear outlines. */
    private static final int MAX_CELLS = 256, MAX_CLEARS = 128;
    /** Half the size of the flying cargo cube. */
    private static final double CARGO = 0.15;
    /** The part of the arc behind the cargo the pulse lights up. */
    private static final double TAIL = 0.18;
    /**
     * The core's height over the nexus centre where {@code NexusRender} draws it floating in its ring: the top of the
     * base layer (+1) and the ring's 4.5 above it; it bobs by a tenth of a block.
     */
    private static final double CORE_HEIGHT = 5.5, CORE_BOB = 0.1;
    /** Clears a tick that get particles, particles per clear, and how far from the camera they are worth it. */
    private static final int CLEARS_PER_TICK = 32, PARTICLES_MIN = 4, PARTICLES_MAX = 6;
    private static final double PARTICLE_RANGE = 64;
    /** The least ticks between two break sounds, and between two placing sounds. */
    private static final int SOUND_GAP = 5;

    /** The parts that can fail on their own; {@link #OFF} says which have. */
    private static final int ARCS = 0, CARGO_PART = 1, PRINTS = 2, FLASHES = 3, CLEAR_OUTLINES = 4, PARTICLES = 5,
        SOUNDS = 6, ALL = 7;
    private static final String[] NAMES = { "arcs", "cargo", "prints", "landing flashes", "clear outlines",
        "break particles", "sounds", "everything" };
    private static final boolean[] OFF = new boolean[NAMES.length];

    /** The corners of a box (bit 0 = +x, bit 1 = +y, bit 2 = +z) of each face, in vanilla's side order. */
    private static final int[][] FACES = { { 0, 4, 5, 1 }, { 2, 6, 7, 3 }, { 2, 0, 1, 3 }, { 6, 4, 5, 7 },
        { 2, 0, 4, 6 }, { 3, 1, 5, 7 } };
    /** The twelve edges of a box, as corner pairs. */
    private static final int[][] EDGES = { { 0, 1 }, { 1, 5 }, { 5, 4 }, { 4, 0 }, { 2, 3 }, { 3, 7 }, { 7, 6 },
        { 6, 2 }, { 0, 2 }, { 1, 3 }, { 5, 7 }, { 4, 6 } };

    // the render handler's scratch: the part icons looked up in this call, the viewer's normal
    private final IIcon[] icons = new IIcon[64 * 6];
    private final boolean[] looked = new boolean[64 * 6];
    private float nx, ny = 1, nz;
    private final double[] p = new double[3], q = new double[3];
    private final double[] bx = new double[8], by = new double[8], bz = new double[8];

    /**
     * A launch as it is drawn: the cell and part of {@link #launch}, its ticks re-based on the client tick it was
     * first seen ({@link LaunchPhase#shownStart}, {@link LaunchPhase#shownLand}).
     */
    private static final class Shown {

        final BuildClient.Key key;
        final BuildClient.Launch launch;
        final int x, y, z, part;
        final long start, land;

        Shown(BuildClient.Key key, BuildClient.Launch l, long seen) {
            this.key = key;
            this.launch = l;
            x = l.x;
            y = l.y;
            z = l.z;
            part = l.part;
            start = LaunchPhase.shownStart(l.start, l.land, seen);
            land = LaunchPhase.shownLand(l.start, l.land, seen);
        }
    }

    // the client tick's state (the render handler only reads it)
    /** Every launch seen and still drawn or still in {@link BuildClient}'s store, by the launch itself. */
    private IdentityHashMap<BuildClient.Launch, Shown> seen = new IdentityHashMap<>();
    /** The launches still to draw, per nexus, as of the last client tick. */
    private Map<BuildClient.Key, List<Shown>> shown = new HashMap<>();
    /** Per nexus, the last world tick whose landings were handled. */
    private final Map<BuildClient.Key, Long> landedTo = new HashMap<>();
    private long breakSoundAt = Long.MIN_VALUE / 2, placeSoundAt = Long.MIN_VALUE / 2;
    private final Random rand = new Random();
    /** Block ids whose particles could not be made; they get none from then on. */
    private final Set<Integer> noParticles = new HashSet<>();

    private BuildFx() {}

    /** Registers the render, tick and world-unload handlers (the client proxy calls it through CampusClient). */
    public static void register() {
        BuildFx h = new BuildFx();
        MinecraftForge.EVENT_BUS.register(h);
        FMLCommonHandler.instance()
            .bus()
            .register(h);
        FarDraw.add(h::onRenderLast);
    }

    // ---- drawing

    @SubscribeEvent
    public void onRenderLast(RenderWorldLastEvent e) {
        if (ShaderCompat.shadowPass()) return;
        if (!Config.buildEffects || OFF[ALL]) return;
        World w = Minecraft.getMinecraft().theWorld;
        if (w == null) return;
        try {
            draw(w, w.getTotalWorldTime() + e.partialTicks);
        } catch (Throwable t) {
            // every part guards itself; this is only for what lies between them, so it never reaches FarDraw
            fail(ALL, t);
        }
    }

    private void draw(World w, double now) {
        Arrays.fill(looked, false);
        double yaw = Math.toRadians(RenderManager.instance.playerViewY),
            pitch = Math.toRadians(RenderManager.instance.playerViewX);
        // towards the viewer, as Shapes and Motes turn their normals
        nx = (float) (Math.sin(yaw) * Math.cos(pitch));
        ny = (float) Math.sin(pitch);
        nz = (float) (-Math.cos(yaw) * Math.cos(pitch));
        int dim = w.provider.dimensionId;
        double range = Config.nexusEffectRange + EXTRA_RANGE;
        for (TileMultiblock m : ClientTiles.all()) {
            if (!(m instanceof TileNexus n) || m.getWorldObj() != w || m.isInvalid()) continue;
            Campus.View v = n.clientCampus;
            if (v == null || !v.active) continue;
            int[] c = n.centre();
            double dx = c[0] + 0.5 - RenderManager.renderPosX, dy = c[1] + 0.5 - RenderManager.renderPosY,
                dz = c[2] + 0.5 - RenderManager.renderPosZ;
            if (dx * dx + dy * dy + dz * dz > range * range) continue;
            List<Shown> launches = shown.get(new BuildClient.Key(dim, n.xCoord, n.yCoord, n.zCoord));
            List<BuildClient.Clear> clears = BuildClient.clears(dim, n.xCoord, n.yCoord, n.zCoord);
            if (launches != null && !launches.isEmpty()) launches(n, c, launches, now);
            if (!clears.isEmpty() && !OFF[CLEAR_OUTLINES]) clears(clears, now);
        }
    }

    /** Where launches leave from: the floating core of a formed nexus, else the top of the core on its console. */
    private static double[] source(TileNexus n, int[] c, double now) {
        if (n.formed())
            return new double[] { c[0] + 0.5, c[1] + CORE_HEIGHT + Math.sin(now * 0.04) * CORE_BOB, c[2] + 0.5 };
        return new double[] { n.xCoord + 0.5, n.yCoord + 1.0, n.zCoord + 0.5 };
    }

    private static double dist2(Shown l) {
        double dx = l.x + 0.5 - RenderManager.renderPosX, dy = l.y + 0.5 - RenderManager.renderPosY,
            dz = l.z + 0.5 - RenderManager.renderPosZ;
        return dx * dx + dy * dy + dz * dz;
    }

    /** One nexus's launches still showing, nearest the camera first and the latest launch first among equals. */
    private void launches(TileNexus n, int[] c, List<Shown> all, double now) {
        List<Shown> found = new ArrayList<>();
        for (Shown l : all) if (now >= l.start && !LaunchPhase.done(l.land, now)) found.add(l);
        if (found.isEmpty()) return;
        int size = found.size();
        double[] d = new double[size];
        long[] start = new long[size];
        Integer[] order = new Integer[size];
        for (int i = 0; i < size; i++) {
            d[i] = dist2(found.get(i));
            start[i] = found.get(i).start;
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> {
            int k = Double.compare(d[a], d[b]);
            return k != 0 ? k : Long.compare(start[b], start[a]);
        });
        List<Shown> live = new ArrayList<>(size);
        for (Integer i : order) live.add(found.get(i));
        double[] src = source(n, c, now);
        // the arcs and their cargo: launches not landed yet, at most buildArcs of them
        List<Shown> arcs = new ArrayList<>();
        for (Shown l : live) {
            if (arcs.size() >= Config.buildArcs) break;
            if (now < l.land) arcs.add(l);
        }
        List<Shown> cells = live.size() > MAX_CELLS ? live.subList(0, MAX_CELLS) : live;
        if (!arcs.isEmpty() && !OFF[ARCS]) arcs(arcs, src, now);
        if (!arcs.isEmpty() && !OFF[CARGO_PART]) cargo(arcs, src, now);
        if (!OFF[PRINTS]) prints(cells, now);
        if (!OFF[FLASHES]) flashes(cells, now);
    }

    private static LaunchPhase.Arc arc(double[] src, Shown l) {
        return new LaunchPhase.Arc(
            src[0] - RenderManager.renderPosX,
            src[1] - RenderManager.renderPosY,
            src[2] - RenderManager.renderPosZ,
            l.x + 0.5 - RenderManager.renderPosX,
            l.y + 0.5 - RenderManager.renderPosY,
            l.z + 0.5 - RenderManager.renderPosZ);
    }

    /**
     * The arcs: a thin line of light from the core to each cell, faint (0.25), with a bright pulse running along it
     * behind the cargo; the line fades out while the cell prints after the cargo has arrived.
     */
    private void arcs(List<Shown> launches, double[] src, double now) {
        Tessellator t = Tessellator.instance;
        boolean drawing = false;
        try {
            Shapes.begin(true);
            t.startDrawingQuads();
            drawing = true;
            t.setNormal(nx, ny, nz);
            for (Shown l : launches) {
                double arrive = LaunchPhase.arrival(l.start, l.land);
                float fade = now < arrive ? 1f
                    : l.land > arrive ? (float) Math.max(0, 1 - (now - arrive) / (l.land - arrive)) : 0f;
                if (fade <= 0.01f) continue;
                LaunchPhase.Arc a = arc(src, l);
                double len = a.length(8);
                int seg = (int) Math.max(8, Math.min(24, Math.ceil(len / 1.2)));
                double travel = LaunchPhase.travel(l.start, l.land, now);
                boolean flying = now < arrive;
                for (int i = 0; i < seg; i++) {
                    double f0 = i / (double) seg, f1 = (i + 1) / (double) seg;
                    a.point(f0, p);
                    a.point(f1, q);
                    line(t, p, q, 0.05, CYAN, 0.25f * fade, 0.25f * fade);
                    // the pulse: the stretch just behind the cargo, brightest at the cargo
                    if (flying && f1 > travel - TAIL && f0 < travel) {
                        float b0 = (float) Math.max(0, 1 - (travel - f0) / TAIL),
                            b1 = (float) Math.max(0, 1 - (travel - Math.min(f1, travel)) / TAIL);
                        if (f1 > travel) a.point(travel, q);
                        line(t, p, q, 0.09, PULSE, 0.8f * b0, 0.8f * b1);
                    }
                }
            }
            drawing = false;
            t.draw();
        } catch (Throwable x) {
            if (drawing) abandon(t);
            fail(ARCS, x);
        } finally {
            Shapes.end();
        }
    }

    /** The cargo: a small turning cube of the part's own texture, tinted cyan and glowing, riding its arc. */
    private void cargo(List<Shown> launches, double[] src, double now) {
        Tessellator t = Tessellator.instance;
        boolean drawing = false;
        List<double[]> glows = new ArrayList<>();
        try {
            Shapes.begin(true);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(TextureMap.locationBlocksTexture);
            t.startDrawingQuads();
            drawing = true;
            t.setNormal(nx, ny, nz);
            for (Shown l : launches) {
                if (!LaunchPhase.flying(l.start, l.land, now)) continue;
                double[] at = arc(src, l).point(LaunchPhase.travel(l.start, l.land, now), new double[3]);
                glows.add(at);
                double spin = now * 0.25 + (l.x * 31 + l.z * 17) % 7;
                double cs = Math.cos(spin) * CARGO, sn = Math.sin(spin) * CARGO;
                for (int k = 0; k < 8; k++) {
                    double sx = (k & 1) == 0 ? -1 : 1, sy = (k & 2) == 0 ? -1 : 1, sz = (k & 4) == 0 ? -1 : 1;
                    bx[k] = at[0] + sx * cs - sz * sn;
                    by[k] = at[1] + sy * CARGO;
                    bz[k] = at[2] + sx * sn + sz * cs;
                }
                texturedBox(t, l.part, 1, CYAN, 0.9f, 6);
            }
            drawing = false;
            t.draw();
            GL11.glDisable(GL11.GL_TEXTURE_2D);
        } catch (Throwable x) {
            if (drawing) abandon(t);
            fail(CARGO_PART, x);
            return;
        } finally {
            Shapes.end();
        }
        if (glows.isEmpty()) return;
        try {
            Motes.begin();
            for (double[] g : glows) {
                Motes.add(g[0], g[1], g[2], 0.42, CYAN, 0.55f);
                Motes.add(g[0], g[1], g[2], 0.2, WHITE, 0.5f);
            }
        } catch (Throwable x) {
            fail(CARGO_PART, x);
        } finally {
            Motes.end();
        }
    }

    /**
     * The prints: each cell's box filling bottom-up with its part's texture over the print, a bright line round the
     * box at the fill height; once landed the full box fades with the flash, so the cell never looks empty while the
     * placed block is on its way from the server.
     */
    private void prints(List<Shown> launches, double now) {
        Tessellator t = Tessellator.instance;
        boolean drawing = false;
        try {
            Shapes.begin(true);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(TextureMap.locationBlocksTexture);
            t.startDrawingQuads();
            drawing = true;
            t.setNormal(nx, ny, nz);
            for (Shown l : launches) {
                double f = LaunchPhase.print(l.start, l.land, now);
                if (f <= 0) continue;
                float a = now < l.land ? 0.75f : 0.75f * (float) LaunchPhase.flash(l.land, now);
                if (a <= 0.01f) continue;
                cellBox(l, f, 0.004);
                texturedBox(t, l.part, f, Shapes.mix(CYAN, WHITE, 0.25 + 0.6 * f), a, 6);
            }
            drawing = false;
            t.draw();
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            // the scan lines, on the untextured pass
            t.startDrawingQuads();
            drawing = true;
            t.setNormal(nx, ny, nz);
            for (Shown l : launches) {
                if (now >= l.land) continue;
                double f = LaunchPhase.print(l.start, l.land, now);
                if (f <= 0) continue;
                cellBox(l, f, 0.012);
                for (int e = 4; e < 8; e++) edge(t, EDGES[e], 0.05, WHITE, 0.9f);
            }
            drawing = false;
            t.draw();
        } catch (Throwable x) {
            if (drawing) abandon(t);
            fail(PRINTS, x);
        } finally {
            Shapes.end();
        }
    }

    /** The landing flash: the cell's twelve edges and a faint glow on its faces, fading over six ticks. */
    private void flashes(List<Shown> launches, double now) {
        Tessellator t = Tessellator.instance;
        boolean drawing = false;
        try {
            Shapes.begin(true);
            t.startDrawingQuads();
            drawing = true;
            t.setNormal(nx, ny, nz);
            for (Shown l : launches) {
                float a = (float) LaunchPhase.flash(l.land, now);
                if (a <= 0.01f) continue;
                cellBox(l, 1, 0.03);
                int col = Shapes.mix(WHITE, CYAN, 1 - a);
                for (int[] e : EDGES) edge(t, e, 0.07, col, 0.9f * a);
                plainBox(t, CYAN, 0.18f * a);
            }
            drawing = false;
            t.draw();
        } catch (Throwable x) {
            if (drawing) abandon(t);
            fail(FLASHES, x);
        } finally {
            Shapes.end();
        }
    }

    /** The cells cleared lately: a warm outline fading over {@link BuildClient#CLEAR_LINGER} ticks. */
    private void clears(List<BuildClient.Clear> clears, double now) {
        List<BuildClient.Clear> shown = new ArrayList<>();
        for (BuildClient.Clear c : clears) {
            double age = now - c.at;
            if (age >= 0 && age < BuildClient.CLEAR_LINGER) shown.add(c);
        }
        if (shown.isEmpty()) return;
        if (shown.size() > MAX_CLEARS) {
            // the latest ones: they are the ones still bright
            shown = new ArrayList<>(shown.subList(shown.size() - MAX_CLEARS, shown.size()));
        }
        Tessellator t = Tessellator.instance;
        boolean drawing = false;
        try {
            Shapes.begin(true);
            t.startDrawingQuads();
            drawing = true;
            t.setNormal(nx, ny, nz);
            for (BuildClient.Clear c : shown) {
                float a = 0.35f * (float) (1 - (now - c.at) / BuildClient.CLEAR_LINGER);
                if (a <= 0.01f) continue;
                corners(c.x, c.y, c.z, 1, 0.01);
                for (int[] e : EDGES) edge(t, e, 0.05, DUST, a);
            }
            drawing = false;
            t.draw();
        } catch (Throwable x) {
            if (drawing) abandon(t);
            fail(CLEAR_OUTLINES, x);
        } finally {
            Shapes.end();
        }
    }

    // ---- geometry

    /** Puts the cell's box, filled to {@code fill} of its height and grown by {@code grow}, into the corner arrays. */
    private void cellBox(Shown l, double fill, double grow) {
        corners(l.x, l.y, l.z, fill, grow);
    }

    private void corners(int x, int y, int z, double fill, double grow) {
        double x0 = x - grow - RenderManager.renderPosX, x1 = x + 1 + grow - RenderManager.renderPosX;
        double y0 = y - grow - RenderManager.renderPosY, y1 = y + fill + grow - RenderManager.renderPosY;
        double z0 = z - grow - RenderManager.renderPosZ, z1 = z + 1 + grow - RenderManager.renderPosZ;
        for (int k = 0; k < 8; k++) {
            bx[k] = (k & 1) == 0 ? x0 : x1;
            by[k] = (k & 2) == 0 ? y0 : y1;
            bz[k] = (k & 4) == 0 ? z0 : z1;
        }
    }

    /**
     * The box in the corner arrays with the part's icons on its faces; the side faces show the bottom {@code fill} of
     * their icon (as vanilla draws a partial block), the top and bottom the whole icon.
     */
    private void texturedBox(Tessellator t, int part, double fill, int rgb, float a, int faces) {
        t.setColorRGBA_I(rgb & 0xFFFFFF, (int) (Math.max(0, Math.min(1, a)) * 255));
        for (int s = 0; s < faces; s++) {
            IIcon ic = icon(part, s);
            if (ic == null) continue;
            double u0 = ic.getMinU(), u1 = ic.getMaxU(), v0 = ic.getMinV(), v1 = ic.getMaxV();
            // side faces: corners 0 and 3 are at the top, 1 and 2 at the bottom
            double vt = s <= 1 ? v0 : v1 - (v1 - v0) * Math.max(0, Math.min(1, fill));
            int[] f = FACES[s];
            t.addVertexWithUV(bx[f[0]], by[f[0]], bz[f[0]], u0, vt);
            t.addVertexWithUV(bx[f[1]], by[f[1]], bz[f[1]], u0, v1);
            t.addVertexWithUV(bx[f[2]], by[f[2]], bz[f[2]], u1, v1);
            t.addVertexWithUV(bx[f[3]], by[f[3]], bz[f[3]], u1, vt);
        }
    }

    /** The box in the corner arrays as six plain faces. */
    private void plainBox(Tessellator t, int rgb, float a) {
        t.setColorRGBA_I(rgb & 0xFFFFFF, (int) (Math.max(0, Math.min(1, a)) * 255));
        for (int[] f : FACES) for (int k : f) t.addVertex(bx[k], by[k], bz[k]);
    }

    /** One edge of the box in the corner arrays, as a thin band of light. */
    private void edge(Tessellator t, int[] e, double w, int rgb, float a) {
        p[0] = bx[e[0]];
        p[1] = by[e[0]];
        p[2] = bz[e[0]];
        q[0] = bx[e[1]];
        q[1] = by[e[1]];
        q[2] = bz[e[1]];
        line(t, p, q, w, rgb, a, a);
    }

    /**
     * A light string from {@code a} to {@code b} (camera-relative) into the open quad batch: a band of width
     * {@code w} turned to the camera, as {@link Shapes#string} draws it.
     */
    private static void line(Tessellator t, double[] a, double[] b, double w, int rgb, float a0, float a1) {
        double dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
        double mx = (a[0] + b[0]) / 2, my = (a[1] + b[1]) / 2, mz = (a[2] + b[2]) / 2;
        double sx = dy * mz - dz * my, sy = dz * mx - dx * mz, sz = dx * my - dy * mx;
        double len = Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (len < 1e-9) return;
        sx = sx / len * w / 2;
        sy = sy / len * w / 2;
        sz = sz / len * w / 2;
        t.setColorRGBA_I(rgb & 0xFFFFFF, (int) (Math.max(0, Math.min(1, a0)) * 255));
        t.addVertex(a[0] - sx, a[1] - sy, a[2] - sz);
        t.addVertex(a[0] + sx, a[1] + sy, a[2] + sz);
        t.setColorRGBA_I(rgb & 0xFFFFFF, (int) (Math.max(0, Math.min(1, a1)) * 255));
        t.addVertex(b[0] + sx, b[1] + sy, b[2] + sz);
        t.addVertex(b[0] - sx, b[1] - sy, b[2] - sz);
    }

    /** The part's icon for a side (its bottom icon when that side has none), looked up once per frame; or null. */
    private IIcon icon(int part, int side) {
        if (part < 0 || part >= 64) return null;
        int k = part * 6 + side;
        if (!looked[k]) {
            looked[k] = true;
            IIcon ic = null;
            try {
                Block b = PartBlocks.block(part);
                if (b != null && b != Blocks.air) {
                    ic = b.getIcon(side, PartBlocks.meta(part));
                    if (ic == null) ic = b.getIcon(0, PartBlocks.meta(part));
                }
            } catch (RuntimeException e) {
                ic = null;
            }
            icons[k] = ic;
        }
        return icons[k];
    }

    /** Ends a batch an error broke off, so the tessellator is not left drawing (vanilla would fail next). */
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
        FluxEcho.LOG.warn("The campus build effects' {} failed and are off until the game restarts", NAMES[part], t);
    }

    // ---- the client tick: particles and sounds

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        World w = mc.theWorld;
        if (w == null || !Config.buildEffects) {
            forget();
            return;
        }
        long now = w.getTotalWorldTime();
        if (!OFF[ALL]) try {
            track(w, now);
        } catch (Throwable x) {
            forget();
            fail(ALL, x);
        }
        if (!OFF[PARTICLES]) try {
            particles(mc, w, now);
        } catch (Throwable x) {
            fail(PARTICLES, x);
        }
        if (!OFF[SOUNDS]) try {
            landings(mc, w, now);
        } catch (Throwable x) {
            fail(SOUNDS, x);
        }
    }

    @SubscribeEvent
    public void onUnload(WorldEvent.Unload e) {
        if (e.world.isRemote) forget();
    }

    /**
     * Forgets the launches and the sound times: the next world's clock starts elsewhere (a sound gate set on a world
     * further on in time would hold back every sound in the next one until its clock caught up).
     */
    private void forget() {
        landedTo.clear();
        if (!seen.isEmpty()) seen = new IdentityHashMap<>();
        if (!shown.isEmpty()) shown = new HashMap<>();
        breakSoundAt = Long.MIN_VALUE / 2;
        placeSoundAt = Long.MIN_VALUE / 2;
    }

    /**
     * Notes the tick each new launch is first seen and works out the launches to draw per nexus: those in
     * {@link BuildClient}'s store, and those it has let go of while their flash still shows. Packets are handled at the
     * start of the client tick, so a launch is noted before any
     * frame draws it.
     */
    private void track(World w, long now) {
        List<BuildClient.Site> sites = BuildClient.sites(w.provider.dimensionId);
        if (sites.isEmpty() && seen.isEmpty()) {
            if (!shown.isEmpty()) shown = new HashMap<>();
            return;
        }
        IdentityHashMap<BuildClient.Launch, Shown> next = new IdentityHashMap<>();
        Map<BuildClient.Key, List<Shown>> lists = new HashMap<>();
        for (BuildClient.Site s : sites) {
            for (BuildClient.Launch l : s.launches) {
                Shown sh = seen.get(l);
                if (sh == null) sh = new Shown(s.key, l, now);
                next.put(l, sh);
                if (!LaunchPhase.done(sh.land, now)) add(lists, sh);
            }
        }
        for (Shown sh : seen.values()) {
            if (next.containsKey(sh.launch) || LaunchPhase.done(sh.land, now) || sh.key.dim != w.provider.dimensionId)
                continue;
            next.put(sh.launch, sh);
            add(lists, sh);
        }
        seen = next;
        shown = lists;
    }

    private static void add(Map<BuildClient.Key, List<Shown>> lists, Shown sh) {
        List<Shown> l = lists.get(sh.key);
        if (l == null) lists.put(sh.key, l = new ArrayList<>());
        l.add(sh);
    }

    /**
     * Break particles for the clears that came in: a few vanilla digging particles of the block that was there (not
     * {@code addBlockDestroyEffects}, which spawns 64), and one quiet break sound at the nearest at most every five
     * ticks.
     */
    private void particles(Minecraft mc, World w, long now) {
        List<BuildClient.Clear> cs = BuildClient.drainClears(CLEARS_PER_TICK);
        if (cs.isEmpty()) return;
        Entity view = mc.renderViewEntity != null ? mc.renderViewEntity : mc.thePlayer;
        int dim = w.provider.dimensionId;
        BuildClient.Clear nearest = null;
        double best = Double.MAX_VALUE;
        for (BuildClient.Clear c : cs) {
            if (c.dim != dim) continue;
            double d2 = view == null ? 0 : view.getDistanceSq(c.x + 0.5, c.y + 0.5, c.z + 0.5);
            if (d2 > PARTICLE_RANGE * PARTICLE_RANGE || noParticles.contains(c.block)) continue;
            Block b = Block.getBlockById(c.block);
            if (b == null || b == Blocks.air || b.getMaterial() == Material.air) continue;
            try {
                if (b.getIcon(0, c.meta) == null) {
                    noParticles.add(c.block);
                    continue;
                }
                int n = PARTICLES_MIN + rand.nextInt(PARTICLES_MAX - PARTICLES_MIN + 1);
                for (int i = 0; i < n; i++) {
                    double x = c.x + 0.15 + rand.nextDouble() * 0.7, y = c.y + 0.15 + rand.nextDouble() * 0.7,
                        z = c.z + 0.15 + rand.nextDouble() * 0.7;
                    mc.effectRenderer.addEffect(
                        new EntityDiggingFX(w, x, y, z, x - c.x - 0.5, y - c.y - 0.5, z - c.z - 0.5, b, c.meta)
                            .applyColourMultiplier(c.x, c.y, c.z));
                }
            } catch (RuntimeException x) {
                // a block whose particles cannot be made here (a modded block wanting its world) gets none
                noParticles.add(c.block);
                continue;
            }
            if (d2 < best) {
                best = d2;
                nearest = c;
            }
        }
        // a clock that went back (a server correcting the time) does not hold the sound back
        if (now < breakSoundAt) breakSoundAt = Long.MIN_VALUE / 2;
        if (nearest == null || now - breakSoundAt < SOUND_GAP || OFF[SOUNDS]) return;
        breakSoundAt = now;
        try {
            Block b = Block.getBlockById(nearest.block);
            Block.SoundType s = b.stepSound;
            w.playSound(
                nearest.x + 0.5,
                nearest.y + 0.5,
                nearest.z + 0.5,
                s.getBreakSound(),
                (s.getVolume() + 1f) / 2f * 0.45f,
                s.getPitch() * 0.8f,
                false);
        } catch (Throwable x) {
            fail(SOUNDS, x);
        }
    }

    /**
     * A soft placing sound when cells land: of the launches whose shown landing passed since the last client tick (per
     * nexus), the nearest, at most one every five ticks. A nexus seen for the first time only starts the count.
     */
    private void landings(Minecraft mc, World w, long now) {
        Entity view = mc.renderViewEntity != null ? mc.renderViewEntity : mc.thePlayer;
        Shown nearest = null;
        double best = Double.MAX_VALUE;
        for (Map.Entry<BuildClient.Key, List<Shown>> e : shown.entrySet()) {
            Long last = landedTo.put(e.getKey(), now);
            if (last == null || last >= now) continue;
            for (Shown l : e.getValue()) {
                if (l.land <= last || l.land > now) continue;
                double d2 = view == null ? 0 : view.getDistanceSq(l.x + 0.5, l.y + 0.5, l.z + 0.5);
                if (d2 < best) {
                    best = d2;
                    nearest = l;
                }
            }
        }
        landedTo.keySet()
            .retainAll(shown.keySet());
        if (now < placeSoundAt) placeSoundAt = Long.MIN_VALUE / 2;
        if (nearest == null || now - placeSoundAt < SOUND_GAP) return;
        Block b = PartBlocks.block(nearest.part);
        if (b == null || b == Blocks.air) return;
        placeSoundAt = now;
        Block.SoundType s = b.stepSound;
        w.playSound(
            nearest.x + 0.5,
            nearest.y + 0.5,
            nearest.z + 0.5,
            s.func_150496_b(),
            (s.getVolume() + 1f) / 2f * 0.3f,
            s.getPitch() * 1.15f,
            false);
    }
}
