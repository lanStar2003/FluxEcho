package com.fluxecho.library.client;

import static com.fluxecho.client.FluxDraw.*;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.fluxecho.client.Motifs;
import com.fluxecho.core.EchoText;
import com.fluxecho.library.LibraryGui;
import com.fluxecho.library.TileLibrary;

/** The drawn parts of the Echo Library's GUI ({@code LibraryGui}). Client only. */
public final class LibraryScreen {

    private static final RenderItem ITEMS = new RenderItem();

    private LibraryScreen() {}

    private static float ticks() {
        return Minecraft.getSystemTime() / 50f;
    }

    public static void background(TileLibrary l, float x, float y, float w, float h, int rows) {
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        begin();
        pane(0, 0, w, h, 1f);
        int left = (int) (w - 162) / 2;
        gradient(left - 3, 21, left + 165, 24 + rows * 18 + 3, 0x120A28, 0.9f, DEEP, 0.9f);
        frame(left - 3, 21, left + 165, 24 + rows * 18 + 3, VIOLET, 0.6f);
        // the depth behind the shelves: lines running in to a point
        float t = ticks();
        double cx = w / 2.0, cy = 24 + rows * 9;
        for (int i = 0; i < 6; i++) {
            double f = (t * 0.01 + i / 6.0) % 1;
            double hw = (162 / 2.0) * (1 - f), hh = rows * 9 * (1 - f);
            frame(cx - hw, cy - hh, cx + hw, cy + hh, VIOLET, 0.12f * (float) f);
        }
        int deskY = LibraryGui.deskY(l);
        rect(8, deskY - 4, w - 8, deskY - 3, SEAM, 1f);
        end();
        GL11.glPopMatrix();
    }

    public static void header(TileLibrary l, float x, float y, float w, float h) {
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        begin();
        hgradient(1, 1, w * 0.6, h - 1, VIOLET, 0.3f, VIOLET, 0f);
        rect(1, h - 1, w - 1, h, VIOLET, 0.6f);
        end();
        String left = EchoText.t("library.gui.title");
        text(left, 4, 3, VIOLET, 1f);
        String dock = l.guiDock;
        String right = EchoText.t("dock." + dock);
        right(
            fit(right, (int) w - 12 - font().getStringWidth(left)),
            (int) w - 4,
            3,
            "docked".equals(dock) ? (l.guiPowered ? GREEN : RED) : AMBER,
            1f);
        GL11.glPopMatrix();
    }

    public static void arrow(float x, float y, float w, float h, boolean right) {
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        begin();
        gradient(0, 0, w, h, PANE, 1f, DEEP, 1f);
        frame(0, 0, w, h, SEAM, 1f);
        double cx = w / 2.0, cy = h / 2.0;
        for (int i = 0; i < 4; i++) {
            double dx = (right ? -1 : 1) * (1.5 - i);
            rect(cx + dx * 1 - 0.5, cy - (3 - i), cx + dx * 1 + 0.5, cy + (3 - i), CYAN, 1f);
        }
        end();
        GL11.glPopMatrix();
    }

    /** The sample the desk writes cards for. */
    public static void chosen(TileLibrary l, float x, float y, float w, float h) {
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        begin();
        gradient(0, 0, w, h, 0x2A2010, 1f, DEEP, 1f);
        frame(0, 0, w, h, 0xFFD27A, 0.8f);
        end();
        ItemStack s = l.selectedSample(l.guiSelected);
        if (s != null) item(s, 1, 1);
        GL11.glPopMatrix();
    }

    /** The press between the desk's slots: an arrow that runs while it writes. */
    public static void press(TileLibrary l, float x, float y, float w, float h) {
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        begin();
        float t = ticks();
        double mid = h / 2.0;
        for (int i = 0; i < 4; i++) {
            double f = (t * 0.04 + i / 4.0) % 1;
            Motifs.dot(3 + f * (w - 8), mid, 1.5, VIOLET, (float) Math.sin(f * Math.PI));
        }
        rect(w - 6, mid - 3, w - 5, mid + 3, CYAN, 1f);
        rect(w - 5, mid - 2, w - 4, mid + 2, CYAN, 1f);
        rect(w - 4, mid - 1, w - 3, mid + 1, CYAN, 1f);
        end();
        GL11.glPopMatrix();
    }

    /** Samples kept, the machines it lends to, power. */
    public static void state(TileLibrary l, float x, float y, float w, float h) {
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        int lw = (int) w - 8;
        text(fit(EchoText.t("library.gui.kept", l.sampleCount(), l.capacity()), lw), 4, 2, WHITE, 1f);
        String lend = EchoText.t("library.gui.lends", l.guiLends);
        text(
            fit("docked".equals(l.guiDock) ? lend : EchoText.t("library.gui.undocked"), lw),
            4,
            13,
            "docked".equals(l.guiDock) ? CYAN : AMBER,
            1f);
        GL11.glPopMatrix();
    }

    static void item(ItemStack s, int x, int y) {
        Minecraft mc = Minecraft.getMinecraft();
        RenderHelper.enableGUIStandardItemLighting();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        ITEMS.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), s, x, y);
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL11.GL_LIGHTING);
    }
}
