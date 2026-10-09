package com.fluxecho.codex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.fluxecho.core.EchoText;

/**
 * The Echo Codex screen: the categories on the left with how many of each your team has done once, the things
 * themselves on the right as icons (hover for the name). A category with nothing yet says how to start it.
 */
public class GuiCodex extends GuiScreen {

    private static final int W = 300, H = 210, LIST_W = 104, ROW = 18, CELL = 18;
    private static final int BG = 0xE0101418, PANEL = 0xC0202830, EDGE = 0xFF3FB8AF, SELECTED = 0x803FB8AF,
        HOVER = 0x40FFFFFF, SLOT = 0x60000000;

    private static final RenderItem ITEMS = new RenderItem();
    private static int selected;

    private final List<Categories.Category> cats = new ArrayList<>();
    private int scroll;

    @Override
    public void initGui() {
        cats.clear();
        cats.addAll(Categories.all());
        if (selected >= cats.size()) selected = 0;
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

    private List<String> keys() {
        if (cats.isEmpty()) return Collections.emptyList();
        return new ArrayList<>(ClientLedger.keys(cats.get(selected).id));
    }

    @Override
    public void drawScreen(int mx, int my, float partial) {
        drawDefaultBackground();
        int x0 = left(), y0 = top();
        drawRect(x0 - 1, y0 - 1, x0 + W + 1, y0 + H + 1, EDGE);
        drawRect(x0, y0, x0 + W, y0 + H, BG);
        int total = 0;
        for (Categories.Category c : cats) total += ClientLedger.keys(c.id)
            .size();
        fontRendererObj.drawString(EchoText.t("codex.title"), x0 + 8, y0 + 8, 0x7FFFF0);
        String sum = EchoText.t("codex.total", total);
        fontRendererObj.drawString(sum, x0 + W - 8 - fontRendererObj.getStringWidth(sum), y0 + 8, 0xA0A0A0);

        // categories
        int ly = y0 + 24;
        drawRect(x0 + 4, ly - 2, x0 + LIST_W, y0 + H - 4, PANEL);
        for (int i = 0; i < cats.size(); i++) {
            Categories.Category c = cats.get(i);
            int ry = ly + i * ROW;
            boolean over = mx >= x0 + 4 && mx < x0 + LIST_W && my >= ry && my < ry + ROW;
            if (i == selected) drawRect(x0 + 4, ry, x0 + LIST_W, ry + ROW, SELECTED);
            else if (over) drawRect(x0 + 4, ry, x0 + LIST_W, ry + ROW, HOVER);
            String name = com.fluxecho.client.FluxDraw.fit(EchoText.t("codex.cat." + c.id), LIST_W - 34);
            fontRendererObj.drawString(name, x0 + 8, ry + 5, 0xE0E0E0);
            String n = String.valueOf(
                ClientLedger.keys(c.id)
                    .size());
            fontRendererObj.drawString(n, x0 + LIST_W - 4 - fontRendererObj.getStringWidth(n), ry + 5, 0x7FFFF0);
        }

        // things
        int gx = x0 + LIST_W + 8, gy = y0 + 24, cols = columns(), rows = visibleRows();
        drawRect(gx - 2, gy - 2, x0 + W - 4, y0 + H - 4, PANEL);
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
        if (maxScroll > 0) {
            String page = (scroll + 1) + "/" + (maxScroll + 1);
            fontRendererObj.drawString(page, x0 + W - 8 - fontRendererObj.getStringWidth(page), y0 + H - 14, 0x707070);
        }
        super.drawScreen(mx, my, partial);
        if (hover != null) drawHoveringText(Collections.singletonList(hover), mx, my, fontRendererObj);
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
        int x0 = left(), ly = top() + 24;
        if (mx < x0 + 4 || mx >= x0 + LIST_W) return;
        int i = (my - ly) / ROW;
        if (my >= ly && i >= 0 && i < cats.size()) {
            selected = i;
            scroll = 0;
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) scroll += wheel > 0 ? -1 : 1;
    }
}
