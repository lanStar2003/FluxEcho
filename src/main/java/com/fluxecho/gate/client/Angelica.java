package com.fluxecho.gate.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.renderer.culling.Frustrum;
import net.minecraft.entity.EntityLivingBase;

import com.fluxecho.FluxEcho;

/**
 * What the real view through a gate needs of Angelica (1.0.0-beta66b, GTNH 2.8.4), by reflection so FluxEcho runs
 * without it:
 * <ul>
 * <li>whether its Sodium renderer is on: that one keeps every chunk's mesh wherever the camera is, so drawing the world
 * a second time from far away costs no rebuild (the vanilla renderer moves its chunks round the camera);</li>
 * <li>the projection its chunk shader uses, which it captured from OpenGL before the gate's cut was put in;</li>
 * <li>where it last sorted see-through blocks from: each camera keeps its own, so two cameras a world apart do not
 * have every pane of glass sorted again every frame;</li>
 * <li>its chunk updater on its own, from another camera and without drawing anything: that builds the meshes of what
 * the camera would see, which is all a shader pack's frame needs to find the far side ready.</li>
 * </ul>
 */
final class Angelica {

    private static Boolean ready;
    private static Object state;
    private static Method setProjection, rendererInstance;
    private static Field manager, sortX, sortY, sortZ;
    private static boolean sortBroken, walkBroken;
    private static Object camera;
    private static Method cameraUpdate, cameraPos, updateChunks, enterManaged, exitManaged;
    private static Field posX, posY, posZ;
    private static int walks = -1_000_000_000;
    private static final float[] NOWHERE = { 3e7f, 3e7f, 3e7f };
    private static final Map<Long, float[]> SORTED = new HashMap<>();
    private static float[] main;

    private Angelica() {}

    /** Angelica is there with its Sodium renderer on, and the projection can be set. */
    static boolean ready() {
        if (ready == null) ready = init();
        return ready;
    }

    private static boolean init() {
        try {
            Class<?> cfg = Class.forName("com.gtnewhorizons.angelica.config.AngelicaConfig");
            if (!cfg.getField("enableSodium")
                .getBoolean(null)) return false;
            Class<?> rs = Class.forName("com.gtnewhorizons.angelica.rendering.RenderingState");
            state = rs.getField("INSTANCE")
                .get(null);
            setProjection = rs.getMethod("setProjectionMatrix", FloatBuffer.class);
        } catch (ClassNotFoundException e) {
            return false;
        } catch (Throwable t) {
            FluxEcho.LOG.warn("Angelica is not as the light gates know it; they show their far side the old way", t);
            return false;
        }
        try {
            Class<?> swr = Class.forName("me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer");
            rendererInstance = swr.getMethod("getInstance");
            manager = swr.getDeclaredField("chunkRenderManager");
            manager.setAccessible(true);
            Class<?> crm = Class.forName("me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderManager");
            sortX = crm.getDeclaredField("lastCameraTranslucentX");
            sortY = crm.getDeclaredField("lastCameraTranslucentY");
            sortZ = crm.getDeclaredField("lastCameraTranslucentZ");
            sortX.setAccessible(true);
            sortY.setAccessible(true);
            sortZ.setAccessible(true);
        } catch (Throwable t) {
            sortBroken = true;
        }
        try {
            Class<?> cam = Class.forName("com.gtnewhorizons.angelica.compat.mojang.Camera");
            camera = cam.getField("INSTANCE")
                .get(null);
            cameraUpdate = cam.getMethod("update", EntityLivingBase.class, float.class);
            cameraPos = cam.getMethod("getPos");
            Class<?> v = Class.forName("org.joml.Vector3d");
            posX = v.getField("x");
            posY = v.getField("y");
            posZ = v.getField("z");
            Class<?> swr = Class.forName("me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer");
            updateChunks = swr.getMethod("updateChunks", cam, Frustrum.class, boolean.class, int.class, boolean.class);
            Class<?> device = Class.forName("me.jellysquid.mods.sodium.client.gl.device.RenderDevice");
            enterManaged = device.getMethod("enterManagedCode");
            exitManaged = device.getMethod("exitManagedCode");
        } catch (Throwable t) {
            walkBroken = true;
            FluxEcho.LOG.warn(
                "Angelica's chunk updater is not as the light gates know it; with a shader pack the far side builds only when looked at",
                t);
        }
        return true;
    }

    /** Whether {@link #walk} can be used. */
    static boolean canWalk() {
        return ready() && !walkBroken;
    }

    /**
     * Lets the chunk updater walk the world from {@code from}, as if the frame were drawn from there, without drawing:
     * whatever that camera would see gets its mesh built. The frustum is whatever {@code ClippingHelperImpl} gives
     * while this runs. The camera is the {@code back} entity's again afterwards.
     */
    static void walk(EntityLivingBase from, EntityLivingBase back, float pt) throws Exception {
        Object renderer = rendererInstance == null ? null : rendererInstance.invoke(null);
        if (renderer == null) return;
        cameraUpdate.invoke(camera, from, pt);
        try {
            Object pos = cameraPos.invoke(camera);
            Frustrum f = new Frustrum();
            f.setPosition(posX.getDouble(pos), posY.getDouble(pos), posZ.getDouble(pos));
            enterManaged.invoke(null);
            try {
                updateChunks.invoke(renderer, camera, f, false, walks--, false);
            } finally {
                exitManaged.invoke(null);
            }
        } finally {
            cameraUpdate.invoke(camera, back, pt);
        }
    }

    /** The projection the chunks are drawn with from now on, in this frame. */
    static void projection(FloatBuffer m) throws Exception {
        setProjection.invoke(state, m);
    }

    private static Object manager() {
        if (sortBroken) return null;
        try {
            Object r = rendererInstance.invoke(null);
            return r == null ? null : manager.get(r);
        } catch (Throwable t) {
            sortBroken = true;
            return null;
        }
    }

    /** A camera through the gate with this key starts drawing: it sorts from where it sorted last. */
    static void enter(long key) {
        Object m = manager();
        if (m == null) return;
        try {
            main = new float[] { sortX.getFloat(m), sortY.getFloat(m), sortZ.getFloat(m) };
            write(m, SORTED.getOrDefault(key, NOWHERE));
        } catch (Throwable t) {
            sortBroken = true;
            main = null;
        }
    }

    /** And stops: the player's camera gets its own back. */
    static void leave(long key) {
        Object m = manager();
        float[] own = main;
        main = null;
        if (m == null || own == null) return;
        try {
            SORTED.put(key, new float[] { sortX.getFloat(m), sortY.getFloat(m), sortZ.getFloat(m) });
            write(m, own);
        } catch (Throwable t) {
            sortBroken = true;
        }
    }

    private static void write(Object m, float[] v) throws IllegalAccessException {
        sortX.setFloat(m, v[0]);
        sortY.setFloat(m, v[1]);
        sortZ.setFloat(m, v[2]);
    }

    static void forget() {
        SORTED.clear();
    }
}
