package com.fluxdepths.client;

import static com.fluxdepths.client.FluxDraw.*;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

import org.lwjgl.opengl.GL11;

import com.fluxdepths.shard.MTEFluxCollector;
import com.fluxdepths.shard.ShardState;
import com.fluxdepths.shard.ShardText;
import com.fluxdepths.shard.ShardTier;

/** The drawn parts of the Flux Shard Collector's GUI (see {@code CollectorGui}): what textures cannot show. */
public final class CollectorScreen {

    private CollectorScreen() {}

    private static float ticks() {
        return Minecraft.getSystemTime() / 50f;
    }

    /** The strip at the top: the core's tier and voltage on the left, what the machine does on the right. */
    public static void header(MTEFluxCollector m, float x, float y, float w, float h) {
        ShardTier tier = m.tier();
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        begin();
        hgradient(1, 1, w * 0.55, h - 1, tier.color(), 0.28f, tier.color(), 0f);
        rect(1, h - 1, w - 1, h, tier.color(), 0.6f);
        end();
        text(ShardText.coreLabel(tier), 4, 3, tier.color(), 1f);
        ShardState.Status st = m.state().status;
        right(ShardText.status(st), (int) w - 4, 3, ShardText.statusColor(st), 1f);
        GL11.glPopMatrix();
    }

    /**
     * The depth shaft: rings sinking into the flux layer, faster while it works, and the ore condensing from the
     * bottom up as the cycle goes.
     */
    public static void shaft(MTEFluxCollector m, float x, float y, float w, float h) {
        ShardTier tier = m.tier();
        boolean working = m.mMaxProgresstime > 0;
        float progress = working ? Math.max(0, Math.min(1, (float) m.mProgresstime / m.mMaxProgresstime)) : 0;
        float t = ticks();
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        begin();
        gradient(0, 0, w, h, DEEP, 0.95f, 0x150A2A, 0.95f);
        frame(0, 0, w, h, SEAM, 1f);
        double cx = w / 2.0, half = 6;
        // the channel
        gradient(cx - half, 3, cx + half, h - 3, VIOLET, 0.10f, VIOLET, 0.35f);
        // rings falling away
        double speed = working ? 0.9 : 0.15;
        for (int i = 0; i < 7; i++) {
            double at = (t * speed + i * 8) % 56;
            double ry = 3 + at;
            if (ry > h - 4) continue;
            float fade = (float) (at / 56);
            double rw = half + 3 - fade * 3;
            rect(cx - rw, ry, cx + rw, ry + 1, CYAN, 0.55f * (1 - fade));
        }
        // the ore condensing
        if (progress > 0) {
            double top = h - 3 - (h - 6) * progress;
            gradient(cx - half + 1, top, cx + half - 1, h - 3, tier.color(), 0.85f, darker(tier.color()), 0.85f);
            rect(cx - half + 1, top, cx + half - 1, top + 1, WHITE, 0.9f);
        }
        // the lens at the bottom
        rect(cx - 9, h - 4, cx + 9, h - 3, working ? CYAN : SEAM, 1f);
        corners(0, 0, w, h, 4, working ? CYAN : SEAM, 1f);
        end();
        GL11.glPopMatrix();
    }

    /** Under the imprints: how many it uses of how many the tier takes. */
    public static void imprintCaption(MTEFluxCollector m, float x, float y, float w, float h) {
        smallCentered(
            ShardText.imprintCaption(m.state().imprints, m.tier().imprints),
            x + w / 2.0,
            y + 2,
            0.75f,
            DIM,
            1f);
    }

    /** Under the outputs: ores a minute at this tier. */
    public static void speedCaption(MTEFluxCollector m, float x, float y, float w, float h) {
        smallCentered(ShardText.speedCaption(m.tier()), x + w / 2.0, y + 2, 0.75f, DIM, 1f);
    }

    /** Power, speed and the drill head; the vein; and the buffer (EU or steam). */
    public static void readout(MTEFluxCollector m, float x, float y, float w, float h) {
        ShardTier tier = m.tier();
        FontRenderer f = font();
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        int lw = (int) w - 8;
        text(f.trimStringToWidth(ShardText.powerLine(tier), lw), 4, 3, WHITE, 1f);
        text(f.trimStringToWidth(ShardText.wearLine(m.state().headUses, m.state().produced), lw), 4, 13, DIM, 1f);
        String vein = m.guiVein == null || m.guiVein.isEmpty() ? ShardText.noVein() : ShardText.veinLine(m.guiVein);
        text(f.trimStringToWidth(vein, lw), 4, 23, CYAN, 1f);

        begin();
        float fill;
        String label;
        if (tier.steam()) {
            long cap = 16_000;
            fill = (float) Math.min(1, (double) m.guiSteam / cap);
            label = ShardText.steamBar(m.guiSteam * 2, cap * 2);
            bar(1, 37, w - 1, 46, fill, AMBER, 1f);
        } else {
            fill = m.guiEUCap <= 0 ? 0 : (float) Math.min(1, (double) m.guiEU / m.guiEUCap);
            label = ShardText.energyBar(m.guiEU, m.guiEUCap);
            bar(1, 37, w - 1, 46, fill, tier.color(), 1f);
        }
        scan(1, 37, w - 1, 46, ticks(), WHITE, 0.08f);
        end();
        smallCentered(label, w / 2.0, 39, 0.6f, WHITE, 1f);
        GL11.glPopMatrix();
    }
}
