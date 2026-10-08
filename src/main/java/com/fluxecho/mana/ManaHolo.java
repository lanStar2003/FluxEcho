package com.fluxecho.mana;

import static com.fluxecho.client.FluxDraw.*;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.opengl.GL11;

import com.fluxecho.Config;
import com.fluxecho.client.HoloStore;
import com.fluxecho.client.ShaderCompat;
import com.fluxecho.codex.EchoNet;
import com.fluxecho.core.CoreCircuits;
import com.fluxecho.logic.Compact;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * The Mana Echo Spring's hologram: what the server last sent ({@link HoloStore}), drawn after the world as a pane
 * facing the player, as FluxDepths' collector and the Flux Vis Pedestal draw theirs, so a shader pack lights it
 * properly. A spring whose hologram is switched off, broken, or not heard from for a few seconds is dropped.
 */
public final class ManaHolo {

    /** The pane's size in its own pixels, the size of one pixel in blocks, and where its lower edge floats. */
    private static final int W = 140, H = 96;
    private static final float PX = 1 / 90f;
    private static final double BOTTOM = 1.25;

    private ManaHolo() {}

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new ManaHolo());
    }

    @SubscribeEvent
    public void onRenderLast(RenderWorldLastEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        int range = Config.manaHologramRange;
        if (mc.theWorld == null || range <= 0 || ShaderCompat.shadowPass()) return;
        float t = mc.theWorld.getTotalWorldTime() + e.partialTicks;
        for (HoloStore.Shown s : HoloStore.current(EchoNet.HOLO_MANA, mc.theWorld.provider.dimensionId)) {
            TileEntity te = mc.theWorld.getTileEntity(s.x, s.y, s.z);
            if (!(te instanceof IGregTechTileEntity g) || !(g.getMetaTileEntity() instanceof MTEManaSpring m)
                || !m.hologram()) {
                HoloStore.forget(s);
                continue;
            }
            double x = s.x - RenderManager.renderPosX, y = s.y - RenderManager.renderPosY,
                z = s.z - RenderManager.renderPosZ;
            double dx = x + 0.5, dy = y + 1.8, dz = z + 0.5;
            float fade = (float) Math.min(1, (range - Math.sqrt(dx * dx + dy * dy + dz * dz)) / 2);
            NBTTagCompound d = s.data;
            if (fade > 0.05f && d != null) draw(d, x, y, z, t, fade);
        }
    }

    private static void draw(NBTTagCompound d, double x, double y, double z, float t, float fade) {
        float a = fade * (0.9f + 0.07f * (float) Math.sin(t * 0.9) * (float) Math.sin(t * 0.17));
        float glitch = ((int) t) % 89 < 2 ? 1.5f : 0f;
        int tier = d.getByte("t"), color = CoreCircuits.color(tier);

        GL11.glPushMatrix();
        worldBegin();
        GL11.glTranslated(x + 0.5, y, z + 0.5);
        GL11.glRotatef(-RenderManager.instance.playerViewY, 0f, 1f, 0f);
        beam(W / 2.0 * PX, a);
        GL11.glTranslated(0, BOTTOM + H * PX, 0);
        GL11.glScalef(-PX, -PX, PX);
        GL11.glTranslatef(-W / 2f + glitch, 0, 0);

        begin();
        pane(0, 0, W, H, a * 0.85f);
        rect(1, 1, W - 1, 15, MANA, a * 0.16f);
        rect(1, 15, W - 1, 16, MANA, a * 0.6f);
        scan(1, 1, W - 1, H - 1, t, MANA, a * 0.12f);
        end();
        content(d, tier, color, t, a);

        worldEnd();
        GL11.glPopMatrix();
    }

    private static void content(NBTTagCompound d, int tier, int color, float t, float a) {
        int l = 5, r = W - 5;
        text(ManaText.t("holo.title"), l, 4, CYAN, a);
        right(CoreCircuits.label(tier), r, 4, color, a);

        MTEManaSpring.State st = MTEManaSpring.State.of(d.getByte("s"));
        String status = ManaText.status(st);
        if (st == MTEManaSpring.State.WORKING) status += "...".substring(0, (int) (t / 6) % 4);
        text(status, l, 19, ManaText.statusColor(st), a);
        if (tier > 0) right(ManaText.t("holo.rate", Compact.si(d.getInteger("mt"))), r, 19, WHITE, a);

        // the mana waiting to be handed on, the cycle's progress along its top edge
        long buffer = d.getInteger("b"), cap = Math.max(1, d.getInteger("bc"));
        int p = d.getInteger("p"), max = d.getInteger("m");
        float progress = max > 0 ? Math.min(1f, (float) p / max) : 0f;
        begin();
        bar(l, 31, r, 38, Compact.fraction(buffer, cap), st == MTEManaSpring.State.POOL_FULL ? AMBER : MANA, a);
        rect(l + 1, 31, l + 1 + (r - l - 2) * progress, 32, MANA_PINK, a * 0.9f);
        end();
        small(ManaText.bufferBar(buffer, cap), l, 40, 0.6f, DIM, a);
        smallRight(ManaText.reach(d.getInteger("r"), d.getInteger("hh")), r, 40, 0.6f, DIM, a);

        // power
        long eu = d.getLong("e"), euCap = Math.max(1, d.getLong("ec"));
        begin();
        bar(l, 49, r, 56, Compact.fraction(eu, euCap), color, a);
        end();
        small(ManaText.energyBar(eu, euCap), l, 58, 0.6f, DIM, a);
        if (tier > 0) smallRight(ManaText.t("holo.eu", Compact.si(d.getLong("et"))), r, 58, 0.6f, DIM, a);

        // the nearest pools
        int[] fill = d.getIntArray("pf");
        int n = MTEManaSpring.SHOWN_POOLS, gap = 3;
        double each = (r - l - gap * (n - 1)) / (double) n;
        begin();
        for (int i = 0; i < n; i++) {
            double x0 = l + i * (each + gap);
            int f = i < fill.length ? fill[i] : -1;
            if (f < 0) frame(x0, 67, x0 + each, 72, SEAM, a * 0.6f);
            else bar(x0, 67, x0 + each, 72, f / 1000f, MANA, a);
        }
        end();
        small(ManaText.poolLine(d.getInteger("n"), d.getLong("pm"), d.getLong("pc")), l, 74, 0.6f, CYAN, a);

        // petals and what it has handed on
        int cf = d.getInteger("cf");
        small(ManaText.t("holo.petals", d.getInteger("pe"), Compact.si(d.getLong("c"))), l, 84, 0.6f, DIM, a);
        smallRight(ManaText.chargeOrSent(cf, d.getLong("d")), r, 84, 0.6f, DIM, a);
    }

    /** Light from the top of the block up to the pane's lower edge. */
    private static void beam(double half, float a) {
        begin();
        Tessellator tes = start(GL11.GL_TRIANGLES);
        tes.setColorRGBA_I(MANA, (int) (a * 80));
        tes.addVertex(0, 1.02, 0);
        tes.setColorRGBA_I(MANA_PINK, (int) (a * 14));
        tes.addVertex(-half, BOTTOM, 0);
        tes.addVertex(half, BOTTOM, 0);
        tes.draw();
        end();
    }
}
