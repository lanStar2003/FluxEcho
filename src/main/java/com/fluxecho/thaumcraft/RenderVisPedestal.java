package com.fluxecho.thaumcraft;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import org.lwjgl.opengl.GL11;

import com.fluxecho.Config;
import com.fluxecho.core.EchoText;
import com.fluxecho.logic.Compact;

import thaumcraft.api.aspects.Aspect;

/**
 * The wand on a Flux Vis Pedestal floats above it, turning and bobbing like on Thaumcraft's own recharge pedestal;
 * while vis flows into it, it turns faster and rises a little. Above the wand the pedestal projects a hologram that
 * always faces the player: the buffer, the power coming in, how fast it draws vis, the modules, the wand's six primals
 * and what it is doing, so nobody has to click it to know.
 */
public class RenderVisPedestal extends TileEntitySpecialRenderer {

    /** The hologram's width in its own pixels, the size of one pixel in blocks, and where its lower edge floats. */
    private static final int W = 132;
    private static final float PX = 1 / 90f;
    private static final double BOTTOM = 1.95;
    private static final int CYAN = 0x4FE3FF, DEEP = 0x0A1C2A, TRACK = 0x1E3444, WHITE = 0xE8F8FF, DIM = 0x8AA8B8;
    private static final int[] STATE_COLOR = { DIM, 0x6CFF8A, CYAN, 0xFF6050 };

    private EntityItem shown;

    @Override
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partial) {
        if (!(te instanceof TileVisPedestal p) || te.getWorldObj() == null) return;
        float t = te.getWorldObj()
            .getTotalWorldTime() + partial;
        if (p.wand() != null) wand(p, x, y, z, t);
        int range = Config.visHologramRange;
        if (range <= 0) return;
        double dx = x + 0.5, dy = y + 2.5, dz = z + 0.5;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        float fade = (float) Math.min(1, (range - dist) / 2);
        if (fade > 0.05f) hologram(p, x, y, z, t, fade);
    }

    private void wand(TileVisPedestal p, double x, double y, double z, float t) {
        ItemStack wand = p.wand();
        if (shown == null || shown.worldObj != p.getWorldObj()) shown = new EntityItem(p.getWorldObj());
        shown.setEntityItemStack(wand);
        shown.hoverStart = 0f;

        boolean charging = p.charging();
        float spin = t * (charging ? 6f : 1.5f) % 360f;
        double lift = (charging ? 1.05 : 0.95) + 0.06 * Math.sin(t / 10.0);

        GL11.glPushMatrix();
        GL11.glTranslated(x + 0.5, y + lift, z + 0.5);
        GL11.glRotatef(spin, 0f, 1f, 0f);
        GL11.glScalef(1.4f, 1.4f, 1.4f);
        boolean frame = RenderItem.renderInFrame;
        RenderItem.renderInFrame = true;
        RenderManager.instance.renderEntityWithPosYaw(shown, 0, 0, 0, 0f, 0f);
        RenderItem.renderInFrame = frame;
        GL11.glPopMatrix();
    }

    // ---- the hologram

    private void hologram(TileVisPedestal p, double x, double y, double z, float t, float fade) {
        ItemStack wand = p.wand();
        // a faint flicker, and now and then a one-pixel glitch
        float a = fade * (0.88f + 0.08f * (float) Math.sin(t * 0.9) * (float) Math.sin(t * 0.17));
        float glitch = ((int) t) % 97 < 2 ? 1.5f : 0f;
        int h = height(wand != null);

        GL11.glPushMatrix();
        GL11.glPushAttrib(
            GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_LIGHTING_BIT
                | GL11.GL_CURRENT_BIT);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GL11.glDepthMask(false);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);

        GL11.glTranslated(x + 0.5, y, z + 0.5);
        GL11.glRotatef(-RenderManager.instance.playerViewY, 0f, 1f, 0f);
        beam(W / 2.0 * PX, a);
        GL11.glTranslated(0, BOTTOM + h * PX, 0);
        GL11.glScalef(-PX, -PX, PX);
        GL11.glTranslatef(glitch, 0, 0);

        frame(h, t, a);
        content(p, wand, t, a);

        GL11.glPopAttrib();
        GL11.glPopMatrix();
    }

    /** Pixels the hologram is high: shorter without a wand, which has no primals to show. */
    private static int height(boolean wand) {
        return 5 + 12 + 19 + 11 * 4 + 4 + 11 + (wand ? 37 : 0) + 10 + 4;
    }

    /** The light from the pedestal's top up to the hologram's lower edge. */
    private static void beam(double half, float a) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        Tessellator tes = Tessellator.instance;
        tes.startDrawing(GL11.GL_TRIANGLES);
        tes.setColorRGBA_I(CYAN, (int) (a * 70));
        tes.addVertex(0, 0.78, 0);
        tes.setColorRGBA_I(CYAN, (int) (a * 18));
        tes.addVertex(-half, BOTTOM, 0);
        tes.addVertex(half, BOTTOM, 0);
        tes.draw();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    /** The translucent pane, its border and corners, and a scan line running down it. */
    private static void frame(int h, float t, float a) {
        int l = -W / 2, r = W / 2;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        rect(l, 0, r, h, DEEP, a * 0.62f);
        rect(l, 0, r, 1, CYAN, a * 0.55f);
        rect(l, h - 1, r, h, CYAN, a * 0.55f);
        rect(l, 0, l + 1, h, CYAN, a * 0.35f);
        rect(r - 1, 0, r, h, CYAN, a * 0.35f);
        for (int cx : new int[] { l, r - 7 })
            for (int cy : new int[] { 0, h - 2 }) rect(cx, cy, cx + 7, cy + 2, CYAN, a);
        for (int cx : new int[] { l, r - 2 })
            for (int cy : new int[] { 0, h - 7 }) rect(cx, cy, cx + 2, cy + 7, CYAN, a);
        float scan = (t * 1.6f) % (h + 24) - 12;
        if (scan > 1 && scan < h - 3) rect(l + 1, scan, r - 1, scan + 2, CYAN, a * 0.13f);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    private void content(TileVisPedestal p, ItemStack wand, float t, float a) {
        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        int l = -W / 2 + 6, r = W / 2 - 6, y = 5;

        center(fr, EchoText.t("holo.title"), y, CYAN, a);
        y += 12;

        long energy = p.energy(), cap = p.shownCapacity();
        float fill = Compact.fraction(energy, cap);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        rect(l, y, r, y + 6, TRACK, a * 0.9f);
        if (fill > 0) rect(l, y, l + (r - l) * fill, y + 6, fill < 0.25f ? 0xFFA040 : CYAN, a * 0.9f);
        rect(l, y + 2, r, y + 3, WHITE, a * 0.08f);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        center(fr, EchoText.t("holo.energy", Compact.si(energy), Compact.si(cap)), y + 8, DIM, a);
        y += 19;

        row(fr, EchoText.t("holo.input"), EchoText.t("holo.input.value", Compact.si(p.averageInput())), l, r, y, a);
        y += 11;
        row(fr, EchoText.t("holo.use"), EchoText.t("holo.input.value", Compact.si(p.averageOutput())), l, r, y, a);
        y += 11;
        row(
            fr,
            EchoText.t("holo.draw"),
            EchoText.t("holo.draw.value", Compact.visPerSecond(p.shownRate())),
            l,
            r,
            y,
            a);
        y += 11;
        row(fr, EchoText.t("holo.modules"), modules(p), l, r, y, a);
        y += 11;

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        rect(l, y + 1, r, y + 2, CYAN, a * 0.3f);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        y += 4;

        if (wand == null) {
            center(fr, EchoText.t("holo.no_wand"), y, DIM, a);
            y += 11;
        } else {
            center(fr, fr.trimStringToWidth(wand.getDisplayName(), r - l), y, WHITE, a);
            y += 11;
            primals(fr, wand, l, r, y, a);
            y += 37;
        }

        int state = Math.max(0, Math.min(STATE_COLOR.length - 1, p.state()));
        String s = EchoText.t("holo.state." + state);
        int sw = fr.getStringWidth(s);
        if (state == TileVisPedestal.CHARGING) s += "...".substring(0, (int) (t / 6) % 4);
        fr.drawString(s, -sw / 2, y, argb(STATE_COLOR[state], a));
    }

    /** Six columns, one per primal: Thaumcraft's own icon, how full it is, and the vis in it. */
    private void primals(FontRenderer fr, ItemStack wand, int l, int r, int y, float a) {
        List<Aspect> primals = Aspect.getPrimalAspects();
        int max = VisItems.max(wand), col = (r - l) / primals.size();
        for (int i = 0; i < primals.size(); i++) {
            Aspect asp = primals.get(i);
            int color = brighter(asp.getColor()), cx = l + col * i + col / 2;
            bindTexture(asp.getImage());
            Tessellator tes = Tessellator.instance;
            tes.startDrawingQuads();
            tes.setColorRGBA_I(color, (int) (a * 255));
            tes.addVertexWithUV(cx - 5, y + 10, 0, 0, 1);
            tes.addVertexWithUV(cx + 5, y + 10, 0, 1, 1);
            tes.addVertexWithUV(cx + 5, y, 0, 1, 0);
            tes.addVertexWithUV(cx - 5, y, 0, 0, 0);
            tes.draw();

            int vis = VisItems.vis(wand, asp);
            float fill = Compact.fraction(vis, max);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            rect(cx - 4, y + 12, cx + 4, y + 26, TRACK, a * 0.9f);
            if (fill > 0) rect(cx - 4, y + 26 - 14 * fill, cx + 4, y + 26, color, a * 0.95f);
            GL11.glEnable(GL11.GL_TEXTURE_2D);

            String n = Compact.vis(vis);
            GL11.glPushMatrix();
            GL11.glTranslatef(cx, y + 28, 0);
            GL11.glScalef(0.5f, 0.5f, 1f);
            fr.drawString(n, -fr.getStringWidth(n) / 2, 0, argb(color, a));
            GL11.glPopMatrix();
        }
    }

    private static String modules(TileVisPedestal p) {
        List<String> parts = new ArrayList<>();
        int extraction = p.count(ItemVisModule.Kind.EXTRACTION);
        if (extraction > 0) parts.add(EchoText.t("holo.module.extraction", extraction));
        if (p.count(ItemVisModule.Kind.WIRELESS) > 0) parts.add(EchoText.t("holo.module.wireless"));
        if (p.count(ItemVisModule.Kind.LINK) > 0) parts.add(EchoText.t("holo.module.link"));
        return parts.isEmpty() ? EchoText.t("holo.module.none") : String.join(" ", parts);
    }

    // ---- drawing helpers, in the hologram's pixels

    private static void center(FontRenderer fr, String s, int y, int rgb, float a) {
        fr.drawString(s, -fr.getStringWidth(s) / 2, y, argb(rgb, a));
    }

    /** A label on the left, its value on the right. */
    private static void row(FontRenderer fr, String label, String value, int l, int r, int y, float a) {
        fr.drawString(label, l, y, argb(DIM, a));
        fr.drawString(value, r - fr.getStringWidth(value), y, argb(WHITE, a));
    }

    private static void rect(double x0, double y0, double x1, double y1, int rgb, float a) {
        Tessellator tes = Tessellator.instance;
        tes.startDrawingQuads();
        tes.setColorRGBA_I(rgb, (int) (Math.max(0, Math.min(1, a)) * 255));
        tes.addVertex(x0, y1, 0);
        tes.addVertex(x1, y1, 0);
        tes.addVertex(x1, y0, 0);
        tes.addVertex(x0, y0, 0);
        tes.draw();
    }

    /** The font renderer takes the alpha from the top byte, and treats almost none as full. */
    private static int argb(int rgb, float a) {
        int alpha = Math.max(5, Math.min(255, (int) (a * 255)));
        return alpha << 24 | rgb & 0xFFFFFF;
    }

    /** A quarter of the way to white, so dark aspects (perditio) still show on the dark pane. */
    private static int brighter(int rgb) {
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        r += (255 - r) / 4;
        g += (255 - g) / 4;
        b += (255 - b) / 4;
        return r << 16 | g << 8 | b;
    }
}
