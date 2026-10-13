package com.fluxecho.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * How a unit made of canvas paints its item (0.14.0, CITY.md §1.4 and §4.2 Visuals), worked out in a module's own
 * frame ({@link ShapeKit}: x across, y up, z from the front); the client draws what this says.
 * <ul>
 * <li>A unit's <em>field</em> ({@link #fields}) is the rectangle its canvas cells ({@link #CANVAS}) cover on the face
 * the unit looks out of ({@link UnitLayout.Unit#face}), a hair in front of the blocks: the Dual-foil Vault's 42
 * canvases are 3 across by 4 high on the long walls, its foil 13 by 11 on the back wall (a gate niche's soft canvas,
 * {@code $}, is the same block but no unit's cell, so the foil's field is its frame's inside all the same).</li>
 * <li>A unit holding an item paints it large and flat on its field ({@link #picture}): over a ground washed in the
 * item's own average colour, the item's icon {@link #paint painted}: posterised to a few flat tones of value and
 * saturation, its hues stepped, brushed with short diagonal dabs and outlined in a darker tone, at twice the icon's
 * resolution. An empty unit shows a faint grid along its canvas cells ({@link #grid}).</li>
 * <li>As the Echo Archive's books and the sockets' samples, the nearest {@code 256} paintings within {@code 24} blocks
 * of the camera are drawn whole; one further off shows only its ground, a patch of the item's colour (the far rule,
 * {@link #quads}).</li>
 * </ul>
 * Pure data and arithmetic over pixels, so the tests hold the fields to their canvas cells and their open faces, the
 * pictures inside their fields and the painting's tones to its levels.
 */
public final class CanvasLook {

    /** The canvas's character (deck CANVAS, checked); its soft twin in a gate niche is {@code $}. */
    public static final char CANVAS = 'Q';
    /** How far in front of the canvas's face a painting lies, in blocks: off the block's face, so they never fight. */
    public static final double OFF = 0.02;
    /** The share of a field's shorter side its picture fills, and the most it may measure (blocks). */
    public static final double PICTURE = 0.86, PICTURE_MAX = 4.5;
    /** How far a field that is not whole lets its ground reach round its picture (blocks). */
    public static final double GROUND_MARGIN = 0.25;
    /** The most render passes of an item a painting draws (a picture a pass), and the quads of its ground. */
    public static final int PASSES = 3, GROUND_QUADS = 1;
    /**
     * The largest side (pixels) an icon is painted from: a bigger one (a high-resolution pack's) is averaged down
     * first. A painting is {@link #SCALE} times its icon, so a dab of the brush or the outline is half an icon pixel.
     */
    public static final int ICON_MAX = 32, SCALE = 2;
    /** A painting's tones: the steps of value and of saturation, and how finely its hues are stepped round. */
    public static final int VALUE_LEVELS = 5, SATURATION_LEVELS = 4, HUE_STEPS = 24;
    /** How much a brush dab lifts or lowers the value it lies on before the tones are stepped. */
    public static final double DAB = 0.08;
    /** How dark a painting's outline is against the tone beside it. */
    public static final double OUTLINE = 0.3;
    /** How opaque an icon's pixel must be to be painted (the rest is the canvas). */
    public static final int SOLID_ALPHA = 128;
    /** The longest piece a drawn line is cut into ({@link DomeLook#PIECE}, the city renderer's). */
    public static final double PIECE = DomeLook.PIECE;

    private CanvasLook() {}

    // ---- the fields

    /**
     * One unit's canvas field: the rectangle its canvas cells cover on the face it looks out of, {@link #OFF} in front
     * of it. A point of it is {@link #at}{@code (u, v)}: {@code u} blocks to the right of its left edge for someone
     * looking at it, {@code v} up from its foot.
     */
    public static final class Field {

        /** The unit (its index in the layout), its first slot, and its kind. */
        public final int unit, slot;
        public final String kind;
        /** The direction {fx, fz} the field looks (the unit's face). */
        public final int[] face;
        /** Its left foot corner {x, y, z}, a hair in front of the face. */
        public final double[] origin;
        /** The way right across it for someone looking at it {rx, rz} (a unit vector along x or z). */
        public final int[] right;
        /** How wide and how high it is (blocks), and how many canvas cells of its unit it covers. */
        public final int width, height, cells;

        Field(int unit, int slot, String kind, int[] face, double[] origin, int[] right, int width, int height,
            int cells) {
            this.unit = unit;
            this.slot = slot;
            this.kind = kind;
            this.face = face;
            this.origin = origin;
            this.right = right;
            this.width = width;
            this.height = height;
            this.cells = cells;
        }

        /** The point {x, y, z} of the frame {@code u} to the right of the field's left edge and {@code v} up. */
        public double[] at(double u, double v) {
            return new double[] { origin[0] + right[0] * u, origin[1] + v, origin[2] + right[1] * u };
        }

        /** The middle of the field {x, y, z}. */
        public double[] middle() {
            return at(width / 2.0, height / 2.0);
        }

        /** Whether every cell of its rectangle is a canvas cell of its unit (no hole, such as a gate niche). */
        public boolean whole() {
            return cells == width * height;
        }
    }

    /**
     * Every canvas field of a module ({@link Field}): one for each unit with canvas cells and a face, in unit order.
     *
     * @throws IllegalStateException when a unit's canvas cells do not lie in one wall plane square to its face
     */
    public static List<Field> fields(CityBuild b, UnitLayout layout) {
        List<Field> out = new ArrayList<>();
        for (UnitLayout.Unit u : layout.units()) {
            if (u.face == null) continue;
            int fx = u.face[0], fz = u.face[1];
            int n = 0, plane = Integer.MIN_VALUE, a0 = Integer.MAX_VALUE, a1 = Integer.MIN_VALUE,
                y0 = Integer.MAX_VALUE, y1 = Integer.MIN_VALUE;
            for (int[] c : u.cells) {
                if (b.cell(c[0], c[1], c[2]) != CANVAS) continue;
                int p = fx != 0 ? c[0] : c[2], a = fx != 0 ? c[2] : c[0];
                if (plane != Integer.MIN_VALUE && p != plane)
                    throw new IllegalStateException(b.key() + " unit " + u.name + ": its canvas is not one wall");
                plane = p;
                a0 = Math.min(a0, a);
                a1 = Math.max(a1, a);
                y0 = Math.min(y0, c[1]);
                y1 = Math.max(y1, c[1]);
                n++;
            }
            if (n == 0) continue;
            if (fx != 0 && fz != 0 || fx == 0 && fz == 0)
                throw new IllegalStateException(b.key() + " unit " + u.name + ": a canvas faces along x or z");
            // the face's plane: the side of the wall's cells it looks out of, a hair in front of it
            int out1 = fx != 0 ? fx : fz;
            double front = plane + (out1 > 0 ? 1 : 0) + out1 * OFF;
            // right for someone looking at it (looking along -face): (-fz, fx) in the module's frame
            int[] right = { -fz, fx };
            int r = fx != 0 ? right[1] : right[0];
            double left = r > 0 ? a0 : a1 + 1;
            double[] origin = fx != 0 ? new double[] { front, y0, left } : new double[] { left, y0, front };
            out.add(
                new Field(u.index, u.firstSlot, u.kind, u.face.clone(), origin, right, a1 - a0 + 1, y1 - y0 + 1, n));
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * A field that is no unit's (0.14.0: a tile of the Dual-foil Vault's facade collage): {@code width} by
     * {@code height} blocks from its left foot corner {@code origin} (already in front of its wall), looking along
     * {@code face}; whole, with neither unit nor slot.
     */
    public static Field rect(double[] origin, int[] face, int width, int height) {
        int[] right = { -face[1], face[0] };
        return new Field(-1, -1, "", face.clone(), origin.clone(), right, width, height, width * height);
    }

    /**
     * Where a field's picture lies: {u0, v0, u1, v1} in the field (blocks), a square of {@link #PICTURE} of its shorter
     * side (at most {@link #PICTURE_MAX}) in its middle.
     */
    public static double[] picture(Field f) {
        double side = Math.min(PICTURE_MAX, Math.min(f.width, f.height) * PICTURE);
        double u0 = (f.width - side) / 2, v0 = (f.height - side) / 2;
        return new double[] { u0, v0, u0 + side, v0 + side };
    }

    /**
     * Where a painted field's ground lies: {u0, v0, u1, v1}, the whole field when it is {@link Field#whole}, else its
     * picture and {@link #GROUND_MARGIN} round it (the foil keeps its dark canvas round its painting).
     */
    public static double[] ground(Field f) {
        if (f.whole()) return new double[] { 0, 0, f.width, f.height };
        double[] p = picture(f);
        return new double[] { Math.max(0, p[0] - GROUND_MARGIN), Math.max(0, p[1] - GROUND_MARGIN),
            Math.min(f.width, p[2] + GROUND_MARGIN), Math.min(f.height, p[3] + GROUND_MARGIN) };
    }

    /**
     * An empty field's faint grid: the lines along its canvas cells' edges, {u0, v0, u1, v1} each in the field: every
     * column's edge from foot to top, then every row's edge from left to right.
     */
    public static List<double[]> grid(Field f) {
        List<double[]> out = new ArrayList<>();
        for (int u = 0; u <= f.width; u++) out.add(new double[] { u, 0, u, f.height });
        for (int v = 0; v <= f.height; v++) out.add(new double[] { 0, v, f.width, v });
        return out;
    }

    // ---- the budget

    /** The quads a line {@code length} blocks long is drawn in (cut into pieces of {@link #PIECE}). */
    public static int pieces(double length) {
        return DomeLook.pieces(length);
    }

    /** The quads of a painting: its ground and a picture for each of at most {@link #PASSES} passes. */
    public static int paintingQuads() {
        return GROUND_QUADS + PASSES;
    }

    /** The quads of an empty field's grid, each line in its pieces. */
    public static int gridQuads(Field f) {
        int n = 0;
        for (double[] l : grid(f)) n += pieces(Math.hypot(l[2] - l[0], l[3] - l[1]));
        return n;
    }

    /**
     * The most quads a module's canvases take a frame at a tier ({@link ModuleLook#tier}): near, each field its
     * painting or its grid, whichever is more (a field further than the near distance only its ground, which is less);
     * mid and far nothing (canvases hang inside).
     */
    public static int quads(List<Field> fields, int tier) {
        if (tier != ModuleLook.NEAR) return 0;
        int n = 0;
        for (Field f : fields) n += Math.max(paintingQuads(), gridQuads(f));
        return n;
    }

    // ---- the painting

    /** An image: its width and height (pixels) and its pixels, ARGB, row by row from the top. */
    public static final class Image {

        public final int width, height;
        public final int[] argb;

        public Image(int width, int height, int[] argb) {
            if (width <= 0 || height <= 0 || argb.length != width * height)
                throw new IllegalArgumentException("an image of " + width + " x " + height + " with " + argb.length);
            this.width = width;
            this.height = height;
            this.argb = argb;
        }

        public int get(int x, int y) {
            return argb[y * width + x];
        }
    }

    /**
     * The picture an icon's texture holds, ready to paint: its first frame when the texture is a strip of square
     * frames (an animated icon), averaged down to at most {@link #ICON_MAX} a side.
     */
    public static Image icon(Image texture) {
        int w = texture.width, h = texture.height;
        Image frame = texture;
        if (h > w && h % w == 0) {
            int[] top = new int[w * w];
            System.arraycopy(texture.argb, 0, top, 0, top.length);
            frame = new Image(w, w, top);
        }
        int k = (Math.max(frame.width, frame.height) + ICON_MAX - 1) / ICON_MAX;
        return k <= 1 ? frame : shrink(frame, k);
    }

    /** An image averaged down by {@code k} each way (its alpha the mean, its colour the alpha-weighted mean). */
    static Image shrink(Image src, int k) {
        int w = Math.max(1, src.width / k), h = Math.max(1, src.height / k);
        int[] out = new int[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            long a = 0, r = 0, g = 0, bl = 0;
            int n = 0;
            for (int j = 0; j < k; j++) for (int i = 0; i < k; i++) {
                int sx = x * k + i, sy = y * k + j;
                if (sx >= src.width || sy >= src.height) continue;
                int p = src.get(sx, sy), pa = p >>> 24;
                a += pa;
                r += (long) (p >> 16 & 0xFF) * pa;
                g += (long) (p >> 8 & 0xFF) * pa;
                bl += (long) (p & 0xFF) * pa;
                n++;
            }
            if (n == 0 || a == 0) continue;
            out[y * w + x] = (int) (a / n) << 24 | (int) (r / a) << 16 | (int) (g / a) << 8 | (int) (bl / a);
        }
        return new Image(w, h, out);
    }

    /** The average colour (RGB) of an icon's solid pixels, or -1 when it has none. */
    public static int average(Image icon) {
        long r = 0, g = 0, b = 0;
        int n = 0;
        for (int p : icon.argb) {
            if (p >>> 24 < SOLID_ALPHA) continue;
            r += p >> 16 & 0xFF;
            g += p >> 8 & 0xFF;
            b += p & 0xFF;
            n++;
        }
        if (n == 0) return -1;
        return (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
    }

    /**
     * An icon painted: {@link #SCALE} times its size, every solid pixel's colour brushed ({@link #dab}) and stepped to
     * the painting's tones ({@link #tone}), and every clear pixel beside a solid one (one painting pixel round the
     * icon's shape) an outline {@link #OUTLINE} as bright as the tone beside it. The rest stays clear, so the canvas's
     * ground shows round the item. Solid pixels are opaque, clear ones nothing: no soft edges.
     */
    public static Image paint(Image icon) {
        int w = icon.width * SCALE, h = icon.height * SCALE;
        int[] tone = new int[w * h];
        boolean[] solid = new boolean[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int p = icon.get(x / SCALE, y / SCALE);
            if (p >>> 24 < SOLID_ALPHA) continue;
            solid[y * w + x] = true;
            tone[y * w + x] = tone(p & 0xFFFFFF, dab(x, y));
        }
        int[] out = new int[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = y * w + x;
            if (solid[i]) {
                out[i] = 0xFF000000 | tone[i];
                continue;
            }
            int beside = -1;
            if (x > 0 && solid[i - 1]) beside = tone[i - 1];
            else if (x + 1 < w && solid[i + 1]) beside = tone[i + 1];
            else if (y > 0 && solid[i - w]) beside = tone[i - w];
            else if (y + 1 < h && solid[i + w]) beside = tone[i + w];
            if (beside >= 0) out[i] = 0xFF000000 | scale(beside, OUTLINE);
        }
        return new Image(w, h, out);
    }

    /**
     * How a brush dab lifts or lowers the paint at a painting pixel, -1 to 1: short diagonal dabs, three pixels across
     * and five along, each its own (the same for every painting).
     */
    public static double dab(int x, int y) {
        return 2 * Mix.unit(0xB7A5L, Math.floorDiv(x + y, 3), Math.floorDiv(x - y, 5)) - 1;
    }

    /**
     * A colour (RGB) stepped to a painting's tones after a dab ({@code dab}, -1..1, lifts or lowers its value by up to
     * {@link #DAB}): its hue to one of {@link #HUE_STEPS} round the wheel, its saturation to one of
     * {@link #SATURATION_LEVELS} and its value to one of {@link #VALUE_LEVELS}, evenly from none to full.
     */
    public static int tone(int rgb, double dab) {
        double r = (rgb >> 16 & 0xFF) / 255.0, g = (rgb >> 8 & 0xFF) / 255.0, b = (rgb & 0xFF) / 255.0;
        double max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), c = max - min;
        double hue = 0;
        if (c > 0) {
            if (max == r) hue = ((g - b) / c) / 6;
            else if (max == g) hue = ((b - r) / c + 2) / 6;
            else hue = ((r - g) / c + 4) / 6;
            hue -= Math.floor(hue);
        }
        double sat = max == 0 ? 0 : c / max, val = Math.max(0, Math.min(1, max * (1 + DAB * dab)));
        hue = Math.round(hue * HUE_STEPS) % HUE_STEPS / (double) HUE_STEPS;
        sat = step(sat, SATURATION_LEVELS);
        val = step(val, VALUE_LEVELS);
        return hsv(hue, sat, val);
    }

    /** A share 0..1 stepped to the nearest of {@code levels} even steps from 0 to 1. */
    static double step(double v, int levels) {
        return Math.round(Math.max(0, Math.min(1, v)) * (levels - 1)) / (double) (levels - 1);
    }

    /** The colour (RGB) of a hue (0..1 round the wheel), saturation and value. */
    static int hsv(double hue, double sat, double val) {
        double h6 = hue * 6;
        int sector = (int) Math.floor(h6) % 6;
        double f = h6 - Math.floor(h6), p = val * (1 - sat), q = val * (1 - sat * f), t = val * (1 - sat * (1 - f));
        double r, g, b;
        switch (sector) {
            case 0 -> {
                r = val;
                g = t;
                b = p;
            }
            case 1 -> {
                r = q;
                g = val;
                b = p;
            }
            case 2 -> {
                r = p;
                g = val;
                b = t;
            }
            case 3 -> {
                r = p;
                g = q;
                b = val;
            }
            case 4 -> {
                r = t;
                g = p;
                b = val;
            }
            default -> {
                r = val;
                g = p;
                b = q;
            }
        }
        return channel(r) << 16 | channel(g) << 8 | channel(b);
    }

    private static int channel(double v) {
        return (int) Math.round(Math.max(0, Math.min(1, v)) * 255);
    }

    /** A colour (RGB) with its channels scaled by {@code k}. */
    static int scale(int rgb, double k) {
        return channel((rgb >> 16 & 0xFF) / 255.0 * k) << 16 | channel((rgb >> 8 & 0xFF) / 255.0 * k) << 8
            | channel((rgb & 0xFF) / 255.0 * k);
    }
}
