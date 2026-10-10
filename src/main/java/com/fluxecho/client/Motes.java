package com.fluxecho.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.fluxecho.FluxEcho;

/**
 * Soft glowing spots in the world, all facing the camera and drawn in one batch: the echo machines' effects and the
 * trails. Drawn after the world like the holograms, with a normal towards the viewer and the shader-aware lightmap
 * of {@link FluxDraw#worldBegin}, so a shader pack lights them properly. Positions are relative to the camera.
 * {@link #begin} and {@link #end} keep the GL state balanced on every path: a batch that fails to open pops its state
 * at once, and {@link #end} pops it even when drawing the batch fails, so a caller's error handling can always just
 * call {@link #end}.
 */
public final class Motes {

    private static final ResourceLocation MOTE = new ResourceLocation(FluxEcho.MODID, "textures/effects/mote.png");

    /** The camera's right and up, in the world. */
    private static double rx, rz, ux, uy, uz;
    private static boolean open;
    /** The normal towards the viewer, worked out by {@link #setUp}. */
    private static final float[] NORMAL = new float[3];

    private Motes() {}

    /**
     * Starts a batch: additive blending, the mote texture, the camera's axes. Pair with {@link #end}. When it fails
     * before the batch is open, the GL state it pushed is popped again before the error goes on.
     */
    public static void begin() {
        FluxDraw.worldBegin();
        boolean drawing = false, ok = false;
        try {
            setUp();
            Tessellator.instance.startDrawingQuads();
            drawing = true;
            // towards the viewer: the opposite of where the camera looks
            Tessellator.instance.setNormal(NORMAL[0], NORMAL[1], NORMAL[2]);
            open = true;
            ok = true;
        } finally {
            if (!ok) {
                if (drawing) try {
                    Tessellator.instance.draw();
                } catch (RuntimeException ignored) {
                    // it was not drawing after all
                }
                GL11.glEnable(GL11.GL_ALPHA_TEST);
                FluxDraw.worldEnd();
            }
        }
    }

    /** The batch's GL state and the camera's axes. */
    private static void setUp() {
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(MOTE);
        double yaw = Math.toRadians(RenderManager.instance.playerViewY),
            pitch = Math.toRadians(RenderManager.instance.playerViewX);
        double sy = Math.sin(yaw), cy = Math.cos(yaw), sp = Math.sin(pitch), cp = Math.cos(pitch);
        rx = cy;
        rz = sy;
        ux = -sp * sy;
        uy = cp;
        uz = sp * cy;
        NORMAL[0] = (float) (sy * cp);
        NORMAL[1] = (float) sp;
        NORMAL[2] = (float) (-cy * cp);
    }

    /** One spot of half-size {@code s} blocks. */
    public static void add(double x, double y, double z, double s, int rgb, float a) {
        if (!open || a <= 0.01f) return;
        Tessellator t = Tessellator.instance;
        t.setColorRGBA_I(rgb, (int) (Math.min(1f, a) * 255));
        double ax = (rx + ux) * s, ay = uy * s, az = (rz + uz) * s;
        double bx = (rx - ux) * s, by = -uy * s, bz = (rz - uz) * s;
        t.addVertexWithUV(x - ax, y - ay, z - az, 0, 1);
        t.addVertexWithUV(x + bx, y + by, z + bz, 1, 1);
        t.addVertexWithUV(x + ax, y + ay, z + az, 1, 0);
        t.addVertexWithUV(x - bx, y - by, z - bz, 0, 0);
    }

    /** Draws the batch and pops its GL state; the state is popped even when drawing fails. Nothing when not open. */
    public static void end() {
        if (!open) return;
        open = false;
        try {
            Tessellator.instance.draw();
        } finally {
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            FluxDraw.worldEnd();
        }
    }
}
