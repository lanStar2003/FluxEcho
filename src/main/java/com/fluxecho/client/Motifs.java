package com.fluxecho.client;

import static com.fluxecho.client.FluxDraw.*;

import com.fluxecho.core.Motif;

/**
 * Each echo machine's picture of its work, drawn flat in a box: the middle of its GUI, its NEI page and its hologram.
 * Plain quads in {@link FluxDraw}'s look; call between {@code begin()} and {@code end()}. {@code t} is in ticks,
 * {@code progress} 0..1 of the running cycle.
 */
public final class Motifs {

    private Motifs() {}

    public static void draw(Motif m, double w, double h, float t, float progress, boolean working, int accent,
        float a) {
        switch (m) {
            case SCAN -> scan(w, h, t, progress, working, accent, a);
            case COMB -> comb(w, h, t, progress, working, accent, a);
            case HELIX -> helix(w, h, t, progress, working, accent, a);
            case VORTEX -> vortex(w, h, t, progress, working, accent, a);
            case GLYPHS -> glyphs(w, h, t, progress, working, accent, a);
            case CAULDRON -> cauldron(w, h, t, progress, working, accent, a);
            case RUNES -> runes(w, h, t, progress, working, accent, a);
            case BLOOD -> blood(w, h, t, progress, working, accent, a);
            case PREY -> prey(w, h, t, progress, working, accent, a);
            case SPROUT -> sprout(w, h, t, progress, working, accent, a);
            case FOUNTAIN -> fountain(w, h, t, progress, working, accent, a);
        }
    }

    // ---- helpers

    public static void dot(double x, double y, double s, int rgb, float a) {
        rect(x - s / 2, y - s / 2, x + s / 2, y + s / 2, rgb, a);
    }

    /** Dots along a line. */
    public static void line(double x0, double y0, double x1, double y1, double s, int rgb, float a) {
        int n = (int) Math.max(1, Math.ceil(Math.hypot(x1 - x0, y1 - y0) / Math.max(0.5, s * 0.7)));
        for (int i = 0; i <= n; i++) dot(x0 + (x1 - x0) * i / n, y0 + (y1 - y0) * i / n, s, rgb, a);
    }

    /** Dots around a circle. */
    public static void ring(double cx, double cy, double r, int n, double s, int rgb, float a) {
        for (int i = 0; i < n; i++) {
            double ang = i * 2 * Math.PI / n;
            dot(cx + Math.cos(ang) * r, cy + Math.sin(ang) * r, s, rgb, a);
        }
    }

    /** A flat-topped hexagon of radius r. */
    public static void hex(double cx, double cy, double r, int rgb, float a) {
        double hh = r * 0.87;
        trapezoid(cx, cy - hh, r * 0.5, cy, r, rgb, a, rgb, a);
        trapezoid(cx, cy, r, cy + hh, r * 0.5, rgb, a, rgb, a);
    }

    public static int mix(int x, int y, double f) {
        f = Math.max(0, Math.min(1, f));
        int r = (int) ((x >> 16 & 0xFF) * (1 - f) + (y >> 16 & 0xFF) * f);
        int g = (int) ((x >> 8 & 0xFF) * (1 - f) + (y >> 8 & 0xFF) * f);
        int b = (int) ((x & 0xFF) * (1 - f) + (y & 0xFF) * f);
        return r << 16 | g << 8 | b;
    }

    /** A fixed pseudo-random 0..1 for an index. */
    public static double hash(int i) {
        double v = Math.sin(i * 12.9898 + 78.233) * 43758.5453;
        return v - Math.floor(v);
    }

    private static float wave(float t, double speed) {
        return (float) (0.5 + 0.5 * Math.sin(t * speed));
    }

    // ---- the motifs

    /** A sample card under a scanning beam; what the beam has passed is tinted. */
    static void scan(double w, double h, float t, float progress, boolean working, int c, float a) {
        double x0 = w * 0.18, x1 = w * 0.82, y0 = h * 0.14, y1 = h * 0.86;
        rect(x0, y0, x1, y1, DEEP, a);
        frame(x0, y0, x1, y1, SEAM, a);
        double cx = w / 2, cy = h / 2, r = Math.min(x1 - x0, y1 - y0) * 0.22;
        trapezoid(cx, cy - r, 0, cy, r, c, a * 0.35f, c, a * 0.6f);
        trapezoid(cx, cy, r, cy + r, 0, c, a * 0.6f, c, a * 0.35f);
        for (double y = y0 + 4; y < y1 - 2; y += 6) {
            rect(x0 + 1, y, x0 + 3, y + 1, c, a * 0.5f);
            rect(x1 - 3, y, x1 - 1, y + 1, c, a * 0.5f);
        }
        float f = working ? progress : 0.5f + 0.4f * (float) Math.sin(t * 0.05);
        double sy = y0 + 1 + (y1 - y0 - 2) * f;
        rect(x0 + 1, y0 + 1, x1 - 1, sy, c, a * 0.12f);
        gradient(x0 + 1, Math.max(y0 + 1, sy - 6), x1 - 1, sy, c, 0f, c, a * 0.45f);
        rect(x0 - 2, sy, x1 + 2, sy + 1, WHITE, a * (working ? 0.95f : 0.5f));
        corners(x0, y0, x1, y1, 3, working ? c : SEAM, a);
    }

    /** Honeycomb cells filling one after another, the newest pulsing. */
    static void comb(double w, double h, float t, float progress, boolean working, int c, float a) {
        double r = Math.min(w, h) * 0.14, d = r * 1.85, cx = w / 2, cy = h / 2;
        double[][] at = new double[7][];
        at[0] = new double[] { cx, cy };
        for (int i = 0; i < 6; i++) {
            double ang = Math.PI / 6 + i * Math.PI / 3;
            at[i + 1] = new double[] { cx + Math.cos(ang) * d, cy + Math.sin(ang) * d };
        }
        int filled = working ? (int) (progress * 7) : 0;
        for (int i = 0; i < 7; i++) {
            double x = at[i][0], y = at[i][1];
            hex(x, y, r + 1, SEAM, a);
            hex(x, y, r, DEEP, a);
            if (i < filled) hex(x, y, r - 1, c, a * 0.8f);
            else if (i == filled && working) hex(x, y, (r - 1) * wave(t, 0.4), c, a * 0.6f);
        }
        if (working) for (int i = 0; i < 3; i++) {
            double ph = t * 0.09 + i * 2.1;
            dot(cx + Math.sin(ph) * w * 0.38, cy + Math.sin(ph * 2) * h * 0.18, 2, i == 0 ? WHITE : c, a);
        }
    }

    /** A double helix turning, its rungs lighting up as the cycle goes. */
    static void helix(double w, double h, float t, float progress, boolean working, int c, float a) {
        int n = 14;
        double cx = w / 2, amp = Math.min(w * 0.32, 14), top = 5, step = (h - 10) / n;
        float speed = working ? 0.12f : 0.03f;
        for (int j = 0; j <= n; j++) {
            double y = top + j * step, ph = t * speed + j * 0.55;
            double xa = cx + Math.sin(ph) * amp, xb = cx - Math.sin(ph) * amp;
            float depth = (float) (0.55 + 0.45 * Math.cos(ph));
            if (j % 2 == 0) {
                boolean lit = working && j <= progress * n;
                rect(Math.min(xa, xb), y, Math.max(xa, xb), y + 1, lit ? c : SEAM, a * (lit ? 0.8f : 0.6f));
            }
            dot(xa, y, 2 + depth, c, a * depth);
            dot(xb, y, 3 - depth, WHITE, a * (1.1f - depth * 0.5f));
        }
    }

    /** Essence spiralling into a vortex; the core swells as the cycle goes. */
    static void vortex(double w, double h, float t, float progress, boolean working, int c, float a) {
        double cx = w / 2, cy = h / 2, m = Math.min(w, h);
        float speed = working ? 0.1f : 0.025f;
        for (int arm = 0; arm < 3; arm++) for (int k = 0; k < 12; k++) {
            double f = (k + (t * speed * 3) % 1) / 12.0;
            double rr = m * 0.46 * (1 - f) + 1, ang = arm * 2 * Math.PI / 3 + f * 3.4 + t * speed;
            dot(
                cx + Math.cos(ang) * rr,
                cy + Math.sin(ang) * rr,
                1 + f * 2,
                mix(c, WHITE, f * 0.6),
                a * (float) (0.25 + 0.75 * f));
        }
        double core = 2 + (working ? progress * m * 0.12 : 1) + wave(t, 0.3);
        dot(cx, cy, core + 3, c, a * 0.35f);
        dot(cx, cy, core, WHITE, a * 0.9f);
    }

    /** Glyphs rising off an open page. */
    static void glyphs(double w, double h, float t, float progress, boolean working, int c, float a) {
        double cx = w / 2, pageTop = h * 0.72, half = w * 0.32;
        trapezoid(cx, pageTop, half, h - 4, half + 2, 0xE8E0C8, a * 0.85f, 0xB8B098, a * 0.85f);
        rect(cx - 0.5, pageTop, cx + 0.5, h - 4, SEAM, a);
        for (double y = pageTop + 3; y < h - 6; y += 3) {
            rect(cx - half + 3, y, cx - 3, y + 1, 0x8A8270, a * 0.6f);
            rect(cx + 3, y, cx + half - 3, y + 1, 0x8A8270, a * 0.6f);
        }
        if (working) rect(cx - half, h - 4, cx - half + (2 * half) * progress, h - 3, c, a);
        float speed = working ? 0.02f : 0.006f;
        for (int i = 0; i < 9; i++) {
            double f = (t * speed + i / 9.0) % 1;
            double x = cx + (hash(i) - 0.5) * w * 0.6 + Math.sin(t * 0.05 + i) * 2;
            double y = pageTop - f * (pageTop - 3);
            double s = i % 3 == 0 ? 3 : 2;
            rect(x, y, x + s, y + s, i % 2 == 0 ? c : WHITE, a * (float) (1 - f));
            if (i % 3 == 0) rect(x + 1, y - 1, x + 2, y + s + 1, i % 2 == 0 ? c : WHITE, a * (float) (1 - f) * 0.6f);
        }
    }

    /** A crucible bubbling over a flame. */
    static void cauldron(double w, double h, float t, float progress, boolean working, int c, float a) {
        double cx = w / 2, top = h * 0.42, bottom = h * 0.8, half = w * 0.32;
        trapezoid(cx, top - 1, half + 1, bottom + 1, half * 0.7 + 1, SEAM, a, SEAM, a);
        trapezoid(cx, top, half, bottom, half * 0.7, 0x161A22, a, 0x0E1016, a);
        double level = top + 3;
        trapezoid(cx, level, half - 1.5, bottom - 1, half * 0.7 - 1, mix(c, WHITE, 0.3), a * 0.9f, c, a * 0.9f);
        for (double x = cx - half + 2; x < cx + half - 2; x += 2) rect(
            x,
            level + Math.sin(x * 0.6 + t * 0.2),
            x + 2,
            level + 1 + Math.sin(x * 0.6 + t * 0.2),
            WHITE,
            a * 0.5f);
        rect(cx - half + 2, bottom, cx - half + 4, bottom + 4, SEAM, a);
        rect(cx + half - 4, bottom, cx + half - 2, bottom + 4, SEAM, a);
        if (working) for (int i = 0; i < 5; i++) {
            double fl = 1.5 + 2 * wave(t + i * 7, 0.7);
            rect(cx - 6 + i * 3, bottom + 4 - fl, cx - 4 + i * 3, bottom + 5, i % 2 == 0 ? AMBER : 0xFF6A2A, a * 0.9f);
        }
        float speed = working ? 0.03f : 0.008f;
        for (int i = 0; i < 6; i++) {
            double f = (t * speed + i / 6.0) % 1;
            double x = cx + (hash(i + 3) - 0.5) * half * 1.4, y = level - f * (level - 3);
            double s = 1.5 + f * 2.5;
            dot(x, y, s, mix(c, WHITE, 0.4), a * (float) (f < 0.85 ? 0.7 : (1 - f) * 4.6));
        }
        if (working) rect(cx - half, top - 4, cx - half + 2 * half * progress, top - 3, c, a);
    }

    /** A runic matrix: stones orbiting a core, a ring filling as the cycle goes. */
    static void runes(double w, double h, float t, float progress, boolean working, int c, float a) {
        double cx = w / 2, cy = h / 2, m = Math.min(w, h);
        int n = 24, lit = working ? (int) (progress * n) : 0;
        for (int i = 0; i < n; i++) {
            double ang = -Math.PI / 2 + i * 2 * Math.PI / n;
            dot(cx + Math.cos(ang) * m * 0.45, cy + Math.sin(ang) * m * 0.45, 1.5, i < lit ? c : SEAM, a);
        }
        float speed = working ? 0.05f : 0.012f;
        for (int i = 0; i < 6; i++) {
            double ang = t * speed + i * Math.PI / 3, r = m * 0.3;
            double x = cx + Math.cos(ang) * r, y = cy + Math.sin(ang) * r * 0.8;
            rect(x - 2, y - 2, x + 2, y + 2, 0x3A3448, a);
            rect(x - 1, y - 1, x + 1, y + 1, i % 2 == 0 ? c : WHITE, a);
            if (working) line(x, y, cx, cy, 1, c, a * 0.12f);
        }
        double s = m * 0.12 * (0.8 + 0.2 * wave(t, 0.2));
        trapezoid(cx, cy - s, 0, cy, s, mix(c, WHITE, 0.4), a, c, a);
        trapezoid(cx, cy, s, cy + s, 0, c, a, mix(c, 0x000000, 0.4), a);
    }

    /** Blood dripping into a chalice, which fills as the cycle goes. */
    static void blood(double w, double h, float t, float progress, boolean working, int c, float a) {
        double cx = w / 2, top = h * 0.5, cupBottom = h * 0.72, half = w * 0.26;
        trapezoid(cx, top - 1, half + 1, cupBottom + 1, half * 0.35 + 1, 0x8A929C, a, 0x5A616D, a);
        trapezoid(cx, top, half, cupBottom, half * 0.35, 0x1A0A0E, a, 0x12060A, a);
        double fill = working ? progress : 0.15;
        double level = cupBottom - (cupBottom - top - 1) * fill;
        double f = (level - top) / (cupBottom - top);
        double halfAt = half + (half * 0.35 - half) * f;
        trapezoid(cx, level, halfAt - 1, cupBottom - 1, half * 0.35 - 1, mix(c, WHITE, 0.15), a, 0x7A0A12, a);
        rect(cx - 1.5, cupBottom, cx + 1.5, h * 0.84, 0x8A929C, a);
        rect(cx - half * 0.6, h * 0.84, cx + half * 0.6, h * 0.84 + 2, 0x6A717D, a);
        float speed = working ? 0.035f : 0.01f;
        for (int i = 0; i < 3; i++) {
            double d = (t * speed + i / 3.0) % 1;
            double x = cx + (i - 1) * 3, y = 3 + d * (level - 4);
            rect(x - 0.5, y - 2, x + 0.5, y, c, a * 0.8f);
            rect(x - 1, y, x + 1, y + 2, c, a);
        }
    }

    /** A turning crosshair; claw marks cut in as the cycle goes. */
    static void prey(double w, double h, float t, float progress, boolean working, int c, float a) {
        double cx = w / 2, cy = h / 2, r = Math.min(w, h) * 0.34;
        float spin = t * (working ? 0.03f : 0.008f);
        for (int i = 0; i < 20; i++) {
            double ang = spin + i * 2 * Math.PI / 20;
            if (i % 5 == 0) continue;
            dot(cx + Math.cos(ang) * r, cy + Math.sin(ang) * r, 1.5, c, a * 0.8f);
        }
        for (int i = 0; i < 4; i++) {
            double ang = spin + i * Math.PI / 2;
            line(
                cx + Math.cos(ang) * (r - 4),
                cy + Math.sin(ang) * (r - 4),
                cx + Math.cos(ang) * (r + 3),
                cy + Math.sin(ang) * (r + 3),
                1,
                WHITE,
                a);
        }
        dot(cx, cy, 2, working ? RED : c, a);
        for (int i = 0; i < 3; i++) {
            float f = working ? Math.max(0, Math.min(1, progress * 3 - i)) : 0;
            if (f <= 0) continue;
            double x0 = cx - r * 0.6 + i * r * 0.45, y0 = cy - r * 0.7;
            line(x0, y0, x0 + r * 0.3 * f, y0 + r * 1.4 * f, 1.5, i == 1 ? WHITE : RED, a);
        }
    }

    /** A sprout growing out of the soil with the cycle. */
    static void sprout(double w, double h, float t, float progress, boolean working, int c, float a) {
        double cx = w / 2, soil = h * 0.78;
        gradient(4, soil, w - 4, h - 3, 0x4A3420, a, 0x2A1C10, a);
        for (int i = 0; i < 10; i++) rect(
            5 + hash(i) * (w - 12),
            soil + 2 + hash(i + 9) * (h - soil - 7),
            6 + hash(i) * (w - 12),
            soil + 3 + hash(i + 9) * (h - soil - 7),
            0x6A5034,
            a);
        float grow = working ? progress : 0.08f;
        double stem = (soil - h * 0.18) * grow;
        rect(cx - 2, soil - 2, cx + 2, soil + 1, 0x8A6A40, a);
        if (stem > 1) {
            double sway = Math.sin(t * 0.06) * Math.min(2, stem * 0.06);
            line(cx, soil - 1, cx + sway, soil - stem, 1.5, 0x3E8A2E, a);
            for (int i = 0; i < 3; i++) {
                double at = 0.35 + i * 0.25;
                if (grow < at) break;
                double ly = soil - stem * at, side = i % 2 == 0 ? 1 : -1, size = 3 + 2 * Math.min(1, (grow - at) * 4);
                trapezoid(cx + sway * at + side * size / 2, ly - 1.5, size / 2, ly + 1.5, 0.5, c, a, 0x3E8A2E, a);
            }
            dot(cx + sway, soil - stem, 2.5, mix(c, WHITE, 0.3), a);
        }
        if (working) for (int i = 0; i < 4; i++) {
            double f = (t * 0.015 + i / 4.0) % 1;
            dot(cx + (hash(i) - 0.5) * w * 0.7, soil - f * soil * 0.8, 1.5, c, a * (float) (1 - f));
        }
    }

    /** Mana welling up and falling back, as the spring does. */
    static void fountain(double w, double h, float t, float progress, boolean working, int c, float a) {
        double cx = w / 2, base = h - 6;
        rect(cx - w * 0.3, base, cx + w * 0.3, base + 2, SEAM, a);
        float speed = working ? 0.03f : 0.01f;
        for (int i = 0; i < 16; i++) {
            double f = (t * speed + i / 16.0) % 1, dir = (hash(i) - 0.5) * 2;
            double x = cx + dir * w * 0.35 * f, y = base - (h - 10) * (4 * f * (1 - f));
            dot(x, y, 2, i % 4 == 0 ? MANA_PINK : c, a * (float) (1 - f * 0.6));
        }
    }
}
