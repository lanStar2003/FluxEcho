package com.fluxecho.logic.ui;

/**
 * Colour arithmetic for the UI tokens: WCAG 2.x contrast (the rule every text and graphic colour is held to), straight
 * alpha compositing, channel mixing, and CIEDE2000 colour difference (how far apart two inks look, used to keep the
 * constellation and building colours apart). Colours are ARGB ints; contrast and colour difference ignore alpha, so a
 * token is always judged as the opaque colour it names. Pure functions, no Minecraft classes.
 */
public final class ColorMath {

    private ColorMath() {}

    /** The colour with full alpha. */
    public static int opaque(int argb) {
        return 0xFF000000 | argb;
    }

    /** The RGB with the given alpha (0..1, rounded to the nearest byte). */
    public static int withAlpha(int rgb, float alpha) {
        int a = Math.round(Math.max(0, Math.min(1, alpha)) * 255);
        return a << 24 | (rgb & 0xFFFFFF);
    }

    /** The colour with its alpha multiplied by {@code factor} (0..1, rounded). */
    public static int alpha(int argb, float factor) {
        int a = Math.round((argb >>> 24) * Math.max(0, Math.min(1, factor)));
        return a << 24 | (argb & 0xFFFFFF);
    }

    /** Linear blend of every channel, alpha included, {@code t} = 0 gives {@code a}; channels round to nearest. */
    public static int mix(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int out = 0;
        for (int s = 0; s < 32; s += 8) {
            int ca = a >>> s & 255, cb = b >>> s & 255;
            out |= Math.round(ca + (cb - ca) * t) << s;
        }
        return out;
    }

    /** {@code fg} (its own alpha byte) composited over the opaque {@code below}. */
    public static int over(int fg, int below) {
        return over(fg, (fg >>> 24) / 255f, below);
    }

    /** {@code fg}'s RGB at {@code alpha} composited over the opaque {@code below}; channels round to nearest. */
    public static int over(int fg, float alpha, int below) {
        double a = Math.max(0, Math.min(1, alpha));
        int out = 0xFF000000;
        for (int s = 0; s < 24; s += 8) {
            int f = fg >>> s & 255, b = below >>> s & 255;
            out |= (int) Math.round(f * a + b * (1 - a)) << s;
        }
        return out;
    }

    /** sRGB channel (0..255) to linear light. */
    static double linear(int c8) {
        double c = c8 / 255.0;
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    /** WCAG relative luminance of the colour (alpha ignored). */
    public static double luminance(int rgb) {
        return 0.2126 * linear(rgb >> 16 & 255) + 0.7152 * linear(rgb >> 8 & 255) + 0.0722 * linear(rgb & 255);
    }

    /** WCAG contrast ratio, 1 to 21 (alpha ignored). */
    public static double contrast(int a, int b) {
        double la = luminance(a), lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    /** CIE L*a*b* (D65) of an sRGB colour. */
    public static double[] lab(int rgb) {
        double r = linear(rgb >> 16 & 255), g = linear(rgb >> 8 & 255), b = linear(rgb & 255);
        double x = 0.4124564 * r + 0.3575761 * g + 0.1804375 * b;
        double y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b;
        double z = 0.0193339 * r + 0.1191920 * g + 0.9503041 * b;
        double fx = f(x / 0.95047), fy = f(y), fz = f(z / 1.08883);
        return new double[] { 116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz) };
    }

    private static double f(double t) {
        return t > 216.0 / 24389 ? Math.cbrt(t) : (24389.0 / 27 * t + 16) / 116;
    }

    /** CIEDE2000 difference between two sRGB colours. */
    public static double deltaE00(int a, int b) {
        return deltaE00(lab(a), lab(b));
    }

    /** CIEDE2000 difference between two L*a*b* colours (Sharma, Wu and Dalal 2005). */
    public static double deltaE00(double[] lab1, double[] lab2) {
        double l1 = lab1[0], a1 = lab1[1], b1 = lab1[2], l2 = lab2[0], a2 = lab2[1], b2 = lab2[2];
        double c1 = Math.hypot(a1, b1), c2 = Math.hypot(a2, b2), cb = (c1 + c2) / 2;
        double cb7 = Math.pow(cb, 7), g = 0.5 * (1 - Math.sqrt(cb7 / (cb7 + Math.pow(25, 7))));
        double a1p = (1 + g) * a1, a2p = (1 + g) * a2;
        double c1p = Math.hypot(a1p, b1), c2p = Math.hypot(a2p, b2);
        double h1p = hue(a1p, b1), h2p = hue(a2p, b2);
        double dl = l2 - l1, dc = c2p - c1p, dh;
        if (c1p * c2p == 0) dh = 0;
        else {
            dh = h2p - h1p;
            if (dh > 180) dh -= 360;
            else if (dh < -180) dh += 360;
        }
        double dH = 2 * Math.sqrt(c1p * c2p) * Math.sin(Math.toRadians(dh / 2));
        double lbp = (l1 + l2) / 2, cbp = (c1p + c2p) / 2, hbp;
        if (c1p * c2p == 0) hbp = h1p + h2p;
        else if (Math.abs(h1p - h2p) <= 180) hbp = (h1p + h2p) / 2;
        else if (h1p + h2p < 360) hbp = (h1p + h2p + 360) / 2;
        else hbp = (h1p + h2p - 360) / 2;
        double t = 1 - 0.17 * Math.cos(Math.toRadians(hbp - 30))
            + 0.24 * Math.cos(Math.toRadians(2 * hbp))
            + 0.32 * Math.cos(Math.toRadians(3 * hbp + 6))
            - 0.20 * Math.cos(Math.toRadians(4 * hbp - 63));
        double dTheta = 30 * Math.exp(-Math.pow((hbp - 275) / 25, 2));
        double cbp7 = Math.pow(cbp, 7), rc = 2 * Math.sqrt(cbp7 / (cbp7 + Math.pow(25, 7)));
        double sl = 1 + 0.015 * (lbp - 50) * (lbp - 50) / Math.sqrt(20 + (lbp - 50) * (lbp - 50));
        double sc = 1 + 0.045 * cbp, sh = 1 + 0.015 * cbp * t;
        double rt = -Math.sin(Math.toRadians(2 * dTheta)) * rc;
        double tl = dl / sl, tc = dc / sc, th = dH / sh;
        return Math.sqrt(tl * tl + tc * tc + th * th + rt * tc * th);
    }

    private static double hue(double a, double b) {
        if (a == 0 && b == 0) return 0;
        double h = Math.toDegrees(Math.atan2(b, a));
        return h < 0 ? h + 360 : h;
    }
}
