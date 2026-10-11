package com.fluxecho.campus.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

import com.fluxecho.Config;
import com.fluxecho.FluxEcho;
import com.fluxecho.campus.Lift;
import com.fluxecho.logic.LiftLook;
import com.fluxecho.nexus.ClientTiles;
import com.fluxecho.nexus.TileMultiblock;
import com.fluxecho.nexus.TileNexus;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Finds the light lift's shafts in the client tick (0.12.0), so that {@link LiftRender} only streams what is ready (it
 * runs several times a frame and must not read the world). A pad has no tile entity, so the shafts are looked for:
 * <ul>
 * <li>every loaded nexus's middle column, the axis whose pads join the root, the dais and the zenith (wherever the
 * viewer stands: a gate can show a campus far away);</li>
 * <li>round the viewer, a sweep of the columns within {@value #NEAR} blocks across and {@value #SPAN} up and down,
 * {@value #PER_TICK} columns a tick, which finds any pad a player set (a shaft of their own);</li>
 * <li>the column the local player rides.</li>
 * </ul>
 * Each shaft found is read again whole every {@value #REREAD} ticks: its pads and the beams between them
 * ({@link LiftLook#beams}, the rides the lift would give), dropped once it has no pad or lies further than
 * {@value #KEEP} blocks from the viewer. At most {@value #MAX_SHAFTS} are kept. The result is published as an
 * immutable list; everything is forgotten when the client leaves its world. Off with the builder's other effects
 * ({@code buildEffects}); an error turns it off until the game restarts.
 */
@SideOnly(Side.CLIENT)
public final class LiftScan {

    /** How far across from the viewer the sweep looks, and how far up and down. */
    public static final int NEAR = 12, SPAN = 24;
    /** Columns swept a tick, the ticks between two readings of a shaft, the most shafts kept, how far one is kept. */
    public static final int PER_TICK = 25, REREAD = 20, MAX_SHAFTS = 48, KEEP = 192;

    /** A column holding pads: where it is, its pads (block heights, lowest first) and its beams. */
    public static final class Shaft {

        public final int x, z;
        public final int[] pads;
        public final List<LiftLook.Beam> beams;

        Shaft(int x, int z, int[] pads, List<LiftLook.Beam> beams) {
            this.x = x;
            this.z = z;
            this.pads = pads;
            this.beams = beams;
        }
    }

    private static volatile List<Shaft> shafts = Collections.emptyList();
    /** The columns known to hold pads (client thread only), as {@link #key}s. */
    private static final Set<Long> COLUMNS = new LinkedHashSet<>();
    private static int cursor;
    private static long lastRead = Long.MIN_VALUE;
    private static boolean failed;

    private LiftScan() {}

    /** Hooks the client tick and the world unload; called once by {@link LiftRender#register()}. */
    static void register() {
        LiftScan h = new LiftScan();
        FMLCommonHandler.instance()
            .bus()
            .register(h);
        MinecraftForge.EVENT_BUS.register(h);
    }

    /** The shafts as last read (immutable). */
    public static List<Shaft> shafts() {
        return shafts;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || failed) return;
        Minecraft mc = Minecraft.getMinecraft();
        World w = mc.theWorld;
        Entity viewer = mc.renderViewEntity != null ? mc.renderViewEntity : mc.thePlayer;
        if (w == null || viewer == null || !Config.buildEffects) {
            clear();
            return;
        }
        try {
            tick(mc, w, viewer);
        } catch (RuntimeException ex) {
            failed = true;
            clear();
            FluxEcho.LOG.warn("Finding the light lift's shafts failed; its beams are off until the game restarts", ex);
        }
    }

    @SubscribeEvent
    public void onUnload(WorldEvent.Unload e) {
        if (e.world != null && e.world.isRemote) clear();
    }

    private static void clear() {
        COLUMNS.clear();
        shafts = Collections.emptyList();
        lastRead = Long.MIN_VALUE;
    }

    private static void tick(Minecraft mc, World w, Entity viewer) {
        long now = w.getTotalWorldTime();
        int vx = MathHelper.floor_double(viewer.posX), vy = MathHelper.floor_double(viewer.posY),
            vz = MathHelper.floor_double(viewer.posZ);
        // the sweep round the viewer
        int side = 2 * NEAR + 1, all = side * side;
        for (int k = 0; k < PER_TICK; k++) {
            cursor = (cursor + 1) % all;
            int x = vx - NEAR + cursor % side, z = vz - NEAR + cursor / side;
            if (COLUMNS.contains(key(x, z))) continue;
            for (int y = Math.max(0, vy - SPAN); y <= Math.min(255, vy + SPAN); y++) {
                if (Lift.pad(w, x, y, z)) {
                    remember(x, z);
                    break;
                }
            }
        }
        // the axes of the loaded nexuses, and the column ridden
        for (TileMultiblock m : ClientTiles.all()) {
            if (!(m instanceof TileNexus n) || m.getWorldObj() != w || m.isInvalid()) continue;
            int[] c = n.centre();
            remember(c[0], c[2]);
        }
        double[] ride = Lift.clientRide();
        if (ride != null) remember((int) ride[0], (int) ride[1]);
        if (now - lastRead < REREAD && now >= lastRead) return;
        lastRead = now;
        List<Shaft> out = new ArrayList<>();
        for (Iterator<Long> it = COLUMNS.iterator(); it.hasNext();) {
            long k = it.next();
            int x = (int) (k >> 32), z = (int) k;
            if (Math.max(Math.abs(x - vx), Math.abs(z - vz)) > KEEP) {
                it.remove();
                continue;
            }
            Shaft s = read(w, x, z);
            if (s == null) it.remove();
            else out.add(s);
        }
        shafts = Collections.unmodifiableList(out);
    }

    /** Keeps a column to read, while there is room. */
    private static void remember(int x, int z) {
        if (COLUMNS.size() < MAX_SHAFTS) COLUMNS.add(key(x, z));
    }

    /** Reads a column whole: its pads and beams; null when it holds none. */
    private static Shaft read(World w, int x, int z) {
        int[] pads = new int[8];
        int n = 0;
        for (int y = 0; y <= 255; y++) {
            if (!Lift.pad(w, x, y, z)) continue;
            if (n == pads.length) pads = Arrays.copyOf(pads, n * 2);
            pads[n++] = y;
        }
        if (n == 0) return null;
        int[] p = Arrays.copyOf(pads, n);
        return new Shaft(x, z, p, LiftLook.beams(y -> Lift.cell(w, x, y, z), p));
    }

    private static long key(int x, int z) {
        return (long) x << 32 | z & 0xFFFFFFFFL;
    }
}
