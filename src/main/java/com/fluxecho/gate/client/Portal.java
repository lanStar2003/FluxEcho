package com.fluxecho.gate.client;

import java.lang.reflect.Field;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.culling.ClippingHelper;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import com.fluxecho.Config;
import com.fluxecho.FluxEcho;
import com.fluxecho.client.ShaderCompat;
import com.fluxecho.gate.TileLightGate;
import com.fluxecho.logic.GateGeometry;
import com.fluxecho.logic.PortalMath;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * The real view through a light gate. With Angelica (and no shader pack) the game draws the whole world a second time
 * for each gate in sight, from where the player's eye comes out on the far side, into an off-screen picture the
 * membrane shows: blocks, machines, mobs and players, particles, holograms, sky and weather, all as the player would
 * see them standing there. What lies between that camera and the far gate is cut away with the projection's near
 * plane laid on the far membrane.
 * <p>
 * Drawing it is also what makes walking through seamless: the renderer builds the meshes of whatever it draws, so
 * the far side is ready long before the player steps in. The step itself is never seen: from the moment the eye
 * passes the membrane the world is drawn from the far side's camera, and when the server moves the player there they
 * come out exactly where that camera was, still walking.
 * <p>
 * Everything here runs on the render thread; the mixins in {@code com.fluxecho.mixins.early} call in.
 */
public final class Portal {

    /** Gates drawn for real in one frame. */
    private static final int PASSES = 2;
    private static final int[] TINY = { 0, 0, 1, 1 };

    private static Pass pass;
    private static boolean broken;
    private static long lastHook = Long.MIN_VALUE / 2, frame;
    private static World world;
    private static GateCamera passCam, transitCam;
    private static final Framebuffer[] FBOS = new Framebuffer[PASSES];
    private static final Map<Long, Integer> PICTURES = new HashMap<>();
    private static final Map<Long, Integer> WARMED = new HashMap<>();
    private static Transit transit;
    private static boolean swapped;
    /** The eye last frame: x, feet, z. */
    private static double[] lastEye;
    private static double[] jump;
    /** Until when a step into a gate is not one: the server lets a player who just came through go back in later. */
    private static long calm;

    private static final FloatBuffer MATRIX = BufferUtils.createFloatBuffer(16);

    /** One drawing of the far side of a gate. */
    private static final class Pass {

        final GateGeometry.Gate far;
        /** Seen from everywhere round the camera, not just through the gate, and not cut: only builds meshes. */
        final boolean all;
        final Block medium;
        final double x, y, z;

        Pass(GateGeometry.Gate far, boolean all, Block medium, double[] at) {
            this.far = far;
            this.all = all;
            this.medium = medium;
            x = at[0];
            y = at[1];
            z = at[2];
        }
    }

    /** The eye has gone through {@code near} and the server has not moved the player out of {@code far} yet. */
    private static final class Transit {

        final GateGeometry.Gate near, far;
        final long start = System.currentTimeMillis();

        Transit(GateGeometry.Gate near, GateGeometry.Gate far) {
            this.near = near;
            this.far = far;
        }
    }

    /** Where a camera stands and looks, at this frame's partial tick. */
    private static final class Pose {

        double x, y, z, ex, ey, ez, feet;
        float yaw, pitch;
    }

    private Portal() {}

    static void register() {
        Portal p = new Portal();
        MinecraftForge.EVENT_BUS.register(p);
        FMLCommonHandler.instance()
            .bus()
            .register(p);
    }

    // ---- state the rest of the client reads

    /** Whether gates are seen through for real: Angelica's renderer, no shader pack, and the hooks running. */
    public static boolean running() {
        return System.nanoTime() - lastHook < 500_000_000L && usable(Minecraft.getMinecraft());
    }

    private static boolean usable(Minecraft mc) {
        return !broken && Config.gateLiveView
            && Config.gateRealView
            && Config.gateViewRange > 0
            && mc.theWorld != null
            && mc.thePlayer != null
            && OpenGlHelper.isFramebufferEnabled()
            && Angelica.ready()
            && !ShaderCompat.packInUse();
    }

    /** The world is being drawn from beyond a gate right now. */
    public static boolean inPass() {
        return pass != null;
    }

    /** The gate the current drawing looks out of, null between drawings. */
    public static GateGeometry.Gate passGate() {
        Pass p = pass;
        return p == null ? null : p.far;
    }

    /** The texture with this gate's far side drawn in this frame, or -1. */
    public static int picture(long key) {
        Integer slot = PICTURES.get(key);
        return slot == null || FBOS[slot] == null ? -1 : FBOS[slot].framebufferTexture;
    }

    /** The occlusion culler must not hide the far side behind the walls the far camera stands among. */
    public static boolean seeThrough() {
        return pass != null;
    }

    private static Field occlusion;
    private static boolean noField;

    /** Turns occlusion culling off in Angelica's culler for this search. */
    public static void noOcclusion(Object culler) {
        if (noField) return;
        try {
            if (occlusion == null) {
                occlusion = culler.getClass()
                    .getDeclaredField("useOcclusionCulling");
                occlusion.setAccessible(true);
            }
            occlusion.setBoolean(culler, false);
        } catch (Throwable t) {
            noField = true;
            FluxEcho.LOG.warn("Angelica's culler is not as the light gates know it; walls may hide the far side", t);
        }
    }

    // ---- each frame, before the world is drawn

    public static void beforeWorld(float pt) {
        if (pass != null) return;
        lastHook = System.nanoTime();
        PICTURES.clear();
        frame++;
        Minecraft mc = Minecraft.getMinecraft();
        if (!usable(mc) || mc.renderViewEntity != mc.thePlayer) {
            transit = null;
            lastEye = null;
            return;
        }
        try {
            if (world != mc.theWorld) newWorld(mc.theWorld);
            transit(mc, pt);
            passes(mc, pt);
        } catch (Throwable t) {
            fail(mc, t);
        }
    }

    /** After the world is drawn: the player's own camera again. */
    public static void afterWorld() {
        if (!swapped) return;
        swapped = false;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.renderViewEntity == transitCam) mc.renderViewEntity = mc.thePlayer;
    }

    private static void fail(Minecraft mc, Throwable t) {
        broken = true;
        pass = null;
        transit = null;
        afterWorld();
        if (mc.renderViewEntity instanceof GateCamera) mc.renderViewEntity = mc.thePlayer;
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        if (OpenGlHelper.isFramebufferEnabled()) mc.getFramebuffer()
            .bindFramebuffer(true);
        FluxEcho.LOG.error(
            "Drawing the world through a light gate failed; the gates show their far side the simple way from now on (gates.realView in config/fluxecho.cfg turns this off for good)",
            t);
    }

    private static void newWorld(World w) {
        world = w;
        passCam = new GateCamera(w);
        transitCam = new GateCamera(w);
        transit = null;
        lastEye = null;
        WARMED.clear();
        Angelica.forget();
    }

    private static Pose pose(EntityLivingBase e, float pt) {
        Pose p = new Pose();
        p.x = e.lastTickPosX + (e.posX - e.lastTickPosX) * pt;
        p.y = e.lastTickPosY + (e.posY - e.lastTickPosY) * pt;
        p.z = e.lastTickPosZ + (e.posZ - e.lastTickPosZ) * pt;
        p.yaw = e.prevRotationYaw + (e.rotationYaw - e.prevRotationYaw) * pt;
        p.pitch = e.prevRotationPitch + (e.rotationPitch - e.prevRotationPitch) * pt;
        // the camera sits a tenth of a block behind the entity, at its eyes
        double[] l = PortalMath.look(p.yaw, p.pitch);
        p.ex = p.x - 0.1 * l[0];
        p.ey = p.y - (e.yOffset - 1.62) - 0.1 * l[1];
        p.ez = p.z - 0.1 * l[2];
        p.feet = p.y - e.yOffset;
        return p;
    }

    private static boolean same(GateGeometry.Gate a, GateGeometry.Gate b) {
        return a.x == b.x && a.y == b.y && a.z == b.z;
    }

    /** Starts, keeps or ends drawing from the far side while the eye is through a gate. */
    private static void transit(Minecraft mc, float pt) {
        EntityPlayer p = mc.thePlayer;
        Pose v = pose(p, pt);
        if (transit != null) {
            double dx = transit.near.cx() - v.ex, dz = transit.near.cz() - v.ez;
            if (System.currentTimeMillis() - transit.start > 1500 || transit.near.front(v.ex, v.ez) > 0.05
                || dx * dx + dz * dz > 64) transit = null;
        }
        // the server takes nobody riding or ridden through, and nobody straight back
        boolean free = p.ridingEntity == null && p.riddenByEntity == null && System.currentTimeMillis() > calm;
        if (transit == null && lastEye != null && free) for (TileLightGate t : GateClient.gates(mc.theWorld)) {
            if (!t.linked) continue;
            GateGeometry.Gate g = t.gate();
            if (Math.abs(g.cx() - v.ex) > 6 || Math.abs(g.cz() - v.ez) > 6) continue;
            if (g.entered(lastEye[0], lastEye[1], lastEye[2], v.ex, v.feet, v.ez)) {
                transit = new Transit(g, t.partner());
                break;
            }
        }
        lastEye = new double[] { v.ex, v.feet, v.ez };
        if (transit == null) return;
        double[] q = GateGeometry.carry(transit.near, transit.far, v.x, v.y, v.z);
        transitCam.place(p, q[0], q[1], q[2], GateGeometry.exitYaw(transit.near, transit.far, v.yaw), v.pitch);
        mc.renderViewEntity = transitCam;
        swapped = true;
    }

    private static final class Seen {

        final TileLightGate tile;
        final GateGeometry.Gate gate;
        final long key;
        final double distance;
        final int[] rect;

        Seen(TileLightGate tile, GateGeometry.Gate gate, double distance, int[] rect) {
            this.tile = tile;
            this.gate = gate;
            this.distance = distance;
            this.rect = rect;
            key = GateClient.key(gate.x, gate.y, gate.z);
        }
    }

    /**
     * Draws the gates in sight, nearest first. A gate close by but not in sight still gets drawn now and then, too
     * small to see, so its far side is built before anyone walks in backwards; and a room is drawn a few times from
     * all round, so it is all there whichever way the player turns inside.
     */
    private static void passes(Minecraft mc, float pt) {
        EntityLivingBase viewer = mc.renderViewEntity;
        Pose v = pose(viewer, pt);
        int w = mc.displayWidth, h = mc.displayHeight;
        if (w <= 0 || h <= 0) return;
        double[] proj = PortalMath.perspective(mc.entityRenderer.getFOVModifier(pt, true), w / (double) h, 0.05, 512);
        double[] view = PortalMath.view(v.yaw, v.pitch);
        double range = Config.gateViewRange + 4;
        List<Seen> seen = new ArrayList<>();
        for (TileLightGate t : GateClient.gates(mc.theWorld)) {
            if (!t.linked || t.far == null) continue;
            GateGeometry.Gate g = t.gate();
            if (transit != null && same(g, transit.far) || g.front(v.ex, v.ez) <= 0) continue;
            double dx = g.cx() - v.ex, dy = g.y + 1.5 - v.ey, dz = g.cz() - v.ez;
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d > range) continue;
            int[] rect = PortalMath.screenRect(proj, view, PortalMath.corners(g, v.ex, v.ey, v.ez, 0.3), w, h, 0.04);
            seen.add(new Seen(t, g, d, rect));
        }
        seen.sort((a, b) -> Double.compare(a.distance, b.distance));
        Set<Long> close = new HashSet<>();
        int slot = 0;
        for (Seen s : seen) {
            if (s.distance <= 24) close.add(s.key);
            if (s.rect != null && slot < PASSES) draw(mc, s, slot++, false, s.rect, pt, viewer, v);
        }
        WARMED.keySet()
            .retainAll(close);
        if (slot >= PASSES) return;
        for (Seen s : seen) {
            if (s.distance > 16) break;
            int n = WARMED.getOrDefault(s.key, 0);
            if (!s.tile.inside && n < 4 && frame % 5 == 0) {
                WARMED.put(s.key, n + 1);
                draw(mc, s, slot, true, TINY, pt, viewer, v);
            } else if (s.rect == null && frame % 3 == 0) draw(mc, s, slot, false, TINY, pt, viewer, v);
            break;
        }
    }

    private static Framebuffer fbo(int slot, int w, int h) {
        Framebuffer f = FBOS[slot];
        if (f != null && f.framebufferWidth == w && f.framebufferHeight == h) return f;
        if (f == null) FBOS[slot] = f = new Framebuffer(w, h, true);
        else f.createBindFramebuffer(w, h);
        f.setFramebufferFilter(GL11.GL_NEAREST);
        return f;
    }

    /** The world from the far side of one gate, into the picture in {@code slot}, inside {@code rect} only. */
    private static void draw(Minecraft mc, Seen s, int slot, boolean all, int[] rect, float pt, EntityLivingBase viewer,
        Pose v) {
        GateGeometry.Gate g = s.gate, f = s.tile.partner();
        double[] q = GateGeometry.carry(g, f, v.x, v.y, v.z);
        passCam.place(viewer, q[0], q[1], q[2], GateGeometry.exitYaw(g, f, v.yaw), v.pitch);
        // the far camera is in whatever the player's eye is in: no water fog just because it stands in a lake
        Block medium = ActiveRenderInfo.getBlockAtEntityViewpoint(mc.theWorld, viewer, pt);
        int prevFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        Framebuffer fbo = fbo(slot, mc.displayWidth, mc.displayHeight);
        EntityLivingBase prevView = mc.renderViewEntity;
        MovingObjectPosition over = mc.objectMouseOver;
        Entity pointed = mc.pointedEntity;
        Angelica.enter(s.key);
        pass = new Pass(f, all, medium, q);
        mc.renderViewEntity = passCam;
        try {
            fbo.bindFramebuffer(true);
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(rect[0], rect[1], rect[2], rect[3]);
            mc.entityRenderer.renderWorld(pt, 0L);
        } finally {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            pass = null;
            mc.renderViewEntity = prevView;
            mc.objectMouseOver = over;
            mc.pointedEntity = pointed;
            Angelica.leave(s.key);
            OpenGlHelper.func_153171_g(OpenGlHelper.field_153198_e, prevFbo);
            GL11.glViewport(0, 0, mc.displayWidth, mc.displayHeight);
        }
        if (rect != TINY) PICTURES.put(s.key, slot);
    }

    // ---- inside a drawing (called from the mixins)

    /** What the frustum keeps: only what can be seen through the gate, from the camera on, so nothing is in the way. */
    public static void planes(ClippingHelper h) {
        Pass p = pass;
        if (p == null || h == null) return;
        double[][] side = p.all || Math.abs(p.far.front(p.x, p.z)) < 0.3 ? null
            : PortalMath.pyramid(PortalMath.corners(p.far, p.x, p.y, p.z, 0.3), 0.75);
        for (int i = 0; i < 6; i++) {
            double[] s = side != null && i < 4 ? side[i] : null;
            float[] f = h.frustum[i];
            f[0] = s == null ? 0 : (float) s[0];
            f[1] = s == null ? 0 : (float) s[1];
            f[2] = s == null ? 0 : (float) s[2];
            f[3] = s == null ? 1 : (float) s[3];
        }
    }

    /** Lays the near plane on the far membrane, for OpenGL and for Angelica's chunk shader. */
    public static void oblique() {
        Pass p = pass;
        if (p == null || p.all) return;
        double[] mv = read(GL11.GL_MODELVIEW_MATRIX), proj = read(GL11.GL_PROJECTION_MATRIX);
        double[] eye = PortalMath.carryPlane(mv, PortalMath.membranePlane(p.far, p.x, p.z));
        if (eye == null || eye[3] > -0.003) return;
        double[] o = PortalMath.oblique(proj, eye);
        MATRIX.clear();
        for (double d : o) MATRIX.put((float) d);
        MATRIX.flip();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadMatrix(MATRIX);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        MATRIX.rewind();
        try {
            Angelica.projection(MATRIX);
        } catch (Exception e) {
            throw new IllegalStateException("Angelica's projection could not be set", e);
        }
    }

    private static double[] read(int which) {
        MATRIX.clear();
        GL11.glGetFloat(which, MATRIX);
        double[] m = new double[16];
        for (int i = 0; i < 16; i++) m[i] = MATRIX.get(i);
        return m;
    }

    /** Particles and name tags face the camera the world is drawn from, not the player. */
    public static void billboards(boolean inverted) {
        if (!(Minecraft.getMinecraft().renderViewEntity instanceof GateCamera c)) return;
        float s = inverted ? -1 : 1, yaw = c.rotationYaw * (float) Math.PI / 180f,
            pitch = c.rotationPitch * (float) Math.PI / 180f;
        ActiveRenderInfo.rotationX = MathHelper.cos(yaw) * s;
        ActiveRenderInfo.rotationZ = MathHelper.sin(yaw) * s;
        ActiveRenderInfo.rotationYZ = -ActiveRenderInfo.rotationZ * MathHelper.sin(pitch) * s;
        ActiveRenderInfo.rotationXY = ActiveRenderInfo.rotationX * MathHelper.sin(pitch) * s;
        ActiveRenderInfo.rotationXZ = MathHelper.cos(pitch);
    }

    /** What the far camera is in, for fog and the field of view: what the player's eye is in. */
    public static Block medium(EntityLivingBase e) {
        Pass p = pass;
        return p != null && e == passCam ? p.medium : null;
    }

    /** The far camera bobs as the player walks, so the picture keeps still on the membrane. */
    public static void bob(float pt) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer p = mc.thePlayer;
        if (!(mc.renderViewEntity instanceof GateCamera) || p == null) return;
        float f1 = p.distanceWalkedModified - p.prevDistanceWalkedModified;
        float f2 = -(p.distanceWalkedModified + f1 * pt);
        float f3 = p.prevCameraYaw + (p.cameraYaw - p.prevCameraYaw) * pt;
        float f4 = p.prevCameraPitch + (p.cameraPitch - p.prevCameraPitch) * pt;
        GL11.glTranslatef(
            MathHelper.sin(f2 * (float) Math.PI) * f3 * 0.5F,
            -Math.abs(MathHelper.cos(f2 * (float) Math.PI) * f3),
            0.0F);
        GL11.glRotatef(MathHelper.sin(f2 * (float) Math.PI) * f3 * 3.0F, 0.0F, 0.0F, 1.0F);
        GL11.glRotatef(Math.abs(MathHelper.cos(f2 * (float) Math.PI - 0.2F) * f3) * 5.0F, 1.0F, 0.0F, 0.0F);
        GL11.glRotatef(f4, 1.0F, 0.0F, 0.0F);
    }

    @SubscribeEvent
    public void onRenderHand(RenderHandEvent e) {
        if (pass != null) e.setCanceled(true);
    }

    /**
     * Torches flicker and machines smoke on the far side too: the blocks round where the player would stand there get
     * their display ticks, with the camera there for the moment, as particles further than 16 blocks from the camera
     * are dropped.
     */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer p = mc.thePlayer;
        WorldClient w = mc.theWorld;
        if (p == null || w == null || mc.isGamePaused() || !running()) return;
        if (world != w || mc.renderViewEntity != p) return;
        TileLightGate best = null;
        double nearest = 16 * 16;
        for (TileLightGate t : GateClient.gates(w)) {
            if (!t.linked) continue;
            double dx = t.xCoord + 0.5 - p.posX, dy = t.yCoord + 1.5 - p.posY, dz = t.zCoord + 0.5 - p.posZ;
            double d = dx * dx + dy * dy + dz * dz;
            if (d < nearest) {
                nearest = d;
                best = t;
            }
        }
        if (best == null) return;
        double[] q = GateGeometry.carry(best.gate(), best.partner(), p.posX, p.posY, p.posZ);
        passCam.place(p, q[0], q[1], q[2], p.rotationYaw, p.rotationPitch);
        mc.renderViewEntity = passCam;
        try {
            w.doVoidFogParticles(
                MathHelper.floor_double(q[0]),
                MathHelper.floor_double(q[1]),
                MathHelper.floor_double(q[2]));
        } finally {
            mc.renderViewEntity = p;
        }
    }

    /** The player's own camera is back by the end of the frame, even if the hook after the world did not run. */
    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent e) {
        if (e.phase == TickEvent.Phase.END) afterWorld();
    }

    // ---- the server moves the player

    /** Just before the client takes a position from the server: what it was. */
    public static void beforeJump() {
        EntityPlayer p = Minecraft.getMinecraft().thePlayer;
        jump = p == null ? null
            : new double[] { p.posX, p.posY, p.posZ, p.prevPosX, p.prevPosY, p.prevPosZ, p.lastTickPosX, p.lastTickPosY,
                p.lastTickPosZ, p.rotationYaw, p.prevRotationYaw, p.rotationPitch, p.prevRotationPitch, p.motionX,
                p.motionY, p.motionZ };
    }

    /**
     * After it: a step through a gate comes out exactly where the eye already is, still moving and still looking the
     * same way; any other long jump does not slide the camera across the world in between.
     */
    public static void afterJump() {
        double[] j = jump;
        jump = null;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer p = mc.thePlayer;
        if (j == null || p == null) return;
        double dx = p.posX - j[0], dy = p.posY - j[1], dz = p.posZ - j[2];
        if (dx * dx + dy * dy + dz * dz < 16 * 16) return;
        Transit t = transit;
        transit = null;
        lastEye = null;
        calm = System.currentTimeMillis() + 600;
        if (t != null && carried(p, t.near, t.far, j)) return;
        if (mc.theWorld != null) for (TileLightGate g : GateClient.gates(mc.theWorld)) {
            if (!g.linked) continue;
            GateGeometry.Gate a = g.gate();
            if (Math.abs(a.cx() - j[0]) > 4 || Math.abs(a.cz() - j[2]) > 4) continue;
            if (carried(p, a, g.partner(), j)) return;
        }
        p.lastTickPosX = p.prevPosX = p.posX;
        p.lastTickPosY = p.prevPosY = p.posY;
        p.lastTickPosZ = p.prevPosZ = p.posZ;
    }

    private static boolean carried(EntityPlayer p, GateGeometry.Gate a, GateGeometry.Gate b, double[] j) {
        double[] q = GateGeometry.carry(a, b, j[0], j[1], j[2]);
        double ex = q[0] - p.posX, ey = q[1] - p.posY, ez = q[2] - p.posZ;
        if (ex * ex + ey * ey + ez * ez > 9) return false;
        double ahead = 0.02 - b.front(q[0], q[2]);
        if (ahead > 0) {
            q[0] += GateGeometry.dx(b.facing) * ahead;
            q[2] += GateGeometry.dz(b.facing) * ahead;
        }
        double sx = p.posX, sy = p.posY, sz = p.posZ;
        p.setPosition(q[0], q[1], q[2]);
        if (!p.worldObj.getCollidingBoundingBoxes(p, p.boundingBox)
            .isEmpty()) p.setPosition(sx, sy, sz);
        // where the server put them if that is where they are, and the rest of the pose carried the same way
        double ox = p.posX - q[0], oy = p.posY - q[1], oz = p.posZ - q[2];
        double[] prev = GateGeometry.carry(a, b, j[3], j[4], j[5]), last = GateGeometry.carry(a, b, j[6], j[7], j[8]);
        p.prevPosX = prev[0] + ox;
        p.prevPosY = prev[1] + oy;
        p.prevPosZ = prev[2] + oz;
        p.lastTickPosX = last[0] + ox;
        p.lastTickPosY = last[1] + oy;
        p.lastTickPosZ = last[2] + oz;
        float turn = GateGeometry.exitYaw(a, b, 0f);
        p.rotationYaw = (float) j[9] + turn;
        p.prevRotationYaw = (float) j[10] + turn;
        p.rotationPitch = (float) j[11];
        p.prevRotationPitch = (float) j[12];
        p.rotationYawHead = p.rotationYaw;
        p.prevRotationYawHead = p.prevRotationYaw;
        p.renderYawOffset += turn;
        p.prevRenderYawOffset += turn;
        if (p instanceof EntityPlayerSP sp) {
            sp.renderArmYaw += turn;
            sp.prevRenderArmYaw += turn;
        }
        double[] m = GateGeometry.rotate(GateGeometry.turns(a, b), j[13], j[15]);
        p.motionX = m[0];
        p.motionY = j[14];
        p.motionZ = m[1];
        return true;
    }
}
