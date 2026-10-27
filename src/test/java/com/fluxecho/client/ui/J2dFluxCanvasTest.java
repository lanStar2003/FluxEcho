package com.fluxecho.client.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fluxecho.logic.ui.AtlasMap;
import com.fluxecho.logic.ui.Tok;
import com.fluxecho.logic.ui.UiGeo;

/**
 * The preview canvas draws the shapes the way the game canvas builds them (the outlines come from {@link UiGeo}): the
 * cut corner is cut, glyphs and sprites come out of the atlas, blend modes mix, and the shape helpers give the
 * outlines the design asks for.
 */
class J2dFluxCanvasTest {

    static int alpha(J2dFluxCanvas c, float gx, float gy) {
        return c.image.getRGB((int) (gx * c.factor()), (int) (gy * c.factor())) >>> 24;
    }

    @Test
    void theTopRightCornerIsCut() {
        J2dFluxCanvas c = new J2dFluxCanvas(60, 40, 4, null);
        c.chamfer(0, 0, 60, 40, Tok.CUT_WINDOW, 0xFF000000);
        assertEquals(0, alpha(c, 59.5f, 0.5f), "cut away");
        assertEquals(255, alpha(c, 52, 1), "left of the cut");
        assertEquals(255, alpha(c, 59, 8), "below the cut");
        assertTrue(alpha(c, 0.1f, 0.1f) < 128, "the 1 px bevel at the top left");
        assertEquals(255, alpha(c, 30, 20));
    }

    @Test
    void theStrokeIsHollow() {
        J2dFluxCanvas c = new J2dFluxCanvas(60, 40, 4, null);
        c.chamferStroke(0, 0, 60, 40, Tok.CUT_CARD, 1, 0xFF000000);
        assertEquals(255, alpha(c, 30, 0.5f));
        assertEquals(0, alpha(c, 30, 20));
    }

    @Test
    void monoAndSpritesComeFromTheAtlas() {
        J2dFluxCanvas c = new J2dFluxCanvas(80, 40, 4, null);
        float w = c.mono("888", 2, 2, 24, 0xFF000000, FluxCanvas.Align.LEFT);
        assertEquals(3 * 14, w, 0);
        int ink = 0;
        for (int y = 2; y < 26; y++) for (int x = 2; x < 44; x++) if (alpha(c, x + 0.5f, y + 0.5f) > 100) ink++;
        assertTrue(ink > 60, "the eights are drawn: " + ink);
        J2dFluxCanvas d = new J2dFluxCanvas(20, 20, 4, null);
        d.sprite(AtlasMap.I_CLOSE, 2, 2, 16, 16, 0xFF000000);
        assertTrue(alpha(d, 10, 10) > 100, "the cross of the close icon goes through the middle");
        assertEquals(0, alpha(d, 10, 3), "and not along the top edge");
    }

    @Test
    void addLightensAndMultiplyDarkens() {
        J2dFluxCanvas c = new J2dFluxCanvas(10, 10, 1, null);
        c.fill(0, 0, 10, 10, 0xFF404040);
        c.blend(FluxCanvas.Blend.ADD);
        c.fill(0, 0, 5, 10, 0xFF404040);
        c.blend(FluxCanvas.Blend.MULTIPLY);
        c.fill(5, 0, 5, 10, 0xFF808080);
        c.blend(FluxCanvas.Blend.NORMAL);
        assertEquals(0x80, c.image.getRGB(2, 5) & 0xFF, "0x40 + 0x40");
        assertEquals(0x20, c.image.getRGB(7, 5) & 0xFF, "0x40 x 0x80 / 0xFF");
    }

    @Test
    void outlines() {
        float[] p = UiGeo.chamfer(10, 20, 100, 50, 6, 1);
        assertEquals(16, p.length);
        assertEquals(104, p[2], 0, "the top edge stops 6 before the right");
        assertEquals(26, p[5], 0, "the right edge starts 6 down");
        float[] q = UiGeo.chamferInner(10, 20, 100, 50, 6, 1, 1);
        assertEquals(21, q[1], 0, "moved in by the stroke");
        assertEquals(6 - (2 - Math.sqrt(2)), 109 - q[2], 1e-5, "the inner cut keeps the 45-degree edge parallel");
        float[] top = UiGeo.polar(0, 0, 10, 0), right = UiGeo.polar(0, 0, 10, 90);
        assertEquals(-10, top[1], 1e-5, "0 degrees is twelve o'clock");
        assertEquals(10, right[0], 1e-5, "90 degrees is three o'clock");
        assertEquals(1, UiGeo.arcSteps(0, 6));
        assertEquals(60, UiGeo.arcSteps(0, 360));
        List<float[]> d = UiGeo.dashes(new float[] { 0, 20 }, new float[] { 0, 0 }, 4, 4, 0);
        assertEquals(3, d.size(), "0-4, 8-12, 16-20");
        assertEquals(8, d.get(1)[0], 1e-5);
        List<float[]> moved = UiGeo.dashes(new float[] { 0, 20 }, new float[] { 0, 0 }, 4, 4, 2);
        assertEquals(2, moved.get(0)[0], 1e-5, "the phase moves the dashes along the line");
        assertEquals(6, moved.get(0)[2], 1e-5);
        assertEquals(
            0,
            UiGeo.dashes(new float[] { 0, 20 }, new float[] { 0, 0 }, 4, 4, 6)
                .get(0)[0],
            1e-5,
            "a dash cut by the start of the line");
    }
}
