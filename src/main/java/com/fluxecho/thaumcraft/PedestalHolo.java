package com.fluxecho.thaumcraft;

import static com.fluxecho.client.FluxDraw.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.opengl.GL11;

import com.fluxecho.Config;
import com.fluxecho.client.ShaderCompat;
import com.fluxecho.core.EchoText;
import com.fluxecho.logic.Compact;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import thaumcraft.api.aspects.Aspect;

/**
 * The Flux Vis Pedestal's hologram, drawn after the world (like FluxDepths' collector), not by the block's renderer:
 * a shader pack draws block renderers through its own lighting, which washed the translucent pane white. Each pedestal
 * on the client reports itself every tick ({@link #seen}); the ones out of range, gone or from another world are
 * skipped.
 */
public final class PedestalHolo {

    /** The pane's width in its own pixels, the size of one pixel in blocks, and where its lower edge floats. */
    private static final int W = 132;
    private static final float PX = 1 / 90f;
    private static final double BOTTOM = 1.95;
    private static final int[] STATE_COLOR = { DIM, GREEN, CYAN, RED };

    private static final Set<TileVisPedestal> SEEN = Collections.newSetFromMap(new WeakHashMap<>());

    private PedestalHolo() {}

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new PedestalHolo());
    }

    /** From {@link TileVisPedestal#updateEntity} on the client. */
    static void seen(TileVisPedestal p) {
        SEEN.add(p);
    }

    @SubscribeEvent
    public void onRenderLast(RenderWorldLastEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        int range = Config.visHologramRange;
        if (mc.theWorld == null || SEEN.isEmpty() || range <= 0 || ShaderCompat.shadowPass()) return;
        float t = mc.theWorld.getTotalWorldTime() + e.partialTicks;
        for (TileVisPedestal p : new ArrayList<>(SEEN)) {
            if (p.isInvalid() || p.getWorldObj() != mc.theWorld) {
                SEEN.remove(p);
                continue;
            }
            double x = p.xCoord - RenderManager.renderPosX, y = p.yCoord - RenderManager.renderPosY,
                z = p.zCoord - RenderManager.renderPosZ;
            double dx = x + 0.5, dy = y + 2.5, dz = z + 0.5;
            float fade = (float) Math.min(1, (range - Math.sqrt(dx * dx + dy * dy + dz * dz)) / 2);
            if (fade > 0.05f) draw(p, x, y, z, t, fade);
        }
    }

    private static void draw(TileVisPedestal p, double x, double y, double z, float t, float fade) {
        ItemStack wand = p.wand();
        // a faint flicker, and now and then a one-pixel glitch
        float a = fade * (0.9f + 0.07f * (float) Math.sin(t * 0.9) * (float) Math.sin(t * 0.17));
        float glitch = ((int) t) % 97 < 2 ? 1.5f : 0f;
        int h = height(wand != null);

        GL11.glPushMatrix();
        worldBegin();
        GL11.glTranslated(x + 0.5, y, z + 0.5);
        GL11.glRotatef(-RenderManager.instance.playerViewY, 0f, 1f, 0f);
        beam(W / 2.0 * PX, a);
        GL11.glTranslated(0, BOTTOM + h * PX, 0);
        GL11.glScalef(-PX, -PX, PX);
        GL11.glTranslatef(-W / 2f + glitch, 0, 0);

        begin();
        pane(0, 0, W, h, a * 0.85f);
        rect(1, 1, W - 1, 15, CYAN, a * 0.16f);
        rect(1, 15, W - 1, 16, CYAN, a * 0.6f);
        scan(1, 1, W - 1, h - 1, t, CYAN, a * 0.12f);
        end();
        content(p, wand, t, a);

        worldEnd();
        GL11.glPopMatrix();
    }

    /** Pixels the pane is high: shorter without a wand, which has no primals to show. */
    private static int height(boolean wand) {
        return 5 + 12 + 19 + 11 * 4 + 4 + 11 + (wand ? 37 : 0) + 10 + 4;
    }

    /** The light from the pedestal's top up to the pane's lower edge. */
    private static void beam(double half, float a) {
        begin();
        Tessellator tes = start(GL11.GL_TRIANGLES);
        tes.setColorRGBA_I(CYAN, (int) (a * 70));
        tes.addVertex(0, 0.78, 0);
        tes.setColorRGBA_I(CYAN, (int) (a * 18));
        tes.addVertex(-half, BOTTOM, 0);
        tes.addVertex(half, BOTTOM, 0);
        tes.draw();
        end();
    }

    private static void content(TileVisPedestal p, ItemStack wand, float t, float a) {
        int l = 6, r = W - 6, y = 4;
        centered(EchoText.t("holo.title"), W / 2.0, y, CYAN, a);
        y += 13;

        long energy = p.energy(), cap = p.shownCapacity();
        float fill = Compact.fraction(energy, cap);
        begin();
        bar(l, y, r, y + 7, fill, fill < 0.25f ? AMBER : CYAN, a);
        end();
        smallCentered(EchoText.t("holo.energy", Compact.si(energy), Compact.si(cap)), W / 2.0, y + 9, 0.75f, DIM, a);
        y += 19;

        row(EchoText.t("holo.input"), EchoText.t("holo.input.value", Compact.si(p.averageInput())), l, r, y, a);
        y += 11;
        row(EchoText.t("holo.use"), EchoText.t("holo.input.value", Compact.si(p.averageOutput())), l, r, y, a);
        y += 11;
        row(EchoText.t("holo.draw"), EchoText.t("holo.draw.value", Compact.visPerSecond(p.shownRate())), l, r, y, a);
        y += 11;
        row(EchoText.t("holo.modules"), modules(p), l, r, y, a);
        y += 11;

        begin();
        rect(l, y + 1, r, y + 2, CYAN, a * 0.3f);
        end();
        y += 4;

        if (wand == null) {
            centered(EchoText.t("holo.no_wand"), W / 2.0, y, DIM, a);
            y += 11;
        } else {
            centered(font().trimStringToWidth(wand.getDisplayName(), r - l), W / 2.0, y, WHITE, a);
            y += 11;
            primals(wand, l, r, y, a);
            y += 37;
        }

        int state = Math.max(0, Math.min(STATE_COLOR.length - 1, p.state()));
        String s = EchoText.t("holo.state." + state);
        double sx = W / 2.0 - font().getStringWidth(s) / 2.0;
        if (state == TileVisPedestal.CHARGING) s += "...".substring(0, (int) (t / 6) % 4);
        text(s, sx, y, STATE_COLOR[state], a);
    }

    /** Six columns, one per primal: Thaumcraft's own icon, how full it is, and the vis in it. */
    private static void primals(ItemStack wand, int l, int r, int y, float a) {
        List<Aspect> primals = Aspect.getPrimalAspects();
        int max = VisItems.max(wand), col = (r - l) / primals.size();
        for (int i = 0; i < primals.size(); i++) {
            Aspect asp = primals.get(i);
            int color = brighter(asp.getColor()), cx = l + col * i + col / 2;
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(asp.getImage());
            Tessellator tes = start(GL11.GL_QUADS);
            tes.setColorRGBA_I(color, (int) (a * 255));
            tes.addVertexWithUV(cx - 5, y + 10, 0, 0, 1);
            tes.addVertexWithUV(cx + 5, y + 10, 0, 1, 1);
            tes.addVertexWithUV(cx + 5, y, 0, 1, 0);
            tes.addVertexWithUV(cx - 5, y, 0, 0, 0);
            tes.draw();

            int vis = VisItems.vis(wand, asp);
            begin();
            column(cx - 4, y + 12, cx + 4, y + 26, Compact.fraction(vis, max), color, a * 0.95f);
            end();
            smallCentered(Compact.vis(vis), cx, y + 28, 0.5f, color, a);
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

    /** A label on the left, its value on the right. */
    private static void row(String label, String value, int l, int r, int y, float a) {
        text(label, l, y, DIM, a);
        right(value, r, y, WHITE, a);
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
