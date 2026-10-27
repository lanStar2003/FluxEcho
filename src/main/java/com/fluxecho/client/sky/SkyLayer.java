package com.fluxecho.client.sky;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.opengl.GL11;

import com.fluxecho.FluxEcho;
import com.fluxecho.client.FarDraw;
import com.fluxecho.client.ShaderCompat;
import com.fluxecho.config.RenderConfig;
import com.fluxecho.logic.sky.Aurora;
import com.fluxecho.logic.sky.SkySheet;
import com.fluxecho.render.Shapes;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The lights of the sky (0.17.0 prototype N0, narrative FINAL §9.1 E5, refactor FINAL F06): the aurora now, the evening
 * star, the rift and the leap's sky later. A source lists the sheets it wants this frame ({@link #add}); each is drawn
 * as the aurora ({@link Aurora}, at most 96 quads) in one of two placements ({@link SkySheet}):
 * <ul>
 * <li>{@link Sheet#sky}: the sky layer, travelling with the camera inside the far plane like the sun;</li>
 * <li>{@link Sheet#world}: the world-fixed curtain, the default ({@code render.skyLayer = world}) that works with every
 * shader pack.</li>
 * </ul>
 * A building's sky light asks {@link Sheet#configured} and gets the placement {@code render.skyLayer} names;
 * {@code /fluxecho probe sky} draws both side by side so the user can judge the sky layer under their pack (brightness,
 * fog, in front of or behind the clouds) before the default is flipped in a patch.
 * <p>
 * Drawn like every light of the city: after the world, skipped in the shadow pass, additive, no depth writes, quads
 * only with a normal on every vertex towards the camera, registered with {@link FarDraw}; the aurora is a pure
 * function of the world time, so every draw of a frame is the same.
 */
@SideOnly(Side.CLIENT)
public final class SkyLayer {

    /** Lists the sheets to draw now. Called while drawing: it must only read. */
    @FunctionalInterface
    public interface Source {

        void list(World w, List<Sheet> out);
    }

    /** One aurora to draw: the sky layer facing a direction, or a curtain hung at an anchor. */
    public static final class Sheet {

        final boolean sky;
        final double ax, ay, az, dx, dz;

        private Sheet(boolean sky, double ax, double ay, double az, double dx, double dz) {
            this.sky = sky;
            this.ax = ax;
            this.ay = ay;
            this.az = az;
            this.dx = dx;
            this.dz = dz;
        }

        /** The sky layer, its middle along the horizontal direction (dx, dz). */
        public static Sheet sky(double dx, double dz) {
            return new Sheet(true, 0, 0, 0, dx, dz);
        }

        /** The world-fixed curtain of an anchor, hung along the horizontal direction (dx, dz) from it. */
        public static Sheet world(double ax, double ay, double az, double dx, double dz) {
            return new Sheet(false, ax, ay, az, dx, dz);
        }

        /** The placement {@code render.skyLayer} names for a light of the sky belonging to an anchor. */
        public static Sheet configured(double ax, double ay, double az, double dx, double dz) {
            return skyMode() ? sky(dx, dz) : world(ax, ay, az, dx, dz);
        }
    }

    private static final List<Source> SOURCES = new CopyOnWriteArrayList<>();
    private static boolean off;

    private SkyLayer() {}

    /** Whether {@code render.skyLayer} asks for the sky layer. */
    public static boolean skyMode() {
        return RenderConfig.SKY_SKY.equals(RenderConfig.skyLayer);
    }

    /** Adds a source of sheets. */
    public static void add(Source s) {
        SOURCES.add(s);
    }

    /** Draws after the world and behind light gates; called once by the client proxy. */
    public static void register() {
        SkyLayer h = new SkyLayer();
        MinecraftForge.EVENT_BUS.register(h);
        FarDraw.add(h::onRenderLast);
    }

    @SubscribeEvent
    public void onRenderLast(RenderWorldLastEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (off || ShaderCompat.shadowPass() || mc.theWorld == null || SOURCES.isEmpty()) return;
        List<Sheet> sheets = new ArrayList<>();
        for (Source s : SOURCES) s.list(mc.theWorld, sheets);
        if (sheets.isEmpty()) return;
        double t = mc.theWorld.getTotalWorldTime() + e.partialTicks;
        try {
            Shapes.begin(true);
            for (Sheet s : sheets) draw(s, t, mc.gameSettings.renderDistanceChunks);
        } catch (Throwable ex) {
            off = true;
            FluxEcho.LOG.error("Drawing the sky layer failed; it is left out until the game restarts", ex);
        } finally {
            Shapes.end();
        }
    }

    /** One aurora, relative to the camera. */
    private static void draw(Sheet s, double t, int renderChunks) {
        double cx = RenderManager.renderPosX, cy = RenderManager.renderPosY, cz = RenderManager.renderPosZ;
        List<double[][]> quads;
        double ox, oy, oz;
        if (s.sky) {
            quads = Aurora.quads(t, SkySheet.sky(s.dx, s.dz, renderChunks), new double[] { 0, 0, 0 });
            ox = oy = oz = 0;
        } else {
            quads = Aurora.quads(t, SkySheet.world(s.ax, s.ay, s.az, s.dx, s.dz), new double[] { cx, cy, cz });
            ox = cx;
            oy = cy;
            oz = cz;
        }
        Tessellator tes = Tessellator.instance;
        tes.startDrawing(GL11.GL_QUADS);
        for (double[][] q : quads) for (double[] v : q) {
            tes.setNormal((float) v[3], (float) v[4], (float) v[5]);
            tes.setColorRGBA_I((int) v[6], (int) Math.round(v[7] * 255));
            tes.addVertex(v[0] - ox, v[1] - oy, v[2] - oz);
        }
        tes.draw();
    }
}
