package com.fluxecho.client.occlusion;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

import com.fluxecho.FluxEcho;
import com.fluxecho.client.skin.ClientSkin;
import com.fluxecho.config.RenderConfig;
import com.fluxecho.logic.occlusion.GateCore;
import com.fluxecho.logic.occlusion.GatePiece;
import com.fluxecho.logic.skin.SkinEntry;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The city's occlusion gate on the client (0.17.0, style FINAL §8.4.1, refactor FINAL F06): the one place every
 * building's light drawn after the world (③) asks whether a piece of it is hidden behind nearer blocks, so that with a
 * shader pack whose depth buffer is not the world's when that light is drawn (prototype P0) the picture loses not one
 * piece and no piece shows through a wall.
 * <p>
 * <b>How a renderer uses it.</b> It lists its pieces once a client tick through a {@link Pieces} source ({@link #add}),
 * each with a stable id and one to five sample points ({@link GatePiece}); when it draws (any number of times a frame,
 * behind light gates too) it asks {@link #draws} with the piece's id and distance, which only reads the last round's
 * answers, so the drawing stays idempotent. Every {@link GateCore#EVERY} ticks a round casts the lines of sight from
 * the player's eye through the client world ({@link WorldSight}), at most {@link GateCore#MAX_RAYS} of them, near to
 * far, with the buildings' boxes from the box table ({@code SkinTable}) and the pieces' hosts for the coarse test.
 * <p>
 * <b>Modes</b> ({@code render.occlusion}, ids.md §7): {@code gate}, the default that works with every pack, hides by
 * this gate; {@code depth} leaves the near pieces to the depth buffer once {@code /fluxecho probe depth} shows the
 * user's pack keeps it, and still hides the far ones a building or hill stands in front of. In 0.17.0 the only pieces
 * are the depth probe's; each building splits its own light into pieces in its version (style §4, "深度失效时").
 */
@SideOnly(Side.CLIENT)
public final class OcclusionGate {

    /** Lists a renderer's pieces for the next round. Called on the client thread, once a round. */
    @FunctionalInterface
    public interface Pieces {

        void list(World w, List<GatePiece> out);
    }

    /** The eye over the feet: vanilla's camera height. */
    static final double EYE = 1.62;

    private static final List<Pieces> SOURCES = new CopyOnWriteArrayList<>();
    private static final GateCore CORE = new GateCore();
    private static boolean registered, failed;
    private static long ticks;

    private OcclusionGate() {}

    /** Adds a renderer's source of pieces. */
    public static void add(Pieces source) {
        SOURCES.add(source);
    }

    /** The mode {@code render.occlusion} asks for. */
    public static GateCore.Mode mode() {
        return RenderConfig.OCCLUSION_DEPTH.equals(RenderConfig.occlusion) ? GateCore.Mode.DEPTH : GateCore.Mode.GATE;
    }

    /** The gate's own answer for a piece, whatever the mode: whether the last round saw it. */
    public static boolean visible(long id) {
        return CORE.visible(id);
    }

    /**
     * Whether a renderer draws a piece now: in the gate mode the gate's answer; in the depth mode a piece nearer than
     * {@link GateCore#FAR} always (the depth buffer hides it), a farther one by the gate.
     */
    public static boolean draws(long id, double distance) {
        if (mode() == GateCore.Mode.DEPTH && distance < GateCore.FAR) return true;
        return CORE.visible(id);
    }

    /** Hooks the client tick and the world unload; called once by the client proxy. */
    public static synchronized void register() {
        if (registered) return;
        registered = true;
        Events e = new Events();
        FMLCommonHandler.instance()
            .bus()
            .register(e);
        MinecraftForge.EVENT_BUS.register(e);
    }

    /** One round: the pieces of every source, seen from the player's eye. */
    private static void round(Minecraft mc) {
        World w = mc.theWorld;
        EntityLivingBase eye = mc.renderViewEntity;
        if (w == null || eye == null) return;
        List<GatePiece> pieces = new ArrayList<>();
        for (Pieces s : SOURCES) s.list(w, pieces);
        if (pieces.isEmpty()) {
            if (CORE.rounds() > 0) CORE.clear();
            return;
        }
        List<int[]> boxes = new ArrayList<>();
        for (SkinEntry e : ClientSkin.view()
            .table()
            .entries()) boxes.add(e.box);
        // the camera: the feet (the box's floor; the client player's posY already holds its 1.62) and the eye height
        double[] at = { eye.posX, eye.boundingBox.minY + EYE, eye.posZ };
        CORE.round(pieces, at, new WorldSight(w), mode(), boxes);
    }

    /** The client tick and the world unload. */
    public static final class Events {

        @SubscribeEvent
        public void onClientTick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END || failed) return;
            if (++ticks % GateCore.EVERY != 0) return;
            try {
                round(Minecraft.getMinecraft());
            } catch (Throwable t) {
                failed = true;
                FluxEcho.LOG.error("The occlusion gate failed; it shows every piece from now on", t);
                CORE.clear();
            }
        }

        @SubscribeEvent
        public void onWorldUnload(WorldEvent.Unload e) {
            if (e.world != null && e.world.isRemote) CORE.clear();
        }
    }
}
