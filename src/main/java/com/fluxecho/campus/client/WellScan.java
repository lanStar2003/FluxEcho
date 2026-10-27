package com.fluxecho.campus.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;

import com.fluxecho.FluxEcho;
import com.fluxecho.campus.Terrain;
import com.fluxecho.config.CampusConfig;
import com.fluxecho.logic.CampusPlan;
import com.fluxecho.logic.Parts;
import com.fluxecho.nexus.ClientTiles;
import com.fluxecho.nexus.TileMultiblock;
import com.fluxecho.nexus.TileNexus;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Finds, in the client tick, the light wells of the active campuses the client has loaded (0.10.1, the user's 「光井是什么」):
 * the middle of each 3x3 well of the plan ({@link CampusPlan#wells}) whose glowing deck really lies in the world, flush
 * in the floor layer (0.10.1) or one block down (a 0.10.0 campus not refreshed yet). {@link BuildRender} draws a soft
 * column of rising light over each, so a well reads as a feature of the forum, not as a pit; it only streams the
 * published list, as the renderer must not read the world.
 * <p>
 * The wells of a campus are worked out once per nexus (its plan is the nexus's own, seeded by its position); the
 * world is looked at every {@value #EVERY} ticks for every active nexus the client has loaded (a short list). How far
 * a well is drawn is the renderer's to decide, from the render position: a campus seen through a light gate is far
 * from the player but near the camera. The list is replaced whole, so the renderer never sees it half-made, and is
 * emptied when the client leaves its world. Nothing is looked for while {@code buildEffects} is off.
 */
@SideOnly(Side.CLIENT)
public final class WellScan {

    /** Ticks between two looks at the world. */
    static final int EVERY = 20;
    private static final int WELL = Parts.deck(Parts.D_WELL);

    /** One well's light: the middle of its 3x3 tiles {x, z} (block centre), where the column starts, a phase. */
    public static final class Well {

        public final double x, y, z;
        /** Ticks the well's animation is shifted by, so neighbouring wells do not pulse together. */
        public final int phase;

        Well(double x, double y, double z, int phase) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.phase = phase;
        }
    }

    private static volatile List<Well> wells = Collections.emptyList();
    /** The middles of the wells of each nexus's plan, campus cells {a, r}, by controller position and front. */
    private static final Map<String, List<int[]>> MIDDLES = new HashMap<>();
    private static boolean failed;
    private static int ticks;

    private WellScan() {}

    public static void register() {
        FMLCommonHandler.instance()
            .bus()
            .register(new WellScan());
    }

    /** The wells to draw now (immutable). */
    public static List<Well> wells() {
        return wells;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || failed) return;
        Minecraft mc = Minecraft.getMinecraft();
        World w = mc.theWorld;
        Entity viewer = mc.renderViewEntity;
        if (w == null || viewer == null) {
            if (!wells.isEmpty() || !MIDDLES.isEmpty()) {
                wells = Collections.emptyList();
                MIDDLES.clear();
            }
            return;
        }
        if (++ticks % EVERY != 0) return;
        if (!CampusConfig.buildEffects) {
            wells = Collections.emptyList();
            return;
        }
        try {
            wells = scan(w);
        } catch (Throwable x) {
            failed = true;
            wells = Collections.emptyList();
            FluxEcho.LOG.warn("The campus wells' light failed and is off until the game restarts", x);
        }
    }

    private static List<Well> scan(World w) {
        List<Well> out = new ArrayList<>();
        for (TileMultiblock m : ClientTiles.all()) {
            if (!(m instanceof TileNexus n) || m.getWorldObj() != w || m.isInvalid()) continue;
            if (n.clientCampus == null || !n.clientCampus.active) continue;
            int[] c = n.centre();
            int fx = n.front().offsetX, fz = n.front().offsetZ;
            for (int[] ar : middles(n, fx, fz)) {
                int[] d = CampusPlan.toWorld(ar[0], ar[1], fx, fz);
                int x = c[0] + d[0], z = c[2] + d[1], y0 = c[1];
                if (w.getChunkFromBlockCoords(x, z)
                    .isEmpty()) continue;
                double base;
                if (well(w, x, y0, z)) base = y0 + 1;
                else if (well(w, x, y0 - 1, z) && w.isAirBlock(x, y0, z)) base = y0;
                else continue;
                out.add(new Well(x + 0.5, base, z + 0.5, (x * 31 + z * 17) & 127));
            }
        }
        return Collections.unmodifiableList(out);
    }

    private static boolean well(World w, int x, int y, int z) {
        Block b = w.getBlock(x, y, z);
        return Terrain.isTarget(b, w.getBlockMetadata(x, y, z), WELL, false);
    }

    /** The middles of the 3x3 wells of the nexus's plan: the well cells whose eight neighbours are wells too. */
    private static List<int[]> middles(TileNexus n, int fx, int fz) {
        String key = n.xCoord + "," + n.yCoord + "," + n.zCoord + "," + fx + "," + fz;
        List<int[]> l = MIDDLES.get(key);
        if (l != null) return l;
        Set<Long> cells = n.campus()
            .plan()
            .wells();
        l = new ArrayList<>();
        for (long k : cells) {
            int a = CampusPlan.keyA(k), r = CampusPlan.keyR(k);
            boolean middle = true;
            for (int da = -1; da <= 1 && middle; da++) for (int dr = -1; dr <= 1; dr++) {
                if (!cells.contains(CampusPlan.key(a + da, r + dr))) {
                    middle = false;
                    break;
                }
            }
            if (middle) l.add(new int[] { a, r });
        }
        if (MIDDLES.size() > 64) MIDDLES.clear();
        MIDDLES.put(key, l);
        return l;
    }
}
