package com.fluxecho.logic.skin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The city's colours of light (0.17.0, style §3.6 and G2, G6): every building's accent ("本色"), the stranded
 * capsule's and the leap ring's, the three colours light may have (the nexus cyan, the accents, warm white), and the
 * two layers every glowing line is drawn in (a white core and a coloured edge). It is the one place these values live:
 * the world's light seams and lamp windows, the interface's building ink ({@code lum}, ui §1.3.4), the swatch wall and
 * the previews all read it, so a colour the swatch wall settles is changed here and nowhere else.
 * <p>
 * It also measures how far apart two colours look, as CIEDE2000 (ΔE00, sRGB to CIELAB under D65), because the style
 * rules are written in it: an accent stands at least 10 from the nexus cyan, and two accents that can be seen in one
 * frame at least 12 ({@code PaletteTest}).
 */
public final class SkinPalette {

    /** The nexus cyan: only the keels, the nexus, the vein channels and the ring channel are lit in it (G2, G6). */
    public static final int NEXUS_CYAN = 0x4FE3FF;
    /** The white core of a keel's cyan line (G2). */
    public static final int KEEL_CORE = 0xE8FCFF;
    /** Warm white, the light at a person's scale: stair noses, rails, thresholds, lanterns, street lamps (G6). */
    public static final int WARM_WHITE = 0xFFE6B8;
    /** The evening-star gold of the memorial state and the interface's gold (OPEN-QUESTIONS D8). */
    public static final int EVENING_GOLD = 0xFFC56B;

    /**
     * The core of every glowing line (style §3.6, G6): white, drawn untinted, so that under a shader pack that warms
     * block light the line still reads as light whatever the hue does.
     */
    public static final int GLOW_CORE = 0xFFFFFF;
    /** The least lightness (CIE L* / 100) a pixel of a glow's core may have (G6: "明度 ≥ 0.9"). */
    public static final double GLOW_CORE_MIN_LIGHTNESS = 0.9;
    /**
     * How wide a glow's core is, and each edge beside it, in pixels of the 32-pixel material textures (style §3.5,
     * §3.6: 1 to 2 each). The 16-pixel textures of the 0.16 parts that the seven-row recolouring redraws (0.17.0, style
     * §7.1) keep a two-pixel core and one-pixel edges.
     */
    public static final int GLOW_CORE_PX = 2, GLOW_EDGE_PX = 2;
    /**
     * The edge's grey ramp, from the pixel next to the core to the outermost: the edge layer is drawn in these greys
     * and tinted by the vertex colour, the building's accent, so one light-seam shape lights in any of the accents.
     */
    public static final int GLOW_EDGE_INNER = 0xFFFFFF, GLOW_EDGE_OUTER = 0x999999;

    /** No accent: the key names no building (the ascension's placeholder row has none either). */
    public static final int NONE = -1;
    /** The root's three forms, by the 0.16.0 shape families' names ({@code RootShape}): well, deep root, ley core. */
    public static final String WELL = "well", DEEP = "root", CORE = "core";
    /** The leap ring's colour once the ship has leapt (style §3.6: the aurora violet turns white). */
    public static final int RING_ASCENDED = 0xFFFFFF;

    /** One row of the palette: a building's key, its form (null for a building of one form) and its accent. */
    public static final class Row {

        public final String key, form;
        public final int rgb;

        Row(String key, String form, int rgb) {
            this.key = key;
            this.form = form;
            this.rgb = rgb;
        }

        @Override
        public String toString() {
            return (form == null ? key : key + "/" + form) + String.format(" #%06X", rgb);
        }
    }

    private static final List<Row> ROWS;

    static {
        // in the order of the Buildings rows (ids.md §2: 00 the capsule ... 20 the ring), the root's three forms on
        // its one row; the values are style §3.6 as revised on 2026-10-15 (tide teal, ice blue) with B12's dawn red
        List<Row> l = new ArrayList<>();
        l.add(new Row("drift_capsule", null, NEXUS_CYAN));
        l.add(new Row("nexus", null, NEXUS_CYAN));
        l.add(new Row("library", null, 0xFFD9A0));
        l.add(new Row("eco_dome", null, 0x7BE08A));
        l.add(new Row("root", WELL, 0x2E9FB8));
        l.add(new Row("root", DEEP, 0x5FD3A8));
        l.add(new Row("root", CORE, 0xFF7A3C));
        l.add(new Row("dome_generator", null, 0x7DB8FF));
        l.add(new Row("dual_foil_vault", null, 0xF2EBDD));
        l.add(new Row("mana_fountain", null, 0x6AA8FF));
        l.add(new Row("arcane_court", null, 0xB07CFF));
        l.add(new Row("resonance_gate", null, 0x5CF2C2));
        l.add(new Row("sophon_array", null, 0xFF8CE6));
        l.add(new Row("holo_table", null, 0xA8E0FF));
        l.add(new Row("blood_covenant", null, 0xE0344A));
        l.add(new Row("hunting_ground", null, 0xFF9A3C));
        l.add(new Row("observatory", null, 0xB9C8FF));
        l.add(new Row("sky_sails", null, 0xFFD27A));
        l.add(new Row("weather_station", null, 0xFF9E8A));
        l.add(new Row("droplet_hangar", null, 0xD8E4F0));
        l.add(new Row("orbital_anchor", null, 0xFFFFFF));
        l.add(new Row("source_heart", null, 0xF6E7FF));
        l.add(new Row("leap_ring", null, 0xC9B8FF));
        ROWS = Collections.unmodifiableList(l);
    }

    private SkinPalette() {}

    /** Every row: 21 buildings and the capsule, the root in its three forms, 23 rows in all. */
    public static List<Row> rows() {
        return ROWS;
    }

    /**
     * A building's accent by its module key ({@code eco_dome}, {@code root}, {@code drift_capsule}...), the root's
     * first form for the root; {@link #NONE} for a key that names no building.
     */
    public static int accent(String key) {
        for (Row r : ROWS) if (r.key.equals(key)) return r.rgb;
        return NONE;
    }

    /**
     * A building's accent in the shape it stands as: the root's form follows its shape name ({@link #rootForm}),
     * every other building has one accent whatever its shape; {@link #NONE} for a key that names no building.
     */
    public static int accent(String key, String shape) {
        if (!"root".equals(key)) return accent(key);
        String form = rootForm(shape);
        for (Row r : ROWS) if (r.key.equals(key) && form.equals(r.form)) return r.rgb;
        return NONE;
    }

    /** {@link #accent(String, String)}, or {@code fallback} where the key names no building. */
    public static int accentOr(String key, String shape, int fallback) {
        int c = accent(key, shape);
        return c == NONE ? fallback : c;
    }

    /**
     * The root's form a shape name stands for: {@code core...} the ley core, {@code root...} the deep root (its
     * chamber and in-well forms), anything else the tidal well, the form a root starts as.
     */
    public static String rootForm(String shape) {
        if (shape != null && shape.startsWith(CORE)) return CORE;
        if (shape != null && shape.startsWith(DEEP)) return DEEP;
        return WELL;
    }

    // ---- glow

    /**
     * A colour as a vertex colour tints a texel: channel by channel, {@code texel * tint / 255}. The edge layer's grey
     * times the accent is the edge as it is seen.
     */
    public static int tint(int texel, int tint) {
        int r = (texel >> 16 & 255) * (tint >> 16 & 255) / 255, g = (texel >> 8 & 255) * (tint >> 8 & 255) / 255,
            b = (texel & 255) * (tint & 255) / 255;
        return r << 16 | g << 8 | b;
    }

    /**
     * The grey of the edge's {@code step}-th pixel out from the core (0 to {@link #GLOW_EDGE_PX} - 1), on the ramp
     * from {@link #GLOW_EDGE_INNER} to {@link #GLOW_EDGE_OUTER}.
     */
    public static int glowEdgeGrey(int step) {
        int n = Math.max(1, GLOW_EDGE_PX - 1), s = Math.max(0, Math.min(n, step));
        int in = GLOW_EDGE_INNER & 255, out = GLOW_EDGE_OUTER & 255, v = in + (out - in) * s / n;
        return v << 16 | v << 8 | v;
    }

    /** The edge's {@code step}-th pixel as it is seen tinted with {@code accent}. */
    public static int glowEdge(int accent, int step) {
        return tint(glowEdgeGrey(step), accent);
    }

    // ---- colour difference (CIEDE2000)

    /** D65's white point (the sRGB matrix's own, so white is exactly L* 100). */
    private static final double XN = 0.95047, YN = 1.0, ZN = 1.08883;

    /** An sRGB colour ({@code 0xRRGGBB}) in CIELAB under D65: {L*, a*, b*}. */
    public static double[] lab(int rgb) {
        double r = linear((rgb >> 16 & 255) / 255.0), g = linear((rgb >> 8 & 255) / 255.0),
            b = linear((rgb & 255) / 255.0);
        double x = 0.4124564 * r + 0.3575761 * g + 0.1804375 * b;
        double y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b;
        double z = 0.0193339 * r + 0.1191920 * g + 0.9503041 * b;
        double fx = f(x / XN), fy = f(y / YN), fz = f(z / ZN);
        return new double[] { 116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz) };
    }

    /** A colour's lightness, CIE L* / 100: 0 for black, 1 for white. */
    public static double lightness(int rgb) {
        return lab(rgb)[0] / 100;
    }

    private static double linear(double c) {
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static double f(double t) {
        return t > 216.0 / 24389 ? Math.cbrt(t) : (24389.0 / 27 * t + 16) / 116;
    }

    /** How far apart two sRGB colours look, CIEDE2000. */
    public static double deltaE00(int a, int b) {
        return deltaE00(lab(a), lab(b));
    }

    /**
     * How far apart two CIELAB colours look, CIEDE2000 (Sharma, Wu and Dalal, 2005), with the parametric factors
     * kL = kC = kH = 1.
     */
    public static double deltaE00(double[] lab1, double[] lab2) {
        double l1 = lab1[0], a1 = lab1[1], b1 = lab1[2], l2 = lab2[0], a2 = lab2[1], b2 = lab2[2];
        double c1 = Math.hypot(a1, b1), c2 = Math.hypot(a2, b2), cBar = (c1 + c2) / 2;
        double c7 = Math.pow(cBar, 7), g = 0.5 * (1 - Math.sqrt(c7 / (c7 + Math.pow(25, 7))));
        double a1p = (1 + g) * a1, a2p = (1 + g) * a2;
        double c1p = Math.hypot(a1p, b1), c2p = Math.hypot(a2p, b2);
        double h1p = c1p == 0 ? 0 : hue(b1, a1p), h2p = c2p == 0 ? 0 : hue(b2, a2p);
        double dLp = l2 - l1, dCp = c2p - c1p, dhp;
        if (c1p * c2p == 0) dhp = 0;
        else {
            dhp = h2p - h1p;
            if (dhp > 180) dhp -= 360;
            else if (dhp < -180) dhp += 360;
        }
        double dHp = 2 * Math.sqrt(c1p * c2p) * Math.sin(Math.toRadians(dhp / 2));
        double lBarP = (l1 + l2) / 2, cBarP = (c1p + c2p) / 2, hBarP;
        if (c1p * c2p == 0) hBarP = h1p + h2p;
        else if (Math.abs(h1p - h2p) <= 180) hBarP = (h1p + h2p) / 2;
        else if (h1p + h2p < 360) hBarP = (h1p + h2p + 360) / 2;
        else hBarP = (h1p + h2p - 360) / 2;
        double t = 1 - 0.17 * Math.cos(Math.toRadians(hBarP - 30))
            + 0.24 * Math.cos(Math.toRadians(2 * hBarP))
            + 0.32 * Math.cos(Math.toRadians(3 * hBarP + 6))
            - 0.20 * Math.cos(Math.toRadians(4 * hBarP - 63));
        double dTheta = 30 * Math.exp(-Math.pow((hBarP - 275) / 25, 2));
        double cp7 = Math.pow(cBarP, 7), rc = 2 * Math.sqrt(cp7 / (cp7 + Math.pow(25, 7)));
        double l50 = (lBarP - 50) * (lBarP - 50);
        double sl = 1 + 0.015 * l50 / Math.sqrt(20 + l50), sc = 1 + 0.045 * cBarP, sh = 1 + 0.015 * cBarP * t;
        double rt = -Math.sin(Math.toRadians(2 * dTheta)) * rc;
        double tl = dLp / sl, tc = dCp / sc, th = dHp / sh;
        return Math.sqrt(tl * tl + tc * tc + th * th + rt * tc * th);
    }

    /** The hue angle of (a, b), 0 to 360 degrees. */
    private static double hue(double b, double a) {
        double h = Math.toDegrees(Math.atan2(b, a));
        return h < 0 ? h + 360 : h;
    }
}
