package com.fluxecho.client;

import static com.fluxecho.client.FluxDraw.*;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.item.ItemStack;

import org.lwjgl.opengl.GL11;

import com.fluxecho.core.EchoText;
import com.fluxecho.core.MTEEchoMachine;
import com.fluxecho.core.MachineId;
import com.fluxecho.logic.Compact;

/** The drawn parts of an echo machine's GUI ({@code FluxMachineGui}): what textures cannot show. Client only. */
public final class MachineScreen {

    private MachineScreen() {}

    private static float ticks() {
        return Minecraft.getSystemTime() / 50f;
    }

    private static boolean working(MTEEchoMachine m) {
        return m.mMaxProgresstime > 0;
    }

    private static float progress(MTEEchoMachine m) {
        return m.mMaxProgresstime > 0 ? Math.max(0, Math.min(1, (float) m.mProgresstime / m.mMaxProgresstime)) : 0;
    }

    /** The strip at the top: tier and what kind of machine on the left, what it does on the right. */
    public static void header(MTEEchoMachine m, String status, float x, float y, float w, float h) {
        MachineId k = m.kind();
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        begin();
        hgradient(1, 1, w * 0.55, h - 1, k.accent, 0.28f, k.accent, 0f);
        rect(1, h - 1, w - 1, h, k.accent, 0.6f);
        end();
        String left = EchoText.tier(m.voltageTier()) + " · " + EchoText.t(k.key + ".type");
        String right = EchoText.status(status);
        FontRenderer f = font();
        right = f.trimStringToWidth(right, (int) w - 12 - f.getStringWidth(left));
        text(left, 4, 3, k.accent, 1f);
        right(right, (int) w - 4, 3, EchoText.statusColor(status), 1f);
        GL11.glPopMatrix();
    }

    /**
     * The machine's work in the middle: its own motif, and while it works motes running in from the inputs on the
     * left and out to the outputs on the right.
     */
    public static void panel(MTEEchoMachine m, float x, float y, float w, float h) {
        MachineId k = m.kind();
        boolean working = working(m);
        float t = ticks();
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        begin();
        gradient(0, 0, w, h, DEEP, 0.95f, 0x0A1430, 0.95f);
        frame(0, 0, w, h, SEAM, 1f);
        if (working) {
            double mid = h / 2;
            for (int i = 0; i < 3; i++) {
                double f = (t * 0.06 + i / 3.0) % 1;
                Motifs.dot(1 + f * w * 0.2, mid, 1.5, k.accent, (float) (1 - f));
                Motifs.dot(w - 1 - w * 0.2 + f * w * 0.2, mid, 1.5, k.accent, (float) f);
            }
        }
        GL11.glPushMatrix();
        GL11.glTranslatef(2, 2, 0);
        Motifs.draw(k.motif, w - 4, h - 4, t, progress(m), working, k.accent, 1f);
        GL11.glPopMatrix();
        if (working) scan(1, 1, w - 1, h - 1, t, k.accent, 0.07f);
        corners(0, 0, w, h, 4, working ? k.accent : SEAM, 1f);
        end();
        GL11.glPopMatrix();
    }

    /** Power and time; the sample; the machine's own line; and the EU buffer. */
    public static void readout(MTEEchoMachine m, ItemStack sample, String info, long eu, long euCap, int eut, float x,
        float y, float w, float h) {
        MachineId k = m.kind();
        FontRenderer f = font();
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        int lw = (int) w - 8;
        String power = working(m) && eut > 0
            ? EchoText
                .t("gui.power", Compact.si(eut), EchoText.seconds(m.mMaxProgresstime), Math.round(progress(m) * 100))
            : EchoText.t("gui.voltage", EchoText.tier(m.voltageTier()), m.voltage());
        text(f.trimStringToWidth(power, lw), 4, 3, WHITE, 1f);
        if (k.sample) {
            String s = sample == null ? EchoText.t("gui.no_sample") : EchoText.t("gui.sample", sample.getDisplayName());
            text(f.trimStringToWidth(s, lw), 4, 13, sample == null ? AMBER : DIM, 1f);
        }
        String line = EchoText.decode(info);
        if (!line.isEmpty()) text(f.trimStringToWidth(line, lw), 4, k.sample ? 23 : 13, CYAN, 1f);

        begin();
        float fill = euCap <= 0 ? 0 : (float) Math.min(1, (double) eu / euCap);
        bar(1, 37, w - 1, 46, fill, k.accent, 1f);
        scan(1, 37, w - 1, 46, ticks(), WHITE, 0.08f);
        end();
        smallCentered(EchoText.t("gui.energy", Compact.si(eu), Compact.si(euCap)), w / 2.0, 39, 0.6f, WHITE, 1f);
        GL11.glPopMatrix();
    }
}
