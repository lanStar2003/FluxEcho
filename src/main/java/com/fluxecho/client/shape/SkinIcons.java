package com.fluxecho.client.shape;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

import com.fluxecho.FluxEcho;
import com.fluxecho.config.StyleConfig;
import com.fluxecho.logic.shape.Family;
import com.fluxecho.logic.shape.Form;
import com.fluxecho.logic.shape.Nbhd;
import com.fluxecho.logic.shape.Overlay;
import com.fluxecho.logic.shape.Quad;
import com.fluxecho.logic.shape.SkinBlocks;
import com.fluxecho.skin.ShapeAccess;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The textures of the new material families (0.17.0, style FINAL §3.5, §3.6), as {@code tools/HullTextures.java} and
 * {@code tools/SkinTextures.java} draw them into {@code textures/blocks/skin/}: per material (hull, footing, moss
 * bronze, white marble, ink, paper) its body and top, its seam, floor and relief patterns, its windows, lamps, light
 * seams, keel, spout, planter and grate faces, and every glow in two layers, a white core and a grey edge the renderer
 * tints with the building's accent.
 * <p>
 * <b>Patterns span cells.</b> Every texture is 32 pixels a cell; a pattern that spans 2 × 2 or 4 × 4 cells (the hull's
 * seams, the footing's coursed masonry, the marble's veins, the reliefs) is drawn whole by the generator and cut into
 * one 32-pixel sprite a cell ({@code <name>_<u>_<v>}). A face takes the sprite of its place in the pattern from the
 * world position along the face's texture axes ({@link Quad}: {@code uAxis}, {@code vAxis}), so the pattern runs on
 * across blocks, whole cubes and shapes alike, and every sprite is an ordinary atlas sprite (no icon of our own reaches
 * vanilla's or Angelica's renderer). Curved faces, whose texture coordinates follow the curve, take the body's first
 * sprite.
 */
@SideOnly(Side.CLIENT)
public final class SkinIcons {

    /** A texture across {@code span × span} cells: one sprite a cell, row by row. */
    static final class Pattern {

        final int span;
        final IIcon[] cells;

        Pattern(IIconRegister r, String name, int span) {
            this.span = span;
            cells = new IIcon[span * span];
            for (int v = 0; v < span; v++) for (int u = 0; u < span; u++) cells[v * span + u] = r
                .registerIcon(FluxEcho.MODID + ":skin/" + (span == 1 ? name : name + "_" + u + "_" + v));
        }

        /** The sprite of pattern cell {@code (u, v)}, each taken modulo the span. */
        IIcon at(int u, int v) {
            return cells[Math.floorMod(v, span) * span + Math.floorMod(u, span)];
        }
    }

    /** A material's textures; a null pattern falls back to the body. */
    static final class Mat {

        Pattern body, top, seam, floor, relief, window, lamp, lightSeam, keel, spout, planter, grate;
        /** The glows by overlay: the white core and the grey edge; null where the material has none. */
        final IIcon[] core = new IIcon[Overlay.values().length], edge = new IIcon[Overlay.values().length];

        void glow(IIconRegister r, String mat, Overlay o, String name) {
            core[o.ordinal()] = r.registerIcon(FluxEcho.MODID + ":skin/" + mat + "/" + name + "_glow_core");
            edge[o.ordinal()] = r.registerIcon(FluxEcho.MODID + ":skin/" + mat + "/" + name + "_glow_edge");
        }
    }

    private static final Map<String, Mat> MATS = new ConcurrentHashMap<>();

    /** The bed's and the hull-number tile's textures (fitting 14, deck 13). */
    private static IIcon bedSide, bedTop, bedTopCore, bedTopEdge, bedSideCore, bedSideEdge, numberTop, numberSide;

    /** The texture axes of each cell face in face order ({@code ForgeDirection}), as {@code ShapeGeo} gives them. */
    private static final int[][] FACE_AXES = { { 1, 3 }, { 1, 3 }, { -1, -2 }, { 1, -2 }, { 3, -2 }, { -3, -2 } };

    private SkinIcons() {}

    // ---- registering

    /** Registers the textures of a family's materials (called by both of its blocks; the atlas keeps one of each). */
    public static void register(IIconRegister r, Family f) {
        switch (f) {
            case HULL:
                MATS.put("hull", hull(r));
                break;
            case FOOTING:
                MATS.put("footing", footing(r));
                break;
            case SKIN_GARDEN:
                MATS.put("moss", skin(r, "moss", 2, "lamp", true, false));
                MATS.put("marble", skin(r, "marble", 4, "lamp", false, false));
                break;
            default:
                MATS.put("ink", skin(r, "ink", 2, "lamp", false, true));
                MATS.put("paper", skin(r, "paper", 2, "lantern", false, false));
        }
    }

    private static Mat hull(IIconRegister r) {
        Mat m = new Mat();
        m.body = m.top = new Pattern(r, "hull/whole", 1);
        m.seam = new Pattern(r, "hull/seam", 2);
        m.floor = new Pattern(r, "hull/floor", 2);
        m.relief = new Pattern(r, "hull/relief", 4);
        m.window = new Pattern(r, "hull/window", 1);
        m.lamp = new Pattern(r, "hull/lamp", 1);
        m.lightSeam = new Pattern(r, "hull/light_seam", 1);
        m.keel = new Pattern(r, "hull/keel", 1);
        m.glow(r, "hull", Overlay.LINE, "light_seam");
        m.glow(r, "hull", Overlay.KEEL, "keel");
        m.glow(r, "hull", Overlay.KEEL_TIP, "keel_tip");
        m.glow(r, "hull", Overlay.LAMP, "lamp");
        m.glow(r, "hull", Overlay.NOSE, "nose");
        return m;
    }

    private static Mat footing(IIconRegister r) {
        Mat m = new Mat();
        m.body = new Pattern(r, "footing/side", 2);
        m.top = new Pattern(r, "footing/top", 2);
        m.seam = new Pattern(r, "footing/seam", 2);
        m.floor = new Pattern(r, "footing/floor", 2);
        m.relief = new Pattern(r, "footing/relief", 4);
        m.lightSeam = new Pattern(r, "footing/light_seam", 1);
        m.spout = new Pattern(r, "footing/spout", 1);
        m.planter = new Pattern(r, "footing/planter", 1);
        m.grate = new Pattern(r, "footing/grate", 1);
        m.glow(r, "footing", Overlay.LINE, "light_seam");
        m.glow(r, "footing", Overlay.NOSE, "nose");
        m.glow(r, "footing", Overlay.LIP, "lip");
        return m;
    }

    private static Mat skin(IIconRegister r, String mat, int span, String lamp, boolean window, boolean grate) {
        Mat m = new Mat();
        m.body = m.top = new Pattern(r, mat + "/whole", span);
        m.seam = new Pattern(r, mat + "/seam", 2);
        m.lightSeam = new Pattern(r, mat + "/light_seam", 1);
        m.lamp = new Pattern(r, mat + "/" + lamp, 1);
        if (window) m.window = new Pattern(r, mat + "/window", 1);
        if (grate) m.grate = new Pattern(r, mat + "/grate", 1);
        m.glow(r, mat, Overlay.LINE, "light_seam");
        m.glow(r, mat, Overlay.LAMP, lamp);
        return m;
    }

    /** Registers the planting bed's textures (fitting 14), from {@code BlockFitting}. */
    public static void registerBed(IIconRegister r) {
        bedSide = r.registerIcon(FluxEcho.MODID + ":skin/fitting/bed_side");
        bedTop = r.registerIcon(FluxEcho.MODID + ":skin/fitting/bed_top");
        bedTopCore = r.registerIcon(FluxEcho.MODID + ":skin/fitting/bed_top_glow_core");
        bedTopEdge = r.registerIcon(FluxEcho.MODID + ":skin/fitting/bed_top_glow_edge");
        bedSideCore = r.registerIcon(FluxEcho.MODID + ":skin/fitting/bed_side_glow_core");
        bedSideEdge = r.registerIcon(FluxEcho.MODID + ":skin/fitting/bed_side_glow_edge");
    }

    /** Registers the hull-number tile's textures (deck 13), from {@code BlockDeck}. */
    public static void registerHullNumber(IIconRegister r) {
        numberTop = r.registerIcon(FluxEcho.MODID + ":skin/deck/hull_number_top");
        numberSide = r.registerIcon(FluxEcho.MODID + ":skin/deck/hull_number_side");
    }

    // ---- the bed and the hull-number tile

    /** The planting bed's face: soil on top, the trough's side elsewhere. */
    public static IIcon bed(int side) {
        return side == 1 ? bedTop : bedSide;
    }

    /** The planting bed's warm lip: the core ({@code edge} false) or the edge layer, on top or on a side. */
    public static IIcon bedGlow(int side, boolean edge) {
        if (side == 1) return edge ? bedTopEdge : bedTopCore;
        return edge ? bedSideEdge : bedSideCore;
    }

    /** The hull-number tile's face: the octagonal badge on top, the footing's stone elsewhere. */
    public static IIcon hullNumber(int side) {
        return side == 1 ? numberTop : numberSide;
    }

    // ---- choosing a texture

    /** The material of a family's shape number; null before the textures are registered. */
    static Mat mat(Family f, int meta) {
        return MATS.get(SkinBlocks.material(f, meta & 15));
    }

    /**
     * The pattern a face of a shape shows: {@code horizontal} for a face projected onto the ground plane (tops,
     * bottoms, slopes nearer flat), {@code up} for a top face, {@code overlay} what lies on it (a spout's mouth).
     */
    static Pattern pattern(Mat m, Form form, boolean horizontal, boolean up, Overlay overlay) {
        Pattern base = horizontal ? m.top : m.body, p;
        switch (form) {
            case SEAM:
                p = m.seam;
                break;
            case FLOOR:
                p = m.floor;
                break;
            case RELIEF:
                p = m.relief;
                break;
            case WINDOW:
                p = m.window;
                break;
            case GRATE:
                p = m.grate;
                break;
            case LAMP:
                p = horizontal ? null : m.lamp;
                break;
            case LIGHT_SEAM:
                p = horizontal ? null : m.lightSeam;
                break;
            case KEEL:
                p = horizontal ? null : m.keel;
                break;
            case SPOUT:
                p = overlay == Overlay.SPOUT ? m.spout : null;
                break;
            case PLANTER:
                p = up ? m.planter : null;
                break;
            default:
                p = null;
        }
        return p != null ? p : base;
    }

    /** The sprite of a quad of a shape at block {@code (x, y, z)}, and its place in the pattern. */
    static IIcon quad(Family f, int meta, Form form, Quad q, int x, int y, int z) {
        Mat m = mat(f, meta);
        if (m == null) return null;
        if (q.uAxis == 0 || q.vAxis == 0) return m.body.at(0, 0);
        boolean horizontal = q.uAxis == 1 && q.vAxis == 3;
        Pattern p = pattern(m, form, horizontal, q.face == 1, q.overlay);
        return p.at(offset(q.uAxis, x, y, z), offset(q.vAxis, x, y, z));
    }

    /** The cell of the pattern along a texture axis: the block position along it, signed, before the modulo. */
    static int offset(int axis, int x, int y, int z) {
        int c = Math.abs(axis) == 1 ? x : Math.abs(axis) == 2 ? y : z;
        return axis > 0 ? c : -c;
    }

    /** A sprite for an item, a particle or a face without a world: the face's pattern, its first cell. */
    public static IIcon icon(Family f, int meta, Form form, int side) {
        Mat m = mat(f, meta);
        if (m == null) return null;
        return pattern(m, form, side <= 1, side == 1, Overlay.NONE).at(0, 0);
    }

    /**
     * A whole cube's face in the world ({@code Block.getIcon(IBlockAccess, ...)}, which vanilla's renderer asks): the
     * sprite of its place in the pattern; a spout's mouth on a side that opens to the air or to water.
     */
    public static IIcon face(IBlockAccess w, int x, int y, int z, Family f, int meta, Form form, int side) {
        Mat m = mat(f, meta);
        if (m == null) return null;
        Overlay o = Overlay.NONE;
        if (form == Form.SPOUT && side >= 2) {
            int[] d = OFFSETS[side];
            int c = ShapeAccess.code(w, x + d[0], y + d[1], z + d[2], f, ShapeAccess.hidden(w));
            if (c == Nbhd.OPEN || c == Nbhd.WATER) o = Overlay.SPOUT;
        }
        int[] a = FACE_AXES[side];
        return pattern(m, form, side <= 1, side == 1, o).at(offset(a[0], x, y, z), offset(a[1], x, y, z));
    }

    /** The glow sprites of an overlay on a material: the core ({@code edge} false) or the edge; null if none. */
    static IIcon glow(Family f, int meta, Overlay o, boolean edge) {
        Mat m = mat(f, meta);
        if (m == null) return null;
        IIcon[] layer = edge ? m.edge : m.core;
        IIcon i = layer[o.ordinal()];
        if (i == null && (o == Overlay.KEEL || o == Overlay.KEEL_TIP || o == Overlay.NOSE)) {
            // a keel or a nose is drawn in the hull's light whatever it is made of
            Mat hull = MATS.get("hull");
            i = hull == null ? null : (edge ? hull.edge : hull.core)[o.ordinal()];
        }
        return i;
    }

    /** The vertex colour a family's material is drawn in: the hull at {@code style.hullBrightness}, the rest white. */
    public static int tint(Family f) {
        if (f != Family.HULL) return 0xFFFFFF;
        int v = (int) Math.round(255 * Math.max(StyleConfig.HULL_MIN, Math.min(1, StyleConfig.hullBrightness)));
        return v << 16 | v << 8 | v;
    }

    private static final int[][] OFFSETS = { { 0, -1, 0 }, { 0, 1, 0 }, { 0, 0, -1 }, { 0, 0, 1 }, { -1, 0, 0 },
        { 1, 0, 0 } };
}
