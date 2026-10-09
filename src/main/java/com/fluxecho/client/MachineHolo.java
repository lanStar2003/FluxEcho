package com.fluxecho.client;

import static com.fluxecho.client.FluxDraw.*;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.StatCollector;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.opengl.GL11;

import com.fluxecho.Config;
import com.fluxecho.codex.EchoNet;
import com.fluxecho.core.EchoText;
import com.fluxecho.core.MTEEchoMachine;
import com.fluxecho.core.MachineId;
import com.fluxecho.logic.Compact;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * The echo machines' holograms: what the server last sent ({@link HoloStore}), drawn after the world as a pane facing
 * the player (shader-safe, like the spring's and the pedestal's): name and tier, status, the machine's motif with its
 * sample and output beside it, the cycle and the EU buffer.
 */
public final class MachineHolo {

    private static final int W = 150, H = 104;
    private static final float PX = 1 / 90f;
    private static final double BOTTOM = 1.25;

    private MachineHolo() {}

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new MachineHolo());
    }

    @SubscribeEvent
    public void onRenderLast(RenderWorldLastEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        int range = Config.machineHologramRange;
        if (mc.theWorld == null || range <= 0 || ShaderCompat.shadowPass()) return;
        float t = mc.theWorld.getTotalWorldTime() + e.partialTicks;
        for (HoloStore.Shown s : HoloStore.current(EchoNet.HOLO_MACHINE, mc.theWorld.provider.dimensionId)) {
            TileEntity te = mc.theWorld.getTileEntity(s.x, s.y, s.z);
            if (!(te instanceof IGregTechTileEntity g) || !(g.getMetaTileEntity() instanceof MTEEchoMachine m)
                || !m.hologram()) {
                HoloStore.forget(s);
                continue;
            }
            double x = s.x - RenderManager.renderPosX, y = s.y - RenderManager.renderPosY,
                z = s.z - RenderManager.renderPosZ;
            double dx = x + 0.5, dy = y + 1.8, dz = z + 0.5;
            float fade = (float) Math.min(1, (range - Math.sqrt(dx * dx + dy * dy + dz * dz)) / 2);
            NBTTagCompound d = s.data;
            if (fade > 0.05f && d != null) draw(m.kind(), m.voltageTier(), d, x, y, z, t, fade);
        }
    }

    private static void draw(MachineId k, int tier, NBTTagCompound d, double x, double y, double z, float t,
        float fade) {
        float a = fade * (0.9f + 0.07f * (float) Math.sin(t * 0.9) * (float) Math.sin(t * 0.17));
        float glitch = ((int) t) % 83 < 2 ? 1.5f : 0f;

        GL11.glPushMatrix();
        worldBegin();
        GL11.glTranslated(x + 0.5, y, z + 0.5);
        GL11.glRotatef(-RenderManager.instance.playerViewY, 0f, 1f, 0f);
        beam(W / 2.0 * PX, k.accent, a);
        GL11.glTranslated(0, BOTTOM + H * PX, 0);
        GL11.glScalef(-PX, -PX, PX);
        GL11.glTranslatef(-W / 2f + glitch, 0, 0);

        begin();
        pane(0, 0, W, H, a * 0.85f);
        rect(1, 1, W - 1, 15, k.accent, a * 0.16f);
        rect(1, 15, W - 1, 16, k.accent, a * 0.6f);
        scan(1, 1, W - 1, H - 1, t, k.accent, a * 0.12f);
        end();
        content(k, tier, d, t, a);

        worldEnd();
        GL11.glPopMatrix();
    }

    private static void content(MachineId k, int tier, NBTTagCompound d, float t, float a) {
        int l = 5, r = W - 5;
        String name = StatCollector.translateToLocal("gt.blockmachines.fluxecho." + k.key + ".name");
        text(fit(name, W - 40), l, 4, CYAN, a);
        right(EchoText.tier(tier), r, 4, k.accent, a);

        String status = d.getString("s");
        String shown = EchoText.status(status);
        if ("working".equals(status)) shown += "...".substring(0, (int) (t / 6) % 4);
        text(fit(shown, 100), l, 19, EchoText.statusColor(status), a);
        int eut = d.getInteger("u");
        if (eut > 0) right(Compact.si(eut) + " EU/t", r, 19, WHITE, a);

        // the motif, and what it echoes and makes
        int p = d.getInteger("p"), max = d.getInteger("m");
        float progress = max > 0 ? Math.min(1f, (float) p / max) : 0f;
        begin();
        rect(l, 30, l + 40, 66, DEEP, a * 0.9f);
        frame(l, 30, l + 40, 66, SEAM, a);
        GL11.glPushMatrix();
        GL11.glTranslatef(l + 1, 31, 0);
        Motifs.draw(k.motif, 38, 34, t, progress, max > 0, k.accent, a);
        GL11.glPopMatrix();
        end();
        int tx = l + 45, tw = r - tx;
        ItemStack sample = stack(d, "sm"), out = stack(d, "o");
        if (k.sample) small(
            fit(
                sample == null ? EchoText.t("gui.no_sample") : EchoText.t("gui.sample", sample.getDisplayName()),
                (int) (tw / 0.75f)),
            tx,
            32,
            0.75f,
            sample == null ? AMBER : WHITE,
            a);
        if (out != null) small(
            fit(EchoText.t("holo.output", out.getDisplayName(), out.stackSize), (int) (tw / 0.75f)),
            tx,
            k.sample ? 43 : 32,
            0.75f,
            DIM,
            a);
        String info = EchoText.decode(d.getString("i"));
        if (!info.isEmpty()) small(fit(info, (int) (tw / 0.75f)), tx, k.sample ? 54 : 43, 0.75f, CYAN, a);

        // the cycle and the buffer
        begin();
        bar(l, 71, r, 77, progress, k.accent, a);
        long eu = d.getLong("e"), cap = Math.max(1, d.getLong("ec"));
        bar(l, 86, r, 93, Compact.fraction(eu, cap), k.accent, a);
        end();
        small(EchoText.t("holo.cycle", Math.round(progress * 100), EchoText.seconds(max)), l, 79, 0.6f, DIM, a);
        small(EchoText.t("gui.energy", Compact.si(eu), Compact.si(cap)), l, 95, 0.6f, DIM, a);
    }

    private static ItemStack stack(NBTTagCompound d, String key) {
        return d.hasKey(key) ? ItemStack.loadItemStackFromNBT(d.getCompoundTag(key)) : null;
    }

    /** Light from the top of the block up to the pane's lower edge. */
    private static void beam(double half, int c, float a) {
        begin();
        Tessellator tes = start(GL11.GL_TRIANGLES);
        tes.setColorRGBA_I(c, (int) (a * 70));
        tes.addVertex(0, 1.02, 0);
        tes.setColorRGBA_I(CYAN, (int) (a * 14));
        tes.addVertex(-half, BOTTOM, 0);
        tes.addVertex(half, BOTTOM, 0);
        tes.draw();
        end();
    }
}
