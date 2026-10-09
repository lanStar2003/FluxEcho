package com.fluxecho.nexus.client;

import static com.fluxecho.client.FluxDraw.*;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.fluxecho.client.Motifs;
import com.fluxecho.core.EchoText;
import com.fluxecho.logic.Compact;
import com.fluxecho.logic.ResearchTree;
import com.fluxecho.nexus.Manifests;
import com.fluxecho.nexus.NexusGui;
import com.fluxecho.nexus.TileNexus;
import com.fluxecho.research.client.StarMapScreen;

/** The drawn parts of the Flux Nexus's GUI ({@code NexusGui}). Client only. */
public final class NexusScreen {

    private static final RenderItem ITEMS = new RenderItem();

    private NexusScreen() {}

    private static float ticks() {
        return Minecraft.getSystemTime() / 50f;
    }

    /** The window: the pane, the two panels and their titles. */
    public static void background(TileNexus n, float x, float y, float w, float h) {
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        try {
            background0(n, w, h);
        } finally {
            GL11.glPopMatrix();
        }
    }

    private static void background0(TileNexus n, float w, float h) {
        begin();
        pane(0, 0, w, h, 1f);
        panel(6, 22, 136, 160);
        panel(140, 22, 242, 120);
        rect(8, 163, w - 8, 164, SEAM, 1f);
        end();
        text(EchoText.t("nexus.gui.table"), 10, 25, VIOLET, 1f);
    }

    private static void panel(double x0, double y0, double x1, double y1) {
        gradient(x0, y0, x1, y1, 0x0C1A28, 0.9f, DEEP, 0.9f);
        frame(x0, y0, x1, y1, SEAM, 1f);
    }

    /** Phase and name on the left, what it does on the right. */
    public static void header(TileNexus n, float x, float y, float w, float h) {
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        try {
            header0(n, w, h);
        } finally {
            GL11.glPopMatrix();
        }
    }

    private static void header0(TileNexus n, float w, float h) {
        begin();
        hgradient(1, 1, w * 0.6, h - 1, CYAN, 0.28f, CYAN, 0f);
        rect(1, h - 1, w - 1, h, CYAN, 0.6f);
        end();
        String left = EchoText.t("nexus.gui.title", EchoText.t("nexus.phase." + TileNexus.PHASE));
        text(left, 4, 3, CYAN, 1f);
        String status = n.guiStatus;
        right(
            fit(EchoText.t("nexus.status." + status), (int) w - 12 - font().getStringWidth(left)),
            (int) w - 4,
            3,
            color(status),
            1f);
    }

    static int color(String status) {
        switch (status) {
            case "manifesting":
            case "researching":
                return GREEN;
            case "ready":
                return CYAN;
            case "unformed":
            case "no_power":
            case "no_owner":
            case "disabled":
                return RED;
            default:
                return AMBER;
        }
    }

    /**
     * The making, between the inputs and the outputs: a crystal lattice turning; while it manifests, motes run in from
     * the inputs and the result takes shape in the middle.
     */
    public static void making(TileNexus n, float x, float y, float w, float h) {
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        try {
            making0(n, w, h);
        } finally {
            GL11.glPopMatrix();
        }
    }

    private static void making0(TileNexus n, float w, float h) {
        float t = ticks();
        boolean on = !n.guiManifest.isEmpty();
        float p = n.guiManifestPm / 1000f;
        double cx = w / 2.0, cy = h / 2.0;
        begin();
        gradient(0, 0, w, h, DEEP, 0.95f, 0x120A30, 0.95f);
        frame(0, 0, w, h, SEAM, 1f);
        int hexes = 6;
        for (int i = 0; i < hexes; i++) {
            double ang = t * 0.03 + i * Math.PI * 2 / hexes;
            double r = 13 + Math.sin(t * 0.05 + i) * 1.5;
            Motifs.dot(cx + Math.cos(ang) * r, cy + Math.sin(ang) * r * 0.6, on ? 2 : 1.5, on ? VIOLET : SEAM, 0.9f);
        }
        if (on) {
            for (int i = 0; i < 4; i++) {
                double f = (t * 0.05 + i / 4.0) % 1;
                Motifs.dot(f * cx, cy + Math.sin(i * 1.7) * 10 * (1 - f), 1.5, CYAN, (float) f);
                Motifs.dot(w - f * (w - cx), cy, 1.2, VIOLET, (float) (1 - f) * 0.6f);
            }
            int seg = 24;
            for (int i = 0; i < seg * p; i++) {
                double ang = -Math.PI / 2 + i * Math.PI * 2 / seg;
                Motifs.dot(cx + Math.cos(ang) * 17, cy + Math.sin(ang) * 17, 1.6, GREEN, 1f);
            }
        }
        Motifs.hex(cx, cy, on ? 6 + 2 * p : 5, on ? VIOLET : SEAM, on ? 0.35f + 0.4f * p : 0.5f);
        if (on) scan(1, 1, w - 1, h - 1, t, VIOLET, 0.08f);
        corners(0, 0, w, h, 4, on ? VIOLET : SEAM, 1f);
        end();
        ItemStack making = n.clientMaking;
        if (on && making != null) item(making, cx - 8, cy - 8, 0.35f + 0.65f * p);
    }

    /** An item in the GUI, faded in by {@code a} (scaled from the middle). */
    static void item(ItemStack s, double x, double y, float a) {
        Minecraft mc = Minecraft.getMinecraft();
        GL11.glPushMatrix();
        GL11.glTranslated(x + 8, y + 8, 0);
        float k = 0.6f + 0.4f * a;
        GL11.glScalef(k, k, 1);
        GL11.glTranslated(-8, -8, 0);
        RenderHelper.enableGUIStandardItemLighting();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        ITEMS.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), s, 0, 0);
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glPopMatrix();
    }

    /** Under the slots: what it makes now, what it can make, the codex entries for echo crystals. */
    public static void table(TileNexus n, float x, float y, float w, float h) {
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        try {
            table0(n, w, h);
        } finally {
            GL11.glPopMatrix();
        }
    }

    private static void table0(TileNexus n, float w, float h) {
        int lw = (int) w - 8;
        int y = 2;
        if (!n.guiManifest.isEmpty()) {
            String name = n.clientMaking != null ? n.clientMaking.getDisplayName() : n.guiManifest;
            text(fit(EchoText.t("nexus.gui.making", name, n.guiManifestPm / 10), lw), 4, y, GREEN, 1f);
            begin();
            bar(4, y + 11, w - 4, y + 16, n.guiManifestPm / 1000f, VIOLET, 1f);
            end();
        } else text(fit(EchoText.t("nexus.gui.idle_table"), lw), 4, y, DIM, 1f);
        y += 20;
        Set<String> open = new HashSet<>(Arrays.asList(n.guiUnlocked.split(",")));
        List<String> names = new ArrayList<>();
        for (Manifests.Recipe r : Manifests.all()) {
            if (!open.contains(r.research)) continue;
            ItemStack o = r.output();
            names.add(o == null ? r.id : o.getDisplayName());
        }
        String can = names.isEmpty() ? EchoText.t("nexus.gui.nothing_open") : String.join(" · ", names);
        small(EchoText.t("nexus.gui.can_make"), 4, y, 0.75f, DIM, 1f);
        y += 8;
        for (String line : wrap(can, (int) ((w - 8) / 0.75f), 2)) {
            small(line, 4, y, 0.75f, names.isEmpty() ? AMBER : WHITE, 1f);
            y += 8;
        }
        y = (int) h - 10;
        small(
            fit(
                EchoText.t("nexus.gui.records", n.guiRecords, n.guiResting, NexusGui.cooldownMinutes()),
                (int) ((w - 8) / 0.75f)),
            4,
            y,
            0.75f,
            VIOLET,
            1f);
    }

    /** The text broken into lines of at most {@code width} font pixels, at most {@code max} lines. */
    static List<String> wrap(String s, int width, int max) {
        @SuppressWarnings("unchecked")
        List<String> lines = font().listFormattedStringToWidth(s, width);
        if (lines.size() <= max) return lines;
        List<String> out = new ArrayList<>(lines.subList(0, max));
        out.set(max - 1, fit(out.get(max - 1) + "…", width));
        return out;
    }

    /** The nexus's state: power, network, compute, research, ring, bound teams. */
    public static void state(TileNexus n, float x, float y, float w, float h) {
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        try {
            state0(n, w, h);
        } finally {
            GL11.glPopMatrix();
        }
    }

    private static void state0(TileNexus n, float w, float h) {
        int y = 3, lw = (int) w - 6;
        y = row(
            EchoText.t("nexus.gui.upkeep"),
            Compact.si(n.guiUpkeep) + " EU/t",
            n.clientActivity % 2 == 1 ? WHITE : RED,
            y,
            w);
        y = row(EchoText.t("nexus.gui.network"), si(n.guiBalance) + " EU", WHITE, y, w);
        y = row(EchoText.t("nexus.gui.compute"), n.guiCompute10 / 10.0 + " FC/s", CYAN, y, w);
        String open = n.guiOpen <= 0 ? EchoText.t("nexus.gui.ring_closed") : n.guiDocked + " / " + n.guiOpen;
        y = row(EchoText.t("nexus.gui.ring"), open, n.guiOpen <= 0 ? AMBER : WHITE, y, w);
        y = row(EchoText.t("nexus.gui.bound"), String.valueOf(n.guiBound), WHITE, y, w);
        y += 4;
        begin();
        rect(3, y - 2, w - 3, y - 1, SEAM, 1f);
        end();
        text(EchoText.t("nexus.gui.research"), 3, y + 1, VIOLET, 1f);
        y += 11;
        if (n.guiResearch.isEmpty()) {
            for (String line : wrap(EchoText.t("nexus.gui.no_research"), lw, 3)) {
                small(line, 3, y, 0.75f, DIM, 1f);
                y += 8;
            }
        } else {
            ResearchTree.Node node = ResearchTree.get(n.guiResearch);
            int c = node == null ? VIOLET : node.branch.color;
            text(fit(EchoText.t("research.node." + n.guiResearch), lw), 3, y, c, 1f);
            begin();
            bar(3, y + 11, w - 3, y + 17, n.guiResearchPm / 1000f, c, 1f);
            scan(3, y + 11, w - 3, y + 17, ticks(), WHITE, 0.08f);
            end();
            smallCentered(n.guiResearchPm / 10 + "%", w / 2.0, y + 12, 0.6f, WHITE, 1f);
        }
    }

    private static int row(String label, String value, int color, int y, float w) {
        small(label, 3, y, 0.75f, DIM, 1f);
        smallRight(value, w - 3, y, 0.75f, color, 1f);
        return y + 9;
    }

    /** A big number in SI form, from its decimal string. */
    static String si(String decimal) {
        try {
            BigInteger v = new BigInteger(decimal);
            if (v.bitLength() < 63) return Compact.si(v.longValue());
            String s = v.toString();
            return s.charAt(0) + "." + s.substring(1, 3) + "e" + (s.length() - 1);
        } catch (NumberFormatException e) {
            return decimal;
        }
    }

    /** A flux button: a pane with its label, lit when on. */
    public static void button(String label, float x, float y, float w, float h, boolean on) {
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        try {
            button0(label, w, h, on);
        } finally {
            GL11.glPopMatrix();
        }
    }

    private static void button0(String label, float w, float h, boolean on) {
        begin();
        gradient(0, 0, w, h, on ? 0x16384A : PANE, 1f, DEEP, 1f);
        frame(0, 0, w, h, on ? CYAN : SEAM, 1f);
        corners(0, 0, w, h, 3, CYAN, on ? 1f : 0.6f);
        end();
        centered(fit(label, (int) w - 4), w / 2.0, (h - 8) / 2.0 + 1, on ? WHITE : CYAN, 1f);
    }

    /** Leaves the GUI (telling the server) and opens the star map of this nexus. */
    public static void openStarMap(TileNexus n) {
        Minecraft mc = Minecraft.getMinecraft();
        mc.thePlayer.closeScreen();
        mc.displayGuiScreen(new StarMapScreen(n.getWorldObj().provider.dimensionId, n.xCoord, n.yCoord, n.zCoord));
    }
}
