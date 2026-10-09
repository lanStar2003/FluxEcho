package com.fluxecho.gate.client;

import java.lang.reflect.Field;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.culling.Frustrum;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.EmptyChunk;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkEvent;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import com.fluxecho.Config;
import com.fluxecho.FluxEcho;
import com.fluxecho.client.ShaderCompat;
import com.fluxecho.gate.GateNet;
import com.fluxecho.gate.TileLightGate;
import com.fluxecho.logic.GateGeometry;
import com.fluxecho.logic.MirrorSection;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.simpleimpl.IMessage;

/**
 * The light gates on the client.
 * <ul>
 * <li>Keeps a {@link Mirror} of what lies behind each gate the server shows it.</li>
 * <li>Draws it: the mirror, turned and moved through the gate, goes into an off-screen picture with the player's own
 * camera, cut at the gate's pane; the membrane then shows that picture's pixels where it covers the screen, so it is
 * a hole into the other side. Off-screen, so a shader pack's pipeline is left alone.</li>
 * <li>Walking through: as soon as the player steps into a gate (or the server says so) the last picture holds the
 * screen with a ripple while the world changes, and fades once the new side is drawn.</li>
 * </ul>
 */
public final class GateClient {

    private static final Map<Long, Mirror> MIRRORS = new HashMap<>();
    /** Chunks filled in ahead of the server, until the server's own replace them. */
    private static final Map<Long, Chunk> PREFILLED = new HashMap<>();
    private static Field chunkList;
    private static World seen;
    private static Transit transit;
    private static boolean broken;
    private static double[] lastFeet;

    private static Framebuffer fbo;
    /** The gate whose view is in the picture, so a step into it can keep showing it. */
    private static long pictured = Long.MIN_VALUE;

    private static final FloatBuffer MATRIX = BufferUtils.createFloatBuffer(16);
    private static final float[] MODELVIEW = new float[16], PROJECTION = new float[16];
    private static final IntBuffer INTS = BufferUtils.createIntBuffer(16);
    private static final DoubleBuffer PLANE = BufferUtils.createDoubleBuffer(4);

    private static final class Transit {

        final long start = System.currentTimeMillis();
        final World from;
        final Mirror mirror;
        final boolean picture;
        boolean changed, filled;
        int frames;
        long fadeAt = -1;

        Transit(World from, Mirror mirror, boolean picture) {
            this.from = from;
            this.mirror = mirror;
            this.picture = picture;
        }
    }

    private GateClient() {}

    public static void register() {
        GateClient c = new GateClient();
        MinecraftForge.EVENT_BUS.register(c);
        FMLCommonHandler.instance()
            .bus()
            .register(c);
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    static long chunkKey(int cx, int cz) {
        return (long) cx << 32 ^ (cz & 0xFFFFFFFFL);
    }

    private static List<TileLightGate> gates(World w) {
        List<TileLightGate> l = new ArrayList<>();
        synchronized (TileLightGate.CLIENT) {
            for (TileLightGate t : TileLightGate.CLIENT) if (t.getWorldObj() == w && !t.isInvalid()) l.add(t);
        }
        return l;
    }

    // ---- ticks

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (e.phase == TickEvent.Phase.START) {
            if (mc.theWorld != seen) worldChanged(mc.theWorld);
            IMessage m;
            while ((m = GateNet.INBOX.poll()) != null) handle(mc, m);
            prefill(mc);
        } else notice(mc);
    }

    private static void worldChanged(World w) {
        Mirror keep = transit != null ? transit.mirror : null;
        for (Mirror m : MIRRORS.values()) if (m != keep) m.free();
        MIRRORS.clear();
        PREFILLED.clear();
        if (transit != null && w != transit.from) transit.changed = true;
        seen = w;
        lastFeet = null;
        pictured = Long.MIN_VALUE;
    }

    private static void handle(Minecraft mc, IMessage msg) {
        if (mc.theWorld == null) return;
        int dim = mc.theWorld.provider.dimensionId;
        if (msg instanceof GateNet.View v) {
            if (v.dim != dim) return;
            long k = key(v.x, v.y, v.z);
            GateGeometry.Gate source = new GateGeometry.Gate(v.sx, v.sy, v.sz, v.sourceFacing);
            Mirror old = MIRRORS.get(k);
            if (old != null && old.sameAs(v.sourceDim, source)) return;
            if (old != null && (transit == null || transit.mirror != old)) old.free();
            MIRRORS.put(
                k,
                new Mirror(v.dim, new GateGeometry.Gate(v.x, v.y, v.z, v.facing), v.sourceDim, source, v.box()));
        } else if (msg instanceof GateNet.Section s) {
            Mirror m = s.dim == dim ? MIRRORS.get(key(s.x, s.y, s.z)) : null;
            if (m != null) m.put(s.key, MirrorSection.decode(s.data));
        } else if (msg instanceof GateNet.Drop d) {
            if (d.dim != dim) return;
            Mirror m = MIRRORS.remove(key(d.x, d.y, d.z));
            if (m != null && (transit == null || transit.mirror != m)) m.free();
        } else if (msg instanceof GateNet.Transit t) {
            if (transit == null && t.dim == dim) begin(mc, key(t.x, t.y, t.z));
        }
    }

    /** The player stepped into the gate with this key: hold the picture until the other side is drawn. */
    private static void begin(Minecraft mc, long gate) {
        Mirror m = MIRRORS.get(gate);
        transit = new Transit(mc.theWorld, m, m != null && pictured == gate && fbo != null);
    }

    /** Notices a step into a gate on the client's own tick, before the server's word arrives. */
    private static void notice(Minecraft mc) {
        EntityPlayer p = mc.thePlayer;
        if (p == null || mc.theWorld == null || transit != null) {
            lastFeet = null;
            return;
        }
        double x = p.posX, y = p.boundingBox.minY, z = p.posZ;
        double[] l = lastFeet;
        lastFeet = new double[] { x, y, z };
        if (l == null) return;
        for (TileLightGate t : gates(mc.theWorld)) {
            if (!t.linked || Math.abs(t.xCoord + 0.5 - x) > 8 || Math.abs(t.zCoord + 0.5 - z) > 8) continue;
            if (t.gate()
                .entered(l[0], l[1], l[2], x, y, z)) {
                begin(mc, key(t.xCoord, t.yCoord, t.zCoord));
                return;
            }
        }
    }

    /** In the new world, before the server's chunks come: the far side as the gate showed it. */
    private static void prefill(Minecraft mc) {
        Transit t = transit;
        WorldClient w = mc.theWorld;
        if (t == null || !t.changed || t.filled || t.mirror == null || w == null) return;
        if (w.provider.dimensionId != t.mirror.sourceDim) return;
        t.filled = true;
        if (!Config.gatePrefill) return;
        try {
            PREFILLED.putAll(t.mirror.fillInto(w));
        } catch (Throwable ex) {
            FluxEcho.LOG.warn("Could not fill the far side of a light gate in ahead of the server", ex);
        }
    }

    /**
     * The server's chunk replaced one filled in ahead: the client's chunk list still holds the old one (it only drops
     * a chunk when told to unload it), so it goes here.
     */
    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load e) {
        if (PREFILLED.isEmpty() || !e.world.isRemote) return;
        Chunk c = e.getChunk();
        Chunk old = PREFILLED.remove(chunkKey(c.xPosition, c.zPosition));
        if (old == null || old == c) return;
        try {
            if (chunkList == null) for (Field f : ChunkProviderClient.class.getDeclaredFields()) {
                if (List.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    chunkList = f;
                    break;
                }
            }
            if (chunkList != null && e.world.getChunkProvider() instanceof ChunkProviderClient p)
                ((List<?>) chunkList.get(p)).remove(old);
        } catch (Throwable ignored) {}
    }

    private static boolean playerChunkReady(Minecraft mc) {
        if (mc.thePlayer == null || mc.theWorld == null) return false;
        Chunk c = mc.theWorld.getChunkFromBlockCoords(
            MathHelper.floor_double(mc.thePlayer.posX),
            MathHelper.floor_double(mc.thePlayer.posZ));
        return !(c instanceof EmptyChunk);
    }

    private static void end() {
        Transit t = transit;
        transit = null;
        if (t != null && t.mirror != null && !MIRRORS.containsValue(t.mirror)) t.mirror.free();
    }

    // ---- drawing

    @SubscribeEvent
    public void onRenderLast(RenderWorldLastEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || ShaderCompat.shadowPass()) return;
        List<TileLightGate> list = gates(mc.theWorld);
        if (list.isEmpty()) return;
        double cx = RenderManager.renderPosX, cy = RenderManager.renderPosY, cz = RenderManager.renderPosZ;
        float time = (mc.theWorld.getTotalWorldTime() % 24000L) + e.partialTicks;
        readMatrices();
        Frustrum view = new Frustrum();
        view.setPosition(cx, cy, cz);
        int budget = 3;
        for (TileLightGate t : list) {
            double dx = t.xCoord + 0.5 - cx, dy = t.yCoord + 1.5 - cy, dz = t.zCoord + 0.5 - cz;
            if (dx * dx + dy * dy + dz * dz > 96 * 96 || !view.isBoundingBoxInFrustum(t.getRenderBoundingBox()))
                continue;
            GateGeometry.Gate g = t.gate();
            long k = key(t.xCoord, t.yCoord, t.zCoord);
            Mirror m = t.linked ? MIRRORS.get(k) : null;
            double front = g.front(cx, cz);
            boolean drawn = false;
            if (m != null && Config.gateLiveView
                && !broken
                && transit == null
                && front > 0
                && OpenGlHelper.isFramebufferEnabled()) {
                budget -= m.build(Math.max(0, budget));
                if (m.hasAny()) try {
                    live(mc, m, g, cx, cy, cz, e.partialTicks);
                    pictured = k;
                    drawn = true;
                } catch (Throwable ex) {
                    broken = true;
                    FluxEcho.LOG.error(
                        "Drawing the view through a light gate failed; the gates only glow from now on (gates.liveView in config/fluxecho.cfg turns the view off for good)",
                        ex);
                }
            }
            if (!drawn) GateDraw.membrane(g, cx, cy, cz, time, t.linked, front);
            GateDraw.frame(g, cx, cy, cz, time, t.linked);
        }
    }

    private static void readMatrices() {
        MATRIX.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MATRIX);
        MATRIX.get(MODELVIEW);
        MATRIX.clear();
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, MATRIX);
        MATRIX.get(PROJECTION);
    }

    private static int boundFramebuffer() {
        return GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
    }

    private static void ensureFbo(int w, int h) {
        if (fbo != null && fbo.framebufferWidth == w && fbo.framebufferHeight == h) return;
        int prev = boundFramebuffer();
        if (fbo == null) fbo = new Framebuffer(w, h, true);
        else fbo.createBindFramebuffer(w, h);
        fbo.setFramebufferFilter(GL11.GL_LINEAR);
        OpenGlHelper.func_153171_g(OpenGlHelper.field_153198_e, prev);
    }

    private static void live(Minecraft mc, Mirror m, GateGeometry.Gate g, double cx, double cy, double cz, float pt) {
        int w = mc.displayWidth, h = mc.displayHeight;
        if (w <= 0 || h <= 0) return;
        ensureFbo(w, h);
        picture(mc, m, g, cx, cy, cz, pt);
        membrane(g, cx, cy, cz);
    }

    /** The mirror, through the gate, from the player's camera, into the off-screen picture. */
    private static void picture(Minecraft mc, Mirror m, GateGeometry.Gate g, double cx, double cy, double cz,
        float pt) {
        int prevFbo = boundFramebuffer();
        INTS.clear();
        GL11.glGetInteger(GL11.GL_VIEWPORT, INTS);
        int vx = INTS.get(0), vy = INTS.get(1), vw = INTS.get(2), vh = INTS.get(3);
        int prevProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        GL11.glPushAttrib(
            GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_LIGHTING_BIT
                | GL11.GL_CURRENT_BIT
                | GL11.GL_FOG_BIT
                | GL11.GL_TRANSFORM_BIT
                | GL11.GL_TEXTURE_BIT);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        try {
            fbo.bindFramebuffer(true);
            Vec3 sky = mc.theWorld.getSkyColor(mc.renderViewEntity, pt);
            float r = (float) sky.xCoord, gr = (float) sky.yCoord, b = (float) sky.zCoord;
            if (m.sourceDim == Config.gateDimension) {
                r = r * 0.75f + 0.04f;
                gr = gr * 0.75f + 0.15f;
                b = b * 0.75f + 0.2f;
            }
            GL11.glClearColor(r, gr, b, 1f);
            GL11.glClearDepth(1.0);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
            if (prevProgram != 0) GL20.glUseProgram(0);
            GL11.glDisable(GL11.GL_FOG);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glDepthMask(true);
            GL11.glEnable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glAlphaFunc(GL11.GL_GREATER, 0.1f);
            GL11.glShadeModel(GL11.GL_SMOOTH);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glColor4f(1f, 1f, 1f, 1f);
            // keep only what lies behind the pane
            int fx = GateGeometry.dx(g.facing), fz = GateGeometry.dz(g.facing);
            PLANE.clear();
            PLANE.put(-fx)
                .put(0)
                .put(-fz)
                .put(0.02 + fx * (g.cx() - cx) + fz * (g.cz() - cz));
            PLANE.flip();
            GL11.glClipPlane(GL11.GL_CLIP_PLANE0, PLANE);
            GL11.glEnable(GL11.GL_CLIP_PLANE0);
            mc.entityRenderer.enableLightmap(pt);
            mc.getTextureManager()
                .bindTexture(TextureMap.locationBlocksTexture);
            GL11.glTranslated(g.cx() - cx, g.y - cy, g.cz() - cz);
            GL11.glRotatef(GateGeometry.glDegrees(GateGeometry.turns(m.source, g)), 0f, 1f, 0f);
            m.draw(0);
            GL11.glEnable(GL11.GL_BLEND);
            OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
            GL11.glDepthMask(false);
            m.draw(1);
            mc.entityRenderer.disableLightmap(pt);
            GL11.glDisable(GL11.GL_CLIP_PLANE0);
        } finally {
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            if (prevProgram != 0) GL20.glUseProgram(prevProgram);
            GL11.glPopAttrib();
            OpenGlHelper.func_153171_g(OpenGlHelper.field_153198_e, prevFbo);
            GL11.glViewport(vx, vy, vw, vh);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
        }
    }

    /** The membrane showing the picture: each point gets the pixel it covers, so it is a window into it. */
    private static void membrane(GateGeometry.Gate g, double cx, double cy, double cz) {
        GL11.glPushAttrib(
            GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_LIGHTING_BIT
                | GL11.GL_CURRENT_BIT
                | GL11.GL_TEXTURE_BIT);
        try {
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_FOG);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            OpenGlHelper
                .setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, ShaderCompat.packInUse() ? 0f : 240f);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, fbo.framebufferTexture);
            GL11.glColor4f(1f, 1f, 1f, 1f);
            if (onPane(g, cx, cy, cz)) fullScreen();
            else grid(g, cx, cy, cz);
        } finally {
            GL11.glPopAttrib();
            GL11.glEnable(GL11.GL_TEXTURE_2D);
        }
    }

    /** The camera is right at the pane: the near plane would cut the membrane, so the picture fills the screen. */
    private static boolean onPane(GateGeometry.Gate g, double cx, double cy, double cz) {
        return g.front(cx, cz) < 0.35 && Math.abs(g.across(cx, cz)) <= GateGeometry.HALF_WIDTH
            && cy >= g.y - 0.5
            && cy <= g.y + GateGeometry.HEIGHT + 0.5;
    }

    /**
     * The membrane as a grid with screen coordinates. Cells reaching behind the camera are left out: from beside the
     * gate they are off the screen anyway.
     */
    private static void grid(GateGeometry.Gate g, double cx, double cy, double cz) {
        int n = 12;
        double lx = -GateGeometry.dz(g.facing), lz = GateGeometry.dx(g.facing);
        double[][][] uv = new double[n + 1][n + 1][];
        double[][][] pos = new double[n + 1][n + 1][];
        for (int i = 0; i <= n; i++) for (int j = 0; j <= n; j++) {
            double s = -GateGeometry.HALF_WIDTH + 2 * GateGeometry.HALF_WIDTH * i / n, v = GateGeometry.HEIGHT * j / n;
            double x = g.cx() - cx + s * lx, y = g.y - cy + v, z = g.cz() - cz + s * lz;
            double[] p = GateGeometry.project(PROJECTION, MODELVIEW, x, y, z);
            if (p[2] > 0.01) uv[i][j] = new double[] { (p[0] + 1) / 2, (p[1] + 1) / 2 };
            pos[i][j] = new double[] { x, y, z };
        }
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.setNormal(GateGeometry.dx(g.facing), 0, GateGeometry.dz(g.facing));
        t.setColorOpaque_I(0xFFFFFF);
        for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) {
            if (uv[i][j] == null || uv[i + 1][j] == null || uv[i + 1][j + 1] == null || uv[i][j + 1] == null) continue;
            vertex(t, pos[i][j], uv[i][j]);
            vertex(t, pos[i + 1][j], uv[i + 1][j]);
            vertex(t, pos[i + 1][j + 1], uv[i + 1][j + 1]);
            vertex(t, pos[i][j + 1], uv[i][j + 1]);
        }
        t.draw();
    }

    private static void vertex(Tessellator t, double[] p, double[] uv) {
        t.addVertexWithUV(p[0], p[1], p[2], uv[0], uv[1]);
    }

    private static void fullScreen() {
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0, 1, 0, 1, -1, 1);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.addVertexWithUV(0, 0, 0, 0, 0);
        t.addVertexWithUV(1, 0, 0, 1, 0);
        t.addVertexWithUV(1, 1, 0, 1, 1);
        t.addVertexWithUV(0, 1, 0, 0, 1);
        t.draw();
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
    }

    /** Over everything, while walking through. */
    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.END || transit == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        Transit t = transit;
        long now = System.currentTimeMillis();
        if (mc.theWorld != null && mc.theWorld != t.from) t.changed = true;
        if (t.changed) t.frames++;
        boolean ready = t.changed && t.frames > 12 && playerChunkReady(mc);
        boolean late = now - t.start > (t.changed ? 4000 : 1500);
        if (t.fadeAt < 0 && (ready || late)) t.fadeAt = now;
        float alpha = t.fadeAt < 0 ? 1f : 1f - (now - t.fadeAt) / 350f;
        if (alpha <= 0f) {
            end();
            return;
        }
        try {
            GateDraw.overlay(
                mc.displayWidth,
                mc.displayHeight,
                t.picture && fbo != null ? fbo.framebufferTexture : -1,
                alpha,
                (now - t.start) / 1000f);
        } catch (Throwable ex) {
            FluxEcho.LOG.error("Drawing the light gate's ripple failed", ex);
            end();
        }
    }
}
