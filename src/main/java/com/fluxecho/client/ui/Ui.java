package com.fluxecho.client.ui;

import com.fluxecho.logic.ui.FluxMono;
import com.fluxecho.logic.ui.Fmt;
import com.fluxecho.logic.ui.TextModel;
import com.fluxecho.logic.ui.TextModel.Run;
import com.fluxecho.logic.ui.TextModel.Style;
import com.fluxecho.logic.ui.TextModel.Surface;

/**
 * The component library of the new UI (ui/FINAL.md section 1.6, 2.4), immediate mode: every component is a static
 * function that draws on a {@link FluxCanvas}. This first part (U0) holds the two text components everything else
 * builds on; the components themselves come with the component library (U1).
 * <p>
 * {@link #text} only takes the three styles (1x, bold 1x, 2x), so no page can draw Chinese at 0.75 again. On paper it
 * replaces section colour codes by the paper inks and draws bold as a second pass half a pixel to the right; on the ink
 * card, the HUD and in the world it hands the string to the font as it is.
 */
public final class Ui {

    private Ui() {}

    /** Text in a style on a surface, {@code color} where no code says otherwise; returns the advance. */
    public static float text(FluxCanvas c, String s, float x, float y, Style style, Surface surface, int color) {
        if (s == null || s.isEmpty()) return 0;
        if (surface != Surface.PAPER) {
            float w = c.text(s, x, y, color, style.scale);
            if (style.bold) c.text(s, x + 0.5f, y, color, style.scale);
            return w;
        }
        float pos = 0;
        for (Run r : TextModel.runs(s, surface, color)) {
            String t = r.formats + r.text;
            float w = c.text(t, x + pos, y, r.color, style.scale);
            if (style.bold || r.bold) c.text(t, x + pos + 0.5f, y, r.color, style.scale);
            pos += w;
        }
        return pos;
    }

    /**
     * A number with its unit (1.4.3 rule 2): the number in FluxMono at {@code size}, the unit in FluxMono 6, 2 px
     * apart, on one baseline; {@code y} is the top of the number. Returns the width.
     */
    public static float value(FluxCanvas c, String number, String unit, float x, float y, int size, int color,
        int unitColor) {
        float w = c.mono(number, x, y, size, color, FluxCanvas.Align.LEFT);
        if (unit == null || unit.isEmpty()) return w;
        float uy = y + FluxMono.baseline(size) - FluxMono.baseline(FluxMono.CAPTION);
        return w + Fmt.UNIT_GAP
            + c.mono(unit, x + w + Fmt.UNIT_GAP, uy, FluxMono.CAPTION, unitColor, FluxCanvas.Align.LEFT);
    }

    /** An eyebrow: the English line under a header, FluxMono 6, upper case, tracked +1. Returns the width. */
    public static float eyebrow(FluxCanvas c, String s, float x, float y, int color) {
        String upper = FluxMono.eyebrow(s);
        return c.mono(upper, x, y, FluxMono.CAPTION, color, FluxCanvas.Align.LEFT, FluxMono.EYEBROW_TRACKING);
    }
}
