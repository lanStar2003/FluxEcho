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

import com.fluxecho.campus.BuildJob;
import com.fluxecho.client.Motifs;
import com.fluxecho.core.EchoText;
import com.fluxecho.logic.BuildState;
import com.fluxecho.logic.Compact;
import com.fluxecho.logic.ResearchTree;
import com.fluxecho.nexus.Manifests;
import com.fluxecho.nexus.NexusGui;
import com.fluxecho.nexus.TileNexus;
import com.fluxecho.research.client.StarMapScreen;

/**
 * The drawn parts of the Flux Nexus's GUI ({@code NexusGui}): the window, the page tabs, the nexus page's panels and
 * the build page's job panel, bill and balance. Client only.
 */
public final class NexusScreen {

    private static final RenderItem ITEMS = new RenderItem();
    /** Button accents: the flux cyan, and the red of a button that ends something. */
    public static final int ACCENT = CYAN, DANGER = RED;

    private NexusScreen() {}

    private static float ticks() {
        return Minecraft.getSystemTime() / 50f;
    }

    /** The window: the pane, the page's two panels and their titles. */
    public static void background(TileNexus n, int page, float x, float y, float w, float h) {
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        try {
            background0(n, page, w, h);
        } finally {
            GL11.glPopMatrix();
        }
    }

    private static void background0(TileNexus n, int page, float w, float h) {
        begin();
        pane(0, 0, w, h, 1f);
        if (page == NexusGui.PAGE_BUILD) {
            panel(6, 22, 136, 160);
            panel(140, 22, 242, 160);
            // under the job panel, above the buttons; under the bill, above the intake
            rect(10, NexusGui.ROW1_Y - 2, 132, NexusGui.ROW1_Y - 1, SEAM, 1f);
            rect(144, NexusGui.INTAKE_Y - 4, 238, NexusGui.INTAKE_Y - 3, SEAM, 1f);
        } else {
            panel(6, 22, 136, 160);
            panel(140, 22, 242, 120);
        }
        rect(8, 163, w - 8, 164, SEAM, 1f);
        end();
        if (page != NexusGui.PAGE_BUILD) text(EchoText.t("nexus.gui.table"), 10, 25, VIOLET, 1f);
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
            case "building":
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
        button(label, x, y, w, h, on, false, ACCENT);
    }

    /**
     * A flux button in an accent colour: lit when on, greyed when it would do nothing now (it still takes the click,
     * and the server says why nothing happened).
     */
    public static void button(String label, float x, float y, float w, float h, boolean on, boolean dim, int accent) {
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        try {
            button0(label, w, h, on, dim, accent);
        } finally {
            GL11.glPopMatrix();
        }
    }

    private static void button0(String label, float w, float h, boolean on, boolean dim, int accent) {
        begin();
        gradient(0, 0, w, h, on ? 0x16384A : PANE, 1f, DEEP, 1f);
        frame(0, 0, w, h, dim ? SEAM : on ? accent : SEAM, 1f);
        corners(0, 0, w, h, 3, dim ? SEAM : accent, dim ? 0.8f : on ? 1f : 0.6f);
        end();
        centered(fit(label, (int) w - 4), w / 2.0, (h - 8) / 2.0 + 1, dim ? DIM : on ? WHITE : accent, 1f);
    }

    /** A page tab: lit with a line along its foot when its page is shown. */
    public static void tab(String label, float x, float y, float w, float h, boolean on) {
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        try {
            begin();
            gradient(0, 0, w, h, on ? 0x16384A : PANE, 1f, DEEP, 1f);
            frame(0, 0, w, h, on ? CYAN : SEAM, 1f);
            if (on) rect(1, h - 2, w - 1, h - 1, CYAN, 0.9f);
            end();
            centered(fit(label, (int) w - 4), w / 2.0, (h - 8) / 2.0, on ? WHITE : DIM, 1f);
        } finally {
            GL11.glPopMatrix();
        }
    }

    // ---- the build page

    /**
     * The job panel: the job and its site, its state (and why it waits), its stage, the progress bar, EU/t and the
     * time left, the counts and the queue; or what to do when there is no job, or no campus yet.
     */
    public static void buildJob(NexusGui.View v, float x, float y, float w, float h) {
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        try {
            buildJob0(v.build, w, h);
        } finally {
            GL11.glPopMatrix();
        }
    }

    private static void buildJob0(NexusGui.Build b, float w, float h) {
        if (!b.received) return;
        int lw = (int) w - 4, sw = (int) ((w - 4) / 0.75f);
        if (!b.enabled) {
            text(fit(EchoText.t("build.gui.disabled"), lw), 2, 2, RED, 1f);
            return;
        }
        if (!b.active) {
            text(fit(EchoText.t("build.gui.legacy"), lw), 2, 2, AMBER, 1f);
            paragraph(EchoText.t("build.gui.legacy_hint"), 14, sw, 6, DIM);
            return;
        }
        if (!b.hasJob()) {
            text(fit(EchoText.t("build.gui.no_job"), lw), 2, 2, DIM, 1f);
            paragraph(EchoText.t("build.gui.no_job_hint"), 14, sw, 5, DIM);
            queue(b, w, h);
            return;
        }
        String name = BuildJob.name(b.job, b.planKey)
            .getUnformattedText();
        text(fit(name, lw), 2, 2, CYAN, 1f);

        BuildState.State s = b.stateEnum();
        BuildState.Pause p = b.pauseEnum();
        String st = EchoText.t(NexusGui.stateKey(s));
        if (s == BuildState.State.PAUSED && p != BuildState.Pause.NONE) st += " · " + EchoText.t(NexusGui.pauseKey(p));
        text(fit(st, lw), 2, 13, stateColor(s, p), 1f);

        int stages = Math.max(1, b.stages);
        String stage = EchoText.t(
            "build.gui.stage",
            Math.min(b.stage + 1, stages),
            stages,
            EchoText.t(NexusGui.stageKey(b.planKey, b.stage)));
        small(fit(stage, sw), 2, 24, 0.75f, WHITE, 1f);

        float f = b.total <= 0 ? 0 : Math.min(1f, b.placed / (float) b.total);
        boolean building = s == BuildState.State.BUILDING;
        begin();
        bar(2, 32, w - 2, 39, f, building || s == BuildState.State.DONE ? GREEN : CYAN, 1f);
        if (building) scan(2, 32, w - 2, 39, ticks(), WHITE, 0.08f);
        end();
        smallCentered(
            EchoText.t("build.gui.progress", b.placed, b.total, Math.round(f * 100)),
            w / 2.0,
            33,
            0.6f,
            WHITE,
            1f);

        small(EchoText.t("build.gui.eu", Compact.si(b.eu)), 2, 43, 0.75f, b.eu > 0 ? WHITE : DIM, 1f);
        smallRight(EchoText.t("build.gui.eta", eta(b.eta)), w - 2, 43, 0.75f, DIM, 1f);
        small(EchoText.t("build.gui.cleared", b.cleared), 2, 52, 0.75f, DIM, 1f);
        smallRight(EchoText.t("build.gui.blocked", b.blocked), w - 2, 52, 0.75f, b.blocked > 0 ? AMBER : DIM, 1f);
        small(EchoText.t("build.gui.skipped", b.skipped), 2, 60, 0.75f, b.skipped > 0 ? AMBER : DIM, 1f);
        smallRight(EchoText.t("build.gui.unloaded", b.unloaded), w - 2, 60, 0.75f, b.unloaded > 0 ? AMBER : DIM, 1f);
        queue(b, w, h);
    }

    /** The queued jobs, on the panel's last line. */
    private static void queue(NexusGui.Build b, float w, float h) {
        List<String> names = new ArrayList<>();
        for (String[] q : b.queue) names.add(
            BuildJob.name(q[0], q[1])
                .getUnformattedText());
        String line = EchoText
            .t("build.gui.queue", names.isEmpty() ? EchoText.t("build.gui.none") : String.join(" · ", names));
        small(fit(line, (int) ((w - 4) / 0.75f)), 2, h - 9, 0.75f, names.isEmpty() ? DIM : VIOLET, 1f);
    }

    /** Wrapped small text from a height, at most {@code max} lines. */
    private static void paragraph(String s, int y, int width, int max, int color) {
        for (String line : wrap(s, width, max)) {
            small(line, 2, y, 0.75f, color, 1f);
            y += 8;
        }
    }

    /** Green while it builds or is done, amber while it waits for something it gets by itself, red when stuck. */
    static int stateColor(BuildState.State s, BuildState.Pause p) {
        switch (s) {
            case BUILDING:
            case DONE:
                return GREEN;
            case CANCELLED:
                return DIM;
            case PAUSED:
                return p == BuildState.Pause.BLOCKED || p == BuildState.Pause.PROTECTED
                    || p == BuildState.Pause.INCOMPLETE ? RED : AMBER;
            default:
                return CYAN;
        }
    }

    /** Ticks as h:mm:ss or m:ss; "?" when the job cannot progress. */
    static String eta(long ticks) {
        if (ticks < 0) return "?";
        long sec = (ticks + 19) / 20, hours = sec / 3600, min = sec % 3600 / 60;
        return hours > 0 ? String.format("%d:%02d:%02d", hours, min, sec % 60)
            : String.format("%d:%02d", min, sec % 60);
    }

    /** The site between ◀ and ▶: its number, or a dash for jobs without one. */
    public static void site(NexusGui.View v, float x, float y, float w, float h) {
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        try {
            NexusGui.Build b = v.build;
            begin();
            gradient(0, 0, w, h, DEEP, 1f, PANE, 1f);
            frame(0, 0, w, h, SEAM, 1f);
            end();
            String label = b.live() && b.site >= 0 ? EchoText.t("build.gui.site", b.site) : "—";
            smallCentered(
                fit(label, (int) ((w - 2) / 0.75f)),
                w / 2.0,
                (h - 6) / 2.0,
                0.75f,
                b.canMove ? WHITE : DIM,
                1f);
        } finally {
            GL11.glPopMatrix();
        }
    }

    /** The bill's title: 材料, or that everything is there. */
    public static void billHead(NexusGui.View v, float x, float y, float w, float h) {
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        try {
            NexusGui.Build b = v.build;
            text(EchoText.t("build.gui.bill"), 2, 1, VIOLET, 1f);
            if (b.live() && b.bill.isEmpty()) smallRight(EchoText.t("build.gui.bill_done"), w, 2, 0.75f, GREEN, 1f);
        } finally {
            GL11.glPopMatrix();
        }
    }

    /** The bill's pager: the page shown of how many. */
    public static void pager(NexusGui.View v, float x, float y, float w, float h) {
        int pages = Math.max(1, (v.build.bill.size() + NexusGui.BILL_ROWS - 1) / NexusGui.BILL_ROWS);
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        try {
            begin();
            gradient(0, 0, w, h, PANE, 1f, DEEP, 1f);
            frame(0, 0, w, h, SEAM, 1f);
            end();
            smallCentered((v.billPage % pages + 1) + "/" + pages, w / 2.0, (h - 6) / 2.0, 0.75f, CYAN, 1f);
        } finally {
            GL11.glPopMatrix();
        }
    }

    /** One bill line: the item, its name, and what the ledger holds of what the job needs (green when enough). */
    public static void billRow(NexusGui.View v, int line, float x, float y, float w, float h) {
        int i = v.billPage * NexusGui.BILL_ROWS + line;
        List<NexusGui.Row> bill = v.build.bill;
        if (i >= bill.size()) return;
        NexusGui.Row r = bill.get(i);
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        try {
            if (line % 2 == 0) {
                begin();
                rect(0, 0, w, h, SEAM, 0.35f);
                end();
            }
            String count = EchoText.t("build.gui.have_need", Compact.si(r.have), Compact.si(r.need));
            float countW = font().getStringWidth(count) * 0.75f;
            smallRight(count, w - 1, 3, 0.75f, r.have >= r.need ? GREEN : AMBER, 1f);
            String name = r.item != null ? r.item.getDisplayName() : r.key;
            int nameW = (int) ((w - 15 - countW - 2) / 0.6f);
            if (nameW > 6) small(fit(name, nameW), 14, 3.5, 0.6f, DIM, 1f);
            if (r.item != null) icon(r.item, 1, 0, 0.75f);
        } finally {
            GL11.glPopMatrix();
        }
    }

    /** Next to the intake slot: its name, the balance and the spoils. */
    public static void buildFoot(NexusGui.View v, float x, float y, float w, float h) {
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        try {
            NexusGui.Build b = v.build;
            small(EchoText.t("build.gui.intake"), 0, 1, 0.75f, VIOLET, 1f);
            small(
                fit(EchoText.t("build.gui.balance_line", b.balanceItems, b.balanceParts), (int) (w / 0.6f)),
                0,
                9,
                0.6f,
                b.balanceItems + b.balanceParts > 0 ? WHITE : DIM,
                1f);
            small(
                fit(EchoText.t("build.gui.spoils", b.spoils), (int) (w / 0.6f)),
                0,
                15,
                0.6f,
                b.spoils > 0 ? AMBER : DIM,
                1f);
        } finally {
            GL11.glPopMatrix();
        }
    }

    /** An item in the GUI at a scale, its corner at (x, y). */
    static void icon(ItemStack s, double x, double y, float scale) {
        Minecraft mc = Minecraft.getMinecraft();
        GL11.glPushMatrix();
        GL11.glTranslated(x, y, 0);
        GL11.glScalef(scale, scale, 1);
        RenderHelper.enableGUIStandardItemLighting();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        ITEMS.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), s, 0, 0);
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glPopMatrix();
    }

    /** Leaves the GUI (telling the server) and opens the star map of this nexus. */
    public static void openStarMap(TileNexus n) {
        Minecraft mc = Minecraft.getMinecraft();
        mc.thePlayer.closeScreen();
        mc.displayGuiScreen(new StarMapScreen(n.getWorldObj().provider.dimensionId, n.xCoord, n.yCoord, n.zCoord));
    }
}
