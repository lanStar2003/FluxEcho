package com.fluxlite.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.fluxecho.FluxEcho;
import com.fluxecho.codex.Categories;
import com.fluxecho.codex.ClientLedger;
import com.fluxecho.logic.ResearchTree;
import com.fluxecho.nexus.TerminalView;
import com.fluxecho.research.client.StarMapScreen;
import com.fluxlite.gui.ui.Canvas;
import com.fluxlite.gui.ui.Theme;
import com.fluxlite.net.Kinds;
import com.fluxlite.util.Fmt;

/**
 * The flux terminal (blueprint 3.11): the team network's dashboard, and, chosen on a rail on the left, the team's
 * flux machines, its Echo Codex, the research and the Flux Nexus the terminal is bound to. Works at any distance and
 * in any dimension, like the dashboard.
 */
public class TerminalScreen extends ControlCenterScreen {

    private static final String[] PAGES = { "network", "machines", "codex", "research", "nexus" };
    private static final float RAIL = 76, ROW = 18;

    private int page = TerminalView.NETWORK;
    private NBTTagCompound term;
    private boolean problemsOnly;
    private int offset;

    public TerminalScreen() {
        super(0, 0, 0, 0, true);
    }

    @Override
    public void tick() {
        if (page == TerminalView.NETWORK || page == TerminalView.CODEX) {
            super.tick();
            return;
        }
        if (ticks % 10 == 0) askPage();
        ticks++;
    }

    private void askPage() {
        NBTTagCompound q = new NBTTagCompound();
        q.setBoolean("hand", true);
        q.setInteger("termPage", page);
        host.send(Kinds.TERM_REQUEST, q);
    }

    public void onTermData(NBTTagCompound d) {
        if (d.getInteger("termPage") == page) term = d;
    }

    private void go(int p) {
        if (p == page) return;
        page = p;
        term = null;
        offset = 0;
        if (p != TerminalView.NETWORK && p != TerminalView.CODEX) askPage();
    }

    @Override
    public void scroll(int direction) {
        if (page == TerminalView.NETWORK) super.scroll(direction);
        else offset = Math.max(0, offset - direction * 3);
    }

    @Override
    protected float rail() {
        return RAIL;
    }

    @Override
    protected void rail(Canvas c, float x, float y, float h) {
        for (int i = 0; i < PAGES.length; i++) {
            float ry = y + i * 22;
            boolean sel = i == page, hov = hover(x, ry, RAIL - 8, 20);
            if (sel) c.round(x, ry, RAIL - 8, 20, Theme.RADIUS_CONTROL, Theme.FILL_SELECTED);
            else if (hov) c.round(x, ry, RAIL - 8, 20, Theme.RADIUS_CONTROL, Theme.FILL);
            c.circle(x + 9, ry + 10, 3, sel ? PAGE_COLORS[i] : Theme.withAlpha(PAGE_COLORS[i], 0x99));
            text(c, tr("fluxlite.gui.page." + PAGES[i]), x + 17, ry + 6, sel ? Theme.LABEL : Theme.LABEL2);
            final int p = i;
            onClick(x, ry, RAIL - 8, 20, () -> go(p));
        }
        c.fill(x + RAIL - 4, y, 0.5f, h, Theme.SEPARATOR);
    }

    private static final int[] PAGE_COLORS = { Theme.GREEN, Theme.ORANGE, Theme.PURPLE, Theme.TEAL, Theme.BLUE };

    @Override
    protected boolean page(Canvas c, float x, float y, float w, float h) {
        if (page == TerminalView.NETWORK) return false;
        if (page == TerminalView.CODEX) {
            codex(c, x, y, w, h);
            return true;
        }
        if (term == null) {
            textCenter(c, tr("fluxlite.gui.loading"), x + w / 2, y + h / 2 - 4, Theme.LABEL2, 1);
            return true;
        }
        if (page == TerminalView.MACHINES) machines(c, x, y, w, h);
        else if (page == TerminalView.RESEARCH) research(c, x, y, w, h);
        else nexus(c, x, y, w, h);
        return true;
    }

    // ---- machines

    private void machines(Canvas c, float x, float y, float w, float h) {
        String head = tr(
            "fluxlite.gui.term.machines",
            term.getInteger("total"),
            term.getInteger("working"),
            term.getInteger("problems"));
        text(c, head, x, y + 3, Theme.LABEL2);
        String[] f = { tr("fluxlite.gui.term.all"), tr("fluxlite.gui.term.problems") };
        float fw = segmentedWidth(c, f);
        segmented(c, x + w - fw, y, f, problemsOnly ? 1 : 0, i -> {
            problemsOnly = i == 1;
            offset = 0;
        });
        NBTTagList l = term.getTagList("m", 10);
        float ly = y + 20;
        int rows = (int) ((h - 22) / ROW);
        int shown = 0, skipped = 0;
        if (l.tagCount() == 0) {
            textCenter(c, tr("fluxlite.gui.term.no_machines"), x + w / 2, y + h / 2 - 4, Theme.LABEL3, 1);
            return;
        }
        for (int i = 0; i < l.tagCount() && shown < rows; i++) {
            NBTTagCompound r = l.getCompoundTagAt(i);
            int level = r.getByte("l");
            if (problemsOnly && level != 2) continue;
            if (skipped++ < offset) continue;
            float ry = ly + shown * ROW;
            boolean hov = hover(x, ry, w, ROW - 2);
            c.round(x, ry, w, ROW - 2, Theme.RADIUS_CONTROL, hov ? Theme.CARD_HOVER : Theme.CARD);
            boolean stale = r.getInteger("a") > 15;
            int col = stale ? Theme.GRAY : level == 0 ? Theme.GREEN : level == 1 ? Theme.TEAL : Theme.ORANGE;
            c.circle(x + 8, ry + 8, 3, col);
            String name = tr(r.getString("n"));
            float nw = Math.min(c.width(name, 1), w * 0.38f);
            text(c, fit(c, name, w * 0.38f), x + 16, ry + 4, Theme.LABEL);
            String st = stale ? tr("fluxlite.gui.term.unloaded") : tr(r.getString("s"));
            text(c, fit(c, st, w * 0.36f), x + 22 + nw, ry + 4, stale ? Theme.LABEL3 : Theme.LABEL2);
            String pos = tr(
                "fluxlite.gui.term.pos",
                r.getInteger("d"),
                r.getInteger("x"),
                r.getInteger("y"),
                r.getInteger("z"));
            textRight(c, pos, x + w - 6, ry + 4, Theme.LABEL3);
            final int dim = r.getInteger("d"), px = r.getInteger("x"), py = r.getInteger("y"), pz = r.getInteger("z");
            onClick(x, ry, w, ROW - 2, () -> host.highlight(dim, px, py, pz));
            shown++;
        }
    }

    // ---- codex

    private void codex(Canvas c, float x, float y, float w, float h) {
        int total = 0;
        for (Categories.Category cat : Categories.all()) total += ClientLedger.keys(cat.id)
            .size();
        text(c, tr("fluxlite.gui.term.codex", total), x, y + 3, Theme.LABEL2);
        button(c, x + w - 96, y, 96, 15, tr("fluxlite.gui.term.open_codex"), Theme.PURPLE, () -> {
            host.close();
            FluxEcho.proxy.openCodex(Minecraft.getMinecraft().thePlayer);
        });
        int cols = 3;
        float cw = (w - (cols - 1) * 6) / cols, chh = 34;
        int i = 0;
        for (Categories.Category cat : Categories.all()) {
            float cx = x + i % cols * (cw + 6), cy = y + 22 + i / cols * (chh + 6);
            if (cy + chh > y + h) break;
            card(c, cx, cy, cw, chh);
            text(c, tr("fluxecho.codex.cat." + cat.id), cx + 8, cy + 6, Theme.LABEL2);
            value(
                c,
                Integer.toString(
                    ClientLedger.keys(cat.id)
                        .size()),
                "",
                cx + 8,
                cy + 17,
                Theme.PURPLE,
                1.2f);
            i++;
        }
    }

    // ---- research and the nexus

    private boolean bound(Canvas c, float x, float y, float w, float h) {
        if (term.hasKey("bound") && term.getBoolean("loaded")) return true;
        card(c, x, y, w, 60);
        if (!term.hasKey("bound")) {
            bold(c, tr("fluxlite.gui.term.not_bound"), x + 10, y + 10, Theme.LABEL);
            text(c, tr("fluxlite.gui.term.how_to_bind"), x + 10, y + 26, Theme.LABEL2);
        } else {
            int[] b = term.getIntArray("bound");
            bold(c, tr("fluxlite.gui.term.nexus_unloaded"), x + 10, y + 10, Theme.ORANGE);
            text(c, tr("fluxlite.gui.term.pos", b[0], b[1], b[2], b[3]), x + 10, y + 26, Theme.LABEL2);
        }
        text(
            c,
            tr("fluxlite.gui.term.done", term.getInteger("done"), term.getInteger("nodes")),
            x + 10,
            y + 42,
            Theme.LABEL3);
        return false;
    }

    private void research(Canvas c, float x, float y, float w, float h) {
        if (!bound(c, x, y, w, h)) return;
        int[] b = term.getIntArray("bound");
        card(c, x, y, w, 64);
        String run = term.getString("research");
        if (run.isEmpty()) {
            bold(c, tr("fluxlite.gui.term.no_research"), x + 10, y + 10, Theme.LABEL2);
        } else {
            ResearchTree.Node n = ResearchTree.get(run);
            int col = 0xFF000000 | (n == null ? 0x8A5CFF : n.branch.color);
            float f = term.getFloat("fraction");
            bold(c, tr("fluxecho.research.node." + run), x + 10, y + 10, col);
            textRight(c, Math.round(f * 100) + "%", x + w - 10, y + 10, Theme.LABEL);
            c.round(x + 10, y + 26, w - 20, 6, 3, Theme.FILL);
            c.round(x + 10, y + 26, Math.max(6, (w - 20) * f), 6, 3, col);
        }
        text(
            c,
            tr(
                "fluxlite.gui.term.compute",
                String.format("%.1f", term.getDouble("compute")),
                term.getInteger("unlocked")),
            x + 10,
            y + 40,
            Theme.LABEL2);
        button(c, x + w - 110, y + 38, 100, 16, tr("fluxlite.gui.term.star_map"), Theme.BLUE, () -> {
            host.close();
            Minecraft.getMinecraft()
                .displayGuiScreen(new StarMapScreen(b[0], b[1], b[2], b[3]));
        });
        text(c, tr("fluxlite.gui.term.available"), x, y + 74, Theme.LABEL2);
        NBTTagList l = term.getTagList("avail", 10);
        float px = x, py = y + 88;
        if (l.tagCount() == 0) text(c, tr("fluxlite.gui.term.none_available"), x, py, Theme.LABEL3);
        for (int i = 0; i < l.tagCount(); i++) {
            String id = l.getCompoundTagAt(i)
                .getString("id");
            ResearchTree.Node n = ResearchTree.get(id);
            String label = tr("fluxecho.research.node." + id);
            float pw = c.width(label, 1) + 10;
            if (px + pw > x + w) {
                px = x;
                py += 15;
            }
            pill(c, px, py, label, 0xFF000000 | (n == null ? 0x8A5CFF : n.branch.color));
            px += pw + 4;
        }
    }

    private void nexus(Canvas c, float x, float y, float w, float h) {
        if (!bound(c, x, y, w, h)) return;
        boolean formed = term.getBoolean("formed"), powered = term.getBoolean("powered");
        String status = term.getString("status");
        int cols = 3;
        float cw = (w - (cols - 1) * 6) / cols, chh = 36;
        String[][] cards = { { tr("fluxlite.gui.term.state"), tr("fluxecho.nexus.status." + status) },
            { tr("fluxlite.gui.term.owner"), term.getString("owner") },
            { tr("fluxlite.gui.term.upkeep"), Fmt.si(term.getLong("upkeep")) + " EU/t" },
            { tr("fluxlite.gui.term.balance"), si(term.getString("balance")) + " EU" },
            { tr("fluxlite.gui.term.compute_short"), String.format("%.1f FC/s", term.getDouble("compute")) },
            { tr("fluxlite.gui.term.bound_teams"), Integer.toString(term.getInteger("boundTeams")) } };
        for (int i = 0; i < cards.length; i++) {
            float cx = x + i % cols * (cw + 6), cy = y + i / cols * (chh + 6);
            card(c, cx, cy, cw, chh);
            text(c, cards[i][0], cx + 8, cy + 6, Theme.LABEL2);
            int col = i == 0 ? (!formed || !powered ? Theme.RED : Theme.GREEN) : Theme.LABEL;
            bold(c, fit(c, cards[i][1], cw - 16), cx + 8, cy + 19, col);
        }
        float my = y + 2 * (chh + 6) + 4;
        NBTTagList mods = term.getTagList("modules", 10);
        int open = term.getInteger("open");
        text(
            c,
            open <= 0 ? tr("fluxlite.gui.term.ring_closed") : tr("fluxlite.gui.term.ring", mods.tagCount(), open),
            x,
            my,
            Theme.LABEL2);
        for (int i = 0; i < mods.tagCount(); i++) {
            NBTTagCompound m = mods.getCompoundTagAt(i);
            float ry = my + 14 + i * ROW;
            if (ry + ROW > y + h) break;
            card(c, x, ry, w, ROW - 2);
            c.circle(x + 8, ry + 8, 3, 0xFF000000 | m.getInteger("c"));
            text(
                c,
                tr("fluxlite.gui.term.slot", m.getInteger("slot") + 1) + "  "
                    + tr("fluxecho.module." + m.getString("k")),
                x + 16,
                ry + 4,
                Theme.LABEL);
            textRight(c, tr("fluxecho.dock." + m.getString("dock")), x + w - 8, ry + 4, Theme.LABEL2);
        }
    }

    private static String si(String decimal) {
        try {
            return Fmt.si(new java.math.BigInteger(decimal));
        } catch (NumberFormatException e) {
            return decimal;
        }
    }
}
