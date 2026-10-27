package com.fluxecho.logic.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * The colour arithmetic the token tests rest on: WCAG 2.x contrast, compositing and CIEDE2000. The CIEDE2000 pairs are
 * from Sharma, Wu and Dalal (2005), table 1, the reference data for the formula.
 */
class ColorMathTest {

    @Test
    void contrastIsTheWcagRatio() {
        assertEquals(21.0, ColorMath.contrast(0xFFFFFFFF, 0xFF000000), 1e-9);
        assertEquals(1.0, ColorMath.contrast(0xFF777777, 0xFF777777), 1e-9);
        assertEquals(ColorMath.contrast(0xFF123456, 0xFFABCDEF), ColorMath.contrast(0xFFABCDEF, 0xFF123456), 1e-12);
        // alpha is ignored: a token is compared as the opaque colour it names
        assertEquals(ColorMath.contrast(0xFF1C2836, 0xFFF7FAFC), ColorMath.contrast(0x001C2836, 0x80F7FAFC), 1e-12);
        assertEquals(4.54, ColorMath.contrast(0xFF767676, 0xFFFFFFFF), 0.005);
    }

    @Test
    void overCompositesStraightAlpha() {
        assertEquals(0xFFFFFFFF, ColorMath.over(0x00000000, 0xFFFFFFFF));
        assertEquals(0xFF000000, ColorMath.over(0xFF000000, 0xFFFFFFFF));
        assertEquals(0xFF7F7F7F, ColorMath.over(0x80000000, 0xFFFFFFFF), "128/255 black over white");
        assertEquals(0xFF1B222D, ColorMath.over(0xF00D1520, 0xFFF7FAFC), "the ink card (alpha byte 240) over paper-1");
        assertEquals(0xFF1B232D, ColorMath.over(0xFF0D1520, 0.94f, 0xFFF7FAFC), "the ink card at exactly 0.94");
    }

    @Test
    void ciede2000MatchesSharmasPairs() {
        double[][] pairs = { { 50, 2.6772, -79.7751, 50, 0, -82.7485, 2.0425 },
            { 50, 3.1571, -77.2803, 50, 0, -82.7485, 2.8615 }, { 50, 2.8361, -74.0200, 50, 0, -82.7485, 3.4412 },
            { 50, 0, 0, 50, -1, 2, 2.3669 }, { 50, 2.5, 0, 73, 25, -18, 27.1492 }, { 50, 2.5, 0, 61, -5, 29, 22.8977 },
            { 60.2574, -34.0099, 36.2677, 60.4626, -34.1751, 39.4387, 1.2644 },
            { 2.0776, 0.0795, -1.1350, 0.9033, -0.0636, -0.5514, 0.9082 } };
        for (double[] p : pairs) {
            double[] a = { p[0], p[1], p[2] }, b = { p[3], p[4], p[5] };
            assertEquals(p[6], ColorMath.deltaE00(a, b), 0.00005);
            assertEquals(p[6], ColorMath.deltaE00(b, a), 0.00005, "symmetric");
        }
    }

    @Test
    void labOfTheCorners() {
        double[] white = ColorMath.lab(0xFFFFFFFF), black = ColorMath.lab(0xFF000000), red = ColorMath.lab(0xFFFF0000);
        assertEquals(100, white[0], 1e-3);
        assertEquals(0, white[1], 1e-3);
        assertEquals(0, white[2], 1e-3);
        assertEquals(0, black[0], 1e-9);
        assertEquals(53.24, red[0], 0.01);
        assertEquals(80.09, red[1], 0.01);
        assertEquals(67.20, red[2], 0.01);
    }

    @Test
    void mixingAndAlpha() {
        assertEquals(0xFF808080, ColorMath.mix(0xFF000000, 0xFFFFFFFF, 0.5f), "channels round");
        assertEquals(0x00FFFFFF, ColorMath.mix(0x00000000, 0x00FFFFFF, 1f));
        assertEquals(0x80FFFFFF, ColorMath.withAlpha(0xFFFFFF, 0.5f));
        assertEquals(0x80123456, ColorMath.alpha(0xFF123456, 0.5f));
        assertEquals(0x40123456, ColorMath.alpha(0x80123456, 0.5f));
        assertEquals(0xFF123456, ColorMath.opaque(0x00123456));
    }
}
