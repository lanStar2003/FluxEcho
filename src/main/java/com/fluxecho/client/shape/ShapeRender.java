package com.fluxecho.client.shape;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

import org.lwjgl.opengl.GL11;

import com.fluxecho.client.ShaderCompat;
import com.fluxecho.frame.Formed;
import com.fluxecho.logic.shape.Form;
import com.fluxecho.logic.shape.Overlay;
import com.fluxecho.logic.shape.Quad;
import com.fluxecho.logic.shape.ShapeGeo;
import com.fluxecho.logic.skin.SkinPalette;
import com.fluxecho.skin.BlockSkin;
import com.fluxecho.skin.ShapeAccess;

import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The one renderer of every new material family (0.17.0, style FINAL §3.1, §3.3.6, §3.6): it draws the blocks into
 * the chunk mesh, so they cast shadows, show in water and take the shader pack's light (style §8.4 #8), and draws them
 * as items.
 * <ul>
 * <li>A whole-block id is drawn as vanilla draws stone ({@code renderStandardBlock}: culling, light, smooth light),
 * each face with the sprite of its place in its material's pattern ({@link SkinIcons#face}).</li>
 * <li>A shape-block id is drawn from {@link ShapeGeo}: every quad of its shape in its neighbourhood
 * ({@link ShapeAccess#quads}), with a normal at every vertex, its face's light (the neighbour across it, or the cell's
 * own for a face inside the cell), vanilla's flat shading by the normal unless a shader pack lights it, and the
 * pattern's sprite for its place ({@link SkinIcons#quad}).</li>
 * <li>On both, the glows ({@link Overlay}: light seams, keels, lamps, noses, lips) are laid over their faces again at
 * full brightness in two layers (style §3.6): the white core untinted, the grey edge tinted with the light's colour,
 * the building's accent from the box table ({@link ShapeSkin}). Lamps are lit only when the table says so (G6), and
 * nothing glows on a dormant building.</li>
 * <li>A block a formed multiblock hides ({@link Formed#HIDE}) draws nothing, and its neighbours draw their faces
 * toward it (style §3.9.8).</li>
 * </ul>
 * It keeps no state between calls but the caches of {@code ShapeAccess} (concurrent) and reads only blocks, metas and
 * immutable tables, so a chunk builder may call it from any thread. It carries no {@code ThreadSafeISBRH} mark
 * (Angelica
 * is not a compile dependency): Angelica then builds these blocks on the main thread, as it does for every renderer of
 * the mod so far.
 */
@SideOnly(Side.CLIENT)
public final class ShapeRender implements ISimpleBlockRenderingHandler {

    /** Full block and sky light: the glows ignore the light around them. */
    static final int BRIGHT = 0xF000F0;

    private static volatile ShapeSkin skin = ShapeSkin.NONE;
    /** Whether a shader pack is on, asked at most once a second (it lights faces by their normals itself). */
    private static volatile boolean packOn;
    private static volatile long packAsked = Long.MIN_VALUE;

    private final int id;

    public ShapeRender(int id) {
        this.id = id;
    }

    /** Plugs in the box table's answer (the box-table work does, once its table exists); null for none. */
    public static void skin(ShapeSkin s) {
        skin = s == null ? ShapeSkin.NONE : s;
    }

    @Override
    public boolean renderWorldBlock(IBlockAccess w, int x, int y, int z, Block block, int modelId, RenderBlocks r) {
        if (!(block instanceof BlockSkin b)) return false;
        ShapeAccess.Hidden hidden = ShapeAccess.hidden(w);
        if (hidden.at(x, y, z)) return false;
        int meta = w.getBlockMetadata(x, y, z);
        Form form = b.form(meta);
        boolean breaking = r.hasOverrideBlockTexture();
        if (b.whole) {
            boolean drawn = r.renderStandardBlock(block, x, y, z);
            if (breaking || !glows(form)) return drawn;
            List<Quad> qs = ShapeAccess.quads(w, x, y, z, hidden);
            return glow(w, x, y, z, b, meta, qs, x, y, z, true) || drawn;
        }
        List<Quad> qs = ShapeAccess.quads(w, x, y, z, hidden);
        if (qs.isEmpty()) return false;
        Tessellator t = Tessellator.instance;
        int tint = SkinIcons.tint(b.family);
        boolean shade = !packOn();
        for (Quad q : qs) {
            IIcon icon = breaking ? r.overrideBlockTexture : SkinIcons.quad(b.family, meta, form, q, x, y, z);
            if (icon == null) continue;
            int light = q.face >= 0
                ? block.getMixedBrightnessForBlock(w, x + DX[q.face], y + DY[q.face], z + DZ[q.face])
                : block.getMixedBrightnessForBlock(w, x, y, z);
            t.setBrightness(light);
            emit(t, q, icon, x, y, z, tint, shade);
        }
        if (!breaking) glow(w, x, y, z, b, meta, qs, x, y, z, true);
        return true;
    }

    /** Whether a whole cube of the shape has glows to lay over it (the others need no neighbourhood at all). */
    static boolean glows(Form form) {
        return form == Form.LIGHT_SEAM || form == Form.KEEL || form == Form.LAMP || form == Form.POOL_LIP;
    }

    /**
     * Lays the glows of the quads that carry one, at full brightness, in two layers. {@code (ox, oy, oz)} is where
     * the quads are drawn (the block in the world; the origin for an item, with {@code world} false: lit lamps, no
     * table). Returns whether anything was drawn.
     */
    private static boolean glow(IBlockAccess w, int x, int y, int z, BlockSkin b, int meta, List<Quad> qs, double ox,
        double oy, double oz, boolean world) {
        ShapeSkin.Look look = null;
        boolean any = false;
        Tessellator t = Tessellator.instance;
        for (Quad q : qs) {
            Overlay o = q.overlay;
            if (o == Overlay.NONE || o == Overlay.SPOUT) continue;
            if (look == null) {
                ShapeSkin.Look l = world ? skin.at(w, x, y, z) : null;
                look = l == null ? (world ? ShapeSkin.Look.DEFAULT : ITEM) : l;
            }
            if (o == Overlay.LAMP ? !look.lamps : !look.awake) continue;
            IIcon core = SkinIcons.glow(b.family, meta, o, false), edge = SkinIcons.glow(b.family, meta, o, true);
            if (core == null || edge == null) continue;
            if (world) t.setBrightness(BRIGHT);
            int colour = edgeColour(o, look);
            if (o == Overlay.LIP && q.face == 1) {
                // the lip runs along every side the pool lies on: the texture's line is on its -v edge, turned to each
                for (int bit = 0; bit < 4; bit++) {
                    if ((q.conn & 1 << bit) == 0) continue;
                    emitAt(t, q, core, ox, oy, oz, 0xFFFFFF, bit + 1);
                    emitAt(t, q, edge, ox, oy, oz, colour, bit + 1);
                }
            } else {
                emitAt(t, q, core, ox, oy, oz, 0xFFFFFF, 0);
                emitAt(t, q, edge, ox, oy, oz, colour, 0);
            }
            any = true;
        }
        return any;
    }

    /** An item's look: its lamp lit, so the item shows what it is. */
    private static final ShapeSkin.Look ITEM = new ShapeSkin.Look(SkinPalette.NONE, true, true, SkinPalette.NEXUS_CYAN);

    /** The colour a glow's edge is tinted (style §3.6, G6). */
    static int edgeColour(Overlay o, ShapeSkin.Look look) {
        switch (o) {
            case KEEL:
            case KEEL_TIP:
                return look.keel;
            case LINE:
            case LAMP:
                return look.light();
            default:
                // a step's nose and a pool's lip are the light at a person's scale
                return SkinPalette.WARM_WHITE;
        }
    }

    /** A glow quad, unshaded, in one colour. */
    private static void emitAt(Tessellator t, Quad q, IIcon icon, double ox, double oy, double oz, int rgb, int turn) {
        for (int i = 0; i < 4; i++) {
            t.setColorOpaque_I(rgb);
            t.setNormal((float) q.normal[3 * i], (float) q.normal[3 * i + 1], (float) q.normal[3 * i + 2]);
            double u = tex(q, i, 0), v = tex(q, i, 1), tu = u, tv = v;
            switch (turn) {
                case 1: // toward -u: the line along the face's left edge
                    tu = v;
                    tv = u;
                    break;
                case 2: // toward +u
                    tu = v;
                    tv = 1 - u;
                    break;
                case 4: // toward +v (the bit 8 side)
                    tv = 1 - v;
                    break;
                default: // toward -v (the bit 4 side), and no turn
            }
            t.addVertexWithUV(
                ox + q.x(i),
                oy + q.y(i),
                oz + q.z(i),
                icon.getInterpolatedU(16 * tu),
                icon.getInterpolatedV(16 * tv));
        }
    }

    /**
     * A material quad: per vertex its normal, its shade (vanilla's flat shading by the normal, or none under a shader
     * pack, which shades by the normal itself) times the family's tint, and its texture coordinates in the sprite.
     */
    private static void emit(Tessellator t, Quad q, IIcon icon, double ox, double oy, double oz, int tint,
        boolean shade) {
        float tr = (tint >> 16 & 255) / 255f, tg = (tint >> 8 & 255) / 255f, tb = (tint & 255) / 255f;
        for (int i = 0; i < 4; i++) {
            float nx = (float) q.normal[3 * i], ny = (float) q.normal[3 * i + 1], nz = (float) q.normal[3 * i + 2];
            float s = shade ? shade(nx, ny, nz) : 1f;
            t.setColorOpaque_F(s * tr, s * tg, s * tb);
            t.setNormal(nx, ny, nz);
            t.addVertexWithUV(
                ox + q.x(i),
                oy + q.y(i),
                oz + q.z(i),
                icon.getInterpolatedU(16 * tex(q, i, 0)),
                icon.getInterpolatedV(16 * tex(q, i, 1)));
        }
    }

    /**
     * Vanilla's flat shading for a face with unit normal {@code n}: 1 on top, 0.5 underneath, 0.8 north and south, 0.6
     * east and west, blended by the squares of the normal's parts, so a curve shades smoothly between them.
     */
    static float shade(float nx, float ny, float nz) {
        return ny * ny * (ny > 0 ? 1f : 0.5f) + nx * nx * 0.6f + nz * nz * 0.8f;
    }

    /**
     * Vertex {@code i}'s texture coordinate {@code k} (0 u, 1 v) in the sprite, 0 to 1: {@link #fit} kept in the cell
     * (a rib's brace reaches a little past it).
     */
    static double tex(Quad q, int i, int k) {
        double c = fit(q, i, k);
        return c < 0 ? 0 : c > 1 ? 1 : c;
    }

    /**
     * Vertex {@code i}'s texture coordinate {@code k} in cells: as {@code ShapeGeo} gives it (in blocks), but a
     * soffit's concave arc, a quarter circle of radius 1 and so pi/2 blocks long, fitted into the one cell its sprite
     * covers.
     */
    static double fit(Quad q, int i, int k) {
        double c = q.uv[2 * i + k];
        if (q.surface == Quad.Surface.CONCAVE && (k == 0 ? q.uAxis : q.vAxis) == 0) c *= 2 / Math.PI;
        return c;
    }

    private static boolean packOn() {
        long now = System.nanoTime();
        if (now - packAsked > 1_000_000_000L) {
            packOn = ShaderCompat.packInUse();
            packAsked = now;
        }
        return packOn;
    }

    @Override
    public void renderInventoryBlock(Block block, int meta, int modelId, RenderBlocks r) {
        if (!(block instanceof BlockSkin b)) return;
        Form form = b.form(meta);
        List<Quad> qs = ShapeGeo.item(form);
        GL11.glTranslatef(-0.5f, -0.5f, -0.5f);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        int tint = SkinIcons.tint(b.family);
        for (Quad q : qs) {
            IIcon icon = SkinIcons.quad(b.family, meta, form, q, 0, 0, 0);
            if (icon != null) emit(t, q, icon, 0, 0, 0, tint, false);
        }
        glow(null, 0, 0, 0, b, meta, qs, 0, 0, 0, false);
        t.draw();
        GL11.glTranslatef(0.5f, 0.5f, 0.5f);
    }

    @Override
    public boolean shouldRender3DInInventory(int modelId) {
        return true;
    }

    @Override
    public int getRenderId() {
        return id;
    }

    private static final int[] DX = { 0, 0, 0, 0, -1, 1 }, DY = { -1, 1, 0, 0, 0, 0 }, DZ = { 0, 0, -1, 1, 0, 0 };
}
