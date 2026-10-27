package com.fluxecho.codex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.fluxecho.client.FluxDraw;
import com.fluxecho.core.EchoText;
import com.fluxecho.logic.codex.CodexPages;

/**
 * The Echo Codex screen, with three tabs in its header. Records: the categories on the left with how many of each
 * your team has done once, the things themselves on the right as icons (hover for the name); a category with nothing
 * yet says how to start it. Volumes (0.17.0): the volumes of the story on the left, the open one's text on the right;
 * a volume not open yet says what opens it ({@link CodexPages}). Cabin logs (0.17.0): every building 0.16.0 has on
 * the left, the log of one that has formed for your team on the right. The narrative pages move to the new Codex
 * screen in 0.18.0; until then they live here, in this screen's look.
 */
public class GuiCodex extends GuiScreen {

    private static final int W = 300, H = 210, LIST_W = 104, ROW = 18, HALL_ROW = 10, CELL = 18, LINE = 10;
    private static final int BG = 0xE0101418, PANEL = 0xC0202830, EDGE = 0xFF3FB8AF, SELECTED = 0x803FB8AF,
        HOVER = 0x40FFFFFF, SLOT = 0x60000000, TEXT = 0xD8D8D8, LOCKED = 0x707070, HEADING = 0xFFC56B;
    private static final int RECORDS = 0, VOLUMES = 1, HALLS = 2;
    private static final String[] TABS = { "codex.tab.records", "codex.tab.volumes", "codex.tab.halls" };

    private static final RenderItem ITEMS = new RenderItem();
    private static int selected, tab, volume, hall;

    private final List<Categories.Category> cats = new ArrayList<>();
    private int scroll;
    /** The page shown on the right of a narrative tab, wrapped to the panel; rebuilt when it changes. */
    private List<String> page = Collections.emptyList();
    private String pageOf = "";

    @Override
    public void initGui() {
        cats.clear();
        cats.addAll(Categories.all());
        if (selected >= cats.size()) selected = 0;
        pageOf = "";
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private int left() {
        return (width - W) / 2;
    }

    private int top() {
        return (height - H) / 2;
    }

    private int columns() {
        return (W - LIST_W - 16) / CELL;
    }

    private int visibleRows() {
        return (H - 34) / CELL;
    }

    private int textWidth() {
        return W - LIST_W - 20;
    }

    private int textRows() {
        return (H - 34) / LINE;
    }

    private List<String> keys() {
        if (cats.isEmpty()) return Collections.emptyList();
        return new ArrayList<>(ClientLedger.keys(cats.get(selected).id));
    }

    /** The tabs' boxes {x0, x1}, right-aligned in the header. */
    private int[][] tabBoxes() {
        int[][] out = new int[TABS.length][];
        int x = left() + W - 6;
        for (int i = TABS.length - 1; i >= 0; i--) {
            int w = fontRendererObj.getStringWidth(EchoText.t(TABS[i])) + 10;
            out[i] = new int[] { x - w, x };
            x -= w + 2;
        }
        return out;
    }

    @Override
    public void drawScreen(int mx, int my, float partial) {
        drawDefaultBackground();
        int x0 = left(), y0 = top();
        drawRect(x0 - 1, y0 - 1, x0 + W + 1, y0 + H + 1, EDGE);
        drawRect(x0, y0, x0 + W, y0 + H, BG);
        fontRendererObj.drawString(EchoText.t("codex.title"), x0 + 8, y0 + 8, 0x7FFFF0);
        int[][] boxes = tabBoxes();
        for (int i = 0; i < TABS.length; i++) {
            boolean over = mx >= boxes[i][0] && mx < boxes[i][1] && my >= y0 + 4 && my < y0 + 18;
            if (i == tab) drawRect(boxes[i][0], y0 + 4, boxes[i][1], y0 + 18, SELECTED);
            else if (over) drawRect(boxes[i][0], y0 + 4, boxes[i][1], y0 + 18, HOVER);
            fontRendererObj.drawString(EchoText.t(TABS[i]), boxes[i][0] + 5, y0 + 7, i == tab ? 0xFFFFFF : 0xA0A0A0);
        }
        drawRect(x0 + 4, y0 + 22, x0 + LIST_W, y0 + H - 4, PANEL);
        drawRect(x0 + LIST_W + 6, y0 + 22, x0 + W - 4, y0 + H - 4, PANEL);
        String hover = null;
        if (tab == VOLUMES) drawVolumes(mx, my);
        else if (tab == HALLS) drawHalls(mx, my);
        else hover = drawRecords(mx, my);
        super.drawScreen(mx, my, partial);
        if (hover != null) drawHoveringText(Collections.singletonList(hover), mx, my, fontRendererObj);
    }

    /** The records tab; returns the name under the mouse, if any. */
    private String drawRecords(int mx, int my) {
        int x0 = left(), y0 = top();
        int total = 0;
        for (Categories.Category c : cats) total += ClientLedger.keys(c.id)
            .size();
        // categories
        int ly = y0 + 24;
        for (int i = 0; i < cats.size(); i++) {
            Categories.Category c = cats.get(i);
            int ry = ly + i * ROW;
            boolean over = mx >= x0 + 4 && mx < x0 + LIST_W && my >= ry && my < ry + ROW;
            if (i == selected) drawRect(x0 + 4, ry, x0 + LIST_W, ry + ROW, SELECTED);
            else if (over) drawRect(x0 + 4, ry, x0 + LIST_W, ry + ROW, HOVER);
            String name = FluxDraw.fit(EchoText.t("codex.cat." + c.id), LIST_W - 34);
            fontRendererObj.drawString(name, x0 + 8, ry + 5, 0xE0E0E0);
            String n = String.valueOf(
                ClientLedger.keys(c.id)
                    .size());
            fontRendererObj.drawString(n, x0 + LIST_W - 4 - fontRendererObj.getStringWidth(n), ry + 5, 0x7FFFF0);
        }
        String sum = FluxDraw.fit(EchoText.t("codex.total", total), LIST_W - 12);
        fontRendererObj.drawString(sum, x0 + 8, y0 + H - 14, 0xA0A0A0);

        // things
        int gx = x0 + LIST_W + 8, gy = y0 + 24, cols = columns(), rows = visibleRows();
        List<String> keys = keys();
        int maxScroll = Math.max(0, (keys.size() + cols - 1) / cols - rows);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        String hover = null;
        if (keys.isEmpty() && !cats.isEmpty()) {
            List<String> lines = fontRendererObj
                .listFormattedStringToWidth(EchoText.t("codex.todo." + cats.get(selected).id), W - LIST_W - 24);
            for (int i = 0; i < lines.size(); i++)
                fontRendererObj.drawString(lines.get(i), gx + 2, gy + 4 + i * 10, 0x909090);
        }
        RenderHelper.enableGUIStandardItemLighting();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        for (int i = scroll * cols; i < keys.size() && i < (scroll + rows) * cols; i++) {
            int cx = gx + (i % cols) * CELL, cy = gy + (i / cols - scroll) * CELL;
            drawRect(cx, cy, cx + 16, cy + 16, SLOT);
            Categories.Category c = cats.get(selected);
            ItemStack icon = safeIcon(c, keys.get(i));
            if (icon != null) {
                try {
                    ITEMS.renderItemAndEffectIntoGUI(fontRendererObj, mc.getTextureManager(), icon, cx, cy);
                } catch (RuntimeException ignored) {
                    // an item that cannot draw itself stays a blank slot
                }
            }
            if (mx >= cx && mx < cx + 16 && my >= cy && my < cy + 16) hover = safeName(c, keys.get(i));
        }
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL11.GL_LIGHTING);
        if (maxScroll > 0) drawPageNumber(maxScroll);
        return hover;
    }

    private void drawVolumes(int mx, int my) {
        int x0 = left(), y0 = top();
        int records = ClientLedger.records();
        Set<String> research = ClientLedger.research();
        for (int v = 0; v < CodexPages.VOLUMES; v++) {
            int ry = y0 + 24 + v * ROW;
            boolean over = mx >= x0 + 4 && mx < x0 + LIST_W && my >= ry && my < ry + ROW;
            if (v == volume) drawRect(x0 + 4, ry, x0 + LIST_W, ry + ROW, SELECTED);
            else if (over) drawRect(x0 + 4, ry, x0 + LIST_W, ry + ROW, HOVER);
            boolean open = CodexPages.open(v, records, research);
            String title = FluxDraw.fit(raw(CodexPages.titleKey(v)), LIST_W - 12);
            fontRendererObj.drawString(title, x0 + 8, ry + 5, open ? 0xE0E0E0 : LOCKED);
        }
        boolean open = CodexPages.open(volume, records, research);
        String text = open ? raw(CodexPages.bodyKey(volume))
            : EnumChatFormatting.GRAY + raw(CodexPages.whenKey(volume));
        drawPage("v" + volume + open, raw(CodexPages.titleKey(volume)), text);
    }

    private void drawHalls(int mx, int my) {
        int x0 = left(), y0 = top();
        Set<String> halls = ClientLedger.halls();
        List<String> all = CodexPages.HALLS;
        if (hall >= all.size()) hall = 0;
        for (int i = 0; i < all.size(); i++) {
            int ry = y0 + 24 + i * HALL_ROW;
            boolean over = mx >= x0 + 4 && mx < x0 + LIST_W && my >= ry && my < ry + HALL_ROW;
            if (i == hall) drawRect(x0 + 4, ry, x0 + LIST_W, ry + HALL_ROW, SELECTED);
            else if (over) drawRect(x0 + 4, ry, x0 + LIST_W, ry + HALL_ROW, HOVER);
            String name = FluxDraw.fit(StatCollector.translateToLocal(CodexPages.nameKey(all.get(i))), LIST_W - 12);
            fontRendererObj.drawString(name, x0 + 8, ry + 1, halls.contains(all.get(i)) ? 0xE0E0E0 : LOCKED);
        }
        int logged = 0;
        for (String h : all) if (halls.contains(h)) logged++;
        String count = FluxDraw.fit(EchoText.t("codex.halls.count", logged, all.size()), LIST_W - 12);
        fontRendererObj.drawString(count, x0 + 8, y0 + H - 14, 0xA0A0A0);
        String key = all.get(hall);
        if (halls.contains(key)) {
            List<String> lines = CodexPages.paragraphs(raw(CodexPages.hallKey(key)));
            String heading = lines.isEmpty() ? "" : lines.remove(0);
            drawPage("h" + key + true, heading, String.join("\n", lines));
        } else drawPage(
            "h" + key + false,
            StatCollector.translateToLocal(CodexPages.nameKey(key)),
            EnumChatFormatting.GRAY + raw("codex.halls.locked"));
    }

    /**
     * Draws a narrative page on the right: its heading in gold, then the text wrapped to the panel, scrolled by the
     * mouse wheel a line at a time.
     */
    private void drawPage(String id, String heading, String text) {
        int x0 = left(), y0 = top(), w = textWidth();
        if (!id.equals(pageOf)) {
            List<String> lines = new ArrayList<>();
            lines.addAll(fontRendererObj.listFormattedStringToWidth(heading, w));
            lines.add("");
            for (String p : CodexPages.paragraphs(text)) {
                if (p.isEmpty()) lines.add("");
                else lines.addAll(fontRendererObj.listFormattedStringToWidth(p, w));
            }
            page = lines;
            pageOf = id;
            scroll = 0;
        }
        int rows = textRows(), headingLines = fontRendererObj.listFormattedStringToWidth(heading, w)
            .size();
        int maxScroll = Math.max(0, page.size() - rows);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        for (int i = 0; i < rows && scroll + i < page.size(); i++) {
            int line = scroll + i;
            fontRendererObj
                .drawString(page.get(line), x0 + LIST_W + 10, y0 + 26 + i * LINE, line < headingLines ? HEADING : TEXT);
        }
        if (maxScroll > 0) drawPageNumber(maxScroll);
    }

    /**
     * A narrative text as it stands, not formatted: the pages hold percent signs ("+20%") that a formatted
     * translation would take for placeholders.
     */
    private static String raw(String key) {
        return StatCollector.translateToLocal("fluxecho." + key);
    }

    private void drawPageNumber(int maxScroll) {
        String n = (scroll + 1) + "/" + (maxScroll + 1);
        fontRendererObj.drawString(n, left() + W - 8 - fontRendererObj.getStringWidth(n), top() + H - 14, 0x707070);
    }

    private static ItemStack safeIcon(Categories.Category c, String key) {
        try {
            return c.icon.apply(key);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String safeName(Categories.Category c, String key) {
        try {
            String n = c.name.apply(key);
            return n == null ? key : n;
        } catch (RuntimeException e) {
            return EnumChatFormatting.GRAY + key;
        }
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) {
        super.mouseClicked(mx, my, button);
        int x0 = left(), y0 = top(), ly = y0 + 24;
        int[][] boxes = tabBoxes();
        for (int i = 0; i < TABS.length; i++) {
            if (mx >= boxes[i][0] && mx < boxes[i][1] && my >= y0 + 4 && my < y0 + 18 && i != tab) {
                tab = i;
                scroll = 0;
                pageOf = "";
                return;
            }
        }
        if (mx < x0 + 4 || mx >= x0 + LIST_W || my < ly) return;
        if (tab == VOLUMES) {
            int v = (my - ly) / ROW;
            if (v < CodexPages.VOLUMES) volume = v;
        } else if (tab == HALLS) {
            int h = (my - ly) / HALL_ROW;
            if (h < CodexPages.HALLS.size()) hall = h;
        } else {
            int i = (my - ly) / ROW;
            if (i < cats.size()) {
                selected = i;
                scroll = 0;
            }
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) scroll += wheel > 0 ? -1 : 1;
    }
}
