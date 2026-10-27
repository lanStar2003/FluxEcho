package com.fluxecho.client.skin;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

import com.fluxecho.logic.skin.LampRule;
import com.fluxecho.logic.skin.RebuildQueue;
import com.fluxecho.logic.skin.SkinCodec;
import com.fluxecho.logic.skin.SkinEntry;
import com.fluxecho.logic.skin.SkinProfiles;
import com.fluxecho.logic.skin.SkinTable;
import com.fluxecho.logic.skin.SkinView;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The client's box table (style §3.9.3, refactor §5.6): the immutable snapshot the server's tables and deltas build
 * ({@link SkinView}), which the chunk renderers of the old parts read ({@link #glow}: {@code DeckRender},
 * {@code FittingRender}, {@code FrameRender}), and the clock that flips the lamp windows at dusk and dawn. Each client
 * tick it asks the snapshot which buildings' lamps the rule now draws otherwise (the wave of dusk reaching them, a
 * power change) and lets them through the throttle ({@link RebuildQueue}: a building once in 10 seconds, four boxes a
 * second); a flip marks only the building's lamp box for a rebuild. Nothing is drawn after the world and nothing is
 * kept per frame, so it holds with a shader pack on.
 */
@SideOnly(Side.CLIENT)
public final class ClientSkin {

    private static volatile SkinView view = SkinView.EMPTY;
    /** The dimension the client's world is in, or {@link Integer#MIN_VALUE} with no world. */
    private static volatile int worldDim = Integer.MIN_VALUE;
    private static final RebuildQueue QUEUE = new RebuildQueue();
    private static long ticks;
    private static boolean registered;

    private ClientSkin() {}

    /** Hooks the client tick and the world unload; called once by {@code CampusClient.register()}. */
    public static synchronized void register() {
        if (registered) return;
        registered = true;
        ClientSkin.Events e = new ClientSkin.Events();
        FMLCommonHandler.instance()
            .bus()
            .register(e);
        MinecraftForge.EVENT_BUS.register(e);
    }

    /** The snapshot now. */
    public static SkinView view() {
        return view;
    }

    /** A whole table arrived (client thread). */
    static void table(SkinCodec.Table t) {
        apply(view.table(t, worldTime()));
    }

    /** One entry changed (client thread). */
    static void delta(SkinCodec.Delta d) {
        apply(view.delta(d, worldTime()));
    }

    private static void apply(SkinView.Change c) {
        view = c.view;
        mark(c.dirty);
    }

    /** Marks block boxes {x0, y0, z0, x1, y1, z1} for a rebuild of their chunk sections. */
    private static void mark(List<int[]> boxes) {
        World w = Minecraft.getMinecraft().theWorld;
        if (w == null) return;
        for (int[] b : boxes) w.markBlockRangeForRenderUpdate(b[0], b[1], b[2], b[3], b[4], b[5]);
    }

    private static long worldTime() {
        World w = Minecraft.getMinecraft().theWorld;
        return w == null ? 0 : w.getWorldTime();
    }

    /**
     * What a chunk renderer draws of a part's glow overlay at a block: {@link LampRule#UNTOUCHED} as 0.16 drew it,
     * {@link LampRule#DARK} not at all, else in that colour. A preview world (NEI, §3.9.7) answers from its own table,
     * by day; any other world from the client's snapshot when it is of the world's dimension. Safe on any thread.
     */
    public static int glow(IBlockAccess w, int x, int y, int z, int part) {
        if (!SkinProfiles.mayBeLamp(part)) return LampRule.UNTOUCHED;
        if (w instanceof World world && world != Minecraft.getMinecraft().theWorld) {
            SkinEntry e = SkinTable.resolve(world, x, y, z);
            return e == null ? LampRule.UNTOUCHED : LampRule.glow(e, part, SkinTable.PREVIEW_TIME);
        }
        SkinView v = view;
        return v.dim == worldDim ? v.glow(x, y, z, part) : LampRule.UNTOUCHED;
    }

    /**
     * Whether a client world of dimension {@code unloadedDim} going away takes the table with it: only when the table
     * is that dimension's. On a change of dimension the server sends the new dimension's table right after the respawn
     * packet, and the channel's pump hands it over at the start of a client tick, before vanilla handles the respawn
     * and unloads the old world; forgetting the table then would leave the new dimension without one until the next
     * login.
     */
    static boolean unloadForgets(SkinView v, int unloadedDim) {
        return v.dim == unloadedDim;
    }

    /** Forgets the table (the world closed). */
    static void clear() {
        view = SkinView.EMPTY;
        worldDim = Integer.MIN_VALUE;
        synchronized (QUEUE) {
            QUEUE.clear();
        }
    }

    /** The client tick (the lamp clock) and the world unload. */
    public static final class Events {

        @SubscribeEvent
        public void onClientTick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) return;
            World w = Minecraft.getMinecraft().theWorld;
            if (w == null || w.provider == null) {
                if (view != SkinView.EMPTY) clear();
                return;
            }
            worldDim = w.provider.dimensionId;
            ticks++;
            SkinView v = view;
            if (v.size() == 0 || v.dim != worldDim) return;
            long t = w.getWorldTime();
            List<Long> go;
            synchronized (QUEUE) {
                for (long id : v.due(t)) QUEUE.request(id, ticks);
                go = QUEUE.poll(ticks);
            }
            if (!go.isEmpty()) apply(v.flip(go, t));
        }

        @SubscribeEvent
        public void onWorldUnload(WorldEvent.Unload e) {
            if (e.world == null || !e.world.isRemote) return;
            // a table of the dimension the player went to may have come first; it stays (unloadForgets)
            if (e.world.provider == null || unloadForgets(view, e.world.provider.dimensionId)) clear();
        }
    }
}
