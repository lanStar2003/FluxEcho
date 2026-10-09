package com.fluxdepths.client;

import static com.fluxdepths.client.FluxDraw.*;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.opengl.GL11;

import com.fluxdepths.Config;
import com.fluxdepths.shard.MTEFluxCollector;
import com.fluxdepths.shard.ShardState;
import com.fluxdepths.shard.ShardText;
import com.fluxdepths.shard.ShardTier;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * The collectors' holograms on the client: what the server last sent for each, drawn above the machine as a pane
 * that always faces the player. A collector whose hologram is switched off, broken, or not heard from for a few
 * seconds is dropped.
 */
public final class HoloClient {

    /** Pixels of the pane, the size of one in blocks, and where its lower edge floats above the block. */
    private static final int W = 148, H = 92;
    private static final float PX = 1 / 90f;
    private static final double BOTTOM = 1.25;
    private static final long STALE_MS = 2500;

    private static final class Shown {

        final int dim, x, y, z;
        volatile NBTTagCompound data;
        volatile long at;

        Shown(int dim, int x, int y, int z) {
            this.dim = dim;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static final Map<String, Shown> SHOWN = new ConcurrentHashMap<>();

    private HoloClient() {}

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new HoloClient());
    }

    /** From the network thread. */
    public static void receive(int dim, int x, int y, int z, NBTTagCompound data) {
        Shown s = SHOWN.computeIfAbsent(dim + ":" + x + ":" + y + ":" + z, k -> new Shown(dim, x, y, z));
        s.data = data;
        s.at = System.currentTimeMillis();
    }

    @SubscribeEvent
    public void onRenderLast(RenderWorldLastEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || SHOWN.isEmpty()) return;
        int range = Config.hologramRange;
        int dim = mc.theWorld.provider.dimensionId;
        long now = System.currentTimeMillis();
        for (Iterator<Shown> it = SHOWN.values()
            .iterator(); it.hasNext();) {
            Shown s = it.next();
            if (now - s.at > STALE_MS || range <= 0) {
                it.remove();
                continue;
            }
            if (s.dim != dim) continue;
            TileEntity te = mc.theWorld.getTileEntity(s.x, s.y, s.z);
            if (!(te instanceof IGregTechTileEntity g) || !(g.getMetaTileEntity() instanceof MTEFluxCollector m)
                || !m.hologram()) {
                it.remove();
                continue;
            }
            double dx = s.x + 0.5 - RenderManager.renderPosX, dy = s.y + 1.8 - RenderManager.renderPosY,
                dz = s.z + 0.5 - RenderManager.renderPosZ;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            float fade = (float) Math.min(1, (range - dist) / 2);
            if (fade <= 0.05f) continue;
            draw(
                s.data,
                s.x - RenderManager.renderPosX,
                s.y - RenderManager.renderPosY,
                s.z - RenderManager.renderPosZ,
                mc.theWorld.getTotalWorldTime() + e.partialTicks,
                fade);
        }
    }

    private static void draw(NBTTagCompound d, double x, double y, double z, float t, float fade) {
        float a = fade * (0.9f + 0.07f * (float) Math.sin(t * 0.9) * (float) Math.sin(t * 0.17));
        ShardTier[] tiers = ShardTier.values();
        ShardTier tier = tiers[Math.max(0, Math.min(tiers.length - 1, d.getByte("t")))];

        GL11.glPushMatrix();
        GL11.glPushAttrib(
            GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_LIGHTING_BIT
                | GL11.GL_CURRENT_BIT);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_FOG);
        GL11.glDepthMask(false);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);

        GL11.glTranslated(x + 0.5, y, z + 0.5);
        GL11.glRotatef(-RenderManager.instance.playerViewY, 0f, 1f, 0f);
        beam(W / 2.0 * PX, tier.color(), a);
        GL11.glTranslated(0, BOTTOM + H * PX, 0);
        GL11.glScalef(-PX, -PX, PX);
        GL11.glTranslatef(-W / 2f + (((int) t) % 89 < 2 ? 1.5f : 0f), 0, 0);

        begin();
        pane(0, 0, W, H, a * 0.85f);
        rect(1, 1, W - 1, 15, tier.color(), a * 0.18f);
        rect(1, 15, W - 1, 16, tier.color(), a * 0.7f);
        scan(1, 1, W - 1, H - 1, t, CYAN, a * 0.12f);
        end();

        // title and tier
        text(ShardText.t("holo.title"), 5, 4, CYAN, a);
        right(tier.label(), W - 5, 4, tier.color(), a);

        // what it does, and the vein
        ShardState.Status st = ShardState.status(d.getByte("s"));
        text(ShardText.status(st), 5, 19, ShardText.statusColor(st), a);
        String vein = d.getString("v");
        if (!vein.isEmpty()) right(font().trimStringToWidth(vein, 70), W - 5, 19, WHITE, a);

        // the cycle
        int p = d.getInteger("p"), max = d.getInteger("m");
        float progress = max > 0 ? Math.min(1f, (float) p / max) : 0f;
        begin();
        bar(5, 31, W - 5, 38, progress, tier.color(), a);
        end();
        small(ShardText.t("holo.cycle", Math.round(progress * 100)), 5, 40, 0.6f, DIM, a);
        smallRight(ShardText.speedCaption(tier), W - 5, 40, 0.6f, DIM, a);

        // the buffer
        begin();
        String buffer;
        if (tier.steam()) {
            long st2 = d.getLong("st"), cap = Math.max(1, d.getLong("sc"));
            bar(5, 49, W - 5, 56, (float) Math.min(1, (double) st2 / cap), AMBER, a);
            buffer = ShardText.steamBar(st2 * 2, cap * 2);
        } else {
            long eu = d.getLong("e"), cap = Math.max(1, d.getLong("c"));
            bar(5, 49, W - 5, 56, (float) Math.min(1, (double) eu / cap), tier.color(), a);
            buffer = ShardText.energyBar(eu, cap);
        }
        end();
        small(buffer, 5, 58, 0.6f, DIM, a);
        smallRight(ShardText.powerShort(tier), W - 5, 58, 0.6f, DIM, a);

        // imprints, drill, output
        int uses = d.getInteger("h");
        text(ShardText.imprintCaption(d.getInteger("i"), tier.imprints), 5, 67, WHITE, a);
        right(ShardText.drillShort(uses), W - 5, 67, uses > 0 ? WHITE : AMBER, a);
        text(ShardText.t("holo.produced", ShardText.compact(d.getLong("o"))), 5, 78, DIM, a);
        if (tier.fluidPerOre > 0)
            right(ShardText.t("holo.fluid", ShardText.compact(d.getInteger("f"))), W - 5, 78, DIM, a);

        GL11.glPopAttrib();
        GL11.glPopMatrix();
    }

    private static void smallRight(String s, double rx, double y, float scale, int rgb, float a) {
        small(s, rx - font().getStringWidth(s) * scale, y, scale, rgb, a);
    }

    /** Light from the top of the block up to the pane's lower edge. */
    private static void beam(double half, int rgb, float a) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        Tessellator tes = Tessellator.instance;
        tes.startDrawing(GL11.GL_TRIANGLES);
        tes.setColorRGBA_I(rgb, (int) (a * 80));
        tes.addVertex(0, 1.02, 0);
        tes.setColorRGBA_I(CYAN, (int) (a * 16));
        tes.addVertex(-half, BOTTOM, 0);
        tes.addVertex(half, BOTTOM, 0);
        tes.draw();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }
}
