package com.fluxecho.client.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fluxecho.logic.ui.FluxMono;
import com.fluxecho.logic.ui.TextModel;
import com.fluxecho.logic.ui.TextModel.Style;
import com.fluxecho.logic.ui.TextModel.Surface;
import com.fluxecho.logic.ui.Tok;

/**
 * {@code Ui.text} and {@code Ui.value} (ui/FINAL.md section 1.4.3, 1.4.4, 2.4): on paper the section colour codes are
 * replaced by inks and bold is a second pass half a pixel right; on the ink card the string goes to the font as it
 * is; a number and its unit sit 2 px apart on one baseline.
 */
class UiTextTest {

    /** What the font was asked to draw: text, x, y, colour, scale. */
    static final class Call {

        final String text;
        final float x, y, scale;
        final int color;

        Call(Object[] a) {
            text = (String) a[0];
            x = (float) a[1];
            y = (float) a[2];
            color = (int) a[3];
            scale = a[4] instanceof Float f ? f : (float) (int) a[4];
        }
    }

    /** A canvas that records text and FluxMono calls and measures with the text model. */
    static FluxCanvas recorder(List<Call> text, List<Object[]> mono) {
        return (FluxCanvas) Proxy
            .newProxyInstance(FluxCanvas.class.getClassLoader(), new Class<?>[] { FluxCanvas.class }, (proxy, m, a) -> {
                switch (m.getName()) {
                    case "text" -> {
                        text.add(new Call(a));
                        return TextModel.width((String) a[0]) * (float) a[4];
                    }
                    case "mono" -> {
                        mono.add(a);
                        return (float) FluxMono.width((String) a[0], (int) a[3], a.length > 6 ? (int) a[6] : 0);
                    }
                    case "factor" -> {
                        return 2;
                    }
                    default -> {
                        Class<?> r = m.getReturnType();
                        return r == float.class ? 0f : r == int.class ? 0 : null;
                    }
                }
            });
    }

    @Test
    void paperRemapsCodesAndDrawsBoldTwice() {
        List<Call> calls = new ArrayList<>();
        float w = Ui.text(recorder(calls, new ArrayList<>()), "a§eb§lc", 10, 20, Style.B, Surface.PAPER, Tok.INK_1);
        assertEquals(4, calls.size());
        assertEquals("a", calls.get(0).text);
        assertEquals(Tok.INK_1, calls.get(0).color);
        assertEquals(10, calls.get(0).x, 0);
        assertEquals("b", calls.get(1).text);
        assertEquals(Tok.Hue.WARN.ink, calls.get(1).color, "§e on paper is warn-ink");
        assertEquals(16, calls.get(1).x, 0);
        assertEquals("c", calls.get(2).text);
        assertEquals(22, calls.get(2).x, 0);
        assertEquals(22.5f, calls.get(3).x, 0, "bold: the same run again, half a pixel right");
        assertEquals(18, w, 0);
        for (Call c : calls) assertEquals(20, c.y, 0);
    }

    @Test
    void stylesAreOneTwoAndBold() {
        List<Call> calls = new ArrayList<>();
        FluxCanvas c = recorder(calls, new ArrayList<>());
        Ui.text(c, "营造", 0, 0, Style.H2, Surface.PAPER, Tok.INK_1);
        assertEquals(2, calls.size(), "H2 is bold: two passes");
        calls.clear();
        float w = Ui.text(c, "营造", 0, 0, Style.H1, Surface.PAPER, Tok.INK_0);
        assertEquals(1, calls.size());
        assertEquals(2f, calls.get(0).scale, 0);
        assertEquals(36, w, 0);
    }

    @Test
    void theInkCardKeepsTheCodes() {
        List<Call> calls = new ArrayList<>();
        Ui.text(recorder(calls, new ArrayList<>()), "a§eb§lc", 0, 0, Style.B, Surface.INK, Tok.LUM_TEXT);
        assertEquals(1, calls.size());
        assertEquals("a§eb§lc", calls.get(0).text);
        assertEquals(Tok.LUM_TEXT, calls.get(0).color);
    }

    @Test
    void valueSetsTheUnitOnTheBaseline() {
        List<Object[]> mono = new ArrayList<>();
        float w = Ui.value(recorder(new ArrayList<>(), mono), "384", "EU/t", 100, 50, 12, Tok.INK_1, Tok.INK_3);
        assertEquals(2, mono.size());
        assertEquals("384", mono.get(0)[0]);
        assertEquals(100f, (float) mono.get(0)[1], 0);
        assertEquals("EU/t", mono.get(1)[0]);
        assertEquals(100f + 3 * 7 + 2, (float) mono.get(1)[1], 0, "2 px after the number");
        float unitTop = (float) mono.get(1)[2];
        assertEquals(50 + FluxMono.baseline(12), unitTop + FluxMono.baseline(6), 1e-4, "one baseline");
        assertEquals(3 * 7 + 2 + 4 * 4, w, 0);
        mono.clear();
        Ui.eyebrow(recorder(new ArrayList<>(), mono), "Nexus · phase III", 0, 0, Tok.INK_3);
        assertEquals("NEXUS · PHASE III", mono.get(0)[0]);
        assertEquals(6, mono.get(0)[3]);
        assertEquals(1, mono.get(0)[6], "tracked +1");
    }
}
