package com.fluxecho.gate.client;

import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.culling.Frustrum;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.Vec3;
import net.minecraft.world.IWorldAccess;
import net.minecraft.world.World;
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

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.simpleimpl.IMessage;

/**
 * The light gates on the client. Both sides of every gate near the player are loaded in the player's own world (the
 * server keeps them so), and a gate's room is in the same world as the gate.
 * <ul>
 * <li>Keeps a {@link FarView} of what lies behind each gate near the player, built from the world itself and built
 * again where a block changes.</li>
 * <li>Draws it: the view, turned and moved through the gate, goes into an off-screen picture with the player's own
 * camera, cut at the gate's pane; the membrane then shows that picture's pixels where it covers the screen, so it is
 * a hole into the other side. Off-screen, so Angelica's renderer and a shader pack's pipeline are left alone: the
 * picture only uses plain OpenGL that Angelica's state tracking follows.</li>
 * <li>Walking through moves the player within the same world, so nothing reloads; the picture holds the screen with a
 * ripple for a moment, while the renderer builds what it has not drawn yet at the new place.</li>
 * </ul>
 */
public final class GateClient {

    private static final Map<Long, FarView> VIEWS = new HashMap<>();
    private static World seen;
    private static Transit transit;
    private static boolean broken;
    private static double[] lastFeet;
    private static int ticks;

    private static Framebuffer fbo;
    /** The gate whose view is in the picture, so a step into it can keep showing it. */
    private static long pictured = Long.MIN_VALUE;

    private static final FloatBuffer MATRIX = BufferUtils.createFloatBuffer(16);
    private static final float[] MODELVIEW = new float[16], PROJECTION = new float[16];
    private static final IntBuffer INTS = BufferUtils.createIntBuffer(16);
    private static final DoubleBuffer PLANE = BufferUtils.createDoubleBuffer(4);

    private static final class Transit {

        final long start = System.currentTimeMillis();
        final double x, y, z;
        final boolean picture;
        long movedAt = -1, fadeAt = -1;

        Transit(Entity p, boolean picture) {
            x = p.posX;
            y = p.posY;
            z = p.posZ;
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
        } else {
            notice(mc);
            if (++ticks % 10 == 0) views(mc);
        }
    }

    private static void worldChanged(World w) {
        for (FarView v : VIEWS.values()) v.free();
        VIEWS.clear();
        seen = w;
        lastFeet = null;
        pictured = Long.MIN_VALUE;
        transit = null;
        if (w != null) w.addWorldAccess(new Listener(w));
    }

    /** Keeps a view for each linked gate near the player, and lets go of the rest. */
    private static void views(Minecraft mc) {
        EntityPlayer p = mc.thePlayer;
        if (p == null || mc.theWorld == null) return;
        double range = Config.gateViewRange + 8;
        Set<Long> keep = new HashSet<>();
        if (Config.gateViewRange > 0 && Config.gateLiveView && !broken) for (TileLightGate t : gates(mc.theWorld)) {
            if (!t.linked || t.far == null) continue;
            double dx = t.xCoord + 0.5 - p.posX, dy = t.yCoord + 1.5 - p.posY, dz = t.zCoord + 0.5 - p.posZ;
            long k = key(t.xCoord, t.yCoord, t.zCoord);
            FarView v = VIEWS.get(k);
            double d = dx * dx + dy * dy + dz * dz;
            if (d > (range + 16) * (range + 16) || v == null && d > range * range) continue;
            keep.add(k);
            GateGeometry.Gate g = t.gate(), source = t.partner();
            if (v != null && v.sameAs(g, source, t.far)) continue;
            if (v != null) v.free();
            VIEWS.put(k, new FarView(g, source, t.far));
        }
        for (Iterator<Map.Entry<Long, FarView>> it = VIEWS.entrySet()
            .iterator(); it.hasNext();) {
            Map.Entry<Long, FarView> en = it.next();
            if (keep.contains(en.getKey())) continue;
            en.getValue()
                .free();
            it.remove();
        }
    }

    private static void handle(Minecraft mc, IMessage msg) {
        if (mc.theWorld == null || mc.thePlayer == null) return;
        if (msg instanceof GateNet.Transit t && transit == null && t.dim == mc.theWorld.provider.dimensionId)
            begin(mc, key(t.x, t.y, t.z));
    }

    /** The player stepped into the gate with this key: hold the picture until the other side is drawn. */
    private static void begin(Minecraft mc, long gate) {
        transit = new Transit(mc.thePlayer, pictured == gate && fbo != null);
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

    /** Hears every block change of the client's world, for the views. */
    private static final class Listener implements IWorldAccess {

        private final World world;

        Listener(World world) {
            this.world = world;
        }

        private boolean current() {
            return world == seen && !VIEWS.isEmpty();
        }

        @Override
        public void markBlockForUpdate(int x, int y, int z) {
            if (current()) for (FarView v : VIEWS.values()) v.changed(x, y, z);
        }

        @Override
        public void markBlockForRenderUpdate(int x, int y, int z) {
            markBlockForUpdate(x, y, z);
        }

        @Override
        public void markBlockRangeForRenderUpdate(int x0, int y0, int z0, int x1, int y1, int z1) {
            if (current()) for (FarView v : VIEWS.values()) v.changed(x0, y0, z0, x1, y1, z1);
        }

        @Override
        public void playSound(String sound, double x, double y, double z, float volume, float pitch) {}

        @Override
        public void playSoundToNearExcept(EntityPlayer except, String sound, double x, double y, double z, float volume,
            float pitch) {}

        @Override
        public void spawnParticle(String name, double x, double y, double z, double vx, double vy, double vz) {}

        @Override
        public void onEntityCreate(Entity e) {}

        @Override
        public void onEntityDestroy(Entity e) {}

        @Override
        public void playRecord(String record, int x, int y, int z) {}

        @Override
        public void broadcastSound(int id, int x, int y, int z, int data) {}

        @Override
        public void playAuxSFX(EntityPlayer player, int id, int x, int y, int z, int data) {}

        @Override
        public void destroyBlockPartially(int breaker, int x, int y, int z, int progress) {}

        @Override
        public void onStaticEntitiesChanged() {}
    }

    /** A chunk arrived: what the views show of it is built again (the range update usually says so already). */
    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load e) {
        if (!e.world.isRemote || e.world != seen || VIEWS.isEmpty()) return;
        int x = e.getChunk().xPosition << 4, z = e.getChunk().zPosition << 4;
        for (FarView v : VIEWS.values()) v.changed(x, 0, z, x + 15, 255, z + 15);
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
        Frustrum frustum = new Frustrum();
        frustum.setPosition(cx, cy, cz);
        int budget = 3;
        for (TileLightGate t : list) {
            double dx = t.xCoord + 0.5 - cx, dy = t.yCoord + 1.5 - cy, dz = t.zCoord + 0.5 - cz;
            if (dx * dx + dy * dy + dz * dz > 96 * 96 || !frustum.isBoundingBoxInFrustum(t.getRenderBoundingBox()))
                continue;
            GateGeometry.Gate g = t.gate();
            long k = key(t.xCoord, t.yCoord, t.zCoord);
            FarView v = t.linked ? VIEWS.get(k) : null;
            double front = g.front(cx, cz);
            boolean drawn = false;
            if (v != null && Config.gateLiveView
                && !broken
                && transit == null
                && front > 0
                && OpenGlHelper.isFramebufferEnabled()) {
                budget -= v.build(mc.theWorld, Math.max(0, budget));
                if (v.hasAny()) try {
                    live(mc, v, g, cx, cy, cz, e.partialTicks);
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
        // what is left goes to views not in sight yet, so they are ready when looked at
        if (budget > 1 && !broken && transit == null) for (FarView v : VIEWS.values()) {
            budget -= v.build(mc.theWorld, budget - 1);
            if (budget <= 1) break;
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

    private static void live(Minecraft mc, FarView v, GateGeometry.Gate g, double cx, double cy, double cz, float pt) {
        int w = mc.displayWidth, h = mc.displayHeight;
        if (w <= 0 || h <= 0) return;
        ensureFbo(w, h);
        picture(mc, v, g, cx, cy, cz, pt);
        membrane(g, cx, cy, cz);
    }

    /** The far side, through the gate, from the player's camera, into the off-screen picture. */
    private static void picture(Minecraft mc, FarView v, GateGeometry.Gate g, double cx, double cy, double cz,
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
            GL11.glClearColor((float) sky.xCoord, (float) sky.yCoord, (float) sky.zCoord, 1f);
            GL11.glClearDepth(1.0);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
            if (prevProgram != 0) GL20.glUseProgram(0);
            blockState();
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
            GL11.glRotatef(GateGeometry.glDegrees(GateGeometry.turns(v.source, g)), 0f, 1f, 0f);
            v.draw(0);
            // machines and chests with renderers of their own
            RenderHelper.enableStandardItemLighting();
            v.drawTiles(mc.theWorld, pt);
            RenderHelper.disableStandardItemLighting();
            blockState();
            mc.entityRenderer.enableLightmap(pt);
            mc.getTextureManager()
                .bindTexture(TextureMap.locationBlocksTexture);
            GL11.glEnable(GL11.GL_BLEND);
            OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
            GL11.glDepthMask(false);
            v.draw(1);
            GL11.glDepthMask(true);
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

    /** The state chunks are drawn with. */
    private static void blockState() {
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
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1f, 1f, 1f, 1f);
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

    /**
     * Over everything, while walking through: the picture holds until the player has been moved and the renderer has
     * had a moment at the new place, then fades.
     */
    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.END || transit == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        Transit t = transit;
        long now = System.currentTimeMillis();
        EntityPlayer p = mc.thePlayer;
        if (t.movedAt < 0 && p != null) {
            double dx = p.posX - t.x, dy = p.posY - t.y, dz = p.posZ - t.z;
            if (dx * dx + dy * dy + dz * dz > 16 * 16) t.movedAt = now;
        }
        boolean ready = t.movedAt >= 0 && now - t.movedAt > 250;
        boolean late = now - t.start > 1500;
        if (t.fadeAt < 0 && (ready || late)) t.fadeAt = now;
        float alpha = t.fadeAt < 0 ? 1f : 1f - (now - t.fadeAt) / 300f;
        if (alpha <= 0f) {
            transit = null;
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
            transit = null;
        }
    }
}
