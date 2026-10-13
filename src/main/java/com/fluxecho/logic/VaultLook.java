package com.fluxecho.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Where the Dual-foil Vault's own drawing puts its light (0.14.0, CITY.md §4.2 Visuals), worked out in the vault's
 * frame ({@link DualFoilVaultShape}: x across, y up from the base layer, z from the front). Its canvases paint their
 * items as every canvas does ({@link CanvasLook}); on top of that:
 * <ul>
 * <li>Forming: a parchment-white wash rises up the facade canvas behind a bright line ({@link #sweepQuads}).</li>
 * <li>Formed: the facade canvas, over the portal on the front wall's outer face, shows a collage of the items the
 * vault holds, the most frequent first ({@link #ranking}): nine tiles of falling size, mirrored about the axis
 * ({@link #tile}); the skylights let static shafts of light into the nave, slanting to the front and stopping on the
 * first floor, deck or wall they meet ({@link #shafts}), as bright as the time of day ({@link #daylight}).</li>
 * <li>Docked: the facade's lit frame breathes in parchment ({@link #frame}); the foil's rim shows a faint line.</li>
 * <li>Powered: the foil's rim glows like an event horizon ({@link #rimPoint}, {@link #horizon}): a white-hot line
 * round the black canvas, a bright arc running round it, a soft halo out over its frame and the dark of the foil
 * deepening in from the rim.</li>
 * </ul>
 * Every motion is a function of the world time alone, so a frame drawn again behind a light gate is the same. Pure
 * arithmetic over the shape, so the tests hold the collage on the facade, the shafts in the nave's air, the rim on the
 * foil and the vault within CITY.md §1.3's budgets ({@link #quads}).
 */
public final class VaultLook {

    // ---- the facade

    /** The facade canvas's field (inside its lit frame): its left foot corner x and y and its size, in blocks. */
    public static final int FIELD_X = DualFoilVaultShape.FACADE_X0 + 1, FIELD_Y = DualFoilVaultShape.FACADE_Y0 + 1,
        FIELD_W = DualFoilVaultShape.FACADE_X1 - DualFoilVaultShape.FACADE_X0 - 1,
        FIELD_H = DualFoilVaultShape.FACADE_Y1 - DualFoilVaultShape.FACADE_Y0 - 1;
    /** The z of the front wall's outer face the facade's drawing lies on (a hair in front of it). */
    public static final double FRONT = -CanvasLook.OFF;
    /**
     * The collage's tiles by rank: {u0, v0, side} in the facade's field (u right from its left edge, v up from its
     * foot): the most frequent item's large square in the middle, then pairs mirrored about the axis.
     */
    private static final double[][] TILES = { { 5, 2.5, 7 }, { 0.75, 6.25, 4 }, { 12.25, 6.25, 4 }, { 1.25, 1.25, 3 },
        { 12.75, 1.25, 3 }, { 5.5, 0.25, 2 }, { 9.5, 0.25, 2 }, { 5.5, 9.75, 2 }, { 9.5, 9.75, 2 } };
    /** How many items the collage shows at most. */
    public static final int COLLAGE = TILES.length;

    // ---- the forming sweep

    /** How far below the rising line the facade's wash reaches (blocks). */
    public static final double SWEEP_TRAIL = 3;

    // ---- the frame

    /** The facade's lit frame's middle line: its corners x0, y0, x1, y1 (the frame's cells' middles). */
    public static final double FRAME_X0 = DualFoilVaultShape.FACADE_X0 + 0.5,
        FRAME_X1 = DualFoilVaultShape.FACADE_X1 + 0.5, FRAME_Y0 = DualFoilVaultShape.FACADE_Y0 + 0.5,
        FRAME_Y1 = DualFoilVaultShape.FACADE_Y1 + 0.5;
    /** The ticks of one breath of the frame's light while docked, and of the glint running round it while powered. */
    public static final int BREATH_T = 120, GLINT_T = 400;

    // ---- the shafts

    /** How far a shaft leans to the front (z) for each block it falls. */
    public static final double SLANT = 0.35;
    /** How far a shaft's ends stay in from its strip's ends (blocks). */
    public static final double SHAFT_IN = 0.1;
    /** Where the light comes in: the skylights' glass, the underside of the roof (y). */
    public static final double SHAFT_TOP = DualFoilVaultShape.ROOF_Y;
    /** How bright the shafts are at night (the moon), as a share of noon. */
    public static final double NIGHT = 0.12;

    // ---- the foil's rim

    /** The foil's canvas field: its left foot corner x and y and its size (blocks), on the back wall's inner face. */
    public static final int FOIL_X = DualFoilVaultShape.FOIL_X0 + 1, FOIL_Y = DualFoilVaultShape.FOIL_Y0 + 1,
        FOIL_W = DualFoilVaultShape.FOIL_X1 - DualFoilVaultShape.FOIL_X0 - 1,
        FOIL_H = DualFoilVaultShape.FOIL_Y1 - DualFoilVaultShape.FOIL_Y0 - 1;
    /** The z of the back wall's inner face the rim lies on (a hair in front of it, into the nave). */
    public static final double BACK = DualFoilVaultShape.NICHE_Z - CanvasLook.OFF;
    /** The rim's length round the field (blocks). */
    public static final int PERIMETER = 2 * (FOIL_W + FOIL_H);
    /** The pieces the rim is drawn in near (one a block) and the ticks the bright arc takes round it once. */
    public static final int RIM_PIECES = PERIMETER, RIM_T = 160;
    /** How far the halo reaches out over the frame and the dark in over the foil from the rim (blocks). */
    public static final double HALO = 0.75, DARK = 1.4;

    /** The skylight shafts, worked out once ({@link #shafts}). */
    private static final List<double[]> SHAFTS = Collections.unmodifiableList(buildShafts());

    private VaultLook() {}

    // ---- the collage

    /**
     * Tile {@code rank} of the facade's collage: {x0, y0, x1, y1} in the vault's frame on the front wall's outer face
     * (at {@link #FRONT}), x to the right for someone in front of it.
     */
    public static double[] tile(int rank) {
        double[] t = TILES[rank];
        return new double[] { FIELD_X + t[0], FIELD_Y + t[1], FIELD_X + t[0] + t[2], FIELD_Y + t[1] + t[2] };
    }

    /**
     * Tile {@code rank} of the facade's collage as a canvas field ({@link CanvasLook#rect}) looking out of the front
     * wall: a square whose painting {@link CanvasLook} lays out as any canvas's (its ground the whole tile, its picture
     * in the middle).
     */
    public static CanvasLook.Field tileField(int rank) {
        double[] t = tile(rank);
        return CanvasLook
            .rect(new double[] { t[0], t[1], FRONT }, new int[] { 0, -1 }, (int) TILES[rank][2], (int) TILES[rank][2]);
    }

    /**
     * Which items the collage shows, the most frequent first: for the items held in the vault's slots ({@code keys}, an
     * item's kind or null for an empty slot), the first slot holding each kind, ordered by how many slots hold it (more
     * first; a tie by the slot that holds it first), at most {@link #COLLAGE}.
     */
    public static <T> int[] ranking(List<T> keys) {
        Map<T, int[]> seen = new HashMap<>();
        List<int[]> kinds = new ArrayList<>();
        for (int i = 0; i < keys.size(); i++) {
            T k = keys.get(i);
            if (k == null) continue;
            int[] c = seen.get(k);
            if (c == null) {
                c = new int[] { i, 0 };
                seen.put(k, c);
                kinds.add(c);
            }
            c[1]++;
        }
        kinds.sort((a, b) -> a[1] != b[1] ? Integer.compare(b[1], a[1]) : Integer.compare(a[0], b[0]));
        int[] out = new int[Math.min(COLLAGE, kinds.size())];
        for (int i = 0; i < out.length; i++) out[i] = kinds.get(i)[0];
        return out;
    }

    // ---- the frame

    /** The facade frame's middle line as four lines {x0, y0, x1, y1}: foot, right, top, left. */
    public static double[][] frame() {
        return new double[][] { { FRAME_X0, FRAME_Y0, FRAME_X1, FRAME_Y0 }, { FRAME_X1, FRAME_Y0, FRAME_X1, FRAME_Y1 },
            { FRAME_X1, FRAME_Y1, FRAME_X0, FRAME_Y1 }, { FRAME_X0, FRAME_Y1, FRAME_X0, FRAME_Y0 } };
    }

    /** How bright the frame's light is at the world time {@code t} while docked, 0..1: a slow breath. */
    public static double breath(double t) {
        return 0.5 + 0.5 * Math.sin(2 * Math.PI * t / BREATH_T);
    }

    /**
     * Where the glint running round the frame is at the world time {@code t} while powered: the share of the way
     * round, 0..1, from the frame's left foot corner along its foot.
     */
    public static double glint(double t) {
        return ArcaneLook.frac(t / GLINT_T);
    }

    // ---- the forming sweep

    /**
     * The facade's wash while the vault forms, for the sweep's height {@code y} in the vault's frame: {yLow, yHigh},
     * the stretch of the frame's height it covers (from {@link #SWEEP_TRAIL} under the line up to it, within the
     * frame), or null while the line is below or above the facade.
     */
    public static double[] sweep(double y) {
        double lo = DualFoilVaultShape.FACADE_Y0, hi = DualFoilVaultShape.FACADE_Y1 + 1;
        if (y <= lo || y > hi + SWEEP_TRAIL) return null;
        return new double[] { Math.max(lo, y - SWEEP_TRAIL), Math.min(hi, y) };
    }

    /** The quads of the facade's sweep: the wash and the line across the frame in its pieces. */
    public static int sweepQuads() {
        return 1 + DomeLook.pieces(DualFoilVaultShape.FACADE_X1 + 1 - DualFoilVaultShape.FACADE_X0);
    }

    // ---- the shafts

    /**
     * The skylight shafts: for each skylight strip (rows {@code z = 3 mod 6}) the pieces of its shaft, each a stretch
     * of the strip's columns whose light falls equally far: {x0, x1, z, bottom, pool} (x0..x1 across, z the strip's
     * row, the light entering at {@link #SHAFT_TOP} over z..z + 1 and leaning {@link #SLANT} to the front for each
     * block it falls down to {@code bottom}; {@code pool} 1 when it lands on a floor or a deck, where it leaves a pool
     * of light, else 0).
     */
    public static List<double[]> shafts() {
        return SHAFTS;
    }

    /** The z the front of a shaft from row {@code z} has reached at the height {@code y} (its back is one further). */
    public static double shaftFront(double z, double y) {
        return z - (SHAFT_TOP - y) * SLANT;
    }

    private static List<double[]> buildShafts() {
        List<double[]> out = new ArrayList<>();
        int x0 = 4, x1 = 22;
        for (int z = 1; z < DualFoilVaultShape.DEPTH - 1; z++) {
            if (!DualFoilVaultShape.skylight(x0, z)) continue;
            double[] run = null;
            for (int x = x0; x <= x1; x++) {
                double[] fall = fall(x, z);
                if (run != null && run[3] == fall[0] && run[4] == fall[1]) {
                    run[1] = x + 1;
                    continue;
                }
                if (run != null) out.add(run);
                run = new double[] { x, x + 1, z, fall[0], fall[1] };
            }
            out.add(run);
        }
        // the strip's two ends stay in a little from its walls and decks
        for (double[] s : out) {
            if (s[0] == x0) s[0] += SHAFT_IN;
            if (s[1] == x1 + 1) s[1] -= SHAFT_IN;
        }
        return out;
    }

    /**
     * How far the light of column {@code x} of the strip at row {@code z} falls: {bottom, pool}. The light is a slab
     * one block deep leaning {@link #SLANT} to the front; it stops at the highest point where it meets a cell that is
     * not open (nothing or must-be-air): on the top of a cell it falls onto, or where its front edge runs into the side
     * of one. It pools when it stops on the top of cells under the whole of it.
     */
    private static double[] fall(int x, int z) {
        double bottom = 0;
        for (int layer = (int) SHAFT_TOP - 1; layer >= 0; layer--) for (int c = 0; c < DualFoilVaultShape.DEPTH; c++) {
            if (isOpen(DualFoilVaultShape.cell(x, layer, c))) continue;
            // the heights at which the slab's z-stretch overlaps the cell's: under the first, over the second
            double enter = SHAFT_TOP - (z - c - 1) / SLANT, leave = SHAFT_TOP - (z + 1 - c) / SLANT;
            double hi = Math.min(enter, layer + 1), lo = Math.max(leave, layer);
            if (hi > lo) bottom = Math.max(bottom, hi);
        }
        boolean pool = bottom == Math.floor(bottom) && bottom > 0;
        double front = shaftFront(z, bottom);
        int c0 = (int) Math.floor(front), c1 = (int) Math.floor(front + 1 - 1e-9);
        for (int c = c0; c <= c1 && pool; c++) pool = !isOpen(DualFoilVaultShape.cell(x, (int) bottom - 1, c));
        return new double[] { bottom, pool ? 1 : 0 };
    }

    private static boolean isOpen(char ch) {
        return ch == ShapeKit.NONE || ch == ShapeKit.AIR;
    }

    /**
     * How bright the shafts are at a world's time of day ({@code World.getWorldTime()}: 0 sunrise, 6000 noon, 12000
     * sunset, 18000 midnight), {@link #NIGHT} to 1: full while the sun stands high, the moon's glow at night.
     */
    public static double daylight(long worldTime) {
        double day = Math.floorMod(worldTime, 24000L);
        double sun = Math.sin(2 * Math.PI * day / 24000);
        double light = Math.max(0, Math.min(1, (sun + 0.1) / 0.6));
        return NIGHT + (1 - NIGHT) * light;
    }

    /** The quads of the shafts: each piece's two broad faces and its pool, and the two ends of every strip. */
    public static int shaftQuads() {
        int n = 0;
        java.util.Set<Double> strips = new java.util.HashSet<>();
        for (double[] s : SHAFTS) {
            n += 2 + (int) s[4];
            strips.add(s[2]);
        }
        return n + 2 * strips.size();
    }

    // ---- the foil's rim

    /**
     * The point {@code s} blocks round the foil's rim from its left foot corner (along its foot to the right, up its
     * right side, back along its top and down its left side): {x, y} on the back wall, at {@link #BACK}.
     */
    public static double[] rimPoint(double s) {
        double p = s - PERIMETER * Math.floor(s / PERIMETER);
        if (p <= FOIL_W) return new double[] { FOIL_X + p, FOIL_Y };
        p -= FOIL_W;
        if (p <= FOIL_H) return new double[] { FOIL_X + FOIL_W, FOIL_Y + p };
        p -= FOIL_H;
        if (p <= FOIL_W) return new double[] { FOIL_X + FOIL_W - p, FOIL_Y + FOIL_H };
        p -= FOIL_W;
        return new double[] { FOIL_X, FOIL_Y + FOIL_H - p };
    }

    /**
     * How bright the event horizon is at {@code s} round the rim at the world time {@code t}, 0..1: a glow all round, a
     * bright arc running round it once every {@link #RIM_T} ticks (as the near side of a lensed ring), and a faint
     * shimmer.
     */
    public static double horizon(double s, double t) {
        double a = 2 * Math.PI * (s / PERIMETER - t / RIM_T);
        double arc = Math.pow(Math.max(0, Math.cos(a)), 4);
        double shimmer = 0.06 * Math.sin(2 * Math.PI * (s / 3.0 + t / 32.0));
        return Math.max(0, Math.min(1, 0.32 + 0.6 * arc + shimmer));
    }

    // ---- the budget

    /** The quads of the collage at a tier: near and mid each tile a painting, far each tile only its ground. */
    public static int collageQuads(int tier) {
        return COLLAGE * (tier == ModuleLook.FAR ? CanvasLook.GROUND_QUADS : CanvasLook.paintingQuads());
    }

    /** The quads of the facade frame's light while docked: each line in its pieces, near with a halo as well. */
    public static int frameQuads(int tier) {
        if (tier == ModuleLook.FAR) return 0;
        int n = 0;
        for (double[] l : frame()) n += DomeLook.pieces(Math.hypot(l[2] - l[0], l[3] - l[1]));
        return tier == ModuleLook.NEAR ? 2 * n : n;
    }

    /**
     * The quads of the foil's rim: docked a faint line (near a piece a block, mid each side in its pieces); powered the
     * event horizon, near also a halo a piece and the dark of the foil (four bands in from its sides), mid a halo a
     * piece as well.
     */
    public static int rimQuads(int tier, boolean docked, boolean on) {
        if (!docked || tier == ModuleLook.FAR) return 0;
        int line = tier == ModuleLook.NEAR ? RIM_PIECES : 2 * DomeLook.pieces(FOIL_W) + 2 * DomeLook.pieces(FOIL_H);
        if (!on) return line;
        return 2 * line + (tier == ModuleLook.NEAR ? 4 : 0);
    }

    /**
     * The most quads the vault's own visuals take a frame at a tier ({@link ModuleLook#tier}), counted as
     * {@code VaultRender} draws them: while it forms the facade's sweep; formed the collage, near the shafts; docked
     * the frame's light and the foil's rim, powered its event horizon. The canvases' paintings
     * ({@link CanvasLook#quads}) and the shared drawing ({@link ModuleLook#baseQuads}) come on top.
     */
    public static int quads(int tier, boolean docked, boolean on, boolean forming) {
        int n = collageQuads(tier);
        if (forming) n += sweepQuads();
        if (tier == ModuleLook.NEAR) n += shaftQuads();
        if (docked) n += frameQuads(tier) + rimQuads(tier, true, on);
        return n;
    }
}
