package com.fluxecho.city.client;

import static com.fluxecho.client.FluxDraw.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.util.Vec3;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.fluxecho.city.CityGui;
import com.fluxecho.city.TileCityModule;
import com.fluxecho.client.Motifs;
import com.fluxecho.core.EchoText;
import com.fluxecho.logic.ModuleMap;
import com.fluxecho.logic.SiteFamily;
import com.fluxecho.logic.UnitBrowse;
import com.fluxecho.logic.WrongCell;
import com.fluxecho.nexus.client.NexusScreen;

/**
 * The drawn parts of a city module's window ({@code CityGui}), in the flux look of the nexus and the Echo Archive
 * (navy panes, cyan seams) with the module's own colour where its light shows: the window and its panels, the header,
 * the state's lines and the living emblem, the unit list and the unit shown, the floor plans, and the strip below.
 * Units
 * are coloured by how full they are: dim when empty, cyan as they fill, the module's colour when full. Everything reads
 * the client's copy of the samples ({@link TileCityModule#clientSamples}) and the window's synced fields
 * ({@link CityGui.View}). Client only.
 */
public final class CityScreen {

    private static final RenderItem ITEMS = new RenderItem();
    // the plans
    private static final int FLOOR = 0x0C1A28, GROUND = 0x13283A, WALL = 0x3A6A86, MASS = 0x1A2C3C, POST = 0x2A4054,
        LIT_FLOOR = 0x1E5A6E, GRASS = 0x2F5A3A, WATER = 0x2A5C9A, RAIL = 0x4A7A92, STAIR = 0x24485A, FIXTURE = 0x2C6C7E,
        EMPTY = 0x24384A, PART = 0x1C5C6E;

    private CityScreen() {}

    private static float ticks() {
        return Minecraft.getSystemTime() / 50f;
    }

    // ---- the window

    /** The pane, the page's panels, the frame round the grid, and the lines over the strip and the inventory. */
    public static void background(TileCityModule c, CityGui.View v, int slots, float x, float y, float w, float h) {
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        try {
            int accent = c.moduleColor();
            begin();
            pane(0, 0, w, h, 1f);
            int top = CityGui.PAGE_TOP, bottom = CityGui.PAGE_BOTTOM, right = CityGui.PANEL_RIGHT;
            if (v.page == CityGui.STATUS) {
                panel(6, top, CityGui.STATUS_RIGHT, bottom, 0x0C1A28);
                panel(CityGui.STATUS_RIGHT + 4, top, right, bottom, darkened(accent));
            } else if (v.page == CityGui.UNITS) {
                panel(6, top, CityGui.LIST_RIGHT, bottom, 0x0C1A28);
                panel(CityGui.DETAIL_LEFT, top, right, bottom, darkened(accent));
                grid(c, v, slots, accent);
                rect(CityGui.HEAD_X, CityGui.NAV_Y - 3, right - 4, CityGui.NAV_Y - 2, SEAM, 1f);
            } else {
                panel(6, top, CityGui.PLAN_RIGHT, bottom, 0x0C1A28);
                panel(CityGui.SIDE_X - 4, top, right, bottom, darkened(accent));
                rect(CityGui.SIDE_X, CityGui.INFO_Y - 3, right - 4, CityGui.INFO_Y - 2, SEAM, 1f);
            }
            rect(8, CityGui.STRIP_Y, w - 8, CityGui.STRIP_Y + 1, SEAM, 1f);
            rect(8, CityGui.INV_Y - 3, w - 8, CityGui.INV_Y - 2, SEAM, 1f);
            end();
        } finally {
            GL11.glPopMatrix();
        }
    }

    /** A page's panel: a gradient from {@code top} down to the deep navy, a seam round it. */
    private static void panel(double x0, double y0, double x1, double y1, int top) {
        gradient(x0, y0, x1, y1, top, 0.9f, DEEP, 0.9f);
        frame(x0, y0, x1, y1, SEAM, 1f);
    }

    /** The module's colour sunk deep into the navy, for the top of its panels. */
    private static int darkened(int accent) {
        return Motifs.mix(0x0C1A28, accent, 0.12);
    }

    /**
     * The frame round the unit shown's slots: the module's colour, lines running in to its back, brighter the fuller
     * it is (only the slots the unit has).
     */
    private static void grid(TileCityModule c, CityGui.View v, int slots, int accent) {
        int u = v.unit;
        int n = UnitBrowse.valid(u, c.unitCount()) ? c.unitSlots(u)[1] : 0;
        if (n <= 0) return;
        int[] a = CityGui.gridPos(slots, 0), b = CityGui.gridPos(slots, Math.min(n, slots) - 1);
        int cols = Math.min(CityGui.GRID_COLS, n);
        double x0 = a[0] - 3, y0 = a[1] - 3, x1 = a[0] + cols * 18 + 3, y1 = b[1] + 21;
        gradient(x0, y0, x1, y1, darkened(accent), 0.9f, DEEP, 0.9f);
        float t = ticks(), fill = c.unitFill(u) / (float) n;
        double cx = (x0 + x1) / 2, cy = (y0 + y1) / 2, hw = (x1 - x0) / 2, hh = (y1 - y0) / 2;
        for (int i = 0; i < 4; i++) {
            double f = (t * 0.012 + i / 4.0) % 1;
            frame(cx - hw * (1 - f), cy - hh * (1 - f), cx + hw * (1 - f), cy + hh * (1 - f), accent, 0.1f * (float) f);
        }
        frame(x0, y0, x1, y1, accent, 0.45f + 0.4f * fill);
        corners(x0, y0, x1, y1, 3, accent, 1f);
    }

    /** The module's name in its colour on the left, its dock state on the right (red when docked without power). */
    public static void header(TileCityModule c, CityGui.View v, float x, float y, float w, float h) {
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        try {
            int accent = c.moduleColor();
            begin();
            hgradient(1, 1, w * 0.6, h - 1, accent, 0.28f, accent, 0f);
            rect(1, h - 1, w - 1, h, accent, 0.6f);
            end();
            String title = EchoText.t("module." + c.moduleKey());
            text(fit(title, (int) w - 8), 4, 3, accent, 1f);
        } finally {
            GL11.glPopMatrix();
        }
    }

    /** The dock state in words, {@code dock.no_power} for a docked module without power. */
    static String dockText(CityGui.View v) {
        boolean docked = "docked".equals(v.dock);
        return EchoText.t("dock." + (docked && !v.powered ? "no_power" : v.dock));
    }

    /** The dock state's colour: green docked and powered, red docked without power, amber otherwise. */
    static int dockColour(CityGui.View v) {
        boolean docked = "docked".equals(v.dock);
        return docked ? v.powered ? GREEN : RED : AMBER;
    }

    // ---- 状态

    /**
     * The state, a line each: the structure, the dock, the power, the upkeep, the phase, the site, the units and
     * samples, the owner; under them what to do about the first thing that stops it.
     */
    public static void status(TileCityModule c, CityGui.View v, float x, float y, float w, float h) {
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        try {
            boolean docked = "docked".equals(v.dock);
            String none = EchoText.t("city.gui.status.none");
            int n = c.sampleCount(), slots = c.samples()
                .getSlots();
            line(
                0,
                "structure",
                EchoText.t(v.formed ? "city.gui.status.formed" : "city.gui.status.unformed"),
                v.formed ? GREEN : RED,
                w);
            line(1, "dock", dockText(v), dockColour(v), w);
            line(
                2,
                "power",
                EchoText.t(v.powered ? "city.gui.status.powered" : "city.gui.status.unpowered"),
                v.powered ? GREEN : docked ? RED : DIM,
                w);
            line(3, "upkeep", EchoText.t("city.gui.status.upkeep_value", v.upkeep), v.powered ? CYAN : DIM, w);
            line(4, "phase", v.phase > 0 ? EchoText.t("nexus.phase." + v.phase) : none, v.phase > 0 ? CYAN : DIM, w);
            line(
                5,
                "site",
                v.site >= 0 ? EchoText.t(SiteFamily.labelKey(v.site), v.site) : none,
                v.site >= 0 ? CYAN : DIM,
                w);
            line(
                6,
                "units",
                EchoText.t("city.gui.status.units_value", c.unitCount(), n, slots),
                n > 0 ? c.moduleColor() : DIM,
                w);
            line(7, "owner", v.owner.isEmpty() ? none : v.owner, WHITE, w);
            String hint = hintText(v);
            double ly = 8 * 12 + 3;
            for (String l : wrap(hint, (int) ((w - 2) / 0.75f), 2)) {
                small(l, 1, ly, 0.75f, v.powered ? DIM : AMBER, 1f);
                ly += 8;
            }
        } finally {
            GL11.glPopMatrix();
        }
    }

    /** One line of the state: its name dim on the left, its value in its colour on the right. */
    private static void line(int i, String key, String value, int rgb, float w) {
        double y = i * 12;
        if (i % 2 == 0) {
            begin();
            rect(-2, y - 1, w + 1, y + 10, SEAM, 0.25f);
            end();
        }
        String label = EchoText.t("city.gui.status." + key);
        small(fit(label, 60), 1, y + 1.5, 0.75f, DIM, 1f);
        float lw = Math.min(60, font().getStringWidth(label)) * 0.75f + 6;
        right(fit(value, (int) (w - lw - 2)), w - 1, y, rgb, 1f);
    }

    /**
     * The state's hint (0.14.2): while it is unformed and the server named the cell that keeps it from forming, where
     * that cell is, what is there and what should be; else what {@link #hintKey} says.
     */
    private static String hintText(CityGui.View v) {
        WrongCell c = v.formed ? null : WrongCell.decode(v.wrong);
        if (c == null) return EchoText.t("city.gui.status.hint." + hintKey(v));
        return EchoText.t(
            "city.gui.status.hint.unformed_at",
            c.pos(),
            StatCollector.translateToLocal(c.found),
            StatCollector.translateToLocal(c.wanted));
    }

    /** What the state's hint says: the first thing that stops the module, or that it runs. */
    private static String hintKey(CityGui.View v) {
        if (!v.formed) return "unformed";
        if (!"docked".equals(v.dock)) return "undocked";
        return v.powered ? "running" : "no_power";
    }

    /** The state's tooltip: the dock state at length, and what the module draws while it runs. */
    public static List<String> statusTip(TileCityModule c, CityGui.View v) {
        List<String> out = new ArrayList<>();
        out.add(EchoText.t("module." + c.moduleKey()));
        out.add(EnumChatFormatting.GRAY + dockText(v));
        out.addAll(EchoText.lines("city.gui.status.tip", v.upkeep));
        return out;
    }

    /**
     * The emblem: rings in the module's colour turning round a seed of light, sparks rising along them while it runs,
     * slow and dim while it is idle, still and dark until it forms; the samples it keeps as a ring of dots under it.
     */
    public static void emblem(TileCityModule c, CityGui.View v, float x, float y, float w, float h) {
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        try {
            int accent = c.moduleColor();
            float t = ticks();
            boolean on = v.powered && v.formed, docked = "docked".equals(v.dock);
            float life = !v.formed ? 0.15f : on ? 1f : docked ? 0.55f : 0.35f;
            double speed = on ? 1 : 0.35;
            double cx = w / 2.0, cy = 44;
            begin();
            for (int r = 0; r < 3; r++) {
                double rr = 14 + r * 9, turn = t * 0.02 * speed * (r % 2 == 0 ? 1 : -1);
                int dots = 10 + r * 6;
                for (int i = 0; i < dots; i++) {
                    double ang = turn + i * 2 * Math.PI / dots;
                    float a = life * (0.25f + 0.55f * (float) (0.5 + 0.5 * Math.sin(ang * 3 + t * 0.05)));
                    Motifs.dot(cx + Math.cos(ang) * rr, cy + Math.sin(ang) * rr * 0.9, 1.6 + r * 0.3, accent, a);
                }
            }
            Motifs.hex(cx, cy, 8, Motifs.mix(DEEP, accent, 0.35), life);
            Motifs.hex(cx, cy, 5, accent, life * (0.6f + 0.4f * (float) Math.sin(t * 0.08)));
            Motifs.dot(cx, cy, 3, WHITE, life);
            if (on) for (int i = 0; i < 8; i++) {
                double f = (t * 0.015 + i / 8.0) % 1, ang = i * 2.4 + t * 0.01;
                Motifs.dot(
                    cx + Math.cos(ang) * (8 + f * 26),
                    cy + Math.sin(ang) * (8 + f * 26) * 0.9 - f * 6,
                    1.4,
                    WHITE,
                    (float) Math.sin(f * Math.PI) * 0.8f);
            }
            int slots = c.samples()
                .getSlots(), n = Math.min(slots, 48);
            for (int i = 0; i < n; i++) {
                double ang = -Math.PI / 2 + i * 2 * Math.PI / n;
                boolean kept = c.sample(i) != null;
                Motifs.dot(
                    cx + Math.cos(ang) * 33,
                    cy + 50 + Math.sin(ang) * 12,
                    kept ? 2 : 1.4,
                    kept ? accent : SEAM,
                    kept ? 0.9f : 0.8f);
            }
            end();
            smallCentered(
                EchoText.t("city.gui.fill", c.sampleCount(), slots),
                cx,
                cy + 47,
                0.75f,
                c.sampleCount() > 0 ? WHITE : DIM,
                1f);
        } finally {
            GL11.glPopMatrix();
        }
    }

    // ---- 单元: the list

    /** The list's title: how many units, and which page. */
    public static void listHead(TileCityModule c, CityGui.View v, float x, float y, float w, float h) {
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        try {
            text(fit(EchoText.t("city.gui.units", c.unitCount()), (int) w - 22), 1, 1, c.moduleColor(), 1f);
            int pages = UnitBrowse.pages(c.unitCount(), CityGui.ROWS);
            if (pages > 1) smallRight(EchoText.t("city.gui.page", v.listPage + 1, pages), w, 2.5, 0.75f, DIM, 1f);
        } finally {
            GL11.glPopMatrix();
        }
    }

    /**
     * One line of the list: the unit's label and kind, a pip per slot (lit for a sample) and its fill; the unit shown
     * marked with a bar in the module's colour.
     */
    public static void row(TileCityModule c, CityGui.View v, int line, float x, float y, float w, float h) {
        int u = UnitBrowse.unitAt(v.listPage, line, c.unitCount(), CityGui.ROWS);
        if (u < 0) return;
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        try {
            int accent = c.moduleColor(), n = c.unitFill(u), slots = c.unitSlots(u)[1];
            boolean shown = u == v.unit;
            begin();
            if (line % 2 == 0) rect(0, 0, w, h, SEAM, 0.35f);
            if (shown) {
                rect(0, 0, w, h, accent, 0.12f);
                rect(0, 0, 2, h, accent, 0.9f);
            }
            int pips = Math.min(slots, 9);
            double px0 = w - 3 - pips * 4;
            for (int i = 0; i < pips; i++) {
                boolean kept = c.sample(c.unitSlots(u)[0] + i) != null;
                rect(px0 + i * 4, 4, px0 + i * 4 + 3, 7, kept ? fillColour(n, slots, accent) : EMPTY, 1f);
            }
            end();
            String label = c.unitName(u);
            text(fit(label, (int) (px0 - 8)), 4, 2, shown ? WHITE : CYAN, 1f);
            int lw = font().getStringWidth(label) + 8;
            int room = (int) ((px0 - 4 - lw) / 0.75f);
            if (room > 8) small(fit(EchoText.t("unit." + c.unitKind(u)), room), lw, 3.5, 0.75f, DIM, 1f);
        } finally {
            GL11.glPopMatrix();
        }
    }

    /** A line's tooltip: the unit, its samples, and what a click does. */
    public static List<String> rowTip(TileCityModule c, CityGui.View v, int line) {
        int u = UnitBrowse.unitAt(v.listPage, line, c.unitCount(), CityGui.ROWS);
        if (u < 0) return Collections.emptyList();
        List<String> out = unitTip(c, u);
        out.add(EchoText.t("city.gui.map.click"));
        return out;
    }

    // ---- 单元: the unit shown

    /** The unit shown: its kind and label, its fill, and the storey it stands on. */
    public static void unitHead(TileCityModule c, CityGui.View v, float x, float y, float w, float h) {
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        try {
            int u = v.unit;
            if (!UnitBrowse.valid(u, c.unitCount())) {
                text("…", 0, 1, DIM, 1f);
                return;
            }
            int n = c.unitFill(u), slots = c.unitSlots(u)[1];
            String fill = EchoText.t("city.gui.fill", n, slots);
            right(fill, w, 1, n >= slots ? c.moduleColor() : n > 0 ? CYAN : DIM, 1f);
            String title = EchoText.t("unit." + c.unitKind(u)) + " " + c.unitName(u);
            text(fit(title, (int) w - font().getStringWidth(fill) - 4), 0, 1, WHITE, 1f);
            small(fit(where(v, u), (int) (w / 0.75f)), 0, 11.5, 0.75f, DIM, 1f);
        } finally {
            GL11.glPopMatrix();
        }
    }

    /** Where a unit stands, in words: its storey and its height. */
    static String where(CityGui.View v, int u) {
        int s = v.map.storeyOf(u);
        return s < 0 ? "" : EchoText.t("city.gui.where", s + 1, v.map.level(s));
    }

    /** A unit's tooltip: its kind, label and fill, then its samples slot by slot. */
    public static List<String> unitTip(TileCityModule c, int u) {
        if (!UnitBrowse.valid(u, c.unitCount())) return new ArrayList<>();
        List<String> out = new ArrayList<>();
        int[] s = c.unitSlots(u);
        out.add(
            EchoText.t("city.gui.unit.tip", EchoText.t("unit." + c.unitKind(u)), c.unitName(u), c.unitFill(u), s[1]));
        for (int i = 0; i < s[1]; i++) {
            ItemStack st = c.sample(s[0] + i);
            out.add(
                EnumChatFormatting.GRAY + EchoText
                    .t("city.gui.unit.slot", i + 1, st == null ? EchoText.t("city.gui.unit.empty") : name(st)));
        }
        return out;
    }

    /** Under the grid: how to fill it, or that it is full. */
    public static void hint(TileCityModule c, CityGui.View v, float x, float y, float w, float h) {
        int u = v.unit;
        if (!UnitBrowse.valid(u, c.unitCount())) return;
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        try {
            boolean full = c.firstFree(u) < 0;
            smallCentered(
                fit(EchoText.t(full ? "city.gui.hint_full" : "city.gui.hint"), (int) (w / 0.75f)),
                w / 2.0,
                2,
                0.75f,
                full ? c.moduleColor() : DIM,
                1f);
        } finally {
            GL11.glPopMatrix();
        }
    }

    /** Between the arrows: which unit of how many. */
    public static void navLabel(TileCityModule c, CityGui.View v, float x, float y, float w, float h) {
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        try {
            begin();
            gradient(0, 0, w, h, DEEP, 1f, PANE, 1f);
            frame(0, 0, w, h, SEAM, 1f);
            end();
            boolean ok = UnitBrowse.valid(v.unit, c.unitCount());
            String label = ok ? EchoText.t("city.gui.nav", v.unit + 1, c.unitCount()) : "…";
            smallCentered(fit(label, (int) ((w - 2) / 0.75f)), w / 2.0, (h - 6) / 2.0, 0.75f, ok ? WHITE : DIM, 1f);
        } finally {
            GL11.glPopMatrix();
        }
    }

    // ---- 平面

    /**
     * A storey's floor plan: what stands on it (walls, glazing lit, the doorways faint, rails, posts, stairs, the
     * furniture, water and grass), every unit of the storey as its cells coloured by its fill, the unit shown outlined
     * white, the one under the mouse cyan, the player where they stand and look; under it, nothing (the storey tabs
     * name it).
     */
    public static void map(TileCityModule c, CityGui.View v, int hover, float x, float y, float w, float h) {
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        try {
            map0(c, v, hover);
        } finally {
            GL11.glPopMatrix();
        }
    }

    private static void map0(TileCityModule c, CityGui.View v, int hover) {
        ModuleMap m = v.map;
        int storey = Math.max(0, Math.min(m.storeys() - 1, v.storey)), accent = c.moduleColor();
        float t = ticks();
        begin();
        rect(0, 0, m.w, m.h, 0x03070C, 0.95f);
        Tessellator q = start(GL11.GL_QUADS);
        for (int x = 0; x < m.width; x++) for (int z = 0; z < m.depth; z++) {
            ModuleMap.Mark k = m.mark(storey, x, z);
            if (k == ModuleMap.Mark.NONE) continue;
            int[] r = m.tile(x, z);
            markQuad(q, k, r, t, x, z);
        }
        for (int u : m.units(storey)) {
            int n = c.unitFill(u), slots = c.unitSlots(u)[1];
            int col = fillColour(n, slots, accent);
            for (int[] r : m.tiles(u)) put(q, r[0], r[1], r[2], r[3], col, n == 0 ? 0.85f : 1f);
        }
        q.draw();
        int sel = v.unit;
        if (hover >= 0 && hover != sel) {
            int[] r = m.bounds(hover);
            if (r != null) frame(r[0] - 1, r[1] - 1, r[2] + 1, r[3] + 1, CYAN, 0.8f);
        }
        if (UnitBrowse.valid(sel, c.unitCount()) && m.storeyOf(sel) == storey) {
            int[] r = m.bounds(sel);
            float a = 0.65f + 0.35f * (float) Math.sin(t * 0.25);
            if (r != null) {
                rect(r[0] - 2, r[1] - 2, r[2] + 2, r[3] + 2, WHITE, 0.12f * a);
                frame(r[0] - 1, r[1] - 1, r[2] + 1, r[3] + 1, WHITE, a);
            }
        }
        you(c, m, storey, t);
        frame(0, 0, m.w, m.h, SEAM, 1f);
        end();
    }

    /** One block of a plan in its mark's colour (water and lit strips shimmer, a doorway is faint). */
    private static void markQuad(Tessellator q, ModuleMap.Mark k, int[] r, float t, int x, int z) {
        switch (k) {
            case FLOOR:
                put(q, r[0], r[1], r[2], r[3], GROUND, 0.9f);
                break;
            case LIT:
                put(q, r[0], r[1], r[2], r[3], LIT_FLOOR, 0.9f);
                break;
            case GRASS:
                put(q, r[0], r[1], r[2], r[3], GRASS, 0.9f);
                break;
            case WATER:
                put(q, r[0], r[1], r[2], r[3], WATER, 0.75f + 0.2f * (float) Math.sin(t * 0.08 + x * 0.7 + z * 0.5));
                break;
            case WALL:
                put(q, r[0], r[1], r[2], r[3], WALL, 1f);
                break;
            case SOLID:
                put(q, r[0], r[1], r[2], r[3], MASS, 1f);
                break;
            case GLAZE:
                put(q, r[0], r[1], r[2], r[3], CYAN, 0.6f);
                break;
            case PILASTER:
                put(q, r[0], r[1], r[2], r[3], Motifs.mix(WALL, CYAN, 0.5), 1f);
                break;
            case DOOR:
                put(q, r[0], r[1], r[2], r[3], CYAN, 0.25f);
                break;
            case RAIL:
                put(q, r[0], r[1], r[2], r[3], GROUND, 0.9f);
                put(q, r[0] + 0.5, r[1] + 0.5, r[2] - 0.5, r[3] - 0.5, RAIL, 1f);
                break;
            case POST:
                put(q, r[0] + 0.5, r[1] + 0.5, r[2] - 0.5, r[3] - 0.5, POST, 1f);
                break;
            case STAIR:
                put(q, r[0], r[1], r[2], r[3], STAIR, 0.9f);
                break;
            case LIFT:
                // the light lift's pad: its iris lit, breathing
                put(q, r[0], r[1], r[2], r[3], GROUND, 0.9f);
                put(q, r[0] + 0.5, r[1] + 0.5, r[2] - 0.5, r[3] - 0.5, CYAN, 0.7f + 0.3f * (float) Math.sin(t * 0.12));
                break;
            case FIXTURE:
                put(q, r[0], r[1], r[2], r[3], GROUND, 0.9f);
                put(q, r[0] + 0.5, r[1] + 0.5, r[2] - 0.5, r[3] - 0.5, FIXTURE, 0.8f);
                break;
            case CONSOLE:
                put(q, r[0], r[1], r[2], r[3], 0xFFD27A, 0.85f);
                break;
            case CORE:
                put(q, r[0], r[1], r[2], r[3], WHITE, 0.9f);
                break;
            case TRUNK:
                put(q, r[0], r[1], r[2], r[3], Motifs.mix(CYAN, WHITE, 0.4), 0.9f);
                break;
            default:
                break;
        }
    }

    /**
     * The colour of a unit for its fill: dim when empty, cyan growing brighter, the module's colour when every slot
     * holds a sample.
     */
    static int fillColour(int n, int slots, int accent) {
        if (n <= 0) return EMPTY;
        if (n >= slots) return accent;
        return Motifs.mix(PART, CYAN, slots <= 1 ? 1 : (n - 1) / (double) Math.max(1, slots - 2));
    }

    /** The player on the plan of the storey they stand on (inside the module's box), and where they look. */
    private static void you(TileCityModule c, ModuleMap m, int storey, float t) {
        EntityPlayer p = Minecraft.getMinecraft().thePlayer;
        if (p == null || c.getWorldObj() == null || p.worldObj != c.getWorldObj()) return;
        double feet = p.boundingBox.minY;
        double[] a = c.localPoint(p.posX, feet, p.posZ);
        if (a[0] < 0 || a[0] > m.width
            || a[2] < 0
            || a[2] > m.depth
            || a[1] < -1
            || a[1] > c.build()
                .height())
            return;
        if (m.storeyAt(a[1]) != storey) return;
        double mx = Math.max(0, Math.min(m.w, m.px(a[0]))), my = Math.max(0, Math.min(m.h, m.py(a[2])));
        Vec3 look = p.getLook(1f);
        double[] b = c.localPoint(p.posX + look.xCoord, feet, p.posZ + look.zCoord);
        double dx = b[0] - a[0], dz = b[2] - a[2], len = Math.sqrt(dx * dx + dz * dz);
        if (len > 1e-3) Motifs.line(mx, my, mx + dx / len * 5, my - dz / len * 5, 1, WHITE, 0.7f);
        Motifs.dot(mx, my, 2.5, WHITE, 0.6f + 0.4f * (float) Math.sin(t * 0.3));
    }

    /** The tooltip over the plan: the unit under the mouse, its samples, and what a click does. */
    public static List<String> mapTip(TileCityModule c, int unit) {
        if (!UnitBrowse.valid(unit, c.unitCount())) return Collections.emptyList();
        List<String> out = unitTip(c, unit);
        out.add(EchoText.t("city.gui.map.click"));
        return out;
    }

    /**
     * A storey's tab: its number, lit while its plan is shown; a level shown for the building alone (0.13.0,
     * {@link ModuleMap#overview}) by its height.
     */
    public static void storeyTab(TileCityModule c, CityGui.View v, int storey, float x, float y, float w, float h) {
        String label = v.map.overview(storey) ? EchoText.t("city.gui.level_short", v.map.level(storey))
            : EchoText.t("city.gui.storey_short", storey + 1);
        NexusScreen.tab(label, x, y, w, h, v.storey == storey);
    }

    /**
     * A storey tab's tooltip: the storey, its height and its units; a level shown for the building alone, its height.
     */
    public static List<String> storeyTip(TileCityModule c, CityGui.View v, int storey) {
        List<String> out = new ArrayList<>();
        if (v.map.overview(storey)) {
            out.add(EchoText.t("city.gui.level.tip", v.map.level(storey)));
            return out;
        }
        int kept = 0, slots = 0;
        List<Integer> units = v.map.units(storey);
        for (int u : units) {
            kept += c.unitFill(u);
            slots += c.unitSlots(u)[1];
        }
        out.add(EchoText.t("city.gui.storey.tip", storey + 1, v.map.level(storey), units.size()));
        out.add(EnumChatFormatting.GRAY + EchoText.t("city.gui.fill", kept, slots));
        return out;
    }

    /** Beside the plan: the unit shown (its kind, label, fill and storey) and its samples as pictures. */
    public static void planInfo(TileCityModule c, CityGui.View v, float x, float y, float w, float h) {
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        try {
            int u = v.unit;
            if (!UnitBrowse.valid(u, c.unitCount())) {
                small(fit(EchoText.t("city.gui.plan.pick"), (int) (w / 0.75f)), 0, 2, 0.75f, DIM, 1f);
                return;
            }
            int[] s = c.unitSlots(u);
            int n = c.unitFill(u);
            text(fit(c.unitName(u), (int) w), 0, 0, WHITE, 1f);
            small(fit(EchoText.t("unit." + c.unitKind(u)), (int) (w / 0.75f)), 0, 10, 0.75f, CYAN, 1f);
            small(fit(where(v, u), (int) (w / 0.75f)), 0, 18, 0.75f, DIM, 1f);
            smallRight(EchoText.t("city.gui.fill", n, s[1]), w, 1.5, 0.75f, n >= s[1] ? c.moduleColor() : DIM, 1f);
            int shown = Math.min(s[1], 6);
            begin();
            for (int i = 0; i < shown; i++) {
                double ix = i * 18;
                rect(ix, 28, ix + 16, 44, DEEP, 1f);
                frame(ix, 28, ix + 16, 44, c.sample(s[0] + i) != null ? c.moduleColor() : SEAM, 0.8f);
            }
            end();
            for (int i = 0; i < shown; i++) {
                ItemStack st = c.sample(s[0] + i);
                if (st != null) icon(st, i * 18 + 1, 29, 0.875f);
            }
        } finally {
            GL11.glPopMatrix();
        }
    }

    // ---- the strip

    /** Below every page: the samples kept in all units, and a hint for the page shown. */
    public static void foot(TileCityModule c, CityGui.View v, float x, float y, float w, float h) {
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        try {
            int lw = (int) ((w - 2) / 0.75f);
            String samples = EchoText.t(
                "city.gui.foot.samples",
                c.sampleCount(),
                c.samples()
                    .getSlots(),
                c.unitCount());
            small(fit(samples, lw), 0, 1, 0.75f, WHITE, 1f);
            small(fit(dockText(v), lw), 0, 9, 0.75f, dockColour(v), 1f);
            String hint = EchoText.t(
                v.page == CityGui.UNITS ? "city.gui.foot.units"
                    : v.page == CityGui.PLAN ? "city.gui.foot.plan" : "city.gui.foot.status");
            small(fit(hint, lw), 0, 17, 0.75f, DIM, 1f);
        } finally {
            GL11.glPopMatrix();
        }
    }

    // ---- helpers

    /** One more quad of a batch begun with {@code start(GL_QUADS)}. */
    private static void put(Tessellator q, double x0, double y0, double x1, double y1, int rgb, float a) {
        q.setColorRGBA_I(rgb, (int) (Math.max(0, Math.min(1, a)) * 255));
        q.addVertex(x1, y0, 0);
        q.addVertex(x0, y0, 0);
        q.addVertex(x0, y1, 0);
        q.addVertex(x1, y1, 0);
    }

    /** An item's name; a broken item names itself by its id. */
    static String name(ItemStack s) {
        try {
            return s.getDisplayName();
        } catch (RuntimeException e) {
            return String.valueOf(s.getItem());
        }
    }

    /** The text broken into lines of at most {@code width} font pixels, at most {@code max} lines. */
    private static List<String> wrap(String s, int width, int max) {
        @SuppressWarnings("unchecked")
        List<String> lines = font().listFormattedStringToWidth(s, width);
        if (lines.size() <= max) return lines;
        List<String> out = new ArrayList<>(lines.subList(0, max));
        out.set(max - 1, fit(out.get(max - 1) + "…", width));
        return out;
    }

    /** An item at a scale, its corner at (x, y). */
    private static void icon(ItemStack s, double x, double y, float scale) {
        Minecraft mc = Minecraft.getMinecraft();
        GL11.glPushMatrix();
        GL11.glTranslated(x, y, 0);
        GL11.glScalef(scale, scale, 1);
        RenderHelper.enableGUIStandardItemLighting();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        try {
            ITEMS.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), s, 0, 0);
        } finally {
            RenderHelper.disableStandardItemLighting();
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glPopMatrix();
        }
    }
}
