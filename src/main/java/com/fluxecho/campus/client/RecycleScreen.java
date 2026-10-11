package com.fluxecho.campus.client;

import static com.fluxecho.client.FluxDraw.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.fluxecho.campus.RecycleGui;
import com.fluxecho.campus.RecyclePage;
import com.fluxecho.core.EchoText;
import com.fluxecho.logic.Compact;

/**
 * The drawn parts of the recycle bin's screen ({@link RecycleGui}, 0.10.1): the window with the grid's panel, the
 * header (its name and which nexus it takes from), each cell (the kind's icon and count, lit under the mouse), the
 * scroll bar and the count line. Drawn in the nexus GUI's flux look, with the violet of the bin's intake. Client only.
 */
public final class RecycleScreen {

    private static final RenderItem ITEMS = new RenderItem();

    private RecycleScreen() {}

    private static float ticks() {
        return Minecraft.getSystemTime() / 50f;
    }

    /** The window: the pane, the grid's panel and the seam above the inventory. */
    public static void background(float x, float y, float w, float h) {
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        try {
            begin();
            pane(0, 0, w, h, 1f);
            int x0 = RecycleGui.GRID_X - 4, y0 = RecycleGui.GRID_Y - 3;
            int x1 = RecycleGui.SCROLL_X + RecycleGui.SCROLL_W + 3,
                y1 = RecycleGui.GRID_Y + RecycleGui.ROWS * RecycleGui.CELL + 3;
            gradient(x0, y0, x1, y1, 0x0C1A28, 0.9f, DEEP, 0.9f);
            frame(x0, y0, x1, y1, SEAM, 1f);
            corners(x0, y0, x1, y1, 4, VIOLET, 0.7f);
            rect(8, RecycleGui.INV_Y - 4, w - 8, RecycleGui.INV_Y - 3, SEAM, 1f);
            end();
        } finally {
            GL11.glPopMatrix();
        }
    }

    /** Its name on the left, the nexus it takes from (or why it takes from none) on the right. */
    public static void header(RecycleGui.View v, float x, float y, float w, float h) {
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        try {
            begin();
            hgradient(1, 1, w * 0.6, h - 1, VIOLET, 0.28f, CYAN, 0f);
            rect(1, h - 1, w - 1, h, VIOLET, 0.6f);
            end();
            String name = EchoText.t("recycle_bin.gui.title");
            text(name, 4, 3, WHITE, 1f);
            if (!v.received) return;
            String right;
            int c;
            if (v.link == RecycleGui.LINKED) {
                right = EchoText.t("recycle_bin.gui.nexus", v.nexus);
                c = CYAN;
            } else if (v.link == RecycleGui.UNLOADED) {
                right = EchoText.t("recycle_bin.gui.unloaded");
                c = AMBER;
            } else {
                right = EchoText.t("recycle_bin.gui.unlinked");
                c = RED;
            }
            float room = (w - 12 - font().getStringWidth(name)) / 0.75f;
            smallRight(fit(right, (int) room), w - 4, 4, 0.75f, c, 1f);
        } finally {
            GL11.glPopMatrix();
        }
    }

    /** One cell: a slot's frame, the kind's icon and its count; lit under the mouse, faint while its row is coming. */
    public static void cell(RecycleGui.View v, int cell, boolean hover, float x, float y, float w, float h) {
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        try {
            RecyclePage.Entry e = v.entry(cell);
            boolean pending = e == null && v.index(cell) < v.page.kinds;
            begin();
            gradient(0.5, 0.5, w - 0.5, h - 0.5, DEEP, 1f, PANE, 1f);
            frame(0.5, 0.5, w - 0.5, h - 0.5, hover && e != null ? VIOLET : SEAM, 1f);
            if (pending) scan(1, 1, w - 1, h - 1, ticks() + cell * 3, VIOLET, 0.12f);
            if (hover && e != null) rect(1, 1, w - 1, h - 1, VIOLET, 0.18f);
            end();
            if (e == null) return;
            ItemStack s = v.stack(cell);
            if (s != null) icon(s, 1, 1);
            else smallCentered("?", w / 2.0, 5, 1f, RED, 1f);
            // the count over the icon, as a slot's stack size is drawn
            GL11.glPushMatrix();
            GL11.glTranslatef(0, 0, 200);
            smallRight(count(e.count), w - 1, h - 6.5, 0.6f, WHITE, 1f);
            GL11.glPopMatrix();
        } finally {
            GL11.glPopMatrix();
        }
    }

    /** A count in at most four characters: 64, 1.2k, 35M. */
    static String count(long n) {
        return n < 1000 ? String.valueOf(n) : Compact.si(n);
    }

    /** The tooltip of a cell: the item's own lines, how many the store holds, and how to take it. */
    public static List<String> cellTip(RecycleGui.View v, int cell) {
        RecyclePage.Entry e = v.entry(cell);
        if (e == null) return Collections.emptyList();
        ItemStack s = v.stack(cell);
        List<String> out = new ArrayList<>();
        if (s == null) out.add(EchoText.t("recycle_bin.gui.unknown"));
        else {
            Minecraft mc = Minecraft.getMinecraft();
            try {
                @SuppressWarnings("unchecked")
                List<String> lines = s.getTooltip(mc.thePlayer, mc.gameSettings.advancedItemTooltips);
                out.addAll(lines);
            } catch (RuntimeException x) {
                out.add(s.getDisplayName());
            }
        }
        out.add(EchoText.t("recycle_bin.gui.count", e.count));
        if (e.cut) out.add(EchoText.t("recycle_bin.gui.cut"));
        out.addAll(EchoText.lines("recycle_bin.gui.cell.tip"));
        return out;
    }

    /** The scroll bar: its track and a thumb as long as the share of the rows shown. */
    public static void scrollbar(RecycleGui.View v, float x, float y, float w, float h) {
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        try {
            int rows = v.rows();
            begin();
            rect(w / 2 - 1, 0, w / 2 + 1, h, SEAM, 1f);
            if (rows > RecycleGui.ROWS) {
                double len = Math.max(8, h * RecycleGui.ROWS / (double) rows);
                double top = (h - len) * v.row / (double) (rows - RecycleGui.ROWS);
                rect(0, top, w, top + len, VIOLET, 0.85f);
                rect(1, top + 1, w - 1, top + len - 1, 0x2A1E5A, 0.9f);
            } else rect(0, 0, w, h, VIOLET, 0.25f);
            end();
        } finally {
            GL11.glPopMatrix();
        }
    }

    /** Under the grid: {@code N 种 / M 个}, and that the store is full when it is. */
    public static void foot(RecycleGui.View v, float x, float y, float w, float h) {
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        try {
            if (!v.received) return;
            String line = EchoText.t("recycle_bin.gui.counts", v.page.kinds, Compact.si(v.page.items));
            if (v.full) line += " · " + EchoText.t("recycle_bin.gui.full");
            text(fit(line, (int) w), 0, 0, v.full ? AMBER : v.page.kinds > 0 ? WHITE : DIM, 1f);
        } finally {
            GL11.glPopMatrix();
        }
    }

    /** An item in the GUI, its corner at (x, y). */
    static void icon(ItemStack s, double x, double y) {
        Minecraft mc = Minecraft.getMinecraft();
        GL11.glPushMatrix();
        GL11.glTranslated(x, y, 0);
        RenderHelper.enableGUIStandardItemLighting();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        try {
            ITEMS.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), s, 0, 0);
        } finally {
            RenderHelper.disableStandardItemLighting();
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL12.GL_RESCALE_NORMAL);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glPopMatrix();
        }
    }
}
