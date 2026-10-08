package com.fluxecho.mana;

import static com.fluxecho.client.FluxDraw.*;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

import org.lwjgl.opengl.GL11;

import com.fluxecho.core.CoreCircuits;
import com.fluxecho.logic.Compact;

/** The drawn parts of the Mana Echo Spring's GUI (see {@link ManaGui}): what textures cannot show. Client only. */
final class ManaScreen {

    /** The basin's rim and floor, and where the channel from the flux layer meets it. */
    private static final double RIM = 9, FLOOR = 30, HALF_RIM = 29, HALF_FLOOR = 11, LAYER = 12;

    private ManaScreen() {}

    private static float ticks() {
        return Minecraft.getSystemTime() / 50f;
    }

    private static boolean working(MTEManaSpring m) {
        return m.mMaxProgresstime > 0 && m.state() == MTEManaSpring.State.WORKING;
    }

    /** The strip at the top: the core's tier and voltage on the left, what the spring does on the right. */
    static void header(MTEManaSpring m, float x, float y, float w, float h) {
        int tier = m.tier(), color = CoreCircuits.color(tier);
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        begin();
        hgradient(1, 1, w * 0.55, h - 1, color, 0.28f, color, 0f);
        rect(1, h - 1, w - 1, h, color, 0.6f);
        end();
        text(ManaText.coreLabel(tier), 4, 3, color, 1f);
        MTEManaSpring.State st = m.state();
        right(ManaText.status(st), (int) w - 4, 3, ManaText.statusColor(st), 1f);
        GL11.glPopMatrix();
    }

    /**
     * The spring itself: the flux layer at the bottom, the echo rising out of it through the channel as the cycle
     * goes, and the basin above holding the mana not yet handed on, its surface rippling.
     */
    static void basin(MTEManaSpring m, float x, float y, float w, float h) {
        boolean working = working(m);
        float progress = working ? Math.max(0, Math.min(1, (float) m.mProgresstime / m.mMaxProgresstime)) : 0;
        float t = ticks();
        double cx = w / 2.0, layer = h - LAYER;
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        begin();
        gradient(0, 0, w, h, DEEP, 0.95f, 0x0A1430, 0.95f);
        frame(0, 0, w, h, SEAM, 1f);

        // the flux layer, motes drifting in it
        gradient(1, layer, w - 1, h - 1, VIOLET, 0.08f, VIOLET, 0.42f);
        rect(1, layer, w - 1, layer + 1, VIOLET, 0.5f);
        for (int i = 0; i < 6; i++) {
            double dir = i % 2 == 0 ? 1 : -1;
            double mx = 2 + ((i * 13 + dir * t * 0.35) % (w - 6) + (w - 6)) % (w - 6);
            double my = layer + 4 + Math.sin(t * 0.08 + i) * 2.5;
            rect(mx, my, mx + 2, my + 1, i % 3 == 0 ? MANA_PINK : VIOLET, 0.7f);
        }

        // the channel, the echo filling it from the bottom as the cycle goes
        gradient(cx - 4, FLOOR, cx + 4, layer, MANA, 0.12f, VIOLET, 0.32f);
        if (progress > 0) {
            double top = layer - (layer - FLOOR) * progress;
            gradient(cx - 3, top, cx + 3, layer, 0xA8ECFF, 0.9f, MANA, 0.9f);
            rect(cx - 3, top, cx + 3, top + 1, WHITE, 0.9f);
        }
        double speed = working ? 0.8 : 0.1;
        for (int i = 0; i < 4; i++) {
            double at = (t * speed + i * 5.5) % (layer - FLOOR);
            float fade = (float) (at / (layer - FLOOR));
            double ry = layer - at, rw = 4 - fade * 2;
            rect(cx - rw, ry, cx + rw, ry + 1, MANA_PINK, 0.55f * (1 - fade) * (working ? 1 : 0.4f));
        }

        // the basin: rim, inside, and the mana in it
        trapezoid(cx, RIM - 1, HALF_RIM + 1, FLOOR + 1, HALF_FLOOR + 1, SEAM, 1f, SEAM, 1f);
        trapezoid(cx, RIM, HALF_RIM, FLOOR, HALF_FLOOR, 0x07101A, 1f, 0x07101A, 1f);
        int cap = Math.max(1, m.guiBufferCap);
        float fill = Math.min(1f, (float) m.buffer() / cap);
        boolean full = m.state() == MTEManaSpring.State.POOL_FULL;
        if (fill > 0) {
            double level = FLOOR - (FLOOR - RIM) * fill;
            double half = halfAt(level);
            trapezoid(cx, level, half, FLOOR, HALF_FLOOR, 0x7FDDFF, 0.9f, 0x1E6FA8, 0.95f);
            for (double wx = cx - half; wx < cx + half - 1; wx += 2) {
                double dy = Math.sin(wx * 0.4 + t * 0.22) * (working ? 0.9 : 0.4);
                rect(wx, level + dy, wx + 2, level + dy + 1, WHITE, 0.55f);
            }
        }
        if (working) for (int i = 0; i < 5; i++) {
            double phase = (t * 0.05 + i * 0.37) % 1.0;
            double sx = cx - HALF_RIM + 6 + (i * 11.3) % (2 * HALF_RIM - 12);
            double sy = RIM + 4 - phase * 10;
            rect(sx, sy, sx + 1, sy + 1, i % 2 == 0 ? MANA_PINK : WHITE, (float) (0.9 * (1 - phase)));
        }
        rect(cx - HALF_RIM - 1, RIM - 1, cx + HALF_RIM + 1, RIM, full ? AMBER : working ? MANA : SEAM, 1f);
        corners(0, 0, w, h, 4, working ? MANA : SEAM, 1f);
        end();
        smallCentered(ManaText.bufferBar(m.buffer(), cap), cx, 2, 0.5f, full ? AMBER : DIM, 1f);
        GL11.glPopMatrix();
    }

    private static double halfAt(double level) {
        double f = (level - RIM) / (FLOOR - RIM);
        return HALF_RIM + (HALF_FLOOR - HALF_RIM) * f;
    }

    /** The nearest pools as columns, how many are in reach, and the mana in all of them. */
    static void pools(MTEManaSpring m, float x, float y, float w, float h) {
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        float t = ticks();
        begin();
        for (int i = 0; i < MTEManaSpring.SHOWN_POOLS; i++) {
            double x0 = 3 + i * 9;
            int f = m.poolFill[i];
            if (f < 0) {
                rect(x0, 12, x0 + 7, 47, DEEP, 1f);
                frame(x0, 12, x0 + 7, 47, SEAM, 0.5f);
            } else {
                column(x0, 12, x0 + 7, 47, f / 1000f, f >= 1000 ? 0x7FDDFF : MANA, 1f);
            }
        }
        if (working(m)) scan(1, 1, w - 1, h - 1, t, MANA, 0.08f);
        end();
        smallCentered(ManaText.t("gui.pools_title", m.poolCount), w / 2.0, 3, 0.6f, m.poolCount > 0 ? CYAN : AMBER, 1f);
        smallCentered(Compact.si(m.poolMana), w / 2.0, 51, 0.6f, m.poolCount > 0 ? WHITE : DIM, 1f);
        GL11.glPopMatrix();
    }

    /** How far the spring reaches: sideways, and up and down. */
    static void reach(MTEManaSpring m, float x, float y, float w, float h) {
        int color = m.tier() > 0 ? CYAN : DIM;
        smallCentered("↔ " + m.guiRange, x + w / 2.0, y + 1, 0.65f, color, 1f);
        smallCentered("↕ ±" + m.guiHeight, x + w / 2.0, y + 9, 0.65f, color, 1f);
    }

    /** The rate; a petal's worth and what is still tuned; the pools and the charge item; and the EU buffer. */
    static void readout(MTEManaSpring m, float x, float y, float w, float h) {
        int tier = m.tier();
        FontRenderer f = font();
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        int lw = (int) w - 8;
        if (tier > 0) text(f.trimStringToWidth(ManaText.rate(m.guiMana, m.guiEUt), lw), 4, 3, WHITE, 1f);
        else text(f.trimStringToWidth(ManaText.t("gui.insert_core"), lw), 4, 3, AMBER, 1f);
        text(
            f.trimStringToWidth(
                ManaText.petalLine(m.guiPetal, m.credit()) + " · " + ManaText.t("gui.per_minute", ManaText.perMinute()),
                lw),
            4,
            13,
            DIM,
            1f);
        text(
            f.trimStringToWidth(
                ManaText.poolLine(m.poolCount, m.poolMana, m.poolCap) + " · "
                    + ManaText.chargeOrSent(m.chargeFill(), m.delivered()),
                lw),
            4,
            23,
            CYAN,
            1f);

        begin();
        float fill = m.guiEUCap <= 0 ? 0 : (float) Math.min(1, (double) m.guiEU / m.guiEUCap);
        bar(1, 37, w - 1, 46, fill, CoreCircuits.color(tier), 1f);
        scan(1, 37, w - 1, 46, ticks(), WHITE, 0.08f);
        end();
        smallCentered(ManaText.energyBar(m.guiEU, m.guiEUCap), w / 2.0, 39, 0.6f, WHITE, 1f);
        GL11.glPopMatrix();
    }
}
