package com.fluxecho.city.client;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;

import com.fluxecho.logic.CanvasLook;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The icons the canvases paint (0.14.0, {@link CanvasLook}), each painted once and kept: an icon's picture is read
 * from its texture's file the way the item atlas read it (its first frame), painted ({@link CanvasLook#paint}:
 * posterised, brushed and outlined) into one of a pool of small textures the texture manager knows ({@link #SIZE}
 * square, the painting in its top left corner), so it is bound as any texture is, under a shader pack too; its
 * average colour is kept with it for the ground and the far patch. An icon that cannot be read so (one a mod loads its
 * own way, or no file of that name) is remembered as such and drawn straight from its atlas instead.
 * <p>
 * This is a cache, not a count: what an icon looks like does not change with how often it is drawn, and a frame drawn
 * again behind a light gate finds it the same. At most {@link #BUILDS} new icons are painted per call (the rest are
 * drawn from the atlas until a later frame paints them), and at most {@link #MOST} are kept, the least recently drawn
 * giving its texture to the next. The pool's textures are made once and reused (the texture manager of this game
 * never lets a texture's name go), so the paintings take a bounded memory however many items pass through. Forgotten
 * when the client leaves a world and when the textures are stitched again (a resource pack changed). Client thread
 * only.
 */
@SideOnly(Side.CLIENT)
public final class Paintings {

    /** How many painted icons are kept, and how many a drawing may paint before it draws the rest from the atlas. */
    static final int MOST = 256, BUILDS = 4;
    /** The side (pixels) of a pool texture: the largest painting ({@link CanvasLook#ICON_MAX} painted). */
    static final int SIZE = CanvasLook.ICON_MAX * CanvasLook.SCALE;

    /** One painted icon. */
    static final class Painting {

        /** Its pool texture, or -1 when it could not be painted (it is drawn from its atlas). */
        final int slot;
        /** How far across and down its texture the painting reaches (its texture coordinates' far edges). */
        final double u1, v1;
        /** Its icon's average colour (RGB), or -1 when not known. */
        final int colour;

        Painting(int slot, double u1, double v1, int colour) {
            this.slot = slot;
            this.u1 = u1;
            this.v1 = v1;
            this.colour = colour;
        }

        /** The texture to bind for it, or null when it is drawn from its atlas. */
        ResourceLocation texture() {
            return slot < 0 ? null : POOL.get(slot).name;
        }
    }

    /** A texture of the pool: the dynamic texture and the name the texture manager knows it by. */
    private static final class Slot {

        final DynamicTexture texture;
        final ResourceLocation name;

        Slot(DynamicTexture texture, ResourceLocation name) {
            this.texture = texture;
            this.name = name;
        }
    }

    private static final Painting NONE = new Painting(-1, 0, 0, -1);
    private static final List<Slot> POOL = new ArrayList<>();
    private static final Deque<Integer> FREE = new ArrayDeque<>();

    private static final Map<String, Painting> KEPT = new LinkedHashMap<String, Painting>(64, 0.75f, true) {

        private static final long serialVersionUID = 1L;

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Painting> eldest) {
            if (size() <= MOST) return false;
            release(eldest.getValue());
            return true;
        }
    };

    private Paintings() {}

    /**
     * The painting of an icon on its sheet (0 the block atlas, 1 the item atlas): kept, or painted now while
     * {@code builds[0]} allows (it counts down), else null (this draw takes the atlas's icon).
     */
    static Painting get(IIcon icon, int sheet, int[] builds) {
        if (icon == null) return null;
        String key = sheet + "|" + icon.getIconName();
        Painting p = KEPT.get(key);
        if (p != null) return p;
        if (builds[0] <= 0) return null;
        builds[0]--;
        p = paint(icon, sheet);
        KEPT.put(key, p);
        return p;
    }

    /** Lets every painting go (the client left a world, or the textures were stitched again); the pool stays. */
    public static void clear() {
        List<Painting> all = new ArrayList<>(KEPT.values());
        KEPT.clear();
        for (Painting p : all) release(p);
    }

    private static void release(Painting p) {
        if (p.slot >= 0) FREE.push(p.slot);
    }

    /**
     * A pool texture for a new painting: a free one, or a new one while the pool holds no more than {@link #MOST} (one
     * more than the paintings kept: a new painting takes its texture before the least recent one gives its back).
     */
    private static int slot() {
        if (!FREE.isEmpty()) return FREE.pop();
        if (POOL.size() > MOST) return -1;
        DynamicTexture tex = new DynamicTexture(SIZE, SIZE);
        ResourceLocation name = Minecraft.getMinecraft()
            .getTextureManager()
            .getDynamicTextureLocation("fluxecho_painting", tex);
        POOL.add(new Slot(tex, name));
        return POOL.size() - 1;
    }

    /**
     * Paints an icon from its texture file ({@code <domain>:textures/blocks|items/<path>.png}, as the atlas found it),
     * or gives {@link #NONE} when it cannot: not an atlas sprite, a sprite its mod loads its own way, no such file, an
     * unreadable image, no texture left in the pool.
     */
    private static Painting paint(IIcon icon, int sheet) {
        int slot = -1;
        try {
            if (!(icon instanceof TextureAtlasSprite sprite)) return NONE;
            String name = icon.getIconName();
            if (name == null || name.isEmpty() || "missingno".equals(name)) return NONE;
            Minecraft mc = Minecraft.getMinecraft();
            IResourceManager rm = mc.getResourceManager();
            ResourceLocation base = new ResourceLocation(name);
            if (sprite.hasCustomLoader(rm, base)) return NONE;
            ResourceLocation file = new ResourceLocation(
                base.getResourceDomain(),
                "textures/" + (sheet == 0 ? "blocks" : "items") + "/" + base.getResourcePath() + ".png");
            BufferedImage img;
            try (InputStream in = rm.getResource(file)
                .getInputStream()) {
                img = ImageIO.read(in);
            }
            if (img == null) return NONE;
            int w = img.getWidth(), h = img.getHeight();
            CanvasLook.Image source = CanvasLook.icon(new CanvasLook.Image(w, h, img.getRGB(0, 0, w, h, null, 0, w)));
            CanvasLook.Image painted = CanvasLook.paint(source);
            if (painted.width > SIZE || painted.height > SIZE) return NONE;
            slot = slot();
            if (slot < 0) return NONE;
            DynamicTexture tex = POOL.get(slot).texture;
            int[] data = tex.getTextureData();
            Arrays.fill(data, 0);
            for (int y = 0; y < painted.height; y++)
                System.arraycopy(painted.argb, y * painted.width, data, y * SIZE, painted.width);
            tex.updateDynamicTexture();
            return new Painting(
                slot,
                painted.width / (double) SIZE,
                painted.height / (double) SIZE,
                CanvasLook.average(source));
        } catch (Exception | LinkageError x) {
            // an icon that cannot be painted here is drawn from its atlas; its pool texture goes back
            if (slot >= 0) FREE.push(slot);
            return NONE;
        }
    }
}
