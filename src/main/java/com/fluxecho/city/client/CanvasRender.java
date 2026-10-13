package com.fluxecho.city.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.fluxecho.city.TileCityModule;
import com.fluxecho.logic.CanvasLook;
import com.fluxecho.render.Shapes;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The items a module's canvas units paint (0.14.0, CITY.md §1.4 and §4.2 Visuals; where they go is
 * {@link CanvasLook}'s): every unit made of canvas paints the item in its slot large and flat on its field, over a
 * ground washed in the item's own colour, the icon posterised to a painted look ({@link Paintings}); an empty one shows
 * a faint grid along its canvas cells in the module's colour. The nearest {@value CityRender#ITEMS} within
 * {@link CityRender#NEAR} blocks of the camera are painted whole, a painting further off is only its ground's patch of
 * colour (the far rule); a field looking away from the camera is left out. Drawn while the camera is near the module,
 * as the sockets' samples are ({@link CityRender}); the Dual-foil Vault's facade collage paints its tiles the same way
 * ({@link #paint}).
 * <p>
 * Shader-safe as {@link CityRender}: in the module's frame relative to the camera, quads only with the viewer's normal
 * on every vertex, no depth writes, texturing switched on by hand. A painted icon is its own small texture the texture
 * manager knows ({@link Paintings}); one that could not be painted is drawn from the atlas of its sheet (blocks or
 * items), bound as the sockets' samples are, each render pass in its colour. Nothing here counts or moves: a frame
 * drawn again behind a light gate is the same.
 */
@SideOnly(Side.CLIENT)
final class CanvasRender {

    /**
     * The canvas's own navy, which a painting's ground is washed into, and the ground of an item of no known colour.
     */
    private static final int NAVY = 0x1A2636, UNKNOWN = 0x3A4656;
    /** How much navy a near painting's ground keeps, and a far painting's patch. */
    private static final double GROUND_NAVY = 0.55, PATCH_NAVY = 0.3;
    /** How far a picture lies in front of its ground (blocks), and the grid's lines' width. */
    private static final double OVER = 0.004, GRID_WIDTH = 0.03;

    /** Each module kind's canvas fields, worked out once from its shape. */
    private static final Map<String, List<CanvasLook.Field>> FIELDS = new ConcurrentHashMap<>();

    private CanvasRender() {}

    /** A module kind's canvas fields ({@link CanvasLook#fields}). */
    static List<CanvasLook.Field> fields(TileCityModule m) {
        return FIELDS.computeIfAbsent(m.moduleKey(), k -> CanvasLook.fields(m.build(), m.layout()));
    }

    /**
     * Draws a module's canvases; {@code p} is its distance fade times how far it has formed, {@code cam} the camera in
     * its frame.
     */
    static void draw(TileCityModule m, CityFrame f, float p, double[] cam) {
        List<CanvasLook.Field> fields = fields(m);
        if (fields.isEmpty() || p <= 0.02f) return;
        List<CanvasLook.Field> painted = new ArrayList<>(), empty = new ArrayList<>();
        List<ItemStack> stacks = new ArrayList<>();
        List<Double> dist = new ArrayList<>();
        double reach = CityRender.NEAR * CityRender.NEAR;
        for (CanvasLook.Field fl : fields) {
            double[] c = fl.middle();
            // a field whose face looks away from the camera is behind its wall
            if ((cam[0] - c[0]) * fl.face[0] + (cam[2] - c[2]) * fl.face[1] <= 0) continue;
            double d = sq(cam[0] - c[0]) + sq(cam[1] - c[1]) + sq(cam[2] - c[2]);
            ItemStack s = m.sample(fl.slot);
            if (s == null || s.getItem() == null) {
                if (d <= reach) empty.add(fl);
                continue;
            }
            painted.add(fl);
            stacks.add(s);
            dist.add(d);
        }
        // the nearest within reach whole, the rest their patches
        boolean[] whole = new boolean[painted.size()];
        Integer[] order = new Integer[painted.size()];
        for (int i = 0; i < order.length; i++) order[i] = i;
        java.util.Arrays.sort(order, (a, b) -> Double.compare(dist.get(a), dist.get(b)));
        for (int k = 0; k < order.length && k < CityRender.ITEMS; k++) whole[order[k]] = dist.get(order[k]) <= reach;
        if (!empty.isEmpty()) grid(f, empty, m.moduleColor(), p);
        if (!painted.isEmpty()) paint(f, painted, stacks, whole, p, new int[] { Paintings.BUILDS });
    }

    /** The faint grid of empty canvases: a line of the module's colour along every edge of their canvas cells. */
    private static void grid(CityFrame f, List<CanvasLook.Field> fields, int rgb, float p) {
        Shapes.begin(true);
        Tessellator tes = CityFrame.start();
        tes.setColorRGBA_I(rgb, CityFrame.alpha255(0.16f * p));
        for (CanvasLook.Field fl : fields) {
            for (double[] l : CanvasLook.grid(fl)) {
                double[] a = fl.at(l[0], l[1]), b = fl.at(l[2], l[3]);
                f.line(tes, a[0], a[1], a[2], b[0], b[1], b[2], GRID_WIDTH);
            }
        }
        tes.draw();
        Shapes.end();
    }

    /**
     * Paints items on fields ({@code stacks[i]} on {@code fields[i]}): first the grounds (a whole painting's over its
     * {@link CanvasLook#ground}, washed in the item's colour; a far one's, {@code whole[i]} false, a brighter patch of
     * that colour and nothing more), then the whole ones' pictures over them, each render pass of the item (at most
     * {@link CanvasLook#PASSES}) in its colour. {@code builds} bounds how many icons are painted now
     * ({@link Paintings}).
     */
    static void paint(CityFrame f, List<CanvasLook.Field> fields, List<ItemStack> stacks, boolean[] whole, float p,
        int[] builds) {
        int n = fields.size();
        int[] colour = new int[n];
        for (int i = 0; i < n; i++) colour[i] = colour(stacks.get(i), builds);
        // the grounds and the far patches, blended over the canvas
        Shapes.begin(false);
        Tessellator tes = CityFrame.start();
        for (int i = 0; i < n; i++) {
            CanvasLook.Field fl = fields.get(i);
            int c = colour[i];
            if (!whole[i] && c < 0) continue;
            int rgb = Shapes.mix(c < 0 ? UNKNOWN : c, NAVY, whole[i] ? GROUND_NAVY : PATCH_NAVY);
            tes.setColorRGBA_I(rgb, CityFrame.alpha255((whole[i] ? 0.92f : 0.85f) * p));
            double[] g = CanvasLook.ground(fl);
            double[][] q = { fl.at(g[0], g[1]), fl.at(g[2], g[1]), fl.at(g[2], g[3]), fl.at(g[0], g[3]) };
            for (double[] v : q) f.vertex(tes, v[0], v[1], v[2]);
        }
        tes.draw();
        Shapes.end();
        // the pictures
        CityRender.worldState();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.05f);
        for (int i = 0; i < n; i++) {
            if (!whole[i]) continue;
            try {
                picture(f, fields.get(i), stacks.get(i), p, builds);
            } catch (RuntimeException e) {
                // a modded item whose picture cannot be had here paints none; a batch it broke off is closed
                try {
                    Tessellator.instance.draw();
                } catch (RuntimeException ignored) {
                    // nothing was open
                }
            }
        }
    }

    /** An item's colour (RGB) as its first pass paints it: its painting's average tinted by the pass's colour. */
    private static int colour(ItemStack s, int[] builds) {
        try {
            Item item = s.getItem();
            Paintings.Painting pt = Paintings.get(item.getIcon(s, 0), item.getSpriteNumber(), builds);
            if (pt == null || pt.colour < 0) return -1;
            return multiply(pt.colour, item.getColorFromItemStack(s, 0));
        } catch (RuntimeException e) {
            return -1;
        }
    }

    /** One painting's picture: each pass's icon, painted or straight from its atlas, a square over its ground. */
    private static void picture(CityFrame f, CanvasLook.Field fl, ItemStack s, float p, int[] builds) {
        Item item = s.getItem();
        int sheet = item.getSpriteNumber();
        int passes = item.requiresMultipleRenderPasses() ? item.getRenderPasses(s.getItemDamage()) : 1;
        passes = Math.min(passes, CanvasLook.PASSES);
        double[] r = CanvasLook.picture(fl);
        double ox = fl.face[0] * OVER, oz = fl.face[1] * OVER;
        double[] tl = fl.at(r[0], r[3]), bl = fl.at(r[0], r[1]), br = fl.at(r[2], r[1]), tr = fl.at(r[2], r[3]);
        for (double[] v : new double[][] { tl, bl, br, tr }) {
            v[0] += ox;
            v[2] += oz;
        }
        Minecraft mc = Minecraft.getMinecraft();
        int alpha = CityFrame.alpha255(0.95f * p);
        for (int pass = 0; pass < passes; pass++) {
            IIcon ic = item.getIcon(s, pass);
            if (ic == null) continue;
            Paintings.Painting pt = Paintings.get(ic, sheet, builds);
            ResourceLocation tex = pt == null ? null : pt.texture();
            double u0, v0, u1, v1;
            if (tex != null) {
                u0 = 0;
                v0 = 0;
                u1 = pt.u1;
                v1 = pt.v1;
            } else {
                tex = sheet == 0 ? TextureMap.locationBlocksTexture : TextureMap.locationItemsTexture;
                u0 = ic.getMinU();
                v0 = ic.getMinV();
                u1 = ic.getMaxU();
                v1 = ic.getMaxV();
            }
            mc.getTextureManager()
                .bindTexture(tex);
            Tessellator tes = Tessellator.instance;
            tes.startDrawingQuads();
            CityFrame.normal(tes);
            tes.setColorRGBA_I(item.getColorFromItemStack(s, pass) & 0xFFFFFF, alpha);
            f.vertexUV(tes, tl, u0, v0);
            f.vertexUV(tes, bl, u0, v1);
            f.vertexUV(tes, br, u1, v1);
            f.vertexUV(tes, tr, u1, v0);
            tes.draw();
        }
    }

    /** Two colours (RGB) multiplied channel by channel, as a tint lies on a texture. */
    private static int multiply(int a, int b) {
        int r = (a >> 16 & 0xFF) * (b >> 16 & 0xFF) / 255, g = (a >> 8 & 0xFF) * (b >> 8 & 0xFF) / 255,
            bl = (a & 0xFF) * (b & 0xFF) / 255;
        return r << 16 | g << 8 | bl;
    }

    private static double sq(double v) {
        return v * v;
    }
}
